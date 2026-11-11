# 게시 상태: 0.4.3 후보 구현 완료, 서명 CI 시작 실패

[현재 후보/차단/재개 절차](../../RELEASE_CANDIDATE_0.4.3_20261111.md). PR47은 main에 병합됐지만 두 CI run의 최초/rerun/새 실행이 job 생성 전 startup_failure로 종료됐다. 새 APK·대시보드·preview25 피드는 미게시이며 공개0.4.2/code24와 실제 APK출처176a9cbe...를 유지한다. `publication-blocked-state.json`을 따른다. gallery/context와 후보 C 대조 JSON은 준비 자료이며 새 공개 APK의 실행·게시 근거가 아니다.

# 최종 로컬 검토: source12

최종 입력은 `binary-build12.json` / `source-freeze12.json`이다. 새 JVM189·lint 오류0/경고57, 같은 APK쌍의 새 Android7과 이전 입력의 지도12/독립22를 구분한다(`android-scope-summary.json`). 아래 source5~11의 ‘최종 후보’는 해당 시점의 이력이다.

`main-review-bundle.json`은 단일 실행 통과가 아닌 명시적인 단계 근거 묶음이다. `live8.json`의 공유 동선3장은 OS 사진 선택·실제 키 입력·미니미 이동·저장/공유/별도 UID 수신·복원을 완료했으나 마지막 지도 수집 selector가 실제 ‘세계 지도’를 놓쳐 전체 FAILED를 보존했다. 지도1장은 같은 APK쌍의 독립 `map-live1.json` 성공에서 가져왔다. 같은 설치 SHA·실제 source snapshots·원래 데이터 일치를 검증했으며, 없는 after-map snapshot이나 전체 성공을 만들지 않았다. 저장·캐시·수신 PNG bytes는 같고 SNS 전송 성공은 별도다.

`widget1.json`은 보통 글씨 pin/캡처 후 큰 글씨에서 도구 스와이프가 고정 시작 버튼 영역에 걸려 전체 실패했다. 측정한 본문 scroll viewport 안에서만 스와이프하도록 검토 도구를 수정했다. 제품 변경 없는 `widget2.json`은 보통/200% 글씨에서 독립 OS pin2회·원본 캡처2장을 완료하고 원래 cache/위젯 IDs·DB행·전체XML/profile/account·소유 환경을 복원해 통과했다. 실제 러닝 FGS 제어나 같은 위젯 resize 검증이 아니다.

Root는 Main4/Launcher2 원본 PNG를 열어 내용과 개인정보를 확인했다(`main-visual-review.json`). 사용자 시각 승인은 별도다. `session-cleanup.json`은 복원 후 정확한 소유 AVD PID 종료를 확인한다. 최초 종료 수집기의 substring이 자기 shell 명령도 찾은 문제는 실제 PID/state·실행 파일명으로 재확인했으며 다른 AVD를 종료하지 않았다.

# 현재 후보: source12 / 0.4.3-dev/code25

기본1:1(1080×1080)과4:5/9:16 공유·현재 미니미/사진 편집,밝은 위젯·지도 밖 조작 메뉴를 검토한다. `binary-build12.json`의 새 JVM189·lint 오류0/경고57과 `native6.json`/`android-review8.json`의 새 Android7개를 확인했다. 실제 Main/Launcher·서명/운영 게시 완료는 해당 proof를 따른다. 이전 입력의 검사와 현재 후보의 검사를 합산하지 않는다.

아래는 각 시점의 실패와 입력을 보존한 기록이다.

# 러닝 공유 이미지 검토 — 0.4.3-dev/code25

사용자 요청: 러닝 결과에서 사진·저장된 기록·본인 미니미로 공유 이미지를 꾸민다. 결과→별도 편집→결과의 기존 스크롤을 유지한다. 기본1:1과4:5/9:16, 3색 테마, 사진 crop/zoom, 기록 상·하단/문구/날짜·닉네임, 미니미 표시/위치·크기를 제공한다.

## 출력과 개인정보

미리보기와 export는 동일한 완성 Bitmap이다. 완료된 canonical record의 whitelist DTO만 렌더에 넘기며 거리·active duration·pace·날짜만 들어간다. raw GPS·UID·recordId·포인트·원본URI/EXIF는 카드에 넣지 않는다. 닉네임은 기본 OFF다. 사진 입력은 OS picker이고 broad storage permission 추가는 없다. 공유는 기존 FileProvider의 해당 UUID PNG 한 장의 READ 권한이며 자동 SNS 게시는 없다.

