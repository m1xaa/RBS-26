import json
import os
import tarfile
import uuid
from pathlib import Path

from flask import Flask, jsonify, request
from flask_cors import CORS

app = Flask(__name__)
CORS(app)

BASE_DIR = Path(__file__).resolve().parent
UPLOAD_DIR = BASE_DIR / "uploads"
CREDENTIALS_FILE = BASE_DIR / "data" / "credentials.json"
FLAG = "CVE-2007-4559{tarslip_path_traversal_pwned}"

UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
CREDENTIALS_FILE.parent.mkdir(parents=True, exist_ok=True)


def load_credentials():
    with CREDENTIALS_FILE.open(encoding="utf-8") as handle:
        return json.load(handle)


@app.get("/api/health")
def health():
    import sys

    return jsonify({"status": "ok", "python": sys.version})


@app.post("/api/submit")
def submit_archive():
    """Prihvata .tar / .tar.gz arhivu i raspakuje je u uploads/<uuid>/."""
    uploaded = request.files.get("archive")
    if uploaded is None or not uploaded.filename:
        return jsonify({"error": "Polje 'archive' je obavezno."}), 400

    session_id = str(uuid.uuid4())
    extraction_dir = UPLOAD_DIR / session_id
    extraction_dir.mkdir(parents=True, exist_ok=True)

    archive_path = extraction_dir / "upload.tar"
    uploaded.save(archive_path)

    entry_names = []
    extracted_files = []
    error = None

    try:
        with tarfile.open(archive_path) as tar:
            entry_names = tar.getnames()
            # CVE-2007-4559: extractall veruje imenima iz arhive bez validacije putanje.
            tar.extractall(extraction_dir)
    except Exception as exc:
        error = str(exc)
    finally:
        archive_path.unlink(missing_ok=True)

    for root, _, files in os.walk(extraction_dir):
        for name in files:
            extracted_files.append(str(Path(root) / name))

    response = {
        "message": "Arhiva je obradjena.",
        "session_id": session_id,
        "extraction_dir": str(extraction_dir),
        "entry_names": entry_names,
        "extracted_files": extracted_files,
    }
    if error:
        response["error"] = error
        return jsonify(response), 422

    return jsonify(response)


@app.post("/api/admin")
def admin_panel():
    """Zasticeni endpoint — proverava kredencijale iz data/credentials.json."""
    payload = request.get_json(silent=True) or {}
    username = payload.get("username", "")
    password = payload.get("password", "")

    try:
        credentials = load_credentials()
    except (OSError, json.JSONDecodeError):
        return jsonify({"error": "Kredencijali nisu dostupni."}), 500

    if username == credentials.get("username") and password == credentials.get("password"):
        return jsonify(
            {
                "message": f"Dobrodosli, {username}! Imate administratorski pristup.",
                "flag": FLAG,
            }
        )

    return jsonify({"error": "Pogresni kredencijali."}), 401


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8000, debug=True)
