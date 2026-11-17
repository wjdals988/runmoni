# Colour fixture dispatcher proposal · candidate9

Prepared at 2026-11-17T22:13:12.897416+00:00 by a read-only/W-only reviewer. I read current input8 test source, the two original failed group logs and the retry's existing private failure diagnostic. I used `javap` only on already-cached Compose1.7.0 bytecode. I performed no mapped source write, Gradle/Android test run, build, install, ADB, emulator, network, signing or upload operation. Only W proposal/report files were written.

## Actual failures read

- `android-review5-closet.txt` contains four executed colour tests with one failure: `changingShoesUsesTheirDefaultColorWhileRetappingKeepsTheCustomTint` timed out at its existing30,000ms all-`minimi_loading` readiness check. It is a loading condition/deadline result, not proof that a wrong palette was rendered, a startup SLA failed, or its cause was repaired.
- The exact-input retry `android-review6-closet.txt` also contains four executed tests with one **different** failure: `closetColorChangesKeepReadyItemSelectionsInventoryAndGender` reached `noTextOverflow` and reported `SnapshotStateObserver` previousThreadId286/currentThread2 through `GraphicsLayerOwnerLayer` drawing. Thus the first timeout case did not fail in that retry; this is not a whole-group pass. All three partial successes in either failed group remain excluded.
- The existing private failure diagnostic maps thread286 to `DefaultDispatcher-worker-2` (BLOCKED) and thread2 to `main` (RUNNABLE). Its actual SHA256 is `23c4271c52c56a1f0e2984ba49521ae6cea1bfe6556f2b286382763272f14a2b`. I copied no full private stack or screenshot into this report. This supports a worker/main observation issue in this configured fixture. It does not prove every trigger or establish a production-threading bug/fix.

The parent has separately recorded the controlled test-dispatcher probe and the source8 isolated Standard/bounded-scroll UI pass. Those are parent execution records, not tests performed by this reviewer.

## Candidate9 changes — use v2 only

`colors-fixture-proposal-v2/apply-plan.json` pins three existing files and one new helper. `proposal.patch` is reviewable. None was applied by this reviewer.

1. `RunmoniDesignTestBase` accepts an optional `fixtureRule: OfflineMapComposeRule = OfflineMapComposeRule()` and stores it in the same Rule property. The previous default expression `OfflineMapComposeRule()` is retained exactly. The rest of this source is byte-identical after only that constructor/property normalization, including all helpers, the120,000ms preparation window, text overflow checks and all six test-method blocks contained in that file. Other subclasses retain their default configuration.
2. Only `RunningKitColorsUiTest` (four cases) and `RunningKitColorsNativeUiTest` (one case) explicitly inject the existing offline wrapper around `createAndroidComposeRule<ComponentActivity>(effectContext = StandardTestDispatcher())`. The offline tile provider and actual ComponentActivity/renderer remain.
3. All **17** original colour `performScrollTo()` calls become explicit `boundedScrollIntoView(compose)` calls. The same closest ScrollBy ancestor, coordinates, axis/reverse/RTL calculation and zero-delta criterion are retained; the same ScrollBy semantic action must return accepted. A real main-clock frame progresses the queued action before geometry is read again, with a finite16-attempt limit and displayed assertion. This does not write selected state or skip a control.
4. The shared helper is extracted from the already-compiled/source8-runtime-tested `ExpandedWardrobeSelectionUiTest` helper. After adapting the receiver, the helper body is identical modulo indentation. The expanded selection test's entire `@Test` block remains byte-identical; its thin `reveal(tag)` adapter supplies the same target and label.

All five colour test blocks equal their originals after reversing **only** the declared scroll substitutions. All **50 assertion call sites and their order** remain, including real shoe-pixel HSV thresholds, palette defaults/custom tint, 48dp control sizes, preview bounds, gender, inventory, ownership and point values. All original10,000/20,000/30,000ms `waitUntil` budgets are unchanged. The expanded UI's23 assertion call sites,30,000ms readiness budget, initial pause click and real bitmap comparison remain. Ripple, animation configuration, failure loaders/retry, actual rendering, wallet state and production sources are not substituted or disabled.

## Cached API review and preparation correction

Cached `AndroidComposeTestRule_androidKt` exposes the generic `createAndroidComposeRule(CoroutineContext)` overload used by this proposal. Cached `SemanticsNodeInteractionsProvider` exposes `onNode` as an interface member. During W-only review I removed an unnecessary non-existent top-level `onNode` import from the first draft and corrected helper indentation. The `colors-fixture-proposal/` v1 directory is superseded; **use v2 only**. Neither draft was compiled or executed, and no runtime success is inferred from disassembly or text comparison.

## Required Root follow-up

- Verify the before hashes and completed original-data/settings restoration/owned AVD shutdown before applying the W proposal.
- Freeze/build the resulting test source as a new input. Product, assets, configuration, Rules and signing should remain unchanged.
- Run the changed colour4 plus expanded-selection1 UI cases first, with all original budgets/assertions. A fresh actual pass is required; the30s case may still expose an independent readiness concern and must not be assumed fixed by a dispatcher change.
- Then run the parent's remaining fresh-source plan. Preserve every original false parent/failed group, and count only complete accepted groups with original state restored. This proposal does not award commercial art quality, user visual approval, physical-phone performance, Google/operating-write or signed-public-runtime success.
