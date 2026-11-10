# 지도·날씨·프로필·팝업 검토 근거 — 2026.11.10

<!-- runtime-validation-v1 -->
## 검증 완료 범위

문서 생성 시각: 2026-11-10T20:31:37.863241+09:00 (한국 시간). 새 서명 CI·공개 APK·대시보드 결과는 이 로컬 검증 문서의 범위에 포함하지 않는다. APK 출처 커밋은 실제 게시 근거에서 별도로 확정한다.

|범위|실제 입력/결과|해석|
|---|---|---|
|JVM/lint|build4 새 JVM 164개, 실패·오류·skip 0개, lint 오류 0개/경고 45개|build5/8은 같은 JVM 결과 재사용이며 새 164회가 아님|
|첫 Android|android-review1.json 전체 실패, 선행 6그룹 23개 통과 보존|실행 당시 원문·APK 쌍·복원을 보존하며 수정하지 않은 기능의 적합 범위에 한정|
|두 번째 Android|android-review2.json 전체 실패, 선행 3그룹 13개 통과 보존|실패한 크루 그룹 안의 부분 3개는 통과 합계에 넣지 않음|
|새 Android|android-review5.json 새 16개 통과|상품 APK는4/5/8이 비트 동일, 테스트 APK는 각각 다름. 단일 설치 쌍에서 새 52개 실행한 결과가 아님|
|추가 실패와 독립 재현|android-review3.json 전체 실패 후 같은 binary8 설치쌍의 android-review4.json 단독 1개 통과|단독 실행에서 예외 비재현. 새 16개 통과 수에 더하지 않음|
|세로 화면 조건|native1.json 실제 세로 320dp/글씨 2배 2개 통과|OS/IME 안전영역과 고정 닫기 동작·검사 소유 환경 복원 확인|
|가로 화면 조건|landscape1.json 실제 가로 짧은 변 320dp/글씨 2배 제품 UI 1개 통과|제품 JUnit UI 1개 통과, 원래 전체 driver는 false 유지. 별도 복원 근거로 회전 정책/정리 범위만 확인|
|SDK|sdk1.json 닉네임 demo Auth/Firestore1개|실제 Google 계정/운영 서버/위치 공유 운영 성공이 아님|
|Rules|demo-runmoni160개 통과|JVM·Android와 별도이며 운영 규칙·색인 게시를 뜻하지 않음|
|실제 Main|live1.json 읽기 전용 동선·사진4장·DB/XML/프로필/통제 환경 복원|Firebase 비활성 TEST debug·앱 전용 제공 CA/프록시·합성 GPS, 실기기/공개 APK 실행 아님|

앱 APK SHA256 `94ad940181699408a755ad432365d5b1c7a21a1b906fc5523540fbfcb7696f75`, 현재 테스트 APK SHA256 `251bb491944d91159e71497dae288a874ce66776f80a6182e87439b626bedc1f`. root의 미커밋 입력 parent `642fccc053657742fcf8a28f6be3de38bbe898f0`는 실행 당시 기존 Git 상태이며 새 공개 APK 출처가 아니다. 현재 여섯 source map의 실행 전후 일치, 각 단계의 실제 설치 APK 쌍과 해당 빌드 메타데이터 SHA 연결을 확인했다. source4/5/8 빌드 메타데이터·source-freeze·원문 로그와 source5의 변경 전 검사 파일 세 개를 보존한다.

