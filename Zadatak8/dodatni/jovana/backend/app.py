"""
PixelBlog - Theme Installer
Vulnerable demo application for CVE-2007-4559 (Python tarfile path traversal).

Endpoints:
  GET  /                 -> simple frontend (theme upload form + About viewer)
  GET  /api/health       -> health check
  GET  /api/about        -> reads whatever file config/site_config.json points to
  POST /api/theme/install -> uploads a .tar theme archive and extracts it (VULNERABLE)
"""

import json
import tarfile
import uuid
from pathlib import Path

from flask import Flask, jsonify, request, send_from_directory

BASE_DIR = Path(__file__).resolve().parent
CONFIG_PATH = BASE_DIR / "config" / "site_config.json"
THEMES_DIR = BASE_DIR / "themes"
FRONTEND_DIR = BASE_DIR.parent / "frontend"

app = Flask(__name__, static_folder=str(FRONTEND_DIR), static_url_path="")


def load_config() -> dict:
    with open(CONFIG_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


@app.get("/")
def index():
    return send_from_directory(FRONTEND_DIR, "index.html")


@app.get("/api/health")
def health():
    return jsonify(status="ok")


@app.get("/api/about")
def about():
    config = load_config()
    about_file = BASE_DIR / config["about_page_file"]
    try:
        content = about_file.read_text(encoding="utf-8")
    except OSError as exc:
        return jsonify(error=str(exc)), 404
    return jsonify(about=content)


@app.post("/api/theme/install")
def install_theme():
    if "theme" not in request.files:
        return jsonify(error="no 'theme' file in request"), 400

    upload = request.files["theme"]
    install_id = uuid.uuid4().hex
    install_dir = THEMES_DIR / install_id
    extract_dir = install_dir / "extracted"
    extract_dir.mkdir(parents=True, exist_ok=True)

    archive_path = install_dir / "theme.tar"
    upload.save(archive_path)

    # VULNERABLE: member names from the archive are trusted as-is.
    # A crafted member name such as "../../../config/site_config.json"
    # escapes `extract_dir` entirely (CVE-2007-4559).
    with tarfile.open(archive_path) as archive:
        archive.extractall(path=extract_dir)
        names = archive.getnames()

    return jsonify(theme_id=install_id, installed_files=names)


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False)
