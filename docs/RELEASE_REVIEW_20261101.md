# 0.2.1-dev Firebase 설정 포함 서명 프리뷰 — 2026-11-01

사용자의 기존 ‘main 병합 승인. APK와 운영 대시보드 배포 진행’ 범위에서 설정 포함 개발 prerelease를 공개했다. 실기기 검증 전 안내를 유지하며 Google 로그인·익명 방 쓰기 성공을 추정하지 않는다.

- [연결 audit41321594248](https://github.com/wjdals988/runmoni/actions/runs/41321594248): 보관 인증서·Android/Web OAuth/현재 SHA-1·운영 Firestore 공개 목록 REST 읽기 통과. 소유자는 설정1~4 완료·색인 생성 중이라고 보고했다. 빈 목록 읽기 성공을 색인 콘솔 완료로 해석하지 않는다.
- 연결 근거/안내 [PR15](https://github.com/wjdals988/runmoni/pull/15)를 main에 병합한 실제 빌드 출처는 **`c6bc5739746fe176269fed3bf64360417b50875e`**다. 이후 문서/피드 커밋으로 APK 출처를 바꾸지 않는다.
- [서명 CI41321774862](https://github.com/wjdals988/runmoni/actions/runs/41321774862): `stage-draft/connected`, JVM42개 실패/오류/skip0, release lint오류0/경고60. 실제 APK의 `com.runmoni.app`·0.2.1-dev/code11·non-debuggable·기존 서명·내장 source revision 검사 통과. 임시 키/Android 설정 삭제 단계도 통과했다.
- APK **218431245 bytes**, SHA256 **`2b74ee38f56be2fcfbe88a5b74b2a11c84c8817c961da467a5f7151f12833218`**.
- 서명 SHA256 **`a21359c657f6797f5fb8229c3098e48bc72a45f9d25269f8d41ff5122465df4a`**.0.1.6/0.2.0과 동일하며 새 키를 생성하지 않았다.
- [공개 릴리스](https://github.com/wjdals988/runmoni/releases/tag/v0.2.1-dev) · [APK](https://github.com/wjdals988/runmoni/releases/download/v0.2.1-dev/runmoni-0.2.1-dev.apk). 초안/공개 전후의3자산 ID·size·서버digest와 metadata/checksum 본문을 대조했다. 자산 재업로드/교체 없이 공개했고 HEAD200/Content-Length 일치를 확인했다. APK 본문 재다운로드는0회이며 실제 서명 검사는 CI runner에서 수행했다.
- [검증](release-review/2026-11-01/verification.json), [공개](release-review/2026-11-01/publication.json), [CI 수치](release-review/2026-11-01/ci-verification.json), [HEAD](release-review/2026-11-01/public-head-verification.json). Python 연결/서명·게시 모의55개도 통과했으며 실제 Firebase 인증/방 쓰기 검사로 합산하지 않는다.

기존0.2.0 출처 `e7b988af2b96c640fbcc40681d5201f74527b36a`,0.1.6 출처 `9dd409f00bab54f9ebf7f0ce3f85f36ff54b1750`,0.1.5 출처 `75c39ca900b140cfb02c1a47807a5955c8b8db7f`는 그대로다. 승인된 아바타 원본·카탈로그·지갑·개인 러닝 저장을 변경하지 않았다.

## 휴대폰·운영 검증 경계

APK는 실제 프로젝트의 Android 설정을 포함한다. 공개 목록 읽기는 확인했지만 익명 제공자/규칙은 소유자 게시 보고이며 실제 운영 계정 생성·방 쓰기·두 휴대폰 입장/채팅·Google 계정 선택 로그인·요약 백업·야외 GPS는 아직 미검증이다. 설정 → 연결·휴대폰 점검과 [휴대폰 확인 순서](FIREBASE_COMMUNITY.md#휴대폰에서-확인하는-순서)를 따른다. 수동 체크를 자동 성공으로 기록하지 않는다.

이전 demo Emulator 규칙110개/Android SDK·UI 검사와 클라우드 System UI ANR 제한은 [이전 검토](app-review/2026-10-30-firebase/README.md)에 보존한다. 이번에 AVD를 새로 실행하거나 실제 휴대폰/Google 로그인으로 재분류하지 않았다. 대시보드의 이전0.2.0 검토 이미지는0.2.1 Firebase 운영 화면으로 표시하지 않는다.

공개를 확인한 뒤 업데이트 피드/대시보드를 변경한다. 운영 대시보드 배포 근거는 실제 확인 후 추가한다.
