# 0.3.8-dev 게시 검증 — 2026.11.07 한국 시간

우상단 버전·레드닷·설정 사이 간격을 확보했다. [구현 PR](https://github.com/wjdals988/runmoni/pull/37) · [APK](https://github.com/wjdals988/runmoni/releases/download/v0.3.8-dev/runmoni-0.3.8-dev.apk) · [릴리스](https://github.com/wjdals988/runmoni/releases/tag/v0.3.8-dev) · [운영 대시보드](https://coldbrewventi.vercel.app/projects/runmoni).

0.3.8-dev/code20, 219,246,942bytes, SHA256 `c4b484900795defb6d2d5b47d7f9b3545ed99174a07445c2e52edf51937ac76c`, 실제 APK 출처 C `f55bdd6343770847e2284a75faf40063c22a5951`. 같은 보관키 `a21359c657f6797f5fb8229c3098e48bc72a45f9d25269f8d41ff5122465df4a`로 공개한 실기기 검증 전 개발 프리뷰다. 후속 문서/피드 D로 APK 출처 C를 바꾸지 않는다. 서명 CI42373912871의 실제 JVM115·실패/오류/skip0·release lint오류0/경고69. [CI](https://github.com/wjdals988/runmoni/actions/runs/42373912871) · [공개3자산/sidecar](app-review/2026-11-07-update-indicator/publication-verification.json) · [HEAD200/크기](app-review/2026-11-07-update-indicator/public-apk-head-verification.json).

로컬 JVM115/debug lint0/39는 build1 실제 실행이며 같은 제품 APK의 test-only build2가 UP-TO-DATE로 재사용했다. 새115회로 합산하지 않는다. Android는 같은 build2 pair의 기본9+실제320dp/font2 native2=11회다. 행12개: line 오른쪽 초과0px/아래 초과0px, maxLines 초과0개, 원래 paragraph overflow flag12개.1px 반올림 허용/최대 줄 잘림 검사는 유지했다. line geometry는 모든 glyph 픽셀이나 부모 clipping의 증명이 아니다. [측정 요약](app-review/2026-11-07-update-indicator/visible-version-layout-summary.json) · [실행 원문](app-review/2026-11-07-update-indicator/README.md) · [build2 출처](app-review/2026-11-07-update-indicator/source-freeze2.json) · [완료 뒤 별도 소스 실측](app-review/2026-11-07-update-indicator/post-runtime-source-verification.json). 원래 Before/After와 별도 실측을 존재하지 않은 Final snapshot으로 합치지 않는다. 전체 XML/프로필·12개 통제 설정·권한/관찰 전원 복원은 전체 OS 동일성 주장이 아니다.

실제 root가 본5장/갤러리4장은 [manifest](app-review/2026-11-07-update-indicator/root-visual-review.json)을 따른다. 일반 홈·다운로드 확인·About 스크롤 crop·native LTR/RTL이며 About crop은 전체 버전 표시 근거가 아니다. [독립 검토3장](app-review/2026-11-07-update-indicator/peer-visual-review.json)에서 새 P0/P1을 발견하지 않았으나 사용자 승인은 아니다. LTR 아바타 로딩 표시는 원화 준비 승인으로 해석하지 않는다. 사진은 API29/AOSP/TCG·TEST 서명 debug의 Android ComponentActivity 픽스처다. 합성99.0.0-dev는 표시/다운로드 확인 검사값이며 공개99 릴리스나 MainActivity/서명 release 실행 사진이 아니다. RTL은 논리 방향 검사이며 OS locale 검증은 아니다.
- [android-review3-version-shortcut-home-fixture.png](app-review/2026-11-07-update-indicator/android-review3-version-shortcut-home-fixture.png)
- [android-review3-version-shortcut-confirmation-fixture.png](app-review/2026-11-07-update-indicator/android-review3-version-shortcut-confirmation-fixture.png)
- [android-review3-about-information-fixture.png](app-review/2026-11-07-update-indicator/android-review3-about-information-fixture.png)
- [native1-update-header-native-320-font2-ltr-fixture.png](app-review/2026-11-07-update-indicator/native1-update-header-native-320-font2-ltr-fixture.png)
- [native1-update-header-native-320-font2-rtl-fixture.png](app-review/2026-11-07-update-indicator/native1-update-header-native-320-font2-rtl-fixture.png)

첫 [시도1](app-review/2026-11-07-update-indicator/android-review1.json)은 옛 code19 literal로 제품검사/변경 전0회 중단했다. [시도2](app-review/2026-11-07-update-indicator/android-review2.json)는8개 중 paragraph overflow flag1개로 실패했고 나머지7개도 최종11에서 제외했다. test helper만 수정했으며 앱/제품 APK는 같다. 원래 false와 실제 실패 시점/후속 관측 구분은 [개발 검토](UPDATE_INDICATOR_REVIEW_20261107.md#실패와-한계)와 원문에 보존한다.

처음 일부 사진을 blank로 잘못 판단했으나 body3617색/동일 SHA 관측으로 정정했다. 설정용 사진은 blank가 아니라 Home와 body가 같은 잘못된 상태여서 제외했다. 타이밍 원인은 미확정이고 원문/정정 범위는 manifest에 보존했다. 직전0.3.7의 출처 `aa9f4e21983899d0d3d90a73ac41dc02f8662632`·219246942bytes/SHA256 `a864ed8cb0df6444a8074368a21892a8b6f46e97e1432ba2936360f3b0525a53` 및 당시63/BG2는 과거 근거이며 이번에 다시 실행하거나 합산하지 않았다.0.1.5 등 과거 APK 출처도 바꾸지 않는다.

대시보드 [PR](https://github.com/wjdals988/mydashboard/pull/21) · [main CI](https://github.com/wjdals988/mydashboard/actions/runs/42375804584) · Production `7621597599` · main `72c8c83ee8ffaa8bccb995533350b40b97b0e048`와 canonical13항목/PNG4장 실제 바이트를 대조했다. 다른8프로젝트·기존12내역/4PNG는 보존했다. [운영 근거](app-review/2026-11-07-update-indicator/dashboard-production-verification.json). preview20/stable null은 이 문서/피드 PR에 준비하며 병합 뒤 verify-feed.py로 canonical raw 전체 JSON을 대조하고 PR에 기록한다. 같은 증명을 위한 반복 커밋은 만들지 않는다.

휴대폰·Google 로그인·운영 쓰기·야외 GPS·Doze/OEM·API31+·공개 서명 APK 실행·사용자 시각 승인은 미검증이다. F03는 읽기 전용 후보 분석만 진행했다. 남자 bottom2/3 색상·성별별 준비판정을 검토하며 양말은16셀 원화/계약이 필요하다. 준비36/미준비22·승인 원본·58ID/9탭/13슬롯·0P/성별·보유권/포인트를 유지한다.