새 renderer는 본인 현재 옷차림의 기존 ApprovedAvatarRenderer 프레임을 빌린다. 승인 원본·의복·58ID/13슬롯/9탭/보유·포인트·키/Rules/과거APK 출처는 바꾸지 않는다. 원본 f9035f29… 해시와 모든 asset을 동결표로 확인한다. 거절된 관절 조립 renderer를 재사용하지 않으며 새 원화 승인으로 취급하지 않는다.

## 설계 참고와 상호 검토

기존 MASTER와 awesome-design-md의 고정 Apple 분석을 참고했다(제3자 분석, 공식 Apple규격 아님). 캐시된 ui-ux-pro-max의 실제 사진 편집/공유 검색 2회는 marketing page/보라색 등의 제품 불일치로 거절했다. drag 접근성 검색의 single-pointer alternative를 적용했고 Compose IME 검색 0건은 검증된 검색결과로 꾸미지 않았다. 시스템폰트·기존녹색·48dp·작은창 단일스크롤 기준을 실제 검사한다.

renderer/파일 IO/실제Main support fixture/UI 테스트/읽기 감사 역할을 나눠 상호 검토했다. Root만 build/AVD/install/instrumentation/network delivery를 순차 실행한다. 캐시 사진 복원 실패 제거·short-window footer·미니미 bounds/drag deadzone·canonical/late callback·gallery cancellation·same-turn edit fence를 보완했다.

## 근거의 경계

- `source-freezeN.json`은 그 시도의 실제 full source maps와 당시 local HEAD를 보존한다. 미커밋 빌드의 당시 HEAD를 새 APK 출처로 고쳐 쓰지 않는다.
- build1/2 컴파일 실패, build3 JVM189 통과/lint 실패의 원문을 보존한다. 시도별189를 합산하지 않는다.
- Android Canvas 출력 fixture는 앱 스크린샷이 아니다. ComponentActivity/주입 UI·실제 MainActivity·시스템 사진 선택기/chooser·별도 테스트 receiver를 구분한다.
- cloud API29/TCG TEST debug 실행은 공개 서명 APK 실행/실제 휴대폰/Google/야외GPS·절전/API31+·사용자 시각 승인을 대체하지 않는다.
- 다른 UID 테스트 receiver가 PNG를 읽어도 SNS 게시 성공은 아니다. 운영 Firestore 규칙/프로필 연결 성공도 이번 범위가 아니다.

실제 실행 결과·원문·복원·최종 배포 출처는 아래에 완료 후 기록한다.

## 로컬 빌드

`binary-build4.json`/`source-freeze4.json`: 앱·Android 검사 APK 조립, 새 JVM189(실패/오류/skip0), lint 오류0/경고57. 오프라인/작업자1이며 production103/test87/resource28/avatars83/approved1/config6의 SHA와 당시 미커밋 HEAD를 보존한다. 이 TEST debug에는 로컬 검토 CA overlay가 포함되고 Firebase는 비활성이다. 공개 서명 APK의 실행 증거로 표시하지 않는다. `installation1.json`은 두 APK install-r와 원래 설정 바이트 보존을 확인하며 uninstall/data clear는 없다. 부팅의 SystemUI Wait1회는 cloud TCG 관측이다.

최종 입력은 `source-freeze5.json` / `binary-build5.json`이다. Android1 실패 뒤 검사1파일만 바뀌어 제품 APK SHA는 build4와 완전히 같다. JVM189는 build4 통과를 재사용하며 build5의 새189개로 세지 않는다(`build5-reuse.json`). AndroidTest lint analysis를 실행했고 최종 lint는 오류0/경고57이다.

## 추가 요청과 최종 검증 입력

사용자는 기본1:1 공유·깔끔한 위젯·지도 위 버튼 정리를 추가 요청했다. `source-freeze7.json` / `binary-build7.json`이 이 범위의 첫 전체 입력이다. product103/test87/resources29/avatars83/approved1/config7을 동결했으며 선택적 NAVER 변수 전달 workflow도 config에 포함한다. 이전 source5/6 결과를 이 입력의 실행으로 합산하지 않는다.

