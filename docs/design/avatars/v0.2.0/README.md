# Runmoni avatar 0.2.0 — native app integration

2026-10-25. 사용자 승인 RPG 비율을 유지하면서 생성형 시안을 잘라 붙이는 미리보기에서 실제 앱용 공통 픽셀 리그로 전환했다. 앱 설치 버전은 다른 디자인/Firebase 작업과 같은 `0.1.3-dev` / versionCode `4`를 유지한다. 아바타 리소스 버전 `0.2.0`과 앱 버전을 혼동하지 않는다.

## 변경

- `AvatarRasterizer.java`: Android/AWT에 의존하지 않는 원본 픽셀 렌더러. 96×112px, 8프레임 × 90ms = 720ms 반복. 얼굴·목·몸 좌표와 관절을 공유하고 헤어/옷/장비만 그 위에 올린다. 포니테일도 얼굴을 교체하지 않는다.
- Compose `MiniMi`와 지도 마커가 동일 렌더러를 사용한다. 픽셀 확대 필터는 nearest-neighbor, 프레임 이미지는 보이는 캐릭터별 최대 8개만 보관한다. 백그라운드에서는 애니메이션을 멈추고 Android 애니메이션 비활성 설정을 존중한다.
- 옷장 표시 탭 9개 / 내부 선택 13슬롯. 모자·이어폰·선글라스·워치·벨트·조끼 동시 착용. 상·하의 각 6종, 양말·표정·헤어·장비의 독립 선택. 실제 규칙과 공식 벤치마킹은 [BENCHMARK.md](BENCHMARK.md).
- 브라탑·크롭티·바이커쇼츠·스플릿 쇼츠·니삭스 추가. 성별 잠금 없음.
- 세트는 산타·스포츠 원피스 수영복·스포츠 비키니·곰인형·오리지널 골든 마스코트 5종. 특정 IP 캐릭터의 앱 사용권은 확인하지 않았고 이번 원화에 사용하지 않았다.
- 코스튬이 덮는 부위의 일반 옷 선택 시 코스튬 해제. 기본 옷 데이터는 보존한다. 수영복에는 일반 양말·신발을 함께 고를 수 있다.
- 첫 네이티브 스크린샷에서 큰 캐릭터가 목록과 함께 스크롤되어 사라지는 문제를 발견해, 캐릭터/재생 버튼은 고정하고 목록만 스크롤하도록 수정했다. 수정 후 UI 20개를 다시 통과했다.

## 데이터와 구매 호환

기존 `accessory:1`은 `headwear:3`, `accessory:2`는 `headwear:1`, `accessory:3`은 `ear:1`로 호환한다. 과거 보유 항목과 마일리지를 유지하고 이전 구매품에 다시 과금하지 않는다. 새 기본 0번 항목만 기본 보유에 추가한다. 로컬 지갑 JSON과 파일럿 프로필 JSON에 신규 필드를 보존한다.

백엔드는 새 슬롯·카탈로그·소유권 검증과 부분 PATCH 병합을 지원한다. 기존 클라이언트가 보내지 않은 새 장비는 유지하며, 덮인 일반 부위를 변경하면 세트를 해제한다. **새 앱은 새 백엔드와 함께 사용해야 한다.** 이전 백엔드는 새 JSON 필드를 금지해 422를 반환할 수 있다. 이번 작업은 원격 서버 게시·기존 서버 호환 검증을 포함하지 않는다.

## 최초 통합 확인 결과

아래 표는 `df2a856` 커밋 전후 최초 아바타 통합의 기준 결과다. 이후 다른 세션의 디자인·앱 정보와 합친 야간 재검증은 [OVERNIGHT_AUDIT.md](OVERNIGHT_AUDIT.md)에 별도 기록한다. 20개 UI 검사에서 실제 시간 기반 재생과 native320dp/글씨200%까지 모두 확인한 것으로 확대 해석하지 않는다.

| 확인 | 결과와 범위 |
|---|---|
| 최종 빌드 | `work/avatar-sticky-preview-build.log`: 오프라인, 작업자 1개, assembleDebug / testDebugUnitTest / lintDebug / assembleDebugAndroidTest 성공, 1분 41초 |
| JVM | 현재 통합 트리 26/26. 이번 AvatarArtTest 6개 포함. 다른 채팅의 AppReleaseTest 5개가 합산된 수치임 |
| 목 연결 | 4헤어 × 6상의 × 6코스튬 × 8프레임 = 1,152개에서 목의 불투명 연결 및 얼굴↔몸 픽셀 경로 확인. 모든 조합 전체를 열거한 검증은 아님 |
| 서버 | backend/tests 27/27, 이번 아바타 회귀 4개 포함. 임시 SQLite/TestClient, 실제 원격 검증 아님 |
| 실제 Android UI | 전용 Runmoni_API36 AVD, AvatarIntegrationTest 3개 + 기존 RunmoniUiTest 17개 = 20/20. 고정 미리보기 수정 후 최종 39.003초. `work/avatar-native-final-tests.log` |
| UI 검증 내용 | 8개 캡처의 서로 다른 프레임, 12개 일반 슬롯 선택, 장비 6개 동시 착용, 산타복→일반 상의 전환, 이전 지갑 이전/새 슬롯 재로드/포인트 유지 |
| lint | 오류 0, 경고 28. 기존/다른 디자인 변경을 포함한 현재 트리 결과이며 경고 해결 완료라고 표현하지 않음 |
| 종료 상태 | emulator-5558의 AVD 이름 Runmoni_API36 확인 후 정상 종료. 연결된 기기 0, Gradle daemon 0 확인 |

