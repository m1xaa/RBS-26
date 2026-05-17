"""
Oblak CDK CLI - klijent aplikacija za upravljanje funkcijama na Oblak platformi.

Komande:
    cdk login         - autentikacija ka serveru
    cdk logout        - poništavanje sesije
    cdk deploy        - pakovanje i upload Python koda
    cdk list          - lista korisnikovih funkcija
    cdk invoke        - pokretanje funkcije (alternativa curl-u)
    cdk whoami        - prikaz trenutno ulogovanog korisnika
"""

import click
import requests
import json
import os
import zipfile
import io
from pathlib import Path
from getpass import getpass

# ---------------------------------------------------------------------------
# Konfiguracija
# ---------------------------------------------------------------------------

# Putanja gde se cuva token i konfiguracija lokalno
CONFIG_DIR = Path.home() / ".cdk"
CONFIG_FILE = CONFIG_DIR / "config.json"

# Default URL servera - prepisuje se kroz config ili env var
DEFAULT_SERVER_URL = os.environ.get("OBLAK_SERVER_URL", "http://localhost:8000")


# ---------------------------------------------------------------------------
# Pomocne funkcije za config
# ---------------------------------------------------------------------------

def load_config() -> dict:
    """Ucitava lokalni config sa tokenom i server URL-om."""
    if not CONFIG_FILE.exists():
        return {}
    try:
        with open(CONFIG_FILE, "r") as f:
            return json.load(f)
    except (json.JSONDecodeError, IOError):
        return {}


def save_config(config: dict) -> None:
    """Cuva config lokalno. Pravi direktorijum ako ne postoji."""
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    with open(CONFIG_FILE, "w") as f:
        json.dump(config, f, indent=2)
    # Na Linux/Mac postavi prava da samo vlasnik moze da cita
    # (na Windows ovo je no-op ali ne pravi gresku)
    try:
        os.chmod(CONFIG_FILE, 0o600)
    except OSError:
        pass


def clear_config() -> None:
    """Brise lokalni config (koristi se pri logout)."""
    if CONFIG_FILE.exists():
        CONFIG_FILE.unlink()


def get_auth_headers() -> dict:
    """Vraca HTTP headere za autentikovane zahteve. Baca gresku ako nema tokena."""
    config = load_config()
    token = config.get("access_token")
    if not token:
        click.echo("Niste ulogovani. Pokrenite: cdk login", err=True)
        raise click.Abort()
    return {"Authorization": f"Bearer {token}"}


def get_server_url() -> str:
    """Vraca URL servera iz config-a ili default-a."""
    config = load_config()
    return config.get("server_url", DEFAULT_SERVER_URL)


# ---------------------------------------------------------------------------
# CLI grupa - glavna ulazna tacka
# ---------------------------------------------------------------------------

@click.group()
@click.version_option(version="0.1.0", prog_name="cdk")
def cli():
    """Oblak CDK CLI - upravljanje serverless Python funkcijama."""
    pass


# ---------------------------------------------------------------------------
# cdk login
# ---------------------------------------------------------------------------

@cli.command()
@click.option("--server", default=None, help="URL Oblak servera")
@click.option("--username", "-u", default=None, help="Korisnicko ime")
def login(server: str, username: str):
    """Autentikacija ka Oblak serveru. Cuva access token lokalno."""
    server_url = server or DEFAULT_SERVER_URL

    if not username:
        username = click.prompt("Korisnicko ime")

    # getpass ne prikazuje lozinku dok korisnik kuca (sigurnost)
    password = getpass("Lozinka: ")

    try:
        response = requests.post(
            f"{server_url}/auth/login",
            json={"username": username, "password": password},
            timeout=10,
        )
    except requests.RequestException as e:
        click.echo(f"Greska u komunikaciji sa serverom: {e}", err=True)
        raise click.Abort()

    if response.status_code == 401:
        click.echo("Neispravno korisnicko ime ili lozinka.", err=True)
        raise click.Abort()
    if response.status_code != 200:
        click.echo(f"Greska {response.status_code}: {response.text}", err=True)
        raise click.Abort()

    data = response.json()
    save_config({
        "server_url": server_url,
        "username": username,
        "access_token": data["access_token"],
        "refresh_token": data.get("refresh_token"),
        "expires_at": data.get("expires_at"),
    })
    click.echo(f"Uspesno ulogovani kao '{username}' na {server_url}")


# ---------------------------------------------------------------------------
# cdk logout
# ---------------------------------------------------------------------------

@cli.command()
def logout():
    """Brise lokalnu sesiju. Opciono javlja serveru za revokaciju tokena."""
    config = load_config()
    if not config.get("access_token"):
        click.echo("Nije bilo aktivne sesije.")
        return

    # Pokusaj da javis serveru za revokaciju tokena (best effort)
    try:
        requests.post(
            f"{get_server_url()}/auth/logout",
            headers=get_auth_headers(),
            timeout=5,
        )
    except requests.RequestException:
        pass  # Cak i ako server ne moze da revokuje, lokalno brisemo

    clear_config()
    click.echo("Uspesno izlogovani.")


# ---------------------------------------------------------------------------
# cdk whoami
# ---------------------------------------------------------------------------

