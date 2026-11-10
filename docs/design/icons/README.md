# Runmoni 아이콘 정리

2026-11-10 한국 시간. 하단 탐색 5개 아이콘과 런처·단색 런처·러닝 알림 심볼을 정리한다. 기존 경로와 두 지점의 브랜드 의미, 녹색 배경, 승인 미니미 원본은 유지한다.

## 적용 기준

- 하단 아이콘은 24×24 viewport, 2dp 윤곽선, round cap/join으로 통일한다. 같은 탐색 계층 안에 채운 머리와 얇은 윤곽선을 섞지 않는다. 기본 잉크는 `#192D25`이며 실제 선택/비선택 색은 기존 Compose tint를 사용한다.
- 홈은 집, 탐색은 펼친 지도, 러닝은 달리는 사람, 크루는 두 사람, 나는 한 사람이다. 크루의 기존 작은 곡선들을 명확한 두 번째 사람의 머리·어깨로 바꾸고 러닝의 팔을 몸과 연결했다.
- 24dp는 그림 크기다. 조작 영역은 기존 NavigationBar의 최소 48dp와 보이는 한글 라벨·선택 semantics를 유지한다. 텍스트와 함께 있는 아이콘에는 중복 접근성 이름을 추가하지 않는다.
- 런처는 기존 경로 심볼의 곡선·끝점 간격을 정돈한다. 108 viewport의 중심 `(54,54)`과 반지름33인 adaptive safe circle 안에 그림을 둔다. 시작 원의 가장 먼 점은 약32.04, 도착 원은 약31.96 단위로 계산되며 이는 수학적 도형 경계이지 Android 실행/마스크 검증 결과가 아니다.
- 일반 런처와 단색 런처는 같은 path와 stroke를 사용한다. 일반 런처의 도착점만 기존 앱의 제한된 라임 강조 `#D5ED98`를 사용한다. 단색 버전에는 Android가 themed tint를 적용한다.
- 알림은 이전 R 문자 대신 같은 경로/지점 심볼의 흰 alpha mask를 사용한다. adaptive padding 없이 24 viewport에 맞추고 작은 표시에서 시작 원의 선만 보강한다. 불투명 배경·컬러를 알림 small icon에 넣지 않는다.
- 런처 리소스를 공유하는 시작 화면·앱 정보·입장 화면도 같은 새 심볼을 사용한다. 새 이미지·폰트·아이콘 라이브러리나 외부 SVG path를 도입하지 않는다.

## 스킬과 출처

기존에 받은 [ui-ux-pro-max SKILL.md](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill/blob/b7d5353e0130feb8c5574ef1251f8a1db1873ccb/.claude/skills/ui-ux-pro-max/SKILL.md), 같은 revision의 [native pro-rules](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill/blob/b7d5353e0130feb8c5574ef1251f8a1db1873ccb/.claude/skills/ui-ux-pro-max/references/pro-rules.md)와 [기존 적용 기록](../ui-ux-pro-max/README.md)을 읽었다. native pro-rules의 벡터·선 두께/스타일 일관성·Android 48dp·맥락별 아이콘 semantics를 적용한다. 이는 새 공식 Android 규격이나 측정된 사용성 개선을 뜻하지 않는다.

실제 로컬 검색은 아래와 같다. 스크립트/CSV를 재설치하거나 네트워크를 사용하지 않았다.

| 질의 | 결과·채택 |
|---|---|
| `consistent icon stroke --domain icons` | 0개. 추천을 만들어 기록하지 않음 |
| `one visual family --domain icons` | 좁힌 재시도도 0개. 선형 스타일 기준은 위 native pro-rules의 명시적 규칙을 따름 |
| `icon button accessible label --domain icons` | 3개. 최상위 `icon-context-accessibility`의 맥락별 이름·선택 상태·한 시각 계열 원칙을 채택. React/Phosphor/Heroicons import나 다른 스타일의 색·폰트 추천은 Android 코드에 복사하지 않음 |

좌표는 이 저장소의 기존 도형을 편집하거나 직접 작성했다. 제3자 SVG의 path를 복제하지 않았고 새 런타임 의존성도 없으므로 라이브러리 라이선스 목록을 늘리지 않는다. Aseprite 제작이나 새 미니미 원화로 표시하지 않는다.

## 확인 범위

작성 에이전트는 XML을 정적으로 읽고 크기·선·path 일치를 확인했다. 빌드·JVM/Android 테스트·에뮬레이터·실기기·새 API의 themed icon 실행은 수행하지 않았다. 루트의 실제 빌드/Android 실행 결과를 별도로 따른다. 작은 크기의 상태 표시줄/알림과 원형·사각 런처 마스크, 밝고 어두운 배경, 글씨 확대 시 라벨과 48dp 터치 영역은 실행 검토 대상이다.

무거운 작업은 루트가 순차 수행한다. ignored `work/icon-system/preview.svg`는 XML path를 그대로 읽어 만든 호스트 도형 검토 자료이며, 실제 Android 앱 스크린샷·시스템 알림·기기 마스크로 표시하지 않는다. 프리뷰를 사용할 때 출처를 그대로 적는다.
