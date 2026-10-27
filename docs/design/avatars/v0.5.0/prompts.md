# 수영복 전신 시안 제작 지시 — 2026-10-27

Mode: built-in `image_gen`, reference-based identity-preserving edit, `transparent_background=true`.

기준 원화: `../v0.3.0/running-motion-art-source.png` (SHA256 `f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65`). 이전 0.4 조립 결과는 사용자가 거절했다. 이 디렉터리는 새 시안이며 승인·앱 통합을 의미하지 않는다.

## 요청 1: 보드숏 / 비키니

```text
Use case: identity-preserve.
Asset type: transparent full-body pixel art outfit try-on comparison for the Runmoni running RPG avatar.

EDIT TARGET / IDENTITY REFERENCE: the attached approved runner sprite sheet. Use ONLY the FIRST male runner in the TOP ROW and the FIRST ponytail female runner in the BOTTOM ROW. The user strongly rejected a previous modular paper-doll reconstruction. This is a precise clothing edit of these SAME two characters, never a new character design.

Output exactly TWO full-body figures side by side on a genuinely transparent canvas, roughly 4:3 landscape. Left: the short navy-black-haired male runner. Right: the brown high-ponytail female runner with teal hair tie. Preserve their exact original friendly faces, large dark eyes and tiny white highlights, mouth, ear, hair silhouette and multi-tone pixel clusters, warm skin shading, chunky navy contour, head-to-body ratio, short compact limbs, and their FIRST-COLUMN screen-right three-quarter running poses. Preserve the original running shoe shapes and colors: male mint sneakers, female coral sneakers, white soles. Preserve natural neck/shoulder anatomy, original torso width and original arm/leg silhouette. Show the whole hair, hands and both shoes with comfortable transparent margins.

Change clothing ONLY:
- Male: remove the coral singlet. Bare torso with the same soft, simple warm pixel shading as the existing exposed arms/legs, no exaggerated muscles, no anatomical embellishment. Wear TEAL boardshorts with a neat white drawstring and a small white side detail. Boardshorts reach the lower mid-thigh, a little longer than his original running shorts. No shirt, vest, cape or extra gear.
- Female: replace the mint tee and navy tights with a CORAL two-piece bikini: a simple opaque classic bikini top with comfortable shoulder straps and matching opaque bikini bottoms. Clearly two separate swimwear pieces with bare midriff and bare legs. Keep her original slim compact body proportions and the same running pose, no new body shape, no exaggerated curves or sexual pose. Draw her visible skin in the same restrained pixel palette as the original face and arms. No shorts, skirt or one-piece.

Art direction: meticulously match the approved rich RPG pixel sprite finish: crisp square pixel clusters, several intentional hair/skin/fabric highlight and shadow tones, dark navy pixel outlines, detailed fabric folds and soles, no smoothing or blur. It should look like the exact original runner artwork with swimsuits drawn onto it. Do not make a taller figure, small head, long neck, mannequin joints, cylindrical limbs, separated body segments, flat simplistic icons, a new anime style or glossy vector illustration.
No background, ground, glow, drop shadow, typography, labels, palette swatches, grid or extra runners. Actual alpha transparency outside the two figures.
```

## 요청 2: 같은 시안의 색상 편집

대상: `swimwear-mint-coral.png`. 색상을 바꾸기 위해 얼굴·체형·자세를 다시 설계하지 않는다.

```text
Use case: identity-preserve, precise garment color edit.
Edit the attached TWO-RUNNER SWIMWEAR IMAGE itself. Return the exact same composition, transparent canvas size, two figures, faces, hairstyles, skin palette, physique, head-to-body ratio, anatomy, outlines, shoe colors, running poses and every non-fabric detail. This is a color selection demonstration for an avatar app, not a redesign.
ONLY recolor the male runner's teal boardshort fabric to COBALT BLUE (mid-tone approximately #3487EC), keeping the original dark folds and lighter highlights and white drawstring/side detail. ONLY recolor the female runner's coral bikini fabric, BOTH top and bottoms, to LAVENDER PURPLE (mid-tone approximately #A374DF), preserving the original seam, shading and trim. Keep the male mint running shoes and female coral running shoes unchanged. Keep her teal hair tie unchanged. Do not recolor skin, hair, eyes, mouth, outlines, socks, soles or any shoe.
Preserve the exact clothing cuts and coverage. Preserve crisp, detailed pixel clusters and the existing pixel density. No smoothing, glow, scene, labels, swatches, text, additional figures or extra items. Background stays truly transparent with alpha. The two complete runner silhouettes and margins stay in exactly the same positions.
```
