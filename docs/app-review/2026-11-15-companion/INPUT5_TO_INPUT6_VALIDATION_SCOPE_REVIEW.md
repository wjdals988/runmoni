# input5 → input6 Android 검증 범위 검토

검토일: 2026-11-15 UTC. 이 문서만 작성했다. 앱/검사 소스·장치·빌드·네트워크·설치·Git은 변경하거나 실행하지 않았다. **실제 source-freeze6 SHA와 3/4/5→6의 정확한 source-map 차이 및 retained concrete test 해시를 확인했다.** 아래 보존 판단은 이 source6에 대한 정적 판단이며 build6/새 Android 실행 성공을 뜻하지 않는다.

## 현재 권고

- input3 전체 통과 그룹 **77개**: [3→4 검토](INPUT3_TO_INPUT4_VALIDATION_SCOPE_REVIEW.md) 및 [실제3/4→5 검토](INPUT4_TO_INPUT5_VALIDATION_SCOPE_REVIEW.md)의 경로 동등성을 보존한다. 새 native marker 구현을 이 77개가 실행하지 않는다는 조건은 유지된다.
- input4 **29개**: default 비Home25 + Firebase demo SDK3 + landscape1의 명시적 후속 복원 예외를 보존한다. 이전에 채택했던 input4 native SDK4는 새6 SDK4로 대체한다.
- input5 **26개**: 실제 screens11과 native15를 보존한다. 아래에서 모든 fixture의 offline injection 경로를 확인했다.
- input6 **5개**: native SDK4와 새 native-online1을 새로 실행한다. 새 online1은 실제 투영 위치의 MiniMi 픽셀 가시성까지 확인하고, Root가 원본 사진을 직접 확인한 뒤에만 최종 지도 성공으로 채택한다.

방법 집합 계획은 **77 + 29 + 26 + 5 =137개**다. 실제 source6에서 helper spec을 실행 없이 AST로 읽고 Kotlin @Test 선언으로 펼쳤을 때 네 묶음 사이 중복은 0개였다. 이는 입력별 계획/보존 판단이며, 새6 5개의 실행 성공이나 동일 APK137개 통과를 뜻하지 않는다.

## input5 screens11/native15의 실제 증거

기준 경로는 `docs/app-review/2026-11-15-companion/`이다. source-freeze5 SHA는 `ce53900b3455b5f7475b8d20825ae12bcdd92f94ab87cd09648fbff6f819bc51`, binary-build5 JSON SHA는 `03ff698cbf11d9de77e9ed8cbef13a9404bfa06cb2723b72287cc65efc550da2`다. app APK SHA는 `ed9548e0231f5c208fea339ff46fdef0fa82018d861eb1c3cf989f3fd45e1482`, test APK SHA는 `6382644d7d62cac58059b0485adf28b5219f49515bc83432ed9d30bb3557ca06`다.

`android-review3.json` SHA `1a7cc8f55ba036bec09cd30b32043b033ab45244677e6ff8750883ce717fbc78`은 completed/pass true, actual11이다. `native2.json` SHA `eb64d85ff69b297538c1adf49ec67890162f2627a833ec17cbf31d103cbcf473`은 completed/pass true, actual15이다. 두 자료 모두 위 source5/binary5를 참조하고 실제 설치 pair가 맞는다. before/after/복원 뒤 6 source maps, 원래 profile/account/preferences 및 controlled settings/permissions/power 복원, executed source/binary 불변이 true이고 restorationCommandFailures는 빈 배열이다.

각 raw SHA 및 `OK (N tests)`를 직접 확인했다.

| 그룹 | actual | raw SHA256 |
| --- | ---: | --- |
| android-review3 screens | 11 | 4a8901d879455ca2b679eb2812c74c0fbbb3c12d79c323944574ccddf802570c |
| native2 native-form-pages | 2 | bbc6373333bfc28bdba6a4ac1c5feee7d8c21121021473c27a7d3963da24158a |
| native2 native-large-text | 12 | 103b6466b711bd1f0e12647ecf2932e27bda982674bd922c46092748848a42ba |
| native2 native-share-font1 | 1 | 693c530377a958bbfc950963db7fd54eee4614f2ca3e79a10160ee538abb40e4 |

