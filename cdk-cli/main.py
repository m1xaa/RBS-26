"""
Oblak CDK CLI - klijent za upload fajlova na Oblak platformu.

Server endpoint-i:
    POST /auth/register     - {username, password} -> AuthResponse
    POST /auth/login        - {username, password} -> AuthResponse
    POST /auth/refresh      - {refreshToken} -> AuthResponse
    POST /auth/logout       - {refreshToken} -> 200
    POST /api/upload        - multipart/form-data sa poljem 'file' -> UploadResponse
    POST /api/execute/{id}  - pokrecu projekat -> ExecutionResult

AuthResponse:    {accessToken, refreshToken, username, accessExpirationMs}
UploadResponse:  {projectId, executeUrl}
ExecutionResult: {exitCode, stdout, stderr, programStdout, programStderr, logPath}

Komande:
    cdk register - kreiranje novog naloga
    cdk login    - prijava na server
    cdk logout   - brisanje sesije
    cdk whoami   - prikaz trenutnog korisnika
    cdk upload   - upload fajla
    cdk run      - pokretanje uploadovanog projekta
    cdk config   - prikaz/podesavanje server URL-a
"""

import click
import requests
import json
import os
from pathlib import Path
from getpass import getpass


CONFIG_DIR = Path.home() / ".cdk"
CONFIG_FILE = CONFIG_DIR / "config.json"

DEFAULT_SERVER_URL = os.environ.get("OBLAK_SERVER_URL", "http://localhost:8080")


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


def save_session(server_url: str, data: dict) -> None:
    """Cuva tokene iz AuthResponse u config."""
    save_config({
        "server_url": server_url,
        "username": data.get("username"),
        "access_token": data["accessToken"],
        "refresh_token": data["refreshToken"],
        "access_expiration_ms": data.get("accessExpirationMs"),
    })



def show_password_validation_error(response: requests.Response) -> None:
    """Prikazuje listu pravila koje lozinka nije ispunila."""
    try:
        data = response.json()
        click.echo("Lozinka ne ispunjava bezbednosne zahteve:", err=True)
        for err in data.get("details", []):
            click.echo(f"  - {err}", err=True)
    except ValueError:
        click.echo(f"Greska: {response.text}", err=True)


def try_refresh_token() -> bool:
    """
    Pokusava da osvezi access token koristeci refresh token.
    Vraca True ako je uspeo, False ako je refresh token istekao/nevazeci.
    """
    cfg = load_config()
    refresh_token = cfg.get("refresh_token")
    if not refresh_token:
        return False

    try:
        response = requests.post(
            f"{get_server_url()}/auth/refresh",
            json={"refreshToken": refresh_token},
            timeout=10,
        )
    except requests.RequestException:
        return False

    if response.status_code != 200:
        return False

    save_session(cfg.get("server_url", DEFAULT_SERVER_URL), response.json())
    return True


def authenticated_request(method: str, url: str, **kwargs) -> requests.Response:
    """
    Salje autentifikovan zahtev. Ako vrati 401, pokusava refresh i ponavlja.
    Ako i refresh ne uspe, prekida komandu sa porukom za login.
    """
    cfg = load_config()
    token = cfg.get("access_token")
    if not token:
        click.echo("Niste ulogovani. Pokrenite: cdk login", err=True)
        raise click.Abort()

    headers = kwargs.pop("headers", {})
    headers["Authorization"] = f"Bearer {token}"

    response = requests.request(method, url, headers=headers, **kwargs)

    if response.status_code == 401:
        if try_refresh_token():
            new_token = load_config().get("access_token")
            headers["Authorization"] = f"Bearer {new_token}"

            # Ako saljemo fajl, ne mozemo da ponovimo (stream je iscrpljen)
            if "files" in kwargs:
                click.echo("Sesija je obnovljena. Pokusajte ponovo.", err=True)
                raise click.Abort()

            response = requests.request(method, url, headers=headers, **kwargs)
        else:
            click.echo("Sesija je istekla. Pokrenite: cdk login", err=True)
            raise click.Abort()

    if response.status_code == 403:
        click.echo("Nemate dozvolu za ovu operaciju.", err=True)
        raise click.Abort()

    return response


@click.group()
@click.version_option(version="0.4.0", prog_name="cdk")
def cli():
    """Oblak CDK CLI - upload fajlova na Oblak platformu."""
    pass


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


