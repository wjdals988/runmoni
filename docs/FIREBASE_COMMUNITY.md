# 무료 Spark 공유 방 연결

2026-10-30 사용자는 별도 호스팅 계정 없이 기존 Firebase에서 방을 운영하는 방향을 선택했다. 기본 연결은 `runmoni-3c08e`의 Authentication 익명 프로필 + Firestore 공개 방/멤버/실시간 채팅이다. **Cloud Functions·Render·유료 디스크·Blaze 결제 연결이 필요하지 않다.** Spark 무료 한도를 사용하며 프로젝트의 실제 요금제는 소유자가 확인한다.

## 소유자가 한 번 연결하는 순서

현재 클라우드에 Firebase 관리 계정/Android 설정 파일이 없고 GitHub Secrets 관리 API는403이다. 다음 항목만 소유자 콘솔에서 적용한다. 비밀번호·서비스 계정키·토큰·서명키는 채팅으로 보내지 않는다.

1. [Authentication 제공자](https://console.firebase.google.com/project/runmoni-3c08e/authentication/providers)에서 **익명(Anonymous)**과 **Google**을 켠다. 익명은 방 닉네임, Google은 개인 기록 요약 백업에 사용한다. 지원 이메일은 본인이 선택한다.
2. [Android 설정](https://console.firebase.google.com/project/runmoni-3c08e/settings/general)의 `com.runmoni.app`에 현재 보관 키 지문을 추가한다. SHA-1 **`19:2A:7E:29:84:3C:90:21:0E:ED:D1:37:3A:78:D4:DA:77:F0:97:73`**, SHA-256 **`A2:13:59:C6:57:F6:79:7F:5F:B8:22:9C:30:98:E4:8B:C7:2A:45:F9:D2:52:69:F8:D4:1F:F5:12:24:65:DF:4A`**. 등록 후 새 `google-services.json`을 받는다. 이전 키 지문은 지우지 않아도 된다.
3. 파일을 로컬 저장소 `.secrets/firebase/google-services.json`에 두고 아래 PowerShell로 **Android 설정만** 클립보드에 복사한다. [GitHub Actions Secret 추가](https://github.com/wjdals988/runmoni/settings/secrets/actions/new)에서 이름 **`RUNMONI_FIREBASE_ANDROID_CONFIG_BASE64`**, 값에 붙여넣어 저장한다. GitHub CLI는 필요 없다. 사용 후 클립보드를 지운다.

```powershell
Set-Clipboard -Value ([Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path (Get-Location) '.secrets/firebase/google-services.json'))))
```

4. [Firestore 규칙](https://console.firebase.google.com/project/runmoni-3c08e/firestore/databases/-default-/rules)에 [firestore.rules](../firebase/firestore.rules) **전체**를 붙여넣고 게시한다. 기존 본인 요약 보호 규칙도 포함돼 있다. 전체 허용 규칙을 쓰지 않으며 다른 프로젝트에 덮어쓰지 않는다.
5. [Firestore 색인](https://console.firebase.google.com/project/runmoni-3c08e/firestore/databases/-default-/indexes)에 컬렉션ID **`communityRooms`**, 컬렉션 범위의 복합 색인을 만든다: `visibility` 오름차순, `kind` 오름차순, `createdAt` 내림차순. [정의](../firebase/firestore.indexes.json)와 같으며 완료될 때까지 기다린다. 기본DB `(default)`가 없다면 Spark/Standard·프로덕션 모드로 먼저 생성한다.

Firebase 방에는 외부 서버 주소와 `RUNMONI_API_URL`이 필요하지 않다. 기본 backend는 `firebase`다. 기존 HTTP 파일럿은 고급 테스트 설정으로 보존하며 Firebase 인증 토큰을 그 서버에 보내지 않는다.

## 새 APK와 휴대폰 확인

[Android release candidate](https://github.com/wjdals988/runmoni/actions/workflows/android-release.yml)의 main, `mode=audit, profile=review`로 먼저 점검한다. 실제 보관 DER·Android 프로젝트/패키지/Web OAuth/현재 SHA-1·공개 방 목록의 REST 읽기를 검사한다. 계정 생성·개인 읽기·방 쓰기는 하지 않는다.

`ready=true`는 **현재 인증서·Android 설정·공개 목록 읽기 준비**다. `anonymousProviderVerified`, `publicRoomWritesVerified`, `physicalDeviceVerified`, `interactiveGoogleLoginVerified`는 CI에서 false다. `mode=stage-draft, profile=connected`는 구성 누락 시 APK 빌드 전에 중단한다. 서명 키4개는 유지하고 Android 파일은0600으로 주입해 종료 시 삭제한다. 원문 설정/API 키/토큰은 artifact·로그·Git에 넣지 않는다.

구성 포함 APK → 크루 탭 새로고침 → 닉네임/성인 확인 → 방 만들기 → 다른 휴대폰에서 같은 방 입장 → 채팅/공지 → 파티 정원/취소 순서로 확인한다. 설정 → 연결·휴대폰 점검에서 Firebase 목록 응답과 Google 계정 선택/온라인 세션 응답을 각각 확인한다. [공유 범위](CONNECTED_DEVICE_CHECKS.md)를 따른다. APK 서명·버전/code·해시·출처를 확인한 뒤 공개 피드와 대시보드를 바꾼다.0.2.0의 출처/자산은 보존한다.

익명 방은 Google 요약 계정과 별도다. **연결 해제·기기 변경·재설치 후 참여 내역/방장 권한은 복구되지 않는다.** 해제 전 앱에서 다시 확인한다. Google 연결로 익명 방을 복구/합치는 기능은 아직 없다. 방 데이터는 자동 삭제하지 않으며 소유자가 콘솔에서 관리한다.

## 권한·비용 경계

- `communityProfiles/{uid}`는 본인만 읽는다. 요약은 기존 `/users/{uid}/runSummaries`를 유지한다. 지갑/보상/원본 GPS/임의 경로 쓰기는 계속 거부한다.
- 공개 목록은 종류별 최근50개와 본인이 참여한 방이다. 공개 메타데이터에 제목·설명·지역·페이스·일정·정원·인원·소유자UID가 포함되며 이메일/계정 프로필/개인 GPS는 넣지 않는다. 멤버/최근50개 채팅은 해당 방 참여자만 읽는다.
- 생성/입퇴장은 인원·개인 참여 링크·멤버3문서를 원자적으로 바꾼다. 정원 경합/카운터 단독 위조를 차단한다. 방장·주최자는 탈퇴하지 못하고 파티 취소는 주최자/부모 크루장만 가능하다. 임의 소유권 변경은 거부한다.
- 채팅은 작성자·닉네임·서버 시각·멤버 속도 제한 문서를 함께 검증한다. 메시지1~1000자/2초 간격, 생성10초 간격/프로필당20회, 참여100방으로 제한한다. 공지는 방장/주최자/DJ/부모 크루장만 허용한다.
- 목록은 수동/작업 후 갱신, 멤버는 관리 화면을 열 때 조회한다. 채팅은 열린 방만 구독하며 닫기/로그아웃 때 해제한다. 캐시를 서버 연결 성공으로 표시하지 않는다.
- 무료 한도는 프로젝트 전체에서 공유된다. 익명 계정 남용·대량 공개 조회를 완전히 막는 구성은 아니다. App Check·계정 복구·운영 신고/정리·부하/배터리 검증은 후속 범위다. Firebase 방은 음성 통화·음악 방송·실시간 GPS 공유를 지원하지 않는다. 개인 러닝/포인트/옷장/GPX는 기기에 보존한다.

로컬 demo Emulator 검증과 운영 게시를 구분한다. [공식 Spark 요금제](https://firebase.google.com/docs/projects/billing/firebase-pricing-plans), [규칙 트랜잭션 함수](https://firebase.google.com/docs/reference/rules/rules.firestore), [익명 인증](https://firebase.google.com/docs/auth/android/anonymous-auth), [복합 색인](https://firebase.google.com/docs/firestore/query-data/indexing)을 참고한다.
