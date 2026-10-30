#!/usr/bin/env python3
"""Validate connected-build inputs without publishing credentials or claiming device tests."""
import argparse
import base64
from datetime import datetime, timezone
import hashlib
import ipaddress
import json
import os
import re
import socket
from pathlib import Path
import subprocess
import tempfile
import urllib.parse
import urllib.request
import urllib.error

ROOT = Path(__file__).resolve().parents[2]
MAX_CONFIG = 65536


class SetupError(Exception):
    pass


def public_url(value):
    try:
        uri = urllib.parse.urlsplit(value)
        if (uri.scheme != 'https' or not uri.hostname or uri.username or uri.password
                or uri.query or uri.fragment or uri.port not in (None, 443)):
            return False
        host = uri.hostname.lower()
        if host == 'localhost' or '.' not in host or host.endswith(('.localhost', '.local', '.internal')):
            return False
        try:
            return ipaddress.ip_address(host).is_global
        except ValueError:
            return True
    except ValueError:
        return False


def validate_config(data, sha1):
    try:
        if len(data) > MAX_CONFIG:
            raise ValueError()
        config = json.loads(data)
        # Only an Android client config is accepted, never an admin credential.
        def has_private(value):
            if isinstance(value, dict):
                return any(k in {'private_key', 'private_key_id', 'client_secret', 'refresh_token', 'service_account'}
                           or has_private(v) for k, v in value.items())
            return isinstance(value, list) and any(has_private(v) for v in value)
        if has_private(config) or config.get('type') == 'service_account':
            raise ValueError()
        if config['project_info']['project_id'] != 'runmoni-3c08e':
            raise ValueError()
        clients = [c for c in config['client']
                   if c['client_info']['android_client_info']['package_name'] == 'com.runmoni.app']
        if len(clients) != 1:
            raise ValueError()
        client = clients[0]
        if (not re.fullmatch(r'1:[0-9]+:android:[A-Za-z0-9]+', client['client_info']['mobilesdk_app_id'])
                or not re.fullmatch(r'[A-Za-z0-9_-]{20,200}', client['api_key'][0]['current_key'])):
            raise ValueError()
        oauth = client['oauth_client']
        web = next((c.get('client_id', '') for c in oauth if c.get('client_type') == 3), '')
        if not re.fullmatch(r'[0-9]+-[A-Za-z0-9]+\.apps\.googleusercontent\.com', web):
            raise ValueError()
        signer_match = any(c.get('client_type') == 1 and
                           c.get('android_info', {}).get('package_name') == 'com.runmoni.app' and
                           c.get('android_info', {}).get('certificate_hash', '').replace(':', '').lower() == sha1
                           for c in oauth)
        return signer_match
    except (ValueError, KeyError, TypeError, IndexError, AttributeError):
        raise SetupError('firebase_android_config_invalid') from None


def retain_config(destination, data):
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists():
        if destination.read_bytes() != data:
            raise SetupError('firebase_existing_config_differs')
        return
    with destination.open('xb') as output:
        destination.chmod(0o600)
        output.write(data)


def certificate(env):
    """Export only public DER; key passwords stay in the subprocess environment."""
    key = env.get('RUNMONI_KEYSTORE_FILE')
    with tempfile.TemporaryDirectory() as directory:
        if not key and env.get('RUNMONI_KEYSTORE_BASE64'):
            try:
                data = base64.b64decode(env['RUNMONI_KEYSTORE_BASE64'], validate=True)
                if not 0 < len(data) <= 1048576:
                    raise ValueError()
            except ValueError:
                raise SetupError('retained_key_invalid') from None
            key = Path(directory) / 'key.jks'
            with key.open('xb') as output:
                key.chmod(0o600)
                output.write(data)
        if not key:
            return None
        if not env.get('RUNMONI_STORE_PASSWORD') or not env.get('RUNMONI_KEY_ALIAS'):
            raise SetupError('retained_key_inputs_missing')
        try:
            result = subprocess.run(['keytool', '-exportcert', '-keystore', str(key),
                                     '-alias', env['RUNMONI_KEY_ALIAS'], '-storepass:env', 'RUNMONI_STORE_PASSWORD'],
                                    env=env, capture_output=True, timeout=20, check=True)
            return {'sha1': hashlib.sha1(result.stdout).hexdigest(),
                    'sha256': hashlib.sha256(result.stdout).hexdigest()}
        except (OSError, subprocess.SubprocessError):
            raise SetupError('retained_certificate_export_failed') from None


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def check_health(url):
    if not public_url(url):
        return 'public_https_url_missing'
    try:
        addresses = socket.getaddrinfo(urllib.parse.urlsplit(url).hostname, 443, type=socket.SOCK_STREAM)
        if not addresses or any(not ipaddress.ip_address(entry[4][0]).is_global for entry in addresses):
            return 'public_https_url_missing'
        # No session, API key, cookies or redirect to another origin.
        request = urllib.request.Request(url.rstrip('/') + '/health', headers={'Accept': 'application/json'})
        with urllib.request.build_opener(NoRedirect()).open(request, timeout=10) as response:
            if response.status != 200:
                return 'server_unreachable'
            payload = response.read(32769)
            if len(payload) > 32768:
                return 'server_health_invalid'
        health = json.loads(payload)
        caps = health.get('capabilities', {})
        return ('ready' if health.get('status') == 'ok' and caps.get('public_rooms') is True
                and caps.get('guest_sessions') is True else 'server_capabilities_missing')
    except Exception:
        # Network response/errors can contain addresses or secrets; never copy them.
        return 'server_unreachable'


