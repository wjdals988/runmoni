# Host preview of production avatar parts

This is a **HOST Java2D compatibility preview, not an Android screenshot**.
The current production AvatarArtRepository.kt and AvatarSpriteRenderer.kt are compiled unchanged.
AvatarStyle is extracted from current Models.kt; current AvatarCatalogue.kt is compiled unchanged.
Android bitmap/canvas/assets/cache and JSON calls are supplied by local host adapters.
Drawing uses nearest-neighbor transforms with antialiasing disabled. Java2D and Android
may rasterize rotations, alpha edges and path boundaries differently. PNGs are lossless;
GIFs use a convenience palette and a 90 ms delay, so compare PNGs for exact host output.

- male.gif / female.gif: 8-frame running previews (presentation names, no gender restrictions).
- costume_1.gif through costume_5.gif: 5 special sets on the same rig.
- *-strip.png: 8-frame contact sheets; catalogue-58.png: every visible recipe.
- landmarks.json: actual renderer pose outputs; checks.json: bounded host assertions.
- wrap-checks.json: near/far wrist and ankle 7-to-0 movement cannot exceed the
  largest other adjacent step by more than 0.01 px. frameAt checks cover -1/719/720 ms.
- component-boundary-checks.json: all 56 frames use 8-neighbor alpha >= 16
  components. Beyond the largest body component, any component over 24 pixels
  fails. Smaller fragments are reported but ignored. This catches large floating
  body/clothing pieces, and can also flag an intentionally detached graphic.
  Foreground bounds and canvas-edge pixel counts are informational: boundary
  contact alone does not prove clipping, and safe padding alone cannot prove no
  pixels were lost before these PNG parts reached the renderer.
- face-roi-checks.json: 24 cheeks/mouth ROI comparisons across 4 hairs and 6 sets.
  Only source head_0 pixels with alpha >= 253 are included; the mask must contain
  at least 100 pixels. Every masked pixel must stay within 3 RGB values per channel.
  This admits minor alpha-254 background compositing differences, while recording
  the largest difference and every pixel exceeding tolerance to detect actual occlusion.
- source-provenance.json: source and manifest hashes recorded by the wrapper script.
- run-status.json must say passed for this run; failed/running means partial diagnostic output.

Alpha connectivity detects an opaque gap between central head/neck and chest regions.
It does not prove skin-color continuity or attractive layering. Human visual review remains necessary.
No AVD, Android runtime, network, installation or app-source modification is involved.
