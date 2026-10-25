"""Textures for the Kaments' stone path: currency, materials, tools, armour and amulets.

Tool and armour shapes follow the vanilla iron ones (read from the Minecraft client jar), recoloured
into granite and deep monolith by brightness; the rest is drawn by hand.
Usage: python3 tools/stone_items.py <path to minecraft-client.jar>
"""
import io
import random
import sys
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/worldbetween/textures"
ITEMS = ROOT / "item"
ARMOR = ROOT / "models/armor"

GRANITE = [(78, 46, 40), (110, 68, 58), (138, 90, 78), (160, 112, 100), (182, 136, 124), (204, 164, 152)]
MONOLITH = [(24, 24, 30), (40, 40, 48), (58, 58, 68), (80, 79, 92), (104, 101, 118), (130, 124, 146)]
FLINT = [(28, 27, 30), (46, 44, 48), (66, 63, 66), (92, 88, 90), (122, 118, 118), (150, 146, 144)]
VEIN = (176, 96, 255)


def vanilla(jar, path):
    with jar.open("assets/minecraft/textures/" + path) as f:
        return Image.open(io.BytesIO(f.read())).convert("RGBA")


def recolour(image, ramp, only_grey=True, veins=0.0, seed=1, speckle=0.0):
    rnd = random.Random(seed)
    out = image.copy()
    px = out.load()
    cells = [(x, y) for y in range(image.height) for x in range(image.width)
             if px[x, y][3] > 0 and (not only_grey or max(px[x, y][:3]) - min(px[x, y][:3]) < 30)]
    if not cells:
        return out
    lums = [sum(px[x, y][:3]) / 3 for x, y in cells]
    lo, hi = min(lums), max(lums)
    for (x, y), lum in zip(cells, lums):
        t = 0 if hi == lo else (lum - lo) / (hi - lo)
        step = min(len(ramp) - 1, int(t * len(ramp)))
        if speckle and rnd.random() < speckle:
            # Granite is grained: darker flecks and pale feldspar here and there.
            step = max(0, min(len(ramp) - 1, step + rnd.choice((-2, -1, 1))))
        r, g, b = ramp[step]
        if speckle and rnd.random() < speckle / 4:
            r, g, b = (r + 200) // 2 - 10, (g + 190) // 2 - 10, (b + 185) // 2 - 10
        if veins and 0.25 < t < 0.85 and rnd.random() < veins:
            r, g, b = VEIN
        px[x, y] = (r, g, b, px[x, y][3])
    return out


def chisel():
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    handle = [(101, 67, 33), (130, 90, 48), (82, 54, 26)]
    for i in range(7):  # the handle, lower left
        x, y = 2 + i, 13 - i
        px[x, y] = handle[1]
        px[x + 1, y] = handle[0]
        px[x, y - 1] = handle[2] if i % 3 else handle[1]
    for i in range(5):  # the flint blade, upper right
        x, y = 9 + i, 6 - i
        px[x, y] = FLINT[3 + (i % 2)]
        px[x + 1, y] = FLINT[2]
        px[x, y - 1] = FLINT[5] if i == 4 else FLINT[4]
    px[9, 7] = (120, 120, 120, 255)
    px[10, 6] = (150, 150, 150, 255)
    return img


def gem(base, light, dark, seed):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    shape = ["......XX......",
             "....XXXXXX....",
             "...XXXXXXXX...",
             "..XXXXXXXXXX..",
             "..XXXXXXXXXX..",
             ".XXXXXXXXXXXX.",
             ".XXXXXXXXXXXX.",
             "..XXXXXXXXXX..",
             "..XXXXXXXXXX..",
             "...XXXXXXXX...",
             "....XXXXXX....",
             "......XX......"]
    for j, row in enumerate(shape):
        for i, c in enumerate(row):
            if c == "X":
                x, y = i + 1, j + 2
                edge = row[i - 1:i] != "X" or row[i + 1:i + 2] != "X" or j in (0, len(shape) - 1)
                colour = dark if edge else base
                if x + y < 13 and rnd.random() < 0.5:
                    colour = light
                px[x, y] = colour + (255,)
    px[5, 5] = (255, 230, 255, 255)
    px[6, 4] = (255, 230, 255, 255)
    return img


def amulet(charmed):
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    cord = (120, 100, 80, 255)
    for i in range(6):  # the cord, a V from the top corners
        px[3 + i, 1 + i] = cord
        px[12 - i, 1 + i] = cord
    disc = [(5, 7), (11, 7), (4, 8), (12, 8), (4, 9), (12, 9), (4, 10), (12, 10), (4, 11), (12, 11), (5, 12), (11, 12)]
    for y in range(7, 14):
        for x in range(4, 13):
            dx, dy = x - 8, y - 10
            if dx * dx + dy * dy * 1.1 <= 14:
                t = 2 + ((x * 7 + y * 3) % 3)
                px[x, y] = GRANITE[t] + (255,)
    for x, y in [(5, 8), (11, 8), (4, 10), (12, 10), (5, 12), (11, 12), (8, 13)]:
        if px[x, y][3]:
            px[x, y] = GRANITE[1] + (255,)
    if charmed:  # a rune of light cut into the stone
        for x, y in [(8, 8), (8, 9), (8, 10), (8, 11), (7, 9), (9, 9), (7, 11), (9, 11)]:
            px[x, y] = VEIN + (255,)
        px[8, 10] = (235, 200, 255, 255)
    else:
        for x, y in [(8, 9), (8, 10), (8, 11)]:
            px[x, y] = GRANITE[0] + (255,)
    return img


def main():
    jar = zipfile.ZipFile(sys.argv[1])
    ITEMS.mkdir(parents=True, exist_ok=True)
    ARMOR.mkdir(parents=True, exist_ok=True)
    recolour(vanilla(jar, "item/iron_nugget.png"), FLINT, seed=3).save(ITEMS / "flint_shard.png")
    recolour(vanilla(jar, "item/iron_ingot.png"), GRANITE, seed=4, speckle=0.3).save(ITEMS / "hewn_granite.png")
    recolour(vanilla(jar, "item/iron_ingot.png"), MONOLITH, veins=0.12, seed=5).save(ITEMS / "deep_monolith.png")
    gem((150, 60, 200), (205, 140, 255), (80, 24, 120), 6).save(ITEMS / "heartstone.png")
    chisel().save(ITEMS / "chisel.png")
    amulet(False).save(ITEMS / "blank_amulet.png")
    amulet(True).save(ITEMS / "stone_amulet.png")
    for kind in ["sword", "pickaxe", "axe", "shovel", "hoe", "helmet", "chestplate", "leggings", "boots"]:
        source = vanilla(jar, f"item/iron_{kind}.png")
        recolour(source, GRANITE, seed=len(kind), speckle=0.3).save(ITEMS / f"granite_{kind}.png")
        recolour(source, MONOLITH, veins=0.1, seed=len(kind)).save(ITEMS / f"monolith_{kind}.png")
    for layer in (1, 2):
        source = vanilla(jar, f"models/armor/iron_layer_{layer}.png")
        recolour(source, GRANITE, only_grey=False, seed=layer, speckle=0.3).save(ARMOR / f"granite_layer_{layer}.png")
        recolour(source, MONOLITH, only_grey=False, veins=0.06, seed=layer).save(ARMOR / f"monolith_layer_{layer}.png")


if __name__ == "__main__":
    main()
