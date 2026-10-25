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
