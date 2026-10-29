# Android 개발 프리뷰 게시

Linux 클라우드 빌드와 GitHub Actions의 기존 키 연결·비공개 초안 업로드는
[클라우드 Android 릴리스 준비](CLOUD_ANDROID_RELEASE.md)를 따른다.

`scripts/publish_android_preview.py`는 기존 `work/publish-release.py`와 `work/verify-published-release.py`의 성공한 게시 흐름을 버전 인수로 일반화했다. 저장소는 `wjdals988/runmoni`로 고정하며 개발 prerelease만 다룬다. 기본 실행은 로컬 계획 확인이며 Git·자격 증명·HTTP를 호출하지 않는다. 명시적인 `--stage-draft`는 원격 비공개 초안까지만 준비하고, `--publish`만 공개 전환을 수행한다. AVD·Gradle·패키지 설치를 시작하지 않는다.

## 최종 빌드 준비

1. 다른 채팅과 순서를 조율하고 앱 버전·versionCode·`bundledAppHistory`·CHANGELOG를 함께 확정한다. 최종 소스를 커밋한 뒤 오프라인·작업자1개로 빌드한다. 현재 사용자 지시로 AVD는 제외한다.
   교체 버전의 versionName과 versionCode가 이전 공개 APK보다 증가했는지도 실제 APK 값으로 확인한다.
2. 실제 APK를 `apksigner`와 `aapt2`로 확인한다. 패키지 `com.runmoni.app`, 실제 버전/code, non-debuggable release와 보관한 인증서가 맞아야 한다. 이 게시 스크립트의 메타데이터 비교는 실제 APK 서명 검사를 대신하지 않는다.
3. 버전마다 다음 파일을 준비한다. APK 사실과 빌드 출처를 JSON에 기록한다.

   - `outputs/runmoni-<version>.apk`
   - `outputs/SHA256SUMS-<version>.txt`: 이 APK 해시와 파일명 한 줄
   - `outputs/runmoni-<version>.metadata.json`: `version`, 정수 `versionCode`, `fileName`, `packageName`, 정수 `sizeBytes`, `sha256`, `signed: true`, `certificateSha256`, 실제 빌드 `sourceRevision`, `sourceDirty: false`
   - `docs/RELEASE_NOTES_<version>.md`: 실제 변경과 실기기·클라우드 등 미검증 범위

공개 sidecar는 APK 사실을 담는다. `publiclyReleased: false`, `sourcePublicationConfirmed`, `validatedSourceCommit` 같은 가변 게시 상태를 억지로 true로 바꾸지 말고 최종 sidecar에서 제외한다. `0.1.4-dev` 보존본은 dirty build/검증 commit 정보를 갖고 있으며 새 공개 대상으로 간주하지 않는다. `0.1.5-dev`는 로컬 서명·실제 APK 검사와 plan을 마쳤고, 원격 초안·공개 상태는 별도 확인한다. 최종 문서 커밋 후 같은 app tree의 assemble을 재확인해 sidecar의 출처를 깨끗한 최종 commit으로 고정한다.

대시보드 저장소의 `docs/release-automation.md`는 교체 APK 게시 전에 실제 기기의 설치/업데이트·실행·로그인·변경 기능 smoke 검증을 요구한다. 현재 실기기와 Firebase 실인증0회는 이 게이트 통과가 아니다. 아래 도구의 로컬 plan 성공·서명 선언·서버 digest 확인도 이 검증을 대체하지 않는다. 최종 공개 판단은 실제 아티팩트와 위 검증 근거가 준비된 뒤 담당자가 수행하며, 이 도구에는 이 요건을 우회하는 승인 옵션이 없다.

2026-10-29 최신 사용자 응답은 이번0.1.6 APK를 대시보드에서 받도록 요청했다. 이에 이번 버전만 **실기기 검증 전 테스트용 prerelease**로 명시해 제공한다. 사용자 요청에 따른 검토 배포 방식 변경이며 smoke/로그인 성공으로 처리하지 않는다. 서명·출처·서버 자산 검사는 유지하고, 일반 배포의 실기기 검증 기준과 구분한다.

## 계획·비공개 초안·공개

