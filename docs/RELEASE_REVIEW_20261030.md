# 0.2.0-dev 서명 개발 프리뷰 게시 — 2026-10-30

사용자가 ‘main 병합 승인. APK와 운영 대시보드 배포 진행’이라고 명시적으로 승인했다. PR9/10/11을 main에 통합하고 같은 보관 키로 서명한 검토용 prerelease를 공개했다. 실제 휴대폰·Google 로그인·공용 API 운영 성공으로 표시하지 않는다.

- 공개 버전: 0.2.0-dev / code10, 패키지 `com.runmoni.app`, non-debuggable release.
- 실제 빌드/내장 출처: `e7b988af2b96c640fbcc40681d5201f74527b36a`. 이후 피드/문서 main 커밋으로 APK sourceRevision을 바꾸지 않는다.
- CI: [run41027995152](https://github.com/wjdals988/runmoni/actions/runs/41027995152), JVM40개 실패/오류/skip0, release lint오류0/경고60. 기존 보관 키 검증과 apksigner·실제 버전/code·패키지·내장 source revision 검사를 통과했다.
- APK 크기: 218300173 bytes. SHA256 `b3d802900d9f969b6c82d6b7b6bd7add40830c73b80c33a4cdca9f11fd43b7ca`.
- 인증서 SHA256: `a21359c657f6797f5fb8229c3098e48bc72a45f9d25269f8d41ff5122465df4a`. 0.1.6과 같다. 새 키를 만들지 않았다.
- [릴리스](https://github.com/wjdals988/runmoni/releases/tag/v0.2.0-dev) · [APK](https://github.com/wjdals988/runmoni/releases/download/v0.2.0-dev/runmoni-0.2.0-dev.apk). 서버3자산의 ID/크기/digest와 작은 metadata/checksum 본문을 대조했다. 공개 전후 원래 자산을 재업로드·교체하지 않았다.
- 공개 APK URL HEAD200/Content-Length 일치. APK 본문을 클라우드에서 다시 다운로드하지 않았고 서명 검사는 CI에서 실제 APK에 수행했다. [검증](release-review/2026-10-30/verification.json) · [공개](release-review/2026-10-30/publication.json) · [CI](release-review/2026-10-30/ci-verification.json) · [HEAD](release-review/2026-10-30/public-head-verification.json).

게시 경계의 오프라인 모의 테스트는 release14개/publisher31개 모두 통과했다(실패0). 실제 서명 APK/서버 검증과 이45개 모의 테스트를 합산하지 않는다. [결과](release-review/2026-10-30/publication-policy-tests.txt).

## 실행 검증과 미확인 범위

같은 UI 소스의 debug Android 검증은 [개편 검토](CONSUMER_REDESIGN_REVIEW_20261030.md)의 초기21개/후속8개/native320dp·font2/키보드3개/실제 앱↔localhost API1개다. 이를 이번 서명 APK의 실제 휴대폰 설치·업데이트·Google 로그인 검증으로 합산하지 않는다. 과거0.1.6 JVM31개도 새40개 수치에 더하지 않는다.

공용 모임 서버 URL은 아직 없다. 기기 기록·꾸미기 보유 내역을 유지하며 미준비 의상은 착용/구매를 막는다. 실제 휴대폰/야외 GPS·화면 끔/지도 SDK 인증/Google 백업/다른 기기 음성은 별도 미확인이다. 승인 원본 SHA256은 그대로고 Aseprite/image_gen을 이번에 사용하지 않았다.

0.1.6과 같은 키라 업데이트 가능한 구성이다. 실제 휴대폰 업데이트는 미검증이며 더 오래된 다른 키 설치본은 중요한 기록을 내보낸 뒤 재설치가 필요할 수 있다. 이전0.1.6 sourceRevision9dd409f와0.1.5 원본75c39ca는 바꾸지 않았다.

## 대시보드

사용자가 승인한 대시보드 PR11에 실제 공개0.2 metadata와7장 검토 갤러리를 반영한다. 새 공개 URL/크기/해시와 버전 설명을 함께 갱신하며 실제 main/Production/HTTP 검증 결과는 후속 기록을 따른다.
