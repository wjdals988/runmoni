# Firebase 계정·기록 요약 검증 — 0.1.4-dev

대상 프로젝트는 `runmoni-3c08e`, 앱은 `com.runmoni.app`이다. Google 계정 선택은 사용자가 직접 한다. 비밀번호·인증번호·토큰을 채팅이나 로그에 남기지 않는다. AVD는 기능 검증에 사용할 수 있지만 실제 야외 GPS·배터리·제조사 절전 검증을 대신하지 않는다.

## 준비 완료와 남은 항목

2026-10-25 사용자 오류: Google 계정 선택창도 나오기 전에 실패. 실측 AVD Google계정0개·font_scale2.0. 기존 GetGoogleIdOption 바텀시트는 계정0개에서 나타나지 않을 수 있다. 0.1.4는 계정 추가를 지원하는 명시적 GetSignInWithGoogleOption 버튼 흐름으로 변경했다. 변경만으로 실제 Firebase 로그인이 검증된 것은 아니다. [Google 공식 동작 설명](https://developer.android.com/identity/sign-in/credential-manager-siwg). 전용 로그인 AVD는 native화면/font100%로 초기화한다.

후속: 사용자가 새Google계정추가화면 Something went wrong과 AVD실행시PC브라우저까지 지연/종료시회복을 보고했다. 최신 지시는 ‘AVD는 빼고 계속 진행’이므로 AVD를 실행하지 않는다. 현재 검증 경로는 Android실기기에 준비된로컬APK를 USB로 설치하는 것이다. 새 AVD나 이미지 다운로드로 우회하지 않는다. 사용자직접 AVD종료로 마지막guest수신제한복원을 확인하지 못했으므로 다음 허가된실행 전에 점검한다. 현재 실제 Firebase로그인·원격CRUD 성공0회.

- 로컬 Android 설정 파일의 프로젝트 ID·패키지·Web OAuth·release 인증서 포함 정보: 4/4 일치.
- 설정 포함 서명 APK 빌드·APK v2 서명·Firebase 활성화 확인: 완료.
- Spark·Google 제공자·Firestore·Rules 콘솔 설정: 사용자 보고. 원격 설정을 직접 읽어 대조하지 않음.
- 실제 Google 로그인·원격 백업/조회/삭제·계정 전환: 직접 검증 전.
- 최종 서명 APK 설치·실행, 계정0개 상태에서 Google 로그인 버튼 활성화: 확인. 로그인 자체를 수행한 증거는 아니다.
- 이번 전용 AVD에서는 공개 GitHub raw 피드의 앱 조회가 재확인도 실패했다. PC의 HTTP200 확인과 AVD 네트워크는 구분한다. 본인 로그인 전에 AVD의 네트워크부터 확인하고, 연결이 안 되면 비밀번호를 반복 입력하거나 프록시·방화벽을 임의 변경하지 않는다. 실제 Firebase 연결 실패 원인이 확인된 것은 아니다.

## AVD 제외 후 공식 API 기본 설정 확인 — 2026-10-25

작업 PC에서 기존 Android client 설정을 메모리로 읽어 Google 공식 endpoint에 최대2개의 작은 요청만 보냈다. 사용자 식별자·비밀번호·ID토큰은 보내지 않았고 Google 로그인·계정 생성·DB 쓰기는0회다. API키는 명령행 인수/로그에 넣지 않았으며 반환된 OAuth URI와 sessionId도 출력·저장하지 않았다.

| 검사 | 관측 결과 | 한계 |
|---|---|---|
| `GET /v1/projects` | HTTP200·본문146bytes. `androidPackageName`, 실제 release `sha1Cert`, Web `clientId`, `firebaseAppId` 검증 인수를 포함. 프로젝트 일치·기본 authDomain 존재 확인 | 공개 client 설정 검사이며 비공개 콘솔 전체/Rules 조회가 아님 |
| `POST /v1/accounts:createAuthUri` | HTTP200·본문851bytes. provider `google.com`, Google HTTPS 인증 host, 로컬 Web client 일치 | URI 생성만 수행했으며 URI 방문·사용자 인증·토큰 교환은0회 |

2개 요청 전체프로세스2.093초, 정제된 결과는 `outputs/validation/mobile-reset-20261025/firebase-public-readiness.json`에 보존했다. 숫자는 응답 본문의 UTF8 길이이며 TLS/HTTP 헤더를 포함한 전체 전송량이 아니다. 기본 등록/제공자 준비 상태는 서버 검사에 통과했지만 실제 Android Google 로그인·Firestore 원격CRUD 성공은 여전히0회다. 이전 AVD 실패의 근본 원인이나 해결을 확정하지 않는다.

근거: [공개 설정 및 앱 식별자 검증 인수](https://docs.cloud.google.com/identity-platform/docs/reference/rest/v1/TopLevel/getProjects), [제공자 인증 URI 생성](https://docs.cloud.google.com/identity-platform/docs/reference/rest/v1/accounts/createAuthUri).

추가로 Firestore 공식 REST endpoint에 인증 없는 GET을 1회 보냈다. 실제 사용자 UID 대신 새 임의 경로의 `runSummaries` 목록을 `pageSize=1`로 요청했으며 로그인·계정 생성·DB 쓰기는 0회다. curl 종료 코드 35, HTTP 상태 0, 응답 본문 0bytes, 요청 프로세스 0.141초로 TLS 연결 단계에서 끝났다. 따라서 익명 접근 거부나 원격 Rules 일치 여부를 확인한 검사가 아니다. 원문 응답·임의 경로·API키는 기록하지 않았고 정제된 결과만 `outputs/validation/mobile-reset-20261025/firestore-anonymous-boundary.json`에 보존했다. 반복 요청이나 보안·프록시 설정 변경은 하지 않았다.

[Firestore REST 공식 문서](https://firebase.google.com/docs/firestore/use-rest-api)는 미인증 요청에 Security Rules가 적용된다고 설명한다. 이번 요청은 그 판단 결과를 받지 못했으므로 앞선 Auth API 2건의 HTTP 200과도 구분한다. 실제 로그인 후 서버 조회·백업·삭제 검증은 계속 남아 있다.

후속 재확인은 요청 의미·endpoint·클라이언트 헤더·10초 상한을 유지하고 작업 샌드박스 밖의 호스트 실행으로 1회만 수행했다. 새로운 임의 경로를 사용했으며 HTTP 403 `PERMISSION_DENIED`·표준 `Missing or insufficient permissions.` 응답(본문127bytes·0.343초)을 받았다. 해당 익명 목록 요청이 거부된 것은 확인했지만 원격 Rules 전체가 로컬 파일과 같거나 인증된 사용자의 CRUD가 성공했다는 증거는 아니다. 초기 TLS 실패 결과를 덮어쓰지 않고 별도 `firestore-anonymous-boundary-host.json`에 기록했다. 실행 환경이 다른 2회 관측만으로 이전 AVD/PC 인터넷 지연의 원인을 확정하지 않는다. 로그인·계정 생성·DB 쓰기·네트워크/보안 설정 변경은 계속 0회다.

## 사용자가 진행하는 순서

현재는 AVD를 제외한다. 아래 도우미 설명은 이력이며 사용자가 AVD를 다시 허용한 경우에만 적용한다. 검증된 서명 APK 준비 후 `.\scripts\open-firebase-check.ps1`로 로그인용 **가시적** 전용 AVD 창을 열 수 있다. 기존 AVD가 실행 중이면 새 창을 중복 실행하지 않는다. 앱을 자동 삭제하거나 비밀번호·토큰을 읽지 않으며, Google 로그인은 사용자가 화면에서 직접 한다. 이 스크립트를 준비했다는 사실은 실제 로그인이 끝났다는 뜻이 아니다.

이 도우미는 **직접 연 로컬 PowerShell 터미널**에서 실행하고 터미널을 유지한다. 오프라인 APK 설치 후 전용 AVD의 수신 제한값65536B/s를 설정하고 AndroidWifi 가상 AP에 연결한다. 기본 네트워크가 없으면 로그인 단계로 진행하지 않는다. 기본 네트워크 존재만으로 HTTPS·Google 로그인이 성공한 것은 아니다.

검증을 마치면 **AVD 창을 먼저 닫지 말고 터미널에서 Enter**를 누른다. 정상 완료·예외의 finally에서 Wi-Fi/데이터를 끄고, 원래 수신값(null이면설정삭제)을 복원·재조회한 뒤 AVD 이름 확인 후 종료한다. 터미널을 강제 종료하거나 AVD를 먼저 닫으면 복원 여부를 확인할 수 없으므로 다음 실행 때 저장된 제한값을 확인한다. 본인 계정과 기기 기록을 삭제하는 절차는 없다.

수신 설정은 AVD 내부의 인터넷 가능 네트워크에 적용하는 다운로드 설정이다. 송신량·PC 전체 트래픽·실제 전송속도·기존 인터넷 지연 해결을 보장하지 않는다. `-netspeed`는 Ethernet/Cellular용 보조 설정이며 Wi-Fi 양방향 제한이라고 표현하지 않는다. [Android 에뮬레이터 옵션](https://developer.android.com/studio/run/emulator-commandline), [AOSP 수신 제한 설정](https://android.googlesource.com/platform/packages/modules/Connectivity/+/bed26e8f00/framework/src/android/net/ConnectivitySettingsManager.java).

1. Google Play 서비스가 있는 전용 AVD 또는 Android 휴대폰에 **새 서명 APK**를 설치한다. 기존 `0.1.1-dev` 다운로드에는 Firebase가 없다. Android 설정 JSON을 내려받는 것만으로 설치된 앱이 갱신되지는 않는다.
2. AVD에 Google 계정이 없다면 Android **설정 → 계정 → 계정 추가 → Google**에서 직접 로그인한다. 앱이 띄우는 Google 선택 화면에서 계정 추가가 가능하면 그 화면을 사용해도 된다. 현재 전용 AVD는 계정 0개로 확인했다.
3. 런모니 오른쪽 위 설정에서 **Google로 로그인**을 누른다. Google 계정 표시와 클라우드 계정 상태가 바뀌는지 확인한다. 파일럿 로그인과 위치 공유가 자동으로 켜지지 않아야 한다.
4. 러닝 탭에서 기록을 시작하고 종료 확인 후 기기에 저장한다. AVD 좌표를 주입한다면 합성 시험 기록이라고 명확히 구분하고 실제 러닝 성과로 취급하지 않는다.
5. **나 → 완료 기록 상세 → 이 기록 요약을 계정에 백업**을 누른다. 확인창의 대상 Google 계정이 현재 계정인지 확인하고 1건만 백업한다.
6. **설정 → 백업한 기록 요약 보기**에서 서버 조회로 같은 기록이 나오는지 확인한다. 앱을 다시 열어 같은 Google 계정에서 조회하고, 같은 기록을 재백업해도 중복 문서가 생기지 않는지 확인한다.
7. 클라우드 요약을 삭제한 뒤 서버 목록에서 사라지고 **나** 탭의 기기 원본은 남아 있는지 확인한다. 두 저장소의 삭제는 독립적이다.
8. 두 번째 계정이 있으면 계정을 바꿔 이전 계정 목록이 사라지고, 백업 확인이 새 계정으로 다시 뜨는지 확인한다. 두 번째 계정 없이 타인 접근 차단을 실제 검증했다고 기록하지 않는다.

## 정상 결과와 문제 구분

0.1.4 이후 고정 오류코드 G01~G09는 계정 선택·Google 응답 단계, F01~F09는 Firebase 인증 단계다. G01은 계정을 불러오지 못한 경우, G02/G03은 로그인 제공자·기기 지원, F01은 인증 서버 네트워크, F03은 Google 제공자 미활성, F04는 앱 등록·서명/API 설정을 확인한다. 원문 오류·토큰·이메일을 로그나 문의에 복사할 필요가 없다. Firestore Rules는 Google 계정 인증 이후 검증하며 계정 선택창 오류를 Rules 완화로 해결하지 않는다.

| 확인 | 정상 결과 | 실패 시 확인할 항목 |
|---|---|---|
| Google 로그인 | Firebase 사용자 세션으로 전환 | 서명 인증서 SHA-1, Android 패키지, Google 제공자, Web OAuth, Play 서비스·네트워크 |
| 요약 백업·조회 | 현재 UID의 `runSummaries`에 1건 저장·조회 | Firestore 생성 여부·Rules 게시·로그인 세션·네트워크 |
| 재백업 | 같은 run ID 문서 갱신 | 중복 문서나 잘못된 UID 경로 여부 |
| 요약 삭제 | 서버 요약만 삭제 | 기기 기록이 유지되는지 확인 |
| 네트워크 실패 | 명시적인 오류·미확인 안내, GPS 기록은 유지 | 15초 응답 제한은 이미 요청된 쓰기의 취소를 보장하지 않으므로 재조회 |

백업은 `schemaVersion`, `runId`, `startedAtMillis`, `finishedAtMillis`, `activeDurationMillis`, `distanceMeters`, `pointCount`, `backupAt`의 **8개 필드**다. 좌표·전체 경로·마일리지·아이템은 포함하지 않으며 요약 조회로 GPS 경로를 복원할 수 없다.

기본 debug APK의 인증서는 release APK와 다르다. Google 로그인 검증에는 설정 파일에 등록된 기존 release 키로 서명한 APK를 사용한다. debug 인증서를 별도로 등록하지 않은 채 로그인 실패를 DB 장애로 단정하지 않는다.

공식 설정 근거: [Firebase Android Google 로그인](https://firebase.google.com/docs/auth/android/google-signin). 앱의 계정 경계·데이터 범위는 [구현 문서](FIREBASE_IMPLEMENTATION.md), 실제 수행 결과는 [검증 기록](VALIDATION.md)에 남긴다.
