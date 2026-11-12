# Runmoni 전체 화면 UI 개편 검토

2026-11-12 한국 시간 후속 상태: 같은 키0.4.3-dev/code25 APK와 운영 대시보드를 공개·검증했다. 실제 APK출처는 `eb32a30d20b0223d436e80ca09c7c17e25d60c69`이며 [게시 검증](../../RELEASE_REVIEW_0.4.3_20261112.md)을 따른다. 아래 미게시·진행 중 문구는 당시 검토 이력이다. 업데이트 피드의 canonical bytes는 문서 PR 병합 뒤 별도 확인한다.

사용자 요청의 frontend-design/ui-ux-pro-max를 적용한 미게시0.4.3-dev/code25 후보다. 폴더 날짜는 작업 시작 UTC 기준이며 재개일은2026-11-12 한국 시간이다. 구현·로컬 실행·실제 서명 APK·운영 다운로드를 구분한다. 공개 다운로드는 검증한0.4.2/code24를 유지한다.

## 방향과 적용

[화면 계획/자체 비평](../../../design-system/runmoni/pages/studio-refresh.md)을 따른다. 홈/프로필은 승인 미니미와 정적 트랙, 러닝은 실제 기록과 명확한 제어, 탐색은 넓은 지도, 크루/파티는 사람과 일정의 읽기 쉬운 계층으로 구성했다. 로그인·옷장·설정·프로필 양식·기기 점검·앱정보/내역/라이선스·확인창도 정리했다. 공유 진입에는 눈에 보이는 ‘공유’ 이름을 추가했다.

frontend-design은 anthropics/skills의 실제 pinned 원문을 읽고 주제 기반 계획→비평→구현을 적용했다(`frontend-design-source.json`). ui-ux-pro-max 고정b7d5353의 실제 검색 원문은 이 폴더에 있다. 시스템 검색의 AI/보라·영상 홍보/CTA 반복 추천은 제품 맥락과 달라 채택하지 않았다. disclosure 재검색은0결과여서 검증된 DB 일치로 부르지 않으며 native 기본 지침을 보완했다. Compose typography 결과와 기존 native 접근성·안전영역 원칙을 적용했다. 새 시장 조사·사용성 향상 수치·새 Compose 버전 적용을 주장하지 않는다.

공통 토큰과 화면별 구성·글자 위계·새 mint/route blue·자연 높이 탭·단일 조작 영역을 사용한다. 기존 원화/58ID/13슬롯/9탭/0P·기록/지갑/계정·100m 동의·FGS/runId·프로필 ACK·공유 export/lifecycle/API29 창 정책을 유지한다. 새 원화/키/Rules/의존성은 없다. 기존 다크 테마 구현으로 확대하지 않는다.

6개 담당의 소스 상호 검토에서 공통 탭의200% 글씨 고정 높이 위험을 발견해 자연 높이 selectable tab으로 보완했다. 소스 검토는 실행 검증이 아니다. 전체 페이지와 주요 상태의 검사 후보는 `qa-inventory.md`를 따른다.

## 보존된 빌드 결과

`source-freeze1.json`/`binary-build1.json`: 오프라인·작업자1로 앱/검사 APK, 새 JVM189(실패/오류/skip0), lint 오류0/경고57을 통과했다. JVM task 자체를 다시 실행했고 로그와 보고서 생성 시각을 확인했다. 앱241404003bytes·검사5522084bytes이며 실제 SHA와 source103/test88/resources29/avatar83/approved1/config7을 보존했다. TEST debug/로컬 검토 CA·Firebase 비활성이며 공개 보관키 APK가 아니다. 빌드 당시 HEAD를 이후 커밋으로 덮어쓰지 않는다.

`contrast.json`: 텍스트·조작 경계16조합을 검사했다. 전체 화면의 모든 픽셀 대비나 사용자 시각 승인으로 확대하지 않는다. 승인 원본 SHA f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65는 그대로다.

## 환경 중단과 재개

최초 클라우드 AVD 시작 뒤 exec-server 연결이 끊겼고 usable environment가 없다는 오류로 설치/검사 수집기를 시작하지 못했다. 원인은 미확정이다. 재개 시 파일/6개 소스맵/빌드 기록이 같음을 확인했고,남아 있던 정확한 소유 AVD PID887490을 확인해 종료했다(`resume-observation.json`). 이 중단을 Android 검사 실패나 전체 복원 완료로 표시하지 않는다.

준비된 검사 수집기의 두 APK guard가 method count 치환에 함께 바뀐 문제는 실행 전에 원래2개 조건으로 바로잡았다. 제품 수정이나 실행 실패가 아니며 원래 설치 SHA 검사를 완화하지 않는다.

