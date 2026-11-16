"""Read-only PNG comparison: the v2 cap crown's surviving source fringe.

Writes a diagnostic JSON only. Does not render, edit, or replace either PNG.
"""
from pathlib import Path
from collections import Counter
import hashlib
import json
from PIL import Image

root = Path(__file__).resolve().parents[2]
source = root / 'docs/design/avatars/v0.3.0/running-motion-art-source.png'
preview = root / 'docs/app-review/2026-11-16-pixel-couture/host-cap-v2.png'
sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
assert sha(source) == 'f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65'
original = Image.open(source).convert('RGBA')
result = Image.open(preview).convert('RGBA')
assert original.size == result.size == (2048, 768)
counts = Counter()
examples = []
for y in range(30, 86):
    for x in range(83, 211):
        before, after = original.getpixel((x, y)), result.getpixel((x, y))
        if max(after[:3]) >= 85 or after[3] <= 16:
            continue
        alpha = '0..16' if before[3] <= 16 else '17..191' if before[3] < 192 else '192..255'
        origin = 'unchanged_source' if before == after else 'changed_or_authored'
        counts[(origin, alpha)] += 1
        if origin == 'unchanged_source' and len(examples) < 16:
            examples.append({'x': x, 'y': y, 'sourceRgba': list(before)})
report = {
    'source': str(source.relative_to(root)), 'sourceSha256': sha(source),
    'preview': str(preview.relative_to(root)), 'previewSha256': sha(preview),
    'scope': 'Host v2 CAP, male frame0 only; half-open ROI x83..211/y30..86',
    'darkOutputPredicate': 'max(R,G,B)<85 and alpha>16',
    'counts': [{'origin': key[0], 'sourceAlpha': key[1], 'count': value}
               for key, value in sorted(counts.items())],
    'unchangedSourceExamples': examples,
    'interpretation': 'Surviving dark source pixels in this ROI all have alpha17..191; '
                      'the old >=192 hair gate excluded them. Authored outer ink is separate. '
                      'This does not establish every pose or other visual defect has the same cause.',
    'writesImages': False,
}
target = root / 'work/pixel-couture/head-fringe-diagnostic.json'
target.write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps({'counts': report['counts'], 'sourceSha256': report['sourceSha256'],
                  'previewSha256': report['previewSha256'], 'json': str(target.relative_to(root))}))
