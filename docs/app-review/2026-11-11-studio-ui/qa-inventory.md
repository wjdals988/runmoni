# Studio refresh 독립 QA inventory

본 문서는 소스 읽기/검사 준비 기록이다. 빌드·Android 실행·사용자 시각 승인을 수행했다고 표시하지 않는다. QA 담당은 root의 허용 범위 내에서 새 테스트1파일과 이 WORK 문서만 작성했다.

## 신규 의미 검사: StudioRefreshUiTest, 2 methods

1. visibleShareLabelKeepsItsFullHeaderTargetAndOpensTheEditorAtLargeText
   - 실제 root navigation + in-memory canonical 완료 기록/FakeActions.
   -411dp/font1 및320dp/font2 ComponentActivity 제약 fixture에서 보이는 ‘공유’ Text 자손,48dp 버튼·뒤로 전체 measured target/safe bounds/본문 viewport와 비중첩을 확인.
   - 실제 클릭으로 별도 편집→닫기→기록→뒤로→나 탭 왕복, 원래 profile/records·위치·export/backup 불변을 확인.
2. largeTextPrimaryJourneysRemainReachableWithoutChangingRecordsOrLocationConsent
   -320dp/font2 ComponentActivity fixture에서 홈 시작64dp→러닝 탭,프로필 꾸미기56dp→옷장→닫기,크루 검색/생성·목록,탐색 지도 조작/위치·공유 target 도달 확인.
   - 모든 hit target의 원래 position+size를 ancestor scroll/safe bounds와 대조. 미리 clip된 bounds만 보고 통과하지 않음.
   - 지도 조작 버튼이 측정 지도 픽셀 영역 아래에 위치하는지 확인. consent/run start/crew join/export를 실행하지 않음.
   - inert map tiles·synthetic fixture이며 MainActivity/실기기/Google/운영 백엔드/사진 저장 증거가 아님.

## 페이지/주요 상태별 소스와 기존 검사 후보

