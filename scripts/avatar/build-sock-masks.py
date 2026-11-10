"""Compile explicit sock label DATA against immutable approved source/masks.

This does not modify source art, infer missing skin, or run the v0.4 rig.
Authored axes describe visible source regions only. Runtime uses labels rather
than color/skin detection. Visual acceptance remains pending after compilation.
"""
from pathlib import Path
from collections import deque
import colorsys
import hashlib
import json
import math

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
DOC = ROOT / "docs/design/avatars/socks-v1"
APP = ROOT / "app/src/main/assets/avatars/socks-v1"
SOURCE = ROOT / "docs/design/avatars/v0.3.0/running-motion-art-source.png"
SOURCE_SHA = "f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65"
FABRIC = ROOT / "docs/design/avatars/running-kit-v1/fabric-mask.png"
FABRIC_SHA = "462433ce98a7a2f52c98823d851bed462418bbab18f4262c75f6113456e74bb5"
WARDROBE = ROOT / "docs/design/avatars/wardrobe-v1/coverage.png"
WARDROBE_SHA = "2d0e8375a5644311b1337cd369708d5ebf35e0cef3314d6d7c2145382d650115"
VERSION = "approved-socks-v1"
SIZE = (2048, 768)
CELL = (256, 384)
MINIMUM_FABRIC_Y = [267] * 8 + [250, 274, 274, 250, 250, 274, 280, 250]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def read_pinned(path, expected):
    data = path.read_bytes()
    if sha(data) != expected:
        raise ValueError(f"Immutable input changed: {path.name}")
    return data


def decoded(path):
    with Image.open(path) as image:
        if image.size != SIZE:
            raise ValueError(f"Invalid source-sheet dimensions: {path.name}")
        return image.convert("RGBA")


def project(x, y, leg):
    a, b = leg["knee"], leg["ankle"]
    dx, dy = b[0] - a[0], b[1] - a[1]
    squared = dx * dx + dy * dy
    if squared < 16:
        raise ValueError("A visible segment needs distinct authored endpoints")
    t = ((x - a[0]) * dx + (y - a[1]) * dy) / squared
    normal = ((x - a[0]) * -dy + (y - a[1]) * dx) / math.sqrt(squared)
    return t, normal


def shoe_lock(rgba, fabric, y, rules):
    r, g, b, alpha = rgba
    if y < rules["minimumY"] or alpha == 0:
        return False
    h, saturation, value = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    hue = h * 360
    red = any(low <= hue <= high for low, high in rules["redHueInclusiveRanges"]) and (
        saturation > rules["redMinimumSaturationExclusive"] and value > rules["redMinimumValueExclusive"])
    white = saturation <= rules["neutralMaximumSaturation"] and value >= rules["neutralMinimumValue"]
    ink = .2126 * r + .7152 * g + .0722 * b <= rules["inkMaximumLuminance"]
    return (fabric[3] == 255 and fabric[0] == 3) or red or white or ink


