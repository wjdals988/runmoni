# 0.3.3-dev 게시 검증 — 2026-11-04 한국 시간

[앱 구현 PR](https://github.com/wjdals988/runmoni/pull/27)을 main에 통합하고 같은 보관키의 실기기 검증 전 개발 prerelease를 공개했다. 실제 APK 출처는 **`abee084b115abfb8693a9fe92c03e024c9adb29d`**다. 후속 문서/피드 커밋으로 바꾸지 않는다.

## 실제 공개 자산

- [개발 릴리스](https://github.com/wjdals988/runmoni/releases/tag/v0.3.3-dev) · [APK](https://github.com/wjdals988/runmoni/releases/download/v0.3.3-dev/runmoni-0.3.3-dev.apk) · [운영 대시보드](https://coldbrewventi.vercel.app/projects/runmoni).
- 0.3.3-dev/code15, `com.runmoni.app`, non-debuggable, **218882079bytes/SHA256 `6fa1b77a70b022eeee92ab1033d5aa0bd287ae3f503f178bd0ced4beea1c612e`**.
- 기존 인증서 SHA256 `a21359c657f6797f5fb8229c3098e48bc72a45f9d25269f8d41ff5122465df4a` 유지. 새키·Secret 변경 없음.
- [서명CI41829484636](https://github.com/wjdals988/runmoni/actions/runs/41829484636) 성공: JVM81개/실패·오류·skip0, release lint오류0/경고64. CI가 실제 APK 서명·패키지/버전·non-debuggable·내장 출처를 검사했다.
- 게시 UTC `2026-11-04T12:50:15Z`. [3자산/sidecar](app-review/2026-11-04-kit-colors/publication-verification.json) · [서명CI](app-review/2026-11-04-kit-colors/signed-ci-verification.json) · [공개HEAD](app-review/2026-11-04-kit-colors/public-apk-head-verification.json).

GitHub 서버digest/크기와 작은sidecar본문·공개HEAD200/크기를 대조했다. 검토자가 APK전체를 다시 내려받아 독립 해시를 계산한 것은 아니다. 연결 readiness의 실제 휴대폰/Google/익명운영쓰기 false 플래그를 바꾸지 않았다.

## 변경과 실제 실행

기본 러닝복 상의·하의·신발 천의 독립 색상/기기 저장을 추가했다. 원본·민트·코랄·블루·퍼플·골드를 지원한다. 원본 파일/얼굴·체형을 재생성하지 않고 명시 프레임 마스크·명암 팔레트를 사용한다. 여자 셀 상단12px의 이전행 누출만 표시에서 가리며 raw 원본은 보존한다. 기존포인트·보유권·58IDs/13슬롯/9탭·무료·성별을 유지한다. 색상 변경과 적립 스냅샷의 화면 경합도 보강했다. [구현/독립검토](RUNNING_KIT_REVIEW_20261104.md).

Host3456사례·서버43개·debug JVM81개와 Android 데이터10/렌더7/UI3/실제320dp·글씨200%1/실제 MainActivity 외부 입력/프로세스 재시작 검증은 [실행별 근거](app-review/2026-11-04-kit-colors/README.md)에 있다. 초기 실패·선택자/로딩 대기 수정·바이너리 해시·실제 화면·환경팝업/정리도 보존했다. 반복을 고유 검사 수로 합산하지 않는다. Android 사진은 API29/AOSP/TCG·TEST서명·Firebase설정없는 debug이며 공개보관키 APK/실기기/Google/운영 데이터 화면이 아니다. 이번 외부 입력 검증은 실제 프로세스 종료/새 PID와 재시작 후 저장/그림을 확인한다. 실제 휴대폰 재부팅/전원 장애 검증은 별도다.

## 대시보드·피드와 미확인

대시보드 [main CI](https://github.com/wjdals988/mydashboard/actions/runs/41833167107)·Production7546479640 성공, 실제 HTML의 버전/다운로드/크기/해시/개발프리뷰/출처 안내와 최신 PNG3장의 응답 바이트 일치를 확인했다. [검증](app-review/2026-11-04-kit-colors/dashboard-production-verification.json). 다른8프로젝트·기존7개 내역/이전2개 갤러리 파일을 보존했다. 브라우저 클릭이나 사용자 시각 승인으로 확대하지 않는다.

공개 preview 피드는 이번 PR에서15로 갱신하고 stable은null이다. main의 실제 raw HTTP200/전체JSON일치는 해당 PR 최종 검증 기록에 남긴다. APK 출처는 `abee084b115abfb8693a9fe92c03e024c9adb29d`로 고정한다.

새 색상 사용자 시각 승인·실제 교체형 의복/헤어/수영복·58종 원화 완성은 남아 있다. 기본 Firebase 색상은 기기 저장이며 Google연결이 원본GPS/포인트/옷장 전체 백업은 아니다. 실제 휴대폰/Google/운영두기기쓰기/야외GPS/배터리는 미검증이다. 최신Rules게시완료는 소유자 보고이며 새Rules변경은 없다. 지도 인증/첫마커 준비와 일시실패 재시도는 후속 점검이다.

이전0.3.2 source8eb04d8·218627853bytes/SHA2b1789d2…는 [이전검증](RELEASE_REVIEW_0.3.2_20261104.md)에 그대로 보존했다.0.1.5 source75c39ca를 포함한 과거 APK 출처를 새main으로 바꾸지 않는다.
