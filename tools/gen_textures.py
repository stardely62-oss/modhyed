"""Draws the obelisk's pixel-art textures (standard library only).

Run: python3 tools/gen_textures.py
"""
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/ender_obelisks"

PALETTE = {
    ".": (0, 0, 0, 0),
    # obelisk stone: black basalt with a cold tint
    "a": (0x10, 0x11, 0x14, 255),  # carved groove / crack
    "b": (0x1F, 0x21, 0x26, 255),
    "c": (0x27, 0x2A, 0x30, 255),
    "e": (0x36, 0x3A, 0x42, 255),  # lit edge of a carving
    # blood: wet at the source, dark and dried at the tips (letters of their own: T and others belong to the eye)
    "V": (0x9C, 0x1C, 0x24, 255),
    "Y": (0x6A, 0x0C, 0x14, 255),
    "Z": (0x3E, 0x06, 0x0C, 255),
    # dark trim (blued metal)
    "k": (0x2A, 0x2D, 0x33, 255),
    "K": (0x41, 0x46, 0x4E, 255),
    "u": (0x4A, 0x2E, 0x22, 255),  # dull rust
    "j": (0x17, 0x19, 0x1C, 255),
    # dark burgundy: the drips on the stone and the time crystal
    "R": (0x62, 0x16, 0x26, 255),
    "r": (0x45, 0x10, 0x1C, 255),
    "W": (0xA8, 0x3A, 0x52, 255),  # crystal light
    "D": (0x6E, 0x1A, 0x2E, 255),  # crystal
    "d": (0x40, 0x0C, 0x1A, 255),  # crystal dark
    "X": (0xD8, 0x7A, 0x8E, 255),  # crystal glint
    # ender eye
    "q": (0x05, 0x0A, 0x08, 255),  # pupil
    "g": (0x0F, 0x3A, 0x2C, 255),  # rim
    "G": (0x1F, 0x7A, 0x5A, 255),  # iris
    "L": (0x4F, 0xD6, 0x9C, 255),  # iris light
    "w": (0xE8, 0xFF, 0xF4, 255),  # glint
    "t": (0x12, 0x4A, 0x3A, 255),  # side swirl dark
    "T": (0x2C, 0x8C, 0x6C, 255),  # side swirl light
}


def speckle(x, y, seed, chars):
    """Deterministic noise: picks one of chars per pixel."""
    n = (x * 73856093) ^ (y * 19349663) ^ (seed * 83492791)
    n = (n ^ (n >> 13)) * 1274126177
    return chars[(n >> 8) % len(chars)]


# Sigils carved down the middle of the shaft (columns 6-9 show on both shaft widths), one every 5 rows
SIGILS = [
    ["aaaa", "a..a", "a.a."],
    [".aa.", "aaaa", ".aa."],
    ["a..a", ".aa.", "a..a"],
]
# Hairline cracks and two thin burgundy drips, placed by hand (x, y): grim, but not noisy
CRACKS = [(5, 2), (5, 3), (6, 4), (10, 9), (10, 10), (11, 11), (5, 13), (6, 14)]
DRIPS = {(7, 13): "R", (7, 14): "R", (8, 15): "r", (9, 5): "R", (9, 6): "r"}


def obelisk_stone(top=False):
    """Black basalt: a quiet two-tone surface; the shaft faces carry a column of carved sigils."""
    rows = [["b" if (x * 3 + y * 5) % 7 else "c" for x in range(16)] for y in range(16)]
    if top:
        for i in range(16):
            for x, y in ((i, 0), (i, 15), (0, i), (15, i)):
                rows[y][x] = "a"
            for x, y in ((i, 1), (1, i)):
                if 0 < i < 15:
                    rows[y][x] = "e"
    else:
        for n, top_row in enumerate(range(1, 16, 5)):
            glyph = SIGILS[n % len(SIGILS)]
            for gy, line in enumerate(glyph):
                for gx, ch in enumerate(line):
                    if ch == "a" and top_row + gy < 16:
                        rows[top_row + gy][6 + gx] = "a"
                        if top_row + gy + 1 < 16 and glyph[gy + 1][gx] != "a" if gy + 1 < len(glyph) else top_row + gy + 1 < 16:
                            rows[top_row + gy + 1][6 + gx] = "e"  # light catches the lower lip of the groove
        for x, y in CRACKS:
            rows[y][x] = "a"
        for (x, y), ch in DRIPS.items():
            rows[y][x] = ch
    return ["".join(r) for r in rows]


