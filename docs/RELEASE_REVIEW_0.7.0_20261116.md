# 0.7.0-dev 검토 — 2026-11-16 한국 시간

기존 보관키로 **0.7.0-dev/code29** APK를 개발 prerelease로 공개했다. [앱 PR58](https://github.com/wjdals988/runmoni/pull/58)·[APK](https://github.com/wjdals988/runmoni/releases/download/v0.7.0-dev/runmoni-0.7.0-dev.apk)·[공개 검증](app-review/2026-11-15-companion/release-publication/proof.json). 대시보드PR30을 main에 통합했고 운영 canonical 페이지13항목·새MainPNG4장 원문 일치를 확인했다. 업데이트 피드는 같은 공개 메타데이터로 갱신하며 canonical raw전문은 후속 병합 뒤 별도 확인한다. 이전0.6 성공을 새 실행으로 합산하지 않는다.

실제 APK출처 **`ca54251a780f56b60aaecce0dbeb94a4f33061ed`**, **172,509,168bytes**, SHA256 **`79d006bffacec3adc377476d97de6881424cfba48fc7b41b677f6b991228a152`**, 인증서 **`a21359c657f6797f5fb8229c3098e48bc72a45f9d25269f8d41ff5122465df4a`**다. 후속 문서·피드 커밋으로 APK출처를 바꾸지 않는다.

[연결 서명 CI43761237483](https://github.com/wjdals988/runmoni/actions/runs/43761237483)는 새 JVM219·실패/오류/skip0, release lint오류0·경고97·Information0이다. 실제 APK의 패키지/버전/비디버그·서명/내장revision은 CI에서 검사했고 Root는 보고서 archive digest/시각·새XML·같은3자산·공개HEAD/두 sidecar 원문을 대조했다. APK 본문을 다시 다운로드하지 않았다. [실제C/실행입력6맵](app-review/2026-11-15-companion/release-source-comparison.json)·[배포 도구 교정과 보존 실패](app-review/2026-11-15-companion/RELEASE_INTEGRATION_NOTES.md).

밝은 표면·차분한 청록색과 넓은 구역 간격, 아이콘으로 앱·위젯을 정리했다. 동일UID 서버ACK 프로필 연결을 복원하고 출발 전에 약100m 공유를 선택한다. MapLibre/OpenFreeMap 평면 지도와 버전별 의상 이미지 갱신을 연결하며 승인 포즈의 산타·곰·골든 세트를 복구했다. 기존 원본/58아이템ID/데이터/키를 보존한다.

- [로컬 input6](app-review/2026-11-15-companion/binary-build6.json): 새 JVM219·실패/오류/skip0, lint오류0·경고66, TEST APK pair.
- [Android 고유137](app-review/2026-11-15-companion/android-scope-summary.json): input3/4/5/6에서77/29/26/5개. 변경 없는 경로의 실제 소스 동등성 검토를 적용했으며 같은APK137개가 아니다. 원래 false parent·그룹 실패·환경/관측기 실패와 후속 복원-only를 보존한다.
- [Native 지도 사진·픽셀](app-review/2026-11-15-companion/root-map-input6-visual-review.json): 전환 전 전체42/48·의복17/24, 전환 후48/48·24/24와 source/readback mismatch0. 특정 과거 원인 하나·모든 프레임/기기의 무공백을 확정하지 않는다.
- [실제 Main2 원본7](app-review/2026-11-15-companion/root-main-visual-review.json)·[Launcher idle2](app-review/2026-11-15-companion/root-launcher-widget-visual-review.json)·[전용 AVD 종료](app-review/2026-11-15-companion/session5-cleanup.json). 로컬 TEST 화면이며 공개 서명 APK 실행 증거는 아니다.
- [소유자 Rules 게시 전후 조회](app-review/2026-11-15-companion/profile-permission-live-probes.json): fresh nonce 예약 exact read403→404, 임시Auth 삭제200, 운영 profile/nickname 문서쓰기0.

휴대폰 업데이트·Google 로그인·운영 프로필/위치 공유 저장·실제 live 위젯/알림 제어·야외 GPS/절전/OEM·SNS 전달·공개서명APK runtime·사용자 시각 승인은 별도 확인이 필요하다. 미승인 수영복은 포함하지 않는다. [실패·비교·원문 전체](app-review/2026-11-15-companion/README.md).


## 운영 대시보드와 피드

[대시보드PR30](https://github.com/wjdals988/mydashboard/pull/30), main `9060d3661b70007ca189df12485bdae9075b9f57`, [새 운영 검증](app-review/2026-11-15-companion/dashboard-production.json). 새Node23·실패/skip0과 PR/운영main CI의 audit/test/lint/build·Vercel Production 성공을 확인했다. canonical 다운로드 URL·버전/code·크기·해시·미검증 안내 등13항목, 새Main4PNG의 실제 byte/SHA를 대조했다. 다른8프로젝트·지난21릴리스노트·기존4PNG를 보존했다.

피드 preview29는 검증된 동일APK이고 stable은 null이다. 문서/피드 병합 뒤 `https://raw.githubusercontent.com/wjdals988/runmoni/main/updates/android.json`의 전문 바이트를 별도 확인한다. 후속 문서 커밋은 APK출처가 아니다. [기본4색쌍 대비 산술](app-review/2026-11-15-companion/palette-contrast.json)은 12.86/5.13/5.68/4.87이며 모든 화면 픽셀/투명도/지도·사진 대비 검사로 확대하지 않는다.
