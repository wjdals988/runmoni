"""Plan locally by default; explicitly stage or publish one pinned Runmoni prerelease.

Adapted from the successful work/publish-release.py workflow. Metadata checks do
not replace the separate apksigner/aapt verification of the actual APK.
"""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import urlencode

ROOT = Path(__file__).resolve().parents[1]
REPO = "wjdals988/runmoni"
WEB = f"https://github.com/{REPO}"
API = f"https://api.github.com/repos/{REPO}"
API_VERSION = "2026-03-10"  # Retain the version used by the existing uploader.
CERTIFICATE = "2d7f724cd20b1ff2263eb34e8e02d5fec9db674488b10b813dd239838b975830"
MAX_APK_BYTES = 512 * 1024 * 1024
MAX_API_RESPONSE_BYTES = 1024 * 1024
HTTP_STATUS_MARKER = b"RUNMONI_API_STATUS:"
PREVIEW = re.compile(r"[0-9]+\.[0-9]+\.[0-9]+-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*")
SHA256 = re.compile(r"[0-9a-fA-F]{64}")
COMMIT = re.compile(r"[0-9a-f]{40}")


class PublicationError(Exception):
    """Only fixed, non-secret diagnostic messages reach the command output."""


def validate_inputs(version, target, rate_kib):
    if not isinstance(version, str) or len(version) > 64 or not PREVIEW.fullmatch(version):
        raise PublicationError("A development preview version is required; path fragments are not allowed.")
    if not isinstance(target, str) or not COMMIT.fullmatch(target):
        raise PublicationError("Target must be a full lowercase 40-character commit SHA.")
    if type(rate_kib) is not int or not 32 <= rate_kib <= 1024:
        raise PublicationError("Upload rate must be between 32 and 1024 KiB/s.")


def file_digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def validate_metadata(metadata, version, actual_size, actual_digest):
    """Validate declarations against local bytes, not the APK's internal signer."""
    if not isinstance(metadata, dict):
        raise PublicationError("APK metadata must be a JSON object.")
    expected_name = f"runmoni-{version}.apk"
    if metadata.get("version") != version or metadata.get("fileName") != expected_name:
        raise PublicationError("Metadata version or APK filename differs from the requested version.")
    if metadata.get("packageName") != "com.runmoni.app":
        raise PublicationError("Unexpected Android package.")
    if type(metadata.get("versionCode")) is not int or not 1 <= metadata["versionCode"] <= 2_100_000_000:
        raise PublicationError("Invalid Android version code.")
    if type(metadata.get("sizeBytes")) is not int or not 1 <= actual_size <= MAX_APK_BYTES or metadata["sizeBytes"] != actual_size:
        raise PublicationError("APK size differs from metadata or exceeds the supported limit.")
    declared = metadata.get("sha256")
    if not isinstance(declared, str) or not SHA256.fullmatch(declared) or declared.lower() != actual_digest:
        raise PublicationError("APK digest differs from metadata.")
    certificate = metadata.get("certificateSha256")
    if metadata.get("signed") is not True or not isinstance(certificate, str) or certificate.lower() != CERTIFICATE:
        raise PublicationError("Release certificate declaration does not match the retained signer.")
    for key, expected in (
        ("downloadUrl", f"{WEB}/releases/download/v{version}/{expected_name}"),
        ("releaseUrl", f"{WEB}/releases/tag/v{version}"),
    ):
        if key in metadata and metadata[key] != expected:
            raise PublicationError("Metadata contains an unexpected publication URL.")


