#!/usr/bin/env python3
"""Native 16px Ciscium, circuit board and assembler textures from pixel geometry."""
from pathlib import Path
import json
import random
from generate_cable_textures import png

ROOT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/netlab'

class Canvas:
    def __init__(self, background=(0,0,0,0)):
        self.pixels=[[background for _ in range(16)] for _ in range(16)]
    def dot(self,x,y,color):
        if 0<=x<16 and 0<=y<16:self.pixels[y][x]=(*color,255)
    def rect(self,x0,y0,x1,y1,color):
        for y in range(y0,y1+1):
            for x in range(x0,x1+1):self.dot(x,y,color)
    def line(self,points,color):
        for a,b in zip(points,points[1:]):
            steps=max(abs(b[0]-a[0]),abs(b[1]-a[1]),1)
            for i in range(steps+1):self.dot(round(a[0]+(b[0]-a[0])*i/steps),round(a[1]+(b[1]-a[1])*i/steps),color)

def ore(deep=False):
    c=Canvas();r=random.Random(730 if deep else 731)
    for y in range(16):
        for x in range(16):
            base=(56 if deep else 117)+r.randrange(-16,17)
            if deep:base-=7 if y%4==3 else 0
            c.dot(x,y,(base,base+1,base+3))
    for x,y in [(3,3),(10,2),(7,8),(12,11),(2,12)]:
        c.rect(x-1,y,x+2,y+2,(32,66,89));c.rect(x,y,x+1,y+1,(54,183,203));c.dot(x,y,(159,248,231));c.dot(x+1,y+2,(81,95,194));c.dot(x+2,y,(85,220,221))
    return c

def raw():
    c=Canvas()
    for x,y in [(4,5),(8,8),(10,4)]:
        c.rect(x-2,y,x+2,y+5,(28,68,90));c.rect(x-1,y-1,x+1,y+4,(59,166,186));c.rect(x,y-1,x+1,y+2,(122,235,221));c.dot(x+2,y+4,(99,104,198))
    c.line([(3,12),(6,14),(11,12),(13,8)],(83,103,126));return c

def ingot():
    c=Canvas();c.rect(2,6,13,11,(28,74,93));c.rect(3,4,12,9,(63,163,184));c.rect(4,3,11,7,(127,230,217));c.rect(2,7,13,8,(83,196,200));c.line([(3,5),(5,4),(11,4)],(199,255,237));c.line([(3,10),(12,10),(13,8)],(47,114,151));return c

def board():
    c=Canvas();c.rect(1,2,14,13,(14,68,63));c.rect(2,3,13,12,(29,127,100))
    for trace in [[(2,5),(5,5),(5,3)],[(2,10),(4,10),(4,8),(7,8)],[(13,5),(11,5),(11,8)],[(9,12),(9,10),(13,10)],[(7,3),(7,5)]]:c.line(trace,(220,170,74))
    c.rect(6,6,10,9,(24,43,50));c.rect(7,6,9,8,(50,76,80));c.dot(2,3,(198,232,214));c.dot(13,12,(198,232,214))
    for x in range(3,13,2):c.rect(x,13,x,14,(231,193,100))
    return c

def metal():
    c=Canvas();r=random.Random(98)
    for y in range(16):
        for x in range(16):
            s=47+r.randrange(-4,5);c.dot(x,y,(s,s+11,s+16))
    c.rect(0,0,15,0,(92,114,123));c.rect(0,15,15,15,(19,28,36));c.rect(0,1,0,14,(74,94,103));c.rect(15,1,15,14,(19,28,36))
    for x,y in [(1,1),(14,1),(1,14),(14,14)]:c.dot(x,y,(142,160,161))
    return c

def main():
    assets={'ciscium_ore':ore(),'deepslate_ciscium_ore':ore(True),'raw_ciscium':raw(),'ciscium_ingot':ingot(),'basic_circuit_board':board()}
    top=metal();top.rect(2,2,13,13,(25,81,70));top.rect(4,5,11,10,(54,137,96));top.rect(6,6,9,9,(28,43,52));top.line([(3,4),(5,4),(5,7)],(211,161,66));top.line([(10,8),(12,8),(12,11)],(211,161,66));top.rect(2,12,5,13,(173,91,48))
    front=metal();front.rect(2,3,13,8,(12,25,34));front.rect(3,4,9,7,(54,140,147));front.rect(11,4,12,5,(81,223,145));front.rect(11,7,12,7,(213,92,63));front.rect(2,11,13,13,(19,28,36));front.rect(3,11,12,11,(126,101,62))
    side=metal()
    for y in (4,7,10):side.rect(3,y,12,y+1,(18,28,36))
    bottom=metal();bottom.rect(3,3,12,12,(30,42,49))
    assets.update({'module_assembler_top':top,'module_assembler_front':front,'module_assembler_side':side,'module_assembler_bottom':bottom})
    sheet=[[(18,25,32,255) for _ in range(192)] for _ in range(192)]
    for index,(name,c) in enumerate(assets.items()):
        kind='item' if name in ('raw_ciscium','ciscium_ingot','basic_circuit_board') else 'block'
        png(ROOT/f'textures/{kind}/{name}.png',16,16,c.pixels)
        for y in range(64):
            for x in range(64):
                rgba=c.pixels[y//4][x//4]
                if rgba[3]:sheet[(index//3)*64+y][(index%3)*64+x]=rgba
    def write(path,value):
        p=ROOT/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,indent=2)+'\n')
    for name in ('ciscium_ore','deepslate_ciscium_ore'):
        write(f'blockstates/{name}.json',{'variants':{'':{'model':f'netlab:block/{name}'}}})
        write(f'models/block/{name}.json',{'parent':'minecraft:block/cube_all','textures':{'all':f'netlab:block/{name}'}})
        write(f'models/item/{name}.json',{'parent':f'netlab:block/{name}'})
    for name in ('raw_ciscium','ciscium_ingot','basic_circuit_board'):write(f'models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'netlab:item/{name}'}})
    write('models/block/module_assembler.json',{'parent':'minecraft:block/orientable_with_bottom','textures':{'top':'netlab:block/module_assembler_top','bottom':'netlab:block/module_assembler_bottom','front':'netlab:block/module_assembler_front','side':'netlab:block/module_assembler_side'}})
    write('models/item/module_assembler.json',{'parent':'netlab:block/module_assembler'})
    write('blockstates/module_assembler.json',{'variants':{f'facing={face}':{'model':'netlab:block/module_assembler','y':angle} for face,angle in [('north',0),('east',90),('south',180),('west',270)]}})
    png(Path('/tmp/netlab-ciscium-textures.png'),192,192,sheet)
    print('Generated Ciscium ore/material/board textures and module assembler block models.')

if __name__=='__main__':main()
