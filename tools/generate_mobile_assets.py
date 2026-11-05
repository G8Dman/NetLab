#!/usr/bin/env python3
"""Native pixel icons/textures and baked geometry following NetLab's existing asset system."""
from pathlib import Path
import json
from generate_fabrication_textures import Canvas, metal, board
from generate_cable_textures import png, icon

ROOT=Path(__file__).resolve().parents[1]/'src/mobile/resources'
A=ROOT/'assets/netlabmobile'
def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
def texture(name,c):png(A/f'textures/{name}.png',16,16,c.pixels)
def box(lo,hi,tex='body'):
    return {'from':lo,'to':hi,'faces':{f:{'texture':'#'+tex} for f in ['north','south','east','west','up','down']}}
def faceplate(lo,hi):
    result=box(lo,hi,'face')
    result['faces']['north']['uv']=[0,0,16,16]
    return result
def model(name,elements):
    write(A/f'models/block/{name}.json',{'parent':'minecraft:block/block','textures':{'body':'netlabmobile:block/body','face':f'netlabmobile:block/{name}','panel':'netlabmobile:block/panel','particle':'netlabmobile:block/body'},'elements':elements,'display':{'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.85,.85,.85]}}})
    variants={}
    for face,angle in [('north',0),('east',90),('south',180),('west',270)]:variants[f'facing={face}']={'model':f'netlabmobile:block/{name}','y':angle,'uvlock':True}
    write(A/f'blockstates/{name}.json',{'variants':variants})
    write(A/f'models/item/{name}.json',{'parent':f'netlabmobile:block/{name}'})
    write(ROOT/f'data/netlabmobile/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'netlabmobile:{name}'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})

def main():
    body=metal()
    for y in range(1,15):
        for x in range(1,15):
            r,g,b,a=body.pixels[y][x];body.pixels[y][x]=(r+28,g+30,b+30,a)
    texture('block/body',body)
    panel=Canvas();panel.rect(0,0,15,15,(192,204,201));panel.rect(1,1,14,14,(222,231,224));panel.rect(3,0,3,15,(173,192,188));panel.rect(12,0,12,15,(173,192,188));texture('block/panel',panel)
    for name,accent in [('sector_antenna',(79,203,192)),('radio_unit',(70,196,227)),('baseband_unit',(67,209,162)),('mobile_core',(100,148,227)),('sim_station',(224,173,72))]:
        c=Canvas();c.rect(0,0,15,15,(62,81,91));c.rect(0,0,15,0,(150,175,183));c.rect(0,15,15,15,(26,41,49));c.rect(1,1,14,2,accent)
        c.rect(1,5,14,14,(28,45,55))
        for x in range(2,15,3):c.rect(x,3,x+1,3,(131,154,162))
        for x,y in [(0,0),(15,0),(0,15),(15,15)]:c.dot(x,y,(205,219,218))
        texture('block/'+name,c)
    antenna_lower=[box([3,0,2],[13,16,5],'panel'),box([6,3,5],[10,5,16]),box([2,0,1.5],[14,2.8,2],'face')]
    antenna_upper=[box([3,0,2],[13,16,5],'panel'),box([6,9,5],[10,11,16])]
    model('sector_antenna',antenna_lower)
    upper_model=json.loads((A/'models/block/sector_antenna.json').read_text());upper_model['elements']=antenna_upper
    write(A/'models/block/sector_antenna_upper.json',upper_model)
    variants={}
    for facing,angle in [('north',0),('east',90),('south',180),('west',270)]:
        for half,suffix in [('lower',''),('upper','_upper')]:variants[f'facing={facing},half={half}']={'model':'netlabmobile:block/sector_antenna'+suffix,'y':angle,'uvlock':True}
    write(A/'blockstates/sector_antenna.json',{'variants':variants})
    item_model=json.loads((A/'models/block/sector_antenna.json').read_text());item_model['elements']=json.loads(json.dumps(antenna_lower))
    for part in antenna_upper:
        element=json.loads(json.dumps(part));element['from'][1]+=16;element['to'][1]+=16;item_model['elements'].append(element)
    for element in item_model['elements']:
        for face in element['faces'].values():face['uv']=[0,0,16,16]
    item_model['display']={'gui':{'rotation':[20,225,0],'translation':[0,-4,0],'scale':[.45,.45,.45]},'ground':{'translation':[0,2,0],'scale':[.25,.25,.25]},'fixed':{'scale':[.5,.5,.5]},'thirdperson_righthand':{'rotation':[75,45,0],'scale':[.3,.3,.3]},'firstperson_righthand':{'rotation':[0,45,0],'translation':[0,0,0],'scale':[.4,.4,.4]}}
    write(A/'models/item/sector_antenna.json',item_model)
    write(ROOT/'data/netlabmobile/loot_table/blocks/sector_antenna.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'netlabmobile:sector_antenna'}],'conditions':[{'condition':'minecraft:block_state_property','block':'netlabmobile:sector_antenna','properties':{'half':'lower'}}]}]})
    rru=[box([1,0,2],[15,12,12]),faceplate([1,0,1.5],[15,12,2])]
    rru += [box([x,4,12],[x+.5,12,14]) for x in range(2,15,2)]
    model('radio_unit',rru)
    for name in ['baseband_unit','mobile_core']:model(name,[box([0,0,1.6],[16,4,14]),faceplate([.2,0,1.5],[15.8,4,1.6])])
    model('sim_station',[box([1,0,1.6],[15,8,14]),faceplate([1,0,1.5],[15,8,1.6]),box([3,8,5],[13,9,12],'face')])
    for name,active in [('blank_sim',False),('sim_card',True)]:
        c=Canvas();c.rect(4,1,11,14,(221,228,225));c.rect(3,2,12,14,(221,228,225));c.rect(4,5,11,11,(209,161,69));c.rect(5,6,10,10,(244,210,122));c.line([(7,5),(7,11)],(145,105,48));c.line([(4,8),(11,8)],(145,105,48));c.rect(4,2,10,3,(41,170,156) if active else (119,140,149));texture('item/'+name,c)
    c=Canvas();c.rect(4,0,11,15,(16,30,38));c.rect(3,1,12,14,(29,51,63));c.rect(4,3,11,11,(48,144,156));c.rect(5,2,9,2,(142,177,187));c.rect(7,13,8,13,(133,219,192))
    for i in range(4):c.rect(5+i*2,9-i,5+i*2,10,(171,246,198))
    texture('item/cisco_pocket',c)
    for i,name in enumerate(['nl_mob_control','nl_mob_baseband','nl_mob_fronthaul','cisco_lte_modem']):
        c=board();c.rect(6,6,10,9,[(68,127,217),(77,186,146),(224,170,62),(162,110,219)][i]);texture('item/module_'+name,c)
    rf=icon('copper',(177,185,194));png(A/'textures/item/rf_cable.png',32,32,rf)
    c=Canvas();c.rect(0,0,15,15,(170,170,170));c.rect(0,0,15,2,(232,232,232));c.rect(7,0,8,15,(117,117,117));texture('cable/rf',c)
    names={'sector_antenna':'Секторная антенна LTE','radio_unit':'Радиомодуль RRU','baseband_unit':'Блок обработки BBU','mobile_core':'Ядро мобильной сети EPC','sim_station':'Станок выпуска SIM'}
    ru={f'block.netlabmobile.{k}':v for k,v in names.items()}
    for k,v in {'blank_sim':'Пустая SIM-карта','sim_card':'SIM-карта оператора','cisco_pocket':'Cisco Pocket','rf_cable':'Антенный RF-кабель','module_nl_mob_control':'NL-MOB-CONTROL','module_nl_mob_baseband':'NL-MOB-BASEBAND','module_nl_mob_fronthaul':'NL-MOB-FRONTHAUL','module_cisco_lte_modem':'CISCO-LTE-MODEM'}.items():
        ru['item.netlabmobile.'+k]=v;write(A/f'models/item/{k}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'netlabmobile:item/'+k}})
    write(A/'lang/ru_ru.json',ru);write(A/'lang/en_us.json',{**{f'block.netlabmobile.{k}':v for k,v in zip(names,['LTE Sector Antenna','Remote Radio Unit (RRU)','Baseband Unit (BBU)','Mobile Core (EPC)','SIM Provisioning Station'])},**{k:v for k,v in ru.items() if k.startswith('item.')}})
    write(ROOT/'data/minecraft/tags/block/mineable/pickaxe.json',{'replace':False,'values':['netlabmobile:'+x for x in names]})
if __name__=='__main__':main()
