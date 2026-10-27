# 클라우드 Android 릴리스 준비

기존 공개 앱을 업데이트할 키는 새로 만들지 않는다. 기존 인증서 SHA-256은
`2d7f724cd20b1ff2263eb34e8e02d5fec9db674488b10b813dd239838b975830`이다.
키·비밀번호·base64 내용을 채팅이나 Git 파일에 넣지 않는다. 서명된 APK에서 개인키를 복원할 수는 없다.

## GitHub에서 서명·업로드

[Android release candidate](../.github/workflows/android-release.yml)는 수동 실행만 지원한다.
서명 작업은 검토된 `main`에서만 가능하며, PR 생성 자체는 병합 승인이 아니다.
이 workflow를 main에 반영한 뒤 GitHub Actions의 Run workflow에서 실행한다.

GitHub 저장소 **Settings → Environments → android-release → Environment secrets**에
기존 보관용 키를 다음 이름으로 연결한다. 값은 GitHub 설정에만 입력한다.

| 이름 | 필요한 값 |
|---|---|
| `RUNMONI_KEYSTORE_BASE64` | 기존 `runmoni-release.jks`의 base64 인코딩. 새 키가 아니다. |
| `RUNMONI_STORE_PASSWORD` | 기존 keystore 비밀번호 |
| `RUNMONI_KEY_ALIAS` | 기존 alias |
| `RUNMONI_KEY_PASSWORD` | 해당 개인키의 기존 비밀번호 |

실행 모드는 다음과 같다.

- `unsigned` — 키 없이 release 컴파일·JVM·lint·실제 APK 패키지/버전/non-debuggable/내장 source revision 검증. 산출물 이름에 unsigned를 넣으며 게시 입력으로 사용할 수 없다.
- `signed` — 기존 인증서 확인 → release 빌드·JVM·lint → 서명·APK 검사 → Actions artifact 보관.
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

현재 클라우드에는 원래 키가 없고, GitHub Actions secret **목록 조회**도403이다.
이는 GitHub에 키가 없다는 증거가 아니며, 이 인증으로 확인·연결할 수 없다는 의미다.
실제 서명/업로드 성공은 별도 실행 결과가 있어야 주장할 수 있다.
