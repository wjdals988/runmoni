# 0.3.3-dev 기본 러닝복 색상 실행 검토

2026-11-04 한국 시간. 클라우드 전용 `Runmoni_Cloud_29`, Android API29/AOSP/TCG, 로컬 TEST 서명 debug 검토다. 공개 보관키 APK·실제 휴대폰·Google·운영 Firebase 쓰기 결과가 아니다. 새 색상 결과의 사용자 시각 승인은 대기 중이다.

## 빌드와 데이터

- 오프라인/작업자1 debug 빌드·JVM81개(실패/오류/skip0), lint오류0/경고34. [최종 소스/바이너리](debug-build-verification.json), [초기 소스/바이너리](initial-debug-build-verification.json).
- Python 서버43개 통과/기존 Starlette 경고1,4.29초. nested 색상 부분 PATCH/기존 장비·성별 요청 호환을 검사했다. 운영 서버·실제 HTTP 세션 경합 검증은 아니다.
- [Host blue/purple/gold16셀 접촉표](host-blue-purple-gold.png)는 실제 Java 색상 함수의 호스트 출력이며 Android 화면이 아니다.
- Host216조합×16셀=3456사례의 실제 Java 팔레트 검사2.827241161초. [결과](host-palette-verification.json). 마스크 밖/명시 보호영역/alpha 변경0과 다른 영역·입력 배열 불변. 반복 픽셀 검사 수를 사람/고유 픽셀 수로 표시하지 않는다.

## Android 실행별 기록

| 실행 | 결과 | 범위 |
|---|---|---|
| [initial-data](initial-data.json) |10개 통과,6.217초|새 색상 저장/손상/구버전 호환4 + 기존 성별/기록/지갑6|
| [initial-renderer](initial-renderer.json) |7개 통과,85.283초|새raw/display/색상/캐시4 + 기존 공유 표시3|
| [initial-ui](initial-ui.json) |3개 중2개 통과/1개 실패,71.549초|실제 문구는 정상이나 테스트의 부분 문자열 옵션 누락; 아래 최종 실행으로 재확인|
| [initial-native](initial-native.json) |1개 통과,55.885초|실제 시스템320dp/글씨200%, ComponentActivity 옷장 fixture|
| [initial-main](initial-main.json) |1개 실패,115.309초|준비 중에도 enabled인 텍스트 자식만 보고 비활성 부모를 누른 이른 탭|
| [final-ui](final-ui.json) |3개 중2개 통과/1개 실패,88.005초|선택3부위/프로필 보존 단언 뒤, 로딩 문구 교체 중 텍스트 검사 노드34→33 변화|
| [final-main](final-main.json) |1개 실패,89.424초|준비 완료 뒤 XML의 설명 노드와 클릭 노드가 분리되어 복합 선택자가 대상을 찾지 못함|
| [final2-ui](final2-ui.json) |3개 통과,84.962초|로딩 완료 후 같은 글자 잘림 검사·3부위 선택/기존 데이터 보존·작은 화면/오류 재시도|
| [final2-native](final2-native.json) |1개 통과,47.742초|실제 시스템320dp/글씨200%·선택/스크롤/미리보기·글자 잘림 검사|
| [final2-main](final2-main.json) |1개 실패,120.125초|실제3색 UI/기기 저장/상의 실제hue·남자사진 통과 뒤 텍스트 기반 홈 이동 검사가 실패; 앱 오류/busy는 없음|
| [final3-main](final3-main.json) |1개 실패,99.339초|저장/선택3색은 일치하나 미니미로딩 중 실제hue 대기가 timeout; 외부화면 polling 중 Compose 검사시계 진행 누락을 보강|
| [final4-main](final4-main.json) |정상 개수/시간 집계 없음|검사시계 진행 경로에서 SnapshotStateObserver의 멀티스레드 관찰 예외로 프로세스 종료; raw를 보존하고 순수 Android 입력으로 재검증|
| [final5-main](final5-main.json) |1개 실패,116.406초|이미 선택된 상의 탭의 접근성 동작 부모/선택 속성 조회에서 멈춤; 정상 finally의 현재 기준 프로필 복원과 이전 크래시는 구분|
| [host-main](host-main.json) |외부 입력 첫 시도 중단|일시적인 XML ParseError로 중단, 현재 시도 전 프로필 복원 확인. JUnit 통과로 합산하지 않음|