초기 컴파일의 AccountCircle import 누락과 AndroidTest assertDoesNotExist import 누락, build1 lint3 오류, build2 테스트 isToggleable import 실패를 보존한다. build3 성공은 이전 JVM 재사용이다. 첫 Android의 ConsumerClosetTest는 저장 후 부모 화면이 Settings로 돌아오는 새 계약 대신 배경 nav_me를 기대해 실패했다. source4→5는 해당 검사 파일 하나만 수정했다. 두 번째 Android는 옷장 1개·정보 8개·기록 4개 뒤 크루 그룹에서 로그인 콜백이 null로 관측되어 전체 실패했다. 키보드 입력 후 스크롤 본문의 연결 버튼까지 이동하지 않던 옛 검사에 scrollTo와 표시·활성 확인을 추가하고, 가로 검사의 스와이프 대상도 단일 modal_scroll_body로 지정했다. source5→8은 CommunityRoomsTest/RunmoniUiTest/ModalDismissUiTest 세 파일만 바뀌었고 제품 코드는 바뀌지 않았다. 두 실패 원문·전체 false·원래 XML/프로필/검사 소유 환경 복원을 유지했다. 중간 준비 Python 구문 실패는 변경 전 중단됐고 build6의 무변경 UP-TO-DATE 실행은 새 검증으로 집계하지 않았다. build7은 빌드만 끝난 뒤 AVD 시작 전 정적 검토에서 구형 파티 취소 태그가 발견돼 Android 검사를 실행하지 않았다. source8은 실제 삭제 콜백·X 취소 시 삭제 미호출·부모 스크롤 복귀·확인 후 삭제를 검증하도록 해당 검사만 보완했다. 빌드 반복·보존 23개·보존 13개·새 16개·native·SDK·Rules 숫자를 합쳐 단일 최신 실행 수로 쓰지 않는다.

추가 source8 실행 android-review3.json은 크루 4개 중 1개에서 LifecycleRegistry.removeObserver의 main-thread 예외가 났다. 원문에는 Compose 테스트 TestMonotonicFrameClock/ApplyingContinuationInterceptor에서 CoroutineScheduler.Worker로 이어지는 apply/onDispose 경로가 있으며, 같은 설치 APK 쌍의 독립 android-review4.json에서는 해당 메서드 1개가 통과했다. 두 실행의 실제 JSON·원문 SHA와 XML/프로필/통제 환경 복원을 확인했다. 이 근거만으로 운영 앱 버그를 확정하지 않으며 단독 조건에서 재현되지 않았다고 기록한다. 제품의 Handler 보정이나 코드 변경은 하지 않았고 처음 실패도 보존한다. 단독 1개는 최종 전체 16개의 성공으로 합산하지 않는다. 실패 JSON SHA256 `e582e18cf693fbd4d445a5514877af9ac81b45ce4e811813705d1ea787a7d0f5`, 원문 `android-review3-crew-party.txt` SHA256 `e1257fdc36aab40fbe36d186b32a20d8e76e97a37411570556b9a0da88333f82`. 독립 JSON SHA256 `1888ff249addab00dce72dae3e9f8a5647cbf042cc4c7322a66303de4345db08`, 원문 `android-review4-crew-clock-repro.txt` SHA256 `ca8a99bcb107523df66c72fb51a649f0420e7e58b95ee799c4fa0ab8e0530f19`.

