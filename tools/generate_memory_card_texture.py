from pathlib import Path
from generate_cable_textures import png
pixels=[[(0,0,0,0) for x in range(16)] for y in range(16)]
def rect(a,b,c,d,rgb):
    for y in range(b,d+1):
        for x in range(a,c+1):pixels[y][x]=(*rgb,255)
rect(3,1,11,14,(22,45,61));rect(4,1,10,1,(102,191,206));rect(2,3,12,13,(24,74,91));rect(3,3,11,8,(52,128,141));rect(4,4,10,7,(184,233,212));rect(5,5,9,6,(31,74,91));rect(4,10,10,11,(17,37,51))
for x in (3,5,7,9,11):rect(x,12,x,14,(226,181,83))
png(Path(__file__).resolve().parents[1]/'src/main/resources/assets/netlab/textures/item/cisco_memory_card.png',16,16,pixels)
