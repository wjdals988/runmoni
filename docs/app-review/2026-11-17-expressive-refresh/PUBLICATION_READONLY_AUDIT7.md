# Publication helper read-only audit: input7

This audit read the eight requested W helpers, their workflow/schema consumers, public input7 build/install evidence and the current local dashboard catalog. It parsed helper syntax with `ast.parse` without executing a helper. It performed no ADB, network, build, test, emulator, publication, Git mutation or source edit. Only this report was written. Actual release source C, completed Main/publication/Production proofs do not exist at this audit stage; no future identifiers or outcomes are supplied here.

## Result

No stale input5/input6 semantic pin or definite schema mismatch was found in these helpers. One concrete evidence-binding gap needs Root's explicit check: `verify-dashboard-production.py` validates successful Node counts and the raw-log hash, but does not itself bind that run to the prepared dashboard tree, command or time. Root has confirmed that it will run a genuinely new sequential Node check against the prepared/staged tree and validate those additional bindings before Production verification. No old Node log may be reused.

## Verified current pins and boundaries

- `compare-release-source.py` accepts only `binary-build7.json`/`source-freeze7.json`/`build7.txt`. Its freeze pin equals actual SHA256 `f76b4d5fd82218f2dcdae05fd39930fb6d8b382537994922d3a1e18e60db834d`. The required caller-supplied binary SHA must be actual `f795921555bc45d747f330a11b2fa83db29b90967c04327129de4d39057d2c34`. No source-C default is present. All six complete Git blob inventories are checked; the local TEST/debug overlay and connected signed release runtime remain explicitly separate.
- Actual input7 evidence reports JVM266/fail0/error0/skip0 and lint Error0/Fatal0/Warning67. Source-map inventory is131 product/116 test/48 resource/981 renderer/1 approved source/11 configuration files. These are local results, not future CI counts.
- `live-review.py --binary binary-build7.json` dynamically derives `installation7.json`, `pre-install7-private.xml` and `pre-install7-appearance-private.json`. The actual installation fields and both backup filenames match its expectations. No input5/6 install default remains.
- Accepted Main scope is fresh schema2, exact final binary/hash, `earlierInputResultsReused=false`, empty `equivalenceReviews`/`restorationExceptions`, and actual per-group/raw/source/restoration references. `supplementalRestorationReferences` must be present even when it is an empty list. Diagnostic proof methods are excluded: dispatcher proofs have an empty `plannedMethodIdsFromCurrentSource`, so they cannot satisfy product-group declaration/count checks. The possible final96+15+4+1=116 product total requires those actual groups to pass; dispatcher2 diagnostics are separate.
- `measure-ci.py` derives actual C/run/attempt from `release-context.json`; it reads fresh attempt-bound XML/artifact timestamps and produces `observedJvm`/`observedLint`. `verify-connected-release.py` requires those exact measured counts/context/archive identity, then independently checks signed connected stage-draft execution, retained signer, readiness timestamps, actual source-C notes and exactly three assets. No local266/67 or older CI count is used as a future default.
- Public input/dashboard helpers require all seven original Main roles and individual Root pixel/privacy reviews, selecting only home-dark/new-technical-set/sharedphoto-stamp/map-dark for the four-image gallery. Weather/GPS injection and public signed APK runtime remain disclaimed. Current Main source supplies the `fixture.distanceMeters` consumed by `prepare-public-inputs.py`; no missing-field mismatch was found.
- Current local dashboard baseline still has9 projects,24 Runmoni release notes and4 screenshots, with actual0.8/code31 APK SHA `5c2c6a12faf75d38f4328046a3a93537472d37842963e4357f0b45fbed62531b`. The0.8 constants in `prepare-dashboard.py` and `finish-publication-docs.py` are intentional preservation baselines. Root must capture a new post-fetch baseline; a remotely changed baseline must be reviewed, not silently forced back.
- Publication day is derived from actual timezone-aware `publishedAt` in Asia/Seoul. `finish-publication-docs.py` takes an explicit cleanup proof and dynamically follows Main's actual binary/scope. Use the actual final owned-session cleanup that references the completed Main proof; session1/2 historical cleanup cannot substitute. Feed sourceRevision remains APK C after the later docs/feed merge D.

## Node manifest: exact existing requirements

`verify-dashboard-production.py --node-tests PATH` currently reads these keys:

| Field | Required observed value |
| --- | --- |
| `exitCode` | `0` |
| `tests` | Actual positive parsed count |
| `passed` | Equal to `tests` |
| `failed` | `0` |
| `skipped` | `0` |
| `rawLog` | Actual raw-log filename resolved relative to the Node manifest's parent directory |
| `rawSha256` | SHA256 of that exact complete raw log |

The whole Node manifest is retained in the Production proof and final docs read these counts. The verifier also requires actual dashboard merge CI success, but this does not establish when or against which local tree the supplied Node log was produced. Its `--plan` input must be the actual `prepare-dashboard.py --mode apply` output with `mode="apply"` and `dashboardWritten=true`.

