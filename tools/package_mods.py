#!/usr/bin/env python3
"""Copy only current NetLab JARs into the repository's ready-to-install mods/ folder."""
from pathlib import Path
from zipfile import ZipFile
import hashlib
import json
import os
import re
import shutil

ROOT = Path(__file__).resolve().parents[1]


def text_value(text, key):
    match = re.search(r'^' + re.escape(key) + r'\s*=\s*"?([^"\r\n]+)', text, re.M)
    if not match:
        raise ValueError('Missing ' + key)
    return match.group(1).strip()


def main():
    specs = [('netlab', 'netlab', text_value((ROOT / 'gradle.properties').read_text(), 'mod_version'))]
    for name in ('automation', 'mobile', 'sputnik', 'media'):
        metadata = (ROOT / 'src' / name / 'resources/META-INF/neoforge.mods.toml').read_text()
        specs.append(('netlab' + name, 'netlab-' + name, text_value(metadata, 'version')))
    output = ROOT / 'mods'
    output.mkdir(exist_ok=True)
    entries = []
    for mod_id, prefix, version in specs:
        filename = prefix + '-' + version + '.jar'
        source = ROOT / 'build/libs' / filename
        with ZipFile(source) as archive:
            metadata = archive.read('META-INF/neoforge.mods.toml').decode()
            if text_value(metadata, 'modId') != mod_id or text_value(metadata, 'version') != version:
                raise ValueError('Unexpected mod identity in ' + filename)
            if any(n.lower().endswith(('.dll', '.so', '.exe', '.dylib')) or n.startswith('META-INF/jarjar/') for n in archive.namelist()):
                raise ValueError('Unexpected bundled runtime/dependency in ' + filename)
        target = output / filename
        pending = target.with_suffix('.pending')
        shutil.copyfile(source, pending)
        if hashlib.sha256(pending.read_bytes()).digest() != hashlib.sha256(source.read_bytes()).digest():
            raise ValueError('Copy verification failed: ' + filename)
        os.replace(pending, target)
        entries.append({'id': mod_id, 'version': version, 'file': 'mods/' + filename,
                        'bytes': target.stat().st_size, 'sha256': hashlib.sha256(target.read_bytes()).hexdigest()})
    expected = {Path(e['file']).name for e in entries}
    # Remove only obsolete builds of our known mods; never silently remove foreign files.
    for p in output.iterdir():
        if p.name not in expected:
            if p.is_file() and re.fullmatch(r'netlab(?:-automation|-mobile|-sputnik|-media)?-[0-9][A-Za-z0-9.+_-]*\.jar', p.name):
                p.unlink()
            else:
                raise ValueError('Unexpected file in ready-to-install folder: ' + p.name)
    manifest = {'minecraft': '1.21.1', 'neoforge': '21.1.265', 'java': 21,
                'platforms': ['Windows 10/11 x64', 'Linux x64'], 'mods': entries,
                'external_dependencies': [{'id': 'rinku', 'version': '3.0.4', 'loader': 'NeoForge',
                    'minecraft': '1.21.1', 'side': 'client', 'required_by': 'netlabsputnik',
                    'url': 'https://modrinth.com/mod/rinku/version/JOrwMXrE', 'included': False},
                    {'id': 'voicechat', 'version': '1.21.1-2.6.27', 'loader': 'NeoForge',
                    'minecraft': '1.21.1', 'side': 'both', 'required_by': 'netlabmedia',
                    'url': 'https://modrinth.com/plugin/simple-voice-chat/version/iR08ksVF',
                    'server_udp_port': 24454, 'included': False}],
                'optional_integrations': [
                    {'id': 'carryon', 'tested_version': '2.2.6.13', 'feature': 'Carry a disconnected NetLab rack with installed devices', 'included': False},
                    {'id': 'minecolonies', 'tested_version': '1.1.1414', 'feature': 'Vkusno i tochka restaurant/cookery style, levels 1-5', 'included': False,
                     'dependencies': ['structurize', 'blockui', 'domum_ornamentum', 'multipiston'],
                     'installation': 'https://minecolonies.com/wiki/installation/'}]}
    (ROOT / 'MODS.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (ROOT / 'SHA256SUMS.txt').write_text(''.join(e['sha256'] + '  ' + e['file'] + '\n' for e in entries), encoding='utf-8')
    print('Ready-to-install folder: mods/ (exactly five NetLab JARs, no third-party mods)')


if __name__ == '__main__':
    main()
