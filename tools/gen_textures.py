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
    # ornament glow: ender teal, in tone with the eye and the diamond
    "o": (0x8A, 0xF5, 0xDC, 255),
    # ender eye
    "k": (0x05, 0x0A, 0x08, 255),  # pupil
    "g": (0x0F, 0x3A, 0x2C, 255),  # rim
    "G": (0x1F, 0x7A, 0x5A, 255),  # iris
    "L": (0x4F, 0xD6, 0x9C, 255),  # iris light
    "W": (0xE8, 0xFF, 0xF4, 255),  # glint
    "t": (0x12, 0x4A, 0x3A, 255),  # side swirl dark
    "T": (0x2C, 0x8C, 0x6C, 255),  # side swirl light
}


def speckle(x, y, seed, chars):
    """Deterministic noise: picks one of chars per pixel."""
    n = (x * 73856093) ^ (y * 19349663) ^ (seed * 83492791)
    n = (n ^ (n >> 13)) * 1274126177
    return chars[(n >> 8) % len(chars)]


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
        rows.append(row)
    return rows


# A running wave, 2 pixels tall, period 4: the ornament on every ring
ORNAMENT = [
    ".oo.",
    "o..o",
]


def ornament():
    rows = []
    for y in range(16):
        rows.append("".join(ORNAMENT[y][x % 4] if y < len(ORNAMENT) else "." for x in range(16)))
    return rows


EYE_FRONT = [
    "ggGGGGgg",
    "gGLLLLGg",
    "GLLkkLLG",
    "GLLkkWLG",
    "GLLkkLLG",
    "GLGkkGLG",
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
    # Steady glow, no animation: the pulsing read as flicker
    write_png(block / "obelisk_runes.png", ornament())
    # Mod icon: the eye front, scaled up
    write_png(ASSETS / "icon.png", EYE_FRONT, scale=16)
