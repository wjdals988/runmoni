# 0.7.1 입력1 공유 PNG 6개 → 입력2 동등성 검토

**판정: 아래 고정된 입력2 소스에 대해서는 입력1에서 새로 통과한 공유 PNG 검사 6개를 동등성 근거로 보존할 수 있다.** 입력2에서 6개를 다시 실행했다는 판정이 아니다. CAP/VISOR를 착용한 공유 PNG나 변경된 Compose 재생 경로까지 이 결과로 보증하지 않는다.

이 검토자는 제품·검사·APK·Grid·기존 proof를 수정하지 않았고 빌드·기기·네트워크 작업을 실행하지 않았다. 이 텍스트만 작성했다. Root가 명시적으로 허용한 과거 소스 두 Git blob을 읽었으며 Git 변경은 하지 않았다. 새 binary/Android/CI 성공·APK 출처 커밋은 이 검토에서 추정하지 않는다.

## 실제 입력 식별과 전체 map 차이

- 입력1: `docs/app-review/2026-11-16-pixel-couture/source-freeze1.json`, SHA256 `4b1c940e466ce8e123b9027287e092d59a94f4eb95d0f19b7e308832508866e7`
- 입력2: 같은 디렉터리 `source-freeze2.json`, SHA256 `adfc447032901a7e703f81f01c69aba07dfef7962eee590aff3f81243f18218a`
- 양쪽 설정은 `0.7.1-dev`/code30이다. 검토 시점 입력2 map에 기록된 모든 실제 파일의 SHA가 일치했다.

| map | 입력1/2 파일 수 | 실제 차이 |
| --- | ---: | --- |
| productSources | 115/115 | 아래 생산 파일 4개 |
| testSources | 100/100 | 아래 검사 파일 3개 |
| rendererAssets | 83/83 | 없음 |
| resources | 34/34 | 없음 |
| configurationSources | 11/11 | 없음 |
| approvedSourceCopies | 1/1 | 없음 |

생산 파일의 전체 SHA256은 다음과 같다. 경로는 `app/src/main/java/com/runmoni/app/` 기준이다.

| 파일 | 입력1 SHA256 | 입력2 SHA256 |
| --- | --- | --- |
| avatar/ApprovedAvatarRenderer.kt | `2c223e5343addc4c46514903282b0bbd66b83798bae6ff46b3f07f942383a7ea` | `079d69297199332033957b388fc2b3dd069e3e8a2f6ff1c9c469695e1b0070be` |
| avatar/AvatarHeadwearLayers.kt | `4449160d6e13d1fd65af874afe07984f4eeac155f6b2f44377e85c2b436c2091` | `f22b131bad5f8775ac87402e0323985dfd1d5839bac719121cbd356d88e1990e` |
| avatar/RunningKitArtwork.kt | `bc552acaec6e8f75f57741bb447bb461babef43c75dd62db7553c5b3bed97e21` | `b9ebfad37c86e09fb1647d7272763713ede93bf9fb6b563151b848bf12d696b9` |
| ui/MiniMi.kt | `011d1c8b30cd3bf785a55880d7925a8f30b310382dadb1e696c6e3875f3a2fe5` | `b594b9ad01dc0f8543af97767a4c8c3e662daf81150dc9e6f34bf4a2b65191a2` |

검사 차이 3개는 `AvatarHeadwearLayersTest.kt`의 모든 포즈 챙 끝 경계 검사, Android `WardrobeRendererTest.kt:65`의 CAP/VISOR 실제 Bitmap 경계 검사, `AvatarPlaybackTest.kt:84-102`의 진단 로그 추가다. 현재 재생 검사의 6000ms 기준은 `:96`에 유지된다. 이 문서에서 검사 실행/성공을 판정하지 않는다.

## 이전 본문을 대조한 근거

