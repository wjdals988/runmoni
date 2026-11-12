# Runmoni 시각 강화 우선순위 — 2026-11-12 한국 시간

frontend-design와 ui-ux-pro-max를 적용한 후속 제안과 첫 구현이다. 현재 시장 점유율이나 실제 경쟁 앱의 최신 버전을 측정한 순위가 아니다. 스킬의 디자인 제안·기존 앱 코드·사용자 피드백을 근거로 우선순위를 정했다.

| 순위 | 영역·적용 방향 | 왜 필요한가 | 이번 상태 |
|---|---|---|---|
| P0 | 미니미 배경에 민트 색면·라임 원·클레이색 러닝 트랙 | 빈 배경을 채우면서 캐릭터와 러닝이라는 주제를 드러낸다. | 공통 미니미·옷장 구현 |
| P0 | 홈 주요 구역에 4dp 색상 띠 | 러닝 시작 영역의 시작점을 명확히 하고 단조로운 흰 화면에 포인트를 준다. | 구현 |
| P0 | 옷장·기록·크루 등의 분류 탭에 선택 색면 | 밑줄만으로 찾기 어려운 선택 상태를 분명히 한다. 기존 밑줄과 selected semantics도 유지한다. | 구현 |
| P0 | 선택 변경에150ms 색상 전환 | 사람이 누른 결과를 알려주면서 레이아웃이나 터치 영역을 움직이지 않는다. | 구현, 시스템 duration scale을 사용하는 Compose API |
| P1 | 완료 기록 상세의 거리·페이스·경로를 한 장면으로 구성 | 사용자가 자신의 러닝 성과를 더 쉽게 이해하고 공유하게 한다. | 다음 묶음; 실제 기록만 사용 |
| P1 | 공유 카드의 매거진·스포츠 넘버 테마 | 현재 제공하는 사진/미니미/1:1 기능을 활용해 게시물 선택 폭을 넓힌다. | 다음 묶음; PNG 가독성과 합성 검증 필요 |
| P1 | 옷장 아이템별 큰 비교 프리뷰와 선택 강조 | 작은 썸네일만 보고 옷을 고르는 부담을 줄인다. | 다음 묶음; 실제 준비된 의복만 사용 |
| P2 | 화면 이동의 연결 모션·축소 모션 대응 | 현재 위치와 뒤로 돌아갈 맥락을 보여준다. | 탐색/Back/IME/접근성 검증 뒤 적용 |
| P2 | 야간 러닝용 다크 테마 | 밤에 사용하기 편하고 지도와 제어를 구분하게 한다. | 별도 전 화면 대비·지도/창·위젯 검증 필요 |

## 1차 설계와 자체 비평

전체를 어두운 바탕/네온/유리 효과로 교체하는 대안은 기존 한글·날씨·지도 가독성과 녹색 정체성을 흐려 제외했다. 기억할 시각 요소는 미니미 주변의 트랙 한 곳에 집중한다. 홈 색띠와 탭은 기능적 계층을 보조한다. 외부 폰트·의존성·새 원화·추가 데이터 수집은 없다.

색은 기존 forest #205641 / mint #E0F0E8 / lime #D7F58A / paper #FFFFFF에 장식용 clay #C97558을 추가한다. Clay는 오류·본문·버튼 경계에 쓰지 않는다. 폰트는 한글 SansSerif 역할 구분을 유지한다. 기존8dp 버튼/12dp 표면, 자연 높이 탭과48dp 조작 영역을 유지한다. 캐릭터 픽셀은 변경하지 않는다. 그림을 실제 GPS 경로나 달성한 성과처럼 표시하지 않는다.

ui-ux-pro-max의 `fitness vibrant mobile` 시스템 결과와 좁힌 `fitness app` 재검색은 둘 다 웹 Feature-Rich Showcase였다. Vibrant & Block-based의 제한된 색면 아이디어만 참고하고 CTA 반복/웹 섹션/오렌지 다크 팔레트/외부 Barlow 폰트는 채택하지 않았다. Compose 검색은 animate*AsState/UI 계층 사용과 맞지만 저장소 Compose를 검색 결과의1.11.4로 올리지 않는다. 시장 유행을 직접 검증한 자료로 부르지 않는다.

실제 검색 원문: [system](app-review/2026-11-12-expressive-track/skill-system.txt), [retry](app-review/2026-11-12-expressive-track/skill-retry.txt), [Compose](app-review/2026-11-12-expressive-track/skill-compose.txt). frontend-design pinned 출처는 [기존 원문 기록](design/frontend-design/README.md), 기존 awesome-design-md 참조는 DESIGN.md를 따른다.

## 검증·게시 경계

이번은 미게시0.4.4-dev/code26 검토 후보다. 공개0.4.3 APK출처 eb32a30d20b0223d436e80ca09c7c17e25d60c69와 공개 피드/다운로드는 그대로다. 빌드·JVM·lint 실제 결과는 별도 proof에 기록한다. 새 Android 실행/작은 화면·큰 글씨/애니메이션0배속/실제 원본PNG 시각 검토·휴대폰·Google·서명 APK·사용자 시각 승인을 추정하지 않는다. 이 단계에서 디자인 실행 승인 완료나 배포 완료로 표시하지 않는다.

1차 로컬 결과: 새 JVM189개(실패/오류/skip0),lint오류0/경고57,debug APK빌드 통과. 선택 배경/글자 endpoint6조합 대비4.5이상. [실제 결과](app-review/2026-11-12-expressive-track/build-result.json). 새 Android/원본PNG/축소모션 검토와 공개 서명 배포는 아직 수행하지 않았다.

교차 검토: 초기 원 장식은 너비만으로 반지름을 정해 가로로 넓고 낮은 영역에서 위로 넘칠 수 있었다. min(width,height) 반지름과clipToBounds로 보완했다. 첫 빌드 성공을 수정 후 검증으로 바꾸지 않고 새 결과를 별도로 기록한다.

색상 전환도 보완했다. 투명 검정과 민트 사이의RGB 변화 대신 같은 민트색에서alpha만0→1로 바꾼다. 중간 프레임의 대비 실패를 실제 측정한 것은 아니며 runtime 검증은 여전히 별도다. 소스 검토 결과는 [교차 검토](app-review/2026-11-12-expressive-track/source-review.json)에 보존했다. 첫 입력·clipping 보완 입력·alpha 보완 입력의 빌드 결과를 서로 합산하지 않는다.

최종alpha 보완 입력도 debug APK/JVM189개(실패/오류/skip0)·lint오류0/경고57을 통과했다. 최종 근거는 [build-result3.json](app-review/2026-11-12-expressive-track/build-result3.json)이다. 앞선 두 입력의 결과는 수정 이력으로 보존한다.
