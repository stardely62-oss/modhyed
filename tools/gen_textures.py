"""Draws the mod's 16x16 pixel-art textures and the 128x128 icon.

Standard library only: python3 tools/gen_textures.py
"""
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/modhyed"

PALETTE = {
    ".": (0, 0, 0, 0),
    "D": (0x3B, 0x12, 0x50, 255),  # outline
    "S": (0x7A, 0x22, 0x96, 255),  # shadow
    "M": (0xB8, 0x3C, 0xDC, 255),  # body
    "L": (0xE2, 0x9A, 0xF5, 255),  # light
    "W": (0xFF, 0xF4, 0xFF, 255),  # sparkle
    # statue stone
    "k": (0x2B, 0x27, 0x33, 255),  # carved line
    "s": (0x4D, 0x47, 0x5B, 255),  # shadow
    "g": (0x6B, 0x65, 0x80, 255),  # body
    "h": (0x8D, 0x86, 0xA2, 255),  # light
    "H": (0xAA, 0xA3, 0xBD, 255),  # top edge
    # staff wood
    "w": (0x2E, 0x20, 0x26, 255),
    "x": (0x46, 0x30, 0x36, 255),
    "y": (0x5E, 0x42, 0x46, 255),
    # dim crystal (staff recharging)
    "d": (0x3E, 0x24, 0x4C, 255),
    "e": (0x56, 0x34, 0x66, 255),
    "f": (0x74, 0x4E, 0x86, 255),
}

GEM = [
    "................",
    "................",
    "................",
    ".....DDDDDD.....",
    "....DWLLMMSD....",
    "...DWLLMMMMSD...",
    "..DLLMMMMMMMSD..",
    "..DDDDDDDDDDDD..",
    "...DLMMMMMMSD...",
    "....DLMMMMSD....",
    ".....DLMMSD.....",
    "......DMSD......",
    ".......DD.......",
    "................",
    "................",
    "................",
]

BLOCK = [
    "DDDDDDDDDDDDDDDD",
    "DLLLLLLLLLLLLLLD",
    "DLMMMMMMMMMMMMSD",
    "DLMWMMMMMMMMMMSD",
    "DLMMMMMLLMMMMMSD",
    "DLMMMMLWMSMMMMSD",
    "DLMMMLWMMMSMMMSD",
    "DLMMLLMMMMSSMMSD",
    "DLMMSSMMMMLLMMSD",
    "DLMMMSMMMLLMMMSD",
    "DLMMMMSMMLMMMMSD",
    "DLMMMMMSSMMMMMSD",
    "DLMMMMMMMMMMWMSD",
    "DLMMMMMMMMMMMMSD",
    "DSSSSSSSSSSSSSSD",
    "DDDDDDDDDDDDDDDD",
]


# Guardian statue: the head is 8x8 units, so these 16x16 faces show at twice the block pixel density
HEAD_FRONT = [
    "HHHHHHHHHHHHHHHh",
    "Hhhhhhhhhhhhhhgs",
    "Hhgggggggggggggs",
    "Hhgkkkkkgkkkkkgs",
    "Hhgsssssgsssssgs",
    "HhgsWLMsgsWLMsgs",
    "HhgsLMMsgsLMMsgs",
    "Hhgsssssgsssssgs",
    "Hhgggggghhgggggs",
    "Hhggggggssgggggs",
    "Hhgggggggggggggs",
    "Hhgkkkkkkkkkkkgs",
    "Hhggsgggggggsggs",
    "Hhgggggsgggggggs",
    "hgggggssgggggsss",
    "ssssssssssssssss",
]

HEAD_SIDE = [
    "HHHHHHHHHHHHHHHh",
    "Hhhhhhhhhhhhhhgs",
    "Hhgggggggggsgggs",
    "Hhggggggggggsggs",
    "Hhgggggggggggggs",
    "Hhggggkkkkgggggs",
    "Hhggggkhhkgggggs",
    "Hhggggkhgkgggggs",
    "Hhggggkhgkgggggs",
    "Hhggggkggkgggggs",
    "Hhggggkkkkgggggs",
    "Hhgggggggggggggs",
    "Hhgsgggggggggggs",
    "Hhggsggggggggggs",
    "hgggggggggggggss",
    "ssssssssssssssss",
]


def speckle(x, y, seed, chars):
    """Deterministic noise: picks one of chars per pixel."""
    n = (x * 73856093) ^ (y * 19349663) ^ (seed * 83492791)
    n = (n ^ (n >> 13)) * 1274126177
    return chars[(n >> 8) % len(chars)]


def head_top():
    rows = [[speckle(x, y, 1, "hhhhHg") for x in range(16)] for y in range(16)]
    for i in range(16):
        rows[0][i] = rows[15][i] = rows[i][0] = rows[i][15] = "g"
    for cx, cy in ((4, 4), (11, 5), (7, 11)):
        for dx, dy, ch in ((0, -1, "L"), (-1, 0, "L"), (0, 0, "W"), (1, 0, "M"), (0, 1, "M"), (0, 2, "S")):
            rows[cy + dy][cx + dx] = ch
    return ["".join(r) for r in rows]


def pedestal_side():
    # the model shows rows 4-11: top edge, carved line, inlay band, carved line, shadow
    band = {4: "H", 5: "h", 6: "k", 9: "k", 11: "s"}
    rows = []
    for y in range(16):
        if y in band:
            rows.append(band[y] * 16)
        elif y in (7, 8):
            rows.append("".join("L" if (x + y) % 4 == 0 else "M" for x in range(16)))
        elif y >= 12:
            rows.append("k" * 16)
        else:
            rows.append("".join(speckle(x, y, 2, "ggghs") for x in range(16)))
    return rows


