# input3 → input4 제한 변경·결과 보존 검토

검토일: 2026-11-16. 이 문서는 **수정 전 소스와 현재 보존된 실패 원문을 읽은 설계 검토**다. input4 수정·빌드·실행·성공은 아직 관측하지 않았다. 진행 중인 input3 소스/검사/Rules/환경은 변경하지 않았으며 WORK 문서만 작성했다.

**추가 관측 우선:** 아래 최초 검토의 진행 중 상태와 조건부55 계획은 마지막 절의 「input3 수집 완료·재생 독립 재검사 추가」를 함께 읽는다. 최초 소스 검토는 보존하며, 최신 실제 whole-pass48과 아직 미완료인 같은 binary3의 독립7 재검사를 합산 완료로 표시하지 않는다.

## 실제 실패와 최소 수정안

### 공유 선택 항목의 46dp 너비

`android-review1-share-ui.txt`는 `everyToolKeepsTextAndActionsUsableAcrossNineViewportAndFontCases`의 ratio 선택 경로에서 실제 너비 **46.0dp**, 최소 요구 **48.0dp**, 기존 허용 오차 **0.5dp**로 실패했다. 실제 6개 실행·1개 실패이며 그룹 전체는 실패다. 숨은 터치 확장이나 테스트 오차를 근거로 통과로 바꾸지 않는다.

`RunShareScreen.kt:530`의 공통 `ShareChoices` Row는 최소 높이만 설정하고 있다. 나중에 다음처럼 **첫 크기 modifier만** 바꾸는 설계가 타당하다.

```kotlin
Row(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
    .background(/* 기존 값 */)
    .selectable(/* 기존 값 */)
    .testTag(/* 기존 값 */)
    .padding(horizontal = 12.dp, vertical = 10.dp),
    /* 기존 정렬 */) { /* 기존 내용 */ }
```

이는 적용된 패치가 아니라 제안이다. `sizeIn`을 background/selectable/testTag보다 앞에 두면 표시 색면과 실제 의미 노드에 최소 크기가 반영된다. 고정 width/height나 `requiredSize`를 사용하지 않아 큰 글씨·선택 체크 아이콘이 필요로 하는 추가 크기를 허용하고, 기존 `FlowRow`의 8dp 가로/세로 간격과 줄바꿈을 보존한다. 최소 너비 증가는 선택 행의 높이·본문 스크롤 위치를 바꿀 수 있으므로 단순히 비율 하나만 다시 누르는 검사로 끝내면 안 된다.

수정 범위는 선택 배치이며 `Role.RadioButton`, 선택/비활성 의미, 콜백, 48dp·전체 측정 경계·텍스트 overflow 판정, IME 기준과 대기 시간은 그대로 둔다.

### 자동 백업 문구 판정

`android-review1-record-auto-backup.txt`는 실제 `automatic_cloud_backup_status`의 의미 텍스트가 **“자동 백업 대기 중이에요”**인데 `assertTextContains("자동 백업 대기")`의 기본 전체 일치 비교로 실패했음을 기록한다. 실제 5개 실행·1개 실패이며 이 그룹의 나머지 4개도 최종 결과로 따로 합산하지 않는다.

현재 소스에서 해당 `assertTextContains` 세 곳은 서로 다른 세 메서드가 아니라 `RunRecordScreenUiTest.automaticBackupShowsItsQueueAndAcknowledgementWithoutARequiredPerRecordAction` 한 메서드의 47/53/60행이다. 각 상태 접두어에 `substring = true`를 명시하는 계획은 상태를 확인하려던 의도와 맞는다. `자동 백업 대기`, `자동 백업했어요`, `대상에서 제외`의 서로 다른 상태 조건과 WAITING→SAVED→EXCLUDED 전환, 직접 백업 콜백 없음 검사는 유지해야 한다. 단순 공통어 “백업”으로 줄이거나 상태 변경을 제거하면 부적합하다. 이 수정은 실제 백업 장애의 제품 수정으로 설명하지 않는다.

## 호출 경계

`ShareChoices`는 이 파일 안의 private 함수이며 호출자는 다음 다섯 종류다.

| 호출 위치 | 항목 | 직접 영향 |
| --- | --- | --- |
| `RunShareScreen.kt:399` | ratio | 1:1 / 4:5 / 9:16의 선택 전후 너비·줄바꿈 |
| `:404` | style | 클린 / 기록 강조 / 스탬프 |
| `:409` | tone | 밝은 글씨 / 어두운 글씨 |
| `:419` | placement | 기록 위 / 가운데 / 아래 |
| `:446` | avatar_side | 미니미 왼쪽 / 오른쪽 |

