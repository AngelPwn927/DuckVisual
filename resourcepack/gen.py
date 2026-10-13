#!/usr/bin/env python3
"""Builds the Duck PvP resource pack for Minecraft 1.21.4 (pack_format 46).

    python3 gen.py path/to/minecraft-1.21.4-client.jar [out.zip]

Short swords in duck colours, low fire, a clean hotbar and crosshair, an orange enchantment glint,
a duck totem, a lower shield and no pumpkin blur. The client jar is needed for the textures that
are edited rather than drawn from scratch (fire, enchantment glint, shield model).
"""
import colorsys, io, json, sys, zipfile
from PIL import Image, ImageDraw

JAR = sys.argv[1]
OUT = sys.argv[2] if len(sys.argv) > 2 else "Duck-PvP.zip"
T = (0, 0, 0, 0)

def hx(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)

def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))

YEL, ORA, INK = hx("ffd43b"), hx("ff8a1f"), hx("0b0b0f")
files = {}

def png(path, im):
    b = io.BytesIO()
    im.save(b, "PNG")
    files["assets/minecraft/" + path] = b.getvalue()

def js(path, obj):
    files[path if path.startswith("pack") else "assets/minecraft/" + path] = json.dumps(obj, indent=1).encode()

jar = zipfile.ZipFile(JAR)
def vanilla(path):
    return Image.open(io.BytesIO(jar.read("assets/minecraft/" + path))).convert("RGBA")

# ------------------------------------------------------------------ swords
TIERS = {  # highlight, body, shade, outline
    "wooden": ("d8ad6a", "a87a3e", "74521f", "3a2810"),
    "stone": ("c8c8cc", "92929a", "62626a", "26262c"),
    "iron": ("ffffff", "dfe3ea", "a9afba", "3e424a"),
    "golden": ("fff3a0", "ffd43b", "e09a18", "6a4206"),
    "diamond": ("d4fff8", "4ee8d8", "1a9ea6", "0a3c40"),
    "netherite": ("7a6a78", "4e424e", "2e262e", "ff8a1f"),
}

def sword(tier):
    hi, mid, lo, out = (hx(c) for c in TIERS[tier])
    im = Image.new("RGBA", (16, 16), T)
    px = im.load()
    blade = {}
    for t in range(10):
        x, y = 5 + t, 10 - t
        blade[(x, y - 1)] = hi
        blade[(x, y)] = mid
        blade[(x + 1, y)] = lo
    blade[(15, 0)] = hi
    for (x, y), c in blade.items():
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = c
    # outline around the blade (netherite gets an orange glow edge)
    for (x, y) in list(blade):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if q not in blade and 0 <= q[0] < 16 and 0 <= q[1] < 16 and px[q] == T and q[0] + q[1] >= 13 and q[0] >= 4:
                px[q] = out
    # guard, grip, pommel
    for i, (x, y) in enumerate(((2, 9), (3, 10), (4, 11), (5, 12), (6, 13))):
        px[x, y] = YEL if i in (1, 2) else ORA
    for (x, y) in ((1, 9), (7, 13), (2, 8), (6, 14)):
        px[x, y] = hx("8a3d08")
    for (x, y) in ((3, 12), (2, 13), (1, 14)):
        px[x, y] = hx("2a2a33")
    for (x, y) in ((4, 12), (3, 13), (2, 14)):
        px[x, y] = hx("15151c")
    px[0, 15] = YEL
    px[1, 15] = ORA
    px[0, 14] = ORA
    return im

for tier in TIERS:
    png(f"textures/item/{tier}_sword.png", sword(tier))
    js(f"models/item/{tier}_sword.json", {"parent": "minecraft:item/duck_short_sword", "textures": {"layer0": f"minecraft:item/{tier}_sword"}})
handheld = json.loads(jar.read("assets/minecraft/models/item/handheld.json"))
short = {"parent": "minecraft:item/handheld", "display": dict(handheld["display"])}
short["display"]["firstperson_righthand"] = {"rotation": [0, -90, 25], "translation": [1.13, 2.6, 1.13], "scale": [0.42, 0.42, 0.42]}
short["display"]["firstperson_lefthand"] = {"rotation": [0, 90, -25], "translation": [1.13, 2.6, 1.13], "scale": [0.42, 0.42, 0.42]}
js("models/item/duck_short_sword.json", short)

# ------------------------------------------------------------------ shield: lower and a bit smaller in first person
shield = json.loads(jar.read("assets/minecraft/models/item/shield.json"))
shield["display"]["firstperson_righthand"] = {"rotation": [0, 180, 5], "translation": [-10, -2, -10], "scale": [1.05, 1.05, 1.05]}
shield["display"]["firstperson_lefthand"] = {"rotation": [0, 180, 5], "translation": [10, -4, -10], "scale": [1.05, 1.05, 1.05]}
js("models/item/shield.json", shield)

