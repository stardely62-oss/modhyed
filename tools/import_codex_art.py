"""Turns Codex images (about 1254x1254 "pixel art") into exact Minecraft textures.

Codex does not keep to a requested pixel grid, so each output pixel is the median colour of the middle half of its
cell in a GRID x GRID split, then the palette is reduced so in-between shades disappear. Writes
textures/block/<name>_codex.png for every <name>.png in the folder.
Needs Pillow:  python3 tools/import_codex_art.py <folder>
"""
import statistics
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/ender_obelisks/textures/block"
# The stone's carved sigils need Codex's own ~64 grid to stay readable; the iron (small parts) is fine at 32
GRIDS = {"obelisk_stone": 64, "obelisk_iron": 32}


def to_texture(img, grid, colors=24):
    img = img.convert("RGB")
    w, h = img.size
    px = img.load()
    out = Image.new("RGB", (grid, grid))
    for gy in range(grid):
        for gx in range(grid):
            x0, x1 = int((gx + 0.25) * w / grid), max(int((gx + 0.75) * w / grid), int((gx + 0.25) * w / grid) + 1)
            y0, y1 = int((gy + 0.25) * h / grid), max(int((gy + 0.75) * h / grid), int((gy + 0.25) * h / grid) + 1)
            samples = [px[x, y] for y in range(y0, y1, 2) for x in range(x0, x1, 2)]
            out.putpixel((gx, gy), tuple(int(statistics.median(c[i] for c in samples)) for i in range(3)))
    return out.quantize(colors, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert("RGBA")


if __name__ == "__main__":
    src = Path(sys.argv[1])
    for image in sorted(src.glob("*.png")):
        grid = GRIDS.get(image.stem, 32)
        to_texture(Image.open(image), grid).save(OUT / f"{image.stem}_codex.png")
        print(f"wrote textures/block/{image.stem}_codex.png ({grid}x{grid})")
