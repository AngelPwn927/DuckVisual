#!/usr/bin/env python3
"""Generates the Duck Visual cosmetics: capes (64x32), masks (32x16) and skins (64x64).

    python3 gen.py [out_dir]

Writes out/capes/*.png, out/masks/*.png, out/skins/*.png and out/catalog.json.
The same files are served by the site (/cosmetics/) and bundled into the mod.
"""
import json, math, os, random, sys
from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "out")

def hx(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)

def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))

def shade(c, k):
    return (max(0, min(255, int(c[0] * k))), max(0, min(255, int(c[1] * k))), max(0, min(255, int(c[2] * k))), c[3])

def grad(stops, t):
    """stops: list of colors evenly spaced."""
    t = max(0.0, min(1.0, t)) * (len(stops) - 1)
    i = min(int(t), len(stops) - 2)
    return mix(stops[i], stops[i + 1], t - i)

T = (0, 0, 0, 0)
YEL, ORA, DARK, INK = hx("ffd43b"), hx("ff8a1f"), hx("15151c"), hx("0b0b0f")
WHITE = hx("f4f4f8")

# ------------------------------------------------------------------ capes
# Cape box uv(0,0) 10x16x1: outer face (1,1) 10x16, inner face (12,1), edges around.
# Elytra uv(22,0) 10x20x2: outer face (24,2) 10x20.

def cape(pattern, lining, edge=None):
    im = Image.new("RGBA", (64, 32), T)
    px = im.load()
    for y in range(16):
        for x in range(10):
            c = pattern(x, y, 10, 16)
            px[1 + x, 1 + y] = c
            px[12 + x, 1 + y] = lining
    e = edge or shade(lining, 0.8)
    for x in range(10):
        px[1 + x, 0] = e; px[11 + x, 0] = e        # top / bottom
    for y in range(16):
        px[0, 1 + y] = e; px[11, 1 + y] = e        # sides
    # elytra: the same pattern stretched to 10x20 on both large faces, edges around
    for y in range(20):
        for x in range(10):
            c = pattern(x, y * 16 / 20, 10, 16)
            px[24 + x, 2 + y] = c
            px[36 + x, 2 + y] = shade(c, 0.85)
    for y in range(20):
        for x in range(2):
            px[22 + x, 2 + y] = e; px[34 + x, 2 + y] = e
    for x in range(10):
        for y in range(2):
            px[24 + x, y] = e; px[34 + x, y] = e
    return im

def icon(rows, colors):
    """rows: strings; returns dict (x,y)->color for non-space chars."""
    out = {}
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch != "." and ch != " ":
                out[(x, y)] = colors[ch]
    return out

def with_icon(base, ic, ox, oy):
    def f(x, y, w, h):
        yy = int(y)
        if (x - ox, yy - oy) in ic:
            return ic[(x - ox, yy - oy)]
        return base(x, y, w, h)
    return f

def border(base, col):
    def f(x, y, w, h):
        if x == 0 or x == w - 1 or int(y) == h - 1:
            return col
        return base(x, y, w, h)
    return f

DUCK = icon([
    "..yy....",
    ".yyyy...",
    ".ykyyoo.",
    ".yyyy...",
    "..yy....",
    "yyyyyy..",
    "yyyyyyy.",
    ".yyyyy..",
], {"y": YEL, "k": INK, "o": ORA})

def p_duck(x, y, w, h):
    return grad([hx("2a1a08"), hx("4a2a08"), hx("1a120a")], y / h)

SWORDS = icon([
    "w......w",
    ".w....w.",
    "..w..w..",
    "...ww...",
    "...ww...",
    "..o..o..",
    ".o....o.",
    "o......o",
], {"w": WHITE, "o": ORA})

BOLT = icon([
    "....yy",
    "...yy.",
    "..yy..",
    ".yyyy.",
    "...yy.",
    "..yy..",
    ".yy...",
    ".y....",
], {"y": YEL})

CROWN = icon([
    "y..y..y.",
    "yy.yy.yy",
    "yyyyyyyy",
    "yoyyyyoy",
    "yyyyyyyy",
], {"y": hx("ffe066"), "o": hx("ff3b3b")})

