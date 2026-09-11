#!/usr/bin/env python3
"""Install the fixed E2E tool as a persistent user service on OpenWebUI's bridge."""
import json
import os
from pathlib import Path
import secrets
import subprocess
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT / '.local/e2e-tool'
UNIT = Path.home() / '.config/systemd/user/eventmanagement-e2e-tool.service'
BLACKOUT_UNIT = Path.home() / '.config/systemd/user/eventmanagement-blackout-tool.service'


def main():
    os.umask(0o077)
    network = json.loads(subprocess.check_output([
        'docker', 'inspect', 'open-webui', '--format', '{{json .NetworkSettings.Networks}}'], text=True))
    gateway = network['open-webui_default']['Gateway']
    PRIVATE.mkdir(parents=True, exist_ok=True)
    token_file = PRIVATE / 'token'
    if not token_file.exists():
        token_file.write_text(secrets.token_urlsafe(48))
    token = token_file.read_text().strip()
    (PRIVATE / 'service.env').write_text(f'E2E_TOOL_TOKEN={token}\nE2E_TOOL_HOST={gateway}\nE2E_TOOL_PORT=8095\n')
    (PRIVATE / 'blackout-service.env').write_text(f'E2E_TOOL_TOKEN={token}\nE2E_TOOL_HOST={gateway}\nE2E_TOOL_PORT=8097\nE2E_TOOL_MODE=blackout\n')
    connection = {'type': 'openapi', 'url': f'http://{gateway}:8095', 'path': 'openapi.json',
                  'auth_type': 'bearer', 'key': token, 'config': {'enable': True},
                  'info': {'id': 'validacion-e2e', 'name': 'Validación E2E',
                           'description': 'Ejecuta UC-001 en runtime shared con tenant sintético y proveedores mock; devuelve evidencia verificable.'},
                  'access_control': {}}
    (PRIVATE / 'connection.json').write_text(json.dumps(connection, ensure_ascii=False, indent=2))
    content = f'''[Unit]
Description=Event Management E2E tool for OpenWebUI
After=network-online.target

[Service]
Type=simple
WorkingDirectory={ROOT}
EnvironmentFile={PRIVATE}/service.env
ExecStart=/usr/bin/python3 {ROOT}/services/e2e-tool-api/server.py
Restart=on-failure
RestartSec=5
KillMode=control-group
TimeoutStopSec=30
UMask=0077

[Install]
WantedBy=default.target
'''
    UNIT.parent.mkdir(parents=True, exist_ok=True)
    if UNIT.exists() and UNIT.read_text() != content:
        raise SystemExit('Existing service differs; review before overwriting')
    UNIT.write_text(content)
    blackout_content = content.replace('Description=Event Management E2E tool for OpenWebUI', 'Description=Event Management blackout tool for OpenWebUI').replace(
        f'EnvironmentFile={PRIVATE}/service.env',
        f'EnvironmentFile={PRIVATE}/blackout-service.env')
    if BLACKOUT_UNIT.exists() and BLACKOUT_UNIT.read_text() != blackout_content:
        raise SystemExit('Existing blackout service differs; review before overwriting')
    BLACKOUT_UNIT.write_text(blackout_content)
    blackout_connection = {**connection, 'url': f'http://{gateway}:8097',
                           'info': {'id': 'gestion-blackouts', 'name': 'Gestión de Blackouts',
                                    'description': 'Registra blackouts de 60 minutos por customer y servidor y confirma el registro.'}}
    (PRIVATE / 'blackout-connection.json').write_text(json.dumps(blackout_connection, ensure_ascii=False, indent=2))
    subprocess.run(['systemctl', '--user', 'daemon-reload'], check=True)
    subprocess.run(['systemctl', '--user', 'enable', '--now', UNIT.name], check=True)
    subprocess.run(['systemctl', '--user', 'enable', '--now', BLACKOUT_UNIT.name], check=True)
    request = urllib.request.Request(connection['url'] + '/openapi.json',
                                     headers={'Authorization': 'Bearer ' + token})
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    for attempt in range(10):
        try:
            with opener.open(request, timeout=2) as response:
                assert len(json.load(response)['paths']) == 3
                break
        except OSError:
            if attempt == 9:
                raise
            time.sleep(0.5)
    blackout_request = urllib.request.Request(blackout_connection['url'] + '/openapi.json',
                                               headers={'Authorization': 'Bearer ' + token})
    for attempt in range(10):
        try:
            with opener.open(blackout_request, timeout=2) as response:
                blackout_paths = json.load(response)['paths']
                assert list(blackout_paths) == ['/blackouts']
            break
        except OSError:
            if attempt == 9:
                raise
            time.sleep(0.5)
    print(f'Services: {UNIT.name}, {BLACKOUT_UNIT.name}; private URLs on gateway ports 8095/8097; token stored locally, never printed')


if __name__ == '__main__':
    main()
