# input4 → input5 Android 검증 범위 검토

검토일: 2026-11-15 UTC. 앱·검사 소스, 장치, 빌드, 네트워크를 변경하거나 실행하지 않고 기존 원문과 소스를 읽었다. 이 문서만 작성했다. **실제 source-freeze5와 4/3→5 delta를 확인한 범위 판단**이다. input5 빌드/실행 성공을 뜻하지 않는다.

## 권고 범위

실제 source-freeze5의 제품 변경은 `RunmoniDestinations.kt` Home의 **largeText 분기 아바타 폭 56→48dp, Home outer gap 24→16dp** 두 곳뿐이다. 별도로 **NativeVectorMapOnlineUiTest의 Map/Frame callback 관측 수정 및 진단**이 있다. 이를 포함한 정확한 delta를 확인했고 다음처럼 진행할 수 있다.

- 새 input5: default의 `screens` **11개**, native **15개**, 변경된 native-online **1개**를 새로 실행한다. native-large-text 12개는 반드시 그룹 전체를 새로 실행한다. 이미 통과한 native form2/share1도 새 native15에 포함해 대체하면 집계가 단순하다.
- input4 보존: default의 나머지 **25개**(closet-navigation2, widget9, profile-local8, sharing-preferences6)와 native-sdk **4개**, Firebase SDK **3개**는 input4의 실제 전체 통과 결과로 보존할 수 있다. Firebase는 아래 별도 외부 프록시 복원-only 증거도 반드시 함께 참조한다.
- landscape **1개**는 아래의 원래 JUnit PASS/parent=false/별도 복원-only 성공을 함께 명시해 보존할 근거가 있다. 소비자가 phase 전체 PASS만 허용한다면 이 예외를 억지로 통과시키지 말고 제외하거나 새로 실행한다.
- map-online1/2 및 Firebase SDK1은 completed=true/parent=false/actual0이며 whole-group PASS가 없다. 부분 관측이나 원문의 점 표기를 통과 수로 계산하지 않는다.
- input3 보존 77개의 기존 판단은 실제 3→5 delta에서도 유지할 수 있다. 새 online test source는 이 77개에서 실행하지 않는다. concrete test hash 및 [이전 검토](INPUT3_TO_INPUT4_VALIDATION_SCOPE_REVIEW.md)의 fake UI/실제 VM/지도/자산 경계를 다시 확인했다.

새 native15 전체를 실행하는 권고안에서는 input4 native1의 이미 통과한 3개를 최종 고유 검사 수에 다시 더하지 않는다. native1은 실패 원문으로 보존한다. 실제 최종 결과는 입력별로 표시하며, `동일 APK 137개 통과`라고 쓰지 않는다.

## 실제 input4 증거

확인한 완료 phase의 before/복원 뒤 6 source maps가 source-freeze4와 일치하며 실제 설치 pair도 binary-build4에 일치한다. 채택 후보 통과 그룹의 raw SHA 및 `OK (N tests)`를 직접 확인했고 실패 그룹에 whole OK가 없다는 점도 구분했다. 기본 source-freeze4 SHA는 `26ebe86bcf861395ee8e267976db51e8b5c5a4cec3180d53c0ce726d0aead629`, binary-build4 JSON SHA는 `ec5003bacb11b4bf7929a783f351723101307e7ba9f1368c496678d0528e183c`다.

실제 input4 app APK SHA는 `2958bbdd2fd152855271662d28c36d4651520eb58a85e7abbb45058d70ca4bc7`, test APK SHA는 `8ad1013979f1bbd37bd451c4c0de8758f27f5ffd946c7b0365477dc08fad0c3d`다. JSON 해시와 APK 해시를 혼동하지 않는다.

| proof | 실제 결과 | input5 판단 |
| --- | --- | --- |
| android-review2.json | completed/pass true, 새 default36 전체 PASS | screens11은 Home 변경 때문에 새 실행. 나머지25 보존 가능 |
| native1.json | completed true/parent false. form2 PASS, large-text12 그룹 실패, share-font1 1 PASS | 실패12의 부분 통과는 0개로 취급. 새 native15 전체 권고 |
| landscape1.json | 실제 JUnit1 PASS이나 rotation policy 복원 실패 때문에 parent false | 별도 recovery와 연결한 제한적 제품1 보존 또는 새 실행 |
| landscape1-rotation-recovery.json | completed/pass true, restorationOnly true, productMethodsCountedByRecovery=0 | 환경 복원 증거만. 새 제품 검사 수 0 |
| map-sdk1.json | completed/pass true, native map SDK4 전체 PASS | Home 경로가 아니므로 보존 가능 |
| map-online1.json | 최종 completed=true/parent=false/actual0, 복원 및 source maps 일치, native-online-map raw에 whole OK 없음 | 완료 실패·채택0. 별도 새 성공 근거 필요 |
| map-online2.json | completed=true/parent=false/actual0, 실제 render/pan 부분 관측은 있으나 전체 검사 실패 | 채택0. 변경된 online1 새 실행 필요 |
| firebase-sdk1.json | completed=true/parent=false/actual0 | 실패 원문만 보존·채택0 |
| firebase-sdk2.json | completed/pass true, demo profile SDK3 전체 PASS, 자체 scoped 복원/source maps 일치 | 별도 외부 proxy 복원-only와 연결해 보존 가능 |
| firebase-sdk2-network-proxy-restoration-only.json | completed/pass true, 원래 5개 proxy 값 정확 복원, productMethodsCounted=0 | 외부 scope 복원 증거만. 새 제품 검사 수0 |

