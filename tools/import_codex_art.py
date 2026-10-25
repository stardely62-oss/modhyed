"""Turns Codex-generated images (about 1254x1254 "pixel art") into exact 32x32 Minecraft textures.

Codex does not keep to a requested 16x16 grid: its images come with 18-36 "pixels" per side, so a 16x16
result loses the eyes and crystals. 32x32 keeps them (Minecraft takes it; model UVs stay in 0-16 units).
The image is split into a 32x32 grid; each texture pixel is the median colour of the middle half of its cell
(the edges of a cell blur into the neighbours), then the palette is reduced so stray in-between shades disappear.

Needs Pillow:  python3 tools/import_codex_art.py <folder with head_front.png ...>
Writes src/main/resources/assets/modhyed/textures/block/statue/codex_<name>.png and a preview sheet
art/codex_preview.png (originals beside their 32x32 results).
"""
import statistics
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/modhyed/textures/block/statue"
NAMES = ["head_front", "head_side", "head_top", "pedestal_side", "pedestal_top"]
GRID = 32
COLORS = 32


def to_texture(img):
    img = img.convert("RGB")
    w, h = img.size
    px = img.load()
    out = Image.new("RGB", (GRID, GRID))
    for gy in range(GRID):
        for gx in range(GRID):
            x0, x1 = int((gx + 0.25) * w / GRID), int((gx + 0.75) * w / GRID)
            y0, y1 = int((gy + 0.25) * h / GRID), int((gy + 0.75) * h / GRID)
            samples = [px[x, y] for y in range(y0, y1, 3) for x in range(x0, x1, 3)]
            out.putpixel((gx, gy), tuple(int(statistics.median(c[i] for c in samples)) for i in range(3)))
    return out.quantize(COLORS, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert("RGBA")


def main(src):
    src = Path(src)
    OUT.mkdir(parents=True, exist_ok=True)
    tile = 256
    sheet = Image.new("RGB", (tile * 2, tile * len(NAMES)), (30, 30, 30))
    for row, name in enumerate(NAMES):
        original = Image.open(src / f"{name}.png")
        texture = to_texture(original)
        texture.save(OUT / f"codex_{name}.png")
        print(f"wrote textures/block/statue/codex_{name}.png from {original.size[0]}x{original.size[1]}")
        sheet.paste(original.convert("RGB").resize((tile, tile), Image.Resampling.LANCZOS), (0, row * tile))
        sheet.paste(texture.convert("RGB").resize((tile, tile), Image.Resampling.NEAREST), (tile, row * tile))
    (ROOT / "art").mkdir(exist_ok=True)
    sheet.save(ROOT / "art/codex_preview.png")
    print("wrote art/codex_preview.png")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else ".")
