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
