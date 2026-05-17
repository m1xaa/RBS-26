# Oblak CDK CLI

Klijentska aplikacija za Oblak platformu. Omogućava korisniku da:
- Autentikuje se ka serveru (JWT)
- Upload-uje Python kod za izvršavanje
- Lista postojeće funkcije
- Pokreće funkcije

## Brzi start

### 1. Instalacija

Iz root foldera projekta (gde je `setup.py`):

```bash
# Napravi virtualenv (preporučeno)
python -m venv venv

# Na Windows-u:
venv\Scripts\activate

# Na Linux/Mac:
source venv/bin/activate

# Instaliraj zavisnosti za CLI
pip install -e .

# Dodatne zavisnosti za mock server
pip install fastapi uvicorn python-multipart pyjwt bcrypt
```

Posle ovoga, komanda `cdk` je dostupna iz terminala.

### 2. Pokreni mock server (u jednom terminalu)

```bash
cd mock_server
python mock_server.py
```

Server se pokreće na `http://localhost:8000`. API dokumentacija je dostupna na `http://localhost:8000/docs`.

Mock server kreira test korisnika:
- username: `test`
- password: `test123`

### 3. Testiraj CLI (u drugom terminalu)

```bash
# Login
cdk login -u test
# Unesi lozinku: test123

# Proveri ko si
cdk whoami

# Napravi probnu funkciju
mkdir hello-fn
echo "def handler(event): return {'msg': 'Hello, ' + event.get('name', 'world')}" > hello-fn/main.py

# Deploy
cdk deploy --name hello --path hello-fn

# Lista funkcija
cdk list

# Pozovi funkciju
cdk invoke hello --data '{"name": "Jovana"}'

# Logout
cdk logout
```

## Arhitektura autentikacije

```
1. Korisnik: cdk login
2. CLI:      POST /auth/login {username, password}
3. Server:   provera lozinke (bcrypt hash poređenje)
4. Server:   generiše JWT access token (15 min) + refresh token (7 dana)
5. CLI:      čuva tokene u ~/.cdk/config.json (chmod 600)
6. CLI:      svaki sledeći zahtev šalje "Authorization: Bearer <token>"
7. Server:   validira token na svakom zahtevu
8. Logout:   token jti se dodaje u revoked listu
```

## Bezbednosne karakteristike

- **bcrypt password hashing** sa automatskim salt-om
- **JWT tokeni** sa kratkim vremenom života (15 min access, 7 dana refresh)
- **Token revocation** kroz revoked list (jti claim)
- **Lokalni token storage** u `~/.cdk/config.json` sa chmod 600
- **Konstantna error poruka** za login ("Neispravni kredencijali") - sprečava user enumeration
- **Skriveno kucanje lozinke** kroz `getpass` (ne pojavljuje se u terminalu)

## Šta nije implementirano (open items za threat model)

- TLS/HTTPS - **mora se dodati pre produkcije**
- Rate limiting na login endpoint-u (zaštita od brute force)
- MFA (multi-factor authentication, npr. TOTP)
- Refresh token rotation
- Password complexity validation
- Account lockout posle N pogrešnih pokušaja
- Audit logging svih auth događaja
- Certificate-based authentication za CLI

## Struktura projekta

```
oblak-cdk-cli/
├── cdk_cli/
│   ├── __init__.py
│   └── main.py          # Glavna CLI aplikacija (click komande)
├── mock_server/
│   └── mock_server.py   # FastAPI mock server za testiranje
├── setup.py             # Pip instalacija
└── README.md
```

## Integracija sa pravim Java serverom

Java server tima mora da implementira iste endpoint-e koje koristi CLI:

| Endpoint | Method | Body | Response |
|----------|--------|------|----------|
| `/auth/login` | POST | `{username, password}` | `{access_token, refresh_token, expires_at}` |
| `/auth/logout` | POST | - | `{message}` |
| `/functions` | POST | multipart: `name`, `entry`, `code` (file) | `{id, name, status, invoke_url}` |
| `/functions` | GET | - | `{functions: [...]}` |
| `/invoke/{name}` | POST | `{...payload}` | `{...result}` |

Svi endpoint-i osim `/auth/login` zahtevaju `Authorization: Bearer <token>` header.

JWT mora biti potpisan istim algoritmom (HS256) i istim secret-om.