Python은 기존 번들 런타임과 표준 라이브러리만 사용한다. GitHub 메타데이터 API와 자산 업로드는 Windows에서 native `curl.exe`, Linux에서 `curl`을 사용하며 외부 Python HTTP 패키지를 설치하지 않는다. 이전 `httpx` 기반 실행은 현재 번들에서 모듈 누락으로 인증 조회·API 요청 전에 실패했으므로 이 native 어댑터로 대체했다. 아래 `<version>`은 `0.1.5-dev` 같은 개발 버전, `<commit>`은 실제 소스의 전체40자리 소문자 SHA로 바꾼다.

```powershell
& '<bundled-python.exe>' scripts/publish_android_preview.py --version <version> --target <commit> --plan
```

`--plan` 생략도 같은 동작이다. 로컬3파일의 크기·해시·메타데이터·파일명·서명 선언·공개URL 핀을 비교하고 게시 차단 사유를 표시한다. Git/source 조회·자격 조회·네트워크·공개피드·대시보드 수정은0회다. `localMetadataReady: true`는 메타데이터 경계만 통과했다는 뜻이다. `actualApkSignatureCheckedByThisScript`와 `physicalDeviceSmokeTestCheckedByThisScript`는 항상 false이며 별도 실제 APK 서명 검사·실기기 기능 검증이 완료됐다고 표시하지 않는다.

소스를 GitHub main에 올리고 실제 APK 서명·버전·해시를 확인한 뒤, 실기기 smoke 전에는 비공개 초안까지만 준비할 수 있다.

```powershell
& '<bundled-python.exe>' scripts/publish_android_preview.py --version <version> --target <commit> --stage-draft --rate-kib 256
```

이 모드도 실제 GitHub 자격과 네트워크를 사용한다. 원격 main/tag 대상·기존 draft 제목/본문/자산집합 검사와 APK·해시·metadata3개의 최신 서버 size/digest/URL 검증은 공개 모드와 공유한다. 그 뒤 `draft: true`를 확인하고 공개 PATCH **0회**로 종료한다. 이미 공개된 release는 초안으로 바꾸거나 삭제하지 않고 고정 오류로 중단한다. 동일 초안 재실행은 일치 자산을 재사용하며, 서버 재조회에서 불일치가 있으면 성공 receipt를 남기지 않는다.

GitHub의 미공개 초안은 tag 조회가404여도 인증된 release 목록에 존재할 수 있다. 목록은100개씩 최대10페이지와 기존1MiB 응답 한도 안에서 검사하며, 중복/불완전한 조회는 생성 전에 중단한다. 초안 자산은 검증된 해당 release의 `untagged-…` 임시 주소만 허용하고, 공개 뒤에는 고정 v버전 주소를 다시 확인한다. 임시 주소를 공개 다운로드로 안내하지 않는다.

초안 결과는 `work/staged-release-<version>.json`에 별도로 기록한다. `mode: stage-draft`, `draft: true`, `publicReleaseConfirmed: false`와 인증이 필요한 API 주소를 담고 공개 다운로드용 `url` 필드는 제공하지 않는다. 초안의 자산 URL은 공개 다운로드 확인 증거가 아니며 업데이트 피드·대시보드는 기존 공개값을 유지한다. 이 모드는 실기기 검증 요건을 우회하는 옵션이 아니다.

실기기 설치/업데이트·실행·로그인·변경 기능 smoke 등 공개 게이트까지 확인한 후에만 다음 명령으로 명시적으로 공개한다.

```powershell
& '<bundled-python.exe>' scripts/publish_android_preview.py --version <version> --target <commit> --publish --rate-kib 256
```

주입된 `GH_TOKEN`/`GITHUB_TOKEN`을 우선 재사용하고, 없으면 GCM의 기존 GitHub 자격을 비대화형으로 메모리에 받으며 토큰을 출력·파일 저장·프로세스 인수에 넣지 않는다. curl에는 stdin 설정으로만 전달한다. 첫 `-q`로 사용자 curlrc를 읽지 않고 CR/LF/따옴표/역슬래시가 있는 자격은 거부한다. 자식 프로세스의 Git/GCM trace·TLS key-log 환경변수를 제외하고 사용자 전역 설정은 변경하지 않는다. 오류 stderr/HTTP body 원문도 출력하지 않는다. 기존 인증이 없으면 자동 로그인창을 띄우지 않고 중단한다.