제품의 진입 경로는 `RunmoniApp`의 기록 페이지 → `sharingRecordId` 분기 → `RunShareScreen` → `RunShareEditorUi` → 해당 `ShareChoices`다. 도메인 draft/Saver, `RunShareCardRenderer`, 사진 로딩·EXIF·PNG 저장, 승인 미니미 렌더러, 지갑·계정·백업 저장소의 코드를 바꿀 필요가 없다. 새 최소 크기로 편집기의 문서 흐름이 달라질 수 있으므로 그 화면을 실제 구성하는 UI/IME 결과는 그대로 재사용하지 않는다.

검사 경계:

- `RunShareUiTest` 5개와 `RunShareControllerTest` 1개는 변경된 편집기를 직접 구성한다. input3 `share-ui` 전체를 제외하고 input4에서 **6개 전체를 새로 실행**한다. 실패 메서드만 고르는 방식이나 기존 controller 부분 통과 보존은 채택하지 않는다.
- `RunShareImeUiTest` 두 메서드는 편집기의 기록 도구·캡션과 IME/실제 윈도 경계를 검사한다. input4의 native15 재실행에 포함하는 계획이 맞다. 작은 너비·큰 글씨·짧은 높이의 선택 전후 wrapping/스크롤 도달성과 footer/Back의 48dp 경계가 중요하다.
- `StudioRefreshUiTest.visibleShareLabelKeepsItsFullHeaderTargetAndOpensTheEditorAtLargeText`도 편집기를 열지만 현재 default 목록에 포함돼 있지 않다. 이 메서드의 과거 결과를 새 공유 화면 결과로 추가 보존해서는 안 된다. 현재 계획의 고유 방법 수를 전체 저장소 Android suite의 완주로 부르지 않는다.
- `RunRecordScreenUiTest` 파일의 자동 백업 메서드가 바뀌므로 `record-auto-backup` 전체 5개를 제외/재실행한다. 같은 파일에 있는 `RunRecordScreenNativeUiTest` 클래스 본문은 계획상 변경하지 않지만 input4 native 전체 재실행에 포함되므로 보존 예외가 필요 없다.

## input3 보존 가능 후보

아래는 **관련 그룹이 최종적으로 whole-pass이고 원래 복원·소스·바이너리 증거를 모두 갖춘 경우에만** 보존할 수 있는 후보 목록이다. 현재 읽은 진행 중 parent JSON의 `completed=false`를 최종 성공으로 바꾸지 않는다. Root가 이미 관측한 앞 4개 외 뒤 그룹의 미래 성공을 이 문서가 확정하지 않는다.

| input3 그룹 | 계획 방법 수 | 변경 경로를 호출하지 않는 근거 |
| --- | ---: | --- |
| backup-room | 21 | 데이터/Room·기록 신뢰성/속도 저장 검사. 공유 Composable이나 이번 변경 검사 파일을 호출하지 않는다. |
| atelier-materials | 7 | 의복/승인 원본 렌더·마스크 검사. 공유 편집기의 선택 항목과 독립적이다. |
| icon | 3 | 실제 drawable/resource 렌더·아이콘 경계 검사. 공유 선택 UI와 독립적이다. |
| share-renderer | 6 | `RunShareCardRenderer` 직접 호출 및 PNG/문자/미니미 합성 검사. `RunShareEditorUi`/`ShareChoices`를 구성하지 않는다. |
| closet | 4 | `ClosetDialog`/색상 조작·작은 부위 프리뷰 검사. 공유 편집기를 열지 않는다. |
| discovery | 7 | 탐색/위치 공유 동의·실제 지도 입력 필터. “공유”라는 용어가 같아도 기록 이미지 편집기 호출은 없다. |
| avatar-identity-playback | 7 | 승인 비트맵·MiniMi·옷장 일시정지와 실제 시간 재생. 기록 공유 선택 UI를 구성하지 않는다. |

합계 **55는 조건부 보존 계획**이다. 이 목록에서 새 `ShareChoices` 변경과 직접 의존하는 추가 그룹을 발견하지 못했다. input3 `share-ui`와 `record-auto-backup`은 보존 부적합하다. input4 계획은 default11(공유6+기록5), native15, map SDK4, online1의 총31이며, 후보55와 중복 없는 경우 고유86이 된다. 실제 proof·방법 ID 집합에서 확인하기 전에는 86 통과로 기록하지 않는다. 새 지도 5개 역시 실제 결과와 온라인/가짜 fixture 경계를 별도 확인한다.

## 최종 채택에 필요한 실제 경계

