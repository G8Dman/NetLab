#!/usr/bin/env python3
"""Populate Prism's library cache from its logged downloads, checking SHA-1.
Copies matching Gradle artifacts first; downloads remaining files via curl.
Checksummed files alone do not populate Prism's metacache index. See
docs/PRISM-LAUNCH-FIX.md for the completed repair and backup location.
Never changes launcher accounts, network settings, or instance metadata.
"""
from pathlib import Path
import re, hashlib, shutil, subprocess, concurrent.futures, json, time
root=Path.home()/'.local/share/PrismLauncher'
log='\n'.join(p.read_text(errors='replace') for p in sorted((root/'logs').glob('PrismLauncher-*.log'),reverse=True))
pattern=r'Checksummed Download for: "([^"]+)" storage: "([^"]+)" url: "([^"]+)" expected sha1: "([0-9a-f]+)"'
entries={x[1]:x for x in re.findall(pattern,log)}
cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
local={p.parent.name:p for p in cache.rglob('*') if p.is_file() and len(p.parent.name)==40}
report=[]
def sha(path):return hashlib.sha1(path.read_bytes()).hexdigest()
def install(entry):
    name,rel,url,expected=entry
    if '..' in Path(rel).parts or Path(rel).is_absolute():raise ValueError('Unsafe path')
    destination=root/'libraries'/rel
    if destination.exists() and sha(destination)==expected:return (name,'already valid')
    destination.parent.mkdir(parents=True,exist_ok=True)
    temp=destination.with_name(destination.name+'.netlab-download')
    candidate=local.get(expected)
    if candidate and sha(candidate)==expected:
        shutil.copy2(candidate,temp);source='Gradle cache'
    else:
        if not url.startswith('https://'):raise ValueError('HTTPS required')
        repository_path=url.split('/releases/',1)[-1]
        alternatives=[url]
        if url.startswith('https://maven.neoforged.net/releases/'):
            alternatives = [
                ('https://mirrors.qlu.edu.cn/bmclapi/' if repository_path.startswith(('net/neoforged/','cpw/mods/')) else 'https://repo.huaweicloud.com/repository/maven/')+repository_path,
                url,
            ]
        errors=[]
        for candidate_url in alternatives:
            proc=subprocess.run(['curl','-4','-fsSL','--connect-timeout','15','--max-time','120','--retry','2',candidate_url,'-o',str(temp)],capture_output=True,text=True)
            if not proc.returncode and sha(temp)==expected:break
            errors.append(proc.stderr.strip()[-100:] or 'Checksum mismatch')
        else:return (name,'FAILED: '+'; '.join(errors))
        source='download'
    if sha(temp)!=expected:
        temp.unlink();return (name,'FAILED: checksum mismatch')
    if destination.exists():
        backup=root/'netlab-repair-backup'/str(int(time.time()))/rel
        backup.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(destination,backup)
    temp.replace(destination)
    return (name,source)
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    for name,result in pool.map(install,entries.values()):
        report.append({'library':name,'result':result})
        if result!='already valid':print(name,result,flush=True)
Path('docs/prism-library-repair.json').write_text(json.dumps(report,indent=2))
failed=[r for r in report if r['result'].startswith('FAILED')]
print(f'Checked {len(report)} libraries; failed: {len(failed)}',flush=True)
raise SystemExit(bool(failed))
