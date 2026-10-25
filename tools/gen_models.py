"""Writes the obelisk's JSON models and blockstate.

The obelisk is described once in "overall" coordinates (two blocks: y 0..32+), then split into the lower and upper
block models; the item model shows the whole thing. The eye and the blood overlays are separate models drawn (and
animated) by ObeliskRenderer.  Run: python3 tools/gen_models.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/ender_obelisks"

# Which texture set the shaft and the iron parts use: "hand" (tools/gen_textures.py) or "codex"
# (tools/gen_codex_art.sh + tools/import_codex_art.py)
VARIANT = "codex"
SUFFIX = "" if VARIANT == "hand" else "_codex"

TEXTURES = {
    "obsidian": "minecraft:block/obsidian",
    "deepslate": "minecraft:block/polished_deepslate",
    "trim": "ender_obelisks:block/obelisk_trim",
    "stone": "ender_obelisks:block/obelisk_stone" + SUFFIX,
    "stone_top": "ender_obelisks:block/obelisk_stone_top",
    "iron": "ender_obelisks:block/obelisk_iron" + SUFFIX,
}

# (name, from, to, side texture, top/bottom texture, optional y-rotation origin). Grim dark fantasy: an obsidian
# plinth gripped by four iron talons, a black runic basalt shaft, the caged eye, and an iron needle on top.
# An element with an origin point is turned 45 degrees about y (it shows an edge, not a face); a rotation dict
# turns it any other way.
PARTS = [
    # The plinth narrows in uneven steps: a broad slab, a short step, a tall deepslate tier, a thin lip
    ("plinth", [0.5, 0, 0.5], [15.5, 2.5, 15.5], "obsidian", "obsidian"),
    ("plinth step", [2.5, 2.5, 2.5], [13.5, 4, 13.5], "obsidian", "obsidian"),
    ("plinth tier", [3.5, 4, 3.5], [12.5, 6.5, 12.5], "deepslate", "deepslate"),
    ("plinth lip", [4.5, 6.5, 4.5], [11.5, 7.5, 11.5], "obsidian", "obsidian"),
    ("shaft lower", [5, 7.5, 5], [11, 16, 11], "stone", "stone_top"),
    ("shaft upper", [5.5, 16, 5.5], [10.5, 21, 10.5], "stone", "stone_top"),
    ("trim collar", [3.5, 21, 3.5], [12.5, 23, 12.5], "trim", "trim"),
    ("cage bar nw", [4, 23, 4], [5, 29, 5], "deepslate", "deepslate"),
    ("cage bar ne", [11, 23, 4], [12, 29, 5], "deepslate", "deepslate"),
    ("cage bar sw", [4, 23, 11], [5, 29, 12], "deepslate", "deepslate"),
    ("cage bar se", [11, 23, 11], [12, 29, 12], "deepslate", "deepslate"),
    ("trim ring", [3.5, 29, 3.5], [12.5, 30, 12.5], "trim", "trim"),
    ("cap", [5, 30, 5], [11, 31, 11], "deepslate", "deepslate"),
    ("cap top", [6.5, 31, 6.5], [9.5, 32, 9.5], "deepslate", "deepslate"),
    ("needle", [7.4, 32, 7.4], [8.6, 35, 8.6], "iron", "iron", [8, 32, 8]),
    ("needle tip", [7.75, 35, 7.75], [8.25, 37, 8.25], "iron", "iron", [8, 35, 8]),
]
# Talons grip the corners of the bottom slab: each one a curved claw of three segments bending further outward
# toward its point, plus a short spur. Size, lean axis and spur differ per corner, so they look grown, not placed.
# (axis, sign) picks the lean: rotating about z by +angle tips the top toward -x, about x by +angle toward +z.
TALONS = {
    "nw": ((1.5, 1.5), 1.0, ("z", 1), ("x", -1)),
    "ne": ((14.5, 1.5), 0.85, ("x", -1), ("z", -1)),
    "sw": ((1.5, 14.5), 1.15, ("x", 1), ("z", 1)),
    "se": ((14.5, 14.5), 0.95, ("z", -1), ("x", 1)),
}


def lean_offset(axis, sign, dist):
    """Where the top of a segment of height dist ends up after leaning 22.5 degrees."""
    d = dist * 0.38
    return (-d * sign, 0.0) if axis == "z" else (0.0, d * sign)


for corner, ((cx, cz), k, (axis, sign), (spur_axis, spur_sign)) in TALONS.items():
    base_h, mid_h, tip_h = 1.6 * k, 2.6 * k, 2.0 * k
    y1 = 2.5 + base_h
    PARTS.append((f"talon base {corner}", [cx - 0.9, 2.5, cz - 0.9], [cx + 0.9, y1, cz + 0.9], "iron", "iron", [cx, 2.5, cz]))
    PARTS.append((f"talon {corner}", [cx - 0.6, y1, cz - 0.6], [cx + 0.6, y1 + mid_h, cz + 0.6], "iron", "iron",
                  {"origin": [cx, y1, cz], "axis": axis, "angle": 22.5 * sign}))
    ox, oz = lean_offset(axis, sign, mid_h)
    tx, tz, y2 = cx + ox, cz + oz, y1 + mid_h * 0.92
    PARTS.append((f"talon point {corner}", [tx - 0.3, y2, tz - 0.3], [tx + 0.3, y2 + tip_h, tz + 0.3], "iron", "iron",
                  {"origin": [tx, y2, tz], "axis": axis, "angle": 45 * sign}))
    PARTS.append((f"talon spur {corner}", [cx - 0.3, y1 - 0.4, cz - 0.3], [cx + 0.3, y1 + 1.2 * k, cz + 0.3], "iron", "iron",
                  {"origin": [cx, y1 - 0.4, cz], "axis": spur_axis, "angle": 45 * spur_sign}))

# Blood that runs down the shaft as the obelisk is upgraded: one overlay per tier (1-4), drawn on top of each
# other. Each wraps the lower and upper shaft a little further out than the last, so they never fight for depth.
BLOOD_LEVELS = 4


def blood_elements(level):
    e = 0.04 + 0.02 * level
    out = []
    for name, frm, to, uv_box in (
            ("lower", [5 - e, 7.5, 5 - e], [11 + e, 16, 11 + e], [0, 0, 6, 8.5]),
            ("upper", [5.5 - e, 16, 5.5 - e], [10.5 + e, 21, 10.5 + e], [6, 0, 11, 5])):
        mirrored = [uv_box[2], uv_box[1], uv_box[0], uv_box[3]]
        faces = {face: {"uv": uv_box if face in ("north", "east") else mirrored, "texture": "#blood"}
                 for face in ("north", "south", "east", "west")}
        out.append({"name": f"blood {level} {name}", "from": frm, "to": to, "faces": faces})
    return out


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


def element(name, frm, to, side, end, origin=None, shift=0.0, cull_down=False):
    frm = [frm[0], frm[1] + shift, frm[2]]
    to = [to[0], to[1] + shift, to[2]]
    faces = {}
    for face in ("north", "south", "east", "west", "up", "down"):
        f = {"uv": uv(face, frm, to), "texture": "#" + (end if face in ("up", "down") else side)}
        if side == "trim" and face not in ("up", "down"):
            # Rows 0-1 of the trim texture are the 2-high inlay, row 2 the 1-high one
            w = to[0] - frm[0]
            f["uv"] = [0, 0, w, 2] if to[1] - frm[1] >= 2 else [0, 2, w, 3]
        if face == "down" and cull_down:
            f["cullface"] = "down"
        faces[face] = f
    el = {"name": name, "from": frm, "to": to, "faces": faces}
    if isinstance(origin, dict):
        o = origin["origin"]
        el["rotation"] = {"origin": [o[0], round(o[1] + shift, 3), o[2]], "axis": origin["axis"], "angle": origin["angle"]}
    elif origin is not None:
        el["rotation"] = {"origin": [origin[0], origin[1] + shift, origin[2]], "axis": "y", "angle": 45}
    return el


def split(lower):
    out = []
    for name, frm, to, side, end, *origin in PARTS:
        origin = origin[0] if origin else None
        if lower and frm[1] < 16:
            out.append(element(name, frm, to, side, end, origin, cull_down=frm[1] == 0))
        elif not lower and frm[1] >= 16:
            out.append(element(name, frm, to, side, end, origin, shift=-16))
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
    for level in range(1, BLOOD_LEVELS + 1):
        dump(f"models/block/obelisk_blood_{level}.json", {"ambientocclusion": False,
             "textures": {"blood": f"ender_obelisks:block/obelisk_blood_{level}",
                          "particle": f"ender_obelisks:block/obelisk_blood_{level}"},
             "elements": blood_elements(level)})

    # Item: the whole obelisk with a still eye in its cage, shifted down to fit -16..32
    item_els = [element(n, f, t, s, e, o[0] if o else None, shift=-6) for n, f, t, s, e, *o in PARTS]
    for el in eye_elements():
        el = json.loads(json.dumps(el))
        el["from"] = [el["from"][0], el["from"][1] + 26 - 8 - 6, el["from"][2]]
        el["to"] = [el["to"][0], el["to"][1] + 26 - 8 - 6, el["to"][2]]
        item_els.append(el)
    item_tex = textures(used_textures(item_els))
    item_tex.update({"eye": "ender_obelisks:block/obelisk_eye"})
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
