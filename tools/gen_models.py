"""Writes the obelisk's JSON models and blockstate.

The obelisk is described once in "overall" coordinates (two blocks: y 0..32+), then split into the lower and upper
block models; the item model shows the whole thing. The eye and the rune rings are separate models drawn (and
animated) by ObeliskRenderer.  Run: python3 tools/gen_models.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/ender_obelisks"

TEXTURES = {
    "obsidian": "minecraft:block/obsidian",
    "deepslate": "minecraft:block/polished_deepslate",
    "diamond": "minecraft:block/diamond_block",
    "stone": "ender_obelisks:block/obelisk_stone",
    "stone_top": "ender_obelisks:block/obelisk_stone_top",
}

# (name, from, to, side texture, top/bottom texture). Every crafting material shows: obsidian plinth,
# polished deepslate corners/cage/cap, diamond bands and tip, and the ender eye (from the pearl) in the cage.
PARTS = [
    ("plinth", [0.5, 0, 0.5], [15.5, 2, 15.5], "obsidian", "obsidian"),
    ("step", [1.5, 2, 1.5], [14.5, 3.5, 14.5], "obsidian", "obsidian"),
    ("corner nw", [1, 2, 1], [3, 4.5, 3], "deepslate", "deepslate"),
    ("corner ne", [13, 2, 1], [15, 4.5, 3], "deepslate", "deepslate"),
    ("corner sw", [1, 2, 13], [3, 4.5, 15], "deepslate", "deepslate"),
    ("corner se", [13, 2, 13], [15, 4.5, 15], "deepslate", "deepslate"),
    ("shaft lower", [5, 3.5, 5], [11, 16, 11], "stone", "stone_top"),
    ("diamond band", [4.5, 8.5, 4.5], [11.5, 9.5, 11.5], "diamond", "diamond"),
    ("shaft upper", [5.5, 16, 5.5], [10.5, 22, 10.5], "stone", "stone_top"),
    ("diamond collar", [3.5, 22, 3.5], [12.5, 23, 12.5], "diamond", "diamond"),
    ("cage bar nw", [4, 23, 4], [5, 29, 5], "deepslate", "deepslate"),
    ("cage bar ne", [11, 23, 4], [12, 29, 5], "deepslate", "deepslate"),
    ("cage bar sw", [4, 23, 11], [5, 29, 12], "deepslate", "deepslate"),
    ("cage bar se", [11, 23, 11], [12, 29, 12], "deepslate", "deepslate"),
    ("diamond ring", [3.5, 29, 3.5], [12.5, 30, 12.5], "diamond", "diamond"),
    ("cap", [5, 30, 5], [11, 31, 11], "deepslate", "deepslate"),
    ("cap top", [6.5, 31, 6.5], [9.5, 32, 9.5], "deepslate", "deepslate"),
    ("tip", [7.25, 32, 7.25], [8.75, 34, 8.75], "diamond", "diamond"),
]

# Ornament rings, one more per tier, all 3 units tall so the 3-pixel ornament maps 1:1. They stand 0.06 off
# the shaft: closer than that the two surfaces fight and shimmer at a distance.
RUNE_BANDS = [
    ([4.94, 4.5, 4.94], [11.06, 7.5, 11.06]),
    ([4.94, 10, 4.94], [11.06, 13, 11.06]),
    ([4.94, 13, 4.94], [11.06, 16, 11.06]),
    ([5.44, 16, 5.44], [10.56, 19, 10.56]),
    ([5.44, 19, 5.44], [10.56, 22, 10.56]),
]


def uv(face, frm, to):
    """Blockbench-style automatic UVs from the element's own extents (local block coordinates)."""
    x1, y1, z1 = frm
    x2, y2, z2 = to

    def v(y):
        return max(0.0, min(16.0, 16 - (y % 16 if y != 16 else 16)))

    if face in ("north", "south"):
        return [x1, v(y2), x2, v(y1)]
    if face in ("east", "west"):
        return [z1, v(y2), z2, v(y1)]
    return [x1, z1, x2, z2]


def element(name, frm, to, side, end, shift=0.0, cull_down=False):
    frm = [frm[0], frm[1] + shift, frm[2]]
    to = [to[0], to[1] + shift, to[2]]
    faces = {}
    for face in ("north", "south", "east", "west", "up", "down"):
        f = {"uv": uv(face, frm, to), "texture": "#" + (end if face in ("up", "down") else side)}
        if face == "down" and cull_down:
            f["cullface"] = "down"
        faces[face] = f
    return {"name": name, "from": frm, "to": to, "faces": faces}