# ------------------------------------------------------------------ low fire: every animation frame moved down
for name in ("fire_0", "fire_1"):
    src = vanilla(f"textures/block/{name}.png")
    w, h = src.size
    out = Image.new("RGBA", (w, h), T)
    drop = w * 3 // 16
    for f in range(h // w):
        frame = src.crop((0, f * w, w, f * w + w))
        out.paste(frame.crop((0, 0, w, w - drop)), (0, f * w + drop))
    png(f"textures/block/{name}.png", out)
    files[f"assets/minecraft/textures/block/{name}.png.mcmeta"] = jar.read(f"assets/minecraft/textures/block/{name}.png.mcmeta")

# ------------------------------------------------------------------ orange enchantment glint
for name in ("enchanted_glint_item", "enchanted_glint_entity"):
    src = vanilla(f"textures/misc/{name}.png")
    px = src.load()
    for y in range(src.height):
        for x in range(src.width):
            r, g, b, a = px[x, y]
            l = (r + g + b) / 3 / 255
            px[x, y] = (min(255, int(255 * l * 1.05)), int(150 * l), int(30 * l), a)
    png(f"textures/misc/{name}.png", src)

# ------------------------------------------------------------------ no pumpkin blur, just a soft frame
pb = Image.new("RGBA", (256, 256), T)
d = pb.load()
for y in range(256):
    for x in range(256):
        e = min(x, y, 255 - x, 255 - y)
        if e < 18:
            d[x, y] = (20, 12, 4, int(150 * (1 - e / 18)))
png("textures/misc/pumpkinblur.png", pb)

# ------------------------------------------------------------------ hud
BG, LINE = (14, 14, 19, 200), (40, 40, 50, 230)
hb = Image.new("RGBA", (182, 22), T)
dr = ImageDraw.Draw(hb)
dr.rounded_rectangle((0, 0, 181, 21), 3, fill=BG)
for x in range(182):  # gradient frame
    c = mix(YEL, ORA, x / 181)
    hb.putpixel((x, 0), c[:3] + (210,))
    hb.putpixel((x, 21), c[:3] + (120,))
hb.putpixel((0, 0), T); hb.putpixel((181, 0), T); hb.putpixel((0, 21), T); hb.putpixel((181, 21), T)
for y in range(1, 21):
    hb.putpixel((0, y), YEL[:3] + (160,)); hb.putpixel((181, y), ORA[:3] + (160,))
for i in range(9):
    x0 = 1 + i * 20
    dr.rectangle((x0 + 2, 3, x0 + 17, 18), fill=(28, 28, 36, 205))
    if i:
        dr.line((x0, 4, x0, 17), fill=LINE)
png("textures/gui/sprites/hud/hotbar.png", hb)

sel = Image.new("RGBA", (24, 23), T)
sp = sel.load()
for y in range(23):
    for x in range(24):
        edge = min(x, y, 23 - x, 22 - y)
        corner = (x in (0, 23)) and (y in (0, 22))
        if edge <= 1 and not corner:
            sp[x, y] = mix(YEL, ORA, (x + y) / 45)
        elif edge == 2:
            sp[x, y] = (255, 190, 60, 70)
png("textures/gui/sprites/hud/hotbar_selection.png", sel)

ch = Image.new("RGBA", (15, 15), T)
for i in list(range(2, 6)) + list(range(9, 13)):
    ch.putpixel((i, 7), (255, 255, 255, 255))
    ch.putpixel((7, i), (255, 255, 255, 255))
ch.putpixel((7, 7), (255, 255, 255, 255))
png("textures/gui/sprites/hud/crosshair.png", ch)

xb = Image.new("RGBA", (182, 5), (20, 20, 26, 220))
for x in range(182):
    xb.putpixel((x, 0), (60, 60, 72, 255)); xb.putpixel((x, 4), (8, 8, 10, 255))
png("textures/gui/sprites/hud/experience_bar_background.png", xb)
xp = Image.new("RGBA", (182, 5), T)
for x in range(182):
    c = mix(YEL, ORA, x / 181)
    for y in range(5):
        xp.putpixel((x, y), mix(c, (255, 255, 255, 255), 0.35) if y == 1 else c if y < 4 else mix(c, INK, 0.4))
png("textures/gui/sprites/hud/experience_bar_progress.png", xp)

# ------------------------------------------------------------------ duck totem
TOTEM = [
    "................",
    ".....kkkk.......",
    "....kyyyyk......",
    "...kyyyyyykkk...",
    "...kyekyyyoook..",
    "...kyyyyyyoook..",
    "...kyyyyyykkk...",
    "....kyyyyk......",
    "..kkyyyyyykkk...",
    ".kyyyyyyyyyyyk..",
    ".kyyywwwyyyyyk..",
    ".kyywwwwwyyyyk..",
    ".kyyyyyyyyyyk...",
    "..kkyyyyyykk....",
    "...kook.kook....",
    "....kk...kk.....",
]
tot = Image.new("RGBA", (16, 16), T)
cols = {"k": hx("3a2406"), "y": YEL, "o": ORA, "e": hx("17c27a"), "w": hx("fff1a8")}
for y, row in enumerate(TOTEM):
    for x, chh in enumerate(row):
        if chh != ".":
            c = cols[chh]
            if chh == "y" and y >= 8 and x >= 9:
                c = hx("f2b51e")
            tot.putpixel((x, y), c)
png("textures/item/totem_of_undying.png", tot)

# ------------------------------------------------------------------ pack meta + icon
js("pack.mcmeta", {"pack": {"pack_format": 46, "supported_formats": [46, 46],
                            "description": "§6Duck §ePvP §8· §7duckvisual.ru"}})
icon = Image.new("RGBA", (16, 16), hx("15151c"))
ip = icon.load()
for y in range(16):
    for x in range(16):
        if x in (0, 15) or y in (0, 15):
            ip[x, y] = mix(YEL, ORA, (x + y) / 30)
sw = sword("diamond")
icon.alpha_composite(sw.crop((0, 0, 16, 16)))
tt = tot.resize((10, 10), Image.NEAREST)
icon.alpha_composite(tt, (1, 5))
b = io.BytesIO()
icon.resize((128, 128), Image.NEAREST).save(b, "PNG")
files["pack.png"] = b.getvalue()

with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as z:
    for p in sorted(files):
        z.writestr(p, files[p])
print(OUT, len(files), "files")