초기 데이터/렌더/시스템 큰 글씨 실행 후 앱 코드 변경은 없으며, test APK의 선택자·준비 대기·실패 진단만 수정했다. 첫 Main 실패 후 실제 MainActivity를 수동으로 열어 마스크 준비 완료 뒤 mint 선택/LocalWallet 저장 성공을 확인하고 원본0으로 복원했다. 두 검토자가 준비 중 부모 비활성/텍스트 자식 enabled 상태를 대조했다. 최종 검사는 인위적인 Compose 검사시계를 제거하고 실제 Android 입력과 Choreographer로 실행한다. 색상 이름의 가장 가까운 동작 부모와 활성 상태를 확인하고, 선택된 탭은 재클릭하지 않으며 전체 내비게이션 영역·유한 카테고리 스와이프를 사용한다. 준비 완료와 저장 후 선택 표시도 기다린다. 실제 AppViewModel/LocalWallet과 픽셀·화면·재실행 검사를 유지하며 주요 색상 선택을 VM 직접 호출로 대체하지 않는다. 마지막 상의0 저장 확인만 VM 호출이며 원본색 UI 선택은 별도 옷장 검사에서 확인한다. 글자 잘림 검사는 미니미 준비 완료 후 같은 기준으로 수행한다.

초기 boot의 검은 화면과 앱 설치 전 System UI 응답 대기 팝업은 [사전 관찰](prelaunch-observation.json)에 구분했다. 최종 부팅에서도 같은 시스템 팝업/입력 대기가 나타났으며 [최종 사전 관찰](final-boot-observation.json)·[설치 시도](final-install-attempts.json)에 남겼다. 첫 검사 APK 설치는 호스트120초 timeout 뒤 구검사본SHA가 남아 실행하지 않았고, 화면 준비 뒤 push 방식으로 설치 성공/새SHA를 검증했다. 설치 후 깨끗한 launcher를 직접 확인했으며 이를 Runmoni ANR이나 근본 원인 해결의 증거로 표시하지 않는다.

## 화면과 경계

`initial-ui-*`와 `initial-native-*`는 컴포넌트 fixture 캡처다. `*-male-*-closet/home`·`*-female-*-closet/home`은 실제 MainActivity·AppViewModel·LocalWallet의 UI 조작으로 수집한다. 이미지 이름과 raw 보고서를 함께 대조하며 호스트 출력/fixture를 실제 MainActivity 화면으로 바꾸어 표시하지 않는다. Activity/VM 재실행은 전체 프로세스 재시작이나 휴대폰 재부팅과 다르다.

승인 원본SHA f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65는3사본 모두 유지한다. 새 의상 형태/수영복/58종 원화 완성과 사용자 시각 승인은 별도다. 실제 Google·운영 두 기기 쓰기·실기기/야외 GPS·다른 기기 전체 데이터 복원은 이번 실행으로 확인하지 않았다. 새 Rules/서명키/Secret 변경은 없다.

final4는 실제 검증 프로세스 종료로 기록한다. 새 원본 프로필 백업이 없어 크래시 전 이름/성별/색상 복원을 확인하지 못했고 추정해 덮어쓰지 않았다. [복원 경계](crash-fixture-restoration-boundary.json). 이후 검사는 변이 전 원문 필드와 존재 여부를 비공개 앱 캐시에 백업하고 정상 finally 또는 다음 검사 시작에서 복원한다. 이전 크래시의 미복원과 새 기준 프로필 복원 성공을 합산하지 않는다. 실제 휴대폰/운영 데이터 변경은 아니다.