def split(lower):
    out = []
    for name, frm, to, side, end in PARTS:
        if lower and frm[1] < 16:
            out.append(element(name, frm, to, side, end, cull_down=frm[1] == 0))
        elif not lower and frm[1] >= 16:
            out.append(element(name, frm, to, side, end, shift=-16))
    return out


def textures(used):
    t = {k: v for k, v in TEXTURES.items() if k in used}
    t["particle"] = TEXTURES["deepslate"]
    return t


def used_textures(elements):
    return {f["texture"][1:] for e in elements for f in e["faces"].values()}


def eye_elements():
    # A rounded 5.5-unit eye: a core cube plus three crossing slabs. The pupil looks north (-Z).
    front, side = [0, 0, 8, 8], [8, 0, 16, 8]
    shapes = [
        ("core", [6, 6, 6], [10, 10, 10]),
        ("x slab", [5.25, 6.5, 6.5], [10.75, 9.5, 9.5]),
        ("y slab", [6.5, 5.25, 6.5], [9.5, 10.75, 9.5]),
        ("z slab", [6.5, 6.5, 5.25], [9.5, 9.5, 10.75]),
    ]
    out = []
    for name, frm, to in shapes:
        faces = {face: {"uv": front if face == "north" else side, "texture": "#eye"}
                 for face in ("north", "south", "east", "west", "up", "down")}
        out.append({"name": name, "from": frm, "to": to, "faces": faces})
    return out


def rune_element(i, frm, to):
    faces = {face: {"uv": [0, 0, 16, 3], "texture": "#runes"} for face in ("north", "south", "east", "west")}
    return {"name": f"ornament ring {i}", "from": frm, "to": to, "shade": False, "faces": faces}


def dump(rel, data):
    path = ASSETS / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent="\t") + "\n")
    print("wrote", rel)


if __name__ == "__main__":
    for half in ("lower", "upper"):
        els = split(half == "lower")
        dump(f"models/block/obelisk_{half}.json",
             {"ambientocclusion": False, "textures": textures(used_textures(els)), "elements": els})

    dump("models/block/obelisk_eye.json", {"ambientocclusion": False,
         "textures": {"eye": "ender_obelisks:block/obelisk_eye", "particle": "ender_obelisks:block/obelisk_eye"},
         "elements": eye_elements()})
    for i, (frm, to) in enumerate(RUNE_BANDS):
        dump(f"models/block/obelisk_runes_{i}.json", {"ambientocclusion": False,
             "textures": {"runes": "ender_obelisks:block/obelisk_runes", "particle": "ender_obelisks:block/obelisk_runes"},
             "elements": [rune_element(i, frm, to)]})

    # Item: the whole obelisk with a still eye in its cage and the first rune ring, shifted down to fit -16..32
    item_els = [element(n, f, t, s, e, shift=-6) for n, f, t, s, e in PARTS]
    for el in eye_elements():
        el = json.loads(json.dumps(el))
        el["from"] = [el["from"][0], el["from"][1] + 26 - 8 - 6, el["from"][2]]
        el["to"] = [el["to"][0], el["to"][1] + 26 - 8 - 6, el["to"][2]]
        item_els.append(el)
    ring = rune_element(0, *RUNE_BANDS[0])
    ring["from"][1] -= 6
    ring["to"][1] -= 6
    item_els.append(ring)
    item_tex = textures(used_textures(item_els))
    item_tex.update({"eye": "ender_obelisks:block/obelisk_eye", "runes": "ender_obelisks:block/obelisk_runes"})
    dump("models/item/obelisk.json", {
        "parent": "minecraft:block/block",
        "textures": item_tex,
        "elements": item_els,
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, -1.5, 0], "scale": [0.42, 0.42, 0.42]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 1, 0], "scale": [0.3, 0.3, 0.3]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, -1, 0], "scale": [0.45, 0.45, 0.45]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3]},
            "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.32, 0.32, 0.32]},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.32, 0.32, 0.32]},
        },
    })

    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    dump("blockstates/obelisk.json", {"variants": {
        f"facing={f},half={h}": {"model": f"ender_obelisks:block/obelisk_{h}", **({"y": r} if r else {})}
        for f, r in rot.items() for h in ("lower", "upper")}})
