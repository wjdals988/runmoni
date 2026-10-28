#!/usr/bin/env python3
"""Encrypt approved signing inputs to a GitHub repository PUBLIC key using libsodium.

The resulting properties file contains only sealed ciphertext and public metadata.
No network, token, private signing value, or replacement key generation is involved.
"""
import argparse
import base64
import ctypes
import ctypes.util
import importlib.util
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('signing_connector', Path(__file__).with_name('connect-signing-key.py'))
connector = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(connector)
FORMAT = 'runmoni-github-sealed-secrets-v1'


def packet_key(packet):
    if (not isinstance(packet, dict) or set(packet) != {'repository', 'scope', 'keyId', 'publicKey'}
            or packet.get('repository') != connector.REPO or packet.get('scope') != 'repository'):
        raise ValueError('Public packet targets another repository or scope.')
    key_id = packet.get('keyId')
    if not isinstance(key_id, str) or not re.fullmatch(r'[A-Za-z0-9_-]{1,128}', key_id):
        raise ValueError('Public encryption key ID is invalid.')
    encoded = packet.get('publicKey')
    if not isinstance(encoded, str):
        raise ValueError('Public encryption key is missing.')
    public_key = base64.b64decode(encoded, validate=True)
    if len(public_key) != 32 or base64.b64encode(public_key).decode() != encoded:
        raise ValueError('Public encryption key must be canonical base64 of32bytes.')
    return public_key


def sodium_library():
    name = ctypes.util.find_library('sodium')
    if not name:
        raise RuntimeError('Installed libsodium is required; no replacement crypto is used.')
    library = ctypes.CDLL(name)
    library.sodium_init.restype = ctypes.c_int
    if library.sodium_init() < 0:
        raise RuntimeError('libsodium initialization failed.')
    library.crypto_box_seal.argtypes = [ctypes.c_void_p, ctypes.c_void_p, ctypes.c_ulonglong, ctypes.c_void_p]
    library.crypto_box_seal.restype = ctypes.c_int
    return library


def seal(library, public_key, value):
    if not isinstance(value, bytes) or not 0 < len(value) <= 65536:
        raise ValueError('Signing input is empty or exceeds GitHub limits.')
    output = ctypes.create_string_buffer(len(value) + 48)
    if library.crypto_box_seal(output, value, len(value), public_key) != 0:
        raise RuntimeError('libsodium sealed-box encryption failed.')
    return base64.b64encode(output.raw).decode()


def encrypted_properties(packet, values):
    public_key = packet_key(packet)
    library = sodium_library()
    names = ('RUNMONI_STORE_PASSWORD', 'RUNMONI_KEY_ALIAS', 'RUNMONI_KEY_PASSWORD', 'RUNMONI_KEYSTORE_BASE64')
    if set(values) != set(names):
        raise ValueError('Exactly four approved signing inputs are required.')
    metadata = {'format': FORMAT, 'repository': connector.REPO, 'scope': 'repository',
                'certificateSha256': connector.POLICY['certificateSha256'],
                'keyId': packet['keyId'], 'publicKey': packet['publicKey']}
    lines = [f'{name}={value}' for name, value in metadata.items()]
    lines += [f'{name}={seal(library, public_key, values[name])}' for name in names]
    return ('\n'.join(lines) + '\n').encode('ascii')


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--packet-file', type=Path, required=True)
    parser.add_argument('--state-file', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        if args.packet_file.stat().st_size > 4096:
            raise ValueError('Public packet is oversized.')
        packet = json.loads(args.packet_file.read_text())
        packet_key(packet)  # Reject wrong public targets before loading private signing inputs.
        values = connector.approved_inputs(args.state_file)
        payload = encrypted_properties(packet, values)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with args.output.open('xb') as stream:
            stream.write(payload)
        print(json.dumps({'encryptedPayload': str(args.output), 'repository': connector.REPO,
                          'certificateSha256': connector.POLICY['certificateSha256'],
                          'plaintextValuesPrinted': False, 'networkRequests': 0}))
        return 0
    except Exception as error:
        # Never serialize arbitrary crypto/key exceptions or credential-bearing objects.
        print(json.dumps({'ok': False, 'error': f'Public-packet sealing failed ({type(error).__name__}); raw values omitted.'}), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