build7은 새 JVM189(실패/오류/skip0)와 lint 오류0/경고57, 앱·검사 APK 조립을 통과했다. AndroidDefault38은 renderer4/storage4/editor4/controller1/record4/navigation2/tabs1/widget7/map6/discovery5, 별도 native2는 실제320dp/font2의 IME/기록이다. 실제 실행 결과는 각 원문/복원 proof를 따른다. 시도별189를 합산하지 않는다.

위젯 전용 skill 검색은 검증된 일치를 찾지 못했다. 원문과 재검색을 보존했고 적합한 native 조작/Compose typography 결과·공식 Android widget 지침(2026-05-12갱신)을 적용했다. 공식 지도 유형 문서와 MapLibre README를 확인했지만 기존 제공자를 새로 계약/교체하거나 온라인 인증을 완료한 것은 아니다. Variables 읽기403·docs루트404도 성공으로 바꾸지 않는다. [지도 선택·연결 조건](../../MAP_PROVIDER_OPTIONS.md).

위젯3장은 Android RemoteViews 합성 snapshot 렌더 fixture이며 Launcher/Main 스크린샷이 아니다. renderer4장은 Android Canvas export fixture다. actualMain/Launcher가 생성되면 원본 PNG를 별도로 검토한다.

## 지도 재검증과 API29 IME 관측

`android-review6.json`은 source8/binary8에서 map6/discovery5/tabs1의 새12개 통과와 원래 XML/프로필/account/소유 환경 복원을 확인한다. source7의 전체38 실패를 성공으로 바꾸지 않는다.

`native3.json`은 source8 IME1 실패를 그대로 보존한다. 실패 직후 private 진단에서 창 onScreen y=-158px, raw IME441px, safe content746px, 뒤로 버튼 y=-110px를 확인했다. private 진단 원문/PNG는 사용자 상태 노출을 피하려고 Git에 넣지 않는다. source9는 API29 raw root IME를 기존 inset과 최대값으로 합치며 기존 Compose inset listener를 덮어쓰지 않는다. `binary-build9.json`은 새 JVM189/lint0·경고57의 성공이며 native/실제 Main 재실행 결과와 구분한다.

## 추가 실패 보존과 source10

`native4.json`은 source9에서 API29 raw inset 보완 뒤에도 창 y=-158px가 남아 엄격한 IME1 검사가 실패한 결과다. safe content는377px로 줄었으므로 이를 padding 계산 실패라고 덮어 쓰지 않는다. XML/profile/account/소유 환경 복원을 확인했다.

`live1.json`은 source9 실제 Main을 처음 실행한 별도 실패다. 기본1:1 편집 화면1장은 실제 촬영했고 Root가 원본을 열어 확인했다. 이후 OS 파일 선택기의 filename 검색 EditText를 문서 행으로 오인한 driver 오류로 사진 callback 대기가 실패했다. 앱의 사진 로딩 실패라고 주장하지 않는다. 검토 전용 기록/사진과 own output을 정리하고 원래 DB행 fingerprint/XML/profile/account/소유 환경을 복원했다. 공개 갤러리의 완료된4장으로 쓰지 않는다.

`source-freeze10.json` / `binary-build10.json`: API29 편집 화면 수명에만 classic decor fitting/ADJUST_RESIZE를 적용하고 이탈 시 세 layout flag와 softInputMode를 복원한다. raw IME bridge는 제거했고 API30+는 기존 정책을 유지한다. AOSP Android10과 출시 Compose1.7/Core1.16 소스를 읽은 근거로 선택했다. 기존 native1의48dp/실제 IME/system bar/fullbounds 기대를 유지하고 화면 이탈 후 창 복원 확인을 추가했다. 새 JVM189(실패/오류/skip0), lint 오류0/경고57, 앱·검사APK 조립은 통과했다. 실제 Android의 새 결과와 구분한다.

## 현재 최종 후보: source11

`source-freeze11.json` / `binary-build11.json`은 framework fitting을 적용한 실제 API29 host에서 Compose inset을0으로 두고, API30+/Window없는 preview는 기존 경로를 유지한다. source10은 static review 후 미실행이며 이중 padding의 실제 실패로 꾸미지 않는다. 새 JVM189(실패/오류/skip0), lint 오류0/경고58(추가1개는 private 반환 composable 이름)을 기록한다. `native5.json`은 같은 APK쌍의 엄격한 실제320dp/font2/IME1과 편집 이탈 후 창 복원을 통과하고 원래 XML/profile/account/소유 환경을 복원했다. Root가 해당 원본PNG도 열어 확인했다. 새 일반 editor/controller와 실제 Main/Launcher 결과는 별도 proof를 따른다.

