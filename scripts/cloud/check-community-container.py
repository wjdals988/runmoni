#!/usr/bin/env python3
"""Check only a task-owned local container/volume; never creates public hosting."""
import argparse
import http.client
import json
import os
from pathlib import Path
import subprocess
import time
from uuid import uuid4

parser = argparse.ArgumentParser()
parser.add_argument('--image', default='runmoni-api:room-review')
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
env = os.environ.copy()
for key in ('DOCKER_HOST', 'DOCKER_CONTEXT', 'DOCKER_TLS', 'DOCKER_TLS_VERIFY', 'DOCKER_CERT_PATH'):
    env.pop(key, None)
docker = ['docker', '--host=unix:///var/run/docker.sock']
suffix = uuid4().hex[:8]
name, volume = 'runmoni-room-check-'+suffix, 'runmoni-room-check-data-'+suffix
port = 0

def command(*parts):
    return subprocess.check_output(docker+list(parts), env=env, text=True).strip()

def api(path, body=None, token=None):
    connection = http.client.HTTPConnection('127.0.0.1', port, timeout=5)
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer '+token
    try:
        connection.request('POST' if body is not None else 'GET', path,
                           json.dumps(body) if body is not None else None, headers)
        response = connection.getresponse()
        data = json.load(response)
        if response.status >= 400:
            raise RuntimeError(f'HTTP{response.status}; '+data.get('error', {}).get('code', 'request_failed'))
        return data
    finally:
        connection.close()

def start():
    global port
    command('run', '-d', '--name', name, '--memory', '256m', '--cpus', '1',
            '-p', '127.0.0.1::8080', '-v', volume+':/var/lib/runmoni', args.image)
    binding = command('port', name, '8080/tcp')
    assert binding.startswith('127.0.0.1:')
    port = int(binding.rsplit(':', 1)[1])
    for _ in range(40):
        try:
            return api('/health')
        except (OSError, ValueError, http.client.HTTPException):
            time.sleep(.25)
    raise RuntimeError('Local container HTTP readiness deadline exceeded')

def stop():
    command('stop', '-t', '10', name)
    command('rm', name)

result = {'publishedAddress': False, 'volume': volume, 'image': args.image}
try:
    command('volume', 'create', volume)
    assert start()['capabilities']['public_rooms']
    login = api('/v1/auth/guest', {'nickname': '컨테이너검증-'+suffix, 'adult_confirmed': True})
    token = login['access_token']
    crew = api('/v1/crews', {'name': '재시작 검증 방', 'visibility': 'open'}, token)
    party = api('/v1/parties', {'title': '재시작 검증 파티', 'kind': 'remote',
                'starts_at_ms': int(time.time()*1000)+3600000, 'distance_km': 5, 'capacity': 2}, token)
    api('/v1/parties/'+party['id']+'/messages', {'body': '재생성 후 보존 검증'}, token)
    result['runtimeUid'] = command('exec', name, 'id', '-u')
    assert result['runtimeUid'] == '10001'
    result['python'] = command('exec', name, 'python', '--version')
    result['imageId'] = command('image', 'inspect', args.image, '--format', '{{.Id}}')
    stop()
    start()
    assert api('/v1/me', token=token)['id'] == login['user']['id']
    assert crew['id'] in [x['id'] for x in api('/v1/crews/discover')['items']]
    assert party['id'] in [x['id'] for x in api('/v1/parties/discover')['items']]
    assert api('/v1/parties/'+party['id']+'/messages', token=token)['items'][0]['body'] == '재생성 후 보존 검증'
    result['recreatedContainerPreservedSessionCrewPartyMessage'] = True
    result['httpTransport'] = 'host loopback to published container port'
except Exception:
    subprocess.run(docker+['logs', '--tail', '35', name], env=env, check=False)
    raise
finally:
    result['containersStopped'] = False
    try:
        stop()
        result['containersStopped'] = True
    except subprocess.CalledProcessError:
        pass
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n')
print(json.dumps(result, ensure_ascii=False))
