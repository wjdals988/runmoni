# 첫 진입 선택 실행 검토 — 2026-11-06 한국 시간

`binary-build2.json`의 최종 소스·앱/검사 APK 해시를 설치 해시와 대조했다. 로컬 TEST 서명/Firebase 미설정 debug이며 공개 서명 APK의 실행 검증은 아니다. API29 AOSP, 소프트웨어 TCG, 프로젝트 전용 `Runmoni_Cloud_29` 한 대에서 순차 실행했다.

- JVM97개: 실패·오류·skip0. debug lint0Error/0Fatal/38Warning.
- `review1.json`: 저장4·진입 UI4·시작2·설정/업데이트6·실제320dp/글씨200%2 = **18회 모두 새 실행 통과**. 각 `review1-*.txt`가 원문이다. 이전 버전 검사나 다른 바이너리 결과를 합산하지 않는다.
- `review1-executed-driver.py`가 실제 실행 원문이다. `review1-installed.json`의 설치 해시 및 `binary-build2.json`의 전체 소스 맵을 대조했다. 비공개 preference XML은 Git에 넣지 않는다.
- 실제 MainActivity의 진입·게스트 후 홈/나·설정/앱 정보·같은 프로세스 복귀·새 프로세스 재실행 홈 **7장**은 프레임워크 포커스/RESUMED 및 촬영 해시를 확인했다. 진입 화면은 기존 데이터 보존을 위해 로컬 검토용 선택 상태를 주입했다. 물리적인 새 설치나 Google 인증 성공 사진이 아니다.
- 실제 게스트 버튼으로 선택 저장, 기존 프로필 보존, 같은 PID에서 프로필 복귀, force-stop 뒤 새 PID에서 진입 화면 생략을 확인했다. 종료 뒤 **설치 전 전체 preference XML 및 설정을 정확 복원**했다. DB·Auth를 초기화하지 않았다.
- `review1-native-*-fixture.png`는 ComponentActivity 검사 fixture다. 진입 화면의 활성 Google 버튼은 fake callback이며 인증 성공이 아니다. 글씨200%/320dp에서 실제 시스템 하단 인셋72px, 조작 영역·글자 넘침·시스템 바 침범 검사를 통과했다.
- `review1-launch-sample-1.png`의 실제 API29 OS 시작 창 로고를 루트와 UI 담당이 확인했다. host 캡처 offset을 시작 시간으로 측정하지 않았다. API31 종료120ms는 구성값이며 API31 실행 결과가 아니다.

초기 build1은 `assertDoesNotExist`의 잘못된 top-level 검사 import에서 실패했다. 해당 import만 제거한 뒤 build2가 성공했고 제품 소스는 두 빌드 사이에 동일하다. 실행 전 System UI 응답 없음에는 정확한 Wait1회를 선택했다(`environment-setup.json`). 접근성 null root는 fresh tree로 재시도했으며 최종 실행18회는 모두 통과했다. 이를 앱 ANR 원인 규명이나 해결로 표현하지 않는다.

`visual-inspection.json`은 루트/서브에이전트의 검토 기록이며 사용자 시각 승인이 아니다. `owned-avd-stop.txt`에 전용 AVD 종료를 기록했다. 실기기·실제 Google·운영 쓰기·Android12이상 실행은 미검증이다. 승인 원본3장/렌더·58ID/보유권/포인트는 변경하지 않았다.
