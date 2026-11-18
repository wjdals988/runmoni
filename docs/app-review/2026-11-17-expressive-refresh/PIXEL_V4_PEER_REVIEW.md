# V4 independent pixel quality review

Reviewed 2026-11-17 UTC. Read-only review of actual V4 host output; the only file written by this reviewer is this report. No Aseprite invocation, builds, installs, transfers, emulator operations, source edits, or asset edits were performed.

## Hair batch: accept as a host material candidate

I directly viewed each complete 2-row, 8-column sheet, covering male frames 0–7 and female frames 0–7 for all three hair materials. I also directly viewed the approved original complete sheet and the earlier V2 original/material comparison. This is a visual acceptance of the three host hair candidates, subject to the independent actual-output guard and fresh Android renderer verification. It is not acceptance of all 39 materials or all 100 catalogue items.

The V4 male hair-6 has a visible connected inner-crown/rear highlight. The earlier V2 comparison's male hair-6 was almost the original; the new full sheet visibly changes the hair surface while retaining the original cut. The new highlight remains within the existing dark hair volume. It does not read as a bright rim outside the silhouette, a helmet band, or a neon stripe. The existing broad dark/shaded planes remain legible.

Hair-4 reads as a curved, side-swept highlight; hair-5 has a more diagonal front-plane highlight; hair-6 has a restrained upper/rear crown reflection. Hair-4 and hair-6 are close material variations, especially on the male character. They must not be described as three new cuts, or as strongly distinguishable silhouettes. Female hair-6 remains the restrained ponytail/backlight material already seen in V2. I did not see the rejected V1's added random speckle treatment reappear.

Across the 48 viewed character cells, I found no obvious new face or ear occlusion, changed eye/mouth identity, broken dark outer contour, sudden highlight placement jump, or accessory-like edge detached from the head. Crown highlights follow the existing head motion and the female ponytail shading follows its existing deformation. The approved body proportions and running poses look intact. These are visual observations; byte-level identity, permitted-region, alpha, and changed-pixel guarantees belong to the separate guard.

## Directly viewed files and provenance

- `/workspace/runmoni/app/src/main/assets/avatars/v0.3.0/running-motion.png` — full original 16-pose sheet. Independently read SHA256: `f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65`.
- `/workspace/runmoni/docs/app-review/2026-11-17-expressive-refresh/aseprite-v2-hair-reference-three.png` — previous original + three-material comparison.
- `/workspace/runmoni/work/expressive-refresh/atelier-v4/categories/hair-1/previews/hair-4-sheet.png` — full 16-pose sheet. SHA256 `fe37022caa05fd7e0b3b3ae5fce8f36bfae2d733435ba4bb39d96c0b8e235e84`.
- `/workspace/runmoni/work/expressive-refresh/atelier-v4/categories/hair-1/previews/hair-5-sheet.png` — full 16-pose sheet. SHA256 `3737f0816d01161cc24e91e73e2938c1bfc656be39017a3ebcdb6cba39a35e43`.
- `/workspace/runmoni/work/expressive-refresh/atelier-v4/categories/hair-1/previews/hair-6-sheet.png` — full 16-pose sheet. SHA256 `a65609fc240b7a99149a6d8b378f92e4f51fc5a6882c2f494d6b00acc35f843f`.

Read `/workspace/runmoni/work/expressive-refresh/atelier-v4/receipts/hair.json`: `completed=true`, `exitCode=0`, 3 projects, 48 PNG cells, script SHA256 `d861fc0ad242ed77e12f92b6bedd88257967398810f25f10a0fd13fd054e04bb`. Independently computed sheet hashes match the receipt. This documents the actual hair batch only; the V3 partial run and its unknown exit/cause are not treated as successful provenance.

## Limits and pending review

The fixed >20 changed-pixel criterion must remain unchanged. I did not execute the 624-cell guard, and the earlier predicted male hair-6 minimum of 362 is not execution evidence. Fresh Android primary rendering for both genders must separately pass the stronger criterion; host appearance alone cannot repair or certify the renderer.

