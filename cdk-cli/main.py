"""
Oblak CDK CLI - klijent aplikacija za upload fajlova na Oblak platformu.

Server endpoint-i koje koristi:
    POST /auth/login      - {username, password} -> {accessToken, ...}
    POST /api/upload      - multipart/form-data sa poljem 'file'

/api/upload zahteva Authorization: Bearer <token> header i rolu ADMIN ili USER.

Komande:
    cdk login    - autentikacija ka serveru, cuva JWT lokalno
    cdk logout   - brise lokalnu sesiju
    cdk whoami   - prikazuje trenutno ulogovanog korisnika
    cdk upload   - upload fajla na server
    cdk config   - prikaz/podesavanje server URL-a
"""

import click
import requests
import json
import os
from pathlib import Path
from getpass import getpass

# ---------------------------------------------------------------------------
# Konfiguracija
# ---------------------------------------------------------------------------

CONFIG_DIR = Path.home() / ".cdk"
CONFIG_FILE = CONFIG_DIR / "config.json"

DEFAULT_SERVER_URL = os.environ.get("OBLAK_SERVER_URL", "http://localhost:8080")


# ---------------------------------------------------------------------------
# Helper-i za lokalni config
# ---------------------------------------------------------------------------

def load_config() -> dict:
    if not CONFIG_FILE.exists():
        return {}
    try:
        with open(CONFIG_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    except (json.JSONDecodeError, IOError):
        return {}


def save_config(config: dict) -> None:
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    with open(CONFIG_FILE, "w", encoding="utf-8") as f:
        json.dump(config, f, indent=2, ensure_ascii=False)
    try:
        os.chmod(CONFIG_FILE, 0o600)
    except OSError:
        pass


def get_server_url() -> str:
    return load_config().get("server_url", DEFAULT_SERVER_URL)


def get_auth_headers() -> dict:
    """Vraca Authorization header. Prekida komandu ako korisnik nije ulogovan."""
    token = load_config().get("access_token")
    if not token:
        click.echo("Niste ulogovani. Pokrenite: cdk login", err=True)
        raise click.Abort()
    return {"Authorization": f"Bearer {token}"}


def handle_auth_error(response: requests.Response) -> None:
    """Centralizovana poruka za 401/403."""
    if response.status_code == 401:
        click.echo("Sesija je istekla ili je token nevazeci. Pokrenite: cdk login",
                   err=True)
        raise click.Abort()
    if response.status_code == 403:
        click.echo("Nemate dozvolu za ovu operaciju.", err=True)
        raise click.Abort()


# ---------------------------------------------------------------------------
# CLI grupa
# ---------------------------------------------------------------------------

@click.group()
@click.version_option(version="0.3.0", prog_name="cdk")
def cli():
    """Oblak CDK CLI - upload fajlova na Oblak platformu."""
    pass


# ---------------------------------------------------------------------------
# cdk config
# ---------------------------------------------------------------------------

@cli.command()
@click.option("--server", default=None, help="Postavi URL Oblak servera")
def config(server: str):
    """Prikazuje ili menja konfiguraciju (server URL)."""
    if server:
        cfg = load_config()
        cfg["server_url"] = server
        save_config(cfg)
        click.echo(f"Server URL postavljen na: {server}")
        return
    cfg = load_config()
    click.echo(f"Server URL: {get_server_url()}")
    click.echo(f"Ulogovan:   {cfg.get('username', '(ne)')}")
    click.echo(f"Config:     {CONFIG_FILE}")


# ---------------------------------------------------------------------------
# cdk login
# ---------------------------------------------------------------------------

@cli.command()
@click.option("--server", default=None, help="URL Oblak servera")
@click.option("--username", "-u", default=None, help="Korisnicko ime")
def login(server: str, username: str):
    """Autentikacija ka Oblak serveru. Cuva access token lokalno."""
    server_url = server or get_server_url()

    if not username:
        username = click.prompt("Korisnicko ime")

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
        "username": data.get("username", username),
        "role": data.get("role"),
        "access_token": data["accessToken"],
        "expires_in_ms": data.get("expiresInMs"),
    })
    click.echo(f"Uspesno ulogovani kao '{username}' ({data.get('role')}) "
               f"na {server_url}")


# ---------------------------------------------------------------------------
# cdk logout
# ---------------------------------------------------------------------------

@cli.command()
def logout():
    """Brise lokalnu sesiju."""
    cfg = load_config()
    if not cfg.get("access_token"):
        click.echo("Nije bilo aktivne sesije.")
        return
    save_config({"server_url": cfg.get("server_url", DEFAULT_SERVER_URL)})
    click.echo("Uspesno izlogovani.")


# ---------------------------------------------------------------------------
# cdk whoami
# ---------------------------------------------------------------------------

@cli.command()
def whoami():
    """Prikazuje trenutno ulogovanog korisnika."""
    cfg = load_config()
    username = cfg.get("username")
    if not username or not cfg.get("access_token"):
        click.echo("Niste ulogovani.")
        return
    click.echo(f"Ulogovani kao: {username} ({cfg.get('role', 'unknown')})")
    click.echo(f"Server:        {cfg.get('server_url')}")


# ---------------------------------------------------------------------------
# cdk upload
# ---------------------------------------------------------------------------

@cli.command()
@click.argument("file_path", type=click.Path(exists=True, dir_okay=False))
def upload(file_path: str):
    """
    Salje fajl serveru kao multipart/form-data.

    Primer:
        cdk upload main.py
        cdk upload C:\\Users\\Jovana\\Desktop\\test.zip
    """
    path = Path(file_path).resolve()

    click.echo(f"Saljem fajl '{path.name}' ({path.stat().st_size} bajta) na server...")

    try:
        with open(path, "rb") as f:
            response = requests.post(
                f"{get_server_url()}/api/upload",
                headers=get_auth_headers(),
                files={"file": (path.name, f)},
                timeout=60,
            )
    except requests.RequestException as e:
        click.echo(f"Greska u komunikaciji sa serverom: {e}", err=True)
        raise click.Abort()

    handle_auth_error(response)

    if response.status_code not in (200, 201):
        click.echo(f"Greska {response.status_code}: {response.text}", err=True)
        raise click.Abort()

    click.echo(f"Fajl '{path.name}' uspesno uploadovan.")


if __name__ == "__main__":
    cli()