#!/usr/bin/env python3
"""Media equipment geometry, models, recipes and localized names. Uses existing NetLab materials."""
from pathlib import Path
import json
from generate_addon_recipes import recipe
ROOT=Path(__file__).resolve().parents[1]/'src/media/resources';A=ROOT/'assets/netlabmedia'
def write(path,data):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
def box(lo,hi,texture='body'):
 return {'from':lo,'to':hi,'faces':{f:{'texture':'#'+texture,'uv':[0,0,16,16]} for f in ('north','south','east','west','up','down')}}
def model(name,parts):
 write(A/f'models/block/{name}.json',{'textures':{'particle':'netlabsputnik:block/body','body':'netlabsputnik:block/body','face':'netlabsputnik:block/face','grille':'netlabsputnik:block/grille','black':'minecraft:block/black_concrete','light':'minecraft:block/light_gray_concrete','display':'minecraft:block/cyan_concrete','key':'minecraft:block/white_concrete'},'elements':parts})
 write(A/f'blockstates/{name}.json',{'variants':{f'facing={f}':{'model':'netlabmedia:block/'+name,'y':r,'uvlock':True} for f,r in [('north',0),('east',90),('south',180),('west',270)]}})
 write(A/f'models/item/{name}.json',{'parent':'netlabmedia:block/'+name,'display':{'gui':{'rotation':[25,225,0],'translation':[0,-2,0],'scale':[.65,.65,.65]},'ground':{'scale':[.3,.3,.3]},'firstperson_righthand':{'rotation':[0,45,0],'scale':[.5,.5,.5]}}})
 write(ROOT/f'data/netlabmedia/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'netlabmedia:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
def main():
 for name in ['headend','pbx','stb','audio_splitter']:model(name,[box([0,0,1.6],[16,4,14]),box([0,0,1.5],[16,3.7,1.6],'face'),box([.2,3.7,1.5],[15.8,4,1.6],'light')])
 model('tv',[box([0,2,1],[16,16,4],'light'),box([.6,4.2,.99],[15.4,13,.995],'black'),box([7,0,2],[9,3,5]),box([3,0,1],[13,.5,7]),box([0,0,1.5],[16,2.5,2],'face')])
 phone=[box([1,0,2],[15,3,14]),box([1,0,1.5],[15,2,2],'face'),box([6,3,7],[14,6,13],'light'),box([6.5,6.01,8],[13.5,6.03,12],'display'),box([1.3,3.2,3],[4,7,13],'black'),box([1,3.1,2],[4.3,7.1,5],'black'),box([1,3.1,10],[4.3,7.1,14],'black')]
 for row in range(4):
  for col in range(3):phone.append(box([6+col*2.4,3.01,2.5+row*1.1],[7.8+col*2.4,3.45,3.2+row*1.1],'key'))
 model('phone',phone)
 model('speaker',[box([2,0,2],[14,15,14]),box([3,3,1.6],[13,14,2],'grille'),box([2,0,1.5],[14,2.5,2],'face')])
 for name in ['module_nl_media_video','module_nl_media_sessions','phone_sip_module']:
  write(A/f'models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'netlab:item/advanced_circuit_board'}})
 for kind in ['audio','hdmi']:
  write(A/f'models/item/{kind}_cable.json',{'parent':'minecraft:item/generated','textures':{'layer0':'netlabsputnik:item/audio_cable' if kind=='audio' else 'netlab:item/routed_fiber'}})
 ru={'headend':'Медиасервер IPTV','pbx':'Сервер IP-АТС','stb':'IPTV-приставка','tv':'Телевизор NetLab','phone':'IP-телефон Cisco','audio_splitter':'Аудиоразветвитель 1→4','speaker':'Колонка Media'}
 items={'module_nl_media_video':'Плата каналов IPTV — NL-MEDIA-VIDEO','module_nl_media_sessions':'Плата ёмкости АТС — NL-MEDIA-SESSIONS','phone_sip_module':'Модуль IP-телефонии Cisco Pocket','audio_cable':'Аудиокабель 3,5 мм','hdmi_cable':'Видеокабель HDMI'}
 write(A/'lang/ru_ru.json',{**{'block.netlabmedia.'+k:v for k,v in ru.items()},**{'item.netlabmedia.'+k:v for k,v in items.items()}})
 write(A/'lang/en_us.json',{**{'block.netlabmedia.'+k:k.replace('_',' ').title() for k in ru},**{'item.netlabmedia.'+k:k.replace('_',' ').title() for k in items}})
 I='minecraft:iron_ingot';C='minecraft:copper_ingot';G='minecraft:gold_ingot';R='minecraft:redstone';Aboard='netlab:advanced_circuit_board';B='netlab:basic_circuit_board';M='netlab:cisco_memory_card';Q='minecraft:quartz';L='minecraft:glass_pane'
 for n,pattern,key,count in [
 ('headend',['IQL','AMA','IGI'],{'I':I,'Q':Q,'L':L,'A':Aboard,'M':M,'G':G},1),
 ('pbx',['IQI','AMA','CGC'],{'I':I,'Q':Q,'A':Aboard,'M':M,'C':C,'G':G},1),
 ('stb',['ILI','CBC','IRI'],{'I':I,'L':L,'C':C,'B':B,'R':R},1),
 ('tv',['ILI','LAL','CRC'],{'I':I,'L':'minecraft:glass','A':Aboard,'C':C,'R':R},1),
 ('phone',['ILI','CBC','QRQ'],{'I':I,'L':L,'C':C,'B':B,'Q':Q,'R':R},1),
 ('audio_splitter',['CRC','CBC','CRC'],{'C':C,'R':R,'B':B},1),
 ('speaker',['PPP','CNC','PBP'],{'P':'minecraft:oak_planks','C':C,'N':'minecraft:note_block','B':B},1),
 ('module_nl_media_video',['GLG','QAQ','GRG'],{'G':G,'L':L,'Q':Q,'A':Aboard,'R':'minecraft:redstone_block'},1),
 ('module_nl_media_sessions',['GQG','AMA','GRG'],{'G':G,'Q':Q,'A':Aboard,'M':M,'R':R},1),
 ('phone_sip_module',[' Q ','BMB',' G '],{'Q':Q,'B':Aboard,'M':M,'G':G},1),
 ('audio_cable',['CSC',' G '],{'C':C,'S':'minecraft:string','G':'minecraft:gold_nugget'},6),
 ('hdmi_cable',['GQG','CSC'],{'G':'minecraft:gold_nugget','Q':Q,'C':C,'S':'minecraft:string'},6)]:recipe('media',n,pattern,key,count)
 print('Generated 7 Media device models, 5 item models, and 12 survival recipes.')
if __name__=='__main__':main()
