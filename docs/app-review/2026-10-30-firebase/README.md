# Firebase 방·휴대폰 점검 검증

대상0.2.1-dev/code11 debug 작업트리. 릴리스 키/운영 Firebase를 사용하지 않았다. 앱 의존성·원화·옷장ID·기기 지갑/기록 스키마는 보존했다.

PR13은 main `4f270f0795125bf96e10b08dfe92ec8433e4d3d7`에 통합됐다. [GitHub 규칙 CI](https://github.com/wjdals988/runmoni/actions/runs/41042955223)는110/실패0으로 통과했다([결과](github-rules-result.json)). 이후 main의 [연결 audit](https://github.com/wjdals988/runmoni/actions/runs/41043328298)은 **실제 보관 DER 지문 일치**, `firebase=missing`, `server=firebase_configuration_missing`, `ready=false`를 확인했다([결과](github-connection-readiness.json)). 이 audit는 APK를 빌드/게시하지 않았고 운영 Firebase를 변경하지 않았다. 로컬 debug 검증과 CI 보관 인증서 검증을 구분한다.

| 실행 | 결과 | 확인 범위 |
|---|---|---|
| 오프라인 Gradle·worker1 | JVM42, 실패/오류/skip0; debug lint오류0/기존 경고35 | 현재 앱/테스트 APK 컴파일, 도메인/공유 allowlist |
| 실제 Firestore Emulator | 110 통과, 실패/skip0 | 기존 개인 요약89+방21; 정원 경쟁·3문서 원자성·역할·속도·개인 경로 차단 |
| Android Firebase SDK | 1 통과,20.091초 | demo 익명 클라이언트3개; 같은 방/실시간 공지/DJ/정원/취소/탈퇴/로그아웃 |
| Android native UI/HTTP | 6 통과,71.049초 | Compose 렌더·접근성 의미·콜백/공유 위임·로컬 무인증 HTTP 처리 |
| Android320dp/font2 | 같은 UI4개 재실행 통과,74.372초 | 작은 화면/큰 글씨의 텍스트 레이아웃과 의미·콜백 |
| Python | 연결10/릴리스14/게시31 통과 | 합성 설정/DER/HTTP/공개 Firestore 쿼리/비밀정보 제외/connected 차단 |

반복 실행을 고유 테스트 수나 실기기·Google 성공 횟수로 합산하지 않는다. [구조화 결과](android-verification.json), [빌드](build-verification.json), 테스트 로그를 함께 보관한다. 로그는 줄끝 공백/마지막 빈 줄만 정리했다. 실제 Android SDK 테스트는 `demo-runmoni`의 로컬 Auth/Firestore에만 연결했다. 닉네임/메시지는 검증용이며 Google 계정은 사용하지 않았다.

## 화면 검증 한계 — 실제 관측

**첨부 native/320dp PNG에는 Android의 `System UI isn't responding` 대화상자가 겹쳐 있다.** 이는 실제 전체 기기 캡처이며 깨끗한 상용 화면·실제 휴대폰 출력·조작 성공의 자료로 사용하지 않는다. System UI/Quickstep ANR를 Runmoni 앱 ANR라고 판정하지 않았고 원인은 미확정이다. Compose 의미/레이아웃/콜백 검사가 통과했어도 OS 창 입력이나 전체 화면 가용성을 증명하지 않는다.

Firebase 전 실제 MainActivity 캡처2회와 최종 기본 Firebase 경로의 재시도1회는 시스템 대화상자 때문에 설정 버튼을 찾지 못했다. **실제 공유 시트 개방은 미확인**이다. 이 결과를 보고서에 false로 기록했다. UI 공유 콜백과 allowlist 문자열은 각각 UI/JVM에서 별도로 확인했다.

전용 AVD는 KVM 없는 TCG `Runmoni_Cloud_29`1개(PID234249)였고 글씨1.0·원래 밀도160·애니메이션 설정 복원 뒤 종료했다. Auth/Firestore 서버는 `emulators:exec` 종료로 정리했다. 사용자 PC AVD·외부 터널·방화벽 예외를 사용하지 않았다.

## 남은 실제 연결

현재 Firebase Android 설정 파일과 관리 계정이 없고 GitHub 설정 관리 API는403이다. [소유자 콘솔 설정](../../FIREBASE_COMMUNITY.md) 후 현재 보관 인증서의 연결 audit→구성 포함 서명 APK→실제 휴대폰2대의 공유 방/Google 로그인/야외 기록/GPX를 확인해야 한다. 운영 규칙 게시·색인 생성·익명/Google provider·운영 쓰기·실제 휴대폰/Google 성공은 이 결과로 대체하지 않는다.

기존 공개0.2.0 APK와 출처/피드/운영 대시보드는 그대로다.0.2.1의 설정 누락 상태를 연결 완료된 새 공개 APK로 배포하지 않았다.
