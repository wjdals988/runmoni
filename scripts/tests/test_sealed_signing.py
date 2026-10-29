"""Real libsodium round-trips with disposable fixture keys, never production data."""
import base64
import ctypes
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('sealed_signing', ROOT / 'scripts/cloud/seal-signing-key.py')
sealer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(sealer)


class SealedSigningTests(unittest.TestCase):
    def setUp(self):
        self.lib = sealer.sodium_library()
        self.lib.crypto_box_keypair.argtypes = [ctypes.c_void_p, ctypes.c_void_p]
        self.lib.crypto_box_keypair.restype = ctypes.c_int
        self.lib.crypto_box_seal_open.argtypes = [ctypes.c_void_p, ctypes.c_void_p, ctypes.c_ulonglong,
                                                 ctypes.c_void_p, ctypes.c_void_p]
        self.lib.crypto_box_seal_open.restype = ctypes.c_int
        self.pk = ctypes.create_string_buffer(32); self.sk = ctypes.create_string_buffer(32)
        self.assertEqual(0, self.lib.crypto_box_keypair(self.pk, self.sk))
        self.packet = {'repository': 'wjdals988/runmoni', 'scope': 'repository', 'keyId': 'fixture-id',
                       'publicKey': base64.b64encode(self.pk.raw).decode()}

    def test_git_hub_sealed_box_round_trip_preserves_exact_fixture_bytes(self):
        plaintext = 'fixture with spaces, equals= and unicode한글\n'.encode()
        encoded = sealer.seal(self.lib, self.pk.raw, plaintext)
        ciphertext = base64.b64decode(encoded)
        self.assertEqual(len(plaintext) + 48, len(ciphertext))
        output = ctypes.create_string_buffer(len(plaintext))
        self.assertEqual(0, self.lib.crypto_box_seal_open(output, ciphertext, len(ciphertext), self.pk, self.sk))
        self.assertEqual(plaintext, output.raw)
        self.assertNotEqual(encoded, sealer.seal(self.lib, self.pk.raw, plaintext))

    def test_all_four_fields_are_ciphertext_for_the_expected_target(self):
        values = {name: ('fixture:' + name).encode() for name in ('RUNMONI_STORE_PASSWORD', 'RUNMONI_KEY_ALIAS',
                   'RUNMONI_KEY_PASSWORD', 'RUNMONI_KEYSTORE_BASE64')}
        payload = sealer.encrypted_properties(self.packet, values).decode()
        fields = dict(line.split('=', 1) for line in payload.splitlines())
        self.assertEqual(sealer.FORMAT, fields['format'])
        self.assertEqual('wjdals988/runmoni', fields['repository'])
        for name, value in values.items():
            self.assertNotIn(value.decode(), payload)
            ciphertext = base64.b64decode(fields[name])
            output = ctypes.create_string_buffer(len(value))
            self.assertEqual(0, self.lib.crypto_box_seal_open(output, ciphertext, len(ciphertext), self.pk, self.sk))
            self.assertEqual(value, output.raw)

    def test_wrong_target_and_malformed_public_keys_are_rejected(self):
        for changed in [dict(repository='other/repo'), dict(scope='environment'), dict(keyId='bad\nheader'),
                        dict(publicKey='not-base64!'), dict(publicKey=base64.b64encode(b'short').decode())]:
            with self.subTest(changed=changed), self.assertRaises((ValueError, TypeError)):
                sealer.packet_key(self.packet | changed)

    def test_wrong_private_key_cannot_open_ciphertext(self):
        ciphertext = base64.b64decode(sealer.seal(self.lib, self.pk.raw, b'fixture-value'))
        wrong = ctypes.create_string_buffer(32); other = ctypes.create_string_buffer(32)
        self.assertEqual(0, self.lib.crypto_box_keypair(other, wrong))
        output = ctypes.create_string_buffer(len(b'fixture-value'))
        self.assertNotEqual(0, self.lib.crypto_box_seal_open(output, ciphertext, len(ciphertext), self.pk, wrong))


if __name__ == '__main__':
    unittest.main()
