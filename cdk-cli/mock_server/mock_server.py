"""
Mock server za testiranje CDK CLI klijenta.

Implementira iste endpoint-e koje ce pravi Java server imati:
  POST /auth/login     - login sa username/password, vraca JWT
  POST /auth/logout    - logout (revokuje token)
  POST /functions      - deploy nove funkcije (zip upload)
  GET  /functions      - lista korisnikovih funkcija
  POST /invoke/<name>  - poziva funkciju

Pokretanje:
    pip install fastapi uvicorn python-multipart pyjwt bcrypt
    python mock_server.py

NAPOMENA: ovo je samo za testiranje CLI klijenta. Pravi server ce
biti u Javi sa pravom bazom, pravom enkripcijom, itd.
"""

from fastapi import FastAPI, HTTPException, Header, UploadFile, File, Form, Depends
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from pydantic import BaseModel
from datetime import datetime, timedelta, timezone
from typing import Optional
import jwt
import bcrypt
import secrets
import uuid

app = FastAPI(title="Oblak Mock Server")
security = HTTPBearer()

# ---------------------------------------------------------------------------
# Konfiguracija - u pravom serveru ide u env vars
# ---------------------------------------------------------------------------

# JWT secret - U PRODUKCIJI mora biti iz env var i jako kompleksan
JWT_SECRET = secrets.token_urlsafe(64)
JWT_ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRES_MINUTES = 15
REFRESH_TOKEN_EXPIRES_DAYS = 7

# In-memory "baza" - u pravom serveru ide u PostgreSQL/MySQL
USERS = {}  # username -> {"password_hash": bytes, "user_id": str}
FUNCTIONS = {}  # function_id -> {"name", "user_id", "status", ...}
REVOKED_TOKENS = set()  # token jti -> oznacen kao revokovan


# ---------------------------------------------------------------------------
# Pomocne funkcije za JWT
# ---------------------------------------------------------------------------

def create_access_token(user_id: str, username: str) -> tuple[str, datetime]:
    """Kreira JWT access token sa kratkim vremenom zivota."""
    expires = datetime.now(timezone.utc) + timedelta(minutes=ACCESS_TOKEN_EXPIRES_MINUTES)
    payload = {
        "sub": user_id,
        "username": username,
        "exp": expires,
        "iat": datetime.now(timezone.utc),
        "jti": str(uuid.uuid4()),  # token ID, koristi se za revokaciju
        "type": "access",
    }
    token = jwt.encode(payload, JWT_SECRET, algorithm=JWT_ALGORITHM)
    return token, expires


def create_refresh_token(user_id: str) -> str:
    """Kreira refresh token sa duzim vremenom zivota."""
    expires = datetime.now(timezone.utc) + timedelta(days=REFRESH_TOKEN_EXPIRES_DAYS)
    payload = {
        "sub": user_id,
        "exp": expires,
        "iat": datetime.now(timezone.utc),
        "jti": str(uuid.uuid4()),
        "type": "refresh",
    }
    return jwt.encode(payload, JWT_SECRET, algorithm=JWT_ALGORITHM)


def verify_token(token: str) -> dict:
    """Validira token i vraca payload. Baca HTTPException ako nije validan."""
    try:
        payload = jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
    except jwt.ExpiredSignatureError:
        raise HTTPException(status_code=401, detail="Token istekao")
    except jwt.InvalidTokenError:
        raise HTTPException(status_code=401, detail="Nevalidan token")

    # Proveri da li je token revokovan
    if payload.get("jti") in REVOKED_TOKENS:
        raise HTTPException(status_code=401, detail="Token je revokovan")

    return payload


def get_current_user(credentials: HTTPAuthorizationCredentials = Depends(security)) -> dict:
    """FastAPI dependency - validira token i vraca info o korisniku."""
    payload = verify_token(credentials.credentials)
    return {
        "user_id": payload["sub"],
        "username": payload["username"],
        "jti": payload["jti"],
    }


# ---------------------------------------------------------------------------
# Modeli za request/response
# ---------------------------------------------------------------------------

class LoginRequest(BaseModel):
    username: str
    password: str


class LoginResponse(BaseModel):
    access_token: str
    refresh_token: str
    expires_at: str  # ISO 8601 string


# ---------------------------------------------------------------------------
# Endpoint-i
# ---------------------------------------------------------------------------

