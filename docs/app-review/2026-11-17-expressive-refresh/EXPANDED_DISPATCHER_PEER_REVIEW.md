# Expanded wardrobe dispatcher/scroll review

Read-only source/cache review for Root's frozen input7 on 2026-11-17 UTC. No app, test, configuration, renderer asset, active driver, AVD, or Android setting was changed; no build, install, ADB, network or emulator operation was run by this reviewer. Only this report and W-only proposed test files/patch/guard manifest were written. `javap` inspected the already-cached bytecode; it did not compile or execute Android/Compose tests.

## Observed facts and bounded conclusion

- The source's four methods use one `createComposeRule(effectContext = StandardTestDispatcher())`, including three synchronous bitmap/wallet methods that never use `compose`.
- Cached JUnit4.13.2 `MethodSorter.DEFAULT` compares method-name Java hash codes first. The UI method hashes to −1858512568, before socks −1209983939, wallet −401030823 and shorts640517619. There is no source `@FixMethodOrder`. Thus the first method normally selected in this class is `closetSelectsNewFreeItemsAndGenderSwitchPreservesTheHiddenSavedShortsId`; an empty class dot suffix is consistent with being in that UI method, not evidence that its pure bitmap methods are slow.
- I independently viewed Root's private current480×854 fixture screenshot. The closet is ready on its initial TOP tab, male original outfit and three visible top thumbnails. The animation control displays a Play triangle. This supports a paused fixture; it does not identify the exact source line by itself and does not establish a production app failure.
- Root then captured the actual owned app Java stack. I read the instrumentation-thread excerpt: `Instrumentation.runOnMainSync` → `AndroidTestOwner.runOnUiThread` → `ActionsKt.scrollToNode(Actions.kt:127)` → `ActionsKt.performScrollTo(Actions.kt:76)` → `ExpandedWardrobeTest…$pick(ExpandedWardrobeTest.kt:224)` → test line230. The current sampled location is the first `pick`'s horizontal tab reveal. Root reported Main's sampled Looper as epoll-idle. A single stack is a sample, not proof of an invariant Main deadlock or an exact count of loop iterations.
- Cached Compose1.7 `ActionsKt.performScrollTo` repeatedly fetches a node and invokes `scrollToNode` while that returns true. It has no progress guard, attempt limit or explicit main-clock advance. `scrollToNode` executes the ScrollBy semantic action via `runOnUiThread`, then returns true when geometry requests movement, without waiting for the launched scroll coroutine to change bounds. With a queued test dispatcher, repeatedly queuing ScrollBy while no recognized recomposition/layout work forces a scheduler tick can leave geometry unchanged. This is a concrete mechanism supported by the observed scroll stack and initial tab screen. The exact whole-run queue sequence was not captured, so it remains a strongly supported test-harness diagnosis rather than a measured production cause.

## Corrections to earlier hypotheses

`MiniMi.kt` does run its lifecycle delay90 loop even when OS animator scale is0 (it selects frame0). That alone does **not** establish an infinite scheduler drain here. Cached `ComposeIdlingResource.isIdleNow` pumps at most100 frames per call, and `AbstractMainTestClock.advanceTimeByFrame` calls `TestCoroutineScheduler.advanceTimeBy(frameDelay)` then `runCurrent`, not `advanceUntilIdle`. The paused current screenshot and actual first-scroll stack make an initial-only pause/clock workaround insufficiently targeted.

Plain `effectContext = Dispatchers.Main.immediate` is not an effective replacement in this Compose build: `AndroidComposeUiTestEnvironment` selects a supplied `TestDispatcher` or falls back to `UnconfinedTestDispatcher`; `createRecomposer` then adds its `ApplyingContinuationInterceptor`, replacing the supplied context interceptor. The fallback interceptor still delegates through the unconfined test dispatcher. It would not demonstrate that the earlier worker→Ripple Looper issue is fixed.

`waitUntil(30_000)` invokes its condition before checking elapsed time. A condition that synchronizes semantics can therefore spend longer in nested idle waits. It must not be described as an absolute30s process bound. The external owned-driver timeout remains distinct from product assertions or a performance SLA.