def pedestal_top():
    rows = [[speckle(x, y, 3, "hhhHg") for x in range(16)] for y in range(16)]
    for i in range(1, 15):  # the model crops to 1..15, so the carved border sits one pixel in
        rows[1][i] = rows[14][i] = rows[i][1] = rows[i][14] = "s"
    return ["".join(r) for r in rows]


def crystal_frames(frames=8):
    """Animated crystal: four 4-px columns (one per crystal face) with a highlight band sliding up."""
    shades = "LMMS"  # lit edge -> body -> shadowed edge, per column
    rows = []
    for frame in range(frames):
        band = 15 - frame * 2
        for y in range(16):
            row = ""
            for x in range(16):
                ch = shades[x % 4]
                if abs(y - band) <= 1 or abs(y - band - 16) <= 1:
                    ch = "W" if abs(y - band) == 0 or abs(y - band - 16) == 0 else "L"
                row += ch
            rows.append(row)
    return rows


def crystal_dim():
    return ["".join("fedd"[x % 4] for x in range(16)) for _ in range(16)]


def staff_shaft():
    # vertical grain; the model shows columns 7-8
    return ["".join(speckle(x // 2, y, 4, "wxxy") if x % 2 == 0 else speckle(x // 2, y + 1, 4, "wxy")
                    for x in range(16)) for y in range(16)]


def golem_textures():
    """64x64 skin for EufGolemModel plus its emissive overlay (same UV layout)."""
    skin = [["." for _ in range(64)] for _ in range(64)]
    glow = [["." for _ in range(64)] for _ in range(64)]

    def box(u, v, w, h, d, chars, seed):
        rects = [(u + d, v, w, d), (u + d + w, v, w, d),            # top, bottom
                 (u, v + d, d, h), (u + d, v + d, w, h),            # right, front
                 (u + d + w, v + d, d, h), (u + 2 * d + w, v + d, w, h)]  # left, back
        for rx, ry, rw, rh in rects:
            for y in range(ry, ry + rh):
                for x in range(rx, rx + rw):
                    skin[y][x] = speckle(x, y, seed, chars)
            for x in range(rx, rx + rw):  # darker bottom edge on the side faces
                if rh > 2 and ry != v:
                    skin[ry + rh - 1][x] = "s"

    def paint(target, x0, y0, rows):
        for dy, row in enumerate(rows):
            for dx, ch in enumerate(row):
                if ch != " ":
                    target[y0 + dy][x0 + dx] = ch

    box(0, 0, 8, 8, 8, "ghhhg", 5)       # head
    box(0, 16, 8, 8, 5, "gghg", 6)       # body
    box(32, 0, 3, 9, 3, "gghs", 7)       # right arm
    box(44, 0, 3, 9, 3, "gghs", 8)       # left arm
    box(32, 16, 3, 6, 3, "gsgs", 9)      # right leg
    box(44, 16, 3, 6, 3, "gsgs", 10)     # left leg
    for y in range(0, 6):                # crystal box (56,0) 2x4x2
        for x in range(56, 64):
            skin[y][x] = glow[y][x] = "LMMS"[(x - 56) % 4] if y >= 2 else "L"

    face = ["HHHHHHHH",
            "hggggggs",
            "hkkggkks",
            "h  gg  s",
            "hggssggs",
            "hgkkkkgs",
            "hggggggs",
            "ssssssss"]
    paint(skin, 8, 8, face)
    for target in (skin, glow):
        paint(target, 9, 11, ["LW"])
        paint(target, 13, 11, ["WL"])
    # chest core on the body front (5,21)
    core = ["   DD   ",
            "  DLMD  ",
            " DLWMSD ",
            "  DMSD  ",
            "   DD   "]
    paint(skin, 5, 22, core)
    paint(glow, 5, 22, [row.replace("D", " ") for row in core])
    return ["".join(r) for r in skin], ["".join(r) for r in glow]


def write_png(path, rows, scale=1):
    """rows: list of strings over PALETTE; each pixel is repeated scale x scale."""
    height = len(rows) * scale
    width = len(rows[0]) * scale
    raw = bytearray()
    for row in rows:
        line = bytearray()
        for ch in row:
            line += bytes(PALETTE[ch]) * scale
        for _ in range(scale):
            raw += b"\x00" + line

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)
    print(f"wrote {path.relative_to(ROOT)} ({width}x{height})")


if __name__ == "__main__":
    assert all(len(r) == 16 for r in GEM + BLOCK) and len(GEM) == len(BLOCK) == 16
    write_png(ASSETS / "textures/item/euf_gem.png", GEM)
    write_png(ASSETS / "textures/block/euf_block.png", BLOCK)
    write_png(ASSETS / "icon.png", GEM, scale=8)
    statue = ASSETS / "textures/block/statue"
    for name, rows in (("head_front", HEAD_FRONT), ("head_side", HEAD_SIDE), ("head_top", head_top()),
                       ("pedestal_side", pedestal_side()), ("pedestal_top", pedestal_top())):
        assert len(rows) == 16 and all(len(r) == 16 for r in rows), name
        write_png(statue / f"hand_{name}.png", rows)

    crystal = ASSETS / "textures/block/euf_crystal.png"
    write_png(crystal, crystal_frames())
    crystal.with_suffix(".png.mcmeta").write_text('{"animation": {"frametime": 2, "interpolate": true}}\n')
    write_png(ASSETS / "textures/block/euf_crystal_dim.png", crystal_dim())
    write_png(ASSETS / "textures/item/euf_staff_shaft.png", staff_shaft())
    skin, glow = golem_textures()
    write_png(ASSETS / "textures/entity/euf_golem.png", skin)
    write_png(ASSETS / "textures/entity/euf_golem_glow.png", glow)