Record and independently validate the following supplemental fields for the new run; current helpers preserve extra Node fields but do not automatically enforce them:

- `command`: exact executed argv array, and `cwd`: actual dashboard working directory. Record the command actually used; do not prefill a guessed command.
- `startedAtUtc` and `finishedAtUtc`: timezone-aware observed timestamps surrounding this specific process, with end after start. Preserve its actual exit status and raw output before parsing counts.
- `testedHeadRevision`: actual dashboard HEAD at execution; `testedIndexTreeOidBefore` and `testedIndexTreeOidAfter`: actual staged Git tree object IDs, required equal. Preserve the corresponding eventual commit/merge identity separately when it exists.
- `preparedPlan`: file/SHA256 reference to the exact applied dashboard plan, plus `catalogSha256` for `src/lib/projects.json`, `updaterSha256` and `gallerySha256` mapping the four new PNG paths to actual hashes. Compare those bytes against the applied plan/gallery before and after testing.
- `testedFilesMatchIndex`: actual observed equality of the relevant working files and staged tree. A staged-tree ID alone does not establish what Node read when unstaged changes exist.
- Before accepting Production evidence, Root verifies the tested tree/content is the tree actually committed/deployed, the run occurred after preparation, raw-log bytes still match `rawSha256`, and all measured counts belong to this new invocation. No future tree/commit/time value is known yet.

## Feature merge manifest: required actual raw PR shape

`prepare-public-inputs.py` reads `E/feature-merge.json`; `prepare-dashboard.release_input` requires all of:

| Field | Required actual relationship |
| --- | --- |
| `url` | `https://github.com/wjdals988/runmoni/pull/<actual positive PR number>` |
| `mergeRevision` | Actual full40-hex APK source C |
| `raw.state` | `"MERGED"` |
| `raw.url` | Exactly equal to top-level `url` |
| `raw.mergeCommit.oid` | Exactly equal to `mergeRevision`/C |

Capture the real `gh pr view --json` observation with this shape. A REST-only `{merged:true, merge_commit_sha:...}` object does not supply the expected fields and would fail this schema even for a real merge. Also record actual base/main, PR head and merge time where available for human review. No PR number/C/merge time is invented here.

## Proof relocation and immutable handoffs

- The connected-release verifier writes `proof.json` beside `executed-driver.py` and versioned metadata/checksum sidecars inside its new W attempt directory. `prepare-dashboard.release_input` hardcodes the driver reference as `verification.parent / "executed-driver.py"` and checks `proof.executedDriverSha256`. If Root copies/renames the proof to `E/connected-release-public.json`, copy the exact sibling driver as `E/executed-driver.py` as well, preserving its hash; a renamed driver alone will not resolve. Alternatively retain a dedicated public evidence subdirectory and pass its actual proof path. Never overwrite a different existing driver/proof.
- Metadata/checksum basenames must remain `runmoni-0.9.0-dev.metadata.json` and `SHA256SUMS-0.9.0-dev.txt`; their exact bytes must equal the verified immutable public sidecars. Preserve actual copied verification proof bytes/SHA. Do not copy private job logs, private report archives or private screenshots into public evidence.
- `release-source-comparison.json` resolves its `executedSourceFreeze` relative to its own directory. Keep the actual input7 freeze there or maintain a valid exact reference; Main and scope references similarly require their actual binary/freeze/raw/executed helper files at their referenced paths.
- Root's seven-image `root-main-visual-review.json` must bind the real Main proof through `{file,sha256}`, with `actuallyViewedWithViewImage=true`, `privacyReviewed=true`, `userVisualApproval=false`, and one exact file/hash/source/individual-review entry per original. No helper supplies missing human review.
- Public manifests reject references whose path contains `.secrets` or `private`. A successful verifier attempt directory must have a suitable public-proof path; private diagnostics remain in W. Generated manifest references must continue resolving after evidence is organized.

## Audited helper snapshot

All entries parsed successfully without execution:

| Helper | SHA256 |
| --- | --- |
| compare-release-source.py | `3fc756fd52af41a16a900a34c7ad516240b0f8808794962fd6ff298eef8461c9` |
| live-review.py | `6d79dcf313373f8f00061274b1394ade470fc6744d0c9382da63dba3296cc09a` |
| measure-ci.py | `52c8ef98eaa15e2b29bc271964fd1a0d317229cd63b136df51817d93d886e983` |
| verify-connected-release.py | `6cf47b060f9581da7ae247e16ae2fdc8c68919bbe32222b95ee6699edef5bd42` |
| prepare-public-inputs.py | `55232079570bb8ac0087c6220e78186a3691a9dfd2ec06cc158a44985ed69922` |
| prepare-dashboard.py | `cef9064830f79c2a04e4932dda8ffb8ac839c714fa1833ba73a4653c8eb9f4f3` |
| verify-dashboard-production.py | `84864b4e1a964ea8407407caf2924a5319665662dfd82087bb31f5cdf425cb92` |
| finish-publication-docs.py | `16544e794d3938413770dcc36c6ce815f2eca860ae38f49ff88c37a064ad521a` |
