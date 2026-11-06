# 0.3.6-dev 게시 검증 — 2026-11-06 한국 시간

[구현 PR](https://github.com/wjdals988/runmoni/pull/33)에서 OS 스플래시→최초 Google/게스트 선택→앱 흐름을 반영하고 같은 보관키의 개발 프리뷰로 공개했다. 선택을 마친 기기와 기존 사용자는 앱으로 바로 들어간다.

- [APK](https://github.com/wjdals988/runmoni/releases/download/v0.3.6-dev/runmoni-0.3.6-dev.apk) · [릴리스](https://github.com/wjdals988/runmoni/releases/tag/v0.3.6-dev) · [운영 대시보드](https://coldbrewventi.vercel.app/projects/runmoni).
- 0.3.6-dev/code18, 219066718bytes/SHA256 `e05bc3be0fa4d4a4120ac2d01aea993662fb8abdcd647da4c094de3f838e61b1`. 실제 APK 출처는 **`d27b31f85eafa42eb4c96fe17ff2483536d919f0`**이며 후속 문서/피드 커밋으로 바꾸지 않는다.
- 기존 인증서 `a21359c657f6797f5fb8229c3098e48bc72a45f9d25269f8d41ff5122465df4a` 유지. [서명 CI42154848444](https://github.com/wjdals988/runmoni/actions/runs/42154848444): JVM97/실패·오류·skip0, release lint오류0/경고68.
- 실제 APK의 패키지·버전·서명·non-debuggable·내장 출처를 CI에서 확인했다. [게시3자산/sidecar](app-review/2026-11-06-entry-flow/publication-verification.json) · [CI](app-review/2026-11-06-entry-flow/signed-ci-verification.json) · [공개 HEAD200/크기](app-review/2026-11-06-entry-flow/public-apk-head-verification.json). APK 전체를 다시 내려받지 않았다.

## 진입과 데이터 경계

입장 선택은 인증 상태와 별도의 기기 preference다. Google 인증은 기존 `runmoni-cloud`의 요약 백업 계정을 연결하되 수동 백업을 자동 실행하지 않는다. 기존 UID·크루 signedIn·일반 함수 반환을 이번 인증 성공으로 해석하지 않는다. 게스트는 기기 오프라인 사용이며 크루 프로필·익명 계정·위치 공유·업로드를 자동 생성하지 않는다.

Google 취소·실패에는 재시도와 게스트를 남기고 중복 클릭·늦은 결과·Activity 재생성에 대응한다. 저장 실패는 이번 실행을 허용하고 선택이 반복될 수 있음을 알린다. 기존 사용자·진행/복구 중 러닝은 가로막지 않는다. 세션 만료나 오프라인 때문에 앱 전체를 다시 잠그지 않는다. [설계/상호 검토](ENTRY_FLOW_REVIEW_20261106.md).

## Android 실행과 실제 화면

최종 오프라인 build2는 JVM97/실패·오류·skip0, debug lint오류0/경고38다. **같은 app/test 설치 해시의 18회 모두 새 실행**이며 저장4·진입UI4·시작2·설정/업데이트6·실제320dp/글씨200%2다. 이전 검사·실패 그룹·다른 바이너리 결과를 합산하지 않았다.

API29/AOSP/TCG의 실제 MainActivity7장·게스트 선택 저장/프로필 보존·같은PID 화면 복귀·force-stop 뒤 새PID의 선택 화면 생략과 설치 전 전체 preference XML·설정의 정확 복원을 확인했다. DB·Auth를 초기화하지 않았고 전용 AVD를 종료했다. [실행 원문](app-review/2026-11-06-entry-flow/review1.json) · [바이너리/사진/실패 기록](app-review/2026-11-06-entry-flow/README.md).

실제 진입 사진은 기존 데이터 보존을 위한 로컬 검토 선택 상태다. 활성 Google 버튼/큰 글씨 사진은 fake callback ComponentActivity fixture이며 실제 Google 인증 성공이 아니다. Main 사진은 TEST 서명·Firebase 미설정 debug의 로컬 검토 프로필이며 공개 서명 APK·휴대폰·운영 쓰기 사진으로 표시하지 않는다. 사용자 시각 승인은 별도다.

[API29 OS 시작 로고](app-review/2026-11-06-entry-flow/review1-launch-sample-1.png)를 직접 확인했다. host 캡처 offset을 시작 시간으로 측정하지 않았고 API31의 종료120ms 설정을 전체 시작 시간이나 API31 실행 결과로 사용하지 않는다.

초기 build1은 잘못된 검사 import에서 실패했다. 그 import만 제거한 뒤 build2가 성공했으며 제품 소스는 두 빌드 사이에 동일하다. 실행 준비의 SystemUI Wait1회와 fresh tree 접근성 재시도를 보존했다. 원인을 확정하거나 앱 ANR 해결로 표현하지 않는다.

## 운영 게시와 다음 확인

대시보드 [PR](https://github.com/wjdals988/mydashboard/pull/19)·[main CI](https://github.com/wjdals988/mydashboard/actions/runs/42157245569)·Production `7591646369` 성공, canonical HTTP200/다운로드·버전/code·크기/해시/안내13항목과 PNG4장 응답 바이트 일치를 확인했다. [운영 근거](app-review/2026-11-06-entry-flow/dashboard-production-verification.json). 다른8프로젝트·순서/sourceUrl·기존10내역·이전4이미지는 보존했다.

이번 PR은 preview18/stablenull을 준비한다. main canonical raw 전체 JSON 일치는 병합 후 실제 응답을 확인해 PR 최종 기록에 남긴다.

실제 휴대폰·Google 로그인·운영 두 기기 쓰기·야외GPS/배터리와 Android12이상 시작 화면 실행은 미검증이다. 승인 원본·36개 표시 품목·미준비22개/58ID·0P·성별·보유권·포인트는 유지했고 원화·Rules·서명키 변경은 없다. Google 요약 백업은 GPS/포인트/옷장 전체 복원이 아니며 크루 계정의 연결·복원은 별도다.

이전0.3.5 출처 `9d9d31dc358d9b0e47d34f73df4e9ad8276d80a9`·219017566bytes/SHA256 `ad0c5cb66d2bda2917ed0a9b6551a3d5c69fbb1ef872d6ce5e60dc49c3883559`는 [기존 게시 검증](RELEASE_REVIEW_0.3.5_20261105.md)에 보존했다.0.3.4/0.3.3 및0.1.5 출처75c39ca도 바꾸지 않는다.
