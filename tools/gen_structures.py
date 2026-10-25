"""Writes the ruined obelisk structure template (data/ender_obelisks/structures/ruined_obelisk.nbt).

A small grim ruin, 7x5x7: a cracked deepslate platform, the ruined obelisk in the middle, a broken pillar,
fallen column pieces, a skull, cobwebs and moss, and a half-buried chest with loot. Positions left out keep the
terrain. Standard library only: python3 tools/gen_structures.py
"""
import gzip
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/data/ender_obelisks/structures/ruined_obelisk.nbt"
DATA_VERSION = 3465  # Minecraft 1.20.1

# --- a minimal NBT writer: compounds, lists, ints, strings ---


def _str(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def _payload(value):
    """Returns (tag id, payload bytes)."""
    if isinstance(value, bool):
        return 1, struct.pack(">b", int(value))
    if isinstance(value, int):
        return 3, struct.pack(">i", value)
    if isinstance(value, str):
        return 8, _str(value)
    if isinstance(value, list):
        if not value:
            return 9, struct.pack(">bi", 0, 0)
        tag, _ = _payload(value[0])
        return 9, struct.pack(">bi", tag, len(value)) + b"".join(_payload(v)[1] for v in value)
    if isinstance(value, dict):
        body = b""
        for key, v in value.items():
            tag, data = _payload(v)
            body += struct.pack(">b", tag) + _str(key) + data
        return 10, body + b"\x00"
    raise TypeError(type(value))


def write_nbt(path, root):
    tag, data = _payload(root)
    raw = struct.pack(">b", tag) + _str("") + data
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(raw))


# --- the ruin ---
P = "polished_deepslate"
blocks = {}  # (x, y, z) -> (name, properties, nbt)


def put(x, y, z, name, props=None, nbt=None):
    blocks[(x, y, z)] = ("minecraft:" + name if ":" not in name else name, props or {}, nbt)


# Ground layer: a broken ring of slabs around the centre, some gone (the terrain shows through)
platform = {
    (1, 2): P, (2, 1): "cracked_deepslate_tiles", (3, 1): P, (4, 1): "deepslate_tiles", (5, 2): "cobbled_deepslate",
    (1, 3): "deepslate_tiles", (2, 2): P, (3, 2): "cracked_deepslate_tiles", (4, 2): P, (5, 3): P,
    (1, 4): "mossy_cobblestone", (2, 3): P, (4, 3): "deepslate_tiles", (5, 4): "cracked_deepslate_tiles",
    (2, 4): "cobbled_deepslate", (3, 4): P, (4, 4): "cracked_deepslate_tiles", (2, 5): "mossy_cobblestone",
    (3, 5): "deepslate_tiles", (4, 5): P, (3, 3): P, (0, 3): P, (6, 3): "cobbled_deepslate",
}
for (x, z), name in platform.items():
    put(x, 0, z, name)

put(3, 1, 3, "ender_obelisks:ruined_obelisk")
put(3, 2, 3, "air")  # room for the restored obelisk's top half
put(3, 3, 3, "air")

# A broken pillar on the west side, two fallen pieces of it lying east
put(0, 1, 3, P)
put(0, 2, 3, "cracked_deepslate_bricks")
put(0, 3, 3, "cobbled_deepslate_wall", {"east": "none", "north": "none", "south": "none", "up": "true", "waterlogged": "false", "west": "none"})
put(5, 1, 2, P)
put(5, 1, 3, "cracked_deepslate_bricks")
put(6, 1, 3, "deepslate_brick_slab", {"type": "bottom", "waterlogged": "false"})

# Grim details
put(2, 1, 2, "skeleton_skull", {"rotation": "10"})
put(4, 1, 5, "cobweb")
put(1, 2, 3, "cobweb")
put(2, 1, 4, "moss_carpet")
put(4, 1, 1, "moss_carpet")

# The half-buried chest
put(1, 0, 1, "chest", {"facing": "south", "type": "single", "waterlogged": "false"},
    {"id": "minecraft:chest", "LootTable": "ender_obelisks:chests/ruined_obelisk"})

palette, palette_index, entries = [], {}, []
for (x, y, z), (name, props, nbt) in sorted(blocks.items()):
    key = (name, tuple(sorted(props.items())))
    if key not in palette_index:
        palette_index[key] = len(palette)
        state = {"Name": name}
        if props:
            state["Properties"] = dict(props)
        palette.append(state)
    entry = {"pos": [x, y, z], "state": palette_index[key]}
    if nbt:
        entry["nbt"] = nbt
    entries.append(entry)

write_nbt(OUT, {"DataVersion": DATA_VERSION, "size": [7, 5, 7], "palette": palette, "blocks": entries, "entities": []})
print(f"wrote {OUT.relative_to(ROOT)}: {len(entries)} blocks, {len(palette)} states")