SKULL = icon([
    ".wwwwww.",
    "wwwwwwww",
    "wkkwwkkw",
    "wkkwwkkw",
    "wwwkkwww",
    ".wwwwww.",
    ".w.ww.w.",
], {"w": WHITE, "k": INK})

def p_sunset(x, y, w, h):
    return grad([hx("ffd43b"), hx("ff8a1f"), hx("e8432c"), hx("5a1b3a")], y / h)

def p_neon(x, y, w, h):
    yy = int(y)
    if (x + yy) % 6 == 0:
        return YEL
    if (x - yy) % 6 == 0:
        return ORA
    return hx("101016")

def p_flame(x, y, w, h):
    rnd = random.Random(x * 31 + 7)
    top = 4 + rnd.randint(0, 5)
    yy = h - 1 - y
    if yy > top + 6:
        return hx("1a0d06")
    return grad([hx("fff1a8"), hx("ffd43b"), hx("ff8a1f"), hx("d8341c"), hx("3a1208")], (yy) / (top + 7))

def p_frost(x, y, w, h):
    c = grad([hx("e9f8ff"), hx("8fd8ff"), hx("2a74c8")], y / h)
    if (x * 7 + int(y) * 3) % 11 == 0:
        return WHITE
    return c

def p_void(x, y, w, h):
    rnd = random.Random(int(x) * 97 + int(y) * 13)
    if rnd.random() < 0.08:
        return WHITE if rnd.random() < 0.5 else hx("d9a3ff")
    return grad([hx("2b0f4a"), hx("170a2a"), hx("06040c")], y / h)

def p_toxic(x, y, w, h):
    yy = int(y)
    drip = random.Random(x).randint(2, 9)
    if yy < drip:
        return hx("7dff3b")
    if yy == drip:
        return hx("3fae19")
    return hx("17220f")

def p_blood(x, y, w, h):
    yy = int(y)
    drip = random.Random(x * 5 + 1).randint(3, 11)
    if yy <= drip:
        return grad([hx("ff3b3b"), hx("9a0f16")], yy / 12)
    return hx("1b0b0d")

def p_dark(x, y, w, h):
    return grad([hx("22222c"), hx("101016")], y / h)

def p_royal(x, y, w, h):
    return grad([hx("3a1458"), hx("1c0a2c"), hx("0c0612")], y / h)

def p_ender(x, y, w, h):
    cx, cy = 4.5, 8
    d = math.hypot(x - cx, (y - cy) * 0.8)
    if d < 2.2:
        return hx("0b3b33")
    if d < 3.6:
        return hx("2fd1a8")
    return grad([hx("0e2b2a"), hx("071413")], y / h)

def p_rainbow(x, y, w, h):
    stops = [hx("ff3b3b"), hx("ff8a1f"), hx("ffd43b"), hx("4ce06a"), hx("3ba8ff"), hx("9b5cff")]
    return grad(stops, (y + x * 0.5) / (h + 5))

