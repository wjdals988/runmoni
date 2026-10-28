"""Offline secret-transport boundaries; fixtures are never production credentials."""
import base64
import contextlib
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import re
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('signing_connection', ROOT / 'scripts/cloud/connect-signing-key.py')
connector = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(connector)


class FakeGitHub:
    def __init__(self, status=200, deny_create=False, deny_public=False, fail_write=-1):
        self.status, self.deny_create, self.deny_public = status, deny_create, deny_public
        self.fail_write, self.calls, self.writes = fail_write, [], []

    def __call__(self, arguments, body):
        self.calls.append((arguments, body))
        if 'PUT' in arguments:
            return int(self.deny_create), b'{}'
        if arguments[-1].endswith('public-key'):
            return int(self.deny_public), b'HTTP/2.0 403\n{}' if self.deny_public else b'HTTP/2.0 200\n{}'
        if '--include' in arguments:
            return (0 if self.status == 200 else 1), f'HTTP/2.0 {self.status}\n{{}}'.encode()
        self.writes.append((arguments, body))
        return int(len(self.writes) - 1 == self.fail_write), b'{}'


class SigningConnectionTests(unittest.TestCase):
    def fixture(self, root):
        key = root / 'fixture.p12'
        key.write_bytes(b'public-keystore-fixture-not-a-private-key')
        key.chmod(0o600)
        state = root / 'credentials.json'
        state.write_text(json.dumps({'keyFile': str(key), 'storePassword': 'fixture-password',
                                     'keyAlias': 'fixture-alias', 'keyPassword': 'fixture-password'}))
        state.chmod(0o600)
        return state, key

    def values(self):
        return {'RUNMONI_STORE_PASSWORD': b'fixture-password', 'RUNMONI_KEY_ALIAS': b'fixture-alias',
                'RUNMONI_KEY_PASSWORD': b'fixture-password', 'RUNMONI_KEYSTORE_BASE64': b'fixture-base64'}

    def run_connect(self, server, scope='repository'):
        with contextlib.redirect_stdout(io.StringIO()) as output:
            receipt = connector.connect(self.values(), server, scope)
        self.assertNotIn('fixture-password', output.getvalue())
        return receipt

    def test_all_runtime_and_release_certificate_pins_match_the_policy(self):
        policy = connector.POLICY['certificateSha256']
        for relative, pattern in [
            ('app/src/main/java/com/runmoni/app/domain/AppRelease.kt', r'const val RELEASE_CERTIFICATE = "([a-f0-9]{64})"'),
            ('scripts/ConnectOriginalSigningKey.java', r'String CERTIFICATE = "([a-f0-9]{64})"'),
        ]:
            self.assertEqual(policy, re.search(pattern, (ROOT / relative).read_text())[1])
        for name, relative in [('cloud_release', 'scripts/cloud/release-app.py'), ('publisher', 'scripts/publish_android_preview.py')]:
            spec = importlib.util.spec_from_file_location(name, ROOT / relative)
            module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
            self.assertEqual(policy, module.CERTIFICATE)
        self.assertNotEqual(policy, connector.POLICY['previousCertificateSha256'])

    def test_only_verified_key_bytes_are_sent_and_local_files_are_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            state, key = self.fixture(Path(directory))
            before = state.read_bytes(), key.read_bytes()
            der = b'public-der-fixture'
            with mock.patch.object(connector, 'POLICY', {'certificateSha256': hashlib.sha256(der).hexdigest()}), \
                    mock.patch.object(connector.subprocess, 'run', return_value=mock.Mock(returncode=0, stdout=der)) as run:
                values = connector.approved_inputs(state)
            self.assertEqual(key.read_bytes(), base64.b64decode(values['RUNMONI_KEYSTORE_BASE64']))
            self.assertEqual(before, (state.read_bytes(), key.read_bytes()))
            self.assertNotIn('fixture-password', ' '.join(run.call_args.args[0]))

    def test_wrong_certificate_prevents_github_calls(self):
        with tempfile.TemporaryDirectory() as directory:
            state, _ = self.fixture(Path(directory))
            with mock.patch.object(connector.subprocess, 'run', return_value=mock.Mock(returncode=0, stdout=b'wrong-der')):
                with self.assertRaises(connector.ConnectionError):
                    connector.approved_inputs(state)

    def test_readable_credentials_are_rejected_before_crypto_or_network(self):
        with tempfile.TemporaryDirectory() as directory:
            state, _ = self.fixture(Path(directory)); state.chmod(0o644)
            with mock.patch.object(connector.subprocess, 'run') as run, self.assertRaises(connector.ConnectionError):
                connector.approved_inputs(state)
            run.assert_not_called()

    def test_repository_success_needs_no_environment_admin_and_uses_only_stdin(self):
        server = FakeGitHub(status=403); receipt = self.run_connect(server)
        self.assertEqual(list(self.values()), receipt['connectedSecretNames'])
        self.assertFalse(any('PUT' in args for args, _ in server.calls))
        self.assertFalse(receipt['appPublished'])
        self.assertEqual('repository', receipt['scope'])
        self.assertFalse(any('/environments/' in ' '.join(args) for args, _ in server.calls))
        for arguments, body in server.writes:
            self.assertNotIn(body.decode(), arguments)
            self.assertNotIn('--env', arguments)

    def test_authorized_missing_environment_is_created_before_secrets(self):
        server = FakeGitHub(status=404); self.run_connect(server, 'environment')
        self.assertEqual(1, sum('PUT' in args for args, _ in server.calls))
        self.assertEqual(4, len(server.writes))

    def test_environment_and_public_key_denials_send_zero_secrets(self):
        for server in [FakeGitHub(status=403), FakeGitHub(status=404, deny_create=True), FakeGitHub(deny_public=True)]:
            with self.subTest(server=server), self.assertRaises(connector.ConnectionError):
                self.run_connect(server, 'environment')
            self.assertEqual([], server.writes)

    def test_partial_failure_never_reports_a_complete_connection_or_secret_values(self):
        server = FakeGitHub(fail_write=2)
        with contextlib.redirect_stdout(io.StringIO()) as output, self.assertRaises(connector.ConnectionError) as error:
            connector.connect(self.values(), server)
        self.assertIn('after 2 entries', str(error.exception))
        self.assertNotIn('fixture-password', output.getvalue() + str(error.exception))

    def test_owner_login_is_isolated_without_changing_injected_task_credentials(self):
        with mock.patch.dict(os.environ, {'GH_TOKEN': 'fixture-task-token', 'GITHUB_TOKEN': 'fixture-other',
                                         'GH_DEBUG': 'api', 'GIT_TRACE': '1'}, clear=True):
            env = connector.child_env('/tmp/fixture-owner-auth')
            self.assertNotIn('GH_TOKEN', env); self.assertNotIn('GITHUB_TOKEN', env)
            self.assertNotIn('GH_DEBUG', env); self.assertNotIn('GIT_TRACE', env)
            self.assertEqual('fixture-task-token', os.environ['GH_TOKEN'])
            self.assertEqual('/tmp/fixture-owner-auth', env['GH_CONFIG_DIR'])

    def test_safe_failure_diagnostics_distinguish_permissions_without_echoing_credentials(self):
        result = mock.Mock(returncode=1, stdout=b'HTTP/2.0 403\nX-OAuth-Scopes: repo\n{}',
                           stderr=b'Resource not accessible by integration fixture-private-value')
        with mock.patch.object(connector.subprocess, 'run', return_value=result), \
                contextlib.redirect_stdout(io.StringIO()) as output:
            connector.request(['api', '--include', 'fixture-path'], None, {})
        diagnostic = json.loads(output.getvalue())
        self.assertEqual(403, diagnostic['httpStatus'])
        self.assertEqual('integration_permission', diagnostic['errorKind'])
        self.assertTrue(diagnostic['oauthScopesHeaderPresent'])
        self.assertTrue(diagnostic['repoOAuthScopeGranted'])
        self.assertNotIn('fixture-private-value', output.getvalue())

    def test_environment_create_sends_explicit_json_and_existing_environment_is_preserved(self):
        missing = FakeGitHub(status=404); self.run_connect(missing, 'environment')
        self.assertEqual([b'{}'], [body for arguments, body in missing.calls if 'PUT' in arguments])
        existing = FakeGitHub(); self.run_connect(existing, 'environment')
        self.assertFalse(any('PUT' in arguments for arguments, _ in existing.calls))


if __name__ == '__main__':
    unittest.main()
