#!/usr/bin/env python3
"""Generate an original functional MineColonies fast-food style, levels 1..5.

Development dependency only: pip install nbtlib. No downloaded world is redistributed.
The resulting blueprints are read by the installed Structurize version in standalone checks.
"""
from pathlib import Path
from collections import Counter, deque
import json
import subprocess
import nbtlib as nbt

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'src/main/resources/blueprints/netlab/netlab_fastfood'
PACK='Вкусно и точка'
SIZE=(25,12,25)
ANCHOR=(5,1,9)


def pos(p): return nbt.Compound(dict(zip(('x','y','z'), map(nbt.Int,p))))
def compound(**values): return nbt.Compound(values)
def string(value): return nbt.String(value)
def empty_inventory(): return nbt.List[nbt.Compound]([compound(empty=nbt.Byte(1)) for _ in range(27)])


class Building:
    def __init__(self, level, kind):
        self.level=level; self.kind=kind; self.blocks={}; self.tiles={}; self.seats=[]; self.work=[]
        self.front=20 if level<3 else 22
        self.left=4 if level==1 else 2
        self.right=20 if level==1 else 22
        self.back=6 if level<3 else 4
    def put(self,x,y,z,name,**properties):
        assert 0<=x<SIZE[0] and 0<=y<SIZE[1] and 0<=z<SIZE[2]
        name=name if ':' in name else 'minecraft:'+name
        self.blocks[x,y,z]=(name,tuple(sorted((k,str(v).lower()) for k,v in properties.items())))
        self.tiles.pop((x,y,z),None)
    def fill(self,a,b,name,**props):
        for y in range(a[1],b[1]+1):
            for z in range(a[2],b[2]+1):
                for x in range(a[0],b[0]+1):self.put(x,y,z,name,**props)
    def tile(self,x,y,z,block,entity,**props):
        self.put(x,y,z,block,**props)
        self.tiles[x,y,z]=compound(id=string(entity),x=nbt.Int(x),y=nbt.Int(y),z=nbt.Int(z))
        return self.tiles[x,y,z]
    def table(self,x,z):
        self.put(x,1,z,'oak_fence');self.put(x,2,z,'oak_pressure_plate')
        for dx,face in [(-1,'east'),(1,'west')]:
            self.put(x+dx,1,z,'oak_stairs',facing=face,half='bottom',shape='straight',waterlogged=False)
            self.seats.append((x+dx,1,z))
    def build(self):
        l,r,b,f=self.left,self.right,self.back,self.front
        self.fill((l,0,b),(r,0,f),'smooth_stone')
        self.fill((l+1,0,b+1),(r-1,0,f-1),'birch_planks')
        self.fill((l,1,b),(r,4,f),'air')
        self.fill((l,1,b),(r,4,b),'white_concrete')
        self.fill((l,1,b),(l,4,f),'brown_terracotta')
        self.fill((r,1,b),(r,4,f),'brown_terracotta')
        self.fill((l,1,f),(r,4,f),'white_concrete')
        for a,z0,z1 in [(l,13,f-2),(r,13,f-2)]:self.fill((a,2,z0),(a,3,z1),'glass')
        self.fill((l+2,1,f),(11,3,f),'glass')
        self.fill((14,1,f),(r-2,3,f),'glass')
        for x,hinge in [(12,'left'),(13,'right')]:
            for y,half in [(1,'lower'),(2,'upper')]:
                self.put(x,y,f,'oak_door',facing='south',half=half,hinge=hinge,open=False,powered=False)
        # Staff corridor at x=6..8: no door, no step, two blocks of headroom.
        self.fill((l+1,1,12),(r-1,1,12),'smooth_quartz')
        self.fill((6,1,12),(8,3,12),'air')
        self.fill((10,2,12),(r-2,2,12),'smooth_quartz_slab',type='bottom',waterlogged=False)
        for x in range(10,r-1,3):self.put(x,2,12,'black_carpet')
        # Reachable workstations share an empty aisle along z=8.
        count=min(2+self.level,6)
        for x in range(8,8+count):
            te=self.tile(x,1,7,'furnace','minecraft:furnace',facing='south',lit=False)
            te.update({'Items':nbt.List[nbt.Compound]([]),'BurnTime':nbt.Short(0),'CookTime':nbt.Short(0),'CookTimeTotal':nbt.Short(200)})
            self.work.append((x,0,8))
        for z in range(7,7+min(self.level,4)):
            te=self.tile(18,1,z,'minecolonies:blockminecoloniesrack','minecolonies:rack',facing='west',variant='blockrackempty')
            te.update({'inventory':empty_inventory(),'inWarehouse':nbt.Byte(0),'version':nbt.Int(0)})
        if self.level>=3:
            for x in range(14,16):self.tile(x,1,7,'smoker','minecraft:smoker',facing='south',lit=False)['Items']=nbt.List[nbt.Compound]([])
        self.put(16,1,10,'crafting_table')
        self.tile(17,1,10,'barrel','minecraft:barrel',facing='south',open=False)['Items']=nbt.List[nbt.Compound]([])
        # Sinks are decorative; never obstruct the work aisle.
        self.put(16,1,5 if self.level>=3 else 6,'cauldron')
        for x,z in [(9,15),(17,15)]+([(9,18),(17,18)] if self.level>=2 else [])+([(9,20),(17,20)] if self.level>=4 else []):self.table(x,z)
        # Flat roof, orange fascia and independently built green sign (no NetLab blocks).
        self.fill((l,5,b),(r,5,f),'white_concrete')
        self.fill((l,4,b),(r,4,b),'orange_concrete')
        self.fill((l,4,f),(r,4,f),'orange_concrete')
        self.fill((l,4,b),(l,4,f),'orange_concrete')
        self.fill((r,4,b),(r,4,f),'orange_concrete')
        self.fill((8,5,f),(18,8,f),'green_concrete')
        # Two fries and a dot, made out of ordinary concrete blocks.
        for x in [11,15]:
            self.put(x,7,f+1,'orange_concrete');self.put(x+1,6,f+1,'orange_concrete');self.put(x+1,5,f+1,'orange_concrete')
        self.put(9,5,f+1,'red_concrete')
        # Ceiling lights and clear two-wide front approach.
        for x in [7,12,17]:
            for z in [10,16]:self.put(x,5,z,'sea_lantern')
        self.fill((11,0,f+1),(14,0,24),'smooth_stone')
        if self.level>=2:
            self.fill((4,3,f+1),(20,3,f+1),'smooth_quartz_slab',type='bottom',waterlogged=False)
            self.put(4,1,f+1,'stone_brick_wall');self.put(20,1,f+1,'stone_brick_wall')
            self.put(4,2,f+1,'lantern',hanging=False,waterlogged=False);self.put(20,2,f+1,'lantern',hanging=False,waterlogged=False)
        if self.level>=4:
            self.fill((l,0,1),(r,0,2),'smooth_stone')
            for x in [4,10,16,21]:
                self.put(x,1,2,'oak_leaves',persistent=True,distance=1,waterlogged=False)
            self.fill((8,6,7),(10,6,9),'smooth_stone_slab',type='bottom',waterlogged=False)
            self.put(9,7,8,'iron_trapdoor',facing='north',half='bottom',open=False,powered=False,waterlogged=False)
        if self.level>=5:
            self.fill((14,6,5),(19,6,7),'black_stained_glass')
            self.fill((3,1,4),(3,2,6),'white_concrete')
            self.put(3,3,5,'sea_lantern')
        # Primary workplace. No baked-in world/colony/player ID is retained.
        hut='minecolonies:blockhutcook' if self.kind=='cook' else 'minecolonies:blockhutkitchen'
        te=self.tile(*ANCHOR,hut,'minecolonies:colonybuilding',facing='east')
        path=self.path()
        tags=[((0,-1,0),'groundlevel')]
        tags += [(tuple(a-b for a,b in zip(p,ANCHOR)),'sit') for p in self.seats] if self.kind=='cook' else []
        tags += [(tuple(a-b for a,b in zip(p,ANCHOR)),'work') for p in self.work]
        provider=compound(corner1=pos(tuple(-v for v in ANCHOR)),corner2=pos(tuple(s-1-v for s,v in zip(SIZE,ANCHOR))),
            pack=string(PACK),path=string(path),schematicName=string(f'{self.kind}{self.level}'),
            posTagMap=nbt.List[nbt.Compound]([compound(tagPos=pos(p),tagNameList=nbt.List[nbt.Compound]([compound(tagName=string(name))])) for p,name in tags]))
        te.update({'colony':nbt.Int(0),'inventory':empty_inventory(),'mirror':nbt.Byte(0),'inWarehouse':nbt.Byte(0),
                   'pack':string(PACK),'path':string(path),'blueprintDataProvider':provider})
        return self
    def path(self):
        return ('fundamentals/' if self.kind=='cook' else 'craftsmanship/luxury/')+f'{self.kind}{self.level}.blueprint'
    def validate(self):
        # Conservative floor-level path check: no jumping, flying or crossing furniture.
        def walk(p):
            x,z=p
            if not (0<=x<25 and 0<=z<25):return False
            if self.blocks.get((x,0,z),('minecraft:air',()))[0]=='minecraft:air':return False
            return all(self.blocks.get((x,y,z),('minecraft:air',()))[0] in ['minecraft:air','minecraft:oak_door','minecraft:black_carpet'] for y in [1,2])
        start=(12,self.front); reached={start}; queue=deque([start])
        while queue:
            x,z=queue.popleft()
            for p in [(x-1,z),(x+1,z),(x,z-1),(x,z+1)]:
                if p not in reached and walk(p):reached.add(p);queue.append(p)
        for x,_,z in self.work:assert (x,z) in reached,('unreachable furnace',self.kind,self.level,x,z)
        assert (6,9) in reached, 'Workplace has no accessible side'
        assert len([v for v in self.tiles.values() if v['id']=='minecolonies:colonybuilding'])==1
        assert not any(name.startswith('netlab:') for name,_ in self.blocks.values())
    def save(self):
        self.validate();palette=[compound(Name=string('minecraft:air'))];index={('minecraft:air',()):0};blocks=[]
        for y in range(SIZE[1]):
            for z in range(SIZE[2]):
                for x in range(SIZE[0]):
                    state=self.blocks.get((x,y,z),('minecraft:air',()))
                    if state not in index:
                        index[state]=len(palette);tag=compound(Name=string(state[0]))
                        if state[1]:tag['Properties']=nbt.Compound({k:string(v) for k,v in state[1]})
                        palette.append(tag)
                    blocks.append(index[state])
        packed=[(blocks[i]<<16)|(blocks[i+1] if i+1<len(blocks) else 0) for i in range(0,len(blocks),2)]
        root=compound(version=nbt.Byte(1),mcversion=nbt.Int(3955),size_x=nbt.Short(SIZE[0]),size_y=nbt.Short(SIZE[1]),size_z=nbt.Short(SIZE[2]),
            name=string(f'{self.kind}{self.level}'),architects=nbt.List[nbt.String]([string('NetLab')]),
            required_mods=nbt.List[nbt.String]([string('minecolonies'),string('structurize')]),palette=nbt.List[nbt.Compound](palette),
            blocks=nbt.IntArray(packed),tile_entities=nbt.List[nbt.Compound](list(self.tiles.values())),entities=nbt.List[nbt.Compound]([]),
            optional_data=compound(structurize=compound(primary_offset=pos(ANCHOR))))
        target=OUT/self.path();target.parent.mkdir(parents=True,exist_ok=True)
        nbt.File(root).save(target,gzipped=True)
        return {'kind':self.kind,'level':self.level,'file':self.path(),'dimensions':SIZE,'anchor':ANCHOR,
                'blocks':sum(v[0]!='minecraft:air' for v in self.blocks.values()),'furnaces':len(self.work),
                'seats':len(self.seats),'racks':sum(v['id']=='minecolonies:rack' for v in self.tiles.values())}


