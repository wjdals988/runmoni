# 0.4.2 프로필 연결·현재 지역명 검토

사용자 0.4.1 화면의 권한 팝업/UID 변경 안내와 현재 위치 표시 요청을 처리한다. 원래 닉네임·실제 위치가 포함된 사용자 사진은 저장하거나 공개 갤러리로 쓰지 않는다. 신규 사진은 합성 이름/공공장소 GPS를 사용하는 실제 Android 또는 명시한 UI 픽스처다.

최종 결과는 실행 후 갱신한다. 원본 실패 로그와 이전 입력 동결표는 덮어쓰지 않는다. 운영 Rules 배포/Google/휴대폰과 로컬 Firebase-disabled 디버그 실행·localhost demo 검사를 구분한다.

- `source-freezeN.json`/`binary-buildN.json`: 미커밋 실행 입력 전체 SHA. 당시 HEAD는 부모 커밋이며 미래 공개 APK의 출처로 바꾸지 않는다.
- `build1-failure.json`: 고정 Firestore SDK API 이름 오류. `cacheSettings` getter로 수정한 뒤 새 빌드를 수행했다.
- `android-review1.json`: 전체 실패. 하위 23개 통과는 보존하고 탐색 큰 글씨 지도 출처 잘림 실패도 유지한다. 보존 범위와 후속 새 실행 범위는 최종 기록에서 구분한다.
- `source2-to3-continuity.json`: 지도 배치 수정. `source3-to4-continuity.json`: 저장 직전 backend 변경 방지와 호환 검사 보완. 모든 검사를 최종 입력에서 새 실행했다고 쓰지 않는다.
- `firestore-current-rules.txt`: 현재 저장소 Rules의 demo 160개. `firestore-schema-contrast.txt`: 과거 규칙 대비 별도 2개. 과거 정책에서 거절되는 결과는 현재 운영 규칙이 과거 버전이라는 증거가 아니다.
- 운영 점검: [프로필 연결 확인](../../FIREBASE_PROFILE_CONNECTION_CHECK.md).

승인 원본·58개 ID/13슬롯/9탭·0P/보유/포인트·기존 서명키와 과거 APK 출처를 보존한다. 새 원화/아이템 작업·운영 규칙 변경은 이번 범위가 아니다.

## 로컬 최종 확인

- build4: 새 JVM177개, 실패/오류/skip0, lint 오류0·경고54. `binary-build4.json`과 `source-freeze4.json`에 실제 앱/검사 APK 및 전체 입력 SHA를 묶었다. build2/3의177개와 합산하지 않는다.
- Android: 최종 입력으로 프로필 UI8·실제 VM2·옷장 저장1·9개 폭/글씨 조합의5탭1=12개를 새 실행했다(`android-review2.json`). 별도 실제 Firebase SDK3개도 같은 APK쌍에서 새 실행했다(`android-review4.json`). 원래 설정·프로필·community account 파일 존재/바이트·소유 환경 복원을 각각 확인했다.
- `android-review1.json`의 지역명 repository3/UI3·날씨3·커뮤니티UI4=13개는 이전 입력의 통과를 보존한다. 새15개와 합쳐 서로 다른28개 검사 범위를 설명하지만,28개 전부를 최종 APK쌍에서 새로 실행한 것은 아니다. `android-scope-summary.json`을 따른다.
- SDK 첫 시도(`android-review3.json`)는 제공 프록시를 거친 localhost 인증의 upstream 연결 오류로2개가 실패했고 전체 false와 복원 결과를 보존했다. 로컬 demo 전용 검사 중에만 소유 AVD 프록시를 임시 해제한 뒤 SDK3개가 통과했다. 호스트 프록시/NO_PROXY/TLS/방화벽을 바꾸지 않았고 원래 guest5키도 복원했다. 앱·Rules·검사 기대를 바꿔 통과시킨 것이 아니다.
- 실제 focused/resumed MainActivity4장과 동일 입력 연결2회·기기 이름 저장1회·프로필 X 실제 조작을 확인했다(`live1.json`). 이 앱은 Firebase 비활성 로컬 TEST debug이므로 configured=false 실패 안내이며 운영 PERMISSION_DENIED나 운영 연결 성공의 재현이 아니다. 실제 서버 계정/닉네임 경합은 별도 localhost demo SDK 근거다.
- Root가 원본4장 전부를 실제 열어 개인정보·표시를 검토했다(`main-visual-review.json`). 홈 미니미 옆 날씨·현재 위치, 지도와 보이는 OSM 출처, 단일 프로필 내부 안내·입력 유지, 저장 뒤 나 화면을 확인했다. 사용자 미술 승인은 아니다.
- 실제 Main은 현재 위치의 근처 좌표를 표시했다. 기기 Geocoder 지원 여부/실패 원인·조회 좌표/HTTP 상태는 독립 측정하지 않았다. 동네 이름이 나오는 UI 사진은 주입한 지역명 픽스처이며 실제 Geocoder 성공 사진으로 쓰지 않는다. 공개 서명 APK/휴대폰/Google/야외 GPS/절전/API31+ 실행은 별도다.
- Main finally에서 원래 전체 XML·프로필·account 파일 존재/바이트·DB rows fingerprint·소유 설정/권한/전원을 대조했다. 원래 GPS fix나 전체 OS 파일 복원을 주장하지 않는다. 두 로컬 backend 종료, guest 프록시5키 복원과 소유 AVD 종료는 `session-cleanup.json` 및 각각의 원문을 따른다.

서명 CI·공개 APK·운영 대시보드·피드는 이 로컬 기록과 별도 게시 근거로 이어간다. 운영 Rules 게시 버전은 아직 확인 전이며 앱이 권한을 우회하지 않는다.
