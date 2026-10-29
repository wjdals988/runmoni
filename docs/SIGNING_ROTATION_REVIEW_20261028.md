# 신규 배포 서명 검증 — 2026-10-28

사용자가 새 키 생성과 GitHub 보안 설정 연결을 명시적으로 요청했다.
새 RSA3072/PKCS12 키는1개이며 이후 환경마다 재생성하지 않는다.
[공개 서명 정책](../config/android-release-signing.json)이 앱·빌드·게시 도구의 기준이다.
개인키/비밀번호는 소유자 전용 임시 위치에 있으며 Git/채팅/로그에 기록하지 않았다.

| 검사 | 실제 결과 |
|---|---|
| clean 소스 | `639b1f943cf73d04aa4d76af0e9fff058169c8ba` |
| 빌드 | 오프라인·작업자1개, 1분28초, 81 tasks(22실행/59최신) |
| JVM | 31개, 실패/오류/skip0. 이전 인증서 거부 회귀검사 포함 |
| release lint | 오류0·경고27 |
| 실제 APK | `com.runmoni.app`, `0.1.6-dev`, code7, non-debuggable |
| 파일 | `outputs/runmoni-0.1.6-dev.apk`, 218,201,869bytes |
| APK SHA-256 | `2de9eb6d1cb427d2df70888d3e97163406da76cc80123b549cabd28f2d8d3ada` |
| 실제 apksigner 검증 | 성공, 승인된 새 인증서와 일치 |
| 인증서 SHA-256 | `a21359c657f6797f5fb8229c3098e48bc72a45f9d25269f8d41ff5122465df4a` |
| APK 내장 revision | 빌드 소스와 일치 |
| 승인 v0.3 PNG | 원본 SHA `f9035f29…` 일치 |
| 도구 경계 검사 | Python41개·Java12개, 모두 오프라인 fixture/fake transport |
| 로컬 게시 plan | 차단0, 네트워크/자격조회0. 원격 main/tag 검증과 별개 |

[기계 판독 기록](release-review/2026-10-28/verification.json)을 함께 보존했다.
이후 문서 커밋을 이 APK sourceRevision으로 바꾸지 않는다.
기존0.1.5 APK의 원본 `75c39ca900b140cfb02c1a47807a5955c8b8db7f`와 과거 서명/해시는 보존한다.

첫 빌드는 sandbox가 네트워크 인터페이스를 제한해 Gradle의 로컬 FileLockContentionHandler 시작에서
중단됐다. 자격을 넣지 않은 최소 help 명령으로 `Could not determine a usable wildcard IP`를 확인했다.
허용된 실행으로 로컬 잠금 통신을 사용한 오프라인 재시도는 성공했다. 의존성 다운로드·TLS 검증 해제는0회다.

현재 주입 GitHub 인증은 secret 암호화 공개키/Environment 수정에서403이다.
새 키 생성이 이 권한을 바꾸지는 않는다. 공식 GitHub CLI의 웹 승인 흐름은 별도 임시 로그인 설정을
사용하고 원래 task 인증은 유지한다. Git 인증 변경 질문에는 No를 선택했다.
cloud의 브라우저 실행 실패는 사용자 브라우저의 공식 로그인 페이지에서 처리하는 흐름으로 구분한다.
키/비밀번호/토큰 값을 채팅으로 요청하지 않는다.

GitHub 연결 성공은4개 secret 등록 receipt로 판정하며 현재 상태는 기계 판독 기록을 따른다.
로그인용 일회 코드는 저장소에 기록하지 않는다. 연결이 끝나기 전에 개인키 상태를 버리거나 스냅샷에 넣지 않는다.

이번 서명 APK의 Android/실기기 실행·기존 앱 업데이트·실제 로그인 검증은 수행하지 않았다.
기존 Android8/4개 결과는 이전 debug 소스의 UI 검증이며 이 APK 실행 결과로 합산하지 않는다.
새 키는 이전 키의 설치본을 덮어쓰지 못하므로 기존 테스트 앱은 재설치가 필요하다.
새 APK 원격 업로드/공개·업데이트 피드/대시보드 다운로드 교체는 별도 단계다.
앱 픽셀/의복/UI 변경은 없으며 DESIGN의 awesome-design-md/Aseprite 참고 기준과 기존 실제 화면을 유지한다.
