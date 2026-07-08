#!/bin/bash
# Download full Make Me a Hanzi stroke data and convert to compact JSON.
# Output: app/src/main/assets/hanzi_full.json (or filesDir on device)
#
# Data source: https://github.com/skishore/makemeahanzi
# License: Arphic Public License (Arphic-1999)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ASSETS_DIR="$PROJECT_ROOT/app/src/main/assets"

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

echo "==> Downloading graphics.txt from Make Me a Hanzi..."
curl -L -o "$TMP_DIR/graphics.txt" \
  "https://raw.githubusercontent.com/skishore/makemeahanzi/master/graphics.txt"

echo "==> Converting to compact JSON..."
python3 - "$TMP_DIR/graphics.txt" "$ASSETS_DIR/hanzi_full.json" <<'PYEOF'
import json
import sys

input_file = sys.argv[1]
output_file = sys.argv[2]

result = {}
with open(input_file, 'r', encoding='utf-8') as f:
    for line in f:
        line = line.strip()
        if not line:
            continue
        try:
            obj = json.loads(line)
            char = obj.get("character", "")
            strokes = obj.get("strokes", [])
            medians = obj.get("medians", [])
            if char and medians:
                result[char] = {
                    "strokes": strokes,
                    "medians": medians
                }
        except json.JSONDecodeError:
            continue

with open(output_file, 'w', encoding='utf-8') as f:
    json.dump(result, f, ensure_ascii=False)

print(f"==> Wrote {len(result)} characters to {output_file}")
PYEOF

echo "==> Done!"
echo "    The full hanzi data is now at: $ASSETS_DIR/hanzi_full.json"
echo "    Rebuild the app to include it in the APK."
echo

echo "==> Copying Arphic Public License..."
curl -L -o "$ASSETS_DIR/ARPHICPL.TXT" \
  "https://raw.githubusercontent.com/skishore/makemeahanzi/master/APL/ARPHICPL.TXT" 2>/dev/null || true

echo "==> All done."
