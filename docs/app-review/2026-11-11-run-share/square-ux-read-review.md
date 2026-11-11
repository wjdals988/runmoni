# 정사각형 기본값·위젯·지도 읽기 검토

`/root/run_share_audit`의 읽기 전용 검토다. 제품/기존 테스트 수정, 빌드, Android/ADB, 네트워크, Git은 실행하지 않았다. Root가 1:1 변경을 작성 중이므로 이 문서는 최종 소스/실행 승인이 아니다.

## 기본값과 복원

- `RunShareDraft` 저장은 enum ordinal이 아닌 name/valueOf다. 기존 POST/STORY 이름을 유지하고 SQUARE를 추가하면 예전 두 비율의 복원 계약은 유지된다.
- `RunShareCardTest.pinchKeepsTheTouchedPhotoPointAnchoredAndUsesTheClampedZoom`은 기본 Draft와1080×1350 하드코딩이 묶여 있다. POST를 명시하거나 모든 계산을 original.ratio에서 유도해야 한다.
- Controller 기본 이미지 기대 높이1350은 새 기본1080으로 바꿔야 한다. ReceiverJava 허용 높이에1080도 포함해야 한다. 실제 Main driver는 9:16 텍스트를 명시적으로 선택해 순서 의존이 없다.
- 새 1:1 출력은 고정1080×1080이고 편집 화면의 OS fontScale과 별개다. 기존 큰 글씨 UI 검사를 줄이지 않는다.

## 정사각형 기록 위계

거리 큰 첫 줄과 시간/평균 페이스 두 열을 유지하고,1:1에서만 panel의 여백·거리 크기·baseline을 줄이는 방식이 최소 변경이다. 세 지표를 같은 한 줄로 압축하면 긴 시간/페이스 가독성이 줄어든다. 캡션 입력60 codepoints를 보존하고 실제 StaticLayout 높이에 따라 글자 크기/여백을 조절한다. glyph fallback 높이를 보존하며 임의 잘림으로 공간을 확보하지 않는다.

TOP/BOTTOM×날짜/닉네임/중단구간×60한글/60emoji에서도 header와 panel이 겹치지 않고 미니미/사진 영역이 충분해야 한다. `max(1f, availableHeight)`가 원화를1px로 줄여 통과한 것처럼 보이지 않도록 실제 출력/원화 픽셀 보존 검사가 필요하다. 원화 색·팔다리·비율을 바꾸지 않는다.

## 지도 현재 소스

MapControlBar는 지도 밖 별도 행으로 출처와 `지도 조작` 하나를 배치하고, permanent +/- 및 floating 버튼 대신 drag/pinch와 접근성 액션을 유지한다. MapOptionsMenu는 lifetime/request와 최신 enabled/actions로 오래된 item/dismiss 콜백을 차단한다. 현재 읽은 코드에서 추가 P0/P1은 찾지 못했다. 320/font2에서 출처+버튼 줄바꿈·48dp와 실제 지도 잔여 높이는 Root의 native 검증이 필요하다.

## 위젯 현재 소스

RunWidgetViews의 크기 후보 실측, 사용자 글자 크기 보존, 텍스트 line/ellipsis 검사,48dp,runId가 들어간 PendingIntent와 살아 있는 서비스 제어 경계는 현재 소스에 남아 있다. 시각 리소스의 최종 변경 완료를 받은 뒤 재검토해야 한다. 이 읽기는 RemoteViews/실제 Launcher 실행 성공이나 실기기 제어 검증을 뜻하지 않는다.

## 최신 변경 완료 뒤 최종 읽기

Root 요청으로 SQUARE 추가 뒤 domain/renderer/UI saver/Receiver 허용 치수와 위젯12개 동결 파일 및 지도 quiet toolbar를 다시 읽었다. 이 범위에서 추가 P0/P1은 찾지 못했다. 아래 내용은 실행 측정이 아닌 소스 확인이다.

- SQUARE1080 기본과 POST1350/STORY1920 이름을 확인했다. DraftSaver는 여전히 이름으로 복원하고, Receiver는 세 높이를 허용한다.
- Square는52px 좌우 여백,104/144/184px 메타정보 영역과 compact stats baseline을 사용한다. 거리 큰 첫 줄과 시간/페이스 두 열을 유지한다. 캡션을36px로 그리되2줄 초과 시32px로 다시 측정하고60 codepoints를 자르지 않는다. layout cache key에는 width/height/캡션/배치/메타정보/중단여부가 들어간다. gesture clamp와 render가 같은 layout을 사용한다.
- 미니미는 stats 밖에 배치하고 승인 입력의 투명 여백만 자른다. nearest와 원래 aspect ratio·입력 bitmap 불변 경계는 유지된다. 실제 square의 남녀·긴 캡션·TOP/BOTTOM 출력은 새 native 검사로 확인해야 한다.
- 위젯 새 리소스는 명확한 거리/시간 위계, idle의 빈 metrics/기준시각 숨김, live에서만 speed 노출을 사용한다. 원래 후보별 실측·fallback·fontScale·48dp·runId 제어를 유지하고 데이터/provider/cache schema를 바꾸지 않는다. surface/primary 색은 리소스로 모으고48dp action 스타일을 유지한다.
- 지도는 픽셀 밖 출처/조작 한 행과 메뉴,drag/pinch/접근성 액션을 유지한다. 메뉴의 latest actions/enabled/retry와 lifetime/request 검사가 남아 있다.

실제 네이버 서비스 가입/키 등록이나 온라인 SDK 실행을 확인하지 않았다. CI의 선택적 NAVER_MAP_CLIENT_ID 전달과 안내 문서만으로 서비스 활성화를 주장하면 안 된다. Root만 native/전체 검증을 실행한다.