이는 OS320dp/font2·IME 등을 사용하는 ComponentActivity fixture 및 Compose-target 관측이다. 실제 MainActivity/휴대폰/공개 서명 APK 실행으로 표시하지 않는다. input4의 Home CTA4.66666dp 겹침 실패는 원문으로 보존하고, 해결 후 새 input5 native15의 결과만 채택한다. input3 SnapshotStateObserver 원인 미확정도 그대로 남긴다.

## 26개 전체가 native map 변경에서 분리되는 이유

`RunmoniDesignTestBase`의 compose rule은 `OfflineMapComposeRule`이다. 이 rule의 `setContent`는 항상 `CompositionLocalProvider(LocalMapTileSource provides offlineTiles)`로 content를 감싼다. offlineTiles는 `MapTileSource { MapTileResult() }`인 nonnull inert source다. unchanged `MapPane`은 `LocalMapTileSource.current != null`이면 RouteTileMap을 호출한 뒤 즉시 return하므로 MapLibreMapPane을 생성하지 않는다. 지도 자체를 열지 않는 fixture도 동일한 provider를 갖는다.

현재 selected class의 상속과 setContent를 확인했다. 아래 파일에는 provider를 null로 덮는 경로나 MapLibreMapPane을 직접 호출하는 코드가 없다. RunRecordScreenNativeUiTest는 Base 외에 explicit inert provider도 추가한다.

| 그룹 | concrete class와 선택한 방법 수 |
| --- | --- |
| screens11 | StudioRefreshUiTest3 / RunmoniDesignTest2 / AppEntryUiTest4 / HomeWeatherLayoutTest2 |
| native-form2 | ModalDismissNativeUiTest2 |
| native-large12 | RunmoniDialogDesignTest4 / AppEntryNativeUiTest1 / AppInformationNativeTest2 / HierarchyClosetLargeTextUxTest1 / ClosetNavigationNativeUiTest1 / RunningKitColorsNativeUiTest1 / RunRecordScreenNativeUiTest1 / RunShareImeUiTest의 font2/IME 방법1 |
| native-share-font1 | RunShareImeUiTest의 font1 caption/IME 방법1 |

따라서 source6가 native pane 구현과 그 native-online 검사만 바꾸면 이 26개가 관측한 Home/글씨/대화상자/옷장/IME/기록 경로의 구현은 달라지지 않는다. 이 26개를 보존하는 것은 새 native marker 가시성을 검증했다고 주장하는 근거가 아니다. 실제 Main의 provider 없는 경로는 새 지도 검증 뒤 별도로 확인해야 한다.

## 이전3·4 입력의 보존 범위

input3 77개는 community11/account14/consumer1/colors2/record-share11/map-modal17/sharePNG6/avatar15의 whole passed groups다. 실제 VM을 사용하지 않는 fake-actions UI 경계, explicit/inherited inert raster source, direct bitmap/옷장 renderer 경로가 앞선 두 검토에 기록되어 있다. renderer83 자산·승인 원본·MiniMi/옷장 구현을 native pane이 바꾸지 않으면 그 판단을 유지할 수 있다. Home/기록 fixture를 native marker 성공으로 바꾸어 설명하지 않는다.

input4 default25는 closet-navigation2/widget9/profile-local8/sharing-preferences6이다. 새 native marker 경로를 사용하지 않는다. Firebase demo SDK3는 프로필/닉네임 repository 검사이며 native map/UI를 생성하지 않는다. 이를 보존하려면 실제 source6에서 VM/data/Rules/config가 그대로여야 한다.

landscape1은 제품 JUnit1 PASS지만 원래 parent=false였다. 원래 fixed-rotation default→disabled 복원 실패와 후속 `landscape1-rotation-recovery.json`의 정확 policy/DB/preferences/controlled환경 복원 완료(product0)를 분리해서 참조해야 한다. 이를 whole-phase PASS로 재작성하지 않는다. Firebase SDK3의 자체 phase PASS 역시 외부 proxy wrapper 중단과 `firebase-sdk2-network-proxy-restoration-only.json`의 원래5키 정확 복원(product0)을 함께 참조한다. 두 예외의 정확한 SHA 연결과 범위는 [4→5 검토](INPUT4_TO_INPUT5_VALIDATION_SCOPE_REVIEW.md)에 있다.

