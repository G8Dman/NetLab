#!/usr/bin/env python3
"""Reproducible pixel assets drawn from geometric primitives; no external libraries."""
import json
import math
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/netlab'
KINDS = {
    'copper': ('ethernet_cable', (39, 119, 225)),
    'crossover': ('crossover_cable', (66, 73, 84)),
    'console': ('console_cable', (93, 209, 237)),
    'serial_dce': ('serial_dce_cable', (207, 71, 72)),
    'serial_dte': ('serial_dte_cable', (164, 55, 66)),
    'fiber': ('fiber_cable', (247, 179, 38)),
    'phone': ('phone_cable', (180, 186, 193)),
    'async': ('async_cable', (88, 198, 208)),
    'power': ('power_cable', (229, 162, 40)),
}

def png(path, width, height, pixels):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    raw = b''.join(b'\0' + bytes(v for rgba in row for v in rgba) for row in pixels)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))

def icon(kind, color):
    pixels = [[(0, 0, 0, 0) for _ in range(32)] for _ in range(32)]
    def dot(x, y, rgb):
        if 0 <= x < 32 and 0 <= y < 32:
            pixels[y][x] = (*rgb, 255)
    def rect(x0, y0, x1, y1, rgb):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1): dot(x, y, rgb)
    def line(points, radius, rgb):
        for a, b in zip(points, points[1:]):
            steps = max(1, int(math.dist(a, b) * 3))
            for step in range(steps + 1):
                t = step / steps
                x, y = a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t
                for py in range(math.floor(y - radius), math.ceil(y + radius) + 1):
                    for px in range(math.floor(x - radius), math.ceil(x + radius) + 1):
                        if (px - x)**2 + (py - y)**2 <= radius**2: dot(px, py, rgb)
    coil = [(15 + 8.5 * math.cos(t), 17 + 7 * math.sin(t)) for t in [i * math.pi / 44 for i in range(3, 85)]]
    path = [(26, 8), (25, 13)] + coil + [(15, 24), (8, 27)]
    dark = tuple(int(c * .44) for c in color)
    light = tuple(min(255, int(c * .8 + 62)) for c in color)
    line(path, 2.6, (23, 29, 37)); line(path, 1.8, dark); line(path, 1.25, color)
    line([(x, y - .7) for x, y in path], .55, light)
    # Small jacket ribs and a metal cable tie keep the coil readable at inventory scale.
    rect(12, 8, 14, 10, (40, 47, 57)); rect(13, 8, 13, 10, (149, 159, 167))
    if kind == 'fiber':
        rect(23, 4, 28, 10, (27, 77, 103)); rect(24, 3, 27, 8, (54, 145, 190))
        rect(24, 1, 25, 3, (216, 230, 221)); rect(27, 1, 28, 3, (216, 230, 221))
        rect(2, 24, 9, 29, (27, 77, 103)); rect(2, 25, 8, 28, (54, 145, 190))
        rect(0, 25, 2, 25, (216, 230, 221)); rect(0, 28, 2, 28, (216, 230, 221))
    elif kind in ('serial_dce', 'serial_dte', 'console', 'async'):
        rect(20, 2, 30, 8, (39, 46, 55)); rect(21, 1, 29, 6, (173, 185, 193)); rect(22, 2, 28, 5, (46, 65, 80))
        for x in (23, 25, 27): dot(x, 3, (244, 207, 100)); dot(x, 5, (244, 207, 100))
        if kind.startswith('serial'):
            rect(1, 23, 9, 30, (173, 185, 193)); rect(2, 24, 8, 29, (46, 65, 80))
            for y in (25, 27, 29): dot(3, y, (244, 207, 100)); dot(6, y, (244, 207, 100))
            # DCE: clock symbol; DTE: terminal symbol.
            rect(1, 1, 7, 7, (24, 31, 42)); rect(2, 2, 6, 6, (227, 220, 195))
            if kind == 'serial_dce': line([(4, 2), (4, 4), (6, 4)], .3, (38, 45, 53))
            else: rect(2, 2, 6, 4, (41, 67, 89)); rect(4, 5, 4, 6, (41, 67, 89))
        elif kind == 'async':
            for y in (22, 26, 30):
                line([(10, 26), (5, y)], .6, color); rect(0, y - 1, 5, y + 1, (143, 165, 176)); rect(0, y, 2, y, (233, 199, 105))
        else:
            rect(1, 24, 9, 30, (126, 164, 180)); rect(1, 25, 7, 28, (224, 236, 229))
            for y in (25, 27, 29): rect(1, y, 3, y, (230, 181, 71))
    elif kind == 'power':
        rect(22, 3, 29, 10, (27, 31, 37)); rect(23, 4, 28, 8, (66, 73, 81)); rect(24, 1, 24, 4, (213, 194, 133)); rect(27, 1, 27, 4, (213, 194, 133))
        rect(1, 23, 9, 30, (27, 31, 37)); rect(2, 24, 7, 29, (66, 73, 81)); rect(2, 25, 3, 25, (12, 16, 23)); rect(2, 28, 3, 28, (12, 16, 23))
    else:
        rect(22, 2, 29, 10, (84, 109, 124)); rect(23, 1, 28, 7, (181, 206, 209)); rect(24, 3, 27, 8, (221, 231, 215))
        for x in (23, 25, 27): rect(x, 1, x, 3, (235, 194, 86))
        rect(1, 23, 10, 30, (84, 109, 124)); rect(1, 24, 7, 29, (181, 206, 209))
        for y in (24, 26, 28): rect(1, y, 3, y, (235, 194, 86))
        if kind == 'crossover':
            line([(23, 5), (27, 8)], .6, (224, 109, 58)); line([(27, 5), (23, 8)], .6, (98, 191, 105))
        elif kind == 'phone': rect(24, 5, 27, 7, (102, 114, 121))
    return pixels