메타데이터용 `CurlApiAdapter`는 고정 저장소의 정해진 GET/POST/PATCH 경로만 허용한다. 각 요청은 native curl1개·HTTP1.1·연결30초·전체120초이며 redirect를 따라가지 않는다. curl의 크기 제한과 Python의 제한 읽기로 API 응답 본문을 최대1MiB로 제한하고 크기 초과 시 자식 curl을 종료한다. 결과는 HTTP status와 JSON object이며 고정된 release 목록 경로만 최대100개 object의 배열을 허용한다. API 오류는 status만 보고한다. 요청 JSON은 `ensure_ascii=True`로 직렬화하고 그 문자열을 다시 JSON의 따옴표/역슬래시 규칙으로 감싸 curl stdin config의 `data-binary`에 넣는다. 한국어·emoji·실제 개행/CR·quote·backslash와 문자 그대로의 `\n`을 JSON 의미대로 보존하며, 본문도 파일/argv로 보내지 않는다.

메타데이터 요청마다 curl 프로세스가 끝나므로 유휴 API 소켓이 자산 업로드와 겹치지 않으며 한 번에1request·한 파일씩 처리한다. 업로드 기본 제한256KiB/s(약2.10Mbit/s), 200MiB의 이론적 최소시간은800초다. 이것이 실제 속도나 PC 전체 지연 해결을 보장하지 않는다. 업로드 timeout은 파일 크기와 선택 속도로 계산하며 자동 반복 재시도는 없다.

원격 main·tag가 요청 commit과 다르면 중단하고 force/태그 이동을 하지 않는다. draft의 제목·본문과 예상 내용이 다르거나 계획 외 자산이 있으면 공개하지 않는다. draft에 APK·해시·metadata3개를 올리고 서버 digest·크기·다운로드URL을 재확인한 뒤에만 prerelease를 공개한다. `--stage-draft`로 준비한 3자산이 같으면 `--publish`는 다시 업로드하지 않고 재검증 후 공개 전환한다. 기존 uploaded asset이 다르면 삭제·덮어쓰기 없이 중단한다. 중단된 업로드의 `starter` 자산도 자동 삭제하지 않는다. 기존 공개 release가 불완전하면 자동 수리하지 않으며 APK 본문 원격 재다운로드는0회다. 공개 결과는 `work/published-release-<version>.json`에 기록한다.

## 게시 이후

원격 공개 release/tag·3자산 digest/size/URL과 작은 sidecar 내용을 별도 읽기로 확인한다. 그 이후에만 `updates/android.json`과 대시보드 APK 메타데이터를 같은 실제 공개값으로 갱신한다. 미게시 APK를 최신 업데이트로 알리지 않는다. 대시보드의 다른 프로젝트·원격 변경을 보존하고 실제 배포 페이지의 다운로드 진입점을 확인한다. 이 게시 도구는 피드·대시보드를 자동 변경하지 않는다.

## 오프라인 검사

```powershell
& '<bundled-python.exe>' -m unittest discover -s scripts/tests -p test_publish_android_preview.py -v
```

테스트의 APK 바이트는 작은 텍스트 fixture이며 실제 서명 APK로 취급하지 않는다. 경로/안정버전/축약SHA 거부, 버전code·패키지·해시·크기·서명선언·URL 핀, 체크섬 파일명, dirty 보존본 차단, 기본 plan의 credential/게시 미호출과 자산 재사용 경계를 검사한다. 제어 흐름 테스트는 자체 API 함수의 status/JSON 반환을 fake로 대체하며 외부 HTTP 모듈을 주입하지 않는다. 공개 전 3자산 최신 검증·불일치 시 공개 차단과 `--stage-draft`의 공개 PATCH0회·동일 초안 재사용·기존 공개본 변경0회를 다룬다. 별도 fake curl 검사는 ASCII JSON/config roundtrip, URL/메서드 제한, 응답1MiB 제한·자식 종료, 실패 시 raw 미출력을 다룬다. socket·subprocess 실행 차단을 유지하고 실제 curl/API 요청을 하지 않는다. 실행하지 않은 테스트를 통과했다고 기록하지 않는다.
