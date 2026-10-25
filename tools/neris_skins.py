"""Draws the Neris skins (64x64, player skin layout). Run: python3 tools/neris_skins.py <output dir>"""
import random
import sys
from PIL import Image

OUT = sys.argv[1]
SKIN = (176, 112, 64)
SKIN_DARK = (150, 92, 50)
HAIR = (24, 20, 22)
EYE = (8, 6, 8)

# Box layouts: name -> (u, v, w, h, d) of the base layer; the overlay is offset as listed.
HEAD = (0, 0, 8, 8, 8)
BODY = (16, 16, 8, 12, 4)
RARM = (40, 16, 4, 12, 4)
RLEG = (0, 16, 4, 12, 4)
LLEG = (16, 48, 4, 12, 4)
LARM = (32, 48, 4, 12, 4)
OVERLAY = {HEAD: (32, 0), BODY: (16, 32), RARM: (40, 32), RLEG: (0, 32), LLEG: (0, 48), LARM: (48, 48)}


def faces(box):
    """Rectangles of each face: top, bottom, right, front, left, back."""
    u, v, w, h, d = box
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def overlay_box(box):
    ou, ov = OVERLAY[box]
    return (ou, ov) + box[2:]


class Skin:
    def __init__(self, seed):
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.rand = random.Random(seed)

    def jitter(self, color, amount=8):
        r = self.rand.randint(-amount, amount)
        return tuple(max(0, min(255, c + r)) for c in color[:3]) + (255,)

    def fill(self, box, color, rows=None, faces_=None, overlay=False, amount=8):
        b = overlay_box(box) if overlay else box
        for name, (x, y, w, h) in faces(b).items():
            if faces_ and name not in faces_:
                continue
            for j in range(h):
                if rows and name not in ("top", "bottom") and not (rows[0] <= j < rows[1]):
                    continue
                for i in range(w):
                    self.img.putpixel((x + i, y + j), self.jitter(color, amount))

    def px(self, x, y, color):
        self.img.putpixel((x, y), color + (255,) if len(color) == 3 else color)

    def save(self, name):
        self.img.save(f"{OUT}/{name}.png")


def base(seed, beard=False):
    s = Skin(seed)
    for box in (HEAD, BODY, RARM, LARM, RLEG, LLEG):
        s.fill(box, SKIN)
    # Hair: top, back and the upper sides of the head.
    s.fill(HEAD, HAIR, faces_=("top",), amount=4)
    s.fill(HEAD, HAIR, faces_=("back",), amount=4)
    s.fill(HEAD, HAIR, rows=(0, 3), faces_=("right", "left"), amount=4)
    s.fill(HEAD, HAIR, rows=(0, 2), faces_=("front",), amount=4)
    fx, fy = 8, 8  # front of head
    # Black eyes, no whites: the Neris were made in the dark of the forges.
    for x in (1, 2, 5, 6):
        s.px(fx + x, fy + 4, EYE)
    s.px(fx + 1, fy + 3, (40, 30, 26)); s.px(fx + 2, fy + 3, (40, 30, 26))
    s.px(fx + 5, fy + 3, (40, 30, 26)); s.px(fx + 6, fy + 3, (40, 30, 26))
    s.px(fx + 3, fy + 5, SKIN_DARK); s.px(fx + 4, fy + 5, SKIN_DARK)
    for x in range(2, 6):
        s.px(fx + x, fy + 6, (110, 60, 40))
    if beard:
        for x in range(1, 7):
            s.px(fx + x, fy + 7, HAIR)
        s.px(fx + 1, fy + 6, HAIR); s.px(fx + 6, fy + 6, HAIR)
    return s


def clothe(s, shirt, trousers, boots, sleeves=12, belt=(30, 24, 22)):
    s.fill(BODY, shirt)
    s.fill(RARM, shirt, rows=(0, sleeves)); s.fill(LARM, shirt, rows=(0, sleeves))
    s.fill(RARM, shirt, faces_=("top",)); s.fill(LARM, shirt, faces_=("top",))
    s.fill(RLEG, trousers); s.fill(LLEG, trousers)
    s.fill(RLEG, boots, rows=(9, 12)); s.fill(LLEG, boots, rows=(9, 12))
    s.fill(RLEG, boots, faces_=("bottom",)); s.fill(LLEG, boots, faces_=("bottom",))
    x, y, w, h = faces(BODY)["front"]
    for i in range(w):
        s.px(x + i, y + 8, belt)
    for name in ("right", "left", "back"):
        x, y, w, h = faces(BODY)[name]
        for i in range(w):
            s.px(x + i, y + 8, belt)