- Headwear 입력1 본문은 `docs/app-review/2026-11-16-pixel-couture/host-v3-inputs/AvatarHeadwearLayers.kt` 전체 SHA가 freeze1과 같음을 확인했다.
- RunningKitArtwork/MiniMi는 허용된 base `9ab10f8e68f7a95361df5adb73248b249bf2b682`의 해당 두 blob을 읽고 각각 계산 SHA가 freeze1과 같음을 확인했다. Root가 `input1-recovered-sources/`에 보존한 복원 파일도 같은 전체 SHA로 재확인했다.
- ApprovedAvatarRenderer는 입력2의 조건화된 5줄 블록을 기록된 기존 2줄 handProtection 호출로 되돌린 메모리상 텍스트의 전체 SHA가 freeze1과 일치했다. Root의 `input1-recovered-sources/ApprovedAvatarRenderer.kt`도 같은 SHA다. 이는 검토 시점에 재구성·검증한 본문이며 빌드 전에 캡처한 스냅샷이라고 표현하지 않는다. Root의 `recovery.json`도 복원 시점과 최초 캡처 시점을 구분한다.

## 검사 경로와 변경점의 관계

공유 검사 파일은 두 입력에서 전체 SHA `4c1d2be2e8cc6a2f1151bd0540fced1174e66e107f4edfca4782cdd31ea4a923`로 같다. 실제 ApprovedAvatarRenderer 호출은 세 곳이다.

| 공유 검사 | 실제 avatar 호출/입력 | 변경 경로와의 관계 |
| --- | --- | --- |
| coralLeavesApprovedMaleAndFemalePaletteAndBorrowedPixelsIntact | `RunShareCardRendererTest.kt:122-123` | frame2/running=true, top1/bottom1/socks2/shoes2, 남녀 |
| approvedMaleAndFemalePixelsRemainVisibleAndUnchangedUnderExtremeStickerControls | `:162-163` | 같은 스타일, 남녀 |
| squareKeepsSixtyCodePointsAndACompleteLegibleAvatarWithAllMetadata | `:262-263` | 같은 스타일, 남녀 |

세 스타일은 `Models.kt:56-63` 기본값과 `AvatarCatalogue.kt:45-50` 해석에 따라 headwear0/accessory0/costume0/waist0/hydration0이다. 나머지 세 메서드는 photo/theme/showAvatar=false/null 경로 또는 `RunShareCardRendererTest.kt:230-238`의 직접 만든 mutable Bitmap bounds 검사이며 실제 headwear 렌더러를 호출하지 않는다.

1. **Bounded brim과 VERSION.** Headwear 변경은 `AvatarHeadwearLayers.kt:188-204`의 `brim()`과 VERSION이다. `brim()` 호출은 CAP/VISOR의 `:156/:166`뿐이다. 공유의 세 스타일은 `AvatarEquipmentLayers.kt:68-75`에서 headKind=null이므로 이 함수를 호출하지 않는다. costume0도 `ApprovedAvatarRenderer.kt:91/:100`에서 costume 경로를 선택하지 않는다. VERSION은 renderer의 `:94/:134` 캐시 키에 사용되고 픽셀 값·선택 분기·팔레트를 계산하지 않는다. 각 공유 검사에서 새 renderer를 만들며 캐시 크기는 `:64-65`의 Bitmap allocationByteCount 기준이다. 접두어 변경이 이 경로의 픽셀이나 용량 계산을 바꾸지 않는다. Kotlin const 인라인 때문에 compiled class 바이트까지 같다는 뜻은 아니다.