완료 자료의 SHA는 다음과 같다. map-online1은 완료 뒤 해시만 적는다.

| 파일 | SHA256 |
| --- | --- |
| android-review2.json | b994f925bafa6cacdbc8fe945b9002e57caf9026783dfd0a82fa388b4cfea53c |
| native1.json | ccb6599ff4de465f7d94b90ae1c3b6dcc8ac8eb08720a1cbd32214c3fd9b067a |
| landscape1.json | 3363b01907fbac41eafffe79f4cd3e8c9f561c6fd7fbb2d8618a4c9ff16c416d |
| landscape1-rotation-recovery.json | 16e6b1124903e254cf31f91a5c8afd03aa6a741990472bbf0e3bf47da5b0ef3f |
| map-sdk1.json | 815a0bd97671057e530b9fdcf8540fc01d7ec020cd83ab5ec36131532639ae5a |
| map-online1.json | bf3f7404526b820e84c5ef1c74babdbbecf89edf7a370401076dca7341092e65 |
| map-online2.json | ab741c2aaf56644bbdcedc7ee77aa15b838c23368452402d41066524843d5b43 |
| firebase-sdk1.json | 6d0ecb545b7c3ed5216205d893e2cee62add67ef2fde2af84ab6de6e8370033e |
| firebase-sdk2.json | 88bdeb9333f24ced3ef75aecee078e3cdaf55aae8555079a092e5922b08f2a28 |
| firebase-sdk2-network-proxy-restoration-only.json | 52dbe78340ae1ce78e798293f2118afc0eaa48f55512ca8336011bd9b153db77 |

| input4 보존 후보 그룹 | whole PASS 수 | raw SHA256 |
| --- | ---: | --- |
| closet-navigation | 2 | 7cc7f9054df3becedb9559c5d11b2a308aca6c2d3063f4f9317f1c66638c0ee5 |
| widget-measurement | 9 | e7324ba079bdf0941385d6a7fcfba27cf156d4097f6ea052633a13db701aebd3 |
| profile-local | 8 | 737c179650b956a37e7b6333c1a27b0581dfa594d5f6737bc9a35d5532a188c3 |
| sharing-preferences | 6 | 60b9026f5294cbfa532441a87738a7b3b385901f5704d61ba88a130c0ff7516b |
| native-map-sdk | 4 | 25ab761186e0f4b14b8af8b39405ff5c109dfd3148f7f4dd84632589ed97acf1 |
| community-return-scroll（복원 별도） | 1 | 4ed5bf92a6dd27b22cdbfb1e033ce5b445eca8a20e9d7e1448fab86d10f22d2b |
| firebase-demo-profile（외부 proxy 복원 별도） | 3 | 7d94820835f650f30b8adb0d186284cf35bee35767a25aa3e920a56ea1bc1706 |

## Home 실패와 새로 필요한 실행

native1의 `HierarchyClosetLargeTextUxTest.genderAndScrollableCategoriesAllowOneTapFreeSelectionWithoutLosingInventory`는 **실제 OS320dp/font2인 ComponentActivity fixture**에서 Home CTA/nav 겹침을 검출했다. raw 및 WORK geometry는 CTA top330.66666/bottom404.66666dp, navigation top400dp이므로 4.66666dp 겹친다. 이는 실제 측정된 레이아웃 실패이며, 단순 timeout/observer 문제로 분류하면 안 된다. 실제 MainActivity 실행이나 휴대폰에서의 측정으로 확대하지 않는다.

실제 input5 Home 변경은 장식 아바타와 구간 간격을 줄이는 방식이며 기능·기록·버튼 최소 높이·텍스트 크기를 바꾸지 않는다. 그렇더라도 **계산상 공간 절감만으로 해결을 선언할 수 없다.** 같은 assertion/동일 OS font2 조건에서 새 native-large-text 12개 전체가 통과해야 한다. assertion을 완화하거나 `performScrollTo`를 추가해 가림을 숨기는 수정은 없다.

