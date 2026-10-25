"""Offline trust-boundary tests; never use Git credentials or HTTP."""
import contextlib
import copy
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import sys
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
    def __init__(self, plan, *, main=TARGET, published=False, incomplete=False, corrupt_upload=False):
        self.plan = plan
        self.main = main
        self.tag_exists = published
        self.corrupt_upload = corrupt_upload
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
        return {"id": 7, "tag_name": self.plan["tag"], "target_commitish": TARGET,
                "upload_url": f"https://uploads.github.com/repos/{publisher.REPO}/releases/7/assets{{?name,label}}",
                "html_url": f"{publisher.WEB}/releases/tag/{self.plan['tag']}", **values}

    def asset(self, local):
        return {"name": local["name"], "state": "uploaded", "size": local["size"],
                "digest": "sha256:" + local["sha256"],
                "browser_download_url": f"{publisher.WEB}/releases/download/{self.plan['tag']}/{local['name']}"}

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
            return self.release_response() if self.release else self.response(404, {})
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


class PreviewPublisherTest(unittest.TestCase):
    def setUp(self):
        # A future regression must fail the offline test instead of starting a
        # real request or credential helper while these fixtures are evaluated.
        for patcher in (mock.patch("socket.socket", side_effect=AssertionError("Offline test: no network")),
                        mock.patch("socket.create_connection", side_effect=AssertionError("Offline test: no connection")),
                        mock.patch.object(publisher.subprocess, "run", side_effect=AssertionError("Offline test: no process"))):
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
        fake_httpx = SimpleNamespace(Client=lambda **_: remote, Timeout=lambda *_, **__: None,
                                     Limits=lambda **values: values)
        real_validate = publisher.validate_remote_asset

        def validate(asset, local, version):
            real_validate(asset, local, version)
            remote.events.append(("validated", local["name"]))

        with mock.patch.dict(sys.modules, {"httpx": fake_httpx}), \
                mock.patch.dict(publisher.os.environ, {}, clear=False), \
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
        self.assertEqual(["curl.exe", "-q"], argv[:2])
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


if __name__ == "__main__":
    unittest.main()