def p_stripe(x, y, w, h):
    return YEL if (x // 2) % 2 == 0 else INK

def p_camo(x, y, w, h):
    v = random.Random(int(x / 2) * 17 + int(y / 2) * 5).random()
    return [hx("2f3a1e"), hx("4b5a2c"), hx("6b5b33"), hx("1c2212")][int(v * 4)]

CAPES = [
    # id, name, rarity, function
    ("duck", "Duck", "legendary", border(with_icon(p_duck, DUCK, 1, 4), YEL)),
    ("sunset", "Sunset", "rare", p_sunset),
    ("neon", "Neon Grid", "epic", p_neon),
    ("flame", "Inferno", "epic", p_flame),
    ("frost", "Frost", "rare", p_frost),
    ("void", "Void", "epic", border(p_void, hx("9b5cff"))),
    ("toxic", "Toxic", "rare", p_toxic),
    ("blood", "Bloodlust", "epic", p_blood),
    ("duelist", "Duelist", "rare", border(with_icon(p_dark, SWORDS, 1, 4), ORA)),
    ("thunder", "Thunder", "rare", border(with_icon(p_dark, BOLT, 2, 4), YEL)),
    ("champion", "Champion", "legendary", border(with_icon(p_royal, CROWN, 1, 4), hx("ffcf3b"))),
    ("reaper", "Reaper", "epic", with_icon(p_dark, SKULL, 1, 4)),
    ("ender", "Ender", "rare", p_ender),
    ("rainbow", "Spectrum", "rare", p_rainbow),
    ("hazard", "Hazard", "common", p_stripe),
    ("camo", "Camo", "common", p_camo),
]
CAPE_LINING = {"duck": hx("3a2408"), "champion": hx("1c0a2c"), "frost": hx("c8ecff"), "toxic": hx("0f160a"),
               "blood": hx("2a0a0c"), "rainbow": hx("2a2a36"), "hazard": INK, "camo": hx("1c2212")}

# ------------------------------------------------------------------ masks
# 32x16 texture, box uv(0,0) 8x8x8: front (8,8), right (0,8), left (16,8), back (24,8), top (8,0).

def mask(front, strap=None, strap_rows=(), sides=None):
    im = Image.new("RGBA", (32, 16), T)
    px = im.load()
    for (x, y), c in front.items():
        px[8 + x, 8 + y] = c
    for y in strap_rows:
        for x in range(8):
            px[0 + x, 8 + y] = strap   # right side
            px[16 + x, 8 + y] = strap  # left side
            px[24 + x, 8 + y] = strap  # back
    if sides:
        for (x, y), c in sides.items():
            px[0 + x, 8 + y] = c
            px[16 + 7 - x, 8 + y] = c
    return im

def face(rows, colors):
    return icon(rows, colors)

STRAP = hx("1b1b22")
MASKS = [
    ("duck", "Duck Beak", "legendary", mask(face([
        "........",
        "........",
        "........",
        "........",
        ".oooooo.",
        "oyyyyyyo",
        ".oooooo.",
        "........",
    ], {"o": ORA, "y": YEL}), STRAP, (4,))),
    ("ninja", "Ninja", "rare", mask(face([
        "........",
        "........",
        "........",
        "........",
        "kkkkkkkk",
        "kkkkkkkk",
        "kkkkkkkk",
        "kkkkkkkk",
    ], {"k": hx("121218")}), hx("121218"), (4, 5, 6, 7))),
    ("hockey", "Hockey", "epic", mask(face([
        "..wwww..",
        ".wwrwww.",
        "wwkkwkkw",
        "wwkkwkkw",
        "wrwwwwrw",
        "wwkwwkww",
        ".wwkkww.",
        "..wwww..",
    ], {"w": hx("ece6d6"), "k": INK, "r": hx("d8341c")}), STRAP, (2,))),
    ("visor", "Cyber Visor", "epic", mask(face([
        "........",
        "........",
        "kkkkkkkk",
        "yyyooyyy",
        "kkkkkkkk",
        "........",
        "........",
        "........",
    ], {"k": hx("202028"), "y": YEL, "o": ORA}), hx("202028"), (2, 3, 4))),
    ("skull", "Skull", "epic", mask(face([
        ".wwwwww.",
        "wwwwwwww",
        "wkkwwkkw",
        "wkkwwkkw",
        "wwwkkwww",
        "wwwwwwww",
        "wkwkwkww",
        ".wwwwww.",
    ], {"w": hx("e9e4d4"), "k": INK}), STRAP, (2,))),
    ("oni", "Oni", "legendary", mask(face([
        "w......w",
        "rrrrrrrr",
        "ryyrryyr",
        "rkkrrkkr",
        "rrrkkrrr",
        "rkwkkwkr",
        "rkkkkkkr",
        ".rrrrrr.",
    ], {"r": hx("c8241c"), "y": YEL, "k": INK, "w": WHITE}), STRAP, (2,))),
    ("gas", "Gas Mask", "rare", mask(face([
        "........",
        "........",
        "kggkkggk",
        "kggkkggk",
        "kkkkkkkk",
        ".kksskk.",
        ".kkssk..",
        "..kkkk..",
    ], {"k": hx("2c3324"), "g": hx("7fd6ff"), "s": hx("6b6b6b")}), STRAP, (3, 4))),
    ("fox", "Fox", "rare", mask(face([
        "o......o",
        "oo....oo",
        "oooooooo",
        "okkookko",
        "wwooooww",
        "wwwkkwww",
        ".wwwwww.",
        "........",
    ], {"o": hx("ff7a1a"), "k": INK, "w": WHITE}), STRAP, (2,))),
    ("shades", "Shades", "common", mask(face([
        "........",
        "........",
        "........",
        "kkkkkkkk",
        "kyykkyyk",
        ".kk..kk.",
        "........",
        "........",
    ], {"k": INK, "y": hx("ff9a2a")}), INK, (3,))),
]

# ------------------------------------------------------------------ skins
# Standard 64x64 layout. Each box: (u, v, w, h, d).
HEAD, HAT = (0, 0, 8, 8, 8), (32, 0, 8, 8, 8)
BODY, JACKET = (16, 16, 8, 12, 4), (16, 32, 8, 12, 4)
RARM, RSLEEVE = (40, 16, 4, 12, 4), (40, 32, 4, 12, 4)
LARM, LSLEEVE = (32, 48, 4, 12, 4), (48, 48, 4, 12, 4)
RLEG, RPANTS = (0, 16, 4, 12, 4), (0, 32, 4, 12, 4)
LLEG, LPANTS = (16, 48, 4, 12, 4), (0, 48, 4, 12, 4)

def faces(box):
    u, v, w, h, d = box
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h),
    }

