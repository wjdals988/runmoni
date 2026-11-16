# 0.7.1-dev 공개 완료

<!-- verified-pixel-couture-release-0.7.1-v1 -->
기존 보관키0.7.1-dev/code30을 개발 프리뷰로 공개했다. 실제 APK 출처는 `cb5b6cb3d70bb56e355bc45a8dc007617a3fa867`, 172,525,552bytes, SHA256 `32604fb58617c477cfdda6fff58676cb09301facb4d83e0b87fc7aa3c4cf671c`다. 앱PR60·서명CI43840377625·대시보드https://github.com/wjdals988/mydashboard/pull/31/main `f1640182cd2ea1cbcb08508a026bc024bc7e2456`와 운영13항목/새MainPNG4/새Node23개를 확인했다. APK 본문을 다시 내려받거나 공개3자산을 교체하지 않았다. 후속 문서·피드 커밋으로 APK 출처C를 바꾸지 않는다.

Pixel Art With Code·frontend-design으로 캡/바이저의 머리 앞 가림·접점과 원근 장비, 세트·양말 소재를 다듬었다. 원본 얼굴·체형·8프레임·58ID/13슬롯/9탭·무료/지갑/보유·키/Rules는 보존한다. 곰은 단순화한 후드/운동복 재질이며 미승인 수영복을 포함하지 않는다.

새 로컬JVM223/lint오류0·경고66, 실제 연결 서명CI 새JVM223/lint오류0·경고97를 구분한다. 최종input3 Android4+12와 별도cold중복1, 이번input1 share6 소스동등성보존을 포함한 범위22를 같은APK22나 이전0.7 재사용으로 부르지 않는다. 실제Main9 원본/선택·저장/Root검토·원래 데이터·소유 환경 복원과 AVD3 PID17380 정확 종료를 확인했다. v2시각거절·v3챙잘림·초기스레드/준비포함6초실패·intentional-loading검사상호대기·ar6관측기형식실패와parentfalse를 보존한다. 새 준비30초+재생6초 검사를 초기6초 성능 성공으로 바꾸지 않는다.

휴대폰·Google 로그인·운영 쓰기·야외GPS/절전/OEM/API31+·실제live위젯/알림·SNS 전달·공개 서명APK 런타임과 사용자 시각 승인은 별도다. canonical raw피드는 이 문서/피드 병합 뒤 전체 바이트로 독립 확인한다.

[공개 검증](connected-release-public.json) · [실제 C/6맵 비교](release-source-comparison.json) · [운영 검증](dashboard-production.json).

아래는 단계별 원래 검토 기록이다.

# 미니미 착용·도트 검토 · 2026-11-16

현재 후보는0.7.1-dev/code30이다. 공개0.7.0의 실제 APK출처ca54251a780f56b60aaecce0dbeb94a4f33061ed는 새게시검증전까지 유지한다. [설계/적용스킬](../../design/avatars/PIXEL_COUTURE_20261116.md).

- hostv1은 Root의 비교 도구에서 BufferedImage의 ARGB 타입 인자를 빠뜨려 컴파일이 실패했다. 제품 코드 오류/검사 성공으로 표시하지 않는다. 당시 소스·로그를 보존한다.
- hostv2는 수치 검사96head/48costume/288gear를 통과했으나 Root·디자인 검토자가 cap의 실린더/내부균열·Santafez·네모곰귀를 시각적으로 거절했다. 자동 성공은 시각적 승인이 아니다.
- source 남자F0 cap crown ROI의 기존과 동일하게 남은 짙은245픽셀이 모두alpha17..191이라는 진단을 보존한다. 명시hair영역·독립facegate 안의 그 반투명머리만 새천alpha253으로 메우며 원본PNG/몸/얼굴/그밖의alpha는 그대로다.
- hostv3는 낮고 둥근 남자cap·접힌Santa/둥근pom·roundbear귀, 스포츠이어폰shape를 반영한 새검토다. 역시96head/48costume/288gear를 새실행했고 source불변과 explicithead/body보호·8pose를 확인했다. 시각검토/실제Android/공개서명은 별도다.

- hostv4는v3 뒤 발견한 여자F1/F5 챙 끝의 x247수직 절단을 보정했다. 16셀cap/visor에서 x247변경픽셀0이며 head96/costume48/gear288 새 실행을 확인했다. Root의 실제 원본/작은 출력 검토는 사용자 승인과 구분한다.
- input3는input2와 제품 APK의 SHA까지 동일하며 테스트만 교정했다. 새JVM223/lint오류0·경고66. [원래 input1 실행 실패](INPUT1_RUNTIME_REVIEW.md)·[input3 검사 경계](INPUT3_TEST_BOUNDARIES.md)를 보존한다. 새 Android 결과는 실제 원문/proof로 별도 기록한다.

## 원본 출력

[새비교](host-contact-v4.png)·[80/112/140px 밝음](host-small-light-v4.png)·[어두움](host-small-dark-v4.png)·[cap16셀](host-cap-v4.png)·[장비16셀](host-gear-v4.png). 모두 HOST JVM production pixel code의 원본출력이며 Android스크린샷이 아니다. 각round의 소스snapshot/hash와 검사 원문을 보존한다.

아래 실제 Android 결과를 확인했다. 새서명APK·운영대시보드는 후속 단계다. 원본/58ID/13슬롯/9탭/무료/성별·지갑·키·Rules는 보존한다. 수영복/실제휴대폰·Google·운영저장과 사용자시각승인은 별도다.

## 최종 로컬 Android 검사

input3의 완전한새phase ar7 loading4 +ar8 kit4/bounded1/identity·playback7=새고유16을 확인했다. 별도cold ar5의clock1은같은메서드이므로중복합산하지않는다. [범위22](android-scope-summary.json)는이16과이번0.7.1 input1에서실행한share6의구체적소스동등성보존을합한것이며같은APK22개/이전0.7성공재사용이아니다. 두최종phase는설치pair/전후·복원뒤6맵·데이터/환경복원을모두확인했다. ar6의제품raw16OK/관측기형식실패/aggregate12·missingAfter·parentfalse는[별도보존](AR6_OBSERVER_EXCEPTION_REVIEW.md)하고최종22에포함하지않는다.

실제Main live1 전체범위가완료됐다. 새 원본9장(홈·프로필IME·남녀cap/visor·남자Santa·여자bear/golden)을Root가직접열고개인정보/픽셀을검토했다. 선택상태·저장ID/성별·무료/기존지갑·보유유지를확인했다. 검토용가명·합성5.24km기록/사진준비fixture이며사진적용/기록공유/지도동선을새로실행하지않았다. 원래DB/XML/계정·관리환경복원을확인하고전용AVD3 PID17380을정확히종료했다. [시각검토](root-main-visual-review.json)·[복원/종료](session3-cleanup.json). 실제휴대폰·공개서명APK런타임/사용자승인은별도다.
