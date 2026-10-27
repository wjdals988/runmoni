# 클라우드 릴리스 준비 검증 — 2026-10-27

소스 `e7bec3e37c7e6acb7d3d16cd08499ed987b6bffe`의 깨끗한 checkout에서
새 Linux 도구로 unsigned release 후보를 만들었다. [기계 판독 결과](release-review/2026-10-27/verification.json)를 함께 보존한다.

| 항목 | 실제 결과 |
|---|---|
| 실행 | `python3 scripts/cloud/prepare-android.py --build-only` 후 `python3 scripts/cloud/release-app.py --unsigned` |
| Gradle | 오프라인·작업자1개, 3분38초, 81 tasks(50실행/4cache/27최신) |
| JVM | 30개, 실패/오류/skip0 |
| release lint | 오류0, 경고27 |
| 실제 APK | `com.runmoni.app`, `0.1.6-dev`, versionCode7, non-debuggable |
| 파일 크기 | 218,120,150bytes |
| SHA-256 | `db47d0f8b7899caef51031c1cdb31852fd34a91e99dff5abfed5bbde0e78b5e3` |
| APK 내장 revision | 빌드 커밋 e7bec3e 전체 SHA와 일치 |
| 승인 원화 | APK의 v0.3 PNG가 원본 SHA `f9035f29…`와 일치 |
| unsigned 차단 | apksigner verify가 거부, `signed:false`, `productionUpdateCompatible:false` |
| 기존 키 미연결 | 일반 서명 명령이 빌드 전에 중단, canonical 서명 산출물 생성0 |
| 게시/서명 경계 테스트 | Python32개 통과. APK 문자열 fixture는 실제 서명 증거가 아님 |
| workflow 정적 검사 | 공식 release digest를 확인한 actionlint1.7.12 통과 |
| 실제 GitHub API | 새 Linux curl 어댑터로 저장소 GET200·push권한 확인, 쓰기0 |

산출물은 `outputs/release-candidate/e7bec3e37c7e6acb7d3d16cd08499ed987b6bffe/`에
unsigned APK·metadata·SHA256SUMS·성공 빌드 로그로 보관했다. 이후 문서 커밋으로 이 APK 출처를 바꾸지 않는다.

GitHub에 기존 키가 저장돼 있는지 확인하려던 Actions secret 목록 조회는403이었다.
Environment 목록 조회는 성공했고0개였으나, `android-release` 생성은403으로 거부됐다.
GitHub에 키가 없다고 결론 내리지 않는다. 현재 인증으로 소유자의 보안 설정을 읽거나 추가할 수 없는 상태다.
[보안 설정 경로와 필요한 이름](CLOUD_ANDROID_RELEASE.md)을 준비했으며 채팅으로 비밀값을 요청하지 않았다.

최초 인증 어댑터는 주입된 프록시 placeholder를 기존 raw token 정규식으로 거부했다.
형태 추정을 제거하고 CR/LF·quote·backslash 등 헤더/설정 주입 차단을 유지한 뒤 실제 GET200을 확인했다.
자동 로그인·프록시 우회·비밀 추출·TLS 검증 해제는 하지 않았다.

GitHub Actions 실행, 기존 키로 서명, 새 APK 업로드/공개, 새 release APK 설치·업데이트,
실기기 로그인 smoke, 공개피드/대시보드 교체는 수행하지 않았다. workflow는 검토 브랜치에 있으며
main 반영 후 사용한다. 임의 키 생성이나 main 자동 병합으로 차단을 우회하지 않는다.
기존0.1.5 APK의 원본 `75c39ca900b140cfb02c1a47807a5955c8b8db7f`를 그대로 유지한다.

이번 변경은 릴리스 도구/문서만 다뤘다. UI/UX·미니미의 표시/저장/픽셀 변경은 없으며,
기존 [실제 기본형 화면](APP_REVIEW_20261027.md), [버전/다운로드 화면](UPDATE_REVIEW_20261027.md),
[로딩/큰 글씨 화면](AVATAR_LOADING_REVIEW_20261027.md)을 기준으로 한다.
awesome-design-md와 Aseprite 참고 기준은 DESIGN.md에 유지한다. 새 Android 캡처나 Aseprite 제작물은 없다.
