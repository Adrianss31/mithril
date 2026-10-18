#!/usr/bin/env python3
"""Firma l'APK release compilato in CI e, se la versione è nuova, pubblica la GitHub Release
con APK, update.json firmato e SHA256SUMS. Le chiavi arrivano dai Secrets e restano in una
cartella temporanea del runner."""
import base64
import hashlib
import json
import os
import re
import subprocess
import tempfile
import time
import urllib.request
from pathlib import Path

REPO = 'Adrianss31/mithril'
PACKAGE = 'it.mithril.app'
EXPECTED_CERT = Path('config/release-cert-sha256.txt').read_text().strip()


def run(args, **kw):
    return subprocess.run([str(a) for a in args], check=True, capture_output=True, **kw).stdout


def main():
    assert os.environ.get('GITHUB_REPOSITORY') == REPO, 'Pubblicazione consentita solo dal repository originale'
    commit = os.environ['GITHUB_SHA']
    build_tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0'
    apksigner, aapt = build_tools / 'apksigner', build_tools / 'aapt'
    unsigned = Path('app/build/outputs/apk/release/app-release-unsigned.apk')
    assert unsigned.is_file(), 'APK release non trovato'
    meta = run([aapt, 'dump', 'badging', unsigned]).decode()
    pkg = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", meta)
    assert pkg and pkg[1] == PACKAGE, 'Pacchetto inatteso'
    code, version = int(pkg[2]), pkg[3]
    notes = Path('docs') / f'CHANGELOG-{version}.md'
    assert notes.is_file(), f'Manca {notes}: ogni versione ha bisogno delle note di rilascio'

    with tempfile.TemporaryDirectory(prefix='mithril-', dir=os.environ.get('RUNNER_TEMP')) as tmp:
        tmp = Path(tmp)
        secrets = {
            'release.p12': base64.b64decode(os.environ.pop('ANDROID_KEYSTORE_BASE64'), validate=True),
            'password.txt': os.environ.pop('ANDROID_KEYSTORE_PASSWORD').encode(),
            'update-private.pem': os.environ.pop('UPDATE_PRIVATE_KEY_PEM').encode(),
        }
        for name, data in secrets.items():
            assert data, f'Secret mancante: {name}'
            (tmp / name).write_bytes(data)
            (tmp / name).chmod(0o600)
        del secrets
        out = tmp / 'release'
        run(['python3', 'scripts/sign_release.py', '--apk', unsigned, '--apksigner', apksigner,
             '--keystore', tmp / 'release.p12', '--password-file', tmp / 'password.txt',
             '--update-key', tmp / 'update-private.pem', '--version', version, '--code', code,
             '--notes-file', notes, '--output', out])
        verification = (out / 'apk-verification.txt').read_text()
        cert = re.search(r'certificate SHA-256 digest: ([a-f0-9]+)', verification)
        assert cert and cert[1] == EXPECTED_CERT, 'Identità di firma cambiata: rifiuto la pubblicazione'
        assert re.search(r'Verified using v2 scheme.*: true', verification), 'Firma APK v2 richiesta'
        print(f'APK {version} ({code}) firmato e verificato', flush=True)

        latest = subprocess.run(['gh', 'api', f'repos/{REPO}/releases/latest'], capture_output=True)
        if latest.returncode == 0:
            rel = json.loads(latest.stdout)
            asset = next((a for a in rel['assets'] if a['name'] == 'update.json'), None)
            if asset:
                env = json.loads(run(['gh', 'api', asset['url'], '-H', 'Accept: application/octet-stream']))
                payload = json.loads(base64.b64decode(env['payload']))
                if payload['versionCode'] >= code:
                    if payload['versionCode'] == code and payload['versionName'] == version:
                        print('Versione già pubblicata: niente da fare.', flush=True)
                        return
                    raise SystemExit(f"versionCode {code} non è maggiore dell'ultima release ({payload['versionCode']})")

        tag = f'v{version}'
        exists = subprocess.run(['gh', 'api', f'repos/{REPO}/git/ref/tags/{tag}'], capture_output=True)
        assert exists.returncode != 0, f'Il tag {tag} esiste già: le release non vengono sovrascritte'
        apk_name = f'mithril-{version}.apk'
        assets = [apk_name, 'update.json', 'SHA256SUMS']
        digests = {n: hashlib.sha256((out / n).read_bytes()).hexdigest() for n in assets}
        run(['gh', 'release', 'create', tag, '--repo', REPO, '--target', commit, '--draft',
             '--title', f'Mithril {version}', '--notes-file', notes, *[out / n for n in assets]])
        draft = next(r for r in json.loads(run(['gh', 'api', f'repos/{REPO}/releases'])) if r['tag_name'] == tag)
        assert {a['name'] for a in draft['assets']} == set(assets)
        run(['gh', 'api', '--method', 'PATCH', f"repos/{REPO}/releases/{draft['id']}", '-F', 'draft=false', '-f', 'make_latest=true'])
        for name, digest in digests.items():
            url = f'https://github.com/{REPO}/releases/latest/download/{name}'
            for attempt in range(8):
                try:
                    with urllib.request.urlopen(url, timeout=60) as r:
                        if hashlib.sha256(r.read()).hexdigest() == digest:
                            break
                except Exception:
                    pass
                time.sleep(5)
            else:
                raise SystemExit(f'Asset pubblico non verificato: {name}')
        print(f'Pubblicata e verificata: https://github.com/{REPO}/releases/tag/{tag}', flush=True)


if __name__ == '__main__':
    main()
