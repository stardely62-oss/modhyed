#!/usr/bin/env bash
# Generates obelisk textures with Codex image generation (your ChatGPT subscription), one at a time.
# Usage: tools/gen_codex_art.sh <output folder>; then python3 tools/import_codex_art.py <output folder>
OUT="${1:?usage: tools/gen_codex_art.sh <output folder>}"
mkdir -p "$OUT"
STYLE="Minecraft-style pixel art block TEXTURE. The whole square canvas is a 32 by 32 grid of large, perfectly square, flat-colored pixels, edge to edge, seamless and tileable. No border, no frame, no perspective, no text, no glow, no gradients inside a pixel. Flat orthographic view of one face. Grim dark fantasy mood, desaturated, very dark."
declare -A P=(
  [obelisk_stone]="Blackened weathered basalt stone of an ancient cursed obelisk: deep charcoal grey with a faint cold blue tint, fine hairline cracks, and a vertical column of small carved runic sigils running down the MIDDLE third of the texture (the sigils are carved grooves, darker than the stone, not glowing). Sides of the texture are plain cracked stone."
  [obelisk_iron]="Old blackened wrought iron plate: hammered dark gunmetal, a row of round rivets along the top and bottom edges, slight dull rust-brown at the edges, worn and grim."
)
for name in obelisk_stone obelisk_iron; do
  [ -s "$OUT/$name.png" ] && continue
  timeout 400 codex exec --skip-git-repo-check --ephemeral -s workspace-write -C "$OUT" \
    "Use your image generation tool to create ONE image. $STYLE Subject: ${P[$name]} Then copy the generated image into the current directory as $name.png. Do nothing else." \
    < /dev/null > "$OUT/$name.log" 2>&1
  echo "$name: $( [ -s "$OUT/$name.png" ] && echo ok || echo FAILED )"
done