def prepare_plan(version, target, rate_kib=256, root=ROOT):
    """No Git invocation, credential lookup, HTTP client or external mutation."""
    validate_inputs(version, target, rate_kib)
    output = (root / "outputs").resolve()
    names = (f"runmoni-{version}.apk", f"SHA256SUMS-{version}.txt", f"runmoni-{version}.metadata.json")
    paths = [output / name for name in names]
    for path in paths:
        if not path.is_file() or path.resolve().parent != output:
            raise PublicationError("A canonical local release file is missing or points outside outputs.")
    if paths[1].stat().st_size > 4096 or paths[2].stat().st_size > 65536:
        raise PublicationError("Release sidecar is unexpectedly large.")
    apk_size = paths[0].stat().st_size
    if not 1 <= apk_size <= MAX_APK_BYTES:
        raise PublicationError("Local APK size is outside the supported limit.")
    metadata = json.loads(paths[2].read_text(encoding="utf-8-sig"))
    digest = file_digest(paths[0])
    validate_metadata(metadata, version, apk_size, digest)
    checksum_lines = [line.strip() for line in paths[1].read_text(encoding="utf-8-sig").splitlines() if line.strip()]
    if len(checksum_lines) != 1 or checksum_lines[0].split() != [digest, names[0]]:
        raise PublicationError("Checksum sidecar must contain exactly this APK digest and filename.")
    blockers = []
    if metadata.get("sourceDirty") is not False:
        blockers.append("The final build must explicitly record sourceDirty=false.")
    if metadata.get("sourceRevision") != target:
        blockers.append("The build sourceRevision does not equal the requested target commit.")
    if any(key in metadata for key in ("publiclyReleased", "sourcePublicationConfirmed", "validatedSourceCommit")):
        blockers.append("Remove mutable publication-state fields from the final sidecar; do not flip them to true.")
    notes = root / "docs" / f"RELEASE_NOTES_{version}.md"
    if not notes.is_file():
        blockers.append("Version-specific release notes are missing.")
    elif notes.resolve().parent != (root / "docs").resolve() or notes.stat().st_size > 65536:
        blockers.append("Release notes must be a bounded local document in docs.")
    assets = [{"name": path.name, "path": str(path), "size": path.stat().st_size,
               "sha256": digest if index == 0 else file_digest(path)} for index, path in enumerate(paths)]
    return {"repository": REPO, "version": version, "versionCode": metadata["versionCode"],
            "tag": f"v{version}", "targetCommit": target, "rateKiBPerSecond": rate_kib,
            "estimatedUploadSecondsAtLimit": math.ceil(sum(item["size"] for item in assets) / (rate_kib * 1024)),
            "assets": assets, "releaseNotesPath": str(notes), "localMetadataReady": not blockers,
            "blockers": blockers, "actualApkSignatureCheckedByThisScript": False,
            "physicalDeviceSmokeTestCheckedByThisScript": False,
            "planNetworkRequests": 0, "planCredentialLookups": 0}


def validate_remote_asset(asset, local, version):
    if (asset.get("name") != local["name"] or asset.get("state") != "uploaded"
            or asset.get("size") != local["size"] or asset.get("digest") != "sha256:" + local["sha256"]
            or asset.get("browser_download_url") != f"{WEB}/releases/download/v{version}/{local['name']}"):
        raise PublicationError("Remote asset differs from local bytes; no asset was overwritten or deleted.")


def child_process_env():
    # Process-local only. Do not let an inherited trace option record credentials.
    return {name: value for name, value in os.environ.items()
            if not name.upper().startswith(("GIT_TRACE", "GCM_TRACE"))
            and name.upper() not in ("GIT_CURL_VERBOSE", "SSLKEYLOGFILE")}


def validate_credential(token):
    # Also excludes CR/LF, quotes and backslashes from curl's stdin config.
    # Managed environments may inject a short proxy placeholder instead of a
    # raw GitHub token. Enforce header/config safety, not an assumed token shape.
    if not isinstance(token, str) or not re.fullmatch(r"[A-Za-z0-9_.:/+=-]{1,4096}", token):
        raise PublicationError("GitHub credential format is invalid; credential output was omitted.")
    return token


def github_credential():
    # Reuse injected CLI/Actions authentication; never prompt or persist a token.
    for name in ("GH_TOKEN", "GITHUB_TOKEN"):
        if os.environ.get(name):
            return validate_credential(os.environ[name])
    env = {**child_process_env(), "GIT_TERMINAL_PROMPT": "0", "GCM_INTERACTIVE": "never"}
    result = subprocess.run(
        ["git", "-c", f"safe.directory={ROOT.as_posix()}", "credential", "fill"],
        input="protocol=https\nhost=github.com\nusername=wjdals988\n\n", capture_output=True,
        text=True, env=env, cwd=ROOT, timeout=30,
    )
    fields = dict(line.split("=", 1) for line in result.stdout.splitlines() if "=" in line)
    token = fields.get("password", "")
    if result.returncode or not token:
        raise PublicationError("Non-interactive GitHub authentication is required; credential output was omitted.")
    return validate_credential(token)


def native_curl():
    return "curl.exe" if os.name == "nt" else "curl"


def curl_config_quote(value):
    """One curl-config double-quoted value; preserve JSON's own escape layer."""
    return '"' + (value.replace("\\", "\\\\").replace('"', '\\"')
                  .replace("\r", "\\r").replace("\n", "\\n")
                  .replace("\t", "\\t").replace("\v", "\\v")) + '"'


