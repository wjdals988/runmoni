# input3 → input4 Android 검증 범위 검토

검토일: 2026-11-15 UTC. 읽기 전용 소스·기존 원문·JSON·SHA 검토이며 이 검토에서 Android/Gradle/ADB/네트워크를 실행하지 않았다. 앱·검사 소스는 수정하지 않았다.

## 판단

**input3에서 전체 통과한 지정 8개 그룹 77개를 input3 실행 결과로 보존하고, input4에서 제안한 60개를 새로 실행하는 범위는 타당하다.** 유지하는 검사들의 구체적인 입력·콜백·렌더링 경로가 아래 7개 제품 변경과 겹치지 않거나, Home의 유일한 정렬 변경이 해당 검사 판단 대상에 영향을 주지 않는 것을 소스로 확인했다. 이는 input4 APK에서 77개를 실행했다는 뜻이 아니다. 새로운 60개의 실행 결과는 이 검토 시점에 미확인이다.

후속 기록은 반드시 `input3 보존 77개 + input4 새 실행 60개`로 구분한다. 두 묶음은 method ID 기준 중복이 없지만, `동일 APK 137개 통과`, `일반 110개 단일 실행 통과`, `SnapshotStateObserver 결함 해결`이라고 쓰면 안 된다. 이전 공개 0.6 결과는 이 집계에 사용하지 않는다.

## 입력과 기존 실행의 증거

기준 경로는 `docs/app-review/2026-11-15-companion/`이다.

| 파일 | SHA256 |
| --- | --- |
| source-freeze3.json | 01dc19ffc010723edeac8f092ffe8e8bfb7355b26a2ff8a94c90a2552009e419 |
| source-freeze4.json | 26ebe86bcf861395ee8e267976db51e8b5c5a4cec3180d53c0ce726d0aead629 |
| binary-build3.json | b83feaeeb976c3ba5c3f811e0bf930cc72041d93c8e912a1c6ae2df13cd6ed1e |
| binary-build4.json | ec5003bacb11b4bf7929a783f351723101307e7ba9f1368c496678d0528e183c |
| android-review1.json | 2016ec626f351a4cdcd892190d26cc33121b459fc150393a2a58faf912e90e8b |

`ec5003…`는 build4 **메타데이터 JSON**의 해시다. build4 APK는 `2958bbdd2fd152855271662d28c36d4651520eb58a85e7abbb45058d70ca4bc7`, 192,174,317 bytes이며, test APK는 `8ad1013979f1bbd37bd451c4c0de8758f27f5ffd946c7b0365477dc08fad0c3d`, 4,696,768 bytes다. 다음 실행에서는 이 APK pair의 실제 설치 해시와 source-freeze4를 다시 결합해야 한다. build4 근거에는 새 JVM 219개, failures/errors/skips 0, lint Error/Fatal 0, Warning 66이 기록되어 있다. 이 검토는 그 빌드를 다시 실행하지 않았다.

`android-review1.json`은 source-freeze3 / binary-build3의 실제 API29/TCG 실행을 가리킨다. `completed=true`, `passed=false`, 계획 110개, 전체 통과 그룹의 실제 합계 97개다. 실패 그룹 `screens` 11개와 `closet-navigation` 2개는 보존 77개에서 전부 제외한다. 실패 그룹의 부분 통과를 추출하지 않는다. 원래 JSON과 실패 원문은 그대로 남긴다.

input3의 before/after/복원 뒤 sourceMaps 6종 일치, binaryEvidenceUnchanged, driverSourceUnchanged, observerHelperSourceUnchanged가 모두 true다. 각 보존 그룹의 `originalProfilePreserved=true`, 최종 preferences/profile/community-account 복원 및 명시된 controlled settings/permissions/power 복원 true, restorationCommandFailures는 빈 배열이다. 이 환경 복원은 JSON의 allowlist 범위이며 전체 OS 동일성을 뜻하지 않는다.

보존할 각 raw 파일을 직접 읽어 SHA가 JSON과 일치하고 `OK (N tests)`의 N이 expected/actual과 같은 것을 확인했다.

| 보존 그룹 | 실제 N | input3 raw SHA256 |
| --- | ---: | --- |
| community | 11 | cc800e535e6d478ae7ac88509b3c80b87462070f11e4552621a0d3e5e500eefd |
| account-information | 14 | 4e84924678b701f01516b0133e3c93caf2f3949bc05f83a25a5f6a624feaa860 |
| closet-consumer | 1 | 2feba19d7d99ce9b114b0def4abd4cedd564b0873b750b434eda533bcfc4874c |
| closet-colors | 2 | abaa788781f1583726033d8c528fd63d0466eb9aa7bcdc500612e042ba1efc2c |
| record-share | 11 | 771489a25f68b3e35de4c7f2c9527bcfb2f02b0e6080fb95967065e480744ac0 |
| map-modal | 17 | db6118272e5f229c87a33874a04e29fbfbf9810facd376bc3c81112ddbeb2e9d |
| share-png-renderer | 6 | 6f378a1ebec89402dcb5a7f3762eb5ee99c860908ba96c6e266cd467ac5a5ead |
| avatar-renderers | 15 | f37680f073882eaa2e99ea03df826b6340f800850ec99d5df98eb8eb5ba3920e |
| 합계 | **77** | 각 그룹 전체 통과, parent 전체 실패 상태는 유지 |

