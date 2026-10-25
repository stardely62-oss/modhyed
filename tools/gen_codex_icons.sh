#!/usr/bin/env bash
# Generates the obelisk menu's ability icons with Codex image generation (your ChatGPT subscription), one at a time.
# Usage: tools/gen_codex_icons.sh <output folder> [name ...]; then python3 tools/import_codex_icons.py <output folder>
OUT="${1:?usage: tools/gen_codex_icons.sh <output folder>}"
mkdir -p "$OUT"
STYLE="Minecraft-style pixel art GUI ICON. The square canvas is a 16 by 16 grid of large, perfectly square, flat-colored pixels. ONE small symbol centered, filling about 80 percent of the canvas, with a crisp 1-pixel near-black outline. The background is a flat, uniform, pure magenta (#FF00FF) everywhere around the symbol, no shadow, no vignette. No text, no frame, no gradients inside a pixel. Palette of the End dimension: obsidian black, deep violet and purple, and glowing ender teal-green (#5FD3A0) as the accent; ancient arcane rune magic mood, like an enchanted artifact."
declare -A P=(
  [teleport]="A swirling ender portal vortex: a violet spiral with a bright teal ender pearl at its center and small sparks flying out."
  [public]="An open, watchful ender eye: almond-shaped eye with a teal iris and a black slit pupil, faint violet rays around it."
  [private]="A closed ancient padlock made of dark obsidian with a glowing teal keyhole rune."
  [friends]="Two hooded mage silhouettes standing side by side, violet robes, teal glowing eyes."
  [upgrade]="A teal crystal shard with a bold upward pointing arrow made of glowing runes above it."
  [cooldown]="An hourglass with a dark obsidian frame and glowing teal sand falling."
  [return_shard]="ITEM SPRITE in the diagonal Minecraft item pose (from bottom-left to top-right): a jagged shard of dark violet obsidian crystal with a glowing ender teal rune of a curved arrow pointing back on itself carved into it, two tiny teal sparks. Bold simple shape, at most 7 colours."
  [range]="A rune circle: a thin glowing teal ring with four small violet diamond runes at north, south, east and west."
)
NAMES=("${@:2}")
[ ${#NAMES[@]} -eq 0 ] && NAMES=(teleport public private friends upgrade cooldown range)
for name in "${NAMES[@]}"; do
  [ -s "$OUT/$name.png" ] && continue
  timeout 400 codex exec --skip-git-repo-check --ephemeral -s workspace-write -C "$OUT" \
    "Use your image generation tool to create ONE image. $STYLE Subject: ${P[$name]} Then copy the generated image into the current directory as $name.png. Do nothing else." \
    < /dev/null > "$OUT/$name.log" 2>&1
  echo "$name: $( [ -s "$OUT/$name.png" ] && echo ok || echo FAILED )"
done