ORANGE = (214, 120, 40)
BLACK = (32, 28, 32)
BROWN = (92, 62, 40)
GREY = (96, 94, 100)
GOLD = (230, 186, 60)

# Citizen: an orange tunic over brown trousers.
s = base(1)
clothe(s, ORANGE, BROWN, (50, 34, 24), sleeves=5)
s.save("citizen")

# Worker: grey shirt with rolled sleeves and a leather apron.
s = base(2, beard=True)
clothe(s, GREY, (70, 52, 38), (44, 30, 22), sleeves=4)
x, y, w, h = faces(BODY)["front"]
for j in range(2, 12):
    for i in range(1, 7):
        s.px(x + i, y + j, s.jitter((120, 80, 46), 6)[:3])
s.save("worker")

# Guard: a dark gambeson under an orange-and-black tabard.
s = base(3)
clothe(s, (54, 52, 58), BLACK, (30, 26, 26))
x, y, w, h = faces(BODY)["front"]
for j in range(0, 12):
    for i in range(2, 6):
        s.px(x + i, y + j, s.jitter(ORANGE if j % 4 else BLACK, 5)[:3])
s.save("guard")

# Seeker: a charcoal cloak with a hood (on the hat layer), an orange sash.
s = base(4)
CLOAK = (40, 40, 46)
clothe(s, CLOAK, (36, 34, 38), (26, 22, 22))
s.fill(BODY, CLOAK, overlay=True, amount=5)
for name in ("top", "back", "right", "left"):
    s.fill(HEAD, CLOAK, overlay=True, faces_=(name,), amount=5)
hx, hy, hw, hh = faces(overlay_box(HEAD))["front"]
for i in range(hw):
    s.px(hx + i, hy, CLOAK); s.px(hx + i, hy + 1, CLOAK)
for j in range(hh):
    s.px(hx, hy + j, CLOAK); s.px(hx + hw - 1, hy + j, CLOAK)
x, y, w, h = faces(overlay_box(BODY))["front"]
for k in range(8):
    s.px(x + k, y + 1 + k if k < 8 else y + 8, s.jitter(ORANGE, 5)[:3])
s.save("seeker")

# Baron: a black robe with an orange stripe and a gold chain.
s = base(5, beard=True)
clothe(s, BLACK, BLACK, (40, 30, 26), belt=GOLD)
x, y, w, h = faces(BODY)["front"]
for j in range(0, 12):
    s.px(x + 3, y + j, ORANGE); s.px(x + 4, y + j, ORANGE)
for i in range(1, 7):
    s.px(x + i, y + 1 + abs(3 - i) // 2, GOLD)
s.fill(RLEG, BLACK, overlay=True, rows=(0, 9), faces_=("front", "right", "left", "back"), amount=4)
s.fill(LLEG, BLACK, overlay=True, rows=(0, 9), faces_=("front", "right", "left", "back"), amount=4)
s.save("baron")

# King: the baron's robe in gold-trimmed black, and a crown of bronze and gold.
s = base(6, beard=True)
clothe(s, BLACK, BLACK, (40, 30, 26), belt=GOLD)
x, y, w, h = faces(BODY)["front"]
for j in range(0, 12):
    s.px(x, y + j, GOLD); s.px(x + 7, y + j, GOLD)
    s.px(x + 3, y + j, ORANGE); s.px(x + 4, y + j, ORANGE)
for name in ("front", "back", "right", "left"):
    bx, by, bw, bh = faces(overlay_box(HEAD))[name]
    for i in range(bw):
        s.px(bx + i, by, GOLD)
        s.px(bx + i, by + 1, ORANGE if i % 2 else GOLD)
    for i in range(0, bw, 3):
        s.px(bx + i, by, GOLD)
s.save("king")
print("ok")
