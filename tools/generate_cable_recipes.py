#!/usr/bin/env python3
"""Expose all supported physical cables in the vanilla recipe book from copper onward."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def main():
    data = ROOT / 'src/main/resources/data/netlab'
    for name, pattern, key in [
        ('crossover_cable_direct', ['CRC', 'RBR', 'CRC'], {'C': 'minecraft:copper_ingot', 'R': 'minecraft:red_dye', 'B': 'netlab:basic_circuit_board'}),
        ('serial_dte_cable_direct', ['ICR', 'RBI', 'RCI'], {'I': 'minecraft:iron_ingot', 'C': 'minecraft:copper_ingot', 'R': 'minecraft:redstone', 'B': 'netlab:basic_circuit_board'}),
    ]:
        result = name.removesuffix('_direct')
        write(data / 'recipe' / (name + '.json'), {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern,
              'key': {k: {'item': v} for k, v in key.items()}, 'result': {'id': 'netlab:' + result, 'count': 1}})
    count = 0
    for addon in ['main', 'mobile', 'sputnik']:
        namespace = 'netlab' if addon == 'main' else 'netlab' + addon
        folder = ROOT / 'src' / addon / 'resources/data' / namespace
        for file in sorted((folder / 'recipe').glob('*.json')):
            recipe = json.loads(file.read_text())
            if not recipe['result']['id'].endswith('_cable'):
                continue
            identifier = namespace + ':' + file.stem
            write(folder / 'advancement/recipes' / file.name, {
                'parent': 'minecraft:recipes/root',
                'criteria': {'has_copper': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': 'minecraft:copper_ingot'}]}},
                             'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipe': identifier}}},
                'requirements': [['has_copper', 'has_the_recipe']], 'rewards': {'recipes': [identifier]}})
            count += 1
    print(f'Generated {count} recipe-book unlocks for 15 physical cable types and direct crossover/DTE recipes.')


if __name__ == '__main__':
    main()
