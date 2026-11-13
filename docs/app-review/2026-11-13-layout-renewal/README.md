# 0.5.0-dev 화면 구성 개편 검토

2026-11-13 한국 시간. [우선순위·이유](../../../design-system/runmoni/pages/layout-renewal.md). 사용자 요청에 따라 정보 구조·화면 구성 자체를 개편하고 같은 키 개발 프리뷰 APK/대시보드/피드까지 진행한다.

현재 소스 후보이며 새 빌드·Android 실행·실제 Main 원본 화면·서명/게시 검증은 각각 후속 근거로 기록한다. 이전 버전의 성공을 새 실행으로 합산하지 않는다. 승인 원화·58ID/13슬롯/9분류/0P·데이터·FGS/runId·위치 공유 동의·프로필 ACK·공유 생명주기와 출력 방어를 유지한다.

공개0.4.4 APK의 실제출처는7d921417e63d41bac5942645f2d2a50f4e1d308a다. 새 문서/디자인 커밋으로 바꾸지 않는다. 실기기/Google/운영쓰기/SNS/사용자시각승인과 클라우드 TEST실행을 구분한다.

## 로컬 빌드

source-freeze1/binary-build1은 첫 후보의 JVM189/lint오류0·경고57 결과다. 상호검토에서 저장오류/권한안내가 지도 뒤로 밀릴 위험을 찾아 지도앞으로 복원했다. 최종 source-freeze2/binary-build2는 새 JVM189(실패/오류/skip0),lint오류0·경고57과 TESTdebug 앱/검사APK를 확인했다. 이전 입력의 성공을 합산하지 않는다. Android/서명/게시/운영은 후속 실제결과로 기록한다.

클라우드 전용 API29/명시TCG 부팅에서 시스템UI 대기 관측이 있었으며 제품 성공으로 세지 않는다(boot-observation.json). 첫 build1의 Kotlin daemon 시작 재시도 메시지는 원문에 보존했고 최종 Gradle 태스크 성공과 구분한다.

## source2 Android 실패와 복구

android-review1은 일반검사27개 부분통과 후 ConsumerClosetTest의 HomeClock observer 정리에서 main-thread 예외를 관측했다. Root는 추가 source2검사를 중단(SIGINT)했고 closet-navigation 중단은 별도 제품결함으로 세지 않는다. 원문/실패JSON은 덮어쓰지 않았다. input2-recovery.json에서 원래XML/프로필/account/소유환경과복원후6맵 일치·이전PID없음·활성qemu없음을 확인했다. 홈시계는 STARTED의 repeatOnLifecycle 수신기수명/Main.immediate로 수정하며 기존분틱/시간대변경 갱신은 유지한다. 새 source3의69개는 동일 selector를6일반+2native 배치로 실행하며 이전27개를 합산하지 않는다.
