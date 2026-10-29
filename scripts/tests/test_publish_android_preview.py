"""Offline trust-boundary tests; never use Git credentials or HTTP."""
import contextlib
import copy
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock

SPEC = importlib.util.spec_from_file_location("publisher", Path(__file__).resolve().parents[1] / "publish_android_preview.py")
publisher = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(publisher)
VERSION = "0.1.5-dev"
TARGET = "a" * 40
PAYLOAD = b"offline-apk-fixture-not-a-real-signature"
DIGEST = hashlib.sha256(PAYLOAD).hexdigest()


def metadata():
    return {"version": VERSION, "versionCode": 6, "fileName": f"runmoni-{VERSION}.apk",
            "packageName": "com.runmoni.app", "sizeBytes": len(PAYLOAD), "sha256": DIGEST,
            "signed": True, "certificateSha256": publisher.CERTIFICATE,
            "sourceRevision": TARGET, "sourceDirty": False}


class FakeGitHub:
    """Small in-memory server; a mutation records facts rather than enforcing the gate."""
    def __init__(self, plan, *, main=TARGET, published=False, incomplete=False, corrupt_upload=False, draft_urls=False):
        self.plan = plan
        self.main = main
        self.tag_exists = published
        self.corrupt_upload = corrupt_upload
        self.draft_urls = draft_urls
        self.calls = []
        self.events = []
        self.uploads = []
        self.publication_snapshots = []
        self.assets = {}
        self.release = None
        if published:
            self.release = self.release_payload({"draft": False, "prerelease": True})
            wanted = plan["assets"][:-1] if incomplete else plan["assets"]
            self.assets = {item["name"]: self.asset(item) for item in wanted}

    def __enter__(self):
        return self

    def __exit__(self, *_):
        return False

    def release_payload(self, values):
        url_tag = 'untagged-0123456789abcdef0123' if self.draft_urls and values.get('draft') else self.plan['tag']
        return {"id": 7, "tag_name": self.plan["tag"], "target_commitish": TARGET,
                "upload_url": f"https://uploads.github.com/repos/{publisher.REPO}/releases/7/assets{{?name,label}}",
                "html_url": f"{publisher.WEB}/releases/tag/{url_tag}", **values}

    def asset(self, local):
        url_tag = ('untagged-0123456789abcdef0123'
                   if self.draft_urls and self.release and self.release['draft'] else self.plan['tag'])
        return {"name": local["name"], "state": "uploaded", "size": local["size"],
                "digest": "sha256:" + local["sha256"],
                "browser_download_url": f"{publisher.WEB}/releases/download/{url_tag}/{local['name']}"}

    def response(self, status, data):
        return SimpleNamespace(status_code=status, json=lambda: copy.deepcopy(data))

    def release_response(self):
        return self.response(200, {**self.release, "assets": list(self.assets.values())})

    def request(self, method, url, **options):
        if not url.startswith(publisher.API):
            raise AssertionError("Fake transport received an unexpected destination")
        path = url[len(publisher.API):]
        self.calls.append((method, path, copy.deepcopy(options)))
        self.events.append(("request", method, path))
        if method == "GET" and path == "":
            return self.response(200, {"full_name": publisher.REPO, "permissions": {"push": True}})
        if method == "GET" and path == "/git/ref/heads/main":
            return self.response(200, {"object": {"sha": self.main}})
        if method == "GET" and path == f"/git/ref/tags/{self.plan['tag']}":
            return self.response(200, {"object": {"type": "commit", "sha": TARGET}}) if self.tag_exists else self.response(404, {})
        if method == "GET" and path == f"/releases/tags/{self.plan['tag']}":
            return self.release_response() if self.release and not self.release['draft'] else self.response(404, {})
        if method == 'GET' and publisher.release_list_path(path):
            return self.response(200, ([{**self.release, 'assets': list(self.assets.values())}]
                                      if self.release and path.endswith('page=1') else []))
        if method == "POST" and path == "/releases":
            self.release = self.release_payload(options["json"])
            return self.response(201, {**self.release, "assets": []})
        if method == "GET" and path == "/releases/7" and self.release:
            return self.release_response()
        if method == "PATCH" and path == "/releases/7" and self.release:
            if options["json"].get("draft") is False:
                # Deliberately accept any state; the test, not this fake server,
                # must catch publication before three fresh validations.
                self.publication_snapshots.append({"assets": copy.deepcopy(self.assets),
                                                   "events": list(self.events)})
                self.tag_exists = True
                if self.draft_urls:
                    self.release['html_url'] = f"{publisher.WEB}/releases/tag/{self.plan['tag']}"
                    for name, asset in self.assets.items():
                        asset['browser_download_url'] = f"{publisher.WEB}/releases/download/{self.plan['tag']}/{name}"
            self.release.update(options["json"])
            return self.release_response()
        raise AssertionError(f"Unexpected fake HTTP operation: {method} {path}")

    def upload(self, _url, local, _token, _rate):
        self.uploads.append(local["name"])
        self.events.append(("upload", local["name"]))
        asset = self.asset(local)
        if self.corrupt_upload:
            asset["digest"] = "sha256:" + "f" * 64
        self.assets[local["name"]] = copy.deepcopy(asset)
        return asset