@cli.command()
@click.option("--server", default=None, help="URL Oblak servera")
@click.option("--username", "-u", default=None, help="Korisnicko ime")
def register(server: str, username: str):
    """Kreira novi nalog na serveru i automatski prijavljuje."""
    server_url = server or get_server_url()

    if not username:
        username = click.prompt("Korisnicko ime")

    password = getpass("Lozinka: ")
    password_confirm = getpass("Ponovite lozinku: ")
    if password != password_confirm:
        click.echo("Lozinke se ne podudaraju.", err=True)
        raise click.Abort()

    try:
        response = requests.post(
            f"{server_url}/auth/register",
            json={"username": username, "password": password},
            timeout=10,
        )
    except requests.RequestException as e:
        click.echo(f"Greska u komunikaciji sa serverom: {e}", err=True)
        raise click.Abort()

    if response.status_code == 400:
        show_password_validation_error(response)
        raise click.Abort()
    if response.status_code == 403:
        click.echo("Korisnicko ime je vec zauzeto.", err=True)
        raise click.Abort()
    if response.status_code != 200:
        click.echo(f"Greska {response.status_code}: {response.text}", err=True)
        raise click.Abort()

    data = response.json()
    save_session(server_url, data)
    click.echo(f"Nalog '{username}' uspesno kreiran i prijavljen na {server_url}.")


@cli.command()
@click.option("--server", default=None, help="URL Oblak servera")
@click.option("--username", "-u", default=None, help="Korisnicko ime")
def login(server: str, username: str):
    """Prijava na Oblak server."""
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
    save_session(server_url, data)
    click.echo(f"Uspesno ulogovani kao '{username}' na {server_url}")


@cli.command()
def logout():
    """Brise sesiju lokalno i revoke-uje refresh token na serveru."""
    cfg = load_config()
    refresh_token = cfg.get("refresh_token")

    if refresh_token:
        try:
            requests.post(
                f"{get_server_url()}/auth/logout",
                json={"refreshToken": refresh_token},
                timeout=10,
            )
        except requests.RequestException:
            # Best-effort - ako server nije dostupan, brisemo lokalno
            pass

    save_config({"server_url": cfg.get("server_url", DEFAULT_SERVER_URL)})
    click.echo("Uspesno izlogovani.")



@cli.command()
def whoami():
    """Prikazuje trenutno ulogovanog korisnika."""
    cfg = load_config()
    username = cfg.get("username")
    if not username or not cfg.get("access_token"):
        click.echo("Niste ulogovani.")
        return
    click.echo(f"Ulogovani kao: {username}")
    click.echo(f"Server:        {cfg.get('server_url')}")


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
            response = authenticated_request(
                "POST",
                f"{get_server_url()}/api/upload",
                files={"file": (path.name, f)},
                timeout=60,
            )
    except requests.RequestException as e:
        click.echo(f"Greska u komunikaciji sa serverom: {e}", err=True)
        raise click.Abort()

    if response.status_code not in (200, 201):
        click.echo(f"Greska {response.status_code}: {response.text}", err=True)
        raise click.Abort()

    try:
        data = response.json()
        project_id = data["projectId"]
        execute_url = data.get("executeUrl", f"/api/execute/{project_id}")
    except (ValueError, KeyError):
        click.echo("Server nije vratio ocekivani odgovor.", err=True)
        raise click.Abort()

    click.echo(f"Fajl '{path.name}' uspesno uploadovan.")
    click.echo(f"  Project ID:    {project_id}")
    click.echo(f"  Pokretanje:    cdk run {project_id}")
    click.echo(f"  Execute URL:   {get_server_url()}{execute_url}")


@cli.command()
@click.argument("project_id")
def run(project_id: str):
    """
    Pokrece prethodno uploadovan projekat na serveru.

    Primer:
        cdk run 3ca8aa31-a907-4073-b1a2-27b349ece1f1
    """
    click.echo(f"Pokrecem projekat {project_id}...")

    try:
        response = authenticated_request(
            "POST",
            f"{get_server_url()}/api/execute/{project_id}",
            timeout=300,
        )
    except requests.RequestException as e:
        click.echo(f"Greska u komunikaciji sa serverom: {e}", err=True)
        raise click.Abort()

    if response.status_code == 404:
        click.echo("Projekat nije pronadjen ili nemate dozvolu za pokretanje.", err=True)
        raise click.Abort()
    if response.status_code != 200:
        click.echo(f"Greska {response.status_code}: {response.text}", err=True)
        raise click.Abort()

    try:
        result = response.json()
    except ValueError:
        click.echo(f"Server vratio nevalidan odgovor: {response.text}", err=True)
        raise click.Abort()

    exit_code = result.get("exitCode", -1)
    program_stdout = result.get("programStdout", "")
    program_stderr = result.get("programStderr", "")

    if program_stdout:
        click.echo("--- stdout ---")
        click.echo(program_stdout)
    if program_stderr:
        click.echo("--- stderr ---", err=True)
        click.echo(program_stderr, err=True)

    click.echo(f"--- exit code: {exit_code} ---")
    if exit_code != 0:
        raise click.exceptions.Exit(code=1)


if __name__ == "__main__":
    cli()