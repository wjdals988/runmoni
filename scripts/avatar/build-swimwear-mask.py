#!/usr/bin/env python3
"""Author a conservative indexed fabric mask, never modify a reference PNG.

Polygons explicitly delimit three garments in the mint/coral proposal. Fixed
fabric-color constraints exclude navy ink, white trim and skin at their edges.
This authoring step is NOT run by the app; runtime consumes only the saved mask.
"""
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/main/assets/avatars/reference-review"
SOURCE = ROOT / "docs/design/avatars/v0.5.0/swimwear-mint-coral.png"
SOURCE_HASH = "bc95bd8fcfb7e0dbddf302ee0fee5828990c3c251c0bbd59d45be64fbb89fb34"
assert hashlib.sha256(SOURCE.read_bytes()).hexdigest() == SOURCE_HASH
image = Image.open(SOURCE).convert("RGBA")
assert image.size == (1448, 1086)
# Screen-space garment boundaries; no rectangle reaches the face, hair or shoes.
POLYGONS = {
    1: [(297, 602), (467, 627), (506, 754), (402, 817), (333, 783), (221, 763), (238, 716)],
    2: [(1084, 475), (1125, 478), (1150, 531), (1182, 554), (1195, 517),
        (1236, 553), (1240, 600), (1208, 626), (1071, 626), (1058, 573), (1087, 552)],
    3: [(1034, 641), (1094, 651), (1150, 670), (1185, 671), (1180, 715),
        (1157, 741), (1107, 703), (1071, 694), (1017, 693)],
}
mask = Image.new("RGBA", image.size)
result = mask.load()
source = image.load()
counts = {}
for region, points in POLYGONS.items():
    area = Image.new("1", image.size)
    ImageDraw.Draw(area).polygon(points, fill=1)
    area_pixels = area.load()
    count = 0
    for y in range(image.height):
        for x in range(image.width):
            if not area_pixels[x, y]:
                continue
            r, g, b, a = source[x, y]
            fabric = (g > r * 1.3 and b > r * 1.2 and g > 62) if region == 1 else (
                r > g * 1.35 and r > b * 1.2 and r > 90 and b > g * .68)
            if a < 240 or not fabric:
                continue
            # Retain continuous fabric shading along an explicit five-color ramp.
            # Skin/outline/trim never enter the ramp. Runtime does not inspect RGB.
            luminance = (54 * r + 183 * g + 19 * b) / 256
            low, high = (44, 185) if region == 1 else (75, 190)
            shade = max(0, min(255, round((luminance - low) * 255 / (high - low))))
            assert result[x, y][3] == 0, "Overlapping garment labels"
            result[x, y] = (region, shade, 0, 255)
            count += 1
    assert count > 500, f"Missing garment {region}"
    counts[str(region)] = count
mask.save(ASSETS / "swimwear-mask.png")
manifest = {
    "schema": 1,
    "status": "unapproved-review-only",
    "sourceSha256": SOURCE_HASH,
    "approvedRunningSha256": "f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65",
    "maskSha256": hashlib.sha256((ASSETS / "swimwear-mask.png").read_bytes()).hexdigest(),
    "encoding": "RGBA: red=garment ID 1..3, green=shade 0..255 along five-color ramp, blue=0, alpha=255; alpha=0 protects source",
    "garments": {"1": "boardshorts", "2": "bikini top", "3": "bikini bottom"},
    "maskedPixelCounts": counts,
    "polygons": POLYGONS,
    "authoring": "Conservative hand-delimited polygons with fixed fabric constraints; not whole-image hue replacement",
    "limitations": ["Unmasked fabric/antialiased trim retains original color", "Swimwear has one pose, not eight frames",
                    "Color invariance does not establish identity with the approved running reference",
                    "No Aseprite source or Android capture is implied"],
}
(ASSETS / "review-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
print(json.dumps({"maskedPixels": counts, "maskSha256": manifest["maskSha256"]}))
