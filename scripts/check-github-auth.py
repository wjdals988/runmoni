"""Probe the configured Git credential helper without logging or persisting credentials."""
import json
import os
import subprocess
from urllib.request import Request, urlopen

env = {**os.environ, "GIT_TERMINAL_PROMPT": "0", "GCM_INTERACTIVE": "never"}
result = subprocess.run(["git", "credential", "fill"], input="protocol=https\nhost=github.com\n\n", text=True, capture_output=True, env=env, timeout=30)
fields = dict(line.split("=", 1) for line in result.stdout.splitlines() if "=" in line)
token = fields.get("password")
if result.returncode or not token:
    print(json.dumps({"authenticated": False, "credentialHelper": "no non-interactive GitHub credential"}))
    raise SystemExit(2)
request = Request("https://api.github.com/repos/wjdals988/runmoni", headers={"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json", "User-Agent": "Runmoni-release"})
with urlopen(request, timeout=20) as response:
    repo = json.load(response)
print(json.dumps({"authenticated": True, "repository": repo["full_name"], "canPush": repo.get("permissions", {}).get("push", False)}))