def main():
    OUT.mkdir(parents=True,exist_ok=True)
    meta={'name':PACK,'icon':'icon.png','authors':['NetLab'],
          'desc':'Ресторан и кухня повара: современный фастфуд, уровни 1–5. Печи, склады, столики и проходы для колонистов. Блок-пасхалка NetLab не нужен.',
          'mods':['minecolonies','structurize'],'version':1,'pack-format':1}
    (OUT/'pack.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2)+'\n')
    subprocess.run(['magick','-background','none',str(ROOT/'tools/assets/vkusno_symbol.svg'),'-resize','128x128','-strip',str(OUT/'icon.png')],check=True)
    reports=[Building(level,kind).build().save() for kind in ['cook','cookery'] for level in range(1,6)]
    (OUT/'validation.json').write_text(json.dumps(reports,ensure_ascii=False,indent=2)+'\n')
    (OUT/'README.txt').write_text('Вкусно и точка — оригинальный стиль NetLab для MineColonies.\n'
        'Выбери стиль «Вкусно и точка» инструментом строителя.\n'
        'Ресторан (cook) кормит колонистов, кухня повара (cookery) готовит рецепты.\n'
        'Это два варианта рабочих зданий; каждый строится отдельно строителем и улучшается до уровня 5.\n'
        'Выдай работнику топливо, ингредиенты и разреши блюда/рецепты в управлении зданием.\n'
        'Декоративный блок NetLab в чертежах не используется.\n'
        'Внешний проект-идея: https://www.planetminecraft.com/project/vkusno-i-tocka/ (_Max_top_024).\n'
        'Исходная карта не скачана и не включена: сайт отклоняет скачивание. Геометрия создана заново.\n')
    print('Generated 10 functional blueprints, levels 1–5, with reachable workstations. No NetLab block is used.')


if __name__=='__main__':main()
