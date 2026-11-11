# Frozen source7 failures and proposed follow-up

This is a WORK-only draft. It has not been applied, compiled, installed or run.
Root may apply it only after the source7 default and native phases have finished,
their restoration has been checked, and the owned AVD has stopped. Preserve all
source7 evidence and failures. This is not source7's result or a new passing result.

Two independent failures are present in actual source7/API29 evidence:

- `docs/app-review/2026-11-11-run-share/android-review5-map.txt`:
  `unlocatedExplorationShowsABrowsableWorldWithoutInventingARouteOrUserFix`,
  line200. The visible status chooses `failed` first and discards the independent
  no-device-fix warning, even though the world-map accessibility description
  correctly says that the device position is unconfirmed. Five other map tests
  passed in that group. A fast inert unavailable tile source exposes the product
  bug; changing only an exact-text test would conceal it.
- `docs/app-review/2026-11-11-run-share/android-review5-discovery.txt`:
  `mapAndOutsideActionsRemainReadableAcrossNarrowScreensAndLargeText`, line161.
  Actual map viewport height was84dp against the meaningful96dp minimum. The
  readiness log contains completed320/1.0 and320/1.5 iterations; the next case is
  320/2.0. The earlier four discovery tests passed. No current native result is
  inferred from these default component fixtures.

The draft keeps both visible states in one status line, joined by a middle dot.
The no-fix warning remains present during loading, after tile failure, and while
the user can still browse the world. It does not invent a current position or
change camera, privacy, tile provider, retry, authentication or sharing behavior.

The map test gates the inert tile completion so it can prove the no-fix warning
both before and after failure. The completed-failure state must visibly contain
both `지도 연결 실패` and `내 위치 확인 전`, and the viewport must still describe
an unconfirmed position. No assertion is removed or reduced.

At200% text, the constrained discovery map grows from280dp to360dp; the ordinary
280dp choice is retained. The enclosing column already scrolls. This adds80dp of
height while allowing the new independent warning to wrap naturally. A320dp
choice adds only40dp to the observed84dp, and a further status line can consume
36dp at labelSmall12sp/18sp×2, leaving about88dp. That is a layout estimate, not a
measurement of the proposed code;360dp provides room for the original96dp
minimum and must still pass the same nine viewport/font, full-bounds,48dp,
no-overflow and no-sharing-request assertions on a fresh binary. Native minimum
height/accessibility checks remain unchanged.

Required Root follow-up: apply the minimal product/test correction only after
restoration; create a fresh source/binary reference; run scoped map6/discovery5
and relevant native map/discovery checks serially; preserve source7 failures and
report new results separately. Existing default38/native2 results cannot be
carried forward as new-source execution merely because a patch is small.
