#!/usr/bin/env python3
"""Read-only audits of actual Aseprite output against independently exported pinned masks.
This is asset data evidence, never Android execution or aesthetic/user approval.
"""
from pathlib import Path
from PIL import Image, ImageChops
import hashlib, json, sys
root, refs, output, target = map(Path, sys.argv[1:5])
source_path = root / 'app/src/main/assets/avatars/v0.3.0/running-motion.png'
source_sha = hashlib.sha256(source_path.read_bytes()).hexdigest()
assert source_sha == 'f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65'
source = Image.open(source_path).convert('RGBA')
hands = Image.open(root / 'app/src/main/assets/avatars/wardrobe-v1/hand-protection.png').convert('RGBA')
items = json.loads((root / 'docs/design/avatars/v0.9.0/item-plan.json').read_text())['items']
tinted = {'top','bottom','costume','shoes'}
gear = {'ear','eyewear','wrist','waist','hydration'}
result = {'schemaVersion':1,'sourceSha256':source_sha,'scope':'Actual Aseprite exported cells compared with independent reference masks; no Android/aesthetic/user approval claim','items':[],'detailCells':0,'tintCells':0,'passed':False}
for spec in items:
    key = f"{spec['slot']}-{spec['variant']}"
    base = Image.open(refs / f'{key}-base.png').convert('RGBA')
    allowance = Image.open(refs / f'{key}-allow.png').convert('RGBA')
    role = Image.open(refs / f'{key}-roles.png').convert('RGBA')
    rendered = Image.open(output / 'previews' / f'{key}-sheet.png').convert('RGBA')
    record = {'key':key,'name':spec['name'],'cells':[]}
    for row, gender in enumerate(('male','female')):
        for frame in range(8):
            box = (frame*256,row*384,(frame+1)*256,(row+1)*384)
            name = f'{key}/{gender}-{frame}.png'
            path = output / 'cels' / name
            detail = Image.open(path).convert('RGBA')
            assert detail.size == (256,384), name
            alpha = detail.getchannel('A')
            h = alpha.histogram()
            assert h[255] > 0 and sum(h[1:255]) == 0, ('Empty/semitransparent ink',name)
            assert ImageChops.subtract(alpha,allowance.crop(box).getchannel('A')).getbbox() is None, ('Ink escaped independent allowance',name)
            foundation = source.crop(box) if spec['slot'] in gear else base.crop(box)
            composite = rendered.crop(box)
            changed_from_original = sum(a != b for a, b in zip(composite.getdata(), source.crop(box).getdata()))
            assert changed_from_original > 20, ('Selected wearable is visually unchanged under the existing strict pixel criterion',name,changed_from_original)
            assert ImageChops.difference(composite.getchannel('A'),foundation.getchannel('A')).getbbox() is None, ('Foundation alpha changed',name)
            if spec['slot'] in {'top','bottom','costume','shoes'}:
                difference = ImageChops.difference(composite,source.crop(box))
                # Independent foreground hands are excluded from cloth and Aseprite ink.
                assert ImageChops.multiply(difference.getchannel('R'),hands.crop(box).getchannel('A')).getbbox() is None, ('Foreground hand red changed',name)
                assert ImageChops.multiply(difference.getchannel('G'),hands.crop(box).getchannel('A')).getbbox() is None, ('Foreground hand green changed',name)
                assert ImageChops.multiply(difference.getchannel('B'),hands.crop(box).getchannel('A')).getbbox() is None, ('Foreground hand blue changed',name)
            cel = {'name':name,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'opaqueInkPixels':h[255],'changedFromOriginalPixels':changed_from_original}
            if spec['slot'] in tinted:
                tint_path = output / 'cels' / f'{key}/{gender}-{frame}-tint.png'
                tint = Image.open(tint_path).convert('RGBA')
                ta = tint.getchannel('A')
                th = ta.histogram()
                assert sum(th[1:255]) == 0 and ImageChops.subtract(ta,alpha).getbbox() is None, ('Tint escaped authored ink',name)
                region = tint.getchannel('R')
                assert ImageChops.multiply(region.point(lambda v: 255 if v not in (1,2,3) else 0),ta).getbbox() is None, ('Invalid tint role',name)
                assert ImageChops.multiply(tint.getchannel('B'),ta).getbbox() is None, ('Invalid tint reserved byte',name)
                assert ImageChops.multiply(ImageChops.difference(region,role.crop(box).getchannel('R')),ta).getbbox() is None, ('Tint role differs from independently labelled garment',name)
                cel['tintSha256'] = hashlib.sha256(tint_path.read_bytes()).hexdigest()
                cel['tintedInkPixels'] = th[255]
                result['tintCells'] += 1
            record['cells'].append(cel)
            result['detailCells'] += 1
    result['items'].append(record)
assert len(result['items']) == 39 and result['detailCells'] == 624 and result['tintCells'] == 192
result['passed'] = True
target.parent.mkdir(parents=True,exist_ok=True)
target.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'passed':True,'items':39,'detailCells':624,'tintCells':192,'report':str(target)},ensure_ascii=False))