`screens`에는 큰 글씨 Home journey 및 HomeWeatherLayoutTest가 있어 변경된 Home과 경계를 직접 다룬다. 새11개 전체를 실행하고 최종 Home 사진도 input5에서 새로 만든다. normal font<1.3 경로는 유지해야 하지만 현재 screens11에 이미 포함되므로 그 회귀도 확인할 수 있다. input4 Home 사진은 원래 입력의 자료로만 남긴다.

나머지 default25는 직접 옷장·위젯·VM 프로필·공유 preference 경로이고 Home의 두 장식/간격 값이 assertion 대상이 아니다. map SDK4는 직접 MapLibre fixture다. landscape1의 확인 대상은 크루→파티→채팅→Back의 동일 목록/scroll이며 Home으로 돌아가지 않는다. Home 한 파일의 전체 해시가 바뀌어도 이 보존 경로의 제품 구현은 그대로라는 조건이다.

## landscape 복원 증거의 정확한 한계

landscape1은 JUnit raw `OK (1 test)`이며 실제 제품 method1은 성공했다. 그러나 `fixedToUserRotation.beforePolicy='default'`, `afterPolicy='disabled'`, effective false→false로 **효과값은 같고 저장 정책은 달라** `restoredExactly=false`였다. 원문 parent=false를 유지해야 한다.

원래 일반 helper의 `controlledSettingsPermissionsPowerRestoredExactly=true`는 settings allowlist·wm size/density·앱 권한·관측 Awake/Asleep에 대한 값이다. 이 scalar 하나는 별도로 기록한 fixed-to-user rotation 저장 정책 복원 실패를 덮어쓰지 않는다. `settingsPermissionsPowerRestoredExactly` 별칭도 전체 OS 정확 복원 의미가 아니다.

별도 recovery는 priorAttempt의 정확한 landscape1 SHA를 참조하고, 새 앱/test APK 변경·사용자 데이터 reset·네트워크 변경이 없음을 기록했다. target effective=false/persistedPolicy=default, 원래 preferences/profile·controlled environment·DB 정확 일치, onlyOwnedAttributeChanged/otherFileBytesPreserved/displayFileOwnerModePreserved=true를 확인했다. framework 재시작이 기록되어 있으며 Launcher 접근성 복구를 확인했다고는 쓰지 않았다. allOsBytesExactlyRestoredClaimed=false, productMethodsCountedByRecovery=0이다.

따라서 `landscape 제품1 JUnit 성공 → 해당 시도의 정책 복원 실패 → 후속 복원-only에서 원래 범위 복원 완료`로 분리 보존할 수 있다. 원래 시도 자체를 전체 PASS로 수정하거나 recovery를 새로운 JUnit 실행으로 세면 안 된다. 소비자가 whole-phase PASS를 필수로 요구한다면 새 landscape 실행이 필요하다.

## input5 채택 전 6 source maps 가드

실제 `source-freeze5.json` SHA는 `ce53900b3455b5f7475b8d20825ae12bcdd92f94ab87cd09648fbff6f819bc51`이다. 현재 파일들의 해시가 이 6개 맵에 모두 맞는 것도 확인했다. 4→5 맵의 항목 수는 동일하다.

| 맵 | 파일 수 | 실제 4→5 차이 |
| --- | ---: | --- |
| productSources | 114 | RunmoniDestinations.kt 한 파일만 다름 |
| testSources | 99 | NativeVectorMapOnlineUiTest.kt 한 파일만 다름 |
| resources | 34 | 항목·해시 전부 동일 |
| rendererAssets | 83 | 항목·해시 전부 동일 |
| approvedSourceCopies | 1 | 항목·해시 전부 동일 |
| configurationSources | 11 | 항목·해시 전부 동일 |

Home의 해시는 `6620d599e9b97d5b34681d18f6021a4b9d856d9b26b3ed434acc5d3adf93da3c` → `5f47a1cfeadf6a96eb670807c174420f90bdf176f80f7ff1b69ade3e0928d416`다. 현재 파일에서 두 치환만 메모리 안에서 되돌린 SHA가 freeze4와 정확히 일치했다. 따라서 일반 font 경로·CTA 조작 영역·다른 목적지·키/UID/공유 gate/지도 구현에 추가 제품 차이는 없다.

online 검사 해시는 `cdc96c7e94585c045ec33dda4e926df8973bd1d38a6742975b2a13f6a1d41489` → `7ccb4c58469ae198ffbcf5418599dd381c8156b73419f1585c1ac95816a04460`다. `native-online-frame-observation-input5-v2.patch`의 9개 hunk를 메모리 안에서 역적용한 SHA가 freeze4와 정확히 일치했다. 다른 검사·공통 기준은 바뀌지 않았다.

