# Runmoni0.4.2-dev 게시 검증

프로필 연결의 서버 확인 전 UID 노출·일반 권한 팝업 중첩과 재시도를 수정하고 홈/탐색에 현재 지역명·근처 좌표를 추가했다. 저장 직전 UID/backend 변경과 이전 snapshot을 차단하며 기존 기록·지갑·소속·Google 경계를 보존한다. 작은 높이/큰 글씨의 지도 출처 잘림도 수정했다. 새 Rules·의존성·원화·서명키 변경은 없다.

## 공개 APK

- 2026-11-11 한국 시간,0.4.2-dev/code24, 동일 보관키 개발 prerelease. [APK](https://github.com/wjdals988/runmoni/releases/download/v0.4.2-dev/runmoni-0.4.2-dev.apk) · [릴리스](https://github.com/wjdals988/runmoni/releases/tag/v0.4.2-dev) · [기능 PR45](https://github.com/wjdals988/runmoni/pull/45).
- 실제 APK 출처 C `176a9cbe1112efa05e9fc55d913738ee1778a57f`. 후속 문서/피드 커밋으로 바꾸지 않는다. 크기 `219,821,173`bytes, SHA256 `2cd7bd0a865ed75af64a6bf30b5e93e7e92259b7eb9467b0f06adb91c415b046`, 인증서 SHA256 `a21359c657f6797f5fb8229c3098e48bc72a45f9d25269f8d41ff5122465df4a`.
- [서명 CI](https://github.com/wjdals988/runmoni/actions/runs/43020432448) JVM177·실패/오류/skip0, release lint오류0·경고84. 패키지/버전·non-debuggable·내장 C와 서명을 CI에서 검사했다. 공개3자산/메타데이터/해시 sidecar와 APK HEAD200/크기를 대조했으며 Root가 APK 본문을 재다운로드하지 않았다. 연결 readiness는 구성 확인이며 휴대폰/Google/운영 쓰기 성공이 아니다.

## 실행 범위와 실패 보존

- 최종 local TEST debug build4는 새 JVM177·실패/오류/skip0, lint오류0·경고54. 이전 build2/3의177개를 합산하지 않는다. 당시 미커밋 빌드의 HEAD는 이전 main `6cc16b0329b47a82c1d46102d1d5a24159625673`이며 전체 입력 SHA를 [실제 공개 C의 blob과 비교](app-review/2026-11-11-profile-location/executed-source-vs-release-C.json)했다. debug CA/Firebase 비활성 설정을 사용한 로컬 APK와 공개 서명 APK는 바이트가 같은 파일이 아니다.
- Android: 최종 입력/같은 APK쌍으로 프로필UI8·실제VM2·옷장 저장1·9개 폭/글씨 조합의5탭1=12개를 새 실행했다. 별도 localhost Firebase SDK3개도 새 실행했다. 이전 입력의 지역명 repository3/UI3·날씨3·커뮤니티UI4=13개는 보존 결과다. 서로 다른28개 검사 범위이며 전부를 최종 APK쌍에서 새 실행했다고 쓰지 않는다. [범위](app-review/2026-11-11-profile-location/android-scope-summary.json).
- 현재 저장소 Rules demo160개와 과거 정책 대비 별도2개를 확인했다. 과거 정책의 예약 경로 거절은 현재 운영 규칙이 과거 버전이라는 증거가 아니다. 이번 새 운영 Rules 게시를 실행하지 않았다.
- 실패는 원문과 함께 보존했다: 초안의 고정 SDK getter 컴파일 오류; 첫 Android 전체 시도의 320dp/font1.5 지도 출처2px 잘림; 첫 설치 준비의 이전 APK SHA45초 timeout(설치 전); SDK 첫 시도의 프록시 경유 localhost Auth upstream 오류. 각각 실제 getter 수정, 제품 세로 배치 수정, 크기 선확인/필요 해시120초, 로컬 demo 전용 guest 프록시 임시 해제로 처리했다. 검사 기대·Rules를 완화하지 않았다. SDK 재실행은 앱/검사/Rules가 같은 입력이다.
- 실제 MainActivity4장: 홈·탐색 현재 위치/날씨/지도, 프로필 동일 입력 연결2회 실패 안내와 재시도, 기기 이름 저장1회·X 실제 조작. [원문](app-review/2026-11-11-profile-location/live1.json) · [Root 이미지 검토](app-review/2026-11-11-profile-location/main-visual-review.json). Main의 연결 실패는 Firebase 비활성 configured=false이며 운영 PERMISSION_DENIED/성공의 재현이 아니다. 닉네임 경합/같은 UID 재연결은 별도 실제 SDK+demo 서버 근거다.
- Root가 원본4장을 모두 실제 열어 표시/개인정보를 검토했다. 실제 Main은 근처 좌표 fallback이며 native Geocoder 지원 여부/조회 실패 원인·HTTP/조회 좌표는 독립 측정하지 않았다. 명명된 지역 사진은 주입한 UI fixture다. API29/TCG·합성 GPS/이름이며 사용자 시각 승인·실기기/공개 서명 APK 실행·Google·운영 쓰기·야외 GPS·Doze/OEM·API31+는 별도다.
- 원래 전체 XML/프로필·account 파일 존재/바이트·소유 설정/권한/전원을 복원했다. Main은 DB rows fingerprint도 대조했다. 전체 OS 파일/원래 GPS fix 복원을 주장하지 않는다. 두 demo backend 종료·guest 프록시5키 복원·소유 AVD796575 종료를 [확인](app-review/2026-11-11-profile-location/session-cleanup.json)했다. 호스트 프록시/NO_PROXY·TLS·방화벽·PC AVD를 바꾸지 않았다.

## 운영 대시보드와 다음 확인

[대시보드](https://coldbrewventi.vercel.app/projects/runmoni) · [PR](https://github.com/wjdals988/mydashboard/pull/25) · [main CI](https://github.com/wjdals988/mydashboard/actions/runs/43024403321) · Production `7710630271` · main `e16f030dc91a5f9c21d795c546fd850a38e2d6c1`. canonical13항목과 원본 Main PNG4장의 바이트·로컬 Node23개를 확인했다. 다른8프로젝트·이전16개 릴리스노트·이전4개 PNG 파일을 보존했다. [운영 근거](app-review/2026-11-11-profile-location/dashboard-production-verification.json).

preview24/stable null을 이 문서/피드 PR에서 준비한다. 정확한 canonical raw 전체 바이트는 병합 뒤 PR 최종 댓글에서 별도 확인한다. 운영 Rules의 `communityNicknames` 경로 게시 여부와 새 APK의 실제 휴대폰 연결은 아직 별도 확인이다. [조건별 점검](FIREBASE_PROFILE_CONNECTION_CHECK.md)을 따른다. 이미 같은 규칙이면 다시 게시할 필요가 없다. 승인 원본 f9035f29…/58ID·13슬롯·9탭·0P/보유/포인트와 과거0.4.1 출처1eff941·0.1.5 출처75c39ca를 보존한다.
