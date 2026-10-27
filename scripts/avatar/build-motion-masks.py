#!/usr/bin/env python3
"""Author fixed identity + fabric masks for the whole-frame swimsuit proposal.

References are read-only. No runtime segmentation and no body-part transforms.
The identity mask locks the original head/neck and source shoe fabric. A visual
review is still required: masks do not prove natural motion or body equivalence.
"""
import hashlib
import json
from collections import deque
from pathlib import Path
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/main/assets/avatars/reference-review"
approved_path = ASSETS / "approved-running.png"
proposal_path = ASSETS / "swimwear-motion-generated.png"
assert hashlib.sha256(approved_path.read_bytes()).hexdigest() == "f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65"
assert hashlib.sha256(proposal_path.read_bytes()).hexdigest() == "bfaa139f349ae8daaacc308c6bb67aeed2dfe738a42ce0755474ac9305f3b074"
approved = Image.open(approved_path).convert("RGBA")
proposal = Image.open(proposal_path).convert("RGBA")
assert approved.size == proposal.size == (2048, 768)
identity = Image.new("RGBA", approved.size)
fabric = Image.new("RGBA", approved.size)
records = []

for row in range(2):
    for frame in range(8):
        origin_x, origin_y = frame * 256, row * 384
        box = (origin_x, origin_y, origin_x + 256, origin_y + 384)
        source = approved.crop(box)
        edit = proposal.crop(box)
        sp, ep = source.load(), edit.load()
        protected = Image.new("L", (256, 384))
        lock = protected.load()
        # The fixed upper region contains all hair and most of the face. Extend
        # around the connected source face/neck skin and its navy outline below.
        cutoff = 175 if row == 0 else 159
        for y in range(cutoff):
            for x in range(256):
                lock[x, y] = 255
        candidates = set()
        for y in range(90, 207):
            for x in range(75, 231):
                r, g, b, a = sp[x, y]
                if a >= 16 and r > 100 and g > 50 and r > g * 1.05 and g > b * 1.4:
                    candidates.add((x,y))
        # Source face pixels seed the connected component; clothing/arms are not
        # inferred at runtime. The resulting mask is an explicit reviewed asset.
        seeds = [p for p in candidates if 110 <= p[0] <= 205 and 120 <= p[1] < cutoff]
        queue = deque(seeds)
        seen = set(seeds)
        while queue:
            x, y = queue.popleft()
            for q in ((x-1,y),(x+1,y),(x,y-1),(x,y+1)):
                if q in candidates and q not in seen:
                    seen.add(q); queue.append(q)
        extension = Image.new("L", (256,384))
        extend = extension.load()
        for x,y in seen:
            extend[x,y] = 255
        # A narrow rim includes the head contour around the source skin region.
        extension = extension.filter(ImageFilter.MaxFilter(5))
        for y in range(cutoff,207):
            for x in range(256):
                if extension.getpixel((x,y)):
                    lock[x,y] = 255
        # Keep existing colored shoe fabric; no recoloring of soles/skin.
        for y in range(280,384):
            for x in range(256):
                r,g,b,a = sp[x,y]
                colored_shoe = (g > r * 1.3 and b > r * 1.15 and g > 65) if row == 0 else (
                    r > g * 1.4 and r > b * 1.2 and r > 120)
                if a >= 16 and colored_shoe:
                    lock[x,y] = 255
        locked = 0
        counts = {1:0,2:0,3:0}
        for y in range(384):
            for x in range(256):
                gx, gy = origin_x+x, origin_y+y
                if lock[x,y]:
                    identity.putpixel((gx,gy),(255,255,255,255)); locked += 1
                    continue
                r,g,b,a = ep[x,y]
                if a < 16:
                    continue
                region = 0
                if row == 0 and 220 <= y <= 309 and 75 <= x <= 193 and g > r*1.3 and b > r*1.15 and g > 62:
                    region = 1
                elif row == 1 and 155 <= y <= 265 and 90 <= x <= 209 and r > g*1.35 and r > b*1.2 and r > 100 and b > g*.68:
                    region = 2 if y < 217 else 3
                if region:
                    luminance = (54*r+183*g+19*b)/256
                    low,high = (44,185) if region == 1 else (75,190)
                    shade = max(0,min(255,round((luminance-low)*255/(high-low))))
                    fabric.putpixel((gx,gy),(region,shade,0,255)); counts[region] += 1
        assert counts[1] > 100 if row == 0 else counts[2] > 100 and counts[3] > 30, (row,frame,counts)
        records.append({"row":row,"frame":frame,"identityLockedPixels":locked,"garmentPixels":counts})

identity.save(ASSETS / "motion-identity-mask.png")
fabric.save(ASSETS / "motion-fabric-mask.png")
report={"schema":1,"status":"unapproved-motion-review", "frames":8,"frameMillis":90,
        "headLockMode":"original upper frame region plus connected source face/neck and contour",
        "noBodyPartRotation":True,"framesChecked":records,
        "assets":{name:hashlib.sha256((ASSETS/name).read_bytes()).hexdigest() for name in (
            "approved-running.png","swimwear-motion-generated.png","motion-identity-mask.png","motion-fabric-mask.png")},
        "limitations":["Source pixel locks are not an aesthetic or natural-motion approval",
                        "Shoe fabric is protected; entire shoe silhouette/sole equivalence is not proven",
                        "Not all 58 wardrobe items have frame layers"]}
(ASSETS/'motion-manifest.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({"framesAuthored":16,"identityMaskSha256":report['assets']['motion-identity-mask.png']}))