이 online 변경은 기존에 Frame 증가를 검사하면서 Map 완료 listener를 세던 관측을 분리한다. Map 완료와 Frame 완료를 각 listener로 센다. 초기 지도 성공 조건에는 기존 Map 완료>0과 **추가 Frame 완료>0**을 모두 요구한다. pinch 후 zoom>afterPan+0.10, idle/gesture 증가 및 새 완전 Frame 증가 조건, shared120초 deadline은 유지한다. 입력 직전/직후 viewport·camera·counters와 실패 최종 camera를 기록하며 retry나 새 budget을 만들지 않는다. 이는 검사 관측 의미를 바꾸므로 새 online1 실행이 필수다. 아직 실제 새 성공으로 해석하지 않는다.

3→5도 직접 비교했다. 변경 경로는 이전 검토의 product7/test5와 동일하며, 4→5의 추가 변경은 이미 달랐던 Home와 online 검사 파일의 내용에 포함된다. 자산/원본/config 4개 맵은 3→5에서도 전부 동일하다. 보존 77개 concrete test 파일은 3→5 동일 해시이고, 변경된 online 클래스는 그 선택 목록에 없다. 기존 진단 변경은 실패 경로만 바꾸며 retained 정상 assertion은 유지된다. 따라서 이전 77개 경로 동등성 판단을 input5에 대해서도 유지할 수 있다.

새 binary-build5는 이 freeze5의 SHA와 실제 새 APK pair를 참조해야 한다. 설치 guard와 실행 전/후/복원 뒤 map 일치를 새 근거로 확인한다. 이 문서에서는 build5 결과나 설치를 실행/확인하지 않았다. helper/복원 수정도 executed source SHA 및 실제 scope를 새 proof에 기록해야 한다. 이후 MapLibre/공유/다른 Home 제품이나 의존성 변경이 추가되면 이 판단을 다시 검토한다.

## Firebase SDK 외부 proxy 복원과 session2 종료

firebase-sdk2의 자체 phase는 실제 JUnit3 PASS이며 profile/account/preferences 및 helper allowlist 복원, before/after-restoration6맵 일치가 기록되어 있다. 그러나 이 helper 밖의 local-network wrapper는 exclusion-list의 빈 인자를 shell quote 없이 복원하다 중단되었다. 자체 phase의 복원 true만으로 외부 proxy scope가 끝났다고 주장할 수 없다.

`firebase-sdk2-network-proxy-restoration-only.json`은 원래 wrapper SHA `c7c9f1747dd860fea9ccdc8b9ee7c15fa3f22b0216d119cef8f19d2bdc8cbdab`와 정확한 product phase SHA `88bdeb9333f24ced3ef75aecee078e3cdaf55aae8555079a092e5922b08f2a28`를 연결한다. 원래 5개 proxy 키 값을 shlex.join으로 복원했고 allFiveOriginalProxySettingsRestoredExactly=true, source/TLS 변경false, restorationOnly=true/productMethodsCounted=0이다. 따라서 **SDK 제품3 성공·외부 wrapper 미완료·후속 원래5키 복원 완료**를 분리하여 보존한다. Firebase SDK1 실패는 그대로 남긴다. demo SDK 결과를 운영 Rules 게시/실제 Google/사용자 프로필 연결 성공으로 바꾸지 않는다.

`session2-cleanup.json` SHA `a5abc1091e286b1d5b63ad591c962a655bdad714b4b2b0ffddfc438e408f18a0`는 소유 PID34317의 command/startTicks 일치 후 SIGTERM, exit 관측, 모든 Qemu가 absent 또는 zombie임을 기록하고 위 원문/별도 복원 자료를 SHA로 연결한다. 이는 세션 종료 증거이며 새 제품 검사나 앞선 실패의 성공 전환이 아니다.

## 집계 방법

권고안의 보존 후보는 input3 **77** + input4 **33**(default 비Home25+native SDK4+Firebase SDK3+별도 복원 예외 landscape1)이다. 새 input5는 screens11/native15/online1의 **27**개다. helper를 import/실행하지 않고 AST spec과 현재 Kotlin @Test 선언만 펼쳐 **77/33/27의 method ID 교집합0·고유 합137**을 확인했다. 이는 계획 방법 집합이며 새27개가 실제 통과했다는 뜻이 아니다. 새 native15를 채택할 때 input4 native form2/share1은 추가로 세지 않는다. actual whole PASS/복원 근거가 갖춰지기 전에는 이 산술을 최종 통과 수로 발표하지 않는다.

휴대폰·Google 실제 로그인·운영 presence write·실제 Main의 온라인 지도·공개 서명 APK 런타임·장기 가용성은 별도다. 기존 SnapshotStateObserver 원인은 여전히 미확정이며 이번 Home 수정으로 해결되었다고 주장하지 않는다.