## 검증/배포 경계

`planned-groups.json`의 일반56/native13은 계획이며 실행 수치가 아니다. 후속 원문과 복원 proof가 생성된 뒤 실제 결과만 기록한다. 이전 source12의189/Android7+12+22·Main 공유 단계 묶음/Launcher는 baseline으로 보존하며 이번 UI의 새 실행으로 합산하지 않는다.

Android ComponentActivity/in-memory/inert-map fixture는 실제 MainActivity/휴대폰/Google·운영 쓰기·실제 SNS 전송이 아니다. Root가 실제 원본 PNG를 연 뒤 시각 검토 여부를 기록한다. AVD 설치/검사 뒤 원래 XML/프로필/account·소유 설정/권한·전원을 확인하고 정확한 소유 프로세스를 종료한다. 미확인 상태는 그대로 남긴다.

커밋·푸시·PR은 구현 보존 단계다. 실제 같은 키 서명 CI·APK/sidecar/digest·공개 HEAD가 확인되기 전에는 업데이트 피드/대시보드를 새 버전으로 바꾸지 않는다. 기존 공개0.4.2 실제 APK출처176a9cbe...와0.1.5 출처75c39ca...는 새 UI/문서 커밋으로 바꾸지 않는다.

## 첫 Android 실행과 source2 보완

`android-review1.json`은 source1/binary1의 일반56 계획 중 독립 그룹 기준53개를 통과했고 전체는 FAILED다. main-design 그룹2범위(실제2개 중1개 실패)에서 러닝320/font2 ‘위치 정보 없음’ Text높이0, closet1은기존120초미니미준비대기초과였다. 나머지통과그룹원문을보존하며전체통과로바꾸지않는다. `installation8.json`은템플릿파일명이남은첫교체설치관측으로,실제참조는binary-build1이다(8회설치의뜻이아님).

원래 XML/프로필/account·소유설정/권한/전원을복원하고소유919531을종료했다. 실패phase의원문에없는after source snapshot은끼워넣지않고,편집전Root가전체6맵을다시측정한별도 `source1-post-review-observation.json`에보존했다. 원래driverproof자체를성공으로바꾸지않는다.

Root가원본4장을열어보았고(`root-visual-input1.json`),프로필큰글씨꾸미기문구의마지막글자줄바꿈을확인했다. 홈날씨공간fixture에는정상로딩hourglass가보였다. Stage부모의clearSemantics가하위loadingmarker를숨긴것을소스상호검토로확인했으므로해당통과를sprite준비완료근거로쓰지않고최종갤러리에서도제외한다.

source2는러닝지도fontScale1.3이상에서360dp/기본188dp,큰글씨의‘꾸미기’문구,장식Canvas만clear하는Stage로보완했다. Consumer의120초·전체loading조건과원래예외는그대로유지하고실패후에만노드/조상tag·full/clippedbounds/표시여부·threadstack/PNG를진단하도록했다. 옷장timeout의구체원인은아직미확정이며타임아웃확대·조건축소로통과시키지않는다.

`binary-build2.json`/`source-freeze2.json`:새JVM189·실패/오류/skip0,lint오류0/경고57,앱241404647bytes·검사5527612bytes. source1의189와합산하지않는다. source2 일반13/native13은계획이며실제실행/복원결과를별도로기록한다.

## source2 실행과 source3 보완

`android-review2.json`은 같은source2 APK쌍의 일반13개 모두통과 및전체XML/프로필/account·소유설정/권한/전원복원,실제sourceMapsBefore/After/AfterRestoration과바이너리불변을확인했다. Consumer의원래120초/전체loading조건은그대로통과했으며source1 timeout의구체원인이확정됐다고쓰지않는다.

`native1.json`은실제320dp/systemfont2 및별도480dp/font1/실제IME의13범위에서독립그룹기준9개를통과했다. 대화상자그룹4범위(실제2실패/2통과)는전체실패로보존했다. 현재루트가아닌legacy Toggle만렌더하고dialog_message를찾던동의검사는현재탐색→run/nonce중앙동의창흐름과맞지않는stale fixture였다. 제품에옛Alert를되살려맞추지않는다. 채팅은최신메시지jump후본문assertDisplayed가실패했고실제body/history측정이없으므로공간추정값을실측으로부르지않는다. 두실패모두120초로딩실패와는별개다. 원래입력/환경복원및실제3개source map관측은실패proof에도그대로있다.

