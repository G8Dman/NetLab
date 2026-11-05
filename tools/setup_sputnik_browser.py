#!/usr/bin/env python3
"""Fetch the pinned, verified Rinku API used to compile Sputnik. Does not start a browser/game."""
from pathlib import Path
from zipfile import ZipFile
import hashlib, urllib.request, os
ROOT=Path(__file__).resolve().parents[1]
DEST=ROOT/'.tools/sputnik'
URL='https://cdn.modrinth.com/data/bQhBuv7x/versions/JOrwMXrE/rinku_neoforge_3.0.4_MC_1.21.1.jar'
SHA512='a8a1062caeb039a8bf31bf9cf5c4b05c4927b686eb96ed9a6fad9bc044b402af2d15fa610b09ea08753397c48940435d72e4e41f659993a6233663e221a6af9e'
API=DEST/'de.keksuccino.rinku-neoforge-3.0.4-1.21.1-mod.jar'
def main():
 DEST.mkdir(parents=True,exist_ok=True)
 outer=DEST/'rinku_neoforge_3.0.4_MC_1.21.1.jar'
 if outer.exists():data=outer.read_bytes()
 else:
  with urllib.request.urlopen(URL,timeout=30) as response:data=response.read(2_000_000)
 if hashlib.sha512(data).hexdigest()!=SHA512:raise SystemExit('Rinku checksum mismatch')
 temp=outer.with_suffix('.pending');temp.write_bytes(data);os.replace(temp,outer)
 with ZipFile(outer) as archive:api=archive.read('META-INF/jarjar/'+API.name)
 if not API.exists() or API.read_bytes()!=api:
  temp=API.with_suffix('.pending');temp.write_bytes(api);os.replace(temp,API)
 print('Rinku 3.0.4 compile API verified')
if __name__=='__main__':main()
