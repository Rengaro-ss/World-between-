"""Draws the Kament textures: every role in every stone, the glowing-eyes layers and the status icon.

    python3 tools/kament_skins.py src/main/resources/assets/worldbetween/textures

The layout matches KamentModel: a 128x64 sheet, each box unwrapped the way Minecraft maps cuboids.
"""
import random
import zlib
import sys
from pathlib import Path

from PIL import Image

OUT = Path(sys.argv[1] if len(sys.argv) > 1 else "out")

# name: (u, v, width, height, depth)
BOXES = {
    "head": (0, 0, 8, 9, 8),
    "body": (32, 0, 12, 18, 7),
    "rarm": (0, 17, 6, 22, 6),
    "larm": (24, 25, 6, 22, 6),
    "rpaul": (70, 0, 7, 5, 7),
    "lpaul": (98, 0, 7, 5, 7),
    "rleg": (48, 25, 5, 12, 5),
    "lleg": (68, 25, 5, 12, 5),
    "ca": (88, 25, 2, 4, 2),
    "cb": (96, 25, 2, 3, 2),
    "cr": (104, 25, 2, 4, 2),
    "cl": (112, 25, 2, 3, 2),
}
CRYSTALS = ("ca", "cb", "cr", "cl")

MATERIALS = {
    "stone": ((125, 125, 125), None),
    "andesite": ((134, 136, 134), (160, 162, 160)),
    "diorite": ((196, 194, 190), (120, 118, 116)),
    "granite": ((152, 104, 88), (186, 134, 116)),
    "tuff": ((106, 109, 98), (128, 126, 110)),
    "deepslate": ((76, 76, 82), (56, 56, 62)),
}
ROLES = ("worker", "guard", "sculptor", "ancient")
MOSS = (74, 112, 48)
AMETHYST = (168, 110, 214)
SOCKET = (18, 18, 22)
EYES = {
    "worker": (150, 228, 255),
    "guard": (255, 182, 72),
    "sculptor": (206, 150, 255),
    "ancient": (255, 240, 190),
}


def faces(box):
    u, v, w, h, d = BOXES[box]
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def clamp(c):
    return tuple(max(0, min(255, int(x))) for x in c)


def shade(color, factor):
    return clamp(x * factor for x in color)


