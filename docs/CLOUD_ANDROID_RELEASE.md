# 클라우드 Android 릴리스 준비

최신 공개는 동일 보관 키의0.2.0-dev/code10이며 [2026-10-30 게시 근거](RELEASE_REVIEW_20261030.md)를 따른다. 아래0.1.6/키 연결 기록은 과거 이력이다.

2026-10-28 최신 사용자 요청으로 **새 배포 서명키를 생성**했다. 이전 Windows 키 유지 선택보다
이 요청이 우선한다. 배포 기준은 [공개 서명 정책](../config/android-release-signing.json)이다.

- 새 인증서 SHA-256: `a21359c657f6797f5fb8229c3098e48bc72a45f9d25269f8d41ff5122465df4a`
- RSA3072, PKCS12. 개인키·비밀번호는 Git/채팅/로그에 넣지 않는다.
- 이전 `2d7f724c…`의 APK는 새 키 APK로 덮어쓸 수 없다. 기존 테스트 설치본은 재설치가 필요하다.
- 과거 APK의 인증서·해시·sourceRevision을 새 키로 바꾸어 기록하지 않는다.
- 공개피드/대시보드는 실제 새 APK 공개·서버 자산 확인 뒤에 갱신한다.

## 모든 환경에서 같은 키 사용

