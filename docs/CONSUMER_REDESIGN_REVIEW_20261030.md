# 소비자 러닝 앱 UI/UX 개편 검토 — 2026-10-30

대시보드 같은 반복 카드 구성을 줄이고 러닝 시작/실제 방 목록/기록/꾸미기에 집중하도록 0.2.0-dev(code10)를 구현했다. 사용자가 대시보드 배포와 전면 개편을 요청했다. 이번 공개는 실기기 검증 전 테스트용 개발 prerelease이며 일반 운영/실기기 검증 완료가 아니다.

## 참고와 보존

[awesome-design-md](https://github.com/VoltAgent/awesome-design-md), 확인 revision `fcc70284932276d085f01ef9f83edde90b1258cb`의 Nike/Airbnb/Apple 분석을 읽었다. 제3자의 웹사이트 분석이며 공식 모바일 규격/실사용 조사로 표시하지 않는다. 구체적인 적용 기준은 루트 DESIGN.md다. 새 의존성/폰트/브랜드 자산은 추가하지 않았다. [Nike Run Club 공식 소개](https://www.nike.com/nrc-app)의 시작·기록·커뮤니티 설명은 HTTP200으로 확인했고, 실제 NRC 앱 설치/시각 비교 실험은 수행하지 않았다. Strava features 페이지는 HTTP200이지만 본문 기능 설명이 추출되지 않아 UI 판단 근거에 포함하지 않았다.

승인 원본 SHA256 `f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65`를 재확인했다. 캐릭터 렌더러/원화·마스크·의상 승인 상태는 변경하지 않았다. 9개 옷장 분류/13슬롯/58아이템과 보유권/지갑/기록 저장 구조를 보존한다. Aseprite/image_gen을 실행하지 않았다.

## 현재 검증

- 오프라인·Gradle 작업자1개로 debug 빌드/JVM40개 통과(실패/오류/skip0), lint오류0/경고35. 최초 sandbox 소켓 제한과 공통 폼 width 컴파일 오류는 수정/재실행 이력으로 CHANGELOG에 보존한다. 추가 옷장 테스트 APK도 빌드했다.
- 실제 불투명 UI 색상9쌍의 대비 검사 통과. 이는 지도/비활성/야외 실기기 시인성까지 검증한 결과가 아니다.
- Android 실행/캡처와 공개 APK/대시보드 확인 결과는 아래에 실제 완료 후 추가한다. [기계 판독 결과](app-review/2026-10-30-redesign/build-verification.json)를 따른다. 과거 테스트를 새 UI 성공 수치로 합산하지 않는다.

## 실행/배포 경계

기존 PR9(러닝)/PR10(방)과 이번 UI 변경을 배포에 통합한다. 공용 호스팅 계정은 연결되지 않아 실제 공용 서버/URL은 없다. Render 유료 자원을 생성하거나 Firebase Spark 설정을 변경하지 않았다. APK·대시보드 게시를 공용 방 서비스 개설로 표현하지 않는다.

실제 휴대폰/Google 로그인/원격 백업/야외 GPS·화면 끔/2대 음성은 미확인이다. 클라우드 AOSP API29 TCG 에뮬레이터는 그 검증을 대체하지 않는다. Android Compose 합성 데이터 캡처와 실제 MainActivity+loopback API 캡처도 각각 표시한다.

자동 승인 검토가 PR9의 main 병합을 거절했다. ‘사용자는 PR 생성·배포를 요청했을 뿐 main 병합을 명시적으로 승인하지 않았다’는 사유이며 우회하지 않는다. 새 UI와 대시보드는 PR/미리보기로 검토 가능하게 준비하고, main 전용 동일 키 서명 workflow·새 APK 공개·운영 대시보드 교체는 명시적 승인 뒤 진행한다. 기존 공개0.1.6과 출처를 그대로 유지한다.

## 초기 Android 실행과 후속 시각 수정

초기 debug APK에서21개 Android 검증이 한 번의 실행으로 모두 통과했다(410.318초). 5개 탭×너비320/360/411dp×fontScale1/1.5/2, 고정 러닝 조작, 방 생성/입장/실패 초안 보존, 전체 화면 옷장, 버전 레드닷/확인 후 링크, 로컬 상태 러닝·기록 흐름을 포함한다. 합성 AppState/테스트 액션을 쓰는 UI 계약 검증이며 실제 GPS·공용 서버·휴대폰 검증이 아니다. 클라우드 부팅 시 ‘System UI isn’t responding’을 확인하고 instrumentation 이전 Wait를 선택한 사실을 preflight에 보존했다. 앱 ANR을 통과 처리하거나 테스트 중 경고를 자동으로 닫지 않았다.

실제 캡처에서 크루 소개/생성 버튼이 첫 목록을 가리는 것을 확인했다. 제목/간격을 압축하고 선택한 방 종류의 생성 행동 하나와 참여 필터를 배치했으며 Android 뒤로 가기로 목록에 복귀한다. 큰 글씨용 원화 준비 placeholder도88dp로 공간을 확보한다. 이 후속 수정은 초기21개 통과와 구분해 새 APK에서 관련 검증을 수행한다. 초기 AVD PID199466은 빌드 전에 종료했다.

소스는 `29700f4`에서 원격 `codex/consumer-app-redesign`에 저장했고 [앱 PR11](https://github.com/wjdals988/runmoni/pull/11)을 생성했다. 기준 브랜치는 아직 열린 방 PR10의 `codex/community-rooms`다. PR 생성/푸시는 main 병합·서명 APK 공개를 뜻하지 않는다.

최종 debug APK의 후속 Android8개는 한 번에 모두 통과했다(262.737초). 방4개/5개 탭의9조합/실제 화면 전환/채팅 선택 유지/옷장 고정 미리보기·보유권을 다시 확인했다. `finalDebugApkSha256`와 현재 실행 로그를 기준으로 초기21개 실행과 분리한다. 실제 동작 없는 지표·방 목록을 채워 넣지 않았으며 오프라인 안내와 정상 빈 결과를 구분한다.

전용 AVD의 실제 해상도를320×640/density160, 시스템 fontScale2로 변경한 native 검증3개가 한 번에 통과했다(114.672초). 옷장/프로필 이름 저장과 보유권 보존, 앱 정보/업데이트 확인, 새 채팅 도착 시 읽던 위치 유지와 실제 IME 위 입력·52dp 전송 버튼의 전체 영역/보내기/뒤로 가기를 확인했다. 테스트 로그의 일시적 UiTestAutomationBridge null-root 진단은 테스트 실패로 이어지지 않았으며 앱/실기기 성공으로 확대하지 않는다. 해상도/폰트/하드웨어 키보드 표시 설정과 애니메이션은 저장한 원래 값으로 복원했다. [복원 결과](app-review/2026-10-30-redesign/native-large-restoration.json)를 따른다.

실제 MainActivity/AppViewModel/PilotClient와 소유한127.0.0.1:8789 API를 연결한 Android1개도 통과했다(81.06초). 임시 게스트2개로 방 생성/공개 발견/독립 파티/중복 입장(정원2명 유지)/채팅을 확인했다. WorkManager를 실제 실행해 기기 기록은 미동기화로 남고 원격 기록0건인 것을 확인했으며 포인트/보유권/아바타를 보존하고 원래 지갑/연결 주소를 복원했다. 임시 로컬 기록은 삭제했고 API 프로세스는 종료했고8789 adb reverse 제거 명령을 실행했다(제거 후 목록은 별도 캡처하지 않았다). 다른 실제 휴대폰2대나 공용 서버 검증이 아니다. room-review의 이전 PNG를 지운 뒤 이번 실행이 새로 만든 PNG만 보관했다. [결과](app-review/2026-10-30-redesign/loopback-api-verification.json).

후속 MainActivity 캡처의 첫 시도는 UIAutomator의 빈 root/현재XML 파싱 실패로 중단됐다. 소유한 임시XML을 먼저 지우고 실제 현재 hierarchy가 파싱되는 경우에만 캡처하는 제한된 재시도를 추가했다. 이를 앱 기능 실패/성공이나 이미 완료된 테스트 수치로 합산하지 않는다.

실제 MainActivity의 홈/탐색/러닝/크루 오프라인/나/옷장6개 전체 기기 캡처를 완료했다. [실제 실행/파일 해시](app-review/2026-10-30-redesign/main-screens-verification.json)와 raw PNG를 보관한다. 방/파티/채팅 PNG는 같은 최종 앱과 임시 loopback API의 실제 검증용 데이터이며, 별도의5개 탭 viewport PNG는 합성 AppState 테스트 화면이다. 호스트 렌더/GIF를 실제 앱 화면으로 표시하지 않았다. 최종 AVD PID203020도 이름/소유를 확인해 종료했고 추가 빌드·설치와 겹치지 않았다.

## 대시보드 미리보기와 공개 경계

[대시보드 PR11](https://github.com/wjdals988/mydashboard/pull/11), 커밋 `14b93f36cc956c68507db9b5477aac9e74c5abcc`에 실제 앱7화면의 대표 이미지/갤러리/원본 열기와 버전 구분을 반영했다. 다른 프로젝트·순서·현재0.1.6 APK 필드/과거 릴리스 내역은 깊은 비교로 불변을 확인했다. 로컬 Node23개 통과(실패/skip0), GitHub CI run41024055547의 Test, lint, and build SUCCESS와 Vercel Preview success를 확인했다. 의존성/잠금 파일 변경이나 APK 재다운로드는 없었다.

실제 발급된 [Vercel 미리보기](https://mydashboard-klausqav0-wjdals988-6836s-projects.vercel.app/projects/runmoni)는 소유자 로그인이 필요하다. 이 환경의 익명 HTTP 요청은 Login – Vercel 페이지로 연결돼 미리보기 갤러리 HTML/이미지의 익명 접근은 확인하지 못했다. 보호를 우회하거나 인증 정보를 요청하지 않았다. CI/미리보기 배포 성공과 실제 페이지의 익명 렌더 확인을 구분한다. Android 화면 자체는 위 실제 PNG에서 볼 수 있다. [서버 확인 결과](app-review/2026-10-30-redesign/dashboard-verification.json).

운영 페이지는 HTTP200으로 기존0.1.6 다운로드를 유지하며 새 갤러리 경로가 없었다. 기존 Production commit34f2924와 새 Preview commit14b93f3도 구분했다. 자동 승인 검토가 main 병합을 거절한 뒤 이를 우회하지 않았으므로 PR9/10/앱UI PR11/대시보드 PR11의 main 통합·동일 키0.2 서명 CI·새 APK 공개·피드/운영 다운로드 교체는 명시적 main 병합 승인 뒤 진행한다. 공용 방 API URL/호스팅 계정 연결도 별개로 남아 있다.

## 후속 공개 완료 — 명시 승인 이후

사용자가 ‘main 병합 승인. APK와 운영 대시보드 배포 진행’을 명시했다. 앱 PR9/10/11과 대시보드 PR11을 main에 통합하고 기존 보관 키로 서명한0.2.0-dev/code10을 공개했다. 실제 APK 출처는 `e7b988af2b96c640fbcc40681d5201f74527b36a`, CI run41027995152의 JVM40/실패0·release lint오류0/경고60과 서명/패키지/내장 출처를 확인했다. 위 debug lint경고35와 온라인 release 경고60은 실행 환경별 결과이며 합치거나 오류0을 경고0으로 표시하지 않는다.

운영 대시보드 main3fc0063의 CI/Vercel Production success, 실제 페이지 HTTP200과 새0.2 다운로드/버전/해시/갤러리7장·이미지 원본 일치도 확인했다. 이전 대기/미게시0.2/기존0.1.6 유지 서술은 검토 당시 이력이며 최신 상태는 [게시 검증](RELEASE_REVIEW_20261030.md)을 따른다. 실기기/Google 로그인/공용 방 서버는 여전히 별도 미확인이다.
