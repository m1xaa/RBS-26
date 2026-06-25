# Dodatni zadatak — CVE-2007-4559 (Jovana)

Ranjiva web aplikacija za demonstraciju **CVE-2007-4559** (Python `tarfile` path traversal).

Tema: **PixelBlog** — minimalna blog platforma kod koje korisnici mogu da "instaliraju"
temu otpremanjem `.tar` arhive. Server arhivu raspakuje bez provere imena fajlova unutar
nje, što dozvoljava napadaču da prepiše proizvoljan fajl na serveru.

## Struktura

```
dodatni/jovana/
├── backend/          # Flask API (ranjiv endpoint) + frontend se servira odavde
│   ├── app.py
│   ├── config/site_config.json
│   ├── data/about.txt
│   ├── secrets/flag.txt
│   └── themes/        # runtime instalacije tema (generisano, gitignored)
├── frontend/
│   └── index.html     # forma za upload teme + dugme za "About" stranicu
├── exploit/
│   ├── create_malicious_theme.py
│   ├── exploit.py
│   └── requirements.txt
├── writeup.md          # detaljan opis napada
└── docker-compose.yml
```

## Pokretanje

### Backend + frontend (Flask)

```bash
cd backend
pip install -r requirements.txt
python app.py
```

Aplikacija je na `http://localhost:5000` (Flask servira i `frontend/index.html` direktno,
nema potrebe za posebnim frontend serverom).

### Docker

```bash
docker compose up --build
```

## Endpointi

| Metoda | Putanja               | Opis                                                   |
|--------|------------------------|---------------------------------------------------------|
| GET    | `/`                    | Frontend (forma za upload teme)                        |
| GET    | `/api/health`          | Health check                                            |
| GET    | `/api/about`            | Čita fajl definisan u `config/site_config.json`        |
| POST   | `/api/theme/install`    | Upload `.tar` arhive teme (ranjiva ekstrakcija)         |

## Eksploatacija

```bash
cd exploit
pip install -r requirements.txt
python exploit.py --url http://localhost:5000
```

Skripta sama kreira malicioznu arhivu, otprema je, a zatim ispisuje izvučeni flag.
Detalji u [writeup.md](writeup.md).

## Ranjivost

Endpoint `/api/theme/install` u `backend/app.py` koristi `tarfile.extractall()` bez
validacije imena članova arhive. Član sa imenom poput `../../../config/site_config.json`
izlazi iz direktorijuma u koji se tema ekstrahuje i prepisuje proizvoljan fajl na serveru
— suština CVE-2007-4559.

## Mitigacija

- Validirati svaki `TarInfo.name` pre ekstrakcije i odbiti članove sa `..` ili apsolutnim putanjama.
- Koristiti `extractall(..., filter="data")` (Python 3.12+).
- Ekstrahovati nepouzdane arhive u izolovan sandbox/kontejner sa minimalnim permisijama.
- Princip najmanjih privilegija — proces koji instalira teme ne treba da ima pisanje u `config/`.
