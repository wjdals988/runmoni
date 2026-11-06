# 전체 UI·백그라운드 러닝 최종 검토

2026-11-06 검토 묶음. 최종 호스트 실행 종료는 **2026-11-07 05:55:04 한국 시간**, 대상은 `0.3.7-dev` / code19다. 현재 공개 APK `0.3.6-dev`와 별도의 로컬 TEST 서명/Firebase 미설정 debug 검증이며 새 서명 CI/공개 APK의 출처는 이후 실제 자산에서 확인한다. [실행별 원문·사진](app-review/2026-11-06-ui-ux-pro-max/README.md).

## 사용자 문제와 적용 결과

- 과도한 pill과 약한 구획을 공통8dp 행동/입력·12dp 표면·제목/본문 폭·구분선·관련 항목 간격으로 정리했다. 녹색·한글 시스템 글꼴·승인 미니미를 유지하고 광고식 카드/새 폰트/가짜 기록을 추가하지 않았다.
- 홈 시작/진행 기록, 크루→소속 파티→채팅, 미니미→옷장과 설정/앱정보의 단계를 유지한다. 오류/로딩/정상0건·검색0건을 구분하고 버전/레드닷/확인 후 다운로드·계정/동의/삭제 의미를 보존했다.
- 작은 화면/글씨200%·가로·키보드에서 닫기·마지막 품목/색상·고정 러닝 조작과 수치를 읽게 보완했다. 이전 실제 활성 러닝 가로 P1을 발견해 compact 화면의 chrome·지표 크기·본문 공간·5탭 배치를 수정했고, 전체 텍스트/viewport 검사를 추가했다.
- 화면에서 시작한 위치 FGS·기기 Room 기록과 알림 일시정지/재개·현재 runId 확인 후 종료·유효 좌표 속도·중단 복구를 검증했다. 새 백그라운드 위치 권한·배터리 예외·외부 터널·유료 서버·서명키를 추가하지 않았다.

요청한 [ui-ux-pro-max](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill)의 고정 revision과 native/Compose 지침을 읽고 실제 검색했다. 자동 스타일 추천2개는 런모니 맥락에 맞지 않아 채택하지 않았다. 기존 awesome-design-md 원칙과 함께 적용한 [MASTER](../design-system/runmoni/MASTER.md)·[출처/검색/판단](design/ui-ux-pro-max/README.md)을 따른다. 경쟁 앱 직접 사용성 실험이나 측정된 만족도 개선으로 표시하지 않는다.

## 최종 검증

build3에서 JVM115개를 새로 실행해 실패·오류·skip0, debug lint오류0/Fatal0/경고39를 확인했다. 최종 build4는 제품 APK/제품 소스가 같고 네이티브 검사 기대값 한 블록만 바뀌었다. JVM 작업은 `UP-TO-DATE`로 build3의 실제 JVM/lint 근거를 유지했으며115개를 다시 실행했다고 하지 않는다. 역할 대비12조합은 별도 수치 근거다.

제품 메서드는 build3의 기본56+실제 native4+가로2, build4의 수정 대상 native1으로 **63회**를 채택했다. [검사 전용 변경 계보](app-review/2026-11-06-ui-ux-pro-max/hierarchy-native-test-lineage.json)로 같은 제품과 서로 다른 두 test APK를 연결한다. 한 pair에서 native5개를 한 번에 통과한 것으로 표시하지 않는다. 관측 보조 메서드·이전 BG 후보/부분 통과21·42·40회·별도 smoke1·시도5의 raw6/실패 parent counter0은 합산하지 않는다.

원래 native 실행은 첫4건 통과 뒤 미준비 hair2/socks2 안내를 없다고 기대한 마지막 검사에서 실패했다. 제품의 기존 선택 보존/정직한 안내를 바꾸지 않고 그 기대값을 수정해 대상1건만 다시 검증했다. 원래 parent는 false로 보존하며 [실패 뒤·수정 전 소스 확인](app-review/2026-11-06-ui-ux-pro-max/native1-post-source-verification.json)을 실패 순간의 측정이라고 하지 않는다.

