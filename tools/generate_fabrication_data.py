#!/usr/bin/env python3
"""Survival progression, recipes, block drops and Ciscium world generation."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'src/main/resources/data'
ADDON = ROOT / 'src/automation/resources/data'

def write(root, path, value):
    path = root / path; path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')

def unlock(root, namespace, name, recipe, ingredient):
    write(root, f'{namespace}/advancement/recipes/{name}.json', {
        'parent': 'minecraft:recipes/root',
        'criteria': {'has_material': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': ingredient}]}},
                     'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipe': recipe}}},
        'requirements': [['has_material', 'has_the_recipe']], 'rewards': {'recipes': [recipe]}})

def shaped(name, pattern, materials, namespace='netlab', root=MAIN):
    result = f'{namespace}:{name}'
    write(root, f'{namespace}/recipe/{name}.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern,
          'key': {k: {'item': v} for k, v in materials.items()}, 'result': {'id': result, 'count': 1}})
    unlock(root, namespace, name, result, 'netlab:basic_circuit_board' if 'netlab:basic_circuit_board' in materials.values() else next(iter(materials.values())))

def shapeless(name, ingredients, result=None, count=1):
    write(MAIN, f'netlab/recipe/{name}.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
          'ingredients': [{'item': x} for x in ingredients], 'result': {'id': result or f'netlab:{name}', 'count': count}})
    unlock(MAIN, 'netlab', name, f'netlab:{name}', ingredients[0])

def self_drop(namespace, block, result=None, root=MAIN):
    write(root, f'{namespace}/loot_table/blocks/{block}.json', {'type': 'minecraft:block',
        'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': result or f'{namespace}:{block}'}],
                   'conditions': [{'condition': 'minecraft:survives_explosion'}]}], 'random_sequence': f'{namespace}:blocks/{block}'})

def main():
    i, c, r, b = 'minecraft:iron_ingot', 'minecraft:copper_ingot', 'minecraft:redstone', 'netlab:basic_circuit_board'
    g = 'minecraft:glass_pane'
    shapeless('basic_circuit_board', ['netlab:ciscium_ingot', c, r])
    shapeless('cisco_memory_card', ['netlab:basic_circuit_board', 'netlab:ciscium_ingot', 'minecraft:gold_nugget'])
    shaped('ciscium_block', ['CCC', 'CCC', 'CCC'], {'C': 'netlab:ciscium_ingot'})
    shapeless('ciscium_ingots_from_block', ['netlab:ciscium_block'], 'netlab:ciscium_ingot', 9)
    specs = {
        'router': (['ICI', 'BBB', 'IRI'], {'I': i, 'C': c, 'B': b, 'R': r}),
        'switch': (['III', 'CBC', 'BBB'], {'I': i, 'C': c, 'B': b}),
        'pc': (['IGI', 'GBG', 'CRC'], {'I': i, 'G': g, 'B': b, 'C': c, 'R': r}),
        'laptop': (['GGG', 'CBC', 'III'], {'I': i, 'G': g, 'C': c, 'B': b}),
        'access_point': ([' C ', 'CBC', ' I '], {'I': i, 'C': c, 'B': b}),
        'traffic_controller': (['IRI', 'BTB', 'III'], {'I': i, 'R': r, 'B': b, 'T': 'minecraft:clock'}),
        'rack': (['I I', 'I I', 'III'], {'I': i}),
        'energy_cabinet': (['IEI', 'BBB', 'ICI'], {'I': i, 'E': 'minecraft:redstone_block', 'B': b, 'C': c}),
        'rack_power_supply': (['III', 'CBC', 'IRI'], {'I': i, 'C': c, 'B': b, 'R': r}),
        'portable_terminal': (['IGI', 'GBG', 'ICI'], {'I': i, 'G': g, 'B': b, 'C': c}),
        'diagnostic_goggles': (['GGG', 'IBI', 'R R'], {'I': i, 'G': g, 'B': b, 'R': r}),
        'screwdriver': (['  I', ' C ', 'R  '], {'I': i, 'C': c, 'R': r}),
        'module_assembler': (['III', 'CBC', 'RIR'], {'I': i, 'C': c, 'B': b, 'R': r}),
        'ethernet_cable': (['CCC', ' R ', 'CCC'], {'C': c, 'R': r}),
        'console_cable': (['CCC', 'LBL', 'CCC'], {'C': c, 'L': 'minecraft:light_blue_dye', 'B': b}),
        'serial_dce_cable': (['IRI', 'CBC', 'IRI'], {'I': i, 'R': r, 'C': c, 'B': b}),
        'fiber_cable': (['GGG', 'CBC', 'GGG'], {'G': 'minecraft:glass', 'C': c, 'B': b}),
        'phone_cable': (['CCC', ' P ', 'CCC'], {'C': c, 'P': 'minecraft:paper'}),
        'async_cable': (['CCC', 'BBB', 'CCC'], {'C': c, 'B': b}),
        'power_cable': (['CCC', 'RBR', 'CCC'], {'C': c, 'R': r, 'B': b}),
    }
    for name, (pattern, materials) in specs.items(): shaped(name, pattern, materials)
    shapeless('crossover_cable', ['netlab:ethernet_cable', 'minecraft:red_dye'])
    shapeless('serial_dte_cable', ['netlab:serial_dce_cable', 'minecraft:paper'])
    shapeless('serial_dce_from_dte', ['netlab:serial_dte_cable', r], 'netlab:serial_dce_cable')
    shapeless('cisco_bible', ['netlab:ciscium_ingot', 'netlab:ciscium_ingot', 'minecraft:book'])
    for ore in ('raw_ciscium', 'ciscium_ore', 'deepslate_ciscium_ore'):
        for kind, ticks in (('smelting', 200), ('blasting', 100)):
            name = f'ciscium_ingot_from_{ore}_{kind}'
            write(MAIN, f'netlab/recipe/{name}.json', {'type': f'minecraft:{kind}', 'category': 'misc', 'group': 'ciscium_ingot',
                'ingredient': {'item': f'netlab:{ore}'}, 'result': {'id': 'netlab:ciscium_ingot'}, 'experience': .7, 'cookingtime': ticks})
            unlock(MAIN, 'netlab', name, f'netlab:{name}', f'netlab:{ore}')
    for name in ('router', 'switch', 'pc', 'laptop', 'access_point', 'traffic_controller', 'rack', 'energy_cabinet', 'module_assembler', 'ciscium_block'):
        self_drop('netlab', name)
    for name in ('rack_router', 'rack_switch'): self_drop('netlab', name, 'netlab:rack')
    for name in ('ciscium_ore', 'deepslate_ciscium_ore'):
        write(MAIN, f'netlab/loot_table/blocks/{name}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{
            'type': 'minecraft:alternatives', 'children': [
                {'type': 'minecraft:item', 'name': f'netlab:{name}', 'conditions': [{'condition': 'minecraft:match_tool', 'predicate': {'predicates': {'minecraft:enchantments': [{'enchantments': 'minecraft:silk_touch', 'levels': {'min': 1}}]}}}]},
                {'type': 'minecraft:item', 'name': 'netlab:raw_ciscium', 'functions': [
                    {'function': 'minecraft:apply_bonus', 'enchantment': 'minecraft:fortune', 'formula': 'minecraft:ore_drops'},
                    {'function': 'minecraft:explosion_decay'}]}]}]}], 'random_sequence': f'netlab:blocks/{name}'})
    ores = ['netlab:ciscium_ore', 'netlab:deepslate_ciscium_ore']
    write(MAIN, 'minecraft/tags/block/mineable/pickaxe.json', {'replace': False, 'values': ores + ['netlab:module_assembler', 'netlab:ciscium_block']})
    write(MAIN, 'minecraft/tags/block/needs_iron_tool.json', {'replace': False, 'values': ores + ['netlab:ciscium_block']})
    for kind in ('block', 'item'):
        write(MAIN, f'c/tags/{kind}/storage_blocks/ciscium.json', {'replace': False, 'values': ['netlab:ciscium_block']})
        write(MAIN, f'c/tags/{kind}/storage_blocks.json', {'replace': False, 'values': ['#c:storage_blocks/ciscium']})
        write(MAIN, f'c/tags/{kind}/ores/ciscium.json', {'replace': False, 'values': ores})
        write(MAIN, f'c/tags/{kind}/ores.json', {'replace': False, 'values': ['#c:ores/ciscium']})
    write(MAIN, 'c/tags/item/ingots/ciscium.json', {'replace': False, 'values': ['netlab:ciscium_ingot']})
    write(MAIN, 'c/tags/item/ingots.json', {'replace': False, 'values': ['#c:ingots/ciscium']})
    write(MAIN, 'c/tags/item/raw_materials/ciscium.json', {'replace': False, 'values': ['netlab:raw_ciscium']})
    write(MAIN, 'c/tags/item/raw_materials.json', {'replace': False, 'values': ['#c:raw_materials/ciscium']})
    write(MAIN, 'netlab/worldgen/configured_feature/ore_ciscium.json', {'type': 'minecraft:ore', 'config': {'size': 7, 'discard_chance_on_air_exposure': 0.0,
        'targets': [{'target': {'predicate_type': 'minecraft:tag_match', 'tag': tag}, 'state': {'Name': f'netlab:{ore}'}} for tag, ore in [('minecraft:stone_ore_replaceables','ciscium_ore'), ('minecraft:deepslate_ore_replaceables','deepslate_ciscium_ore')]]}})
    write(MAIN, 'netlab/worldgen/placed_feature/ore_ciscium.json', {'feature': 'netlab:ore_ciscium', 'placement': [
        {'type': 'minecraft:count', 'count': 10}, {'type': 'minecraft:in_square'},
        {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:trapezoid', 'min_inclusive': {'absolute': -48}, 'max_inclusive': {'absolute': 48}}},
        {'type': 'minecraft:biome'}]})
    write(MAIN, 'netlab/neoforge/biome_modifier/add_ciscium_ore.json', {'type': 'neoforge:add_features', 'biomes': '#minecraft:is_overworld', 'features': 'netlab:ore_ciscium', 'step': 'underground_ores'})
    for name, pattern, extra in (
        ('management_server', ['IBI', 'BCB', 'IRI'], {'C': 'minecraft:comparator'}),
        ('multiinterface', ['ICI', 'RBR', 'ICI'], {'C': c}),
        ('packet_analyzer', ['IGI', 'RBR', 'ICI'], {'G': g, 'C': c})):
        shaped(name, pattern, {'I': i, 'B': b, 'R': r, **extra}, namespace='netlabautomation', root=ADDON)
    print('Generated survival recipes, unlocks, block loot, ore tags and Overworld worldgen; expansion modules have no crafting-table recipes.')

if __name__ == '__main__': main()
