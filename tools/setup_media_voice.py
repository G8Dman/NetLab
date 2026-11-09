#!/usr/bin/env python3
"""Download only the verified compile API. Does not install/start Minecraft or a microphone."""
from pathlib import Path
import urllib.request,hashlib,os
ROOT=Path(__file__).resolve().parents[1]
URL='https://maven.maxhenkel.de/repository/public/de/maxhenkel/voicechat/voicechat-api/2.6.0/voicechat-api-2.6.0.jar'
# Filled from the official Maven artifact; never bundle the API into the addon.
SHA256='4e7f7a9c8e318a7ce6aea12bac030975d14db7a298fdd1511d61d66c6e634c43'
def main():
 p=ROOT/'.tools/media/voicechat-api-2.6.0.jar';p.parent.mkdir(parents=True,exist_ok=True)
 data=p.read_bytes() if p.exists() else urllib.request.urlopen(URL,timeout=30).read(500000)
 if hashlib.sha256(data).hexdigest()!=SHA256:raise SystemExit('Simple Voice Chat API checksum mismatch')
 temp=p.with_suffix('.pending');temp.write_bytes(data);os.replace(temp,p)
 print('Simple Voice Chat 2.6.0 compile API verified')
if __name__=='__main__':main()
