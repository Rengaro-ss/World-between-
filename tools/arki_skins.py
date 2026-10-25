"""Draws the Arki textures: the night-sky core with its eyes and gem, and the crystal shards.

    python3 tools/arki_skins.py src/main/resources/assets/worldbetween/textures

Layout matches ArkiModel: a 64x32 sheet, the core at (0, 0), the three rings of shards below.
"""
import random
import sys
import zlib
from pathlib import Path

from PIL import Image

OUT = Path(sys.argv[1] if len(sys.argv) > 1 else "out") / "entity/arki"
ROLES = {
    # core, stars, shard, tip
    "gatherer": ((18, 52, 96), (140, 230, 255), (80, 196, 236), (220, 250, 255)),
    "gardener": ((52, 18, 92), (230, 170, 255), (168, 92, 228), (245, 220, 255)),
    "astronomer": ((12, 12, 52), (230, 230, 255), (78, 88, 228), (205, 210, 255)),
    "keeper": ((96, 104, 148), (255, 255, 255), (226, 226, 255), (170, 130, 255)),
}


def faces(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def clamp(c):
    return tuple(max(0, min(255, int(x))) for x in c)


for role, (core, star, shard, tip) in ROLES.items():
    rand = random.Random(zlib.crc32(role.encode()))
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    for name, (x, y, w, h) in faces(0, 0, 8, 8, 8).items():
        for i in range(w):
            for j in range(h):
                shade = 0.75 + 0.5 * (j / h) * (1 if name != "top" else 0.4)
                color = clamp(c * shade + rand.randint(-8, 8) for c in core)
                if rand.random() < 0.08:
                    color = clamp(s * (0.7 + rand.random() * 0.3) for s in star)
                img.putpixel((x + i, y + j), color + (255,))
    x, y, w, h = faces(0, 0, 8, 8, 8)["front"]
    for ex in (1, 2, 5, 6):
        img.putpixel((x + ex, y + 3), (255, 255, 255, 255))
        img.putpixel((x + ex, y + 4), clamp(s * 0.7 for s in star) + (255,))
    for gx in (3, 4):
        img.putpixel((x + gx, y + 1), tip + (255,))
    for (u, length) in ((0, 8), (8, 7), (16, 5)):
        for name, (sx, sy, sw, sh) in faces(u, 16, 2, length, 2).items():
            for i in range(sw):
                for j in range(sh):
                    t = j / max(1, sh - 1)
                    color = clamp(shard[k] * (1 - t) + tip[k] * t + rand.randint(-10, 10) for k in range(3))
                    if i == 0:
                        color = clamp(c * 1.15 for c in color)
                    img.putpixel((sx + i, sy + j), color + (255,))
    OUT.mkdir(parents=True, exist_ok=True)
    img.save(OUT / f"{role}.png")
print("ok")
