#!/usr/bin/env python3
"""Circuit-board and cooling pixel assets, with material-based crafting recipes."""
from pathlib import Path
import json, math
from generate_fabrication_textures import Canvas, board
from generate_cable_textures import png
ROOT=Path(__file__).resolve().parents[1]
MAIN=ROOT/'src/main/resources'
AUTO=ROOT/'src/automation/resources'
def write(path,data):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
def asset(root,namespace,name,c):
    png(root/f'assets/{namespace}/textures/item/{name}.png',16,16,c.pixels)
    write(root/f'assets/{namespace}/models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'{namespace}:item/{name}'}})
def recipe(root,namespace,name,pattern,keys):
    write(root/f'data/{namespace}/recipe/{name}.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':pattern,'key':{k:{'item':v} for k,v in keys.items()},'result':{'id':f'{namespace}:{name}','count':1}})
    material='netlab:basic_circuit_board' if namespace=='netlab' else 'netlab:advanced_circuit_board'
    write(root/f'data/{namespace}/advancement/recipes/{name}.json',{'parent':'minecraft:recipes/root','criteria':{'has_material':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':material}]}},'has_the_recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipe':f'{namespace}:{name}'}}},'requirements':[['has_material','has_the_recipe']],'rewards':{'recipes':[f'{namespace}:{name}']}})
def language(root,namespace,locale,extra):
    path=root/f'assets/{namespace}/lang/{locale}.json';data=json.loads(path.read_text()) if path.exists() else {};data.update(extra);write(path,data)
def main():
    c=board();c.rect(2,3,13,12,(22,59,100));c.line([(2,5),(5,5),(5,3),(9,3)],(247,194,68));c.line([(3,11),(6,11),(6,9),(12,9)],(247,194,68));c.rect(6,5,10,8,(26,35,50));c.rect(7,6,9,7,(69,221,212));c.rect(10,10,12,11,(243,196,71));asset(MAIN,'netlab','advanced_circuit_board',c)
    recipe(MAIN,'netlab','advanced_circuit_board',['RGR','CBC','RGR'],{'R':'minecraft:redstone','G':'minecraft:gold_ingot','C':'netlab:ciscium_ingot','B':'netlab:basic_circuit_board'})
    for name in ['module_nl_server_accelerator','module_nl_server_buffer']:
        c=board();c.rect(2,3,13,12,(24,54,90));c.rect(3,5,7,10,(26,31,42));c.rect(4,6,6,9,(127,109,187) if 'accelerator' in name else (56,86,109));c.rect(9,5,12,10,(30,36,45));c.rect(10,6,11,9,(218,222,228) if 'accelerator' in name else (87,141,153));c.line([(2,4),(8,4),(8,11),(13,11)],(246,190,65));asset(AUTO,'netlabautomation',name,c)
    for pro in [False,True]:
        c=Canvas();c.rect(1,2,14,13,(106,70,42) if pro else (68,81,90))
        for x in range(2,15,2):c.rect(x,2,x,13,(228,140,67) if pro else (163,181,187))
        c.rect(3,3,12,12,(28,38,47));c.rect(4,4,11,11,(44,61,72))
        for a in range(8 if pro else 4):
            angle=2*math.pi*a/(8 if pro else 4);x=round(7.5+3*math.cos(angle));y=round(7.5+3*math.sin(angle));c.rect(x-1,y-1,x+1,y+1,(104,215,220) if pro else (127,150,161))
        c.rect(6,6,9,9,(229,171,70));c.rect(7,7,8,8,(46,56,65));asset(AUTO,'netlabautomation','module_nl_server_cooler'+('_pro' if pro else ''),c)
    A='netlab:advanced_circuit_board'
    recipe(AUTO,'netlabautomation','module_nl_server_accelerator',['QAQ','CRC','QAQ'],{'Q':'minecraft:quartz','A':A,'C':'minecraft:copper_ingot','R':'minecraft:repeater'})
    recipe(AUTO,'netlabautomation','module_nl_server_buffer',['AGA','CRC','AGA'],{'A':A,'G':'minecraft:gold_ingot','C':'minecraft:copper_ingot','R':'minecraft:comparator'})
    recipe(AUTO,'netlabautomation','module_nl_server_cooler',['ICI','CAC','IRI'],{'I':'minecraft:iron_ingot','C':'minecraft:copper_ingot','A':A,'R':'minecraft:redstone'})
    recipe(AUTO,'netlabautomation','module_nl_server_cooler_pro',['AGA','CFC','IGI'],{'A':A,'G':'minecraft:gold_ingot','C':'minecraft:copper_block','F':'netlabautomation:module_nl_server_cooler','I':'minecraft:iron_bars'})
    language(MAIN,'netlab','ru_ru',{'item.netlab.advanced_circuit_board':'Улучшенная плата CISCO'});language(MAIN,'netlab','en_us',{'item.netlab.advanced_circuit_board':'Advanced CISCO Circuit Board'})
    names={'module_nl_server_accelerator':('Плата ускорения сервера','Server Acceleration Board'),'module_nl_server_buffer':('Плата объёма сервера','Server Buffer Board'),'module_nl_server_cooler':('Обычный серверный кулер','Basic Server Cooler'),'module_nl_server_cooler_pro':('Улучшенный серверный кулер','Advanced Server Cooler')}
    for locale,index in [('ru_ru',0),('en_us',1)]:language(AUTO,'netlabautomation',locale,{'item.netlabautomation.'+k:v[index] for k,v in names.items()})
if __name__=='__main__':main()