def validate_api_path(method, path, json_body):
    if not isinstance(path, str) or len(path) > 256:
        raise PublicationError("Unexpected GitHub metadata API path.")
    release_id = re.fullmatch(r"/releases/[1-9][0-9]{0,19}", path) is not None
    tag = None
    for prefix in ("/git/ref/tags/v", "/releases/tags/v"):
        if path.startswith(prefix):
            tag = path[len(prefix):]
            break
    tag_path = tag is not None and len(tag) <= 64 and PREVIEW.fullmatch(tag) is not None
    allowed = ((method == "GET" and (path in ("", "/git/ref/heads/main") or release_id or tag_path
                                     or re.fullmatch(r"/git/tags/[0-9a-f]{40}", path)))
               or (method == "POST" and path == "/releases")
               or (method == "PATCH" and release_id))
    if not allowed or (method == "GET" and json_body is not None):
        raise PublicationError("Unexpected GitHub metadata API operation.")
    if method in ("POST", "PATCH") and not isinstance(json_body, dict):
        raise PublicationError("GitHub metadata mutation requires a JSON object.")


def parse_api_output(raw):
    if not isinstance(raw, bytes):
        raise PublicationError("Native curl API output was not binary data.")
    body, marker, status = raw.rpartition(HTTP_STATUS_MARKER)
    if not marker or not re.fullmatch(rb"[1-5][0-9]{2}", status):
        raise PublicationError("Native curl API status was not confirmed.")
    if len(body) > MAX_API_RESPONSE_BYTES:
        raise PublicationError("GitHub metadata response exceeded 1 MiB.")
    try:
        payload = json.loads(body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError, RecursionError):
        raise PublicationError("GitHub metadata response was not valid bounded JSON.") from None
    if not isinstance(payload, dict):
        raise PublicationError("GitHub metadata response must be a JSON object.")
    return int(status), payload


def github_api_request(method, path, token, *, json_body=None):
    """One bounded native-curl metadata request; no redirects or HTTP package."""
    validate_api_path(method, path, json_body)
    validate_credential(token)
    lines = [f"header = {curl_config_quote('Authorization: Bearer ' + token)}",
             'header = "Accept: application/vnd.github+json"',
             f'header = "X-GitHub-Api-Version: {API_VERSION}"',
             'header = "User-Agent: Runmoni-preview-publisher"']
    if json_body is not None:
        # ASCII JSON avoids the Windows locale encoding. Curl then unquotes only
        # its config layer, leaving JSON's \n/\u/quote/backslash escapes intact.
        try:
            serialized = json.dumps(json_body, ensure_ascii=True, allow_nan=False, separators=(",", ":"))
        except (TypeError, ValueError, RecursionError):
            raise PublicationError("GitHub metadata body could not be serialized as ASCII JSON.") from None
        lines += ['header = "Content-Type: application/json"',
                  "data-binary = " + json.dumps(serialized, ensure_ascii=True)]
    config = ("\n".join(lines) + "\n").encode("ascii")
    arguments = [native_curl(), "-q", "--silent", "--show-error", "--http1.1", "--proto", "=https",
                 "--connect-timeout", "30", "--max-time", "120", "--max-filesize", str(MAX_API_RESPONSE_BYTES),
                 "--max-redirs", "0", "--request", method, "--config", "-",
                 "--write-out", HTTP_STATUS_MARKER.decode("ascii") + "%{http_code}", API + path]
    process = None
    try:
        process = subprocess.Popen(arguments, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                   stderr=subprocess.DEVNULL, env=child_process_env())
        process.stdin.write(config)
        process.stdin.close()
        # Python enforces the body bound even if curl cannot reject a chunked
        # response using Content-Length. Never accumulate arbitrary stdout.
        bound = MAX_API_RESPONSE_BYTES + len(HTTP_STATUS_MARKER) + 3
        raw = process.stdout.read(bound + 1)
        if len(raw) > bound:
            raise PublicationError("GitHub metadata response exceeded 1 MiB.")
        code = process.wait(timeout=125)
        if code:
            raise PublicationError(f"Native curl API request failed (curl {code}); raw details omitted.")
        return parse_api_output(raw)
    except subprocess.TimeoutExpired:
        raise PublicationError("Native curl API request exceeded its completion bound; raw details omitted.") from None
    except (OSError, BrokenPipeError):
        raise PublicationError("Native curl API transport failed; raw details omitted.") from None
    finally:
        if process is not None:
            if process.poll() is None:
                process.kill()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    pass
            for stream in (process.stdin, process.stdout):
                if stream is not None:
                    try:
                        stream.close()
                    except OSError:
                        pass