class CapturedStdin(io.BytesIO):
    def close(self):
        if not self.closed:
            self.saved = self.getvalue()
        super().close()


class BoundedStdout(io.BytesIO):
    def __init__(self, payload):
        super().__init__(payload)
        self.read_sizes = []
        self.bytes_read = 0

    def read(self, size=-1):
        self.read_sizes.append(size)
        value = super().read(size)
        self.bytes_read += len(value)
        return value


class FakeCurlProcess:
    def __init__(self, payload, code=0):
        self.stdin = CapturedStdin()
        self.stdout = BoundedStdout(payload)
        self.exit_code = code
        self.returncode = None
        self.killed = False

    def wait(self, timeout=None):
        if self.returncode is None:
            self.returncode = self.exit_code
        return self.returncode

    def poll(self):
        return self.returncode

    def kill(self):
        self.killed = True
        self.returncode = -9


def decode_curl_config_string(value):
    """Test decoder for curl's documented double-quoted escape layer, not JSON."""
    if len(value) < 2 or value[0] != '"' or value[-1] != '"':
        raise AssertionError("Expected one quoted curl config value")
    result = []
    index = 1
    escapes = {"\\": "\\", '"': '"', "n": "\n", "r": "\r", "t": "\t", "v": "\v"}
    while index < len(value) - 1:
        char = value[index]
        if char == "\\":
            index += 1
            if index >= len(value) - 1 or value[index] not in escapes:
                raise AssertionError("Unknown curl config escape")
            char = escapes[value[index]]
        elif char == '"':
            raise AssertionError("Unescaped quote inside curl config value")
        result.append(char)
        index += 1
    return "".join(result)


