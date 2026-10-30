"""Synthetic client settings only; these tests do not sign in to Google."""
import base64
import importlib.util
import json
import hashlib
from pathlib import Path
import tempfile
import unittest
import sys
import contextlib
import io
from unittest import mock

spec = importlib.util.spec_from_file_location('connected', Path(__file__).resolve().parents[1] / 'cloud/prepare-connected-build.py')
connected = importlib.util.module_from_spec(spec)
spec.loader.exec_module(connected)
SHA1 = '1' * 40


def config():
    return {'project_info': {'project_id': 'runmoni-3c08e'}, 'client': [{
        'client_info': {'android_client_info': {'package_name': 'com.runmoni.app'}, 'mobilesdk_app_id': '1:123:android:abc123'},
        'api_key': [{'current_key': 'fixture-public-android-api-key'}],
        'oauth_client': [{'client_type': 3, 'client_id': '123-fixture.apps.googleusercontent.com'},
                         {'client_type': 1, 'android_info': {'package_name': 'com.runmoni.app', 'certificate_hash': SHA1}}]}]}


class ConnectedBuildTest(unittest.TestCase):
    def test_current_signer_required(self):
        self.assertTrue(connected.validate_config(json.dumps(config()).encode(), SHA1))
        self.assertFalse(connected.validate_config(json.dumps(config()).encode(), '2' * 40))

    def test_invalid_or_admin_config_rejected_without_content_in_errors(self):
        values = [b'not json', b'[]', b'x' * 65537]
        for change in ('wrong_project', 'wrong_package', 'no_web_oauth', 'private_key', 'client_secret'):
            value = config()
            if change == 'wrong_project': value['project_info']['project_id'] = 'other'
            elif change == 'wrong_package': value['client'][0]['client_info']['android_client_info']['package_name'] = 'other'
            elif change == 'no_web_oauth': value['client'][0]['oauth_client'] = []
            else: value['client'][0][change] = 'NEVER_PRINT_THIS'
            values.append(json.dumps(value).encode())
        for value in values:
            with self.subTest(value=value[:20]), self.assertRaisesRegex(connected.SetupError, '^firebase_android_config_invalid$'):
                connected.validate_config(value, SHA1)

    def test_existing_file_preserved_and_new_file_private(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'client.json'
            connected.retain_config(path, b'first')
            self.assertEqual(0o600, path.stat().st_mode & 0o777)
            connected.retain_config(path, b'first')
            with self.assertRaises(connected.SetupError): connected.retain_config(path, b'changed')
            self.assertEqual(b'first', path.read_bytes())

    def test_public_https_only(self):
        for url in ('http://example.com', 'https://localhost', 'https://127.0.0.1', 'https://10.0.0.2',
                    'https://[::1]', 'https://user:pass@example.com', 'https://example.com/?key=secret',
                    'https://example.com/#secret', 'https://example.com:invalid', 'https://x.internal'):
            self.assertFalse(connected.public_url(url), url)
        self.assertTrue(connected.public_url('https://runmoni-api.example.com'))

    def test_health_requires_both_capabilities_and_no_authentication(self):
        response = mock.MagicMock()
        response.__enter__.return_value = response
        response.status = 200
        opener = mock.Mock()
        opener.open.return_value = response
        with mock.patch.object(connected.urllib.request, 'build_opener', return_value=opener), \
                mock.patch.object(connected.socket, 'getaddrinfo', return_value=[(2, 1, 6, '', ('8.8.8.8', 443))]):
            for health, expected in [({'status': 'ok'}, 'server_capabilities_missing'),
                                     ({'status': 'ok', 'capabilities': {'public_rooms': True, 'guest_sessions': True}}, 'ready')]:
                response.read.return_value = json.dumps(health).encode()
                self.assertEqual(expected, connected.check_health('https://example.com'))
            request = opener.open.call_args.args[0]
            self.assertEqual('https://example.com/health', request.full_url)
            self.assertFalse(request.has_header('Authorization'))
        self.assertIsNone(connected.NoRedirect().redirect_request(None, None, 302, '', {}, 'http://bad'))

    def test_public_name_resolving_to_private_address_is_rejected(self):
        with mock.patch.object(connected.socket, 'getaddrinfo', return_value=[(2, 1, 6, '', ('127.0.0.1', 443))]), \
                mock.patch.object(connected.urllib.request, 'build_opener') as request:
            self.assertEqual('public_https_url_missing', connected.check_health('https://127.1'))
            request.assert_not_called()

    def test_firebase_audit_is_a_bounded_public_query_without_sign_in(self):
        response = mock.MagicMock()
        response.__enter__.return_value = response
        response.status = 200
        opener = mock.Mock()
        opener.open.return_value = response
        with mock.patch.object(connected.urllib.request, 'build_opener', return_value=opener):
            response.read.return_value = b'[{"readTime":"2026-10-30T00:00:00Z"}]'
            self.assertEqual('ready', connected.firebase_health(json.dumps(config()).encode(), SHA1))
            request = opener.open.call_args.args[0]
            self.assertEqual('POST', request.method)
            self.assertFalse(request.has_header('Authorization'))
            self.assertNotIn('fixture-public', request.full_url)
            self.assertEqual(1, json.loads(request.data)['structuredQuery']['limit'])
            self.assertNotIn('communityProfiles', request.data.decode())
            response.read.return_value = b'{"ok":true}'
            self.assertEqual('firebase_public_response_invalid', connected.firebase_health(json.dumps(config()).encode(), SHA1))
        with mock.patch.object(connected.urllib.request, 'build_opener', side_effect=connected.urllib.error.HTTPError('https://fixed',403,'private detail',{},None)):
            self.assertEqual('firebase_rules_or_api_access_missing', connected.firebase_health(json.dumps(config()).encode(), SHA1))

    def test_certificate_export_uses_password_environment_and_redacts_failures(self):
        env = {'RUNMONI_KEYSTORE_FILE': '/fixture/key.jks', 'RUNMONI_KEY_ALIAS': 'fixture', 'RUNMONI_STORE_PASSWORD': 'NEVER_PRINT'}
        with mock.patch.object(connected.subprocess, 'run', return_value=mock.Mock(stdout=b'public DER')) as run:
            fingerprints = connected.certificate(env)
            self.assertEqual(hashlib.sha256(b'public DER').hexdigest(), fingerprints['sha256'])
            self.assertNotIn('NEVER_PRINT', ' '.join(run.call_args.args[0]))
            self.assertIn('-storepass:env', run.call_args.args[0])
        with mock.patch.object(connected.subprocess, 'run', side_effect=connected.subprocess.CalledProcessError(1, 'NEVER_PRINT')):
            with self.assertRaisesRegex(connected.SetupError, '^retained_certificate_export_failed$'):
                connected.certificate(env)

    def test_report_contains_no_config_or_false_device_success(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'config').mkdir()
            (root / 'config/android-release-signing.json').write_text(json.dumps({'certificateSha1': SHA1, 'certificateSha256': 'a' * 64}))
            env = {'RUNMONI_FIREBASE_ANDROID_CONFIG_BASE64': base64.b64encode(json.dumps(config()).encode()).decode()}
            report = connected.prepare(env, root=root, check_server=False)
            self.assertEqual('ready', report['firebase'])
            self.assertFalse(report['ready'])
            self.assertFalse(report['physicalDeviceVerified'])
            self.assertFalse(report['interactiveGoogleLoginVerified'])
            self.assertNotIn('fixture', json.dumps(report))
            self.assertFalse((root / '.secrets').exists())

    def test_connected_profile_fails_before_build_when_not_ready(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / 'report.json'
            argv = ['prepare', '--report', str(report), '--require-connected']
            with mock.patch.object(sys, 'argv', argv), mock.patch.object(connected, 'prepare', return_value={'ready': False}), contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(1, connected.main())
            self.assertEqual({'ready': False}, json.loads(report.read_text()))


if __name__ == '__main__':
    unittest.main()