외부 입력 최종 검증은 `host-main-final` 원문/사진으로 별도 기록한다. 현재 실제 남자 mint/coral/blue 옷장·홈, 여자 purple/blue/gold 옷장을 직접 수집했고 실제 상의 hue 표본430/386픽셀을 확인했다. 호스트가 그림을 생성한 것이 아니라 ADB 입력으로 실제 MainActivity를 조작하고 raw Android PNG를 수집한 것이다. 실제 프로세스 종료·새 PID와 재시작 후 여자4/3/5·상의386픽셀을 확인했다. 마지막 복원 성공은 최종 JSON 완료 후 기록하며 JUnit1개 통과로 계산하지 않는다.

이번에 새로 작성했으나 채택하지 않은 Main JUnit은 [실행했던 마지막 소스](failed-main-instrumentation.kt.txt)와 실패 raw/해시를 보존하고 컴파일 대상에서 제외했다. 기존 테스트를 제거한 것이 아니며 실제 앱 통합은 외부 입력 검증으로 대체했다. 기존 자동 Android 데이터10/렌더7/UI3/시스템 큰 글씨1의 실제 실행 APK 참조를 새 컴파일 바이너리로 바꾸지 않는다.

## 외부 입력 최종 완료와 정리

[host-main-final](host-main-final.json)은 **통과**했다. 실제 Android 입력·MainActivity/VM·기기 저장과 남녀 두 조합의 실제 옷장/홈5장을 확인했다. 남자 상의 hue430픽셀, 여자386픽셀, 실제 프로세스 종료 뒤 새PID4481→6333과 여자 색상/실제 상의386픽셀 유지, 이름·잔액·보유권·장비 불변과 현재 시도 전 프로필 복원을 확인했다. JUnit1개 통과로 합산하지 않는다. [재현 절차 원문](host-input-driver.py)은 실행한 소스와 SHA가 같으며 비공개 baseline은 포함하지 않는다. 이 스크립트는 고정된 검토환경/버전과 별도 검토 이름을 사용하며 이전 증거를 덮어쓰지 않는다.

[남자 옷장](host-main-final-male-mint-coral-blue-closet.png) · [남자 홈](host-main-final-male-mint-coral-blue-home.png) · [여자 옷장](host-main-final-female-purple-blue-gold-closet.png) · [여자 홈](host-main-final-female-purple-blue-gold-home.png) · [실제 프로세스 재시작 후](host-main-final-female-colors-after-process-relaunch.png). Root/독립 검토자가 남녀 화면에서 눈에 띄는 얼굴·피부·비율·alpha·밑창 변색을 찾지 못했다. 사용자 시각 승인은 별도다.

[정리](cleanup.json): 전용AVD153313을 확인해 종료했다. 현재 시도 전 프로필/설정 복원과 이전 final4크래시 직전 원문 복원 미확인은 구분한다. 새 Main JUnit은 이번 미커밋 시도만 제외했고 기존 Android 검사들은 유지했다. 최종 컴파일을 진행하되 기존 실행 보고서가 가리키는 실제 검사APK를 바꾸지 않는다.

최종 컴파일(새 Main harness 제외)도 오프라인/작업자1로50초 성공했다. JVM81개/실패·오류·skip0, debug lint오류0/경고34이며 App APK2b4b1f…는 동일하다. 최종 instrumentation APK는 컴파일만 했고 설치/실행하지 않았다. 기존 통과 raw는 각각 실제 실행했던 초기/최종2 검사 APK를 계속 가리킨다. [최종 컴파일](debug-build-verification.json)·[호스트 실행 당시 소스](final5-debug-build-verification.json)·[호스트 당시 설치 해시](final5-installed-verification.json).
