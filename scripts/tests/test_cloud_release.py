"""Release trust boundaries. APK strings are fixtures, not signed Android output."""
import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
from unittest import mock


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).resolve().parents[1] / path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


release = module('release_app', 'cloud/release-app.py')
publisher = module('publisher_cloud', 'publish_android_preview.py')
BADGING = "package: name='com.runmoni.app' versionCode='7' versionName='0.1.6-dev' platformBuildVersionName='16'\n"
SIGNATURE = 'Signer #1 certificate SHA-256 digest: ' + release.CERTIFICATE + '\n'


class ReleaseBoundaries(unittest.TestCase):
    def test_release_requires_retained_certificate_and_non_debuggable_package(self):
        self.assertEqual(7, release.inspect_apk(BADGING, SIGNATURE)['versionCode'])
        for badging, signature in [
            (BADGING, ''),
            (BADGING, SIGNATURE.replace(release.CERTIFICATE, 'a' * 64)),
            (BADGING, SIGNATURE + SIGNATURE.replace('#1', '#2')),
            (BADGING + 'application-debuggable\n', SIGNATURE),
            (BADGING.replace('com.runmoni.app', 'com.runmoni.avatarreview'), SIGNATURE),
            (BADGING.replace("versionCode='7'", "versionCode='2'"), SIGNATURE),
        ]:
            with self.subTest(badging=badging, signature=signature), self.assertRaises(release.ReleaseError):
                release.inspect_apk(badging, signature)

    def test_unsigned_does_not_bypass_debug_or_package_checks(self):
        self.assertEqual(7, release.inspect_apk(BADGING, '', unsigned=True)['versionCode'])
        with self.assertRaises(release.ReleaseError):
            release.inspect_apk(BADGING + 'application-debuggable\n', '', unsigned=True)

    def test_existing_release_bytes_are_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'artifact'
            release.preserve(path, b'original')
            release.preserve(path, b'original')
            with self.assertRaises(release.ReleaseError):
                release.preserve(path, b'different')
            self.assertEqual(b'original', path.read_bytes())

    def test_dirty_source_is_rejected_before_revision_is_accepted(self):
        with mock.patch.object(release, 'checked', return_value=' M app/build.gradle.kts') as run:
            with self.assertRaises(release.ReleaseError):
                release.source_revision({})
            self.assertEqual(1, run.call_count)

    def test_inherited_java_and_credential_traces_are_removed(self):
        with mock.patch.dict(os.environ, {'JAVA_TOOL_OPTIONS': 'fixture', 'GIT_TRACE': '1',
                                         'SSLKEYLOGFILE': 'fixture', 'RUNMONI_STORE_PASSWORD': 'fixture'}, clear=True):
            self.assertEqual({'RUNMONI_STORE_PASSWORD': 'fixture'}, release.clean_env())

    def test_injected_github_auth_does_not_invoke_interactive_credentials(self):
        token = 'ghs_' + 'x' * 32
        with mock.patch.dict(os.environ, {'GH_TOKEN': token}, clear=True), \
                mock.patch.object(publisher.subprocess, 'run') as run:
            self.assertEqual(token, publisher.github_credential())
            run.assert_not_called()

    def test_managed_proxy_placeholder_is_not_rejected_as_a_short_token(self):
        with mock.patch.dict(os.environ, {'GH_TOKEN': 'proxy-placeholder'}, clear=True), \
                mock.patch.object(publisher.subprocess, 'run') as run:
            self.assertEqual('proxy-placeholder', publisher.github_credential())
            run.assert_not_called()

    def test_invalid_injected_github_auth_does_not_fall_back_or_leak(self):
        with mock.patch.dict(os.environ, {'GITHUB_TOKEN': 'fixture\nheader'}, clear=True), \
                mock.patch.object(publisher.subprocess, 'run') as run:
            with self.assertRaises(publisher.PublicationError) as error:
                publisher.github_credential()
            self.assertNotIn('fixture', str(error.exception))
            run.assert_not_called()

    def test_curl_platform_selection_keeps_windows_compatibility(self):
        with mock.patch.object(publisher.os, 'name', 'nt'):
            self.assertEqual('curl.exe', publisher.native_curl())
        with mock.patch.object(publisher.os, 'name', 'posix'):
            self.assertEqual('curl', publisher.native_curl())


if __name__ == '__main__':
    unittest.main()
