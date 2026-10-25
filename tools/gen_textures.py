"""Draws the obelisk's pixel-art textures (standard library only).

Run: python3 tools/gen_textures.py
"""
import math
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/ender_obelisks"

PALETTE = {
    ".": (0, 0, 0, 0),
    # obelisk stone: purple-black, a little lighter than obsidian so the carving reads
    "a": (0x16, 0x10, 0x1E, 255),
    "b": (0x20, 0x17, 0x2C, 255),
    "c": (0x2B, 0x1F, 0x3B, 255),
    "e": (0x3A, 0x2A, 0x50, 255),
    "p": (0x6B, 0x3F, 0x9E, 255),  # violet glint
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
    rows = []
    for y in range(16):
        row = ""
        for x in range(16):
            ch = speckle(x, y, 2 if top else 1, "bbbcca")
            if not top and x in (5, 10):
                ch = "a"  # carved vertical grooves
            if not top and x in (6, 11) and speckle(x, y, 3, "ab") == "b":
                ch = "e"  # groove highlight
            if speckle(x, y, 4, "................p") == "p":
                ch = "p"
            row += ch
        rows.append(row)
    if top:
        rows = ["e" + r[1:15] + "a" for r in rows]
        rows[0] = "e" * 16
        rows[15] = "a" * 16
    return rows


GLYPHS = [
    ["###", "#.#", "#.."],
    ["#.#", ".#.", "#.#"],
    [".#.", "###", ".#."],
    ["##.", ".#.", ".##"],
    ["#..", "###", "..#"],
    ["###", "..#", "###"],
]


def rune_mask():
    """16x16 of glyph pixels: a 3x3 glyph in each 4x4 cell, chosen by position."""
    mask = [[False] * 16 for _ in range(16)]
    for cy in range(4):
        for cx in range(4):
            glyph = GLYPHS[(cx * 7 + cy * 3 + cx * cy) % len(GLYPHS)]
            for gy in range(3):
                for gx in range(3):
                    if glyph[gy][gx] == "#":
                        mask[cy * 4 + gy][cx * 4 + gx] = True
    return mask


def rune_frames(frames=8):
    """An animated strip: the glyphs pulse from dim violet to bright magenta-white and back."""
    mask = rune_mask()
    lo, hi = (0x7A, 0x3C, 0xC8), (0xF2, 0xA8, 0xFF)
    out = []
    for f in range(frames):
        k = 0.5 - 0.5 * math.cos(f / frames * 2 * math.pi)  # 0 -> 1 -> 0
        color = tuple(int(lo[i] + (hi[i] - lo[i]) * k) for i in range(3)) + (255,)
        for y in range(16):
            out.append([color if mask[y][x] else (0, 0, 0, 0) for x in range(16)])
    return out


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
    runes = block / "obelisk_runes.png"
    write_png(runes, rune_frames())
    runes.with_suffix(".png.mcmeta").write_text('{"animation": {"frametime": 3, "interpolate": true}}\n')
    # Mod icon: the eye front, scaled up
    write_png(ASSETS / "icon.png", EYE_FRONT, scale=16)
