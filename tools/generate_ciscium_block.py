#!/usr/bin/env python3
"""Pixel-art Ciscium storage block with an embossed Cisco bridge/word mark."""
from pathlib import Path
import json
from generate_cable_textures import png

ROOT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/netlab'

def main():
    pixels=[]
    for y in range(32):
        row=[]
        for x in range(32):
            grain=(x*7+y*13)%5-2
            color=(41+grain,126+grain,144+grain)
            if x in (0,31) or y in (0,31):color=(19,62,81)
            elif x in (1,30) or y in (1,30):color=(102,205,206) if x+y<32 else (28,89,110)
            elif x in (3,28) or y in (3,28):color=(32,102,120)
            row.append((*color,255))
        pixels.append(row)
    def rect(x0,y0,x1,y1,color):
        for y in range(y0,y1+1):
            for x in range(x0,x1+1):pixels[y][x]=(*color,255)
    # Two high arches and nine vertical bars form the bridge mark.
    for i,height in enumerate([4,6,9,6,3,6,9,6,4]):
        x=7+i*2;rect(x+1,17-height,x+1,16,(24,77,99));rect(x,16-height,x,15,(200,253,242))
    glyphs={'C':['111','100','100','100','111'], 'I':['111','010','010','010','111'],
            'S':['111','100','111','001','111'], 'O':['111','101','101','101','111']}
    for index,letter in enumerate('CISCO'):
        for y,row in enumerate(glyphs[letter]):
            for x,on in enumerate(row):
                if on=='1':rect(7+index*4+x,21+y,7+index*4+x,21+y,(24,77,99));rect(6+index*4+x,20+y,6+index*4+x,20+y,(220,255,242))
    for x,y in [(4,4),(27,4),(4,27),(27,27)]:rect(x,y,x,y,(138,220,217))
    png(ROOT/'textures/block/ciscium_block.png',32,32,pixels)
    def write(path,value):
        target=ROOT/path;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(value,indent=2)+'\n')
    write('blockstates/ciscium_block.json',{'variants':{'':{'model':'netlab:block/ciscium_block'}}})
    write('models/block/ciscium_block.json',{'parent':'minecraft:block/cube_all','textures':{'all':'netlab:block/ciscium_block'}})
    write('models/item/ciscium_block.json',{'parent':'netlab:block/ciscium_block'})
    preview=[[pixels[y//8][x//8] for x in range(256)] for y in range(256)]
    png(Path('/tmp/netlab-ciscium-block.png'),256,256,preview)
    print('Generated Ciscium block texture and block/item models.')

if __name__=='__main__':main()