source3는큰글씨/짧은창채팅을압축header로표시해본문공간을확보하고메뉴대안을유지했다. 동의Checkbox는문구행전체에서단일toggle을사용하며미체크초기값/수동확인/nonce/run경계를유지한다. Native동의검사는현재root탐색의요청/100m·75초고지/실제scroll/disabled확인/전체48dp안전영역/취소후데이터불변을검증하도록갱신했다. 채팅은최신본문전체bounds와기존IME/Back/메시지전송기준을함께검증한다.

`binary-build3.json`/`source-freeze3.json`:오프라인새JVM189(실패/오류/skip0),lint오류0/경고57,앱241404991bytes·검사5530720bytes. 이전189를합산하지않는다. source3일반2/native4는현재계획이며이후실제원문·복원결과를따른다. 새서명APK/운영다운로드는아직미게시다.

## source3 Android 실행 결과

`android-review3.json` 일반2개와 `native2.json` native4개를 최종 로컬 APK쌍에서 새로 실행해 모두 통과했다. 큰 글씨 채팅 최신 본문의 전체 bounds·기존 메시지 읽기 위치·실제 IME 입력/Back, 현재 루트의 약100m 동의·수동확인·48dp/안전영역·취소 후 불변을 확인했다. 각 phase의 원래 XML/프로필/account와 소유 설정/권한/전원 복원, 전/후/복원후6맵과 APK쌍 불변을 확인했다.

`android-scope-summary.json`은 원문 통과 개수와 실행 selector·소스 method ID를 대조한 중복 제거 결과다. 최종 입력 새6개와 앞선 입력에서 유지한64개, 총70개 서로 다른 통과 method를 구분한다. 70개 모두를 최종 바이너리에서 새로 실행했다고 주장하지 않는다. 첫 전체실패/source2 native전체실패와원문도그대로보존한다. JVM189와lint0/경고57은최종빌드결과이며이전빌드의189를합산하지않는다.

Root가 source2 원본5장과 source3 native target 원본3장을 열어 검토했다(`root-visual-input2.json`, `root-visual-input3.json`). 스크롤/Compose target crop은 전체 화면 검증·MainActivity·휴대폰·사용자 시각 승인이 아니다. source3 실제 Main 검토와 공개 배포는 이후 별도 근거를 따른다.

`scope-root-audit.json`은 별도 재현 검토다. 각 입력과 같은88개 test blob의 SHA를 실제 커밋과 대조하고 fully-qualified class/selector로 method 이름을 다시 읽어6/64/70을 확인했다. 설치APK쌍과각실제source snapshot의6key·개수·matches/빈mismatch도확인했다. 원래scope파일/빌드당시HEAD는고치지않았다. 최초audit의단순class명충돌은도구를package포함이름으로바로잡았으며제품/Android검사실패가아니다.

## source3 실제 MainActivity 검토

`live1.json`은 최종 TEST APK쌍에서 실제 MainActivity의 전체 검토를 통과한 단일 실행이다. 검토 전 원래 기록이 비어 있음을 확인한 홈과 옷장,1:1 기본 공유/선택한 합성 사진9:16/문구와미니미편집/위치확인전탐색의원본6장을획득했다. Root가모두열어개인정보를검토했다(`root-main-visual-review.json`). 합성 완료기록과 합성 사진이며 실제 사용자 러닝/사진/GPS를 대신하지 않는다.

실제OS파일선택·실제키로Morning Miles입력/IME중Back의48dp·안전영역·미니미이동,갤러리저장·실제chooser의별도UID수신기READ와쓰기/다른파일거부,갤러리/cache/receiver PNG전체bytes일치를확인했다. 결과Back과편집재진입의draft도확인했다. 실제SNS전송/휴대폰/Google/운영쓰기/서명APK실행은미검증이다. 지도는조작행이실제mapbounds밖임을확인했으며현재GPS·타일HTTP/콘텐츠·다른러너나새제공자인증은측정하지않았다.

검토용기록/사진/갤러리/cache/수신출력을한정정리하고원래DB행/전체XML/프로필/account와소유설정/권한/전원을복원했다. 전/후/복원후6맵과설치APK쌍·검토도구불변을확인했다. `session-cleanup.json`에서정확한소유AVD934800종료를확인했다. 이전Launcher검토는이전입력baseline이며새전체UI의새Launcher결과로합산하지않는다.

원문 Node coverage log의 두 행은 출력 표 정렬용 끝 공백을 포함한다. 기록한 SHA를 보존하기 위해 해당 `dashboard-node-tests.log`에만 whitespace 예외를 뒀으며 앱/검사 소스 규칙은 그대로다.
