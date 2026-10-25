# 최종 서명 APK 화면 확인 — 2026-10-25

## 후속 0.1.4-dev — 모바일 위계 재구성

새 로컬 서명본 code5, SHA256 `aaddecd9436c2357d7973f513085553961da3624ccb6d81d4a789184dda8d829`,209946142bytes. `outputs/design-review/20261025/mobile-reset`의 home/explore/run/crew/me/settings/app-information/version-history 8장은 실제 설치된 non-debuggable 서명APK의 ADB screencap이다. launcher-icon.png1개는 같은 앱 리소스를 Android Drawable에서 내보낸256×256PNG이며 화면 캡처 수에 합산하지 않는다. 글씨100%,1080×2400,density420. 이미지 합성 목업 없음.

홈의 주간통계/빈파티/연결상태카드 제거·시작1개, 러닝시작전 지도우선, 크루단일빈상태, 슬림프로필과 하단버전행, 전체화면설정, 달리는 사람 아이콘을 확인했다. 지도SDK 미연결 상태를 가짜 지도로 꾸미지 않았다. 새 캐릭터 원화는 별도 채팅 진행 중으로 이번 APK에 포함되지 않는다. 배치38개 통과는 실제 러닝/GPS/실인증 또는 사용자 취향 승인을 대신하지 않는다.

## 이전 0.1.3-dev — 비교 기준

설치본은 `0.1.3-dev`/code4, 패키지 `com.runmoni.app`, 코드 `e757cb7a3065433a423f64c366d5943f068d8374`다. 실제 ADB screencap 원본을 보존했으며 합성 목업·편집 이미지가 아니다. APK SHA-256은 `d8d64d076e6b19b75888dc7edc90d7a00015280f001e6b4c053bb66e109aa676`이다.

원본 폴더는 작업 PC의 `outputs/design-review/20261025/release/`다. outputs는 Git 제외 산출물이며 아래 파일이 GitHub 저장소에도 올라가 있다는 뜻은 아니다.

| 파일 | 확인한 화면 |
|---|---|
| home.png | 실제 오늘 기록0회·0km, 시작 버튼·도트 미니미·주간 기록 |
| explore.png | 실제 경로/지도 미연결 상태, 탐색 필터·검색. 가짜 러너 없음 |
| run.png | 거리·시간·페이스, 하단 고정 시작 버튼 |
| crew.png | 서버 미연결 안내, 크루·파티 진입 |
| me.png | 월 기록·꾸미기·마일리지 안내 |
| mileage.png | 1km=100P 기본 환산·기기/서버 지갑·백업 제외 범위 |
| app-information.png | 설치 버전·브랜드 아이콘·실제 업데이트 조회 실패/재확인 표시 |
| version-history.png | 설치 버전과 버전별 날짜·변경 내역 |
| support-links.png | 대시보드·GitHub·고지·신고 아이콘 링크 |
| licenses.png | 앱에 포함한 실제 의존성 고지 목록 |
| wardrobe.png | 고정 미니미·분류·아이템·재생 제어 |
| account-settings.png | Google 계정 우선 배치, 로그인 버튼 활성, 요약 백업 경계 |
| wardrobe-320-200.png | 실제320dp·시스템 글씨200%, 스크롤 전 분류 목록 |
| app-information-320-200.png | 같은 큰 글씨 조건의 앱 정보, 내용 스크롤·닫기 |
| app-information-feed-recheck.png | 네트워크 안정화 후에도 실제 앱 피드 조회 실패. 성공으로 바꾸어 표시하지 않음 |
| wardrobe-320-200-product.png | 분류를 위로 스크롤한 후 미니미·상품명·가격·구매 버튼 전체가 함께 보임 |

일반 조건은1080×2400px/density420(약411dp), 큰 글씨 조건은840×1680px/density420(320dp)다. 상품 화면에서 ‘민트 러닝 반팔’은 y804..1030, 가격은 y1051..1150, 구매 글씨는 y1192..1296 안에 보이며 버튼 표면 전체도 viewport 안에 있다. 잔액0P라 구매는 비활성화된다. 화면을 보기 위해 가짜 포인트나 보유품을 주입하지 않았다.

초기 캡처만 보고 큰 글씨 옷장 조작 불가라고 판단한 의견은 소스와 실제 스크롤 확인으로 철회했다. 상품과 분류는 함께 스크롤되고 미리보기는 유지된다. 이 확인을 실제 구매·모든 화면 크기·실기기 접근성 검증으로 확대하지 않는다.

공개 raw 피드는 PC에서 HTTP200/810bytes로 확인했다. AVD 앱은 초기·재확인 모두 실패했으므로 실제 원격 업데이트 동작 완료로 표시하지 않는다. 테스트의 새 버전99.0.0-dev fixture는 이 폴더의 공개 버전 증거에 사용하지 않았다. 실제 공개 APK는0.1.1-dev/code2, 새1.3/code4는 로컬 서명 개발본이다.

계정0개·Google 로그인 버튼 활성화까지만 확인했다. 실제 본인 Google 로그인·Firestore 백업/조회/삭제·계정 전환0회, 야외 GPS·실기기·NAVER 지도 인증·2대 음성/DJ도 미검증이다. 각 순차 확인 후 화면/글씨를 복원하고 전용 AVD를 이름 확인 후 종료했다.
