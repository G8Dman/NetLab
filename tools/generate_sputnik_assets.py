#!/usr/bin/env python3
"""Baked dish geometry and pixel assets in the established NetLab style."""
from pathlib import Path
import json,math
from generate_fabrication_textures import Canvas,board
from generate_cable_textures import png,icon
ROOT=Path(__file__).resolve().parents[1]/'src/sputnik/resources'
A=ROOT/'assets/netlabsputnik'
def write(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n')
def tex(name,c):png(A/f'textures/{name}.png',16,16,c.pixels)
def box(a,b,t='body'):return {'from':a,'to':b,'faces':{f:{'texture':'#'+t,'uv':[0,0,16,16]} for f in ['north','south','east','west','up','down']}}
def model(name,parts):
 d={'textures':{k:'netlabsputnik:block/'+k for k in ['body','face','panel','grille','copper']},'elements':parts};write(A/f'models/block/{name}.json',d)
 write(A/f'blockstates/{name}.json',{'variants':{f'facing={f}':{'model':'netlabsputnik:block/'+name,'y':y,'uvlock':True} for f,y in [('north',0),('east',90),('south',180),('west',270)]}})
 write(A/f'models/item/{name}.json',{'parent':'netlabsputnik:block/'+name,'display':{'gui':{'rotation':[22,225,0],'translation':[0,-2,0],'scale':[.35,.35,.35] if name=='dish' else [.6,.6,.6]},'ground':{'scale':[.25,.25,.25]},'firstperson_righthand':{'rotation':[0,45,0],'scale':[.4,.4,.4]}}})
 write(ROOT/f'data/netlabsputnik/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'netlabsputnik:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
def main():
 for name,color in [('body',(72,88,101)),('face',(25,44,57)),('panel',(210,222,226)),('copper',(180,115,54)),('grille',(26,32,38))]:
  c=Canvas()
  for y in range(16):
   for x in range(16):c.dot(x,y,tuple(max(0,min(255,v+((x*3+y*7)%9-4))) for v in color))
  if name=='grille':
   for y in range(1,16,3):c.rect(1,y,14,y,(7,15,23))
  if name=='face':
   c.rect(1,1,14,2,(83,195,211));c.rect(1,14,14,15,(11,28,37))
  tex('block/'+name,c)
 for name in ['hub','gateway','modem']:model(name,[box([0,0,1.6],[16,4,14]),box([.1,0,1.5],[15.9,4,1.6],'face')])
 model('odu',[box([1,0,2],[15,10,12]),box([1,0,1.6],[15,10,2],'face')]+[box([x,1,12],[x+.6,10,14],'copper') for x in range(2,15,2)])
 model('speaker',[box([2,0,2],[14,15,14]),box([3,3,1.6],[13,14,2],'grille'),box([3,0,1.5],[13,2.6,2],'face')])
 for name,size,center,cy in [('dish',2,8,17),('vsat_dish',1,8,13)]:
  radius=7*size;parts=[box([3,0,3],[13,2,14]),box([7,2,10],[9,cy,12]),box([2,0,1.5],[14,3,3],'face')]
  # Stepped circular reflector: shallow central bowl, deeper outer rim, visible feed arm.
  for yi in range(-int(radius),int(radius),2):
   half=math.sqrt(max(0,radius**2-(yi+1)**2));lo=center-half;hi=center+half
   depth=6+2*abs(yi)/max(1,radius);part=box([lo,cy+yi,depth],[hi,cy+yi+2,depth+1.1],'panel');part['rotation']={'origin':[8,cy,7],'axis':'x','angle':45,'rescale':False};parts.append(part)
  parts.extend([box([7,3,2.8],[9,cy+3,4],'body'),box([6,cy+2,2.5],[10,cy+5,5],'copper')]);model(name,parts)
 for n,accent in [('module_nl_sat_control',(82,209,205)),('module_nl_sat_tx',(227,181,71)),('module_nl_sat_rx',(104,158,231))]:
  c=board();c.rect(5,4,11,11,(20,34,44));c.rect(6,5,10,10,accent);tex('item/'+n,c);write(A/f'models/item/{n}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'netlabsputnik:item/'+n}})
 for i,n in enumerate(['coax_tx','coax_rx','sat_rf_tx','sat_rf_rx','audio']):
  pixels=icon('console' if n=='audio' else 'copper',(70,75,80) if n=='audio' else (208,205,166))
  for cx,cy in [(26,6),(5,26)]:
   for yy in range(max(0,cy-6),min(32,cy+7)):
    for xx in range(max(0,cx-6),min(32,cx+7)):pixels[yy][xx]=(0,0,0,0)
   for yy in range(max(0,cy-4),min(32,cy+5)):
    for xx in range(max(0,cx-4),min(32,cx+5)):
     distance=math.hypot(xx-cx,yy-cy)
     if distance<=4:pixels[yy][xx]=(*( (226,193,105) if distance<1.3 else (37,46,53) if distance<2.7 else (180,193,199)),255)
  png(A/f'textures/item/{n}_cable.png',32,32,pixels);c=Canvas()
  for y in range(16):
   for x in range(16):v=135+(25 if x%4==0 else 0)+(15 if y%8==0 else 0);c.dot(x,y,(v,v,v))
  tex('cable/'+n,c);write(A/f'models/item/{n}_cable.json',{'parent':'minecraft:item/generated','textures':{'layer0':'netlabsputnik:item/'+n+'_cable'}})
 ru={'hub':'Спутниковый HUB провайдера','gateway':'Интернет-шлюз Sputnik','odu':'Радиоблок BUC / LNB','dish':'Антенна провайдера Sputnik','vsat_dish':'Абонентская антенна VSAT','modem':'Абонентский модем VSAT','speaker':'Колонка Sputnik'}
 names={'module_nl_sat_control':'Модуль управления SAT-CONTROL','module_nl_sat_tx':'Модуль передачи SAT-TX','module_nl_sat_rx':'Модуль приёма SAT-RX','coax_tx_cable':'Коаксиальный кабель TX','coax_rx_cable':'Коаксиальный кабель RX','sat_rf_tx_cable':'Радиокабель BUC → антенна','sat_rf_rx_cable':'Радиокабель антенна → LNB','audio_cable':'Аудиокабель 3,5 мм'}
 write(A/'lang/ru_ru.json',{**{'block.netlabsputnik.'+k:v for k,v in ru.items()},**{'item.netlabsputnik.'+k:v for k,v in names.items()}})
 en={'hub':'Provider Satellite HUB','gateway':'Sputnik Internet Gateway','odu':'BUC / LNB Radio Unit','dish':'Sputnik Gateway Dish','vsat_dish':'VSAT Subscriber Dish','modem':'VSAT Subscriber Modem','speaker':'Sputnik Speaker'}
 write(A/'lang/en_us.json',{**{'block.netlabsputnik.'+k:v for k,v in en.items()},**{'item.netlabsputnik.'+k:k.replace('_',' ').title() for k in names}})
if __name__=='__main__':main()
