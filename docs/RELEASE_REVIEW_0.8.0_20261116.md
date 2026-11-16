# 0.8.0-dev 의복·사진 공유·자동 백업 공개 — 2026-11-16 한국 시간

<!-- verified-atelier-release-0.8.0-v1 -->
기존 보관키0.8.0-dev/code31을 실기기 검증 전 개발 프리뷰로 공개했다. 실제 APK출처 `9a0051e28a2ac24badbbf1b57a554a37e4a9a85e`, 172,828,638bytes, SHA256 `5c2c6a12faf75d38f4328046a3a93537472d37842963e4357f0b45fbed62531b`다. 앱https://github.com/wjdals988/runmoni/pull/63·서명CI43886075423·대시보드https://github.com/wjdals988/mydashboard/pull/32/main `c89e16ccd9773594cc7e3fbbf75a203b817a9b4c`와 운영 다운로드/새MainPNG4/새Node23개를 확인했다. 첫 서명CI43883353138/source572939386fcf01c000f4604621e16b7dcc093169는 필수 배포 안내 누락으로 업로드 전 차단됐고, 안내 보완 뒤 실제 새 출처로 다시 서명했다. 첫 전체failure를 새 성공으로 합산하지 않았다. APK 본문을 다시 내려받거나 공개3자산을 교체하지 않았다. 후속 문서·피드D로 실제 APK출처C를 바꾸지 않는다.

frontend-design와 실제 로컬 headless Aseprite1.3.18.6-dev의 소재80셀/프로젝트5개를 적용했다. 새 메시 저지·리브 양말·패널 신발3종과 부위별 옷장, 전체 사진 공유3스타일/독립 배치, Google 요약 자동 백업/Roomv2·고정UID/삭제억제, 무료 지도 정리/반복 오류 안내와 Stride R 앱/알림 아이콘을 반영했다. 원본 얼굴·체형·8프레임·기존58ID prefix/13슬롯/9탭/무료·보유·지갑·키를 보존하며 전체61ID다. Aseprite GUI 손그림·전신 털 인형 원화/모든61종 준비·사용자 시각 승인은 주장하지 않는다. 원본GPS/포인트·옷장 전체 백업과 다른 기기의 전역 삭제 동기화는 별도다. 새3ID Rules는 소유자 게시 완료 보고이며 관리 API/운영 쓰기 확인으로 바꾸지 않는다.

최종 input4 새 로컬JVM248/lint오류0·경고65, 실제 연결 서명CI 새JVM248/lint오류0·경고97를 구분한다. Android 범위86는 input3 보존55+최종input4 새31이며 같은APK 전체나 이전0.7 재사용이 아니다. 원래 false parent와 실패 그룹 부분통과 제외, 실제46dp 버튼의48dp 수정·부분문구 검사 교정, 초기 재생4초 실패/원인미확정과 같은binary3 독립7 통과를 보존했다. 재생 실패를 제품 수정 완료나 초기 로딩 SLA로 바꾸지 않는다. 실제Main 원본7장과 선택/저장·사진Picker/스타일/초안/지도·Root 시각검토·원래 DB행/백업 origin·전체XML/계정·소유 설정 복원 및 AVD4 PID69127 정확 종료를 확인했다.

휴대폰·실제 Google 로그인/자동 백업·운영 프로필/위치공유 쓰기·SNS 전달·야외GPS/절전/OEM/API31+·실제live위젯/알림·공개 서명APK 런타임과 사용자 시각 승인은 별도다. canonical raw피드는 이 문서/피드 병합 뒤 전체 바이트로 독립 확인한다.

[APK 다운로드](https://github.com/wjdals988/runmoni/releases/tag/v0.8.0-dev) · [운영 대시보드](https://coldbrewventi.vercel.app/projects/runmoni).

아래는 이전 구현·검토 시점의 보존 기록이다.

# 2026-11-16 0.8 후보 최종 로컬 검증 완료

input4 새JVM248/lint오류0·경고65, Android input3 보존55+input4 새31=검토범위86, 실제Main2 새7원본/Root검토·원래데이터/백업origin·소유환경복원과 AVD4 PID69127 정확종료를 확인했다. 두 입력/원래false·부분통과제외·초기재생원인미확정/분리7·첫Main관측실패를 보존한다. [검토](app-review/2026-11-16-atelier-refresh/README.md). 새 서명/게시·운영은 후속 단계이며 공개0.7.1 실제출처cb5b6cb3d70bb56e355bc45a8dc007617a3fa867를 유지한다. 기존 PR/main·같은키 개발 프리뷰·대시보드/피드 승인을 이어간다.

# 0.8.0-dev 후보 · 의복·사진 공유·자동 백업

현재 공개0.7.1-dev/code30의 실제 APK출처는 cb5b6cb3d70bb56e355bc45a8dc007617a3fa867다. 이 후보는0.8.0-dev/code31이며 새 서명·공개 검증 전에는 기존 피드/다운로드를 교체하지 않는다.

[후보 구현·검증 기록](app-review/2026-11-16-atelier-refresh/README.md)과 [디자인](../design-system/runmoni/pages/atelier-refresh.md), [실제 Aseprite 제작 경계](design/avatars/v0.8.0/README.md)를 따른다. 새 의복3종으로61ID이며 기존58ID prefix/13슬롯/9탭·무료/보유/포인트와 원본 f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65 및 기존 보관키를 보존한다. 소유자는 새3ID의 Rules 게시 완료를 보고했다. 관리 API나 운영 쓰기 확인으로 바꾸지 않는다.

새 input3 로컬 JVM248개·실패0·skip0, lint오류0·경고65 및 TEST APK pair 생성 완료. Android 실행 검증은 진행 중이다. 이전0.7.1의 실행·CI·이미지를 새 검증으로 합산하지 않는다. 실제 Google·휴대폰·야외GPS/절전/OEM/API31+·SNS 전달·공개 서명 APK 실행·사용자 시각 승인은 별도다.

후속 게시 문서에서는 실제 서명 CI·공개 자산의 버전/크기/SHA256/보관키/실제 출처C, 운영 대시보드의 새 Main 원본4장과 다운로드를 확인한 뒤에만 새 피드를 연결한다. 이 문서의 후보 출처·후속 문서D를 기존 APK출처로 기록하지 않는다.
