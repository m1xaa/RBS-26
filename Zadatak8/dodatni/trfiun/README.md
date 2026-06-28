# Dodatni zadatak — CVE-2007-4559 (Trifun)

Ranjiva web aplikacija za demonstraciju **CVE-2007-4559** (Python `tarfile` path traversal).

## Struktura

```
dodatni/trifun/
├── server/          # Flask API (ranjiv endpoint)
├── frontend/        # Angular forma za upload
├── exploit/         # Skripte za eksploataciju
├── writeup.md       # Detaljan opis napada
└── docker-compose.yml
```

## Pokretanje

### Backend (Flask)

```bash
cd server
pip install -r requirements.txt
python app.py
```

Server sluša na `http://localhost:5000`.

### Frontend (Angular)

```bash
cd frontend
npm install
npm start
```

Frontend je na `http://localhost:4200` i proksira `/api` ka Flask serveru.

### Docker (samo backend)

```bash
docker compose up --build
```

## Endpointi

| Metoda | Putanja        | Opis                                      |
|--------|----------------|-------------------------------------------|
| GET    | `/api/health`  | Health check                              |
| GET    | `/api/welcome` | Čita fajl definisan u `settings.json`   |
| POST   | `/api/submit`  | Upload `.tar` arhive (ranjiva ekstrakcija)|

## Eksploatacija

```bash
cd exploit
python exploit.py
```

Detalji u [writeup.md](writeup.md).

## Ranjivost

Funkcija `extract_submission` u `server/app.py` koristi `tarfile.extractall()` bez validacije imena članova arhive, što omogućava path traversal (`../`) i pisanje fajlova van ciljnog direktorijuma.

## Mitigacija

- Validirati svaki `TarInfo.name` pre ekstrakcije
- Koristiti `extractall(..., filter="data")` (Python 3.12+)
- Ekstrahovati u izolovan sandbox sa ograničenim permisijama