## 변경 소스와 실행 경로

source-freeze3/4의 실제 맵을 비교한 변경 목록은 `work/companion-redesign/input3-to4-changes.json`과 일치한다. productSources는 113→114, testSources는 97→99개다. resources 34개, rendererAssets 83개, approvedSourceCopies 1개, configurationSources 11개는 항목·해시 모두 같다. 보존 77개의 concrete test class가 들어 있는 파일은 전부 동일 해시다.

| input4 제품 변경 | 보존 검사와의 관계 | 새 검증/한계 |
| --- | --- | --- |
| AppViewModel.kt | 보존 UI는 `RunmoniApp(state, FakeActions)` 또는 직접 화면 fixture다. 실제 VM을 만들지 않는다. FakeActions 및 화면 상태/액션 계약은 동일하다. | profile-local 8개는 실제 VM을 포함하므로 새 실행한다. 이 묶음만으로 실제 GPS·Firestore sharing write까지 검증했다고 말하지 않는다. |
| RunnerSharingController.kt / 새 RunnerSharingGateway.kt | 보존 RunnerDiscovery UI는 fake actions와 상태를 사용한다. 보존 RunShareControllerTest의 컨트롤러는 별개의 기록 이미지 공유 컨트롤러이며, 변경된 runner-presence 공유 컨트롤러가 아니다. | 새 JVM RunnerSharingControllerTest 10개가 expected UID, cleanup 중 계정/동의 변경, 늦은 activation/프로필 read, retry fence, 기존 run의 정지 간섭을 검사한다. 실제 Firebase adapter/서버 성공을 대신하지 않는다. |
| RunnerPresenceRepository.kt | 보존 ComponentActivity 화면 fixture는 이 실제 repository를 초기화·호출하지 않는다. RunmoniApplication의 해당 의존성은 lazy이며 onCreate는 이를 강제로 생성하지 않는다. | expectedUid 전/후 검사·계정별 cleanup 변경은 정적 검토와 controller fake 경계 검사로 확인한다. 새 Firebase SDK 3개는 닉네임/프로필 검사이고 운영 presence write 증거가 아니다. |
| RunnerSharingPreferenceStore.kt | 보존 UI가 참조하는 NEXT_RUN 상수 외에 실제 store를 실행하지 않는다. | sharing-preferences를 기존 3개 대신 현재 6개 모두 새 실행한다. 새 3개는 commit 실패와 즉시 suppress/late ACK 경계를 검사한다. |
| MapLibreMapPane.kt | 보존 map-modal 및 기록 화면은 `OfflineMapComposeRule` 또는 명시적 LocalMapTileSource를 제공한다. unchanged MapPane.kt는 이 값이 있으면 RouteTileMap 호출 후 return하므로 변경된 native map 구현을 실행하지 않는다. | native-sdk 현재 4개와 새 native-online 1개를 새 실행한다. provider 없는 실제 Main의 경로와 fixture 경로를 섞어 보고하지 않는다. |
| RunmoniDestinations.kt | input3 보존된 원문과 현재 파일의 차이는 Home의 캐릭터/날씨 Row `CenterVertically → Top` 한 곳뿐이다. 버전 shortcut/탭/다른 목적지의 구현은 동일하다. | screens 11개에서 Home을 새 확인한다. account-information 중 Home에서 시작하는 검사의 버전 진입 동작은 동일하지만, 당시 Home PNG를 input4 최종 화면 사진으로 재사용하면 안 된다. |

공통 `RunmoniDesignTest.kt`의 변경은 실패 catch에서 failureType/최대 128 thread×24 frame을 semantics 재조회보다 먼저 쓰고, 부가 semantics·스크린샷을 별도 runCatching으로 분리하는 진단 변경이다. 정상 경로의 assertion, readiness 조건, 120,000ms timeout, 텍스트 overflow 기준은 유지한다. 이 차이는 이미 통과한 그룹의 의미를 바꾸지 않는다. 실패 원인이나 재현 빈도가 해결되었다는 근거는 아니다.

### 렌더러 15개를 보존할 수 있는 구체적 이유