class Sheet:
    def __init__(self, seed, transparent=False):
        self.img = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
        self.rand = random.Random(seed)

    def px(self, x, y, color, alpha=255):
        if 0 <= x < 128 and 0 <= y < 64:
            self.img.putpixel((x, y), clamp(color) + (alpha,))

    def get(self, x, y):
        return self.img.getpixel((x, y))[:3]

    def save(self, path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self.img.save(path)


def stone(sheet, material):
    base, speck = MATERIALS[material]
    for box in BOXES:
        if box in CRYSTALS:
            continue
        for name, (x, y, w, h) in faces(box).items():
            light = {"top": 1.12, "bottom": 0.78, "front": 1.0, "back": 0.9, "right": 0.94, "left": 0.94}[name]
            for i in range(w):
                for j in range(h):
                    n = sheet.rand.randint(-11, 11)
                    # Coarse grain: rock is blotchy, not noisy.
                    if (i // 2 + j // 2 + zlib.crc32(box.encode()) % 3) % 5 == 0:
                        n -= 8
                    color = shade(tuple(c + n for c in base), light)
                    if speck and sheet.rand.random() < 0.12:
                        color = shade(speck, light)
                    if material == "deepslate" and j % 4 == 0:
                        color = shade(color, 0.85)
                    sheet.px(x + i, y + j, color)


def cracks(sheet, count):
    for _ in range(count):
        box = sheet.rand.choice(["body", "rarm", "larm", "rleg", "lleg", "head"])
        name = sheet.rand.choice(["front", "back", "right", "left"])
        x, y, w, h = faces(box)[name]
        cx, cy = x + sheet.rand.randrange(w), y + sheet.rand.randrange(h)
        for _ in range(sheet.rand.randint(3, 7)):
            if x <= cx < x + w and y <= cy < y + h:
                sheet.px(cx, cy, shade(sheet.get(cx, cy), 0.55))
            cx += sheet.rand.choice((-1, 0, 1))
            cy += 1


def face(sheet):
    x, y, w, h = faces("head")["front"]
    for i in range(w):
        sheet.px(x + i, y + 2, shade(sheet.get(x + i, y + 2), 0.75))
    for ex in (1, 2, 5, 6):
        sheet.px(x + ex, y + 4, SOCKET)
        sheet.px(x + ex, y + 5, shade(SOCKET, 1.6))
    for mx in range(2, 6):
        sheet.px(x + mx, y + 7, shade(sheet.get(x + mx, y + 7), 0.6))


def crystals(sheet):
    for box in CRYSTALS:
        for name, (x, y, w, h) in faces(box).items():
            for i in range(w):
                for j in range(h):
                    sheet.px(x + i, y + j, shade(AMETHYST, 1.15 - 0.12 * j + (0.1 if i == 0 else 0)))


def role_marks(sheet, role):
    bx, by, bw, bh = faces("body")["front"]
    if role == "worker":
        for _ in range(40):
            i, j = sheet.rand.randrange(bw), sheet.rand.randrange(bh)
            sheet.px(bx + i, by + j, shade(sheet.get(bx + i, by + j), 1.25))
        for i in range(bw):
            sheet.px(bx + i, by + 12, shade((110, 104, 96), 1 + sheet.rand.random() * 0.2))
    elif role == "guard":
        for i in range(2, 10):
            for j in range(3, 11):
                edge = i in (2, 9) or j in (3, 10)
                sheet.px(bx + i, by + j, shade(sheet.get(bx + i, by + j), 0.7 if edge else 1.2))
        for paul in ("rpaul", "lpaul"):
            for name, (x, y, w, h) in faces(paul).items():
                for i in range(w):
                    for j in range(h):
                        if (j + i // 3) % 3 == 0:
                            sheet.px(x + i, y + j, shade(sheet.get(x + i, y + j), 0.72))
    elif role == "sculptor":
        for k in range(10):
            sx, sy = sheet.rand.randrange(bw - 3), sheet.rand.randrange(bh - 3)
            for t in range(3):
                sheet.px(bx + sx + t, by + sy + t, shade(sheet.get(bx + sx + t, by + sy + t), 1.3))
        for i in range(4, 8):
            for j in range(5, 9):
                sheet.px(bx + i, by + j, shade(AMETHYST, 0.9 + 0.05 * (i + j) % 2))
    elif role == "ancient":
        for box in ("head", "body", "rpaul", "lpaul", "rarm", "larm"):
            x, y, w, h = faces(box)["top"]
            for i in range(w):
                for j in range(h):
                    if sheet.rand.random() < 0.65:
                        sheet.px(x + i, y + j, shade(MOSS, 0.85 + sheet.rand.random() * 0.3))
            for name in ("front", "back", "left", "right"):
                x, y, w, h = faces(box)[name]
                for i in range(w):
                    for j in range(min(h, 3)):
                        if sheet.rand.random() < 0.5 - j * 0.15:
                            sheet.px(x + i, y + j, shade(MOSS, 0.8 + sheet.rand.random() * 0.3))
        # Runes of the old race, inlaid in white stone.
        for k in range(4):
            ry = by + 4 + k * 3
            for i in range(3, 9):
                if (i + k) % 3:
                    sheet.px(bx + i, ry, (214, 212, 206))
        cracks(sheet, 10)


def eyes(role):
    sheet = Sheet(0)
    color = EYES[role]
    x, y, w, h = faces("head")["front"]
    for ex in (1, 2, 5, 6):
        sheet.px(x + ex, y + 4, color)
        sheet.px(x + ex, y + 5, shade(color, 0.55), 200)
    if role == "sculptor":
        bx, by, bw, bh = faces("body")["front"]
        for i in range(4, 8):
            for j in range(5, 9):
                sheet.px(bx + i, by + j, (150, 70, 200) if (i in (5, 6) and j in (6, 7)) else (90, 40, 130), 255)
    if role in ("ancient", "sculptor"):
        for box in CRYSTALS:
            for name, (cx, cy, cw, ch) in faces(box).items():
                for i in range(cw):
                    for j in range(ch):
                        sheet.px(cx + i, cy + j, (110, 60, 160), 255)
    sheet.save(OUT / "entity/kament" / f"eyes_{role}.png")


def icon():
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    rand = random.Random(7)
    for x in range(18):
        for y in range(18):
            # A mountain against nothing: the land of stone.
            top = 16 - min(abs(x - 9) * 1.6, 14) if 2 <= x <= 15 else 99
            if y >= top and y <= 16:
                v = 118 + rand.randint(-14, 14) + (12 if y < top + 2 else 0)
                img.putpixel((x, y), (v, v, v - 4, 255))
    for x, y in ((7, 9), (10, 9)):
        img.putpixel((x, y), (150, 228, 255, 255))
    out = OUT / "mob_effect/kament_land.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)


for role in ROLES:
    for material in MATERIALS:
        sheet = Sheet(zlib.crc32(f"{role}/{material}".encode()))
        stone(sheet, material)
        cracks(sheet, 8)
        face(sheet)
        crystals(sheet)
        role_marks(sheet, role)
        sheet.save(OUT / "entity/kament" / f"{role}_{material}.png")
    eyes(role)
icon()
print("ok")