가로 landscape1.json은 실제 짧은 변 320dp/글씨 2배에서 제품 JUnit 1개가 통과했지만 전체 driver는 실패로 유지한다. effective false는 같았으나 persisted 회전 정책이 원래 default 대신 disabled로 남았으며 XML/프로필/나머지 통제 환경 복원은 통과했다. 첫 IWindowManager helper는 컴파일됐지만 API29에 필요한 int,int setter가 없어 NoSuchMethodException으로 실제 호출 전에 실패했다(runtimeSupportedOnApi29=false). 그 시도를 동작한 복원 도구나 제품 APK 변경으로 취급하지 않는다. 다음 rotation-recovery2.json은 소유 AVD의 Android 프레임워크를 멈추고 fixedToUserRotation 속성 하나를 원래 값으로 돌린 뒤 재시작해 캐시를 다시 읽었으나, 이후 Launcher 접근성 관찰이 TimeoutExpired로 끝나 전체 false를 보존한다. 최종 P3 landscape1-manual-restoration.json은 그 P2 실패 JSON SHA에 연결해 새 프레임워크 재시작/정책 수정/DB 쓰기 없이 원 소유 설정 복원과 독립 확인을 수행했다. 실제 원래 persisted default/effective false·앱 XML/프로필·통제 환경·P2 원 baseline과 같은 DB 행·동일 설치 APK 쌍을 확인했고, 앞선 수정 시점의 파일 소유권/mode·나머지 바이트 보존과 실제 새 framework PID 근거도 유지했다. rotation-recovery3-installation.json은 그 확인의 설치 SHA/정책/source map 관측을 별도로 묶은 근거다. 제품/UI 검사를 재실행한 결과나 가로 전체 driver/P2 성공으로 바꾸지 않으며 source8의 고유 가로 제품 검사 1개로만 집계한다. 전체 OS 바이트 동일성·사용자 데이터 초기화·APK/네트워크 변경을 주장하지 않는다. P2 전체 실패 JSON SHA256 `1c7ef3c0d2af018496e65a6913d1778d85e1be28223ce98c017254b757e9a6cd`, P3 복원 sidecar SHA256 `13ece35c50b39fe6fda62ee1cf10e546218878c7b65adb7838e931f8df22f1fc`, 설치 확인 SHA256 `68230b5a92f6c86e758a67800e7457021df8a010017e8963af14c7dc31e9eded`.

실제 Main에서 네이티브 MET 예보 출력·상단 시각/기온, 탐색 지도와 출처·크루 목록 제거, 같은 프로필 편집 진입, 앱정보 X 뒤 원래 Settings/스크롤 복귀를 확인했다. 날씨와 지도 사진은 출력 근거이며 새 HTTP 상태를 측정한 결과가 아니다. 호스트의 MET HTTP200 자료와 기기 앱의 실제 출력은 별개다. 사진 시각은 Android 기기 설정 시간대 기준이며 한국 시간 실기기 관측으로 바꾸지 않는다.

루트가 독립 manifest에 직접 보기·개인정보 검토 완료를 명시했다. 사용자 시각 승인은 아니다.

### 실제 Main 사진

- [live1-home-weather.png](live1-home-weather.png) — 실제 MainActivity/API29/TCG, 서울 공공장소의 합성 GPS
- [live1-explore-map.png](live1-explore-map.png) — 실제 MainActivity/API29/TCG, 서울 공공장소의 합성 GPS
- [live1-unified-profile.png](live1-unified-profile.png) — 실제 MainActivity/API29/TCG, 서울 공공장소의 합성 GPS
- [live1-app-information.png](live1-app-information.png) — 실제 MainActivity/API29/TCG, 서울 공공장소의 합성 GPS

### 실행과 검증의 경계

- 자동 UI 사진은 ComponentActivity·합성 상태/콜백·inert tile fixture이며 실제 Main과 구분한다. 관찰용 UIAutomator 호출은 제품 검사 수가 아니다.
- 실제 Main은 계정 로그인·러닝 생성/DB 쓰기·운영 공유 없이 서울 합성 GPS를 사용했다. Google·휴대폰·두 실기기 공유·운영 쓰기·Android31 이상·공개 서명 APK 실행·사용자 시각 승인은 미검증이다.
- 복원은 앱 전체 XML/프로필·검사 소유 설정/권한/flags·관측 전원과 읽기 전용 DB fingerprint 범위다. 전체 OS 파일 바이트 동일이나 자동 시스템 변경의 복원을 뜻하지 않는다.
- 원본 GPS/개인 계정/비공개 XML/원본 DB 사본은 WORK에만 두고 공개하지 않는다. 날씨 위치는0.01도, 공개 러너 위치는 사용자가 선택한0.001도 격자이며 서로 다른 기능이다.
- 새 Firebase 규칙·색인은 검증된 파일의 운영 게시가 필요하다. demo160/SDK1 결과를 소유자 콘솔 게시·운영 인증/쓰기로 바꾸지 않는다.
- 세션 프록시 정리와 전용 AVD 종료는 별도의 실제 루트 관측으로 남긴다. 이 생성기는 종료/정리 성공을 만들지 않는다.
