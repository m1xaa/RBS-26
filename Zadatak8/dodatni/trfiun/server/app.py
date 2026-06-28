import json
import tarfile
import uuid
from pathlib import Path

from flask import Flask, jsonify, request
from flask_cors import CORS

BASE_DIR = Path(__file__).resolve().parent
SETTINGS_PATH = BASE_DIR / "settings.json"
UPLOADS_DIR = BASE_DIR / "uploads"

app = Flask(__name__)
CORS(app, origins=["http://localhost:4200"])


def load_settings() -> dict:
    with SETTINGS_PATH.open(encoding="utf-8") as handle:
        return json.load(handle)


def read_welcome_file() -> str:
    settings = load_settings()
    welcome_path = BASE_DIR / settings["welcome_file"]
    return welcome_path.read_text(encoding="utf-8")


def extract_submission(archive_path: Path, destination: Path) -> None:
    """Ranjivo: extractall ne proverava path traversal u imenima članova arhive."""
    destination.mkdir(parents=True, exist_ok=True)
    with tarfile.open(archive_path) as archive:
        archive.extractall(path=destination)


@app.get("/api/health")
def health():
    return jsonify({"status": "ok"})


@app.get("/api/welcome")
def welcome():
    try:
        message = read_welcome_file()
    except (OSError, KeyError, json.JSONDecodeError) as exc:
        return jsonify({"error": str(exc)}), 500
    return jsonify({"message": message})


@app.post("/api/submit")
def submit():
    uploaded = request.files.get("archive")
    if uploaded is None or uploaded.filename == "":
        return jsonify({"error": "Polje 'archive' je obavezno."}), 400

    if not uploaded.filename.lower().endswith((".tar", ".tar.gz", ".tgz")):
        return jsonify({"error": "Dozvoljene su samo .tar, .tar.gz i .tgz arhive."}), 400

    submission_id = str(uuid.uuid4())
    submission_dir = UPLOADS_DIR / submission_id
    submission_dir.mkdir(parents=True, exist_ok=True)

    archive_path = submission_dir / "upload.tar"
    uploaded.save(archive_path)

    extract_dir = submission_dir / "extracted"
    try:
        extract_submission(archive_path, extract_dir)
    except tarfile.TarError as exc:
        return jsonify({"error": f"Neispravna tar arhiva: {exc}"}), 400

    extracted_files = [
        str(path.relative_to(extract_dir))
        for path in extract_dir.rglob("*")
        if path.is_file()
    ]

    return jsonify(
        {
            "submission_id": submission_id,
            "message": "Arhiva je uspešno primljena i raspakovana.",
            "extracted_to": str(extract_dir.relative_to(BASE_DIR)),
            "files": extracted_files,
        }
    )


if __name__ == "__main__":
    UPLOADS_DIR.mkdir(exist_ok=True)
    app.run(host="0.0.0.0", port=5000, debug=True)
