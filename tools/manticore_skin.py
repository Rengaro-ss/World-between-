"""Texture of the Neris manticore (128x128): a tawny lion's body and dark mane, leathery bat wings, a black sting."""
import random
from pathlib import Path
from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/worldbetween/textures/entity/manticore.png"
rnd = random.Random(7)
img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
px = img.load()


def fill(x0, y0, x1, y1, base, spread=14, dark=None, dark_rows=0):
    for y in range(y0, y1):
        for x in range(x0, x1):
            j = rnd.randint(-spread, spread)
            c = tuple(max(0, min(255, v + j)) for v in base)
            if dark and y >= y1 - dark_rows:
                c = dark
            px[x, y] = c + (255,)


TAWNY = (176, 126, 64)
fill(0, 0, 56, 28, TAWNY)                       # body
for x in range(0, 56):                          # a darker stripe down the spine (top face)
    for y in range(0, 10):
        if 10 <= x < 20 or 38 <= x < 48:
            r, g, b, a = px[x, y]
            px[x, y] = (r - 25, g - 22, b - 15, 255)
fill(0, 28, 28, 42, (190, 140, 78))             # head
for x, y in [(8, 34), (14, 34), (9, 34), (15, 34)]:   # eyes on the front face (u 6..14, v 34..42)
    px[x, y] = (255, 196, 40, 255)
fill(0, 42, 12, 47, (150, 104, 56))             # snout
px[2, 45] = (30, 20, 16, 255)
px[3, 45] = (30, 20, 16, 255)
fill(28, 28, 58, 43, (84, 50, 26), 18)          # mane
fill(0, 48, 12, 61, TAWNY, dark=(70, 50, 36), dark_rows=2)   # legs and paws
fill(12, 48, 34, 59, (150, 100, 52))            # tail
fill(36, 48, 54, 57, (96, 62, 40))              # tail tip
fill(56, 48, 70, 55, (24, 20, 26), 6)           # sting
fill(0, 64, 70, 78, (62, 44, 48), 10)           # wings: dark leather
for x in range(0, 70):                          # the bones of the wing
    for y in range(64, 78):
        if x % 7 == 0 or y == 64:
            px[x, y] = (120, 96, 84, 255)
OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT)
print(OUT)