가로 `landscape4`의 제품 검사 통과와 원래 종료 복원 실패는 별도다. 원문 passed=false를 보존하고 [landscape4-post-restoration.json](app-review/2026-11-06-ui-ux-pro-max/landscape4-post-restoration.json)의 이후 통제된 복원을 확인했다. 추가 제품 검사는0개이고 원래 디스플레이 파일 바이트/존재 여부까지 같다고 주장하지 않는다.

실제 Main13장과 활성 러닝 세로/가로2장은 [host-review6](app-review/2026-11-06-ui-ux-pro-max/host-review6.json)의 build4 pair에서 촬영했다. 최종 [host-review8](app-review/2026-11-06-ui-ux-pro-max/host-review8.json)는 같은 pair와 원문 SHA를 확인해15장을 명시적으로 가져왔고13장 새 촬영·새 OS조건 전환으로 표시하지 않는다. 원본20장의 루트 시각 확인과 UI13/활성2의 완료 근거를 유지하며, 전체 실패인 host-review6의 BG 성공을 가져오지 않았다. 이전 host1–5의 실패/검은 프레임/host2의8장 재사용도 이13장의 새 근거로 삼지 않는다.

host-review6에서 OSdark 설정의 밝은 앱 테마·실제320/font2·가로, 설정 이름 초안의 키보드 표시/숨김·초안 유지/미저장, 앱 정보 창의 문제 신고까지 스크롤·닫기·바깥 탭 닫기를 확인했다. 실제 Main13장의 `modal` 사진은 생성/확인 폼이 아니다. 생성 폼 검사는 기본56의 별도 ComponentActivity fixture와 구분한다. 실제 Main의 노출 bounds 측정과 별도 native fixture의 자연 텍스트 크기 검사도 구분한다.

가로 지표 fixture4장은 루트와 독립 검토자가 직접 보았고 각 목표 값/단위·고정 두 조작·5탭·OS 바의 가림을 확인했다. 이들은 ComponentActivity/fake callback 화면이며 실제 Main/FGS 사진이 아니다. 실제 Main/SystemUI의 합성 GPS BG **2시나리오**는 host-review8에서 새로 실행했다. 화면 끔 진행·알림 pause/resume/content 이동·강제 종료/새 프로세스 복구와 구간 거리/저장을 확인했으며 홈/옷장 대표 사진은 host-review6, 러닝/알림 대표 사진은 새 host-review8의 출처를 따른다. 자동 재시작·야외 GPS·Doze/OEM 성공을 뜻하지 않는다.

## 보존·실패·미확인

실패·중단·P1 시각 거절은 실행 문서에 원문 그대로 보존했다. host-review6는 두 번째 종료 알림 뒤 SystemUI shade가 열린 채 저장 확인창을 관찰하지 못했고 원인은 미확정이다. 제품을 바꾸지 않고 수집기에 제한된 activity 응답 확인을 추가한 뒤 BG만 새로 실행했다. 옷장30초 준비 실패나 검은 프레임의 원인을 단정하지 않고 노출된 bounds를 완전한 가독성으로 바꾸지도 않았다. 소스 Before/After와 실패 후 측정 시점을 구분한다. 원본/최종 호스트 각각의 원래 앱 XML/프로필·DB 내용과 소유한12설정키/WM·night mode·권한/flags·관찰 전원 상태를 복원했으며 무관한 OS 자동 변경이나 전체 OS 복원을 주장하지 않는다.

승인 원본3사본 SHA256 `f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65`와58ID/9분류/13슬롯·준비36(기존13+추가23)/미준비22·0P·성별·기존 보유/포인트/저장을 보존한다. 사용자 시각 승인은 **false**다. 실제 휴대폰·Google·운영 쓰기·야외GPS·Doze/OEM·장시간 배터리·Android12 이상 실행과 공개 서명 APK 실행은 미확인이다. 새 공개 APK의 sourceRevision과 서명 CI/자산 검증은 별도이며 현재 공개 `0.3.6-dev` 출처 `d27b31f85eafa42eb4c96fe17ff2483536d919f0`와 과거 APK 기록을 재작성하지 않는다.
