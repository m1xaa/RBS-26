"""
Oblak CDK CLI - klijent aplikacija za upravljanje projektima na Oblak platformi.

Server endpoint-i koje koristi:
    POST /auth/login                - {username, password} -> {accessToken, ...}
    POST /api/projects/upload       - JSON {name, files: {filename: content}}
    POST /api/projects/{id}/execute - pokrece main.py i vraca stdout
    GET  /api/projects              - lista projekata (USER svoje, ADMIN sve)

Sve /api/projects zahteva Authorization: Bearer <token> header.

Komande:
    cdk login    - autentikacija ka serveru, cuva JWT lokalno
    cdk logout   - brise lokalnu sesiju
    cdk whoami   - prikazuje trenutno ulogovanog korisnika
    cdk deploy   - upload Python projekta iz lokalnog foldera
    cdk list     - lista projekata sa servera
    cdk invoke   - pokretanje projekta po imenu ili id-u
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
    # Restriktivne dozvole na Unix-u (token je osetljiv); na Windows-u no-op.
    try:
        os.chmod(CONFIG_FILE, 0o600)
    except OSError:
        pass


def clear_config() -> None:
    if CONFIG_FILE.exists():
        CONFIG_FILE.unlink()


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
    """Centralizovana poruka za 401/403 - token istekao ili nemas pristup."""
    if response.status_code == 401:
        click.echo("Sesija je istekla ili je token nevazeci. Pokrenite: cdk login",
                   err=True)
        raise click.Abort()
    if response.status_code == 403:
        click.echo("Nemate dozvolu za ovu operaciju.", err=True)
        raise click.Abort()


# ---------------------------------------------------------------------------
# Pakovanje foldera u files-mapu
# ---------------------------------------------------------------------------

def collect_files(source_dir: Path) -> dict:
    """
    Server prima Map<String, String> (filename -> sadrzaj).
    Citamo sve .py fajlove, preskacuci skrivene i __pycache__.
    """
    files = {}
    for file_path in source_dir.rglob("*.py"):
        if not file_path.is_file():
            continue
        if any(part.startswith(".") or part == "__pycache__"
               for part in file_path.relative_to(source_dir).parts):
            continue
        rel = file_path.relative_to(source_dir).as_posix()
        try:
            files[rel] = file_path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            click.echo(f"Preskacem ne-tekstualni fajl: {rel}", err=True)
    return files


# ---------------------------------------------------------------------------
# CLI grupa
# ---------------------------------------------------------------------------

@click.group()
@click.version_option(version="0.2.0", prog_name="cdk")
def cli():
    """Oblak CDK CLI - upravljanje Python projektima na Oblak platformi."""
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

    # getpass ne ehoira lozinku dok korisnik kuca
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
    # Server je stateless (JWT), nema sta da revokujemo - samo lokalno brisanje.
    # Zadrzavamo server_url za sledeci login.
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
# cdk deploy
# ---------------------------------------------------------------------------

@cli.command()
@click.option("--name", "-n", required=True, help="Ime projekta")
@click.option("--path", "-p", default=".", help="Putanja do foldera sa kodom")
def deploy(name: str, path: str):
    """Pakuje folder u JSON i salje serveru kao novi projekat."""
    source_dir = Path(path).resolve()
    if not source_dir.is_dir():
        click.echo(f"Folder ne postoji: {source_dir}", err=True)
        raise click.Abort()

    if not (source_dir / "main.py").exists():
        click.echo(f"main.py ne postoji u {source_dir}", err=True)
        raise click.Abort()

    files = collect_files(source_dir)
    click.echo(f"Spakovano {len(files)} fajl(ova). Salje se serveru...")

    try:
        response = requests.post(
            f"{get_server_url()}/api/projects/upload",
            headers=get_auth_headers(),
            json={"name": name, "files": files},
            timeout=60,
        )
    except requests.RequestException as e:
        click.echo(f"Greska u komunikaciji sa serverom: {e}", err=True)
        raise click.Abort()

    handle_auth_error(response)

    if response.status_code not in (200, 201):
        click.echo(f"Greska {response.status_code}: {response.text}", err=True)
        raise click.Abort()

    data = response.json()
    click.echo(f"Projekat '{name}' uspesno deployovan.")
    click.echo(f"  ID:     {data.get('id')}")
    click.echo(f"  Status: {data.get('status')}")
    click.echo(f"  Owner:  {data.get('ownerUsername')}")
    click.echo(f"  Invoke: cdk invoke {name}")


# ---------------------------------------------------------------------------
# cdk list
# ---------------------------------------------------------------------------

@cli.command(name="list")
def list_projects():
    """Lista projekata sa servera (USER svoje, ADMIN sve)."""
    try:
        response = requests.get(
            f"{get_server_url()}/api/projects",
            headers=get_auth_headers(),
            timeout=10,
        )
    except requests.RequestException as e:
        click.echo(f"Greska u komunikaciji sa serverom: {e}", err=True)
        raise click.Abort()

    handle_auth_error(response)

    if response.status_code != 200:
        click.echo(f"Greska {response.status_code}: {response.text}", err=True)
        raise click.Abort()

    projects = response.json()
    if not projects:
        click.echo("Nema projekata.")
        return

    click.echo(f"{'ID':<6} {'IME':<25} {'STATUS':<12} {'OWNER'}")
    click.echo("-" * 60)
    for p in projects:
        click.echo(f"{p.get('id', ''):<6} "
                   f"{p.get('name', ''):<25} "
                   f"{p.get('status', ''):<12} "
                   f"{p.get('ownerUsername', '')}")


# ---------------------------------------------------------------------------
# cdk invoke
# ---------------------------------------------------------------------------

@cli.command()
@click.argument("identifier")
def invoke(identifier: str):
    """
    Pokrece projekat na serveru. Argument moze biti id (broj) ili ime
    projekta - ako je ime, prvo se trazi u listi sa servera.
    """
    project_id = None

    # Ako je argument broj, koristi ga direktno kao id.
    if identifier.isdigit():
        project_id = int(identifier)
    else:
        # Inace povuci listu i nadji po imenu.
        try:
            response = requests.get(
                f"{get_server_url()}/api/projects",
                headers=get_auth_headers(),
                timeout=10,
            )
        except requests.RequestException as e:
            click.echo(f"Greska u komunikaciji sa serverom: {e}", err=True)
            raise click.Abort()

        handle_auth_error(response)

        if response.status_code != 200:
            click.echo(f"Greska {response.status_code}: {response.text}", err=True)
            raise click.Abort()

        matches = [p for p in response.json() if p.get("name") == identifier]
        if not matches:
            click.echo(f"Projekat '{identifier}' nije pronadjen na serveru.",
                       err=True)
            raise click.Abort()
        if len(matches) > 1:
            click.echo(f"Vise projekata sa imenom '{identifier}'. "
                       f"Koristi id umesto imena:", err=True)
            for p in matches:
                click.echo(f"  cdk invoke {p['id']}  # owner: {p.get('ownerUsername')}",
                           err=True)
            raise click.Abort()
        project_id = matches[0]["id"]

    try:
        response = requests.post(
            f"{get_server_url()}/api/projects/{project_id}/execute",
            headers=get_auth_headers(),
            timeout=30,
        )
    except requests.RequestException as e:
        click.echo(f"Greska u komunikaciji sa serverom: {e}", err=True)
        raise click.Abort()

    handle_auth_error(response)

    if response.status_code != 200:
        click.echo(f"Greska {response.status_code}: {response.text}", err=True)
        raise click.Abort()

    # /execute vraca plain text (stdout, 'Timeout' ili 'Error: ...').
    click.echo(response.text)


if __name__ == "__main__":
    cli()