## W-only proposal

`expanded-dispatcher-proposal/` contains:

- `ExpandedWardrobeTest.kt`: retains all three synchronous bitmap/wallet method blocks byte-for-byte, removes their unused Compose rule/imports. Actual Android bitmap/identity/alpha/sole/palette/wallet checks remain.
- `ExpandedWardrobeSelectionUiTest.kt`: moves the UI method to its own class, keeps the Standard dispatcher, actual initial pause click, real bitmap capture, every wallet/gender/ID/selection assertion, and Ripple/production UI. Its body changes only **seven** declared `performScrollTo()` call sites to a `reveal(tag)` helper, including selected-sock and selected-bottom assertions. There are23 original assertion/fail calls in the UI body; their order is identical. Reversing only those seven substitutions reproduces the original method block exactly.
- The helper uses the same closest ScrollBy ancestor, unclipped target coordinates, viewport, axis delta calculation, reverse/RTL handling and zero-delta reveal criterion as cached Compose Actions. It issues the **same ScrollBy semantic action**, asserts it was accepted, then explicitly advances one main-clock frame before re-reading geometry. It caps attempts at16 and adds a final displayed assertion. It does not directly set selected state, alter a gesture into a state write, inject a bitmap, disable Ripple, force a pose, remove an assertion, or modify production code.
- `apply-plan.json` pins the before/after hashes and exact block/substitution checks. `proposal.patch` is reviewable. Neither file is applied, compiled or executed. API compatibility and runtime behavior still require Root's next fresh build/test.

Root's planned driver split should be pure `atelier-materials`10 methods and separate `atelier-selection`1 method. Total default96 remains unchanged; the moved UI method must be counted only under its new fully qualified class. This makes UI-only diagnosis/retry possible without rerunning the full20-minute renderer group. It does not turn the source7 interrupted/false parent or partial bitmap dots into accepted results.

## Root-only next checks

1. Keep Root's actual source7 interruption/finally/restoration/shutdown evidence unchanged.
2. Review the W patch and actual current original hash before applying; create a new source freeze/build pair after the test-source split.
3. Compile and run the isolated UI method first. A completed actual scroll/preview/selection pass is still required; the source argument is not runtime success.
4. Preserve any new failed parent, raw output, all data/settings restoration and strict pixel/identity checks. The proposal does not certify commercial art quality, production threading, phone performance or user approval.

## Actual read/proposal pins

- Original source7 test: `447c4adcc0c0430241418f154a7e8ffafa6e33c863f2dd580cf04aa6f3a9aff5`.
- Cached Compose test classes: `9072603cb0c2c4ee3e7b43c5e00197a544008009da79fe38e8610a68da23d45c`.
- Cached JUnit4: `8e495b634469d64fb8acfa3495a065cbacc8a0fff55ce1e31007be4c16dc57d3`.
- Root-owned private Java stack: `e492a0bccff0314635a98dbaecb7e5b511c600d59ab5e54a5fae6c4827de076b`.
- Proposed pure test: `5507a25e701156694aebd9419b1eab0221376250d44e2ff5b1dc7b70590ebc78`.
- Proposed UI test: `6d9a807ced1e6ae1a3a0ef2b73ea1db82963b77b35bdf76443570809494ee6aa`.

The private stack and screenshot stay in W; this report does not copy their full bodies into public evidence.

## Root 후속 적용 및 새 빌드

위 검토/제안은 동료의 읽기 전용 작업 시점 기록이다. Root는 실제 스택과 UI를 검토하고 검사3의 안전한 중단/원래 상태 복원 및 AVD3 정확 종료 후 해시 가드로 두 파일을 적용했다. 소스8의 제품·리소스·에셋·설정은7과 동일하고 테스트 두 경로만 달라졌다. 새 빌드는1분31초 성공, 새 JVM266개/실패0·lint오류0·경고67이며 Android 새 실행은 아직 대기다. 원래 UI23개 assertion/순서, 세 순수 검사 본문은 유지한다. 원래false parent와 부분 그룹 제외는 유지한다.