class Skin:
    def __init__(self):
        self.im = Image.new("RGBA", (64, 64), T)
        self.px = self.im.load()

    def fill(self, box, fn):
        """fn(face, x, y, w, h) -> color or None."""
        for name, (fx, fy, fw, fh) in faces(box).items():
            for y in range(fh):
                for x in range(fw):
                    c = fn(name, x, y, fw, fh)
                    if c is not None:
                        self.px[fx + x, fy + y] = c

    def paint(self, box, face_name, rows, colors, ox=0, oy=0):
        fx, fy, fw, fh = faces(box)[face_name]
        for y, r in enumerate(rows):
            for x, ch in enumerate(r):
                if ch not in ". ":
                    self.px[fx + ox + x, fy + oy + y] = colors[ch]

def noise(c, x, y, amt=0.06, seed=0):
    k = 1 + (random.Random(x * 131 + y * 71 + seed).random() - 0.5) * 2 * amt
    return shade(c, k)

def solid(c, amt=0.05, top=None, bottom=None):
    def f(face, x, y, w, h):
        if face == "top" and top: return noise(top, x, y, amt)
        if face == "bottom" and bottom: return noise(bottom, x, y, amt)
        return noise(c, x, y, amt, 3)
    return f

def vgrad(stops, amt=0.04):
    def f(face, x, y, w, h):
        if face in ("top", "bottom"):
            return noise(stops[0] if face == "top" else stops[-1], x, y, amt)
        return noise(grad(stops, y / max(1, h - 1)), x, y, amt)
    return f

def make_skin(spec):
    s = Skin()
    sk, hair, eye = spec["skin"], spec["hair"], spec["eye"]
    s.fill(HEAD, lambda f, x, y, w, h: noise(hair, x, y) if f in ("top", "back") or (f in ("left", "right") and y < 3) or (f == "front" and y < 2) else noise(sk, x, y, 0.03))
    s.paint(HEAD, "front", ["", "", "", "", "", "", "...mm..."], {"m": shade(sk, 0.7)})
    s.fill(BODY, spec["torso"])
    s.fill(RARM, spec["arms"])
    s.fill(LARM, spec["arms"])
    s.fill(RLEG, spec["legs"])
    s.fill(LLEG, spec["legs"])
    for box, key in ((HAT, "hat"), (JACKET, "jacket"), (RSLEEVE, "sleeve"), (LSLEEVE, "sleeve"), (RPANTS, "pants"), (LPANTS, "pants")):
        if key in spec:
            s.fill(box, spec[key])
    for fn in spec.get("extra", []):
        fn(s)
    return s.im