@cli.command()
def whoami():
    """Prikazuje trenutno ulogovanog korisnika."""
    config = load_config()
    username = config.get("username")
    if not username:
        click.echo("Niste ulogovani.")
        return
    click.echo(f"Ulogovani kao: {username}")
    click.echo(f"Server: {config.get('server_url')}")


# ---------------------------------------------------------------------------
# cdk deploy - upload korisnikovog koda
# ---------------------------------------------------------------------------

@cli.command()
@click.option("--name", "-n", required=True, help="Ime funkcije")
@click.option("--path", "-p", default=".", help="Putanja do foldera sa kodom")
@click.option("--entry", "-e", default="main.py", help="Entry-point fajl")
def deploy(name: str, path: str, entry: str):
    """Pakuje folder u zip i salje serveru kao novu funkciju."""
    source_dir = Path(path).resolve()
    if not source_dir.is_dir():
        click.echo(f"Folder ne postoji: {source_dir}", err=True)
        raise click.Abort()

    entry_path = source_dir / entry
    if not entry_path.exists():
        click.echo(f"Entry-point fajl ne postoji: {entry_path}", err=True)
        raise click.Abort()

    # Pakuj folder u zip u memoriji (ne pravimo privremeni fajl)
    zip_buffer = io.BytesIO()
    with zipfile.ZipFile(zip_buffer, "w", zipfile.ZIP_DEFLATED) as zf:
        for file_path in source_dir.rglob("*"):
            if file_path.is_file():
                # Preskoci skrivene fajlove i Python cache
                if any(part.startswith(".") or part == "__pycache__"
                       for part in file_path.parts):
                    continue
                arcname = file_path.relative_to(source_dir)
                zf.write(file_path, arcname)

    zip_bytes = zip_buffer.getvalue()
    click.echo(f"Spakovano {len(zip_bytes)} bajtova. Salje se serveru...")

    try:
        response = requests.post(
            f"{get_server_url()}/functions",
            headers=get_auth_headers(),
            files={"code": (f"{name}.zip", zip_bytes, "application/zip")},
            data={"name": name, "entry": entry},
            timeout=60,
        )
    except requests.RequestException as e:
        click.echo(f"Greska u komunikaciji sa serverom: {e}", err=True)
        raise click.Abort()

    if response.status_code == 401:
        click.echo("Token istekao. Pokrenite: cdk login", err=True)
        raise click.Abort()
    if response.status_code not in (200, 201):
        click.echo(f"Greska {response.status_code}: {response.text}", err=True)
        raise click.Abort()

    data = response.json()
    click.echo(f"Funkcija '{name}' uspesno deployovana.")
    click.echo(f"  ID:      {data.get('id')}")
    click.echo(f"  URL:     {data.get('invoke_url')}")
    click.echo(f"  Status:  {data.get('status', 'pending verification')}")


# ---------------------------------------------------------------------------
# cdk list
# ---------------------------------------------------------------------------

@cli.command(name="list")
def list_functions():
    """Lista funkcija trenutno ulogovanog korisnika."""
    try:
        response = requests.get(
            f"{get_server_url()}/functions",
            headers=get_auth_headers(),
            timeout=10,
        )
    except requests.RequestException as e:
        click.echo(f"Greska u komunikaciji sa serverom: {e}", err=True)
        raise click.Abort()

    if response.status_code == 401:
        click.echo("Token istekao. Pokrenite: cdk login", err=True)
        raise click.Abort()
    if response.status_code != 200:
        click.echo(f"Greska {response.status_code}: {response.text}", err=True)
        raise click.Abort()

    functions = response.json().get("functions", [])
    if not functions:
        click.echo("Nemate deployovanih funkcija.")
        return

    click.echo(f"{'IME':<20} {'STATUS':<15} {'URL'}")
    click.echo("-" * 80)
    for fn in functions:
        click.echo(f"{fn['name']:<20} {fn.get('status', 'unknown'):<15} {fn.get('invoke_url', '')}")


# ---------------------------------------------------------------------------
# cdk invoke
# ---------------------------------------------------------------------------

@cli.command()
@click.argument("name")
@click.option("--data", "-d", default="{}", help="JSON payload za funkciju")
def invoke(name: str, data: str):
    """Poziva funkciju i prikazuje rezultat. Alternativa za curl."""
    try:
        payload = json.loads(data)
    except json.JSONDecodeError:
        click.echo("Argument --data mora biti validan JSON.", err=True)
        raise click.Abort()

    try:
        response = requests.post(
            f"{get_server_url()}/invoke/{name}",
            headers=get_auth_headers(),
            json=payload,
            timeout=30,
        )
    except requests.RequestException as e:
        click.echo(f"Greska u komunikaciji sa serverom: {e}", err=True)
        raise click.Abort()

    if response.status_code != 200:
        click.echo(f"Greska {response.status_code}: {response.text}", err=True)
        raise click.Abort()

    # Prikazi rezultat kao formatirani JSON
    try:
        click.echo(json.dumps(response.json(), indent=2, ensure_ascii=False))
    except json.JSONDecodeError:
        click.echo(response.text)


if __name__ == "__main__":
    cli()
