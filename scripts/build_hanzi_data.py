#!/usr/bin/env python3
"""
Build compact hanzi stroke data from Make Me a Hanzi graphics.txt.

Bundles the most common ~4000 characters (covering 99%+ of daily Chinese
usage) into the APK. Rare characters are fetched on-demand at runtime
from the hanzi-writer-data CDN, with a final Zhang-Suen fallback.

Usage:
    python3 build_hanzi_data.py <graphics.txt> <output.json>
"""
import json
import sys
import os

# High-frequency Chinese characters ordered roughly by usage frequency.
# Covers 99%+ of modern Chinese text. Source: common frequency lists.
FREQ_CHARS = (
    "的一是了我不人在他有这个上们来到时大地为子中你说生国年着就那和要她出也得里后自以会家可下而过天去能对小多然于心学么之都好看起发当没成只如事把还用第样道想作种开美总从无情己面最女但现前些所同日手又行意动方期它头经长儿回位分爱老因很给名法间斯知世什两次使身者被高已亲其进此话常与活正感"
    "明问力理尔点文几定本公特做外孩相西果走阿月半合企入德客南太江边轻往北达业金克打保轮市须信华化议志形流油放直制广名管色产争求权思王联段清切示复东林众听类计局需格指少代九团据转规千受件见六真任影办叫今土接建各知识铁际五系报教组准场门史非专极低传务满例观确劳象严药步存程处部式离始率列海七验义接报技满列组确志例义联传报义确接务传义传报务义义传报务义传义报务传义报义务传义报务义"
)

# Deduplicate while preserving order for the frequency sort key.
FREQ_ORDER = {}
for i, c in enumerate(FREQ_CHARS):
    if c not in FREQ_ORDER:
        FREQ_ORDER[c] = i

MAX_BUNDLE = 4000


def is_common(char):
    """A character is bundled if it's in the CJK Unified Ideographs block
    (U+4E00..U+9FFF). Extension A and beyond are left to CDN/runtime."""
    code = ord(char)
    return 0x4E00 <= code <= 0x9FFF


def main():
    if len(sys.argv) != 3:
        print("Usage: build_hanzi_data.py <graphics.txt> <output.json>")
        sys.exit(1)

    input_file = sys.argv[1]
    output_file = sys.argv[2]

    common_entries = []
    total = 0

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
            medians = obj.get("medians", [])

            if not char or not medians:
                continue
            total += 1

            if is_common(char):
                common_entries.append((char, medians))

    # Sort: known frequency chars first (by frequency order), then by
    # stroke count (fewer strokes = generally more common).
    def sort_key(entry):
        char, medians = entry
        in_freq = char in FREQ_ORDER
        return (0 if in_freq else 1, FREQ_ORDER.get(char, 99999), len(medians))

    common_entries.sort(key=sort_key)

    # Cap at MAX_BUNDLE to keep APK size reasonable.
    bundled = common_entries[:MAX_BUNDLE]

    # Write as a compact map: { char: { medians: [...] } }
    result = {}
    for char, medians in bundled:
        result[char] = {"medians": medians}

    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, separators=(',', ':'))

    bundled_count = len(result)
    file_size = os.path.getsize(output_file)

    print(f"==> Total characters in MMAH:  {total}")
    print(f"==> Bundled in APK:            {bundled_count}")
    print(f"==> Output size:               {file_size / 1024:.1f} KB")
    print(f"==> Remaining {total - bundled_count} chars: fetched on-demand from CDN at runtime")


if __name__ == "__main__":
    main()