RunningKitRendererTest/WardrobeRendererTest/ExpandedWardrobeTest와 AvatarIntegrationTest의 concrete test source, 실제 renderer/domain/MiniMi/옷장 경로, 83개 자산, 승인 원본 사본 및 빌드 설정의 해시가 동일하다. 이들은 bitmap 합성 또는 직접 MiniMi/ClosetDialog fixture를 사용하며 변경된 Home Row·실제 AppViewModel·native map·runner presence 경로를 필요로 하지 않는다. 일부 검사는 LocalWallet/PilotClient의 serialization을 사용하지만 이 소스들도 동일하다. input3 raw 15개 전체 PASS 및 원래 profile 복원이 실제 근거다. 따라서 **이번 0.7 input3에서 새로 실행된 15개**를 보존할 수 있다. 사용자 원화 승인, 실제 Main 스크린샷, 공개 서명 APK 런타임으로 바꾸어 표기하지 않는다.

## input4 새 실행의 정확한 방법 수

`android-review.py`는 import/실행하지 않았다. AST에서 spec 상수만 읽고, 현재 Kotlin package/class/@Test 선언을 정적으로 펼쳐 method ID를 확인했다. 보존 77개는 모두 input3의 plannedMethodIdsFromCurrentSource에 있으며, input4 새 60개와의 교집합은 0이다. 새 60개 안에도 중복이 없다.

| input4 새 범위 | 방법 수 | 실행 이유 |
| --- | ---: | --- |
| default: screens | 11 | 이전 실패 그룹 전체 + 새 Home 정렬 |
| default: closet-navigation | 2 | 이전 실패 그룹 전체, 실패 진단 보강 뒤 관측 |
| default: profile-local | 8 | 실제 VM 회귀, ACK receipt/typed failure |
| default: sharing-preferences | 6 | 변경된 저장·즉시 억제·late ACK |
| default: widget-measurement | 9 | 최종 입력의 위젯 측정 회귀(보수적 재실행) |
| default 소계 | **36** | |
| native | 15 | 최종 입력의 큰 글씨/IME/하단 조작 회귀 |
| landscape | 1 | partyChatBackReturnsToTheSamePartyScrollAndCrewTab; 보존 community의 4개 선택 방법과 다름 |
| native-sdk | 4 | 변경된 native map/footer와 현재 검사 소스 |
| native-online | 1 | 새 실제 지도 온라인 fixture |
| Firebase SDK | 3 | demo SDK profile/nickname; 운영 연결 성공과 구별 |
| 별도 phase 소계 | **24** | |
| 새 실행 합계 | **60** | 이 문서 작성 시 아직 통과 결과 아님 |

기존 97개에서 다시 실행할 profile-local 8 + old sharing-preferences 3 + widget 9 =20개를 빼면 보존 77개다. 기존 실패 그룹 13개와 새 sharing-preferences 3개가 새 default36 안에 포함된다. 이 산술은 실패 그룹의 일부 검사, 이전 0.6, 또는 중복 방법을 더하지 않는다.

## 채택 조건과 남는 경계

1. input4 실제 설치 pair/source-freeze4 guard, 새 각 그룹의 whole PASS와 실제 개수·raw SHA, 원래 profile/account/preferences 및 controlled 환경 복원을 모두 확인한 뒤에만 새 60개를 채택한다. 최종 source/자산/설정이 한 번 더 바뀌면 이 검토를 다시 적용해야 한다.
2. input3 원본 JSON의 parent=false, 두 실패 그룹, SnapshotStateObserver 원인 미확정을 그대로 보존한다. 진단 patch 뒤 재통과는 원인 해결의 증거가 아니다. 새 실패 그룹은 부분 PASS를 집계하지 않는다.
3. 보존 PNG는 input3 출처를 유지한다. 특히 Home 픽셀은 달라졌으므로 final Home 사진은 새 screens 또는 새 Main 실행에서 얻어야 한다. 컴포넌트 fixture를 실제 Main으로 표시하지 않는다.
4. UID/gate/cleanup은 fake UI 77개로 검증했다고 주장하지 않는다. 새 JVM controller 검사·새 실제 VM 및 preference 검사·정적 adapter 검토를 각각 표시한다. 운영 presence 쓰기, 실제 Google/휴대폰, 공개 서명 APK 런타임과 실제 네트워크 장기 안정성은 별도 경계다.
5. 단일 최종 APK에서 모든 retained contract를 다시 실행해야 하는 별도의 정책이 있다면 77개도 재실행해야 한다. 현재 제안의 목적은 근거가 있는 경로 동등성에 따라 입력별 관측을 구분해 보존하는 것이며, 미실행을 실행으로 바꾸는 것이 아니다.

위 조건을 유지하면 추가 범위 확대나 렌더러 15개 재실행을 필수로 권고할 정적 근거는 발견하지 못했다.
