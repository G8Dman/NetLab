#!/usr/bin/env python3
"""Survival recipes for Mobile/Sputnik and optional Pocket modules; no cross-addon ingredients."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
I, C, G, R = ('minecraft:' + x for x in ('iron_ingot', 'copper_ingot', 'gold_ingot', 'redstone'))
B, A, M = ('netlab:' + x for x in ('basic_circuit_board', 'advanced_circuit_board', 'cisco_memory_card'))
Q, P, L = ('minecraft:' + x for x in ('quartz', 'paper', 'glass_pane'))


def write(root, path, value):
    p = root / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def recipe(addon, name, pattern, key, count=1):
    namespace = 'netlab' + addon
    root = ROOT / 'src' / addon / 'resources/data' / namespace
    assert set(''.join(pattern)) - {' '} == set(key), name
    data = {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern,
            'key': {k: {'item': v} for k, v in key.items()}, 'result': {'id': namespace + ':' + name, 'count': count}}
    write(root, 'recipe/' + name + '.json', data)
    identifier = namespace + ':' + name
    write(root, 'advancement/recipes/' + name + '.json', {
        'parent': 'minecraft:recipes/root',
        'criteria': {'has_material': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': B}]}},
                     'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipe': identifier}}},
        'requirements': [['has_material', 'has_the_recipe']], 'rewards': {'recipes': [identifier]}})


def main():
    for name, pattern, key, count in [
        ('sector_antenna', ['ICI', 'CQC', 'ICI'], {'I': I, 'C': C, 'Q': Q}, 1),
        ('radio_unit', ['ICI', 'GAG', 'IRI'], {'I': I, 'C': C, 'G': G, 'A': A, 'R': R}, 1),
        ('baseband_unit', ['IMI', 'QAQ', 'IGI'], {'I': I, 'M': M, 'Q': Q, 'A': A, 'G': G}, 1),
        ('mobile_core', ['IAI', 'GMG', 'IRI'], {'I': I, 'A': A, 'G': G, 'M': M, 'R': 'minecraft:redstone_block'}, 1),
        ('sim_station', ['ILI', 'PAP', 'IGI'], {'I': I, 'L': L, 'P': 'minecraft:piston', 'A': A, 'G': G}, 1),
        ('blank_sim', ['PGP', 'PRP'], {'P': P, 'G': 'minecraft:gold_nugget', 'R': R}, 4),
        ('cisco_pocket', ['ILI', 'CAC', 'IRI'], {'I': I, 'L': L, 'C': C, 'A': A, 'R': R}, 1),
        ('rf_cable', ['CSC', 'CSC'], {'C': C, 'S': 'minecraft:string'}, 8),
        ('module_nl_mob_control', ['GCG', 'RBR', 'GMG'], {'G': G, 'C': 'minecraft:comparator', 'R': R, 'B': A, 'M': M}, 1),
        ('module_nl_mob_baseband', ['GQG', 'QMQ', 'GBG'], {'G': G, 'Q': Q, 'M': M, 'B': A}, 1),
        ('module_nl_mob_fronthaul', ['GFG', 'CBC', 'GRG'], {'G': G, 'F': 'netlab:module_glc_lh_smd', 'C': C, 'B': A, 'R': R}, 1),
        ('module_cisco_lte_modem', ['CQC', 'RBR', 'G G'], {'C': C, 'Q': Q, 'R': R, 'B': A, 'G': G}, 1),
        ('phone_diagnostics_module', [' C ', 'RBR', ' G '], {'C': 'minecraft:comparator', 'R': R, 'B': B, 'G': G}, 1),
        ('phone_terminal_module', [' M ', 'BTB', ' G '], {'M': M, 'B': A, 'T': 'netlab:portable_terminal', 'G': G}, 1),
    ]:
        recipe('mobile', name, pattern, key, count)
    for name, pattern, key, count in [
        ('dish', ['III', 'IQI', ' C '], {'I': I, 'Q': Q, 'C': 'minecraft:copper_block'}, 1),
        ('vsat_dish', [' I ', 'IQI', 'CBC'], {'I': I, 'Q': Q, 'C': C, 'B': B}, 1),
        ('odu', ['ICI', 'QGQ', 'IBI'], {'I': I, 'C': C, 'Q': Q, 'G': G, 'B': A}, 1),
        ('modem', ['ICI', 'QAQ', 'IRI'], {'I': I, 'C': C, 'Q': Q, 'A': A, 'R': R}, 1),
        ('hub', ['IAI', 'GMG', 'IQI'], {'I': I, 'A': A, 'G': G, 'M': M, 'Q': Q}, 1),
        ('gateway', ['IQI', 'AMA', 'IRI'], {'I': I, 'Q': Q, 'A': A, 'M': M, 'R': R}, 1),
        ('speaker', ['PPP', 'CBC', 'PPP'], {'P': 'minecraft:oak_planks', 'C': C, 'B': B}, 1),
        ('module_nl_sat_control', ['GMG', 'QBQ', 'GRG'], {'G': G, 'M': M, 'Q': Q, 'B': A, 'R': R}, 1),
        ('module_nl_sat_tx', ['GQG', 'CBC', 'GRG'], {'G': G, 'Q': Q, 'C': C, 'B': A, 'R': 'minecraft:redstone_block'}, 1),
        ('module_nl_sat_rx', ['GLG', 'QBQ', 'GCG'], {'G': G, 'L': L, 'Q': Q, 'B': A, 'C': C}, 1),
        ('phone_browser_module', [' L ', 'BMB', ' G '], {'L': L, 'B': A, 'M': M, 'G': G}, 1),
    ]:
        recipe('sputnik', name, pattern, key, count)
    for name, pattern in [('coax_tx_cable', ['GCG', 'SRS']), ('coax_rx_cable', ['SRS', 'GCG']),
                          ('sat_rf_tx_cable', ['CRC', 'SGS']), ('sat_rf_rx_cable', ['SGS', 'CRC']),
                          ('audio_cable', ['C C', 'SGS'])]:
        recipe('sputnik', name, pattern, {k: v for k, v in {'G': 'minecraft:gold_nugget', 'C': C, 'S': 'minecraft:string', 'R': R}.items() if k in ''.join(pattern)}, 8)
    recipe('automation', 'phone_automation_module', [' C ', 'BMB', ' G '], {'C': 'minecraft:comparator', 'B': A, 'M': M, 'G': G})
    write(ROOT / 'src/mobile/resources/assets/netlabmobile', 'models/item/phone_terminal_module.json', {
        'parent': 'minecraft:item/generated', 'textures': {'layer0': 'netlabmobile:item/module_nl_mob_control'}})
    print('Generated 31 addon crafting recipes and recipe-book unlocks. Operator SIMs are issued by the SIM station.')


if __name__ == '__main__':
    main()