SDK4는 input4에서 실제 전체 통과했지만 이번에 **검사 대상 MapLibreMapPane 자체가 바뀌므로** input6에서 새로 실행해 대체한다. input4 SDK4를 최종 고유 방법 수에 추가로 더하지 않는다.

## online3/4의 JUnit 성공과 시각적 거절

map-online3/4는 동일 binary5/source5에서 각각 JUnit1 전체 PASS, 자체 source/복원 guard 통과였다. 원문은 다음 해시로 보존한다.

| 파일 | SHA256 |
| --- | --- |
| map-online3.json | d4cc0185aadd1c7281c05188c852076d21cf23afec8667bbcbecb5785ef1062b |
| map-online4.json | 0eec3de45503b5abc393a8722db3d085750acb442ec30de42f9346a0a1db70ec |
| map-online3-root-visual-review.json | fc20f180bc532cdeafe8c5e8b906d445fb9301cdfdac7570038a4e0ff233edb5 |
| input5-graphics-backend-visual-comparison.json | 8ad5013c9c5c83eab63c3b440d7bad3dfdaf7e0ce87722057260c6ff6ee44583 |

Root의 원본 사진 검토 기록에 따르면 online3은 두 사진 모두 기대 위치에서 MiniMi를 식별하지 못했고, 사각형/한글 지도 이름 부재도 관측됐다. 같은 APK를 swangle에서 실행한 online4는 초기 산타 MiniMi와 한글 이름은 보였으나 costume4로 바꾼 뒤 사진에서 MiniMi가 식별되지 않았다. 양쪽 모두 rootVisualAccepted=false다.

이것은 두 번의 서로 다른 제품 방법 성공이 아니고 같은 online 방법의 반복이다. 또한 GPU만의 문제, alpha/fade만의 문제, async/occlusion의 지속 시간, 버전 없는 이미지 교체만의 단일 원인을 확정한 근거도 아니다. 캐시/외부 응답 조건이 완전히 같았다고 주장하지 않는다. 따라서 online3/4의 JUnit PASS를 이번 최종 지도 통과로 채택하지 않고, 새 제품 변경 후 actual projected-pixel visibility 검사와 Root의 원본 사진 확인으로 대체한다. 이전 online1/2·SDK1 실패도 그대로 보존한다.

## 실제 source6의 6개 맵

`source-freeze6.json` 실제 SHA는 `0b9ee924c8bf814191f4565e559e70f9477d7b9bbd7c91a5176001d3f3634438`로 Root가 전달한 값과 맞다. 현재 파일 각각의 SHA도 6개 맵에 전부 일치한다. 5→6의 정확한 변경은 다음 두 경로뿐이다.

| 경로 | source5 SHA256 | source6 SHA256 |
| --- | --- | --- |
| app/src/main/java/com/runmoni/app/ui/MapLibreMapPane.kt | 31d564198fbd87f33b251d8e37895cecec71f9936fff4de1f9fb449aaf655b40 | b5ea1f0eafa5fbda8df770ef49f6c0d15be3978966ebc4f1054c2eab7d1c42fd |
| app/src/androidTest/java/com/runmoni/app/ui/NativeVectorMapOnlineUiTest.kt | 7ccb4c58469ae198ffbcf5418599dd381c8156b73419f1585c1ac95816a04460 | 4ff6bcab1cb4d00b1a867c900175d046c8837f9607419cbb30fccc01d5967738 |

| 맵 | source5→6 수 | 실제 비교 결과 |
| --- | ---: | --- |
| productSources | 114→114 | native pane1경로만 변경; Home/VM/data/renderer/MapPane/RouteTileMap 동일 |
| testSources | 99→99 | native-online1경로만 변경; retained concrete tests/OfflineMapComposeRule/Base 동일 |
| resources | 34→34 | 항목·해시 동일 |
| rendererAssets | 83→83 | 항목·해시 동일 |
| approvedSourceCopies | 1→1 | 항목·해시 동일 |
| configurationSources | 11→11 | 의존성/빌드설정 포함 항목·해시 동일 |

