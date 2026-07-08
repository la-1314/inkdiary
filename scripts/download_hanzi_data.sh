#!/bin/bash
# Download full Make Me a Hanzi stroke data and convert to compact JSON.
# Output: app/src/main/assets/hanzi_common.json
#
# This is the same script the CI workflow runs. Use it locally to refresh
# the bundled data without waiting for CI.
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

echo "==> Converting to compact JSON (common chars)..."
python3 "$SCRIPT_DIR/build_hanzi_data.py" \
  "$TMP_DIR/graphics.txt" \
  "$ASSETS_DIR/hanzi_common.json"

echo "==> Copying Arphic Public License..."
curl -L -o "$ASSETS_DIR/ARPHICPL.TXT" \
  "https://raw.githubusercontent.com/skishore/makemeahanzi/master/APL/ARPHICPL.TXT" 2>/dev/null || true

echo "==> Done!"
echo "    Bundled data: $ASSETS_DIR/hanzi_common.json"
echo "    Rare characters are fetched on-demand from CDN at runtime."
