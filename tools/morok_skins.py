"""Textures for Morok (from the Ancient's deepslate skin, cracked with soul-light) and his stone hounds."""
import random
from pathlib import Path
from PIL import Image

TEX = Path(__file__).resolve().parent.parent / "src/main/resources/assets/worldbetween/textures/entity"
rnd = random.Random(11)

# Morok: the Ancient in deepslate, its stone split by glowing violet cracks.
base = Image.open(TEX / "kament/ancient_deepslate.png").convert("RGBA")
px = base.load()
w, h = base.size
for _ in range(w * h // 40):
    x, y = rnd.randrange(w), rnd.randrange(h)
    for _ in range(rnd.randint(2, 6)):  # a short jagged crack
        if 0 <= x < w and 0 <= y < h and px[x, y][3] > 0:
            px[x, y] = (150 + rnd.randint(0, 40), 70 + rnd.randint(0, 30), 230, 255)
        x += rnd.choice((-1, 0, 1))
        y += rnd.choice((0, 1))
for y in range(h):  # darker overall: deep stone
    for x in range(w):
        r, g, b, a = px[x, y]
        if a and not (b > 200 and r > 140):
            px[x, y] = (int(r * 0.75), int(g * 0.72), int(b * 0.8), a)
base.save(TEX / "kament/morok.png")

# The stone hound: grey stone, darker seams, eyes of violet light.
img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
p = img.load()
for y in range(32):
    for x in range(64):
        v = 118 + rnd.randint(-18, 18)
        if rnd.random() < 0.08:
            v -= 40
        p[x, y] = (v, v, v + 4, 255)
for x, y in [(38, 7), (41, 7)]:
    p[x, y] = (200, 120, 255, 255)
    p[x, y + 1] = (150, 80, 230, 255)
img.save(TEX / "stone_hound.png")
print("ok")
