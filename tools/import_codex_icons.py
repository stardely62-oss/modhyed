"""Turns Codex icon images (about 1254x1254 "pixel art" on magenta) into 32x32 GUI icons with transparency.

The menu draws them in a 16x16 GUI cell, so at GUI scale 2 and above every pixel still shows.

Each output pixel is the median colour of the middle half of its cell; cells close to the magenta background become
transparent, then the palette is reduced. Writes textures/gui/icon/<name>.png for every <name>.png in the folder.
Needs Pillow:  python3 tools/import_codex_icons.py <folder>
"""
import statistics
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/ender_obelisks/textures/gui/icon"
GRID = 32
# A faint violet rim around the symbol, so dark obsidian icons stay readable on the dark panels
RIM = (122, 92, 170, 255)


def is_background(c):
    r, g, b = c
    return r > 150 and b > 150 and g < 110 and abs(r - b) < 90


def bbox(img):
    """The symbol's bounds: Codex often leaves wide margins, so crop to the non-magenta area (square, centred)."""
    small = img.resize((128, 128), Image.NEAREST)
    px = small.load()
    xs = [x for y in range(128) for x in range(128) if not is_background(px[x, y])]
    ys = [y for y in range(128) for x in range(128) if not is_background(px[x, y])]
    if not xs:
        return img
    x0, x1, y0, y1 = min(xs), max(xs) + 1, min(ys), max(ys) + 1
    side = max(x1 - x0, y1 - y0) + 4
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    k = img.size[0] / 128
    return img.crop((int((cx - side / 2) * k), int((cy - side / 2) * k), int((cx + side / 2) * k), int((cy + side / 2) * k)))


def to_icon(img, colors=16):
    img = bbox(img.convert("RGB"))
    w, h = img.size
    px = img.load()
    out = Image.new("RGBA", (GRID, GRID), (0, 0, 0, 0))
    for gy in range(GRID):
        for gx in range(GRID):
            x0, x1 = int((gx + 0.25) * w / GRID), max(int((gx + 0.75) * w / GRID), int((gx + 0.25) * w / GRID) + 1)
            y0, y1 = int((gy + 0.25) * h / GRID), max(int((gy + 0.75) * h / GRID), int((gy + 0.25) * h / GRID) + 1)
            samples = [px[min(x, w - 1), min(y, h - 1)] for y in range(y0, y1, 2) for x in range(x0, x1, 2)]
            fg = [c for c in samples if not is_background(c)]
            if len(fg) * 2 < len(samples):
                continue
            out.putpixel((gx, gy), tuple(int(statistics.median(c[i] for c in fg)) for i in range(3)) + (255,))
    alpha = out.getchannel("A")
    rgb = out.convert("RGB").quantize(colors, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert("RGBA")
    rgb.putalpha(alpha)
    return add_rim(rgb)


def add_rim(img):
    px = img.load()
    out = img.copy()
    opx = out.load()
    for y in range(GRID):
        for x in range(GRID):
            if px[x, y][3]:
                continue
            near = ((x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if any(0 <= nx < GRID and 0 <= ny < GRID and px[nx, ny][3] for nx, ny in near):
                opx[x, y] = RIM
    return out


if __name__ == "__main__":
    src = Path(sys.argv[1])
    OUT.mkdir(parents=True, exist_ok=True)
    for image in sorted(src.glob("*.png")):
        to_icon(Image.open(image)).save(OUT / f"{image.stem}.png")
        print(f"wrote textures/gui/icon/{image.stem}.png")