class PreviewPublisherTest(unittest.TestCase):
    def setUp(self):
        # A future regression must fail the offline test instead of starting a
        # real request or credential helper while these fixtures are evaluated.
        for patcher in (mock.patch("socket.socket", side_effect=AssertionError("Offline test: no network")),
                        mock.patch("socket.create_connection", side_effect=AssertionError("Offline test: no connection")),
                        mock.patch.object(publisher.subprocess, "run", side_effect=AssertionError("Offline test: no process")),
                        mock.patch.object(publisher.subprocess, "Popen", side_effect=AssertionError("Offline test: no child process"))):
            patcher.start()
            self.addCleanup(patcher.stop)

    def fixture(self, root, value=None):
        output = root / "outputs"
        output.mkdir()
        (root / "docs").mkdir()
        (output / f"runmoni-{VERSION}.apk").write_bytes(PAYLOAD)
        (output / f"SHA256SUMS-{VERSION}.txt").write_text(f"{DIGEST}  runmoni-{VERSION}.apk\n", encoding="utf-8")
        (output / f"runmoni-{VERSION}.metadata.json").write_text(json.dumps(value or metadata()), encoding="utf-8")
        (root / "docs" / f"RELEASE_NOTES_{VERSION}.md").write_text("Offline test fixture only.\n", encoding="utf-8")

    @contextlib.contextmanager
    def transport(self, root, remote):
        real_validate = publisher.validate_remote_asset

        def validate(asset, local, version, **kwargs):
            real_validate(asset, local, version, **kwargs)
            remote.events.append(("validated", local["name"]))

        def request(method, path, _token, *, json_body=None):
            options = {"json": json_body} if json_body is not None else {}
            response = remote.request(method, publisher.API + path, **options)
            return response.status_code, response.json()

        with mock.patch.object(publisher, "github_api_request", side_effect=request), \
                mock.patch.object(publisher, "ROOT", root), \
                mock.patch.object(publisher, "github_credential", return_value="gho_" + "x" * 32), \
                mock.patch.object(publisher, "upload_asset", side_effect=remote.upload), \
                mock.patch.object(publisher, "validate_remote_asset", side_effect=validate), \
                contextlib.redirect_stdout(io.StringIO()):
            yield

    def test_default_plan_cannot_retrieve_credentials_or_publish(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            with mock.patch.object(publisher, "github_credential", side_effect=AssertionError("No credential use")) as credential, \
                    mock.patch.object(publisher, "publish", side_effect=AssertionError("No publication")) as publish, \
                    contextlib.redirect_stdout(io.StringIO()):
                # The real local planner and CLI dispatch share the same
                # credential/process/socket blockers, including future changes.
                plan = publisher.prepare_plan(VERSION, TARGET, root=root)
                with mock.patch.object(publisher, "prepare_plan", return_value=plan):
                    self.assertEqual(0, publisher.main(["--version", VERSION, "--target", TARGET]))
            credential.assert_not_called()
            publish.assert_not_called()
            self.assertTrue(plan["localMetadataReady"])
            self.assertFalse(plan["actualApkSignatureCheckedByThisScript"])
            self.assertFalse(plan["physicalDeviceSmokeTestCheckedByThisScript"])

    def test_version_traversal_stable_versions_and_abbreviated_commits_are_rejected(self):
        for version, target in (("../0.1.5-dev", TARGET), ("0.1.5", TARGET), (VERSION, "abcdef0")):
            with self.subTest(version=version), self.assertRaises(publisher.PublicationError):
                publisher.validate_inputs(version, target, 256)

    def test_byte_mismatch_package_signer_code_and_untrusted_urls_are_rejected(self):
        corruptions = (("sha256", "b" * 64), ("sizeBytes", len(PAYLOAD) + 1),
                       ("packageName", "com.other.app"), ("certificateSha256", "c" * 64),
                       ("signed", False), ("versionCode", True),
                       ("downloadUrl", "https://example.invalid/untrusted.apk"))
        for key, value in corruptions:
            candidate = {**metadata(), key: value}
            with self.subTest(field=key), self.assertRaises(publisher.PublicationError):
                publisher.validate_metadata(candidate, VERSION, len(PAYLOAD), DIGEST)

    def test_checksum_filename_must_match_not_only_the_hash(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            (root / "outputs" / f"SHA256SUMS-{VERSION}.txt").write_text(f"{DIGEST}  another.apk\n", encoding="utf-8")
            with self.assertRaises(publisher.PublicationError):
                publisher.prepare_plan(VERSION, TARGET, root=root)

    def test_dirty_preservation_metadata_has_blockers_without_authentication(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root, {**metadata(), "sourceDirty": True, "sourceRevision": "d" * 40,
                                "publiclyReleased": False, "validatedSourceCommit": TARGET})
            plan = publisher.prepare_plan(VERSION, TARGET, root=root)
            self.assertFalse(plan["localMetadataReady"])
            self.assertEqual(3, len(plan["blockers"]))
            with mock.patch.object(publisher, "github_credential") as credential, self.assertRaises(publisher.PublicationError):
                publisher.publish(plan)
            credential.assert_not_called()

    def test_missing_release_notes_are_a_plan_blocker(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            (root / "docs" / f"RELEASE_NOTES_{VERSION}.md").unlink()
            self.assertFalse(publisher.prepare_plan(VERSION, TARGET, root=root)["localMetadataReady"])

    def test_remote_asset_reuse_requires_exact_digest_size_and_pinned_url(self):
        local = {"name": f"runmoni-{VERSION}.apk", "size": len(PAYLOAD), "sha256": DIGEST}
        remote = {"name": local["name"], "size": local["size"], "digest": "sha256:" + DIGEST,
                  "state": "uploaded", "browser_download_url": f"{publisher.WEB}/releases/download/v{VERSION}/{local['name']}"}
        publisher.validate_remote_asset(remote, local, VERSION)
        for key, value in (("digest", "sha256:" + "e" * 64), ("size", 0), ("state", "starter"),
                           ("browser_download_url", "https://example.invalid/apk")):
            with self.subTest(field=key), self.assertRaises(publisher.PublicationError):
                publisher.validate_remote_asset({**remote, key: value}, local, VERSION)

    def test_zero_or_unbounded_upload_rates_are_rejected(self):
        for rate in (0, 31, 1025, True):
            with self.subTest(rate=rate), self.assertRaises(publisher.PublicationError):
                publisher.validate_inputs(VERSION, TARGET, rate)

    def test_credential_header_injection_is_rejected_before_subprocess(self):
        for suffix in ("\n", "\r", '"', "\\"):
            with self.subTest(character=repr(suffix)), mock.patch.object(publisher.subprocess, "run") as run:
                with self.assertRaises(publisher.PublicationError):
                    publisher.upload_asset("unused", {}, "x" * 30 + suffix, 256)
                run.assert_not_called()

    def test_curl_ignores_rc_and_uses_stdin_without_trace_environment(self):
        token = "gho_" + "x" * 32
        result = mock.Mock(returncode=0, stdout='{"state":"uploaded"}')
        local = {"name": "fixture.apk", "path": "fixture.apk", "size": 100}
        with mock.patch.dict(publisher.os.environ, {"GIT_TRACE": "1", "GCM_TRACE_SECRETS": "1", "SSLKEYLOGFILE": "not-used"}), \
                mock.patch.object(publisher.subprocess, "run", return_value=result) as run:
            publisher.upload_asset("https://uploads.github.com/repos/wjdals988/runmoni/releases/1/assets", local, token, 256)
        argv = run.call_args.args[0]
        options = run.call_args.kwargs
        self.assertEqual([publisher.native_curl(), "-q"], argv[:2])
        self.assertNotIn(token, " ".join(argv))
        self.assertIn("Authorization: Bearer " + token, options["input"])
        self.assertEqual("256K", argv[argv.index("--limit-rate") + 1])
        self.assertNotIn("GIT_TRACE", options["env"])
        self.assertNotIn("GCM_TRACE_SECRETS", options["env"])
        self.assertNotIn("SSLKEYLOGFILE", options["env"])

    def test_publish_makes_draft_public_once_only_after_three_fresh_asset_validations(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            plan = publisher.prepare_plan(VERSION, TARGET, root=root)
            remote = FakeGitHub(plan)
            with self.transport(root, remote):
                result = publisher.publish(plan)
            wanted = {item["name"] for item in plan["assets"]}
            self.assertEqual([item["name"] for item in plan["assets"]], remote.uploads)
            self.assertEqual(1, len(remote.publication_snapshots))
            self.assertEqual(1, sum(method == "PATCH" for method, _, _ in remote.calls))
            snapshot = remote.publication_snapshots[0]
            self.assertEqual(wanted, set(snapshot["assets"]))
            for local in plan["assets"]:
                self.assertEqual("sha256:" + local["sha256"], snapshot["assets"][local["name"]]["digest"])
                self.assertEqual(local["size"], snapshot["assets"][local["name"]]["size"])
            events = snapshot["events"]
            last_upload = max(index for index, item in enumerate(events) if item[0] == "upload")
            last_refresh = max(index for index, item in enumerate(events)
                               if item == ("request", "GET", "/releases/7"))
            self.assertGreater(last_refresh, last_upload)
            self.assertEqual(wanted, {item[1] for item in events[last_refresh + 1:] if item[0] == "validated"})
            created = [options["json"] for method, path, options in remote.calls if (method, path) == ("POST", "/releases")]
            self.assertEqual(1, len(created))
            self.assertIs(created[0]["draft"], True)
            self.assertFalse(result["draft"])
            self.assertTrue((root / "work" / f"published-release-{VERSION}.json").is_file())

    def test_publish_stops_on_upload_digest_mismatch_without_public_patch_or_deletion(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            plan = publisher.prepare_plan(VERSION, TARGET, root=root)
            remote = FakeGitHub(plan, corrupt_upload=True)
            with self.transport(root, remote), self.assertRaises(publisher.PublicationError):
                publisher.publish(plan)
            self.assertEqual(1, len(remote.uploads))
            self.assertEqual([], remote.publication_snapshots)
            self.assertFalse(any(method in ("PATCH", "DELETE") for method, _, _ in remote.calls))
            self.assertTrue(remote.release["draft"])
            self.assertFalse((root / "work" / f"published-release-{VERSION}.json").exists())

    def test_publish_stops_on_remote_main_mismatch_before_release_creation_or_upload(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            plan = publisher.prepare_plan(VERSION, TARGET, root=root)
            remote = FakeGitHub(plan, main="b" * 40)
            with self.transport(root, remote), self.assertRaises(publisher.PublicationError):
                publisher.publish(plan)
            self.assertEqual([], remote.uploads)
            self.assertFalse(any(method != "GET" for method, _, _ in remote.calls))
            self.assertIsNone(remote.release)
            self.assertFalse((root / "work" / f"published-release-{VERSION}.json").exists())

    def test_publish_does_not_repair_incomplete_existing_public_release(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            plan = publisher.prepare_plan(VERSION, TARGET, root=root)
            remote = FakeGitHub(plan, published=True, incomplete=True)
            original = copy.deepcopy(remote.assets)
            with self.transport(root, remote), self.assertRaises(publisher.PublicationError):
                publisher.publish(plan)
            self.assertEqual([], remote.uploads)
            self.assertEqual([], remote.publication_snapshots)
            self.assertFalse(any(method != "GET" for method, _, _ in remote.calls))
            self.assertEqual(original, remote.assets)
            self.assertFalse(remote.release["draft"])
            self.assertFalse((root / "work" / f"published-release-{VERSION}.json").exists())

    def test_stage_draft_cli_verifies_three_fresh_assets_without_public_patch_and_reuses_retry(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            plan = publisher.prepare_plan(VERSION, TARGET, root=root)
            remote = FakeGitHub(plan)
            with self.transport(root, remote), mock.patch.object(publisher, "prepare_plan", return_value=plan):
                argv = ["--version", VERSION, "--target", TARGET, "--stage-draft"]
                self.assertEqual(0, publisher.main(argv))
                self.assertEqual(0, publisher.main(argv))
            wanted = {item["name"] for item in plan["assets"]}
            self.assertEqual([item["name"] for item in plan["assets"]], remote.uploads)
            self.assertEqual(1, sum(method == "POST" for method, _, _ in remote.calls))
            self.assertFalse(any(method in ("PATCH", "DELETE") for method, _, _ in remote.calls))
            self.assertEqual([], remote.publication_snapshots)
            self.assertTrue(remote.release["draft"])
            self.assertFalse(remote.tag_exists)
            last_upload = max(index for index, item in enumerate(remote.events) if item[0] == "upload")
            last_refresh = max(index for index, item in enumerate(remote.events)
                               if item == ("request", "GET", "/releases/7"))
            self.assertGreater(last_refresh, last_upload)
            self.assertEqual(wanted, {item[1] for item in remote.events[last_refresh + 1:] if item[0] == "validated"})
            receipt = json.loads((root / "work" / f"staged-release-{VERSION}.json").read_text(encoding="utf-8"))
            self.assertEqual("stage-draft", receipt["mode"])
            self.assertTrue(receipt["draft"])
            self.assertFalse(receipt["publicReleaseConfirmed"])
            self.assertEqual(wanted, {item["name"] for item in receipt["assets"]})
            self.assertFalse(receipt["dashboardChanged"])
            self.assertFalse(receipt["updateFeedChanged"])
            self.assertNotIn("url", receipt)
            self.assertFalse((root / "work" / f"published-release-{VERSION}.json").exists())

    def test_actions_token_without_user_permission_hint_can_stage_verified_assets(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            plan = publisher.prepare_plan(VERSION, TARGET, root=root)
            remote = FakeGitHub(plan)
            original = remote.request
            def request(method, url, **options):
                response = original(method, url, **options)
                if method == 'GET' and url == publisher.API:
                    return remote.response(200, {'full_name': publisher.REPO})
                return response
            with self.transport(root, remote), mock.patch.object(remote, 'request', side_effect=request):
                result = publisher.publish(plan, stage_draft=True)
            self.assertTrue(result['draft'])
            self.assertEqual(3, len(remote.uploads))
            self.assertEqual([], remote.publication_snapshots)

    def test_draft_assets_use_only_the_matching_temporary_url(self):
        local = {'name': f'runmoni-{VERSION}.apk', 'size': len(PAYLOAD), 'sha256': DIGEST}
        release = {'draft': True, 'html_url': f'{publisher.WEB}/releases/tag/untagged-0123456789abcdef0123'}
        asset = {'name': local['name'], 'state': 'uploaded', 'size': len(PAYLOAD),
                 'digest': 'sha256:' + DIGEST,
                 'browser_download_url': f"{publisher.WEB}/releases/download/untagged-0123456789abcdef0123/{local['name']}"}
        publisher.validate_remote_asset(asset, local, VERSION, release=release)
        for bad in (None, {**release, 'draft': False},
                    {**release, 'html_url': release['html_url'].replace('runmoni', 'other')},
                    {**release, 'html_url': release['html_url'].replace('0123456789abcdef0123', 'aaaaaaaaaaaaaaaaaaaa')}):
            with self.subTest(release=bad), self.assertRaises(publisher.PublicationError):
                publisher.validate_remote_asset(asset, local, VERSION, release=bad)

    def test_duplicate_or_incompletely_inspected_drafts_are_never_created(self):
        for duplicates in (True, False):
            with self.subTest(duplicates=duplicates), tempfile.TemporaryDirectory() as directory:
                root = Path(directory); self.fixture(root)
                plan = publisher.prepare_plan(VERSION, TARGET, root=root)
                remote = FakeGitHub(plan)
                original = remote.request
                def request(method, url, **options):
                    if publisher.release_list_path(url[len(publisher.API):]):
                        data = ([{'id': 7, 'tag_name': plan['tag']}, {'id': 8, 'tag_name': plan['tag']}]
                                if duplicates else [{'tag_name': 'unrelated'}] * 100)
                        return remote.response(200, data)
                    return original(method, url, **options)
                with self.transport(root, remote), mock.patch.object(remote, 'request', side_effect=request):
                    with self.assertRaises(publisher.PublicationError):
                        publisher.publish(plan, stage_draft=True)
                self.assertIsNone(remote.release)
                self.assertEqual([], remote.uploads)

    def test_realistic_temporary_draft_urls_are_verified_without_publication(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); self.fixture(root)
            plan = publisher.prepare_plan(VERSION, TARGET, root=root)
            remote = FakeGitHub(plan, draft_urls=True)
            with self.transport(root, remote):
                result = publisher.publish(plan, stage_draft=True)
            self.assertTrue(result['draft'])
            self.assertEqual(3, len(remote.uploads))
            self.assertEqual([], remote.publication_snapshots)
            self.assertTrue(all('/untagged-' in asset['browser_download_url'] for asset in result['assets']))

    def test_publishing_rechecks_canonical_urls_after_temporary_draft_urls(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); self.fixture(root)
            plan = publisher.prepare_plan(VERSION, TARGET, root=root)
            remote = FakeGitHub(plan, draft_urls=True)
            with self.transport(root, remote):
                result = publisher.publish(plan)
            self.assertFalse(result['draft'])
            self.assertEqual(1, len(remote.publication_snapshots))
            self.assertTrue(all(f"/download/v{VERSION}/" in asset['browser_download_url'] for asset in result['assets']))

    def test_authoritative_write_denial_stops_without_upload_or_success_receipt(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            plan = publisher.prepare_plan(VERSION, TARGET, root=root)
            remote = FakeGitHub(plan)
            original = remote.request
            def request(method, url, **options):
                if method == 'POST':
                    return remote.response(403, {})
                response = original(method, url, **options)
                if method == 'GET' and url == publisher.API:
                    return remote.response(200, {'full_name': publisher.REPO, 'permissions': {'push': False}})
                return response
            with self.transport(root, remote), mock.patch.object(remote, 'request', side_effect=request):
                with self.assertRaisesRegex(publisher.PublicationError, 'GitHub API returned 403'):
                    publisher.publish(plan, stage_draft=True)
            self.assertEqual([], remote.uploads)
            self.assertIsNone(remote.release)
            self.assertFalse((root / 'work' / f'staged-release-{VERSION}.json').exists())

    def test_wrong_repository_identity_stops_before_release_mutation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            plan = publisher.prepare_plan(VERSION, TARGET, root=root)
            remote = FakeGitHub(plan)
            with self.transport(root, remote), mock.patch.object(remote, 'request', return_value=
                    remote.response(200, {'full_name': 'example/other', 'permissions': {'push': True}})):
                with self.assertRaisesRegex(publisher.PublicationError, 'repository identity'):
                    publisher.publish(plan, stage_draft=True)
            self.assertIsNone(remote.release)
            self.assertEqual([], remote.uploads)

    def test_stage_draft_rejects_digest_changed_in_fresh_server_response(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            plan = publisher.prepare_plan(VERSION, TARGET, root=root)
            remote = FakeGitHub(plan)
            normal_request = remote.request

            def corrupt_fresh_response(method, url, **options):
                response = normal_request(method, url, **options)
                if (method, url) == ("GET", publisher.API + "/releases/7"):
                    data = response.json()
                    data["assets"][-1]["digest"] = "sha256:" + "b" * 64
                    return remote.response(200, data)
                return response

            with self.transport(root, remote), \
                    mock.patch.object(remote, "request", side_effect=corrupt_fresh_response), \
                    self.assertRaises(publisher.PublicationError):
                publisher.publish(plan, stage_draft=True)
            self.assertEqual(3, len(remote.uploads))
            self.assertFalse(any(method in ("PATCH", "DELETE") for method, _, _ in remote.calls))
            self.assertTrue(remote.release["draft"])
            self.assertFalse((root / "work" / f"staged-release-{VERSION}.json").exists())
            self.assertFalse((root / "work" / f"published-release-{VERSION}.json").exists())

    def test_stage_draft_rejects_existing_public_release_without_any_mutation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            plan = publisher.prepare_plan(VERSION, TARGET, root=root)
            remote = FakeGitHub(plan, published=True)
            original_release = copy.deepcopy(remote.release)
            original_assets = copy.deepcopy(remote.assets)
            with self.transport(root, remote), self.assertRaisesRegex(
                    publisher.PublicationError, "Draft staging cannot change an already published release"):
                publisher.publish(plan, stage_draft=True)
            self.assertEqual([], remote.uploads)
            self.assertFalse(any(method != "GET" for method, _, _ in remote.calls))
            self.assertEqual(original_release, remote.release)
            self.assertEqual(original_assets, remote.assets)
            self.assertFalse((root / "work" / f"staged-release-{VERSION}.json").exists())
            self.assertFalse((root / "work" / f"published-release-{VERSION}.json").exists())

    def test_publish_reuses_staged_assets_without_second_upload_and_preserves_draft_receipt(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            plan = publisher.prepare_plan(VERSION, TARGET, root=root)
            remote = FakeGitHub(plan)
            with self.transport(root, remote):
                staged = publisher.publish(plan, stage_draft=True)
                self.assertTrue(staged["draft"])
                self.assertEqual([], remote.publication_snapshots)
                self.assertEqual(3, len(remote.uploads))
                published = publisher.publish(plan)
            self.assertEqual(3, len(remote.uploads))
            self.assertEqual(1, sum(method == "POST" for method, _, _ in remote.calls))
            self.assertEqual(1, sum(method == "PATCH" for method, _, _ in remote.calls))
            self.assertFalse(any(method == "DELETE" for method, _, _ in remote.calls))
            self.assertFalse(published["draft"])
            self.assertEqual(f"{publisher.WEB}/releases/tag/{plan['tag']}", published["url"])
            self.assertTrue((root / "work" / f"published-release-{VERSION}.json").is_file())
            original = json.loads((root / "work" / f"staged-release-{VERSION}.json").read_text(encoding="utf-8"))
            self.assertTrue(original["draft"])
            self.assertFalse(original["publicReleaseConfirmed"])

    def test_native_api_json_config_roundtrip_preserves_unicode_and_escapes_without_argv_secrets(self):
        payload = {"name": "런모니 🏃", "body": '첫 줄\n다음 줄\r\n"quote"',
                   "literal": r"\n", "path": r"C:\runner\new", "tab": "left\tright"}
        token = "gho_" + "x" * 32
        process = FakeCurlProcess(b'{"id":7}' + publisher.HTTP_STATUS_MARKER + b"201")
        with mock.patch.object(publisher.subprocess, "Popen", return_value=process) as popen:
            status, response = publisher.github_api_request("POST", "/releases", token, json_body=payload)
        self.assertEqual((201, {"id": 7}), (status, response))
        argv = popen.call_args.args[0]
        options = popen.call_args.kwargs
        config = process.stdin.saved.decode("ascii")
        data_line = next(line for line in config.splitlines() if line.startswith("data-binary = "))
        encoded = data_line.split(" = ", 1)[1]
        serialized = decode_curl_config_string(encoded)
        self.assertEqual(payload, json.loads(serialized))
        self.assertEqual(json.dumps(json.dumps(payload, ensure_ascii=True, separators=(",", ":")), ensure_ascii=True), encoded)
        self.assertNotIn("런모니", config)
        self.assertNotIn(token, " ".join(argv))
        self.assertNotIn("data-binary", " ".join(argv))
        self.assertEqual([publisher.native_curl(), "-q"], argv[:2])
        self.assertIn("--http1.1", argv)
        self.assertEqual("30", argv[argv.index("--connect-timeout") + 1])
        self.assertEqual("120", argv[argv.index("--max-time") + 1])
        self.assertEqual(str(publisher.MAX_API_RESPONSE_BYTES), argv[argv.index("--max-filesize") + 1])
        self.assertEqual("0", argv[argv.index("--max-redirs") + 1])
        self.assertNotIn("--location", argv)
        self.assertEqual(publisher.subprocess.DEVNULL, options["stderr"])
        popen.assert_called_once()

    def test_native_api_rejects_unbounded_paths_and_methods_before_a_process(self):
        cases = (("GET", "https://example.invalid/steal", None), ("GET", "/../other", None),
                 ("GET", "/git/tags/" + "a" * 300, None),
                 ("GET", "/releases/7?redirect=elsewhere", None), ("GET", "/git/ref/tags/v0.1.5", None),
                 ("DELETE", "/releases/7", None), ("POST", "/releases/7", {}),
                 ("PATCH", "/releases/7", None), ("GET", "", {"body": True}))
        with mock.patch.object(publisher.subprocess, "Popen") as popen:
            for method, path, body in cases:
                with self.subTest(method=method, path=path), self.assertRaises(publisher.PublicationError):
                    publisher.github_api_request(method, path, "gho_" + "x" * 32, json_body=body)
            popen.assert_not_called()

    def test_only_bounded_release_listing_accepts_array_responses(self):
        path = '/releases?per_page=100&page=1'
        payload = [{'id': 7, 'tag_name': 'v' + VERSION}]
        process = FakeCurlProcess(json.dumps(payload).encode() + publisher.HTTP_STATUS_MARKER + b'200')
        with mock.patch.object(publisher.subprocess, 'Popen', return_value=process):
            self.assertEqual((200, payload), publisher.github_api_request('GET', path, 'fixture-token'))
        for bad in (path.replace('100', '101'), path.replace('page=1', 'page=11'), path + '&redirect=elsewhere'):
            with self.subTest(path=bad), self.assertRaises(publisher.PublicationError):
                publisher.validate_api_path('GET', bad, None)
        for body in ({'id': 7}, [None], [{}] * 101):
            with self.subTest(shape=type(body).__name__), self.assertRaises(publisher.PublicationError):
                publisher.parse_api_output(json.dumps(body).encode() + publisher.HTTP_STATUS_MARKER + b'200',
                                           allow_release_list=True)

    def test_native_api_response_read_is_bounded_and_oversize_child_is_killed(self):
        raw = b"x" * (publisher.MAX_API_RESPONSE_BYTES + 1) + publisher.HTTP_STATUS_MARKER + b"200"
        process = FakeCurlProcess(raw)
        with mock.patch.object(publisher.subprocess, "Popen", return_value=process), self.assertRaisesRegex(
                publisher.PublicationError, "exceeded 1 MiB"):
            publisher.github_api_request("GET", "", "gho_" + "x" * 32)
        bound = publisher.MAX_API_RESPONSE_BYTES + len(publisher.HTTP_STATUS_MARKER) + 3 + 1
        self.assertEqual([bound], process.stdout.read_sizes)
        self.assertLessEqual(process.stdout.bytes_read, bound)
        self.assertTrue(process.killed)
        self.assertIsNotNone(process.returncode)

    def test_native_api_curl_failure_and_malformed_json_never_expose_raw_data(self):
        secret = b"private-response-and-secret-marker"
        cases = ((22, secret + publisher.HTTP_STATUS_MARKER + b"403"),
                 (0, secret + publisher.HTTP_STATUS_MARKER + b"200"),
                 (0, secret), (0, b"[]" + publisher.HTTP_STATUS_MARKER + b"200"))
        for code, raw in cases:
            process = FakeCurlProcess(raw, code=code)
            with self.subTest(code=code, raw_bytes=len(raw)), \
                    mock.patch.object(publisher.subprocess, "Popen", return_value=process), \
                    self.assertRaises(publisher.PublicationError) as caught:
                publisher.github_api_request("GET", "", "gho_" + "x" * 32)
            self.assertNotIn(secret.decode("ascii"), str(caught.exception))
            self.assertNotIn("gho_", str(caught.exception))

    def test_publish_rejects_redirect_status_without_echoing_metadata_body(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            plan = publisher.prepare_plan(VERSION, TARGET, root=root)
            remote = FakeGitHub(plan)
            with self.transport(root, remote), \
                    mock.patch.object(publisher, "github_api_request", return_value=(302, {"message": "private-body"})), \
                    self.assertRaisesRegex(publisher.PublicationError, "GitHub API returned 302") as caught:
                publisher.publish(plan, stage_draft=True)
            self.assertNotIn("private-body", str(caught.exception))
            self.assertEqual([], remote.uploads)
            self.assertEqual([], remote.publication_snapshots)


if __name__ == "__main__":
    unittest.main()