def shoes(col, rows=2):
    def f(base):
        def g(face, x, y, w, h):
            if face == "bottom" or (face not in ("top",) and y >= h - rows):
                return noise(col, x, y)
            return base(face, x, y, w, h)
        return g
    return f

def hands(col, base):
    def g(face, x, y, w, h):
        if face == "bottom" or (face != "top" and y >= h - 2):
            return noise(col, x, y, 0.03)
        return base(face, x, y, w, h)
    return g

def belt(col, base, at=8):
    def g(face, x, y, w, h):
        if face not in ("top", "bottom") and y == at:
            return col
        return base(face, x, y, w, h)
    return g

def stripe_v(col, base, xs):
    def g(face, x, y, w, h):
        if face == "front" and x in xs:
            return col
        return base(face, x, y, w, h)
    return g

def logo(s):
    s.paint(BODY, "front", ["..yy....", ".yyyoo..", "..yy....", ".yyyy..."], {"y": YEL, "o": ORA}, 0, 3)

SKIN_TONE, DARK_TONE = hx("e8b48c"), hx("a8724c")
SKINS = [
    ("duckling", "Duckling", "legendary", {
        "skin": hx("ffe066"), "hair": hx("ffd43b"), "eye": INK,
        "torso": vgrad([hx("fff1a8"), YEL, hx("f5b82a")]),
        "arms": solid(YEL), "legs": shoes(ORA, 3)(solid(hx("f5c22a"))),
        "hat": lambda f, x, y, w, h: (ORA if f == "front" and y in (5, 6) and 1 <= x <= 6 else None),
        "extra": [],
    }),
    ("warrior", "Duck Warrior", "epic", {
        "skin": SKIN_TONE, "hair": hx("2a1a0a"), "eye": hx("ff8a1f"),
        "torso": belt(hx("111111"), stripe_v(YEL, vgrad([ORA, hx("d8641a")]), (3, 4)), 9),
        "arms": hands(SKIN_TONE, vgrad([ORA, hx("d8641a")])),
        "legs": shoes(hx("101010"))(solid(hx("1e1e26"))),
        "jacket": lambda f, x, y, w, h: (hx("15151c") if f in ("front", "back") and y < 3 else None),
        "extra": [logo],
    }),
    ("ninja", "Shadow Ninja", "epic", {
        "skin": SKIN_TONE, "hair": hx("101014"), "eye": hx("ff3b3b"),
        "torso": belt(ORA, solid(hx("15151c")), 8),
        "arms": solid(hx("15151c")), "legs": shoes(hx("0b0b0f"))(solid(hx("15151c"))),
        "hat": lambda f, x, y, w, h: (hx("15151c") if (f == "front" and y >= 5) or (f in ("left", "right", "back") and y >= 4) else (ORA if f in ("front", "left", "right", "back") and y == 2 else None)),
    }),
    ("neon", "Neon Runner", "rare", {
        "skin": DARK_TONE, "hair": hx("ffd43b"), "eye": hx("3bf0ff"),
        "torso": lambda f, x, y, w, h: (YEL if (f == "front" and (x == 1 or x == 6)) or y == 11 else noise(hx("14141a"), x, y)),
        "arms": lambda f, x, y, w, h: (ORA if y in (3, 9) else (DARK_TONE if y >= h - 2 else noise(hx("14141a"), x, y))),
        "legs": lambda f, x, y, w, h: (YEL if (f == "front" and x == 0) or y >= h - 2 else noise(hx("1b1b22"), x, y)),
    }),
    ("knight", "Crimson Knight", "legendary", {
        "skin": SKIN_TONE, "hair": hx("8a8f9a"), "eye": hx("ff3b3b"),
        "torso": lambda f, x, y, w, h: (hx("b8222a") if f == "front" and 2 <= x <= 5 and y >= 2 else noise(hx("9aa0aa"), x, y, 0.1)),
        "arms": solid(hx("8a8f9a"), 0.1), "legs": shoes(hx("5a5f6a"))(solid(hx("7a7f8a"), 0.1)),
        "hat": lambda f, x, y, w, h: (noise(hx("9aa0aa"), x, y, 0.1) if f in ("top", "back", "left", "right") or (f == "front" and (y < 3 or x in (0, 7))) else (INK if f == "front" and y == 3 else None)),
    }),
    ("frost", "Ice Assassin", "rare", {
        "skin": hx("f0d0b8"), "hair": hx("e9f8ff"), "eye": hx("3ba8ff"),
        "torso": vgrad([hx("e9f8ff"), hx("8fd8ff"), hx("2a74c8")]),
        "arms": hands(hx("f0d0b8"), vgrad([hx("e9f8ff"), hx("8fd8ff")])),
        "legs": shoes(hx("1a3a6a"))(solid(hx("2a4a7a"))),
        "hat": lambda f, x, y, w, h: (hx("ffffff") if f == "top" or (f != "bottom" and y < 2) else None),
    }),
    ("inferno", "Inferno", "epic", {
        "skin": DARK_TONE, "hair": hx("ff3b1b"), "eye": YEL,
        "torso": vgrad([hx("1a0d06"), hx("d8341c"), hx("ff8a1f")]),
        "arms": vgrad([hx("1a0d06"), hx("d8341c"), YEL]),
        "legs": shoes(hx("ffd43b"))(vgrad([hx("2a1208"), hx("1a0d06")])),
    }),
    ("gold", "Golden Champion", "legendary", {
        "skin": SKIN_TONE, "hair": hx("ffe066"), "eye": hx("1a7aff"),
        "torso": belt(hx("8a5a08"), vgrad([hx("ffe066"), hx("e6a61a")]), 8),
        "arms": hands(SKIN_TONE, solid(hx("e6a61a"))), "legs": shoes(hx("8a5a08"))(solid(hx("c48a12"))),
        "hat": lambda f, x, y, w, h: (hx("ffe066") if f != "bottom" and y == 0 and f != "top" else (hx("ff3b3b") if f == "front" and y == 1 and x in (3, 4) else None)),
    }),
]