## 실제 Main 검토 도구의 추가 실패

`live2.json`은 source11 실제1:1 화면을 촬영했으나, title TextView만 허용한 OS 선택기 selector가 실제 API29 grid holder의 filename 접근성 이름을 놓친 뒤 관측 timeout으로 실패했다. 초기 private 계층에서 검토 nonce 사진과 DocumentsUI dir_list/icon_thumb 구조를 확인했다. `live3.json`은 앞선 실패의 시스템 선택기가 남아 Main focus 진입이 실패한 별도 시도다. 두 시도 모두 nonce fixture/own output·원래 DB행/전체XML/profile/account/소유 환경을 복원했다. 앱의 사진 callback 실패로 표시하지 않는다.

`owned-picker-recovery1.json`은 실제 이전 검토 nonce를 확인하고 Back만 사용해 그 선택기를 닫은 별도 복구다. 시스템 DocumentsUI 전체 force-stop이나 다른 앱/파일 삭제는 없었고 원래 앱 XML은 그대로였다. 이후 WORK driver는 목록의 정확한 filename+thumbnail/icon 구조를 사용하며, 자신이 요청한 선택기가 foreground에 남은 경우에만 bounded Back으로 닫는다. 제품/source11은 그대로 유지한다. 실제 저장 완료 문구를 보기 위해 위쪽 스크롤이 필요하면 그 실제 횟수를 별도 기록한다.

## Source12와 실제 Main 관측의 구분

Source11은 실제 Main에서 기본 글씨 IME 뒤 caption이 가려지는 문제가 드러났다. Source12는 outer 스크롤 하나를 유지하고 성공 안내를 동작 위로 옮겼다. 기존 font2 검사와 새480dp/font1의 실제 키 입력 검사가 통과했다. 새7과 보존12+22의 서로 다른41개 범위는 `android-scope-summary.json`에 나누어 기록했다.

`live6.json`은 실제 Main의 caption 입력 Morning Miles와 Back 안전영역을 관측했지만, 입력칸에 맞춰 스크롤된 미리보기 노드가 보인다고 가정한 도구 assertion 때문에 실패했다. `live7.json`은 미리보기의 사진 이동 제스처 영역에서 페이지 스크롤을 시도해 전체 preview 관측이 실패했다. 두 시도 모두 원래 데이터/소유 환경을 복원했다. 제품 입력 실패나 후속 전체 성공으로 고쳐 쓰지 않는다. 도구는 IME에서는 focused EditText 값을 확인하고,사진 바깥 여백에서 페이지를 스크롤해 preview를 확인하도록 보완했다. 추가 passive XML 읽기는 UI나 데이터를 변경하지 않았고,불완전한 동시 읽기는 결과로 사용하지 않았다.

사진의 최근 목록 문제는 실제 원인이 확정되지 않았다. 단독 provider 조회/정상 크기·시각과 실제 폴더 경로를 각각 확인했으며,메타데이터 갱신이 최근 목록을 고쳤다고 주장하지 않는다. Main은 정상 OS 폴더 탐색/정확한 파일 선택을 사용하고 원래 storage 표시 설정을 복원했다. 앱 callback에 URI를 주입하지 않는다.

## 서명 CI 시작 실패와 동일 작업 재시도

`ci-startup-attempt1.json`은 C의 서명 CI43095642325가 job/실제 빌드 생성 전 startup_failure로 종료된 원문 메타데이터다. APK나 JVM 검사를 실행한 것으로 세지 않는다. 소스 변경/중복 dispatch 없이 같은 작업의 rerun을 요청했으며 실제 최종 결과는 후속 CI proof를 따른다. 공식 상태 페이지의 Actions 정상 표시는 이 개별 실패의 원인을 설명하지 않으므로 underlying cause는 미확정이다. rerun 직후 API의 queued/run_attempt 필드 반영 시점도 최종 성공이나 새 빌드 수로 해석하지 않는다.

같은 작업 rerun의 실제 실패는 `ci-startup-rerun-observation.json`, 별도 새 실행의 실패는 `ci-fresh-startup-failure.json`에 보존했다. 둘 다 GitHub의 unexpected error 진단/job0이며 세부 원인은 미확정이다. 현재 대기·실행 중인 CI는 없고 대시보드 저장소는 미변경이다.