def visible_ankles(src, protected, cell, rules, exclusions):
    accepted = [False] * len(src)
    if cell["gender"] != "female":
        if cell.get("visibleAnkleBoxes"):
            raise ValueError("Visible-ankle extension is female source data only")
        return accepted, []
    boxes = cell["visibleAnkleBoxes"]
    if len(boxes) != 2:
        raise ValueError("Each female cell must author its two ankle candidate bounds")
    inside = Image.new("1", CELL)
    draw = ImageDraw.Draw(inside)
    for bounds in boxes:
        if (len(bounds) != 4 or any(type(v) is not int for v in bounds) or
                not (0 <= bounds[0] <= bounds[2] < 256 and 250 <= bounds[1] <= bounds[3] < 384)):
            raise ValueError("Ankle candidate rectangle exceeds the authored lower cell")
        draw.rectangle(bounds, fill=1)
    in_boxes = list(inside.get_flattened_data())
    candidates = set()
    for i, (r, g, b, alpha) in enumerate(src):
        if not in_boxes[i] or protected[i] or exclusions[i] or alpha < rules["minimumAlpha"]:
            continue
        h, saturation, value = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        low, high = rules["hueDegreesExclusiveLowerInclusiveUpper"]
        if low < h * 360 <= high and saturation >= rules["minimumSaturation"] and value >= rules["minimumValue"]:
            candidates.add(i)
    components = []
    while candidates:
        seed = candidates.pop()
        queue = deque([seed])
        part = [seed]
        while queue:
            index = queue.popleft()
            x, y = index % 256, index // 256
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    nx, ny = x + dx, y + dy
                    neighbor = ny * 256 + nx
                    if 0 <= nx < 256 and 0 <= ny < 384 and neighbor in candidates:
                        candidates.remove(neighbor)
                        part.append(neighbor)
                        queue.append(neighbor)
        keep = len(part) >= rules["minimumComponentPixels"]
        if keep:
            for index in part:
                accepted[index] = True
        components.append({"pixels": len(part), "accepted": keep,
            "bounds": [min(i % 256 for i in part), min(i // 256 for i in part),
                       max(i % 256 for i in part), max(i // 256 for i in part)]})
    return accepted, components


def compile_masks():
    original_bytes = read_pinned(SOURCE, SOURCE_SHA)
    fabric_bytes = read_pinned(FABRIC, FABRIC_SHA)
    wardrobe_bytes = read_pinned(WARDROBE, WARDROBE_SHA)
    # Bundle copies must be the exact inputs used by the Android renderer.
    read_pinned(ROOT / "app/src/main/assets/avatars/v0.3.0/running-motion.png", SOURCE_SHA)
    read_pinned(ROOT / "app/src/main/assets/avatars/running-kit-v1/fabric-mask.png", FABRIC_SHA)
    read_pinned(ROOT / "app/src/main/assets/avatars/wardrobe-v1/coverage.png", WARDROBE_SHA)
    annotation_bytes = (DOC / "annotations.json").read_bytes()
    ann = json.loads(annotation_bytes)
    if (ann["schemaVersion"], ann["maskVersion"], ann["sourceSha256"],
            ann["fabricMaskSha256"], ann["wardrobeMaskSha256"]) != (
            1, VERSION, SOURCE_SHA, FABRIC_SHA, WARDROBE_SHA):
        raise ValueError("Annotations do not bind the pinned approved inputs")
    if (ann["canvas"], ann["cell"], ann["frames"], ann["rows"]) != ([2048, 768], [256, 384], 8, 2):
        raise ValueError("Invalid annotation dimensions")
    source, fabric, wardrobe = decoded(SOURCE), decoded(FABRIC), decoded(WARDROBE)
    recipes = ann["recipes"]
    expected_cells = {(gender, frame) for gender in ("male", "female") for frame in range(8)}
    if len(ann["cells"]) != 16 or {(c["gender"], c["frame"]) for c in ann["cells"]} != expected_cells:
        raise ValueError("Annotations require exactly sixteen distinct source cells")
    for name, flag, start, cuff in (("crew", 1, .55, [.55, .66]), ("knee", 2, .12, [.12, .23])):
        r = recipes[name]
        if (r["flag"], r["start"], r["end"], r["cuff"]) != (flag, start, 1.0, cuff):
            raise ValueError("Review changed coverage/encoding before compiling")
    if not 0 < recipes["ribHalfWidth"] <= 2:
        raise ValueError("Rib highlights must remain a narrow authored strip")
    ankle_rules = ann["visibleAnkleRules"]
    protection_rules = ann["shoeProtectionRules"]
    if (ankle_rules["minimumAlpha"], ankle_rules["hueDegreesExclusiveLowerInclusiveUpper"],
            ankle_rules["minimumSaturation"], ankle_rules["minimumValue"],
            ankle_rules["minimumComponentPixels"], ankle_rules["connectivity"]) != (200, [13, 55], .16, .18, 40, 8):
        raise ValueError("Review changed visible-ankle selection bounds before compiling")
    if (protection_rules["minimumY"], protection_rules["redHueInclusiveRanges"],
            protection_rules["redMinimumSaturationExclusive"], protection_rules["redMinimumValueExclusive"],
            protection_rules["neutralMaximumSaturation"], protection_rules["neutralMinimumValue"],
            protection_rules["inkMaximumLuminance"]) != (250, [[0, 13], [345, 360]], .16, .18, .20, .60, 24):
        raise ValueError("Review changed independent footwear protection before compiling")
    if len(ann["knownPixels"]) != 17:
        raise ValueError("Keep the independently read positive/negative source-pixel checks")
    mask = Image.new("RGBA", SIZE)
    ankle_mask = Image.new("RGBA", SIZE)
    protection_mask = Image.new("RGBA", SIZE)
    inspection = []
    for cell in ann["cells"]:
        row = 0 if cell["gender"] == "male" else 1
        frame = cell["frame"]
        if type(cell["minimumFabricY"]) is not int or cell["minimumFabricY"] != MINIMUM_FABRIC_Y[row * 8 + frame]:
            raise ValueError("Review changed per-cell upper-thigh protection before compiling")
        box = (frame * 256, row * 384, frame * 256 + 256, row * 384 + 384)
        src = list(source.crop(box).get_flattened_data())
        fab = list(fabric.crop(box).get_flattened_data())
        cov = list(wardrobe.crop(box).get_flattened_data())
        exclude = Image.new("1", CELL)
        for polygon in cell.get("excludePolygons", []):
            ImageDraw.Draw(exclude).polygon([tuple(p) for p in polygon], fill=1)
        exclusions = list(exclude.get_flattened_data())
        if len(cell["legs"]) != 2 or {leg["id"] for leg in cell["legs"]} != {"A", "B"}:
            raise ValueError("Each cell must independently author two possible visible segments")
        for leg in cell["legs"]:
            if not 0 < leg["radius"] <= (14 if row == 0 else 24):
                raise ValueError("Review wider segment coverage explicitly")
            if any(len(leg[k]) != 2 or any(type(v) is not int or not 0 <= v < limit
                   for v, limit in zip(leg[k], CELL)) for k in ("knee", "ankle")):
                raise ValueError("Invalid cell-local authored endpoint")
        low, high = recipes["sourceShadeRanges"][cell["gender"]]
        if not 0 <= low < high <= 255:
            raise ValueError("Invalid explicit material shade range")
        protected = [shoe_lock(rgba, fab[i], i // 256, protection_rules) for i, rgba in enumerate(src)]
        ankles, ankle_components = visible_ankles(src, protected, cell, ankle_rules, exclusions)
        labels = []
        counts = {"crew": 0, "knee": 0, "crewCuff": 0, "kneeCuff": 0, "rib": 0}
        leg_counts = {leg["id"]: {"crew": 0, "knee": 0} for leg in cell["legs"]}
        for i, rgba in enumerate(src):
            fabric_region = fab[i][0] if fab[i][3] == 255 else 0
            covered_skin = cov[i][3] == 255 and cov[i][0] & 4 != 0
            below_fabric_boundary = i // 256 >= cell["minimumFabricY"]
            eligible = (covered_skin and fabric_region == 0 and below_fabric_boundary) if row == 0 else (
                (fabric_region == 2 and below_fabric_boundary) or ankles[i])
            if not eligible or rgba[3] == 0 or exclusions[i] or protected[i]:
                labels.append((0, 0, 0, 0))
                continue
            x, y = i % 256, i // 256
            flags, cuff = (3, 0) if ankles[i] else (0, 0)
            if not ankles[i]:
                for leg in cell["legs"]:
                    t, normal = project(x, y, leg)
                    if abs(normal) > leg["radius"]:
                        continue
                    for name in ("crew", "knee"):
                        recipe = recipes[name]
                        if recipe["start"] <= t <= recipe["end"]:
                            flags |= recipe["flag"]
                            leg_counts[leg["id"]][name] += 1
                            if recipe["cuff"][0] <= t <= recipe["cuff"][1]:
                                cuff |= recipe["flag"]
                            if abs(normal) <= recipes["ribHalfWidth"]:
                                cuff |= 4
            if flags == 0:
                labels.append((0, 0, 0, 0))
                continue
            if ankles[i]:
                luminance = .2126 * rgba[0] + .7152 * rgba[1] + .0722 * rgba[2]
                shade = round(max(0, min(1, (luminance - 65) / 160)) * 255)
            else:
                original_shade = cov[i][1] if row == 0 else fab[i][1]
                shade = round(max(0, min(1, (original_shade - low) / (high - low))) * 255)
            labels.append((flags, shade, cuff, 255))
            counts["crew"] += bool(flags & 1)
            counts["knee"] += bool(flags & 2)
            counts["crewCuff"] += bool(cuff & 1)
            counts["kneeCuff"] += bool(cuff & 2)
            counts["rib"] += bool(cuff & 4)
        # A completely occluded segment may correctly contribute zero pixels;
        # a selectable cell must still visibly distinguish both sock lengths.
        if counts["crew"] == 0 or counts["knee"] == 0:
            raise ValueError(f"No visible authored sock pixels: {cell['gender']} {frame}")
        if not any((label[0] & 2) and not (label[0] & 1) for label in labels):
            raise ValueError(f"Sock lengths are not distinct: {cell['gender']} {frame}")
        if any(label[3] and protected[i] for i, label in enumerate(labels)):
            raise ValueError("Sock data intersects independent shoe/sole/ink protection")
        if any(ankles[i] and (labels[i][0] != 3 or labels[i][2] != 0) for i in range(len(labels))):
            raise ValueError("Every visible ankle belongs to both sock lengths")
        for known in ann["knownPixels"]:
            if known["gender"] != cell["gender"] or known["frame"] != frame:
                continue
            x, y = known["point"]
            index = y * 256 + x
            if tuple(known["sourceRgba"]) != src[index]:
                raise ValueError(f"Independently read original pixel changed: {cell['gender']} {frame} {x},{y}")
            if ankles[index] != known["visibleAnkle"] or protected[index] != known["shoeProtected"]:
                raise ValueError(f"Known positive/negative pixel classification failed: {frame} {x},{y}")
            if (known["visibleAnkle"] and labels[index][0] != 3) or (
                    not known["visibleAnkle"] and labels[index] != (0, 0, 0, 0)):
                raise ValueError(f"Known visible-ankle or protected-label regression: {frame} {x},{y}")
        tile = Image.new("RGBA", CELL)
        tile.putdata(labels)
        mask.paste(tile, (frame * 256, row * 384))
        ankle_tile = Image.new("RGBA", CELL)
        ankle_tile.putdata([(255, 255, 255, 255) if yes else (0, 0, 0, 0) for yes in ankles])
        ankle_mask.paste(ankle_tile, (frame * 256, row * 384))
        protection_tile = Image.new("RGBA", CELL)
        protection_tile.putdata([(255, 255, 255, 255) if yes else (0, 0, 0, 0) for yes in protected])
        protection_mask.paste(protection_tile, (frame * 256, row * 384))
        inspection.append({"gender": cell["gender"], "frame": frame, "pixels": counts,
                           "minimumFabricY": cell["minimumFabricY"],
                           "visibleSegmentContributions": leg_counts,
                           "visibleAnklePixels": sum(ankles), "ankleComponents": ankle_components,
                           "shoeProtectionPixels": sum(protected), "protectedIntersection": 0,
                           "seedAxes": cell["legs"], "visualApproval": "pending"})
    # Validate everything before writing the new data files. Existing inputs
    # are read-only; only this new sock-mask namespace receives output.
    DOC.mkdir(parents=True, exist_ok=True)
    APP.mkdir(parents=True, exist_ok=True)
    mask.save(DOC / "sock-mask.png", optimize=False)
    ankle_mask.save(DOC / "ankle-coverage.png", optimize=False)
    protection_mask.save(DOC / "shoe-protection.png", optimize=False)
    mask_bytes = (DOC / "sock-mask.png").read_bytes()
    ankle_bytes = (DOC / "ankle-coverage.png").read_bytes()
    protection_bytes = (DOC / "shoe-protection.png").read_bytes()
    manifest = {
        "schemaVersion": 1, "maskVersion": VERSION, "canvas": [2048, 768], "cell": [256, 384],
        "frames": 8, "rows": 2, "sourceAsset": "avatars/v0.3.0/running-motion.png",
        "sourceSha256": SOURCE_SHA, "fabricMaskSha256": FABRIC_SHA,
        "wardrobeMaskSha256": WARDROBE_SHA, "annotationSha256": sha(annotation_bytes),
        "maskFile": "sock-mask.png", "maskSha256": sha(mask_bytes),
        "minimumFabricY": MINIMUM_FABRIC_Y,
        "visibleAnkleFile": "ankle-coverage.png", "visibleAnkleSha256": sha(ankle_bytes),
        "shoeProtectionFile": "shoe-protection.png", "shoeProtectionSha256": sha(protection_bytes),
        "knownPixels": ann["knownPixels"],
        "encoding": "A255,R flags(crew1/knee2),G shade0..255,B cuff flags(crew1/knee2)+rib4. A0 copies input.",
        "visualApproval": "pending", "scope": ann["eligibility"],
        "authoring": "Fixed source-bound labels from manually proposed axes; no rig, hidden skin, or new source pixels."
    }
    for directory in (DOC, APP):
        (directory / "sock-mask.png").write_bytes(mask_bytes)
        (directory / "ankle-coverage.png").write_bytes(ankle_bytes)
        (directory / "shoe-protection.png").write_bytes(protection_bytes)
        (directory / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    (DOC / "mask-inspection.json").write_text(json.dumps({"sourceSha256": SOURCE_SHA,
        "maskSha256": sha(mask_bytes), "visibleAnkleSha256": sha(ankle_bytes),
        "shoeProtectionSha256": sha(protection_bytes), "knownPixelChecks": len(ann["knownPixels"]),
        "visualApproval": "pending", "cells": inspection}, indent=2) + "\n")
    if (sha(SOURCE.read_bytes()), sha(FABRIC.read_bytes()), sha(WARDROBE.read_bytes())) != (
            sha(original_bytes), sha(fabric_bytes), sha(wardrobe_bytes)):
        raise ValueError("An immutable input changed during compilation")
    print(json.dumps({"maskSha256": sha(mask_bytes), "cells": inspection,
        "sourceUnchanged": True, "visualApproval": "pending"}, indent=2))


if __name__ == "__main__":
    compile_masks()
