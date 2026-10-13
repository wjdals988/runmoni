"""Export a committed source snapshot; ignored keys/data/APKs can never enter this payload."""
import json
import subprocess
import sys

rows = subprocess.check_output(["git", "ls-tree", "-r", "-z", "HEAD"]).split(b"\0")
files = []
for row in filter(None, rows):
    attributes, path_bytes = row.split(b"\t", 1)
    mode, kind, sha = attributes.decode().split()
    path = path_bytes.decode("utf-8")
    if kind != "blob" or path.startswith((".secrets/", "work/", "outputs/")) or path.endswith((".jks", ".keystore", ".apk")):
        raise ValueError(f"Unexpected tracked item: {path}")
    content = subprocess.check_output(["git", "cat-file", "blob", sha])
    try:
        files.append({"path": path, "mode": mode, "type": "blob", "content": content.decode("utf-8")})
    except UnicodeDecodeError:
        import base64
        files.append({"path": path, "mode": mode, "type": "blob", "base64": base64.b64encode(content).decode("ascii")})
sys.stdout.reconfigure(encoding="utf-8")
print(json.dumps({"local_commit": subprocess.check_output(["git", "rev-parse", "HEAD"]).decode().strip(), "files": files}, ensure_ascii=False))
