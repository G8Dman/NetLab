#!/usr/bin/env python3
"""Deterministic Academy catalog/native advancement data from the reviewed specification."""
from pathlib import Path
import json,re
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'src/academy/resources'
GROUPS={'B':('base','Начало и производство','Блоки → Цискиум, базовая плата, сборщик модулей'), 'P':('physical','Шкаф и питание','Блоки → Шкаф, питание, кабели и модули'), 'C':('console','Консоль','Команды → Режимы, адреса, сохранение'), 'L':('lan','Локальная сеть','Команды → Коммутатор, VLAN, STP'), 'R':('routing','Маршрутизация','Команды → Маршрутизатор, адреса, маршруты'), 'W':('wifi','Wi-Fi','Блоки → Wi-Fi'), 'D':('diagnostics','Диагностика','Блоки → Очки, контроллер трафика'), 'A':('automation','Автоматика','Автоматика'), 'M':('mobile','Мобильная связь','Мобильная связь'), 'S':('sputnik','Спутниковая связь','Спутниковая связь'), 'T':('iptv','IPTV','IPTV'), 'V':('telephony','IP-телефония','IP Телефония'), 'X':('combined','Совместные задания','Разделы соответствующих дополнений'), 'E':('extra','Интеграции и секреты','Блоки и Автоматика'), 'F':('finals','Выпуск','Обучение → Основной маршрут')}
MAIN='B01 B02 B03 B04 B05 B07 B08 B09 B10 B15 B16 P01 P03 P04 P05 P06 P08 C01 C02 C03 C04 C06 C09 L01 L02 L03 D01 R01 R02'.split()
def mods(i):
 p=i[0];m=['netlab']
 if p in 'AMSTV':m+=dict(A=['netlabautomation'],M=['netlabmobile'],S=['netlabsputnik'],T=['netlabsputnik','netlabmedia','voicechat'],V=['netlabsputnik','netlabmedia','voicechat'])[p]
 if p=='X':m+=dict(X01=['netlabmobile','netlabautomation'],X02=['netlabmobile','netlabautomation'],X03=['netlabmobile','netlabsputnik'],X04=['netlabmobile','netlabmedia','netlabsputnik','voicechat'],X05=['netlabmobile','netlabmedia','netlabsputnik','voicechat'],X06=['netlabmobile','netlabmedia','netlabsputnik','voicechat'],X07=['netlabautomation','netlabsputnik'],X08=['netlabautomation','netlabsputnik'],X09=['netlabmedia','netlabsputnik','voicechat'],X10=['netlabmedia','netlabsputnik','voicechat'])[i]
 if i in ('E01','E02','E03'):m+=dict(E01=['carryon'],E02=['minecolonies'],E03=['netlabautomation','ae2'])[i]
 return m

def generate():
 entries=[]
 for line in (ROOT/'docs/NETLAB-ACHIEVEMENTS.md').read_text().splitlines():
  cells=[s.strip() for s in line.split('|')]
  if len(cells)!=6 or not re.fullmatch('[A-Z][0-9]{2}',cells[1]):continue
  i,title,task,parents=cells[1:5];g,name,guide=GROUPS[i[0]]
  entries.append(dict(id=i,path=f'{g}/{i.lower()}',title=title,task=task,parents=re.findall('[A-Z][0-9]{2}',parents),mods=mods(i),group=g,groupTitle=name,guide=guide,main=i in MAIN))
 assert len(entries)==215 and len({e['id'] for e in entries})==215
 ids={e['id']:e for e in entries}
 visited=set()
 def visit(i,stack=()):
  assert i not in stack,stack
  if i in visited:return
  for p in ids[i]['parents']:assert p in ids;visit(p,(*stack,i))
  visited.add(i)
 for i in ids:visit(i)
 def write(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n')
 write(OUT/'assets/netlabacademy/catalog.json',dict(version=1,main=MAIN,entries=entries))
 # Actual conditions remove absent branches at datapack loading, not merely hidden icons.
 def condition(ms):return [{'type':'neoforge:mod_loaded','modid':m} for m in ms if m!='netlab']
 def display(title,description,root=False):
  d=dict(icon=dict(id='minecraft:book' if root else 'minecraft:paper'),title=dict(text=title),description=dict(text=description),frame='task',show_toast=not root,announce_to_chat=False,hidden=False)
  if root:d['background']='minecraft:textures/gui/advancements/backgrounds/stone.png'
  return d
 # Retire the separate tabs, retaining every actual task ID and its saved evidence.
 for g,name,guide in GROUPS.values():
  (OUT/f'data/netlabacademy/advancement/{g}/root.json').unlink(missing_ok=True)
 root=dict(display=display('NetLab','Единое дерево обучения. Библия по CISCO → Обучение',True),criteria=dict(course=dict(trigger='minecraft:impossible')))
 write(OUT/'data/netlabacademy/advancement/root.json',root)
 for e in entries:
  # Connect courses through their actual prerequisites, across section boundaries.
  # Minecraft draws one parent; the server still enforces ALL logical parents.
  parent=ids[e['parents'][0]]['path'] if e['parents'] else 'root'
  d=dict(parent='netlabacademy:'+parent,display=display(e['title'],e['groupTitle']+'\n'+e['task']),criteria=dict(evidence=dict(trigger='minecraft:impossible')))
  if e['mods'][1:]:d['neoforge:conditions']=condition(e['mods'])
  if e['id']=='E03':d['neoforge:conditions'].append({'type':'netlabacademy:ae2_policy'})
  write(OUT/f"data/netlabacademy/advancement/{e['path']}.json",d)
 print(f'Academy: {len(entries)} achievements in one connected tree, {len(MAIN)} main steps')
if __name__=='__main__':generate()