def jacket(kind):
    pixels = []
    for y in range(16):
        row = []
        for x in range(32):
            # Neutral highlights multiply the chosen cable/dye color in the renderer.
            shade = (185, 221, 244, 211)[y // 4] + ((x * 7 + y * 11) % 5 - 2)
            if kind == 'power' and (x + y * 2) % 10 < 2: shade -= 43
            elif kind == 'crossover' and (x + y) % 12 < 3: shade -= 52
            elif kind.startswith('serial') and x % 8 < 2: shade -= 50
            elif kind == 'fiber' and y in (2, 10): shade = 252
            elif kind == 'console' and x in (2, 3, 24) and y % 4 in (1, 2): shade -= 62
            elif kind == 'phone' and y % 4 == 0: shade -= 40
            elif kind == 'async' and (x - y) % 8 < 2: shade -= 40
            elif kind == 'copper' and x in (10, 11, 14) and y % 4 == 1: shade -= 48
            shade = max(0, min(255, shade)); row.append((shade, shade, shade, 255))
        pixels.append(row)
    return pixels

def main():
    sheet = [[(16, 23, 32, 255) for _ in range(384)] for _ in range(384)]
    for index, (kind, (item, color)) in enumerate(KINDS.items()):
        pixels = icon(kind, color)
        png(ROOT / f'textures/item/routed_{kind}.png', 32, 32, pixels)
        png(ROOT / f'textures/cable/{kind}.png', 32, 16, jacket(kind))
        model = {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'netlab:item/routed_{kind}'}}
        (ROOT / f'models/item/{item}.json').write_text(json.dumps(model, indent=2) + '\n')
        for y in range(128):
            for x in range(128):
                rgba = pixels[y // 4][x // 4]
                if rgba[3]: sheet[(index // 3) * 128 + y][(index % 3) * 128 + x] = rgba
    png(Path('/tmp/netlab-cable-icons.png'), 384, 384, sheet)
    print('Generated 9 cable icons, 9 jacket textures and their item models.')

if __name__ == '__main__': main()