2. **Hand mask 조건화.** 입력2 `ApprovedAvatarRenderer.kt:110-114`는 waist/hydration이 있을 때만 gearHands를 읽는다. 공유 스타일은 둘 다0이라 이전 non-null 인자가 null이 된다. `AvatarEquipmentLayers.kt:79`에서 이 인자는 body Raster에만 전달되며 실제 body draw는 `:80-81`의 vest/belt 조건이 참일 때뿐이다. Raster 생성자는 픽셀을 쓰지 않으며 hand label 검사는 실제 `put()`의 `:248`에만 있다. 공유 스타일은 해당 draw를 하지 않으므로 인자 차이가 출력 배열에 영향을 주지 않는다. top1/bottom1 때문에 `ApprovedAvatarRenderer.kt:103-105`의 기존 coverage frame 경로는 계속 실행되고, `WardrobeCoverage.kt:35-58`에서 hand labels도 함께 로드·검증된다. 따라서 이 공유 경로는 hand mask 무결성 검증을 건너뛰는 경로로 바뀌지도 않는다. costume 자체의 손 마스크 인자 `ApprovedAvatarRenderer.kt:100-102`는 그대로다.

3. **초기 generation 발행 제거.** 두 전체 본문을 대조한 결과 RunningKitArtwork는 StateFlow import/field와 최초 load 끝의 `loadedGeneration.value = 1`만 제거했다. 로드·검증·프레임 추출·`frames = result; return result`는 같다. 입력2의 `:22-27/:68-75`는 여전히 동기 IntArray 반환/캐시 경로다. 공유 검사는 직접 `ApprovedAvatarRenderer.renderForDisplay()`를 호출하므로 이 StateFlow를 수집하지 않는다.

4. **MiniMi 구독/key 제거.** 두 전체 본문에서 RunningKitArtwork import, collectAsState와 key의 generation 인자 제거 및 설명 주석 변경을 확인했다. `MiniMi.kt:136-144`의 직접 avatarBitmap/shared renderer 함수는 그대로다. 공유 검사에는 MiniMi/Compose 구독이 없고, renderer를 직접 만들어 Bitmap을 전달하므로 변경된 composition key/취소/재생 경로를 실행하지 않는다. 이 경로의 UI 동등성이나 6000ms 실패 원인을 본 공유 결과로 판정하지 않는다.

공유 compositor `RunShareCardRenderer.kt`는 전체 SHA `3b5813897c5fc89557abe34eb530912979395d0669585eba97101042a49bd68d`로 같다. `:59/:89-95`는 전달받은 Bitmap을 합성하며 `:233-240`의 bounds cache는 Bitmap과 generationId를 키로 사용한다. 위 세 직접 renderer 입력의 픽셀이 같으므로 이들 공유 출력의 입력/합성/PNG 검증 경로를 보존할 근거가 된다.

## 보존할 실행 결과와 제한

입력1 `android-review1.json`의 share-png-renderer group은 독립 실행 passed/actual6이고, 원문 `android-review1-share-png-renderer.txt`의 `OK (6 tests)`와 SHA256 `263408281ccf77735542cfedf93b3971b84431d459fda22e8d8b511684b04a28`가 기록과 일치한다. 이는 이전0.7 결과가 아니라 현재0.7.1 입력1에서 새로 수행한 6개다.

- 최종 집계에서는 **입력1 실행·입력2 소스 동등성으로 보존한 공유6개**로 표시한다. 입력2 새 실행이나 전체 최신 입력 단일 PASS로 표시하지 않는다.
- identity/playback7 묶음의 부분 성공6개와 6000ms 실패·분리 retry는 별도이며 본 판정으로 합산/승격하지 않는다.
- CAP/VISOR가 포함된 공유 PNG의 바뀐 alpha bounds/극단 배치, Compose 구독/재생·성능·클라우드 밖 기기 동작, input2의 새 binary/실행은 본 보존6개의 검증 범위가 아니다. 새 boundary와 playback 재실행 결과는 Root의 실제 별도 근거가 필요하다.
- 이 판정은 위 freeze2 SHA/실제 map에 한정한다. 이후 제품·검사·원화/마스크/설정이 다시 바뀌면 이 동등성을 자동으로 적용하지 않는다.

검토 범위에서 보존을 거부할 구체적 출력 차이는 발견하지 못했다. timeout 원인 해결이나 성능 향상 수치를 주장하지 않는다.