def main():
    random.seed(7)
    for d in ("capes", "masks", "skins"):
        os.makedirs(os.path.join(OUT, d), exist_ok=True)
    cat = {"capes": [], "masks": [], "skins": []}
    for cid, name, rar, fn in CAPES:
        cape(fn, CAPE_LINING.get(cid, hx("1d1d26"))).save(os.path.join(OUT, "capes", cid + ".png"))
        cat["capes"].append({"id": cid, "name": name, "rarity": rar})
    for mid, name, rar, im in MASKS:
        im.save(os.path.join(OUT, "masks", mid + ".png"))
        cat["masks"].append({"id": mid, "name": name, "rarity": rar})
    for sid, name, rar, spec in SKINS:
        im = make_skin(spec)
        # eyes: white + iris on row 4 of the face
        px = im.load()
        for x, c in ((1, WHITE), (2, spec["eye"]), (5, spec["eye"]), (6, WHITE)):
            px[8 + x, 8 + 4] = c
        for x in (1, 2, 5, 6):
            px[8 + x, 8 + 3] = shade(spec["hair"], 0.8) if sid != "duckling" else px[8 + x, 8 + 3]
        if sid == "duckling":
            for x in range(1, 7):
                px[8 + x, 8 + 3] = hx("ffd43b")
        im.save(os.path.join(OUT, "skins", sid + ".png"))
        cat["skins"].append({"id": sid, "name": name, "rarity": rar, "model": "classic"})
    with open(os.path.join(OUT, "catalog.json"), "w", encoding="utf-8") as f:
        json.dump(cat, f, ensure_ascii=False, indent=1)
    print("capes", len(CAPES), "masks", len(MASKS), "skins", len(SKINS))

if __name__ == "__main__":
    main()