def firebase_health(data, sha1):
    """Public room query only. No account creation, auth token, private reads or writes."""
    config = json.loads(data)
    client = next(c for c in config['client'] if c['client_info']['android_client_info']['package_name'] == 'com.runmoni.app')
    api_key = client['api_key'][0]['current_key']
    query = {'structuredQuery': {'from': [{'collectionId': 'communityRooms'}], 'limit': 1,
        'where': {'compositeFilter': {'op': 'AND', 'filters': [
            {'fieldFilter': {'field': {'fieldPath': 'visibility'}, 'op': 'EQUAL', 'value': {'stringValue': 'open'}}},
            {'fieldFilter': {'field': {'fieldPath': 'kind'}, 'op': 'EQUAL', 'value': {'stringValue': 'crew'}}}]}},
        'orderBy': [{'field': {'fieldPath': 'createdAt'}, 'direction': 'DESCENDING'}]}}
    try:
        request = urllib.request.Request('https://firestore.googleapis.com/v1/projects/runmoni-3c08e/databases/(default)/documents:runQuery',
            data=json.dumps(query).encode(), method='POST', headers={'Content-Type': 'application/json',
                'X-Goog-Api-Key': api_key, 'X-Android-Package': 'com.runmoni.app', 'X-Android-Cert': sha1.upper()})
        with urllib.request.build_opener(NoRedirect()).open(request, timeout=10) as response:
            if response.status != 200:
                return 'firebase_public_query_failed'
            payload = response.read(32769)
            if len(payload) > 32768:
                return 'firebase_public_response_invalid'
        rows = json.loads(payload)
        if not isinstance(rows, list) or not rows or len(rows) > 2:
            return 'firebase_public_response_invalid'
        for row in rows:
            if not isinstance(row.get('readTime'), str):
                return 'firebase_public_response_invalid'
            if 'document' in row:
                document = row['document']
                if (not re.fullmatch(r'projects/runmoni-3c08e/databases/\(default\)/documents/communityRooms/[A-Za-z0-9]{20}', document['name'])
                        or document['fields']['visibility'] != {'stringValue': 'open'} or document['fields']['kind'] != {'stringValue': 'crew'}):
                    return 'firebase_public_response_invalid'
        return 'ready'
    except urllib.error.HTTPError as error:
        return {403: 'firebase_rules_or_api_access_missing', 404: 'firebase_database_missing',
                400: 'firebase_index_or_query_missing', 412: 'firebase_index_or_query_missing'}.get(error.code, 'firebase_public_query_failed')
    except Exception:
        return 'firebase_public_query_failed'


def prepare(env, *, root=ROOT, install=False, check_server=True):
    approved = json.loads((root / 'config/android-release-signing.json').read_text())
    revision = env.get('GITHUB_SHA', '')
    report = {'schemaVersion': 1, 'checkedAt': datetime.now(timezone.utc).isoformat(),
              'sourceRevision': revision if re.fullmatch('[0-9a-f]{40}', revision) else None,
              'certificateSha1': approved['certificateSha1'],
              'certificateSha256': approved['certificateSha256'], 'certificateVerified': False,
              'firebase': 'missing', 'server': 'not_checked', 'ready': False,
              'physicalDeviceVerified': False, 'interactiveGoogleLoginVerified': False,
              'anonymousProviderVerified': False, 'publicRoomWritesVerified': False,
              'backend': env.get('RUNMONI_COMMUNITY_BACKEND', 'firebase')}
    try:
        actual = certificate(env)
        if actual:
            if actual['sha1'] != approved['certificateSha1'] or actual['sha256'] != approved['certificateSha256']:
                raise SetupError('retained_certificate_mismatch')
            report['certificateVerified'] = True
        encoded = env.get('RUNMONI_FIREBASE_ANDROID_CONFIG_BASE64', '')
        destination = root / '.secrets/firebase/google-services.json'
        data = destination.read_bytes() if destination.is_file() else None
        if encoded:
            try:
                if len(encoded) > MAX_CONFIG * 2:
                    raise ValueError()
                data = base64.b64decode(encoded, validate=True)
            except ValueError:
                raise SetupError('firebase_encoding_invalid') from None
        if data is not None:
            matches = validate_config(data, approved['certificateSha1'])
            report['firebase'] = 'ready' if matches else 'current_android_oauth_certificate_missing'
            if install:
                if not matches:
                    raise SetupError('current_android_oauth_certificate_missing')
                retain_config(destination, data)
        if report['backend'] not in ('firebase', 'http'):
            raise SetupError('community_backend_invalid')
        if check_server:
            report['server'] = (firebase_health(data, approved['certificateSha1']) if report['firebase'] == 'ready'
                                else 'firebase_configuration_missing') if report['backend'] == 'firebase' else check_health(env.get('RUNMONI_API_URL', ''))
        report['ready'] = (report['firebase'] == report['server'] == 'ready' and report['certificateVerified'])
    except SetupError as error:
        report['error'] = str(error)  # only fixed codes defined above
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--install', action='store_true')
    parser.add_argument('--require-connected', action='store_true')
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    report = prepare(dict(os.environ), install=args.install)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))
    return 1 if report.get('error') or (args.require_connected and not report['ready']) else 0


if __name__ == '__main__':
    raise SystemExit(main())
