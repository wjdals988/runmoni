# 공용 방·Google 계정·휴대폰 연결

**최신 방향 — Firebase Spark 공유 방:** 별도 호스팅 없이 기존 Firebase를 사용하도록 사용자가 선택했다. [최신 공유 방 안내](FIREBASE_COMMUNITY.md)를 먼저 따른다. 아래 HTTP 방식은 선택 가능한 파일럿 경로다. Firebase 방에서는 API 주소/Render 결제가 필요하지 않다.

0.2.1-dev/code11은 설정 → **연결·휴대폰 점검**에서 방 서버 응답, Google 계정 선택/온라인 세션 응답, 사용자가 직접 확인한 휴대폰 결과를 구분한다. 설정이 없는 빌드에 로그인 성공 표시를 넣지 않는다. Google 계정과 방 게스트 프로필은 별도이며 방의 본인 인증을 Google로 대체하지 않는다.

## 1. Google Android 설정 연결 — 소유자 콘솔

현재 클라우드에는 Firebase 관리 계정과 Android 설정 파일이 없다. GitHub 소스/Actions 실행 권한은 있지만 Secrets/Variables 관리 API는403이다. 관리 권한을 확보했다고 주장하거나 토큰/서명키/서비스 계정키를 채팅으로 받지 않는다. 기존 서명 Secrets4개는 유지한다.

1. [Firebase 프로젝트 설정](https://console.firebase.google.com/project/runmoni-3c08e/settings/general) → `com.runmoni.app`에서 현재 서명 SHA-1 **`19:2A:7E:29:84:3C:90:21:0E:ED:D1:37:3A:78:D4:DA:77:F0:97:73`**, SHA-256 **`A2:13:59:C6:57:F6:79:7F:5F:B8:22:9C:30:98:E4:8B:C7:2A:45:F9:D2:52:69:F8:D4:1F:F5:12:24:65:DF:4A`**를 추가한다. Google provider를 켜고 저장한다.
2. **등록 후 새 `google-services.json`을 다운로드**한다. Android 앱 설정이며 Admin SDK/서비스 계정 파일을 받는 단계가 아니다. 로컬 저장소 `.secrets/firebase/google-services.json`에 둔다.
3. 저장소 루트의 PowerShell에서 아래 명령으로 **Android 설정만** Base64로 클립보드에 복사한다. 값은 채팅/터미널에 출력하지 않는다.

```powershell
Set-Clipboard -Value ([Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path (Get-Location) '.secrets/firebase/google-services.json'))))
```

4. [GitHub Actions Secrets](https://github.com/wjdals988/runmoni/settings/secrets/actions/new)에서 이름 **`RUNMONI_FIREBASE_ANDROID_CONFIG_BASE64`**, 값에는 클립보드를 붙여넣어 저장한다. GitHub CLI 설치는 필요 없다. 클립보드는 사용 후 지운다. 설정 파일을 Git·대시보드·공개 artifact에 올리지 않는다.

## 2. 공유 방 연결

기본은 [Firebase 공유 방](FIREBASE_COMMUNITY.md)이다. 익명 제공자·새 규칙·복합 색인·Android 설정을 등록하면 Python 서버 없이 동작한다. 별도 호스팅은 생성하지 않았다. HTTP 방을 선택할 때만 [서버 배포](COMMUNITY_SERVER_DEPLOYMENT.md)를 참고한다.

HTTP 파일럿을 따로 운영하는 경우에만 플랫폼이 발급한 HTTPS 주소를 `RUNMONI_API_URL`로 등록한다. Firebase 기본 연결에서는 이 변수를 설정하지 않는다. 토큰/비밀번호/query를 주소에 넣지 않는다.

## 3. APK 빌드 전 확인

[Android release candidate](https://github.com/wjdals988/runmoni/actions/workflows/android-release.yml)에서 main, `mode=audit`, `profile=review`를 실행한다. SDK/AVD/APK 업로드 없이 기존 보관 키의 공개 DER을 추출해 지문을 대조한다. Artifact `runmoni-connections-<commit>`는 아래 상태만 포함한다.

- `certificateVerified`: 실제 보관 인증서 일치. 비교 기준 지문만 있는 상태와 구분한다.
- `firebase=ready`: 지정 프로젝트/패키지/Web OAuth와 **현재 SHA-1의 Android OAuth** 포함. Google provider의 실제 활성화/계정 로그인 성공은 별도다.
- `server=ready`: 기본 Firebase에서는 복합 색인을 사용하는 공개 방 목록의 REST 읽기를 확인한다. 선택한 HTTP 방식에서는 `/health`의 `status=ok`, `public_rooms=true`, `guest_sessions=true`다. 응답32KiB·10초 상한을 적용하며 방 쓰기/채팅/부하는 별도다.
- `physicalDeviceVerified=false`, `interactiveGoogleLoginVerified=false`: CI가 실제 휴대폰/사용자 계정 선택을 수행했다는 뜻으로 바꾸지 않는다.

세 조건을 충족한 후 `mode=stage-draft, profile=connected`로 서명 후보를 준비한다. `review`는 누락 상태를 기록한 개발 검토 빌드를 허용한다. `connected`는 누락 시 APK 빌드 전에 중단한다. Android 설정은0600 임시 파일로 주입하고 작업 종료 시 삭제한다. APK에 포함되는 Android API/OAuth 식별 정보는 앱 배포의 정상 구성이다. 개인정보/관리용 자격은 받지 않는다.

## 4. 휴대폰에서 직접 확인

동일 보관 키의 서명 APK를 설치한다. 설정 → 연결·휴대폰 점검에서 서버 확인, **Google 계정 선택·로그인**, **계정 서버 응답 확인**을 순서대로 누른다. 복원된 계정 세션은 이번 실행의 대화형 로그인으로 표시하지 않는다. 로그인/세션 시각은 이번 앱 프로세스에서 관찰한 결과만이며 재실행 후 다시 확인한다.

야외 기록 저장 → 앱 재실행 → 기록 확인 → GPX 내보내기를 수행한다. 다른 휴대폰에서 같은 방 입장/채팅을 확인한 뒤 해당 항목을 직접 표시한다. 직접 확인 표시는 버전별 기기에 저장되며 새 버전에는 승계하지 않는다. 원치 않게 표시한 항목은 다시 눌러 해제한다. Firebase 백업/조회/삭제·다른 계정 접근 차단은 [기존 검증 절차](FIREBASE_DEVICE_CHECK.md)를 별도로 수행한다.

**점검 결과 공유**는 Android 공유 시트를 열며 사용자가 보낼 대상을 선택한다. 앱 버전/code·설치 APK의 실제 공개 서명 지문·고정 상태 코드·시각·사용자 확인 표시만 포함한다. 이메일·UID·토큰·서버 주소·경로·좌표·오류 원문을 넣지 않는다. 수동 표시를 자동 성공이나 Google 로그인 검증 횟수로 합산하지 않는다.