실제 3→6/4→6도 직접 비교했다.

- 3→6: product113→114의 변경 경로7개는 앞선3→4 검토의 VM/공유4경로+새 gateway/native pane/Home과 같다. test97→99의 변경 경로5개도 앞선3→4 검토와 같으며 native-online 새 파일의 최종 내용만 갱신됐다. resources/rendererAssets/approved/config의 4개 맵은 3→6 전부 동일하다. 보존77개가 들어 있는 concrete test21개 파일은 3→6 같은 해시다.
- 4→6: product114개 중 Home/native pane2경로, test99개 중 native-online1경로만 다르다. 4개 나머지 맵은 동일하다. 보존29개가 들어 있는 concrete test9개 파일은 4→6 같은 해시다. VM/data는 source4 그대로이고 Home 차이는 이전4→5 검토의 두 largeText 장식/간격 값뿐이다.
- 5→6: 위 표의 두 경로만 다르다. 보존26개가 들어 있는 concrete test11개 파일은 5→6 같은 해시다.

세 보존 cohort의 파일 수는 cohort별 수이며 같은 파일에 서로 다른 class가 있을 수 있어 합계41개 고유 파일이라고 주장하지 않는다. MapPane/RouteTileMap/MiniMi/AvatarClosetScreen 및 OfflineMapComposeRule는 3/4/5→6 전부 같은 해시다. Base가 있는 RunmoniDesignTest.kt는 4/5→6 같은 해시이고, 3→6의 차이는 앞선 검토에 분리한 실패-only 진단 변경이다. 따라서 실제 source6에서도 위 inert 주입·renderer·fake UI/실제 VM 분리 경로가 유지된다.

native pane 변경은 versioned avatar image key, 준비된 마지막 image registry, 작은 runner FeatureCollection의 동기 갱신 후 이전 image prune 및3D FillExtrusion 가림 조정이다. 독립 검토 `NATIVE_MARKER_INPUT6_PEER_REVIEW.md` SHA `f2475b69a388804c491402700f7c609b4f9943f541cd56aabd2b802bbf63e41e`는 최초 지적 후 setGeoJsonSync 최종 변경, 캐시 SDK public API·Main 스레드 조건을 분리해 기록한다. 이 정적 결론을 GPU 업로드 완료/모든 프레임 무공백/입력5의 유일한 원인 확정으로 확대하지 않는다.

online 검사는 실제 TextureView 픽셀과 투영 위치·native image/source 참조·bitmap roundtrip을 함께 검사하고 초기/교체 뒤 body/clothing 픽셀 조건을 새로 추가한다. pixel ratio/bearing/tilt 등 지원 조건과 shared120초 budget이 코드에 명시되어 있다. 이는 기존 JUnit1의 성공을 그대로 이전할 수 없는 새 관측 계약이므로 input6에서 새 online1이 필요하다. 모든 기기/뷰포트/드라이버에 동일한 pixel oracle가 성립한다고 주장하지 않는다.

위 실제 source6에서는 retained3 77 + retained4 29 + retained5 26의 source 경로 동등성을 채택할 수 있다. 이후 예정 외 product/test/asset/dependency 변경이 있으면 관련 retained 그룹을 다시 평가한다.

새 binary6/설치 pair는 실제 source6에 결합되어야 한다. 새 SDK4/online1의 raw whole PASS·실제 개수·before/after/복원 뒤6맵·원래데이터/controlled환경 복원을 확인하고, online1 원본 projected-pixel 증거와 Root 시각 승인도 별도로 남긴다. Main은 그 뒤의 새 별도 실행이며 이 fixture 결과로 대신하지 않는다.

권고 범위의 137개 고유 방법은 서로 다른 입력에서의 근거를 합친 추적 목록이다. source6 실제 동결과 새5개 통과 전에는 최종 통과 수로 발표하지 않는다. 실기기·실제 GPS·실제 Google·운영 writes·공개 서명 APK runtime·장기 지도 가용성은 여전히 별도다.