`native-screenshots/`는 실제 Compose 앱/테스트 Activity에서 캡처한 이미지다. 테스트용 보유 아이템 fixture를 사용했으며 실제 계정 구매·원격 지갑 증거가 아니다. `native-running-8frames.gif`는 Android에서 캡처한 8장으로 조립했고, `costume-running-8frames.gif`는 동일 앱 렌더러의 원화로 조립했다. 원화 내보내기와 GIF 파일의 메타데이터도 8프레임·90ms·무한 반복을 확인했다.

남은 범위: 실기기·야외 가독성·TalkBack 실사용·모든 작은 화면/큰 글씨 행렬·원격 백엔드 게시/호환·서명 릴리스 공개는 미검증이다. Firebase 로그인·백업 검증으로 이번 결과를 확대하지 않는다.

## 야간 통합 재검증

디자인·앱 정보 변경과 합친 수정 debug 빌드가 `work/design-chat-screen-debug-build2-20261025.log`에서 1분57초에 성공했다. 현재 JVM은 28/28(AppReleaseTest가 5개에서 7개로 늘어남), debug lint 오류0/경고24다. 전용 `Runmoni_API36` AVD에서 기본33개(72.071초) + 실제320dp/글씨200% 조건5개(18.998초) = **38/38**을 통과했다. 원본 실패3개가 있던 첫37개 실행과 최종 결과를 구분해 보존했다.

새 `AvatarPlaybackTest` 4개는 `frameOverride` 없이 실제 시간으로 재생한다. 정상0..7프레임 방문과 캡처 변화, 실제 옷장 정지·재개, Lifecycle 중단·재개, 시스템 애니메이션0 설정 시 정지·복원을 확인했다. 그리기 관찰자는 내부 선택 인자로 기본 null이며 일반 화면에 테스트 조작 기능을 노출하지 않는다. 정지 검사는 실제 frame0 그리기 도달과 Compose 스케줄러 정착 후5회 캡처가 같은지 확인한다.

native320dp/글씨200%에서는 9탭·액세서리5하위분류, 고정 미리보기, 세트→일반 상의 해제까지 확인했다. 시스템 키보드에 가리던 채팅은 다른 세션이 Activity 전체 화면으로 수정했고 같은 최종38개에 포함해 통과했다. 기록한 APK는 위의 최초 아바타 통합 debug 보존본이며, 최종 서명 release와 공개 피드는 다른 세션의 후속 검증 대상이다. 38개 fixture는 원격 서버·개인 Google 로그인·야외 GPS·실기기 검증을 포함하지 않는다.

후속 최종 서명본은 코드 e757cb7 기준 `outputs/runmoni-0.1.3-dev.apk`(209,946,174bytes)이며 같은 release 인증서·APK v2·실제 AVD 설치/COLD 실행2,201ms를 확인했다. 일반/큰 글씨/후속 실제 화면16장과 최종 release lint0오류/20경고를 보존했다. 큰 글씨 상품 화면은 목록을 위로 스크롤하면 미니미·상품명·500P 가격·구매 버튼 전체가 함께 보이며, 초기 화면만 보고 조작 불가라고 판단했던 의견은 철회했다. 자세한 해시·로그·공개 경계는 verification.json의 overnightAudit.finalSignedRelease와 OVERNIGHT_AUDIT에 있다.

소스와 작은 공개 피드는 GitHub에 게시했다. PC의 피드 GET은HTTP200/810bytes지만 AVD 앱 조회는 재확인도 실패해 실제 원격 업데이트 성공으로 표시하지 않는다. 새 서명 APK는 로컬 보존본, 공개 다운로드는 기존0.1.1-dev/code2다. 본인 Google 로그인·원격 백업·실기기0회이며 AVD는 확인 후 종료했다.

## 파일과 재현

- 실제 화면: `native-screenshots/closet-running-gear.png`, `native-screenshots/closet-santa.png`.
- 액티브웨어/코스튬 원화 보드 및 8열 프레임 시트: 현재 디렉터리의 PNG.
- 원본 구현: `app/src/main/java/com/runmoni/app/avatar/AvatarRasterizer.java`.
- 데스크톱 출력: JDK `javac -d work/avatar-v2/classes app/src/main/java/com/runmoni/app/avatar/AvatarRasterizer.java scripts/avatar/ExportAvatar.java`, 이후 `java -cp work/avatar-v2/classes ExportAvatar docs/design/avatars/v0.2.0`.
- 실제 Android 프레임 GIF 출력: `RUNMONI_NODE_MODULES`에 번들 패키지 경로를 설정한 뒤 `node scripts/avatar/export-animations.cjs work/avatar-native-sticky`.
- 앱 검증: `scripts/build.ps1 -IncludeDeviceTests`는 기본 오프라인/작업자 1개. AVD 실행과 APK 설치는 빌드 종료 후 순차 진행하며 이름을 확인해 종료한다.
- 보존 APK: `outputs/runmoni-0.1.3-dev-avatar-0.2.0-debug.apk` (Git 제외). 정확한 크기·SHA-256은 `verification.json` 참조.

Aseprite의 레이어·프레임·스프라이트 개념을 참고했으나, 이번 버전은 기존 코드 기반 캐릭터를 확장한 네이티브 픽셀 구현이다. .aseprite 원본이나 Aseprite로 제작했다는 주장을 하지 않는다. 새로운 패키지 설치·대량 다운로드·터널·외부 공개 서버·방화벽 변경은 수행하지 않았다. 모든 구현·검토·빌드·AVD·내보내기를 순차 진행했고, 이 사실을 PC 인터넷 지연 해결의 증거로 사용하지 않는다.
