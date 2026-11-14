# 0.6.0-dev 페이지 이동·위젯 개편 검토

현재 후보이며 서명 APK·대시보드에 아직 게시하지 않았습니다. [설계·우선순위](../../../design-system/runmoni/pages/sport-pages.md)에 따라 frontend-design·ui-ux-pro-max 검색 결과의 적합성을 검토하고 일반적인 카드 나열과 장식 문구를 줄였습니다.

긴 설정·정보·프로필·크루/파티 작성은 독립 페이지, 짧은 확인은 대화상자로 구분합니다. 시작 화면과 위젯은 실제 가용 높이에 따라 여백을 나누며 긴 목록은 위에서부터 스크롤합니다. 원화·기존 아이템/기록·서명키·Rules·의존성을 보존합니다.

소스 상호 검토와 실제 빌드/Android 결과를 구분해 아래 자료에 추가합니다. 과거0.5.0의 JVM189·Android69·Main5는 이번 실행으로 합산하지 않습니다. 기존 공개 APK의 실제 출처df09c233f711dc5a0573daf4e030e53af51dddf8도 새 문서 커밋으로 바꾸지 않습니다. 실기기·Google 로그인·운영 쓰기·SNS 전달·새 공개서명 APK 실행·사용자 시각 승인은 별도입니다.

최종 로컬 binary3/source-freeze3: 새 JVM193개, lint 오류0·경고57, debug APK와 test APK를 빌드했습니다. 첫 컴파일의 높이 참조 오류와 두 번째 lint의 위젯 tint 오류를 원문으로 보존했고 최종 입력에서 재실행했습니다. Android90개 선택 계획·실제 Main·Launcher 검증은 아직 실행 전입니다.

입력3 진단 실행: 일반 parent=false/성공 그룹63개와 native parent=false/성공 그룹13개를 보존했습니다. 파티 검사의 실제 스크롤 전제와 폼 IME 저장 버튼의 OS 경계 실패를 구분하며 최종90개에 합산하지 않습니다. 원래 데이터/소유 환경 복원 뒤 AVD47376을 종료했습니다. 입력4는 파티 테스트에서 실제 마지막 부분을 읽도록 하고, 폼 테스트를 Main의 edge-to-edge/resize 설정에 맞추며 판정 전 geometry를 기록합니다. 원래 경계·48dp·시간 제한은 유지합니다. 새 전체 일반75/native15와 실제 Main/Launcher 검증은 아직 실행 전입니다.

입력4에서는 앱과 같은 초기 창 설정에서도 실제 키보드가 저장 버튼을 가리는 것을 좌표와 비공개 실패 캡처로 확인했습니다(764px 대413px). 검사값을 바꾸지 않고 공유에서 쓰던 API29 framework fit/resize를 폼과 공통화하는 제품 수정을 진행합니다. 첫 실패 뒤 나머지 검사는 소유 collector에 SIGINT로 중단했고 원래 데이터/소유 환경 복원 및 AVD57956 종료를 확인했습니다. 입력5는 전체90개와 Main·Launcher를 새로 검증하며 입력4 결과를 최종 성공에 합산하지 않습니다.

## 입력5의 최종 검사 범위와 남은 관측

같은 binary-build5의 일반 ar2는 전체 parent=false이다. 성공한 전체 그룹의72개만 유지하고, 실패한 Consumer1·Colors2 그룹의 부분 결과는 전부 제외했다. 동일 앱·테스트 APK와6맵·assertion·timeout을 바꾸지 않은 단 한 번의 ar3 재검사3개와 native3 전체15개를 합쳐 서로 다른90개 항목의 통과를 확인했다. [정확한 범위](android-scope-summary.json)와 [실패/재실행 진단](input5-thread-diagnosis.json)을 함께 읽는다. 일반75 단일PASS나 최초 충돌 해결을 주장하지 않는다.

원문은 SnapshotStateObserver previousThread247/251과Main2 사이의 동시 관측 예외다. 두 담당자가 제품 Bitmap 작업과 캐시된Compose1.7 캡처·시계 경로를 검토했으나 최초 호출원은 확정하지 못했다. 이 관측은 과거0.5의30초 semantics대기 실패나 이미수정한API29 IME 가림과 별개다. 원래 prefs/profile/account와 소유 설정·권한·전원 상태, 전체6맵·설치pair 복원을 각 실행에서 확인했다. 실제Main·Launcher는 별도 후속 검토이며 아직 이 문단에서 성공을 주장하지 않는다.

## 실제 앱과 런처 후속 결과

최종 [Main live2](live2.json)는 전체 동선과 새 원본7장, [Root 검토](root-main2-visual-review.json)를 확인했다. 첫 Main의 관측 도구 실패·부분6장은 별도 보존하며 재사용하지 않는다. 최종 [Launcher widget2](widget2.json)는 두 OS 배치/font1·font2와 원본2장·[Root 검토](root-widget2-visual-review.json)를 확인했다. widget1의 미완료 상태와 [복원 전용 기록](widget1-interruption-recovery.json)은 런처 검증 성공으로 집계하지 않는다.

[현재 종료 상태 관측](session-cleanup.json)은 일곱 복원 기록과 시작 소유권을 묶는다. 재연결 후 PID61846과 에뮬레이터 프로세스가 없는 상태를 확인했으며, 실제 종료 시각·원인은 미확정이고 종료 신호를 보내지 않았다. 세계 지도 대체/권한·연결 오류 화면은 실제 위치나 정상 타일 네트워크 검증이 아니다. 공개 갤러리는 최종 Main의 홈·옷장·기록·전체 기본1:1 편집4장을 선택한다. 서명·게시·운영은 다음 실제 근거로만 기록한다.