class CurlApiAdapter:
    """Sequential requests use completed curl processes, so no idle API socket remains."""
    def __init__(self, token):
        self._token = validate_credential(token)

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self._token = ""
        return False

    def request(self, method, path, **options):
        if set(options) - {"json"}:
            raise PublicationError("Unexpected GitHub API request option.")
        return github_api_request(method, path, self._token, json_body=options.get("json"))


def upload_asset(upload_url, local, token, rate_kib):
    # Authorization travels only through curl's stdin, not argv, a file or logs.
    validate_credential(token)
    print(json.dumps({"phase": "uploading_asset", "asset": local["name"],
                      "bytes": local["size"], "rateKiBPerSecond": rate_kib}),
          file=sys.stderr, flush=True)
    config = (f'header = "Authorization: Bearer {token}"\n'
              'header = "Accept: application/vnd.github+json"\n'
              f'header = "X-GitHub-Api-Version: {API_VERSION}"\n')
    seconds = max(120, math.ceil(local["size"] / (rate_kib * 1024)) * 2 + 120)
    result = subprocess.run([
        native_curl(), "-q", "--silent", "--show-error", "--fail-with-body", "--http1.1", "--proto", "=https",
        "--connect-timeout", "30", "--max-time", str(seconds), "--limit-rate", f"{rate_kib}K",
        "--request", "POST", "--header", "Content-Type: application/octet-stream",
        "--upload-file", local["path"], "--config", "-", upload_url + "?" + urlencode({"name": local["name"]}),
    ], input=config, text=True, capture_output=True, timeout=seconds + 30, env=child_process_env())
    if result.returncode:
        raise PublicationError(f"Asset upload failed (curl {result.returncode}); no automatic retry or raw output.")
    return json.loads(result.stdout)


