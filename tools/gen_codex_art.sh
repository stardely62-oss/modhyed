#!/usr/bin/env bash
# Generates the statue textures with Codex image generation, one at a time.
OUT="${1:?usage: tools/gen_codex_art.sh <output folder>}"
mkdir -p "$OUT"
STYLE="Minecraft-style pixel art TEXTURE. The whole square canvas must be exactly a 16 by 16 grid of large, perfectly square, flat-colored pixels, filling the canvas edge to edge. No border, no frame, no background, no perspective, no shading gradients inside a pixel, no text. Limited palette of about 8 colors. Orthographic flat view of one face."
declare -A P=(
  [head_front]="The FACE of a stone golem guardian mob head, seen straight from the front: purple-grey carved stone skin, two glowing magenta crystal eyes, heavy brow, a stern straight mouth, a few cracks."
  [head_side]="The SIDE of the same stone golem guardian head (no face): purple-grey carved stone skin, a small carved ear ridge, a couple of cracks, darker toward the bottom edge."
  [head_top]="The TOP of the same stone golem guardian head: purple-grey stone with three small glowing magenta crystal shards growing from it."
  [pedestal_side]="The SIDE of a polished purple-grey stone statue pedestal: a darker base line at the bottom, a carved horizontal magenta inlay band across the middle, lighter top edge."
  [pedestal_top]="The TOP of a polished purple-grey stone pedestal slab: smooth stone with a slightly darker carved border one pixel wide."
)
for name in head_front head_side head_top pedestal_side pedestal_top; do
  [ -s "$OUT/$name.png" ] && continue
  timeout 400 codex exec --skip-git-repo-check --ephemeral -s workspace-write -C "$OUT" \
    "Use your image generation tool to create ONE image. $STYLE Subject: ${P[$name]} Then copy the generated image into the current directory as $name.png. Do nothing else." < /dev/null > "$OUT/$name.log" 2>&1
  echo "$name: $( [ -s "$OUT/$name.png" ] && echo ok || echo FAILED )"
done
