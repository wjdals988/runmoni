# 0.9 appearance and motion plan

frontend-design was read for this change. The product is an outdoor running companion with an approved pixel runner, not an analytics console. The memorable visual element stays the runner; controls, maps and text use a quiet, consistent structure.

## Tokens and layout before implementation

Light base: Chalk `#F7F9FA`, Paper `#FFFFFF`, Ink `#24343D`, Muted `#5B6D73`, Ocean `#356F78`, Mist `#E7EFF1`. Night base: Harbour `#142128`, Deck `#1D2E36`, Fog `#E2ECEF`, Slate `#ADBEC5`, Tide `#9BCDD1`, Cove `#29454C`. Existing garment palettes remain independent. Environment green/amber/red are semantic and paired with text.

Korean system Sans is deliberate: it covers Korean accurately without network font loading or a mismatched decorative Latin face. Existing 16/24 body, 24/32 headings, stronger distance figures and 24dp gutters remain. Appearance preferences live with settings, in three full-width 48dp-or-taller labelled rows: system / light / dark. No popup or new account setting.

```
화면 테마                         [title]
○ 기기 설정에 맞추기
○ 밝게
○ 어둡게
```

Page changes get one short 160ms fade and 8dp entrance movement. There are no animated metric counters or automatic section staggers. The content tree is never duplicated for an outgoing animation, protecting page saveable-state ownership and live actions. OS disabled animations use an immediate visible frame.

## Critique before building

A black canvas with a neon accent would make the original runner harsh and read like a generic fitness template. Revised night tokens use desaturated harbour blues and pale teal, with readable ink and distinct surfaces. Three identical theme cards would add more chrome to an already complex app; revised choices are plain semantic radio rows. A crossfade that keeps two live route trees can duplicate effects or lose state: revised motion is an entrance on the single current tree.

Native free-map appearance changes its already-loaded style in place, keeping camera, route sources, marker images and attribution. It does not reload the style or request a new provider because of a theme change. Raster fallback cartography retains provider pixels and uses a night dim only behind route/labels. Share image colours and approved avatar pixels are not theme-tinted.

## Verification boundaries

New source review, meaningful appearance/motion policy tests, Android light/dark, large text and back/state checks are required. No existing 0.8 execution result counts as a new 0.9 runtime result. A new screenshot is not user visual approval; no market-trend adoption percentage or commercial quality score is invented.