1. 진행 중 input3 전체 수집이 끝나고 원래 DB/프로필/계정 XML·소유 설정·제어 환경·소스 6맵이 정확 복원된 근거와 소유 AVD 종료 근거를 보존한 뒤 수정한다. 이 문서는 종료를 관측하거나 실행하지 않았다.
2. 새 freeze4와 binary4를 실제 생성한 뒤 freeze3와 6맵을 비교한다. 허용 계획은 `productSources`의 **`ui/RunShareScreen.kt` 한 파일**과 `testSources`의 **`ui/RunRecordScreenUiTest.kt` 한 파일**뿐이다. 나머지 product/test 엔트리와 resources/rendererAssets/approvedSourceCopies/configurationSources는 이름 집합과 바이트 SHA 모두 같아야 한다. 예기치 않은 델타가 생기면 이 검토의 동등성 결론을 그대로 적용하지 않는다.
3. 두 변경은 줄 단위로 검토해 위의 크기 modifier와 세 문구 비교 옵션만 바뀌었음을 확인한다. 필요한 경우 새 파일에서 해당 패치를 메모리로 역적용한 preimage SHA가 freeze3와 일치하는지 검증할 수 있다. 향후 실제 hash/patch를 수동 검토한 Root의 동등성 proof를 연결한다. 이 문서는 아직 없는 freeze4를 대신하는 proof가 아니다.
4. 원래 실패 parent와 raw 바이트를 보존한다. 성공한 그룹만 `(proof, group, methodIds)`로 선택하고 input3 parent 전체를 PASS로 재해석하지 않는다. `share-ui`/`record-auto-backup` 두 실패 그룹의 부분 통과는 0으로 제외하며, 새11 전체가 통과해야 한다. 최종 scope는 **서로 다른 입력의 결과를 소스 동등성으로 보존**한 것임을 명시한다. 이전 입력 재사용=false 또는 같은 APK86·단일 default 전체 PASS라는 표현은 부적합하다.
5. scope는 input3/4 proof·binary·freeze·동등성 검토·원문 raw·정확 복원 근거의 실제 SHA를 연결해야 한다. 최종 선택 방법의 중복0과 선언된 고유 방법 수, 각 whole-group의 actual==expected·JUnit OK를 확인한다. 새로운 Main/런처·시각 검토·공개 서명 APK 실행 여부는 이 고유 Android 검사 수와 별개다.

현재 `work/atelier-refresh/make-scope.py`는 모든 proof가 `passed=true`이고 `binary-build3.json` 하나라고 가정하며 `earlierInputResultsReused=false`를 고정한다. 따라서 **그대로 실행하면 이번 보존 계획을 정직하게 표현할 수 없다.** Root가 실제 최종 자료로 적응하기 전에는 사용하지 않는다. 이 검토는 해당 helper를 편집/실행하지 않았다.

## 검토 시점의 불변 입력

- freeze3 JSON SHA256: `68959ada59e1097370cefd2a6de0b827b863928df08abcc8e66ef38dbae4ac7c`
- binary3 JSON SHA256: `5da8cdebcfdebaa4a4d5951f3db4d78ec9ff10b15a948bc7e90bf53048570c26`
- 기존 `RunShareScreen.kt`: `eac5cb6302ab1f352caf6061670bbd339969c91478e4f3139baf9e5e7f6ca8ac`
- 기존 `RunRecordScreenUiTest.kt`: `cc95de0f4cac9e61df762230ee7307012066d3640ae3323f7308cfb7b4faafb9`
- share-ui 실패 raw: `a33c9aedc6b1f08e406fe1b6fcb625a5abf26cc46b02a57200de63c422145b5a`
- record-auto-backup 실패 raw: `43e0401c5d5d4ce0fa290d9021586b2cb9a9cb559279a37b0f9f68881f71999a`

진행 중 parent JSON은 이후 collector가 완료/복원 필드를 기록할 수 있으므로 이 문서에서 현재의 미완료 parent 바이트를 최종 SHA로 고정하지 않았다. 제품·검사·Rules·Git·빌드·AVD·네트워크 실행은 수행하지 않았다. 할루시네이션 확률은 미측정이다.

## input3 수집 완료·재생 독립 재검사 추가

이 절은 최초 검토 이후의 실제 추가 관측이며, 앞의 소스 변경 설계는 그대로 보존한다.

### 원래 default 수집과 새 실패

`android-review1.json`의 완료된 원문은 `completed=true`, `passed=false`, `plannedProductMethods=66`, `actualPassedProductMethods=48`이다. 보존 가능한 whole-pass 그룹은 backup-room21 + atelier-materials7 + icon3 + share-renderer6 + closet4 + discovery7의 **48**이다. 원래 parent의 SHA256은 `6f10c7fd265dec8f83b1065b537ba8bed7352df51977ebaf22b4c0032bd21cfb`다.