def publish(plan, *, stage_draft=False):
    if not plan["localMetadataReady"]:
        raise PublicationError("Local plan has publication blockers; no credential or network was used.")
    token = github_credential()
    with CurlApiAdapter(token) as client:
        def api(method, path, optional=False, **kwargs):
            status, payload = client.request(method, path, **kwargs)
            if optional and status == 404:
                return None
            if not 200 <= status < 300:
                raise PublicationError(f"GitHub API returned {status}; raw response omitted.")
            return payload

        def check_target(require_tag=False):
            if api("GET", "/git/ref/heads/main")["object"]["sha"] != plan["targetCommit"]:
                raise PublicationError("Remote main changed; no force operation was performed.")
            tag = api("GET", f"/git/ref/tags/{plan['tag']}", optional=not require_tag)
            if tag is None:
                return
            obj = tag["object"]
            for _ in range(5):
                if obj.get("type") != "tag":
                    break
                if not COMMIT.fullmatch(obj.get("sha", "")):
                    raise PublicationError("Invalid remote annotated tag.")
                obj = api("GET", f"/git/tags/{obj['sha']}")["object"]
            if obj.get("type") != "commit" or obj.get("sha") != plan["targetCommit"]:
                raise PublicationError("Existing tag target differs; no tag was changed.")

        repo = api("GET", "")
        if repo.get("full_name") != REPO or repo.get("permissions", {}).get("push") is not True:
            raise PublicationError("Expected Runmoni repository write permission.")
        check_target()
        release = api("GET", f"/releases/tags/{plan['tag']}", optional=True)
        notes = Path(plan["releaseNotesPath"]).read_text(encoding="utf-8-sig")
        apk = plan["assets"][0]
        body = notes + f"\n\nAPK SHA-256: `{apk['sha256']}`\nAPK bytes: {apk['size']}\nSource: `{plan['targetCommit']}`\n"
        release_name = f"런모니 {plan['version']} — 서명된 Android 개발 프리뷰"
        if release is None:
            release = api("POST", "/releases", json={"tag_name": plan["tag"], "target_commitish": plan["targetCommit"],
                          "name": release_name, "body": body,
                          "draft": True, "prerelease": True, "make_latest": "false"})
        def verify_release_identity(candidate):
            identifier = candidate.get("id")
            if (type(identifier) is not int or identifier < 1 or candidate.get("tag_name") != plan["tag"]
                    or candidate.get("prerelease") is not True or type(candidate.get("draft")) is not bool
                    or (candidate["draft"] and candidate.get("target_commitish") != plan["targetCommit"])):
                raise PublicationError("Existing release does not match the requested development preview.")
            if stage_draft and candidate["draft"] is False:
                raise PublicationError("Draft staging cannot change an already published release.")
            candidate_body = candidate.get("body")
            if candidate["draft"] and (candidate.get("name") != release_name or not isinstance(candidate_body, str)
                                       or candidate_body.replace("\r\n", "\n") != body.replace("\r\n", "\n")):
                raise PublicationError("Draft notes or title differ; no unreviewed draft was made public.")
            return identifier

        def record_result(candidate, remote_assets):
            result = {"repository": REPO, "version": plan["version"], "tag": plan["tag"],
                      "targetCommit": plan["targetCommit"], "draft": candidate["draft"], "prerelease": True,
                      "assets": [{key: remote_assets[item["name"]][key] for key in
                      ("name", "size", "digest", "browser_download_url")} for item in plan["assets"]],
                      "apkBodyDownloaded": False, "updateFeedChanged": False, "dashboardChanged": False}
            if stage_draft:
                result.update({"mode": "stage-draft", "releaseId": candidate["id"],
                               "apiUrl": f"{API}/releases/{candidate['id']}", "publicReleaseConfirmed": False})
                prefix = "staged-release"
            else:
                result["url"] = candidate["html_url"]
                prefix = "published-release"
            receipt = ROOT / "work" / f"{prefix}-{plan['version']}.json"
            receipt.parent.mkdir(parents=True, exist_ok=True)
            receipt.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
            return result

        release_id = verify_release_identity(release)
        upload_url = release.get("upload_url", "").split("{", 1)[0]
        if upload_url != f"https://uploads.github.com/repos/{REPO}/releases/{release_id}/assets":
            raise PublicationError("Unexpected upload destination.")
        expected_names = {item["name"] for item in plan["assets"]}
        remote = {item["name"]: item for item in release.get("assets", [])}
        if release["draft"] and set(remote) - expected_names:
            raise PublicationError("Draft has unplanned assets; it was not made public.")
        for local in plan["assets"]:
            if local["name"] in remote:
                asset = remote[local["name"]]
            elif release["draft"]:
                print(json.dumps({"uploading": local["name"], "bytes": local["size"],
                                  "limitKiBPerSecond": plan["rateKiBPerSecond"]}), flush=True)
                asset = upload_asset(upload_url, local, token, plan["rateKiBPerSecond"])
            else:
                raise PublicationError("Published release is incomplete; no asset was added or replaced.")
            validate_remote_asset(asset, local, plan["version"])
        check_target()
        # Freshly re-read identity and all three server digests for either mode.
        release = api("GET", f"/releases/{release_id}")
        if verify_release_identity(release) != release_id:
            raise PublicationError("Release identity changed during asset verification.")
        remote = {item["name"]: item for item in release.get("assets", [])}
        if release["draft"] and set(remote) != expected_names:
            raise PublicationError("Draft asset set changed; it remains unpublished.")
        for local in plan["assets"]:
            validate_remote_asset(remote.get(local["name"], {}), local, plan["version"])
        if stage_draft:
            # Identity verification above requires draft=true. This branch exits
            # before every public-state mutation, including an idempotent PATCH.
            return record_result(release, remote)
        check_target()
        if release["draft"]:
            release = api("PATCH", f"/releases/{release_id}", json={"draft": False, "prerelease": True})
        release = api("GET", f"/releases/{release_id}")
        if release.get("draft") is not False or release.get("prerelease") is not True or release.get("html_url") != f"{WEB}/releases/tag/{plan['tag']}":
            raise PublicationError("Published preview state was not confirmed.")
        remote = {item["name"]: item for item in release.get("assets", [])}
        for local in plan["assets"]:
            validate_remote_asset(remote.get(local["name"], {}), local, plan["version"])
        check_target(require_tag=True)
        return record_result(release, remote)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True)
    parser.add_argument("--target", required=True, help="Exact source commit expected on remote main and tag")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--plan", action="store_true", help="Default: local files only, no credentials or HTTP")
    mode.add_argument("--stage-draft", action="store_true", help="Upload and verify a draft only; never make it public")
    mode.add_argument("--publish", action="store_true", help="Upload verified assets, then publish the prerelease")
    parser.add_argument("--rate-kib", type=int, default=256)
    args = parser.parse_args(argv)
    try:
        plan = prepare_plan(args.version, args.target, args.rate_kib)
        if args.stage_draft:
            result = publish(plan, stage_draft=True)
        else:
            result = publish(plan) if args.publish else {"mode": "plan", **plan}
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0
    except PublicationError as error:
        print(json.dumps({"ok": False, "error": str(error)}), file=sys.stderr)
    except Exception as error:
        # Do not serialize arbitrary HTTP/subprocess exceptions carrying request data.
        print(json.dumps({"ok": False, "error": f"Operation failed ({type(error).__name__}); raw details omitted."}), file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
