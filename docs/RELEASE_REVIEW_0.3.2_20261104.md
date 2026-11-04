# 0.3.2-dev 게시 검증 — 2026-11-04 한국 시간

[앱 PR24](https://github.com/wjdals988/runmoni/pull/24)와 [릴리스 안내 PR25](https://github.com/wjdals988/runmoni/pull/25)를 main에 병합하고 기존 보관 키의 **실기기 검증 전 개발 prerelease**를 공개했다. 실제 APK 출처는 **`8eb04d80e3b810f6514c7586dfefde4e1207f1f2`**이며 후속 문서·피드 커밋으로 바꾸지 않는다.

## 공개 자산과 서명 CI

- [릴리스](https://github.com/wjdals988/runmoni/releases/tag/v0.3.2-dev) · [APK 다운로드](https://github.com/wjdals988/runmoni/releases/download/v0.3.2-dev/runmoni-0.3.2-dev.apk)
- 0.3.2-dev/code14, package `com.runmoni.app`, non-debuggable.
- **218627853bytes**, SHA256 **`2b1789d22f811bdfe84e7c81dcb62aa472e3ed4a693f2831cf4fa3113ee809a4`**.
- 기존 인증서 SHA256 `a21359c657f6797f5fb8229c3098e48bc72a45f9d25269f8d41ff5122465df4a` 유지. 새 키는 생성하지 않았다.
- 실제 게시 UTC `2026-11-04T06:28:22Z`, 한국 날짜 **2026-11-04**.
- [서명 CI41783393629](https://github.com/wjdals988/runmoni/actions/runs/41783393629) 성공: JVM76개, 실패·오류·skip0. Release lint 오류0/경고64. 실제 APK의 서명·패키지·버전·non-debuggable·내장 출처 검사 단계가 성공했다.

[공개 3자산과 작은 sidecar](app-review/2026-11-04-reliability/publication-verification.json) · [서명 CI](app-review/2026-11-04-reliability/signed-ci-verification.json) · [공개 HEAD](app-review/2026-11-04-reliability/public-apk-head-verification.json). GitHub 서버의 digest/크기와 sidecar 본문을 대조하고 공개 HEAD200/크기를 확인했다. APK 본문을 다시 다운로드해 독립 해시를 계산한 것은 아니다. 연결 readiness의 false 검증 플래그는 변경하지 않았다.

## 변경과 검증 범위

저장 실패 시 같은 runId로 재시도하고 최초 종료 시점을 고정한다. 기기 저장·포인트 처리·Google 요약 백업을 분리하며 GPS 신선도와 하단 재시도를 보강했다. 크루 UID를 보존하는 Google 연결과 서버의 기존 프로필을 확인한 뒤 재연결하는 경로를 구현했다. 자동 계정 합치기는 없다. [상호 검토](RELIABILITY_REVIEW_20261104.md).

최종 debug Android 데이터12개·UI7개·시스템 큰 글씨4개·로컬 demo Firebase SDK1개와 실제 MainActivity 홈/러닝2장, 초기 실패·빈 프레임·시스템 팝업, 설정 복원/AVD 종료는 [실행별 근거](app-review/2026-11-04-reliability/README.md)에 보존했다. 반복과 선행 회귀5개를 고유 검사 수로 합산하지 않는다. Debug 실행·공개 서명 APK 실행·실제 휴대폰·Google·운영 Firebase 쓰기는 각각 다른 근거다.

배포 도구 unit70개가 통과했다. 사전 검토에서 릴리스 안내 문구 확인 조건을 맞췄고, 첫 HEAD 검사의 부적절한 파일 크기 제한을 제거해 재확인했다. 명시적 `--head --location`은 유지했고 APK 본문은 요청하지 않았다. [사전 검토](app-review/2026-11-04-reliability/release-preflight-verification.json) · [도구 실행 관찰](app-review/2026-11-04-reliability/publication-tool-observations.json). 앱 코드나 APK를 다시 변경하지 않았다.

실제 휴대폰·Google 자격 충돌/다른 기기 복원·운영 두 기기 쓰기/권한·야외 GPS/배터리·TalkBack 전체 동선은 미검증이다. 로컬 GPS·지갑·옷장 전체 백업과 기존 요약 UID/크루 UID 자동 합치기는 미구현이다. 최신 Rules 게시 완료는 소유자 보고이며 이번 새 Rules 변경은 없다. 승인 원본·데이터·무료/성별·58개 ID/13슬롯/9탭을 보존하고 미준비 의복을 완성으로 표시하지 않는다.

## 대시보드와 업데이트 피드

[대시보드 PR15](https://github.com/wjdals988/mydashboard/pull/15)의 main `68992b06d84a0f63b6e702c7d15a6f58eb631e96`, main CI41786128760, Production7539646407 성공을 확인했다. [운영 페이지](https://coldbrewventi.vercel.app/projects/runmoni)의 실제 HTML에서0.3.2/code14·다운로드 anchor·크기·해시·개발 프리뷰 표시와 PNG2장의 응답 바이트 일치를 확인했다. [운영 근거](app-review/2026-11-04-reliability/dashboard-production-verification.json). 로컬 Node23개도 통과했다. 다른8개 프로젝트·기존6개 내역/5개 이미지는 보존했다. 브라우저 클릭이나 사용자 시각 승인까지 확인한 것은 아니다.

이 문서와 함께 공개 preview 피드를 갱신하며 stable은 null이다. Main 통합 후 실제 raw HTTP200/전체 피드 일치 결과는 해당 PR의 최종 검증 기록에 남긴다. 피드·문서의 main 커밋은 APK 출처 `8eb04d80e3b810f6514c7586dfefde4e1207f1f2`와 구분한다.

이전0.3.1 출처3074c0f와218496781bytes/SHA2f778496…는 [이전 검증](RELEASE_REVIEW_0.3.1_20261103.md)에 보존한다.0.1.5 출처75c39ca를 포함한 과거 자산을 재기록하지 않는다. 다음 독립 개발은 [승인 의복 레이어 계약](APPROVED_GARMENT_LAYER_PLAN_20261104.md)을 기반으로 한 F03 실제 착용이다.