**현재 상태 — 2026-10-29:** [GitHub 공개키 암호화 전달](GITHUB_SIGNING_BRIDGE.md)을 거쳐
사용자가 원래 PC의4개 등록 receipt를 전달했다. repository scope/승인 지문/미게시 상태가 일치한다.
이는 사용자 제공 등록 결과다. 이후 승인된 [PR2](https://github.com/wjdals988/runmoni/pull/2) main 병합과
SDK 충돌 수정 뒤 run40855991283에서 같은 키의 실제 서명 APK 검사가 성공했다.
Actions 권한 힌트·실제 미공개 URL/검색 처리 보강 뒤 최종9dd409f의 run40858081567에서
서명·초안 업로드·3자산 검증이 완료됐다. [현재 검증 기록](RELEASE_REVIEW_20261029.md)을 따른다.
이후 사용자가 대시보드 다운로드를 요청해 이번0.1.6만 실기기 검증 전 테스트용 prerelease로 공개했다. 실제 설치/로그인 검증은 미완료로 유지하며 [공개 기록](release-review/2026-10-29/publication.json)을 따른다.

소유자 OAuth 로그인 뒤에도 이 클라우드의 Secrets API는403으로 거부됐다. 정확한 중계 원인은
미확인이며 같은 승인을 반복하지 않는다. 아래 --login 방식은 실패 이력으로만 보존한다.

개발 환경마다 키를 새로 만들지 않는다. GitHub 저장소의 Actions Secrets에 새 키를 한 번
등록하고, 배포 APK는 GitHub Actions에서 그 키로 서명한다. PC/클라우드 변경이 개인키 자동 공유를
뜻하지는 않는다. GitHub Secrets는 Actions 실행에 제공되며 일반 API로 평문을 다시 읽는 저장소가 아니다.

현재 클라우드의 주입 GitHub 인증은 보안 설정에서403이다. 소스 읽기/푸시 권한과 secrets 쓰기 권한은
다르다. [클라우드 연결 도구](../scripts/cloud/connect-signing-key.py)는 보호된 개인키 상태 파일에서
입력을 읽어 공개 정책과 실제 인증서를 대조한 뒤, GitHub CLI로4개 값을 로컬 암호화해 등록한다.
비밀값은 stdin으로만 전달한다. 기본 연결 대상은 저장소 Actions Secrets이며 Environment 생성/관리 권한을 요구하지 않는다. 필요할 때만 `--scope environment`로 별도 Environment를 사용한다.

```bash
python3 scripts/cloud/connect-signing-key.py --state-file <private-state.json> --login
```

`--login`은 공식 GitHub CLI의 웹 승인을 요청한다. 현재 task의 주입 인증을 덮어쓰지 않고 별도 임시
인증 설정을 사용하며, 연결 종료 시 임시 로그인 설정을 삭제한다. 토큰/비밀번호를 채팅으로 요청하지
않는다. `--login` 없이 실행하면 기존 인증을 사용한다. 이 도구는 새 키를 생성하거나 APK를 공개하지 않는다.
실제 권한/secret 등록 성공은 모든4개 항목의 성공 receipt가 있어야 판단한다.

재연결된 현재 환경에서는 알려진 소유자 전용 임시 키 경로가 없는 상태다. 에이전트가 정리를
수행했다고 주장하지 않는다. GitHub에 등록된 동일 키로 실제 CI 서명을 확인했으며 자동 재생성하지 않는다.
클라우드 스냅샷에는 개인키를 포함하지 않는다.

## Windows 보관 키 도구의 범위

[`ConnectOriginalSigningKey.java`](../scripts/ConnectOriginalSigningKey.java)는 보관된 `.secrets` 파일의
키를 연결하는 별도 복구 도구다. 현재 승인된 새 인증서만 받으므로 이전 Windows 키에는 사용하지 않는다.
현재 새 키는 위 공개키 bridge의4개 등록 receipt를 수신한 상태다. 이전 키 연결 명령을 반복하지 않는다.

## GitHub에서 서명·업로드

실제 첫 소유자 로그인은 성공했지만 Environment 생성은 실패해 secret 전송0회로 종료됐다. 기존 도구가 그 거부 응답의 HTTP 상세를 남기지 않아 원인을 권한 거부로 단정하지 않는다. 저장소 Actions Secrets를 기본 대상으로 변경해 불필요한 Environment 생성 단계를 제거했고, HTTP status/오류 분류/OAuth scope 헤더 존재 여부만 남기는 진단을 추가했다. 원문 응답/토큰/비밀값은 출력하지 않는다.

[Android release candidate](../.github/workflows/android-release.yml)는 수동 실행만 지원한다.
서명 작업은 검토된 `main`에서만 가능하며, PR 생성 자체는 병합 승인이 아니다.
이 workflow를 main에 반영한 뒤 GitHub Actions의 Run workflow에서 실행한다.

GitHub 저장소 **Settings → Secrets and variables → Actions → Repository secrets**에
승인된 새 배포 키를 다음 이름으로 연결한다. 값은 GitHub 설정에만 입력한다.

| 이름 | 필요한 값 |
|---|---|
| `RUNMONI_KEYSTORE_BASE64` | 승인된 새 배포 keystore의 base64 인코딩 |
| `RUNMONI_STORE_PASSWORD` | 배포 keystore 비밀번호 |
| `RUNMONI_KEY_ALIAS` | 배포 alias |
| `RUNMONI_KEY_PASSWORD` | 해당 개인키의 비밀번호 |

실행 모드는 다음과 같다.

- `unsigned` — 키 없이 release 컴파일·JVM·lint·실제 APK 패키지/버전/non-debuggable/내장 source revision 검증. 산출물 이름에 unsigned를 넣으며 게시 입력으로 사용할 수 없다.
- `signed` — 승인된 인증서 확인 → release 빌드·JVM·lint → 서명·APK 검사 → Actions artifact 보관.
- `stage-draft` — signed와 같은 검사 뒤 GitHub Release의 **비공개 초안**에 APK·metadata·SHA256SUMS 세 파일을 올리고 서버 digest/size/URL을 재확인한다.

서명키는 runner 임시 경로에0600 권한으로 복원하고 마지막 단계에서 삭제한다.
빌드 캐시·artifact에 키를 포함하지 않는다. 임시 GitHub runner를 사용한다.
GitHub API 인증은 workflow의 `github.token`을 사용하며 별도 PAT를 요구하지 않는다.
초안 업로드는 기본256KiB/s, 단일 파일/요청으로 진행하고 기존 자산을 삭제·덮어쓰지 않는다.
소스가 원격 main/tag와 다르면 게시 도구가 중단한다. 자동 병합·공개 전환·피드/대시보드 교체는 하지 않는다.

## 현재 클라우드에서 빌드

체크아웃이 깨끗한 커밋이어야 한다. 전용 AVD는 빌드 전에 종료한다.

```bash
cd /workspace/runmoni
python3 scripts/cloud/prepare-android.py --build-only
python3 scripts/cloud/release-app.py --unsigned
```

기본은 오프라인·Gradle 작업자1개다. 최초 캐시에 없는 의존성이 확인된 경우에만 `--online`을 사용한다.
`--build-only`는 emulator/system image를 다운로드하지 않는다. 기존 클라우드 AVD 설정은 바꾸지 않는다.

서명 입력이 이미 안전하게 연결된 실행 환경에서는 `--unsigned`를 빼면 된다.
기존 `.secrets/signing.properties`를 사용하는 Gradle 경로도 지원한다. 환경변수 방식은
`RUNMONI_KEYSTORE_FILE`과 위의 store password/alias/key password 세 변수가 필요하다.
클라우드의 HTTPS 프록시 secret은 로컬 서명 프로그램에 사용할 원시 파일/비밀번호 주입 기능이 아니므로,
이를 설정했다고 키가 준비됐다고 주장하지 않는다. 지속 클라우드 스냅샷에 개인키를 포함하지 않는다.

서명본은 `outputs/runmoni-<version>.apk`, metadata, SHA256SUMS로 내보낸다.
unsigned는 `outputs/release-candidate/<sourceRevision>/`에만 내보낸다.
어느 경로든 다른 바이트가 이미 있으면 덮어쓰지 않는다. 메타데이터의 sourceRevision은
실제 빌드 커밋과 APK 내장 revision을 대조하며 이후 문서 커밋으로 바꾸지 않는다.

## 공개 다운로드 연결

[기존 게시 규약](ANDROID_RELEASE_WORKFLOW.md)의 실제 기기 설치/업데이트·실행·로그인·변경 기능 smoke를
수행한 뒤 서명본을 공개한다. 그 후 서버 자산의 크기·해시·서명을 기준으로 `updates/android.json`과
대시보드 다운로드 메타데이터를 갱신한다. unsigned/debug/미공개 초안은 새 공개 업데이트로 알리지 않는다.

원래 PC의 등록 receipt를 수신해 보관했다. 클라우드의 기존403을 추가 OAuth 승인으로 다시 시도하지 않는다.
main 병합·실제 CI 서명·비공개 초안3자산 업로드/검증은 완료했다. 등록/서명/초안 결과를 APK 공개 성공으로 해석하지 않는다.

2026-10-29 최신 배포 상태: 사용자의 대시보드 다운로드 요청에 따라 검증 전0.1.6 개발 프리뷰를 공개했다. [공개 기록](release-review/2026-10-29/publication.json)의 sourceRevision9dd409f와 canonical URL/서버 digest를 기준으로 피드·대시보드를 갱신하며, 휴대폰/Google 로그인 성공으로 표시하지 않는다.