|페이지/상태|소유/확인 소스|새 실행할 기존 검사 후보|유지할 의미|
|---|---|---|---|
|첫 로그인 Google/게스트·진행/실패|AppEntryScreen|AppEntryUiTest,AppEntryPersistenceTest|완료된 기기 선택/인증과 러닝 복구 우회 구분|
|홈 정상/날씨 없음·실패·지역 불일치|RunmoniDestinations,HomeConditions|HomeWeatherLayoutTest,HomeWeatherUiTest,CurrentAreaUiTest|예보/현재지역/이전정보 구분,제공자/시각 고지|
|전역 헤더/업데이트 레드닷/5탭|RunmoniApp,AppInformation|RunmoniDesignTest,AppInformationTest|레드닷 비중첩/실제 버전/확인 후 다운로드|
|탐색 게스트/연결·오류/빈·실제 공개 러너|RunnerDiscoveryScreen,MapPane,RouteTileMap|RunnerDiscoveryUiTest,RouteTileMapUiTest|100m consent/만료/self 필터/지도 출처·manual camera|
|러닝 idle/running/paused/recovery·저장/알림 오류|RunmoniApp,RunPersistenceNotice|RunmoniDesignTest,RunReadinessTest,RunReliabilityUiTest,RunNotificationUiTest|고정 조작/FGS/runId·복구·내구성 경계|
|크루 내 목록/둘러보기/검색/오류·생성|CommunityRooms|CommunityRoomsTest,HierarchyClosetUxTest|크루 최상위·파티 소속·서버 동의/오류 유지|
|크루 상세/멤버/나가기·부모 Back|CommunityRooms|HierarchyClosetUxTest,CommunityRoomsTest|권한/부모별 실제 소속·Back 단계|
|파티 상세/일정/생성/참가/삭제|PartyDetailScreen,RunmoniDialogs|HierarchyClosetUxTest,RunmoniDialogDesignTest|날짜/시간 validation,가입/삭제 확인·원래 입력|
|채팅/공지/새 메시지/IME|RunmoniChat|RunmoniDialogDesignTest.arrivingChatDoesNotMoveReadersAwayFromOlderMessages|새 메시지로 기존 독자 스크롤 강제 이동 금지|
|나/마일리지/기간 이동·기록|RunmoniDestinations|StudioRefreshUiTest,RunRecordNavigationUiTest|기간/합계는 실제 저장 기록 기준|
|옷장/기본형/재생·마지막 품목/색|AvatarClosetScreen,RunningKitColorControls|ConsumerClosetTest,ClosetNavigationUiTest,RunningKitColorsUiTest|58ID/13슬롯/9탭/0P·보유권·미준비 경계·하단 접근|
|기록 상세/경로·삭제/요약 백업|RunRecordScreen|RunRecordScreenUiTest,RunRecordNavigationUiTest|선택 기록·계정 변경에 기존 action 무효화|
|공유 발견성/독립 편집·Back|RunRecordScreen,RunShareScreen|StudioRefreshUiTest,RunShareUiTest|visible 공유 label·1:1 default·draft/scroll 보존|
|공유 모든 도구/짧은 창·IME|RunShareScreen|RunShareUiTest,RunShareImeUiTest,RunShareControllerTest|API29 window/single scroll·lifecycle/단일 URI READ·동일 PNG|
|설정/기기 점검/프로필 연결|RunmoniDialogs,DeviceChecksScreen,UnifiedProfileDialog|UnifiedProfileUiTest,DeviceChecksTest,PopupNavigationUiTest|profile ACK 대 pending UID·inline retry·원본 기록 보존|
|앱정보/업데이트 내역/라이선스 긴 글|AppInformation|AppInformationTest,ModalDismissUiTest,PopupNavigationUiTest|실제 metadata/닫기48dp/스크롤·소유 inset|
|입력/동의/확인창·키보드·Back/바깥 닫기|RunmoniModal 및 각 caller|ModalDismissUiTest,RunmoniDialogDesignTest|상단 닫기 고정·취소와 확정 구분·오류/필드 유지|
|OS splash/알림·위젯|기존 native XML/provider/service|StartupUiTest/기존 위젯·알림 검사|새 전체 페이지 검증과 선행검사를 합산하지 않음|

## 읽기 상호 검토와 실행 주의

- share 담당이 visible Text('공유')/기존 tag·action snapshot·width<360 또는 fontScale>=1.3의 별도 행을 확인했다. QA는 visual label을 contentDescription만으로 대신 검사하지 않는다.
- home 담당이 모든 기존 태그와 avatar tag의 바깥 Box 배치를 확인했다. decorative clear semantics가 조작/측정 태그를 제거하면 안 된다.
- Record DistanceNumber의 단위 분리 뒤에도 record_distance semantics 문자열은 원래 계약을 유지한다. 긴 실제 거리/큰 글씨의 overflow는 root 실행에서 확인한다.
- 모든 기본 화면을 같은 white card로 만드는 실수를 피하고 실제 중요 데이터/행동의 계층을 유지하는지 root 원본 PNG 비평이 필요하다.
- 이번 변경은 의도된 밝은 테마다. 실제 다크 테마/상위 Android/실기기 검증은 별도이며 완료했다고 쓰지 않는다.
- native IME/landscape/Launcher 검증은 실제 기기 구성으로 실행하는 별도 검사다. 위 virtual 320/font2 테스트를 실제 시스템 글씨 변경이나 회전으로 표시하지 않는다.
- source12의 이전 189/7/12+22·Main 단계 묶음은 baseline이다. 이번 source freeze에 대한 새 실행 결과와 섞지 않는다.

QA 자체 실행 결과: 없음. diff whitespace 검사만 수행. 최종 tests/PNG count는 Root의 실제 실행 proof를 따른다.
