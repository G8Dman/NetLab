#!/usr/bin/env python3
"""Produce book sections from the same device-by-device Markdown shipped to users."""
from pathlib import Path
import json,re
ROOT=Path(__file__).resolve().parents[1]
for filename,title,section,order in [('NETLAB-IPTV.md','IPTV','iptv',60),('NETLAB-IP-TELEPHONY.md','IP Телефония','ip-telephony',61)]:
 text=(ROOT/'docs'/filename).read_text();parts=re.split(r'^## ',text,flags=re.M);articles=[]
 for index,part in enumerate(parts[1:],1):
  head,body=part.split('\n',1)
  body=body.replace('```text\n','').replace('```','').strip()
  articles.append({'id':f'{section}-{index}','title':head.strip(),'body':body})
 p=ROOT/'src/media/resources/assets/netlabmedia/netlab_guide'/f'{section}.json';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps({'id':'netlabmedia:'+section,'title':title,'order':order,'articles':articles},ensure_ascii=False,indent=2)+'\n')
 print(title,len(articles),'chapters')