Fresh V4 80/112-pixel contact sheets have not yet been supplied to this reviewer. The earlier V2 small-size limitation remains relevant: hair, cheek/face adornments, ear pieces, and watch textures are naturally subtle at whole-body size. Enlarged region previews can explain a material but do not establish distinct small-size silhouettes. Final small-contact review will be appended after those exact V4 files are directly viewed.

No Android screenshot/runtime review, user visual approval, commercial quality certification, or all-items-ready claim is made here.

## Final V4 small-contact addendum

I directly viewed the actual V4 full contact sheets at 80, 112, and 144 pixels per character, plus the new original/three-material hair comparison. These sheets show the 39 new items, each in a male/female representative pose. Together with the complete hair sheets reviewed above, they support acceptance as an internal host integration candidate. They do not provide a new full-frame visual review of every non-hair item.

At 80 pixels, the clothing and coordinated colour sets, headwear cap, eyewear, shoes, and hydration vest silhouettes are readable. Top panel lines, the female full-length bottoms, shoe panels, and vest pockets remain coherent at 112/144 pixels. I found no obvious detached straps, harsh surface noise, newly distorted faces, or visible body-shape regression in these representative cells. The middle hydration treatment reads as a two-pocket vest; the honest dual-pocket name remains appropriate.

The weakest small-size distinction remains the hair textures, face/cheek adornments, ear pieces, watch details, and some waist/sock treatments. At 80 pixels several adjacent alternatives are very close, and male hair-4/hair-6 are still a restrained comparison at 112 pixels. At 144 pixels their highlight placement is easier to inspect, but the same original haircut deliberately dominates their silhouettes. This is an honest limitation of subtle materials on small, preserved characters; it must not be turned into a claim that all 39 alternatives are immediately distinguishable in the whole-body preview. A region preview helps users inspect those materials without changing the approved eyes, mouth, ears, body, or cut to force distinction.

The new enlarged hair comparison confirms the correction observed in the full V4 hair-6 sheet: its male inner-crown reflection is now visible while the original dark outline and face remain intact. No V1-style added random stippling or neon/helmet band is apparent. The improvement is controlled shading/material work over the approved original, not newly drawn character identities or three new haircuts.

Directly viewed addendum paths, with independently read SHA256:

- `/workspace/runmoni/work/expressive-refresh/aseprite-review-v4/all-items-80px.png` — `53c53860bf1faa954f4f7a896d21e19f16f6b2c1713f441f7e2cd0ddba44b807`.
- `/workspace/runmoni/work/expressive-refresh/aseprite-review-v4/all-items-112px.png` — `8e867bcc5ebe43b126a75eb77f60cba8e5772b5e8e08641cc829a8f9d7c779a2`.
- `/workspace/runmoni/work/expressive-refresh/aseprite-review-v4/all-items-144px.png` — `df48ac59781a73c9d1d8c30b25acc08e384691a3a761e3175bd1c7f2253ebfd6`.
- `/workspace/runmoni/work/expressive-refresh/aseprite-review-v4/hair-reference-three-proposals.png` — `f99fa4dcf18bd8f7499aed88fd32b6b835c92bf6485ea5ff2d2b30ff2ea2a391`.

I also independently read the completed receipt inventory: 13 receipts, all `completed=true` and `exitCode=0`, with 39 projects and 816 PNG cells total. I read the actual `/workspace/runmoni/work/expressive-refresh/aseprite-asset-guard4.json` report: `passed=true`, original source SHA256 `f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65`, 624 detail cells and 192 tint cells. From the report's 624 individual detail-cell records, every `changedFromOriginalPixels` value is >20 (minimum 22). Hair-6's 16-cell minimum is 151; its male-only minimum is 362. These are read results of Root's actual guard execution, not the earlier recipe prediction or a guard execution by this reviewer. Android rendering remains a separate fresh check.

Final peer decision: accept this V4 output as an internal host material candidate, with the small-size limits above. No claim of all 100 items ready, commercial certification, Android/runtime acceptance, or user visual approval is made.
