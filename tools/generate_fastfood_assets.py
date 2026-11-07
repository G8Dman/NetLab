#!/usr/bin/env python3
"""Render the native SVG logo asset and generate the decorative block's data."""
from pathlib import Path
import json
import subprocess

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/netlab'
DATA = ROOT / 'src/main/resources/data'
ID = 'vkusno_i_tochka_block'


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def main():
    output = ASSETS / f'textures/block/{ID}.png'
    output.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(['magick', '-background', 'none', str(ROOT / 'tools/assets/vkusno_symbol.svg'),
                    '-strip', str(output)], check=True)
    write(ASSETS / f'blockstates/{ID}.json', {'variants': {'': {'model': f'netlab:block/{ID}'}}})
    write(ASSETS / f'models/block/{ID}.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'netlab:block/{ID}'}})
    write(ASSETS / f'models/item/{ID}.json', {'parent': f'netlab:block/{ID}'})
    write(DATA / f'netlab/loot_table/blocks/{ID}.json', {'type': 'minecraft:block', 'pools': [{
        'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'netlab:{ID}'}],
        'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    write(DATA / f'netlab/recipe/{ID}.json', {'type': 'minecraft:crafting_shaped', 'category': 'building',
        'pattern': ['GGG', 'ORB', 'GGG'], 'key': {
            'G': {'item': 'minecraft:green_concrete'}, 'O': {'item': 'minecraft:orange_dye'},
            'R': {'item': 'minecraft:red_dye'}, 'B': {'item': 'minecraft:bread'}},
        'result': {'id': f'netlab:{ID}', 'count': 1}})
    write(DATA / f'netlab/advancement/recipes/{ID}.json', {'parent': 'minecraft:recipes/root',
        'criteria': {'has_bread': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': ['minecraft:bread']}]}}},
        'requirements': [['has_bread']], 'rewards': {'recipes': [f'netlab:{ID}']}})
    for locale, title in [('ru_ru', 'Вкусно и точка'), ('en_us', 'Vkusno i tochka')]:
        path=ASSETS / f'lang/{locale}.json'; value=json.loads(path.read_text()); value[f'block.netlab.{ID}']=title; write(path,value)
    print('Generated decorative block, native SVG texture, recipe, loot and translations.')


if __name__ == '__main__':
    main()
