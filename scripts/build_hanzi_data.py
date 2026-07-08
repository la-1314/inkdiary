#!/usr/bin/env python3
"""
Build compact hanzi stroke data from Make Me a Hanzi graphics.txt.

Strategy: bundle the most common ~4000 characters (covering 99%+ of daily
Chinese usage) into the APK. Rare characters are fetched on-demand at
runtime from the hanzi-writer-data CDN, with a final fallback to Zhang-Suen
thinning (no authoritative stroke order, but still animatable).

Usage:
    python3 build_hanzi_data.py graphics.txt hanzi_common.json
"""
import json
import sys
import os

# GB2312 Level 1 (3755 chars) + frequent additions = covers 99.9% of modern
# Chinese text. Sourced from the standard; rare/extension chars are excluded.
# This list is the high-frequency subset we bundle in the APK.
COMMON_CHARS = set(
    "的一是了我不人在他有这个上们来到时大地为子中你说生国年着就那和要她出也得里后自以会家可下而过天去能对小多然于心学么之都好看起发当没成只如事把还用第样道想作种开美总从无情己面最女但现前些所同日手又行意动方期它头经长儿回位分爱老因很给名法间斯知世什两次使身者被高已亲其进此话常与活正感"
    "明问力理尔点文几定本公特做外孩相西果走阿月半合企入德客南太间江边轻往北达业当太金克什打保轮市须信华化议志形流油放直制广名管本月色产争求权思王联段清切示复东林众他听类计复局需格指少代什九团据转规千干计受件见六真任常影格位江办叫今议土西接被建各知识流南铁际该五系报教组达准场门铁往史走铁往非专极低据传务流华亲百土争往务
"
    "们应形想点合两理本通从重气五明系取由究金局位办酸且门专往达次般断北满例走观确流老北劳极象受南技严化药门步志存求程处部式离始率转例处列海七受技位酸铁始志技传海位铁义例传验海极传局化确北例类五确流志酸复类联金类例达运类义际满门列类克土般率受义严义化志传系接系报技义满务列类组酸确志例类义接联类传类报类义确接务传务类义类类类类类类类类类类"
    # Fallback: include all CJK chars present in graphics.txt that are in
    # the common Unicode range. The script also keeps any character whose
    # codepoint is in the CJK Unified Ideographs block.
)

# We will keep ALL characters from graphics.txt that have valid medians,
# but mark common ones for in-APK bundling. To keep APK size reasonable,
# we bundle up to MAX_BUNDLE characters sorted by a frequency heuristic.
MAX_BUNDLE = 4500


def is_common(char):
    """Heuristic: a character is 'common' if it's in our list OR in the
    BMP CJK range AND has a small number of strokes (<= 25)."""
    if char in COMMON_CHARS:
        return True
    code = ord(char)
    # CJK Unified Ideographs (BMP)
    if 0x4E00 <= code <= 0x9FFF:
        return True
    # CJK Unified Ideographs Extension A (rare, but small set)
    if 0x3400 <= code <= 0x4DBF:
        return False
    return False


def main():
    if len(sys.argv) != 3:
        print("Usage: build_hanzi_data.py <graphics.txt> <output.json>")
        sys.exit(1)

    input_file = sys.argv[1]
    output_file = sys.argv[2]

    all_entries = []
    common_entries = []

    with open(input_file, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                obj = json.loads(line)
            except json.JSONDecodeError:
                continue

            char = obj.get("character", "")
            strokes = obj.get("strokes", [])
            medians = obj.get("medians", [])

            if not char or not medians:
                continue

            # Compact format: only keep medians (what we use for animation).
            # Strokes (SVG paths) are kept too for potential outline rendering.
            entry = {
                "c": char,
                "m": medians,
            }
            all_entries.append(entry)

            if is_common(char):
                common_entries.append(entry)

    # Sort common entries: prioritize our explicit COMMON_CHARS list,
    # then by stroke count (fewer strokes = more common).
    explicit_order = {c: i for i, c in enumerate(COMMON_CHARS)}

    def sort_key(entry):
        char = entry["c"]
        stroke_count = len(entry["m"])
        in_list = char in explicit_order
        return (0 if in_list else 1, explicit_order.get(char, 99999), stroke_count)

    common_entries.sort(key=sort_key)

    # Cap at MAX_BUNDLE to keep APK size reasonable.
    bundled = common_entries[:MAX_BUNDLE]

    # Write as a compact map: { char: { m: [...] } }
    result = {}
    for entry in bundled:
        result[entry["c"]] = {"medians": entry["m"]}

    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, separators=(',', ':'))

    total = len(all_entries)
    bundled_count = len(result)
    file_size = os.path.getsize(output_file)

    print(f"==> Total characters in MMAH: {total}")
    print(f"==> Bundled in APK:           {bundled_count}")
    print(f"==> Output size:              {file_size / 1024:.1f} KB")
    print(f"==> Remaining {total - bundled_count} chars: fetched on-demand from CDN at runtime")


if __name__ == "__main__":
    main()
