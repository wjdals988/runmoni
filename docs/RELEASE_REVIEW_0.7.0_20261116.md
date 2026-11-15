# 0.7.0-dev 검토 — 2026-11-16 한국 시간

현재 앱/code29의 로컬 검토를 완료했으며 같은 키 서명·APK 공개·운영 대시보드는 후속 단계다. 이 문서는 이전0.6의 성공을 새 실행으로 합산하지 않는다.

밝은 표면·차분한 청록색과 넓은 구역 간격, 아이콘으로 앱·위젯을 정리했다. 동일UID 서버ACK 프로필 연결을 복원하고 출발 전에 약100m 공유를 선택한다. MapLibre/OpenFreeMap 평면 지도와 버전별 의상 이미지 갱신을 연결하며 승인 포즈의 산타·곰·골든 세트를 복구했다. 기존 원본/58아이템ID/데이터/키를 보존한다.

- [로컬 input6](app-review/2026-11-15-companion/binary-build6.json): 새 JVM219·실패/오류/skip0, lint오류0·경고66, TEST APK pair.
- [Android 고유137](app-review/2026-11-15-companion/android-scope-summary.json): input3/4/5/6에서77/29/26/5개. 변경 없는 경로의 실제 소스 동등성 검토를 적용했으며 같은APK137개가 아니다. 원래 false parent·그룹 실패·환경/관측기 실패와 후속 복원-only를 보존한다.
- [Native 지도 사진·픽셀](app-review/2026-11-15-companion/root-map-input6-visual-review.json): 전환 전 전체42/48·의복17/24, 전환 후48/48·24/24와 source/readback mismatch0. 특정 과거 원인 하나·모든 프레임/기기의 무공백을 확정하지 않는다.
- [실제 Main2 원본7](app-review/2026-11-15-companion/root-main-visual-review.json)·[Launcher idle2](app-review/2026-11-15-companion/root-launcher-widget-visual-review.json)·[전용 AVD 종료](app-review/2026-11-15-companion/session5-cleanup.json). 로컬 TEST 화면이며 공개 서명 APK 실행 증거는 아니다.
- [소유자 Rules 게시 전후 조회](app-review/2026-11-15-companion/profile-permission-live-probes.json): fresh nonce 예약 exact read403→404, 임시Auth 삭제200, 운영 profile/nickname 문서쓰기0.

휴대폰 업데이트·Google 로그인·운영 프로필/위치 공유 저장·실제 live 위젯/알림 제어·야외 GPS/절전/OEM·SNS 전달·공개서명APK runtime·사용자 시각 승인은 별도 확인이 필요하다. 미승인 수영복은 포함하지 않는다. [실패·비교·원문 전체](app-review/2026-11-15-companion/README.md).