앞서 기록한 share-ui6·record-auto-backup5에 더해 avatar-identity-playback7도 실패했다. `android-review1-avatar-identity-playback.txt`는 실제 7개 실행·1개 실패를 기록하며 SHA256은 `8f4a06a2c0c9b19ae29506fc4eecf9b795e3f460ab825e632092fdc610bda530`이다. 오류는 `lifecycleStopFreezesLastFrameAndStartResumes`의 **첫 `assertMoving` (`AvatarPlaybackTest.kt:156`)**이며 `Normal playback did not produce three different actual captures`다.

소스를 대조하면 이 시점은 `compose.setContent` 뒤, lifecycle을 CREATED로 낮추기 **전**이다. 따라서 이 실패를 lifecycle STOP 이후의 정지/재개 오류로 단정하면 안 된다. `assertMoving`은 초기 약4초 관측창에서 실제 캡처의 pixel hash 3종을 요구한다. 원문에는 실제 hash 개수·준비 완료 시각이 없으므로 “0프레임”, “로딩만 느렸다”, “제품 애니메이션이 고장났다” 중 어느 원인도 확정하지 않는다. 제품 원인·준비 지연·실행 조건의 원인은 미확정이다.

원래 default parent에서 preferences/profile/community account preferences·소유 설정/권한/전원 복원 true, 복원 명령 실패 없음, 실행 후/복원 후 6맵 일치를 읽었다. 이 복원은 원래 실패를 PASS로 바꾸지 않는다. AVD 종료나 새 input4는 이 문서에서 실행하지 않았다.

### 같은 입력의 독립7 재검사

Root는 제품·검사 소스/4초 판정/hash3종 기준/timeout을 바꾸지 않고 **동일 `binary-build3.json`의 그룹7 전체**를 독립 `android-review2`로 재실행하고 있다. 이 검토에서 읽은 시점의 `android-review2.json`은 `completed=false`, `passed=false`, 계획7·집계0이므로 결과는 아직 미확정이다. 이를 준비 지연 수정이나 재생 성공 근거로 쓰지 않는다.

나중에 이 독립 그룹이 whole-pass7이고 원래 APK pair/6맵·raw·원래 데이터/환경의 정확 복원 근거를 갖춘 경우에만, 원래 avatar-identity-playback 실패 그룹7 **전체를 제외**하고 독립7만 선택할 수 있다. 원래 실패 그룹의 나머지6개는 따로 합산하지 않는다. 새 시도도 실패하거나 복원/입력 근거가 맞지 않으면 해당7은 채택하지 않는다. 재검사 성공이 생겨도 원래 실패의 원인을 확정하거나 제품 수정을 입증한 것은 아니다.

최종 범위의 조건부 형태는 **input3 원래 whole-pass48 + 같은 input3 독립7 + input4 새31**이다. 합계86은 여전히 미래의 실제 성공·방법 ID 중복0 검증을 요구하는 계획이다. 최초 계획의55를 원래 default parent의 성공55로 표시하지 않는다. 세 실패 그룹의 원문·parent false를 보존하고, 최종 scope에서 원래 실패3개를 각각 제외 사유와 SHA로 연결한다.

input4 UI/기록 default 재검사 이름은 이제 **`android-review3`** 계획이며, `android-review2`를 input4 proof로 혼동하지 않는다. 아직 없는 input4 결과/해시를 만들지 않는다. 새 동등성 채택 시 input3의 원래 parent와 독립 retry의 입력·복원을 각각 연결해야 하며, 동일 입력의 독립 재검사7과 서로 다른 input3→4 소스 동등성 보존을 구분한다.


## Root 실행 후 보강

같은 binary3의 독립 android-review2는 원화/재생7 전체와 소스·원래 XML/프로필/계정·소유 설정 복원을 완료했다. 최초 parent false와7그룹 실패 원인은 미확정으로 유지한다. Root는 session3-cleanup.json으로 소유PID63563의 정확 종료를 확인한 뒤 ShareChoices minimum48dp와 신규 백업UI1메서드의 substring 옵션3곳만 수정했다. input4-fix.json의 역패치가 input3 바이트를 정확 복원하며 freeze3→freeze4 델타는 지정한 두 파일뿐이다. 다른 원화/리소스/설정/제품/검사 경로는 같다. 새 input4 빌드/검사는 아직 후속 단계다. 원래48 + 독립7은 같은input3의55개이며 이전0.7의 재사용이 아니다.
