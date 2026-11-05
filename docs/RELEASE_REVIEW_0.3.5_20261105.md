# 0.3.5-dev 게시 검증 — 2026-11-05 한국 시간

로고 시작 화면과 홈·설정·옷장의 문구 정리를 [구현 PR](https://github.com/wjdals988/runmoni/pull/31)에서 반영하고 같은 보관키의 개발 프리뷰로 공개했다.

- [APK](https://github.com/wjdals988/runmoni/releases/download/v0.3.5-dev/runmoni-0.3.5-dev.apk) · [릴리스](https://github.com/wjdals988/runmoni/releases/tag/v0.3.5-dev) · [운영 대시보드](https://coldbrewventi.vercel.app/projects/runmoni).
- 0.3.5-dev/code17, 219017566bytes/SHA256 `ad0c5cb66d2bda2917ed0a9b6551a3d5c69fbb1ef872d6ce5e60dc49c3883559`. 실제 APK 출처는 **`9d9d31dc358d9b0e47d34f73df4e9ad8276d80a9`**이며 후속 문서/피드 커밋으로 바꾸지 않는다.
- 기존 인증서 `a21359c657f6797f5fb8229c3098e48bc72a45f9d25269f8d41ff5122465df4a` 유지. [서명 CI42009532323](https://github.com/wjdals988/runmoni/actions/runs/42009532323): JVM84/실패·오류·skip0, release lint오류0/경고65.
- CI에서 실제 APK의 패키지·버전·서명·non-debuggable·내장 출처를 검사했다. [게시3자산/sidecar](app-review/2026-11-05-startup-polish/publication-verification.json) · [CI](app-review/2026-11-05-startup-polish/signed-ci-verification.json) · [공개 HEAD200/크기](app-review/2026-11-05-startup-polish/public-apk-head-verification.json). 전체 APK를 다시 내려받지는 않았다.

## 화면과 시작 동작

기존 런처 로고를 시작 화면과 앱 정보에 맞췄다. 로그인·서버 응답을 기다리게 하거나 고정 대기 시간을 추가하지 않았다. 홈·설정·옷장의 반복 안내를 줄이고 연결 점검은 도움말로 모았다. 계정 연결·복원·연결 해제와 백업 범위 안내는 유지했다.

최종 debug 빌드의 JVM84개는 실패·오류·skip0, lint는 오류0·경고35다. Android22회는 같은 최종 app/test SHA에서 review3의 성공13회와 review4의 성공9회를 원문별로 대조했다. 실패한 그룹과 이전 그래픽 검사를 합산하지 않았다.

review5에서는 Android 검사를 반복하지 않고 실제 OS 시작 창·Main6장·같은 프로세스의 화면 복귀를 새로 확인했다. 설치 전 전체 XML과 화면/애니메이션 설정을 정확히 복원했다. [최종 실행 원문](app-review/2026-11-05-startup-polish/review5.json)·[사진과 바이너리별 근거](app-review/2026-11-05-startup-polish/README.md).

|이전 시도|실패와 처리|
|---|---|
|review1|실제 통과6개를 wrapper가7개로 기대했다. 큰글씨 메서드의 소속 클래스를 바로잡았다.|
|review2|키보드 전환 중 UIA 좌표와 Compose 전체 높이가5초 안에 일치하지 않았다. 대기를30초로 늘리고 표본별 높이를 기록했으며 전체 입력창 검사 조건은 유지했다. 최종 로그에서3.474초의111/174px가5.332초에174/174px로 수렴했다.|
|review3|옷장2개 중1개에서 미니미 로딩 종료 대기30초를 넘겼다. 같은 바이너리·조건으로 해당 그룹을 다시 실행해 통과했지만 원인은 미확정이다.|
|review4|유효한 Android22회 확인 뒤 촬영 스크립트의 미지원 실행 옵션이 API29에서 거부됐다. 검증한 `-f 0x10008000`과 명령 출력·반환값 검사로 고치고 host 촬영만 다시 진행했다.|

이전 실행의 전체 `passed=false`, 원문·드라이버·정확한 복원 기록은 보존했다. 내부 재검토를 사용자 시각 승인이나 실제 휴대폰 결과로 바꾸지 않는다.

API29/AOSP/TCG의 [실제 시작 화면](app-review/2026-11-05-startup-polish/review5-launch-sample-1.png)을 직접 확인했다. ADB 샘플의 host offset은 전체 앱 시작 시간 측정이 아니다. Android12이상 시작 화면 종료120ms는 코드 설정값이며 해당 API 실행 결과나 총 시작 시간으로 쓰지 않는다.

사진은 TEST 서명·Firebase 미설정 debug·로컬 검토 프로필의 실제 MainActivity다. 공개 서명 APK·실제 휴대폰·Google·운영 쓰기 사진으로 표시하지 않는다. 사용자 시각 승인은 별도다.

## 운영 반영과 남은 확인

대시보드 [main CI](https://github.com/wjdals988/mydashboard/actions/runs/42012826438)와 Production `7571416876` 성공, canonical HTTP200/다운로드·버전/code·크기/해시/안내 및 PNG4장의 응답 바이트 일치를 확인했다. [운영 근거](app-review/2026-11-05-startup-polish/dashboard-production-verification.json). 다른8프로젝트·순서/sourceUrl·기존9내역·이전4이미지를 보존했다.

이번 PR은 preview17/stablenull을 준비한다. main canonical raw 전체 JSON 일치는 병합 후 실제 응답을 확인해 PR 최종 기록에 남긴다.

실제 휴대폰·Google 로그인·운영 두 기기 쓰기·야외GPS/배터리와 Android12이상 시작 화면 실행은 미검증이다. 승인 원본·36개 표시 품목·미준비22개/58ID·0P·성별·보유권·포인트를 유지했고 새 원화·Rules·서명키 변경은 없다. Google 요약 백업과 기기의 GPS/포인트/옷장 복원은 별도다.

이전0.3.4 출처 `e2edc2a7ae349422763fc9727aa4e5360a4214e7`·219017510bytes/SHA256 `a1b078587c9357cef41aeaa884c41ee2735a6c83768f614e0354b757ba671947`는 [기존 게시 검증](RELEASE_REVIEW_0.3.4_20261104.md)에 보존했다.0.1.5 출처75c39ca도 바꾸지 않는다.
