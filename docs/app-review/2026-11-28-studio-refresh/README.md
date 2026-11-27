# 런모니 0.10.0-dev 화면 검토

API 29 에뮬레이터에서 새 빌드의 홈과 나이트 방풍 셸을 착용한 옷장을 촬영했다. 기록 상세, 공유 편집, 320dp·200% 글씨의 러닝 화면은 검토용 데이터를 사용한 Android 화면이다.

| 화면 | 확인한 내용 |
| --- | --- |
| [홈](home-main.png) | 미니미와 현재 위치 날씨 진입, 러닝 준비, 최근 기록이 없는 상태 |
| [옷장](closet-night-main.png) | 야간 방풍 셸 착용과 선택 상태, 상의 섬네일, 색상 설정 |
| [기록 상세](studio-record-share-411-1.0-fixture.png) | 거리·시간·페이스, 공유 진입, GPS 경로가 없는 기록 |
| [러닝 중 · 320dp·200% 글씨](studio-run-320-font2-fixture.png) | 첫 화면의 거리와 일시정지·종료 조작 |
| [공유 편집](studio-share-editor-fixture.png) | 카드 미리보기와 비율·스타일 선택 |

Android 렌더러로 15종의 착용 셀 240개와 원단 색상 셀 144개를 검증했다. 탐색·날씨 검사 26개가 통과했다. 공개 APK는 기존 배포 인증서로 서명했으며, 서명 CI의 JVM 267개와 릴리스 린트 오류 0개를 확인했다. 휴대폰 검증 전 개발 프리뷰다. [착용 비교와 Aseprite 원본](../../design/avatars/atelier-v3/README.md) · [서명 CI](https://github.com/wjdals988/runmoni/actions/runs/46029745465) · [릴리스](https://github.com/wjdals988/runmoni/releases/tag/v0.10.0-dev)
