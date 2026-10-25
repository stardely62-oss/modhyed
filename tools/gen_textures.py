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
    # obelisk stone: neutral dark slate, like polished deepslate
    "a": (0x1E, 0x20, 0x24, 255),  # joint
    "b": (0x2C, 0x2F, 0x34, 255),
    "c": (0x34, 0x37, 0x3D, 255),
    "e": (0x44, 0x48, 0x4F, 255),  # edge highlight
    # dark trim (blued metal)
    "k": (0x2A, 0x2D, 0x33, 255),
    "K": (0x41, 0x46, 0x4E, 255),
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


# Burgundy seeping out of the joints, a few uneven drips (pixel by pixel; "r" is the thin end or a falling drop).
# Only columns 5-10 show on the narrow shaft faces.
DRIPS = {
    (6, 8): "R", (6, 9): "R", (7, 10): "R", (7, 11): "r", (7, 13): "r",
    (9, 0): "R", (9, 1): "R", (9, 2): "r", (10, 3): "r",
    (8, 8): "R", (8, 9): "r",
}


def obelisk_stone(top=False):
    """Two courses of dressed stone: dark joints, a lit top-left edge on each block, faint even texture."""
    rows = []
    for y in range(16):
        row = ""
        for x in range(16):
            ch = "b" if (x + y) % 3 else "c"  # a regular, low-contrast tooling pattern
            if top:
                if x in (0, 15) or y in (0, 15):
                    ch = "a"
                elif x in (1, 14) or y in (1, 14):
                    ch = "e"
            else:
                course = y // 8
                joint_x = 0 if course == 0 else 8  # staggered vertical joints
                if y % 8 == 7 or x == joint_x:
                    ch = "a"
                elif y % 8 == 0 or x == (joint_x + 1) % 16:
                    ch = "e"
            row += ch
        rows.append(list(row))
    if not top:
        for (x, y), ch in DRIPS.items():
            rows[y][x] = ch
    return ["".join(r) for r in rows]


# One ring = a glowing line with beads hanging under it, drawn at the exact width of each shaft face
ORNAMENT = [
    "oooooo",  # lower shaft (6 wide)
    ".o..o.",
    "ooooo",   # upper shaft (5 wide)
    ".o.o.",
]
# Very dim "breathing": brightness levels and an uneven rhythm (ticks per level), as if power seeps through
GLOW_LEVELS = [0.84, 0.9, 0.95, 1.0]
GLOW_RHYTHM = [(0, 40), (1, 12), (2, 10), (3, 18), (2, 14), (1, 22), (0, 30), (1, 8), (2, 26), (1, 16)]
GLOW = (0x8A, 0xF5, 0xDC)


def ornament_frames():
    out = []
    for level in GLOW_LEVELS:
        color = tuple(int(c * level) for c in GLOW) + (255,)
        for y in range(16):
            out.append([color if y < len(ORNAMENT) and x < len(ORNAMENT[y]) and ORNAMENT[y][x] == "o" else (0, 0, 0, 0)
                        for x in range(16)])
    return out


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


def crystal():
    """Time crystal facets: left 8x16 for the sides (diagonal facets), right 8x8 for the ends."""
    rows = []
    for y in range(16):
        row = ""
        for x in range(16):
            if x < 8:
                v = (x + y // 2) % 6
                ch = "W" if v == 0 else ("D" if v < 4 else "d")
                if (x, y) in ((1, 3), (5, 10), (2, 13)):
                    ch = "X"
            else:
                cx, cy = abs(x - 11.5), abs(y % 8 - 3.5)
                ch = "W" if cx + cy < 2 else ("D" if cx + cy < 4 else "d")
            row += ch
        rows.append(row)
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
    runes = block / "obelisk_runes.png"
    write_png(runes, ornament_frames())
    frames = ", ".join('{"index": %d, "time": %d}' % f for f in GLOW_RHYTHM)
    runes.with_suffix(".png.mcmeta").write_text('{"animation": {"interpolate": true, "frames": [%s]}}\n' % frames)
    write_png(block / "obelisk_trim.png", trim())
    write_png(block / "obelisk_crystal.png", crystal())
    write_png(ASSETS / "textures/item/time_shard.png", TIME_SHARD)
    # Mod icon: the eye front, scaled up
    write_png(ASSETS / "icon.png", EYE_FRONT, scale=16)