@app.post("/auth/register")
def register(req: LoginRequest):
    """Registracija novog korisnika. U produkciji bi imao i email verifikaciju."""
    if req.username in USERS:
        raise HTTPException(status_code=400, detail="Korisnik vec postoji")

    # bcrypt - jak hash za lozinke, sa salt-om
    password_hash = bcrypt.hashpw(req.password.encode(), bcrypt.gensalt())
    user_id = str(uuid.uuid4())
    USERS[req.username] = {
        "password_hash": password_hash,
        "user_id": user_id,
    }
    return {"message": "Korisnik registrovan", "user_id": user_id}


@app.post("/auth/login", response_model=LoginResponse)
def login(req: LoginRequest):
    """Login sa username/password, vraca JWT access + refresh token."""
    user = USERS.get(req.username)
    if not user:
        # Vazno: ista poruka za "nema korisnika" i "pogresna lozinka"
        # da napadac ne moze da enumeruje postojece korisnike
        raise HTTPException(status_code=401, detail="Neispravni kredencijali")

    if not bcrypt.checkpw(req.password.encode(), user["password_hash"]):
        raise HTTPException(status_code=401, detail="Neispravni kredencijali")

    access_token, expires = create_access_token(user["user_id"], req.username)
    refresh_token = create_refresh_token(user["user_id"])

    return LoginResponse(
        access_token=access_token,
        refresh_token=refresh_token,
        expires_at=expires.isoformat(),
    )


@app.post("/auth/logout")
def logout(current_user: dict = Depends(get_current_user)):
    """Revokuje trenutni token (dodaje ga u revoked list)."""
    REVOKED_TOKENS.add(current_user["jti"])
    return {"message": "Uspesno izlogovani"}


@app.post("/functions", status_code=201)
async def deploy_function(
    name: str = Form(...),
    entry: str = Form(...),
    code: UploadFile = File(...),
    current_user: dict = Depends(get_current_user),
):
    """Prima zip sa kodom, simulira deploy. Pravi server bi pozvao Code Verifier."""
    # U pravom sistemu ovo bi proslo kroz Code Verifier (Bandit, ClamAV)
    # pa bi se enkriptovao i sacuvao u Code Storage

    zip_bytes = await code.read()
    function_id = str(uuid.uuid4())
    invoke_url = f"http://localhost:8000/invoke/{name}"

    FUNCTIONS[function_id] = {
        "id": function_id,
        "name": name,
        "entry": entry,
        "user_id": current_user["user_id"],
        "status": "verified",  # u pravom sistemu pocinje sa "pending"
        "size": len(zip_bytes),
        "invoke_url": invoke_url,
    }

    return {
        "id": function_id,
        "name": name,
        "status": "verified",
        "invoke_url": invoke_url,
    }


@app.get("/functions")
def list_functions(current_user: dict = Depends(get_current_user)):
    """Lista funkcija trenutno ulogovanog korisnika."""
    user_functions = [
        fn for fn in FUNCTIONS.values()
        if fn["user_id"] == current_user["user_id"]
    ]
    return {"functions": user_functions}


@app.post("/invoke/{name}")
def invoke_function(name: str, payload: dict, current_user: dict = Depends(get_current_user)):
    """Simulira pozivanje funkcije. Pravi server bi pokrenuo Firecracker microVM."""
    # Nadji funkciju po imenu i user-u
    fn = next(
        (f for f in FUNCTIONS.values()
         if f["name"] == name and f["user_id"] == current_user["user_id"]),
        None,
    )
    if not fn:
        raise HTTPException(status_code=404, detail=f"Funkcija '{name}' ne postoji")

    # Mock rezultat - pravi server bi prosledio payload Firecracker MVM-u
    return {
        "function": name,
        "result": "Mock rezultat - pravi server bi pokrenuo Firecracker",
        "received_payload": payload,
    }


if __name__ == "__main__":
    import uvicorn
    # Kreiraj test korisnika za laksu probu
    test_password_hash = bcrypt.hashpw(b"test123", bcrypt.gensalt())
    USERS["test"] = {
        "password_hash": test_password_hash,
        "user_id": str(uuid.uuid4()),
    }
    print("Test korisnik: username='test', password='test123'")
    print("Server pokrenut na http://localhost:8000")
    print("API dokumentacija: http://localhost:8000/docs")
    uvicorn.run(app, host="0.0.0.0", port=8000)