def iron():
    """Blackened wrought iron: hammered plate, rivets along the top and bottom, dull rust at the edges."""
    rows = []
    for y in range(16):
        row = ""
        for x in range(16):
            ch = "k" if (x // 2 + y // 3) % 3 else "j"
            if y in (1, 14) and x % 5 == 2:
                ch = "K"  # rivet
            if (x in (0, 15) or y in (0, 15)) and (x + y) % 3 == 0:
                ch = "u"  # rust
            row += ch
        rows.append(row)
    return rows


# Blood running down the shaft, one overlay per tier; the overlays stack, so tier 4 shows all of them.
# Columns 0-5 / rows 0-8 cover a lower shaft face, columns 6-10 / rows 0-4 an upper one (half the faces mirror it).
# Thin streaks only: (column, first row, length) - "V" wet where it starts, "Y" blood, "Z" the dried end.
BLOOD = {
    1: [(2, 0, 3)],
    2: [(8, 0, 3), (4, 0, 2)],
    3: [(0, 0, 4), (4, 4, 1)],
    4: [(2, 3, 6), (6, 0, 4)],
}


def blood(level):
    rows = [["." for _ in range(16)] for _ in range(16)]
    for col, top, length in BLOOD[level]:
        for i in range(length):
            starts_here = top == 0 and i == 0
            rows[top + i][col] = "V" if starts_here else ("Z" if i == length - 1 else "Y")
    return ["".join(r) for r in rows]


def trim():
    """Plain blued metal: rows 0-1 the 2-high band, row 2 the 1-high ring, the rest the top faces."""
    rows = []
    for y in range(16):
        if y == 0:
            rows.append("K" * 16)
        elif y in (1, 2):
            rows.append("".join("k" if x % 4 else "j" for x in range(16)))
        else:
            rows.append("".join("K" if x in (0, 15) or y in (3, 15) else "k" for x in range(16)))
    return rows


# Item sprite: a small cluster of three time crystals
TIME_SHARD = [
    "................",
    ".......d........",
    "......dXd.......",
    "......dWDd......",
    ".....dWDDd......",
    ".....dWDDd..d...",
    ".....dWDDd.dXd..",
    ".....dWXDd.dWDd.",
    ".....dWDDd.dWDd.",
    "..d..dWDDd.dWDd.",
    ".dXd.dWDDddWDd..",
    ".dWDddWDDdWDDd..",
    ".dWDDdWDDDDDd...",
    "..dDDDDDDDDd....",
    "...ddddddddd....",
    "................",
]
assert all(len(row) == 16 for row in TIME_SHARD)


EYE_FRONT = [
    "ggGGGGgg",
    "gGLLLLGg",
    "GLLqqLLG",
    "GLLqqwLG",
    "GLLqqLLG",
    "GLGqqGLG",
    "gGLLLLGg",
    "ggGGGGgg",
]


def eye_texture():
    """Left 8x8: the front with a slit pupil and a glint. Right 8x8 (and the rest): the swirled sides."""
    rows = []
    for y in range(16):
        row = ""
        for x in range(16):
            if x < 8 and y < 8:
                row += EYE_FRONT[y][x]
            else:
                d = abs(x % 8 - 3.5) + abs(y % 8 - 3.5)
                row += "g" if d > 5.5 else ("T" if (int(d) + x // 8) % 2 == 0 else "t")
        rows.append(row)
    return rows


def write_png(path, rows, scale=1):
    """rows: strings over PALETTE, or lists of RGBA tuples."""
    height = len(rows) * scale
    width = len(rows[0]) * scale
    raw = bytearray()
    for row in rows:
        line = bytearray()
        for px in row:
            line += bytes(PALETTE[px] if isinstance(px, str) else px) * scale
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
    block = ASSETS / "textures/block"
    write_png(block / "obelisk_stone.png", obelisk_stone())
    write_png(block / "obelisk_stone_top.png", obelisk_stone(top=True))
    write_png(block / "obelisk_eye.png", eye_texture())
    for level in BLOOD:
        write_png(block / f"obelisk_blood_{level}.png", blood(level))
    write_png(block / "obelisk_trim.png", trim())
    write_png(block / "obelisk_iron.png", iron())
    write_png(ASSETS / "textures/item/time_shard.png", TIME_SHARD)
    # Mod icon: the eye front, scaled up
    write_png(ASSETS / "icon.png", EYE_FRONT, scale=16)
