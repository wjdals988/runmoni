# 0.3.7-dev 게시 검증 — 2026-11-07 한국 시간

ui-ux-pro-max·기존 awesome-design-md 기준을 전체 제품 화면에 적용하고 백그라운드 러닝·알림을 보강했다. 실제 가로/font2 검토에서 발견한 P1 가독성 문제를 수정한 [구현 PR](https://github.com/wjdals988/runmoni/pull/35)의 소스를 같은 보관키의 **실기기 검증 전 개발 프리뷰**로 공개했다.

## 실제 공개 APK

- [APK](https://github.com/wjdals988/runmoni/releases/download/v0.3.7-dev/runmoni-0.3.7-dev.apk) · [릴리스](https://github.com/wjdals988/runmoni/releases/tag/v0.3.7-dev) · [운영 대시보드](https://coldbrewventi.vercel.app/projects/runmoni).
- 0.3.7-dev/code19, 219246942bytes, SHA256 `a864ed8cb0df6444a8074368a21892a8b6f46e97e1432ba2936360f3b0525a53`.
- 실제 APK 출처 C는 **`aa9f4e21983899d0d3d90a73ac41dc02f8662632`**다. 이후 문서·피드 커밋 D로 출처를 바꾸지 않는다.
- 기존 보관 인증서 SHA256 `a21359c657f6797f5fb8229c3098e48bc72a45f9d25269f8d41ff5122465df4a`를 유지했다. [서명 CI42293094340](https://github.com/wjdals988/runmoni/actions/runs/42293094340)의 JVM115·실패/오류/skip0, release lint오류0/경고69를 확인했다.
- 실제 패키지·버전·서명·non-debuggable·내장 출처와 공개3자산/sidecar·HEAD200/크기를 대조했다. APK 본문을 클라우드에서 다시 내려받지 않았다.

[공개 자산](app-review/2026-11-06-ui-ux-pro-max/publication-verification.json) · [서명 CI](app-review/2026-11-06-ui-ux-pro-max/signed-ci-verification.json) · [공개 HEAD](app-review/2026-11-06-ui-ux-pro-max/public-apk-head-verification.json).

## 디자인과 기능

[실제 스킬 검색/채택](design/ui-ux-pro-max/README.md)의 revision `b7d5353e0130feb8c5574ef1251f8a1db1873ccb`를 사용했다. 제품 방향과 맞지 않은 자동 색·폰트·marketing 추천은 채택하지 않았다. native/Compose 조작·접근성 지침을 [MASTER](../design-system/runmoni/MASTER.md)와 기존 녹색·한글 시스템 글꼴·승인 미니미 기준에 종합했다. 특정 브랜드 복제나 공식 규격/WCAG 인증으로 표시하지 않는다.

첫 진입, 홈·탐색·러닝·크루/파티·프로필, 옷장·설정/점검·앱 정보/내역/라이선스·생성/확인 화면의 제목·목록·행동 위계, 여백·폭·정렬·48dp 조작·스크롤/IME를 정리했다. 실제 가로/font2의 진행 중 러닝 본문이 좁아지는 문제는 공간에 따른 헤더/탭 배치와 별도 가로 회귀로 확인했다. OS 다크 조건에서는 기존 밝은 테마를 유지하며 새 다크 테마 완성을 뜻하지 않는다.

기록 서비스와 화면 수명을 분리하고 알림의 거리·시간·최근 경로 속도·일시정지/재개를 보강했다. 종료는 앱에서 현재 러닝을 확인한 뒤 실행한다. 미수신/일시정지의 옛 속도를 현재 값으로 표시하지 않는다. 새 Rules·서명키·DB 스키마·의존성 변경 없이 Google/게스트 진입·기록·포인트·36개 표시/미준비22개·58ID/13슬롯/0P·성별·승인 원본을 보존했다.

## 최종 실행

debug JVM115·실패/오류/skip0는 [binary-build3.json](app-review/2026-11-06-ui-ux-pro-max/binary-build3.json)의 실제 새 실행이다. 최종 [binary-build4.json](app-review/2026-11-06-ui-ux-pro-max/binary-build4.json)는 같은 제품/JVM 소스·리소스·설정·제품 APK 바이트로 Gradle UP-TO-DATE 보고서를 재사용했다. 옛 warning 부재 기대를 고친 한 LargeText 메서드 때문에 Android test APK만 달라졌으며 JVM 새 실행이나 제품 변경으로 세지 않는다. local debug lint오류0/경고39도 build3 근거를 test-only build4가 UP-TO-DATE로 유지한 결과다. 공개 서명 CI의 실제 실행과 local debug 실행/재사용은 별개 근거이며 횟수를 합산하지 않는다.

debug build3 소스 고정점은 `cfa9b69bf49929594cb447a5e6b7bb4ed93e3965`, test-only build4는 `0aa4ef997fe9626238bc721f4e3776e8c01c76c0`다. 둘 다 위 실제 서명 release APK 출처 C와 구분하며 debug 커밋을 공개 APK 출처로 대신 기록하지 않는다. build2→3 제품 변경은 AppRoot, 검사 변경은 준비 대기30→120초/실제 대기시간 cache trace와 native 가로2다. build3→4는 한 LargeText 메서드의 warning 기대와 hair/socks 보존 assertion만 고쳤다. 기존 normal Hierarchy class는 그대로다.120초는 emulator 준비 제한이며 시작속도 SLA나 실제 대기시간 측정값이 아니다.

동일 제품 APK에서 기본56+원래 native 성공4+가로2(build3)와 test-only 새1(build4)=63회 메서드 통과를 확인했다. 원래 native1은 마지막 warning 부재 기대가 틀려 전체false다. 성공4만 유지하고 실패1은 제외했다. 제품 APK 바이트는 같은 test-only build4에서 그 한 메서드만 새로 확인했다. 한 번의 native5 전체통과를 주장하지 않는다. 가로2는 두 메서드가 실제 통과했지만 원래 parent 전체 결과는 finally 정책 복원 실패로 false다. effective bool은 같았으나 default가 disabled로 남았다. 원문 false를 보존하고 별도 후속 복원0제품검사로 원래 정책/통제 환경을 돌렸다.63회는 메서드 통과 합계이며 모든 parent 전체가 passed=true였다는 뜻이 아니다. UI13종/4조건·active 세로/가로2는 host-review6에서 실제 수집한15원본을 최종 host-review8가 명시적 출처로 재사용한다. 최종 host에서13종을 새로 촬영한 것이 아니며 BG2만 새로 실행한다. 갤러리는 홈/옷장 source6 원본과 최종 host의 새 러닝/알림(Main3/SystemUI1)이다. 다른 APK 사진·부분 BG는 합산하지 않는다.

[기본56](app-review/2026-11-06-ui-ux-pro-max/android-review6.json) · [원래 native 성공4](app-review/2026-11-06-ui-ux-pro-max/native1.json) · [가로2](app-review/2026-11-06-ui-ux-pro-max/landscape4.json) · [test-only 새 native1](app-review/2026-11-06-ui-ux-pro-max/native2.json) · [최종 host](app-review/2026-11-06-ui-ux-pro-max/host-review8.json). 같은 제품 APK·서로 다른 test APK3/4와 실제 소스 범위/복원 시점을 구분해 대조했다. native/가로 parent의 원래 전체false와 root 시각 검토는 사용자 시각 승인이 아니다.

원래 native1은 마지막 warning 부재 기대가 틀려 전체false다. 성공4만 유지하고 실패1은 제외했다. 제품 APK 바이트는 같은 test-only build4에서 그 한 메서드만 새로 확인했다. 한 번의 native5 전체통과를 주장하지 않는다. [실패1 원문](app-review/2026-11-06-ui-ux-pro-max/native1-native-hierarchy.txt)은 hair2/socks2를 유지하는 fixture에서 올바른 ‘미리보기가 없는 이전 선택은 보관 중이에요’를 없다고 기대했다. 제품은 원래 선택과 안내를 올바르게 유지했다. 교정은 안내 표시/원래 hair·socks 보존을 요구하는 한 블록뿐이며 앱 코드·제품 APK는 바꾸지 않았다. 네 그룹 성공4+수정 메서드 새1로 세로 coverage5를 구성하고 옛 실패1은 합산하지 않는다.

원래 native1의 XML/프로필·통제 환경 복원은 정확했지만 당시 sourceMapsAfter/Final은 없었다. [실패 후·교정 전 실제 소스 관측](app-review/2026-11-06-ui-ux-pro-max/native1-post-source-verification.json)과 [한 블록 test-only lineage](app-review/2026-11-06-ui-ux-pro-max/hierarchy-native-test-lineage.json)를 별도로 연결했다. 이를 실패 순간의 After snapshot으로 만들지 않는다. 통과한 기본56·native4·가로2의 검사를 수정하거나 새 test APK에서 다시 실행했다고 표시하지 않는다.

[가로 parent](app-review/2026-11-06-ui-ux-pro-max/landscape4.json)의 실제2개 메서드와4장 fresh fixture PNG는 통과했다. 그러나 finally에서 AOSP29의 wm default 명령이 disabled 정책으로 남아 effective bool만 복원됐고, parent는 completed=true/passed=false·정책 restoredExactly=false로 보존됐다. 이를 통과 parent로 다시 쓰지 않는다.

[별도 후속 복원](app-review/2026-11-06-ui-ux-pro-max/landscape4-post-restoration.json)은 원래 실패한 finally 이후 실제 실행했으며 추가 제품검사0회다. owned display의 fixedToUserRotation 속성만 제거하고 나머지 parsed XML·owner/group/mode를 보존한 뒤, owned AVD 재부팅/boot ID 변경으로 원래 default 정책을 다시 읽었다. 이후 원래 앱 전체 XML/프로필·12개 통제 설정·WM/모드/권한/전원·effective bool과 explicit policy·6개 소스맵 일치를 확인했다. 실제 후속 복원 완료 뒤 최종 host를 시작한 시각도 대조했다. 원래 finally 시점의 성공이나 전체 display 설정 파일 바이트/파일 존재 복원으로 바꾸지 않는다.

|실행|범위|실제 통과|근거|
|---|---|---|---|
|기본56|speed-recovery-data|6|[원문](app-review/2026-11-06-ui-ux-pro-max/android-review6-speed-recovery-data.txt)|
|기본56|recording-regression|9|[원문](app-review/2026-11-06-ui-ux-pro-max/android-review6-recording-regression.txt)|
|기본56|notification-factory|6|[원문](app-review/2026-11-06-ui-ux-pro-max/android-review6-notification-factory.txt)|
|기본56|notification-ui|6|[원문](app-review/2026-11-06-ui-ux-pro-max/android-review6-notification-ui.txt)|
|기본56|main-design|2|[원문](app-review/2026-11-06-ui-ux-pro-max/android-review6-main-design.txt)|
|기본56|dialog-design|4|[원문](app-review/2026-11-06-ui-ux-pro-max/android-review6-dialog-design.txt)|
|기본56|community|4|[원문](app-review/2026-11-06-ui-ux-pro-max/android-review6-community.txt)|
|기본56|hierarchy|3|[원문](app-review/2026-11-06-ui-ux-pro-max/android-review6-hierarchy.txt)|
|기본56|closet-navigation|2|[원문](app-review/2026-11-06-ui-ux-pro-max/android-review6-closet-navigation.txt)|
|기본56|app-information|8|[원문](app-review/2026-11-06-ui-ux-pro-max/android-review6-app-information.txt)|
|기본56|entry-startup|6|[원문](app-review/2026-11-06-ui-ux-pro-max/android-review6-entry-startup.txt)|
|원래 native parent의 성공4|native-closet-navigation|1|[원문](app-review/2026-11-06-ui-ux-pro-max/native1-native-closet-navigation.txt)|
|원래 native parent의 성공4|native-kit-colors|1|[원문](app-review/2026-11-06-ui-ux-pro-max/native1-native-kit-colors.txt)|
|원래 native parent의 성공4|native-entry|1|[원문](app-review/2026-11-06-ui-ux-pro-max/native1-native-entry.txt)|
|원래 native parent의 성공4|native-information|1|[원문](app-review/2026-11-06-ui-ux-pro-max/native1-native-information.txt)|
|새 native continuation1|native-hierarchy|1|[원문](app-review/2026-11-06-ui-ux-pro-max/native2-native-hierarchy.txt)|
|가로 회귀2·parent finally실패/후속복원 별도|active-run-landscape|2|[원문](app-review/2026-11-06-ui-ux-pro-max/landscape4-active-run-landscape.txt)|

실제 Main UI13종(기본8+추가조건5)은 [host-review6](app-review/2026-11-06-ui-ux-pro-max/host-review6.json)에서 새로 수집한 원본이다. 같은 시도의 active 세로/가로2장까지15원본을 host-review8가 동일 SHA·메타데이터·source6 참조로 재사용한다. **최종 host에서13종이나 active2를 새로 촬영한 결과가 아니다.** 원래 source6의 전체false·복원/소스 최종 일치를 보존하고 조건/사진과 완료하지 못한 BG를 분리한다. 옛 host2의8사진은 APK가 달라 사용하지 않았다.

source6의 native320dp/font2·작은 가로창·OS 다크의 밝은 테마·설정 IME/앱 정보창4조건과 진행 중 러닝 세로/가로를 실제 원문·사진으로 검토했다. `ime`는 실제 설정창의 키보드 표시/숨김·미저장 입력 보존이며 저장은 실행하지 않았다. `modal`은 앱 정보창을 문제 신고 행동까지 스크롤한 사진이다. source6에서 창 닫기·다시 열기·바깥 터치로 닫기를 확인했으며 host-review8에서 다시 실행했다고 표시하지 않는다. OS night 명령도 source6 원문에만 보존하고 최종 host의 새 명령으로 복사하지 않는다. 기본56의 생성/확인 callback fixture 검사와 실제 MainActivity 사진의 범위도 구분한다. 갤러리 홈/옷장은 source6의 GPS 주입 전 MainActivity 원본, 러닝/알림은 host-review8의 새 합성 GPS 검토 MainActivity/SystemUI다. TEST서명·Firebase미설정 debug·API29/AOSP/TCG 사진이며 공개 서명 APK·휴대폰·운영 Firebase 실행 사진이 아니다.

host의 숫자/조작 접근성 노출 bounds와, native 가로2의 positionInWindow+전체 measured size 검사는 범위가 다르다. 전체 숫자 줄의 크기·본문/OS 안 배치는 가로2의 실제 native fixture 원문으로 확인하며 이 callback fixture를 실제 recorder/GPS 실행으로 바꾸지 않는다. host PNG의 실제 화면 켜짐/비흑 probe와 root 시각 검토도 별도 근거이며, 픽셀이 검지 않다는 사실만으로 가독성이나 사용자 승인을 주장하지 않는다.

가로2 준비는 Launcher의 세로 복귀가 target context를 오염시키지 않도록 OS의 fixed-to-user-rotation을 일시 enabled로 고정했다. `/data/system/display_settings.xml`의 파일 부재를 default로 해석하는 실제 parser의 원래 정책 `default`와 effective bool을 각각 snapshot했다. 복원 시점/결과는 위 가로 parent·후속 복원 구분을 따른다. boolean만 같다는 이유로 default/disabled/enabled 정책까지 복원됐다고 간주하지 않는다. 이는 native OS 준비이며 Compose의 가상 viewport 변경이 아니다. 전체 display_settings.xml 바이트나 원래 파일 존재가 정확 복원됐다는 주장은 하지 않는다.

BG2는 host-review8에서 화면 끔 기록 증가, 실제 SystemUI 알림 일시정지/재개·본문 이동·앱 확인 후 종료, force-stop 뒤 새 프로세스의 마지막 commit 복구 종료를 새로 실행한 결과만 집계한다. source6의 부분 BG나 실패한 종료/저장·미실행 복구를 재사용하지 않는다. 이미 확인한 UI/active geometry와 메서드63회는 다시 실행한 것으로 세지 않는다. 저장 거리는 같은 segment 내부의 거리합과 대조하고 미관측 공백을 더하지 않는다. 모든 절전 상태의 무중단 실행이나 force-stop 뒤 연속 기록을 보장하지 않는다.

복원 범위는 원래 앱 전체 XML/프로필·검토행 정리 후 DB 내용과 12개 통제 설정·WM 크기/밀도·UiModeManager 모드·자기 패키지 권한/flags·관찰한 Awake/Asleep이다. AOSP29의 모드 잠금에 대한 별도 OS 진단1회는 제품검사0/사진0이다. source6의 OS 다크 검토는 일시 su0 모드 설정/원상복원과 실제 getter/night bit를 확인했다. 해당 명령은 최종 BG host에서 실행한 것으로 복사하지 않는다. adbd·패키지 권한·정책 resource·앱 데이터를 바꾸지 않는다. 최종 가로 검토의 fixed-to-user-rotation은 effective bool과 default/disabled/enabled 정책을 함께 원상복원한다. display_settings.xml 전체 바이트/파일 존재 복원, Android 전체 OS 상태 동일성이나 과거 BG host5 전체 설정 복원은 주장하지 않는다. 비공개 원문 값은 WORK에만 두며 Git·로그·채팅에 넣지 않는다. 실제 휴대폰·Google 로그인·운영 쓰기·야외 GPS·Doze·제조사 절전·Android12이상 실행·공개 서명 APK의 실제 실행·사용자 시각 승인은 별도 미검증이다. [전체 실행/사진/복원](app-review/2026-11-06-ui-ux-pro-max/README.md).

## 이전 실패와 판단 변경

이전 BG build2 JVM115/review3 Android33은 전체 UI 변경 전 baseline이다. [과거 BG host5 중단](app-review/2026-11-06-background-running/host5-environment-interruption.json)은 finally/result JSON 미완료다. [후속 정리](app-review/2026-11-06-ui-ux-pro-max/interrupted-host5-cleanup.json)에서 원래 앱 XML/논리 DB·복구 진입 설정을 복원했지만 과거 전체 OS 설정 스냅샷은 없어 정확 복원을 주장하지 않는다.

debug JVM115는 binary-build1.json의 실제 실행을 동일 제품 binary-build2.json가 UP-TO-DATE로 재사용한 결과이며 추가 실행이 아니다. 이 build1/2 근거는 보존 JSON·빌드 로그·기록된 바이너리 SHA로만 대조한다. 새 빌드가 같은 local APK 경로를 덮어써도 옛 출처를 새 바이너리로 바꾸지 않는다.

[android-review1](app-review/2026-11-06-ui-ux-pro-max/android-review1.json)은21회 뒤 UI 준비의 run-as test Permission denied로 중단했다. [진단](app-review/2026-11-06-ui-ux-pro-max/cache-preparation-diagnostic1.json)은 직접 실행/앱 shell builtin 경로를 구분했다. [android-review2](app-review/2026-11-06-ui-ux-pro-max/android-review2.json)는 완료 그룹42회 뒤 앱 정보8개 중 옛 ‘공지 · 채팅’ 선택자/경로 기대1개가 실패했다. AppInformationTest만 수정한 build2는 제품 APK byte-identical이었다. 독립 chat-smoke 결과도 아래 실제 원문 그대로 보존하고 새63회에 더하지 않는다.

[android-review3](app-review/2026-11-06-ui-ux-pro-max/android-review3.json)는 앞8개 그룹40회 뒤 [옷장2개 그룹](app-review/2026-11-06-ui-ux-pro-max/android-review3-closet-navigation.txt)에서1개 실패했다. bounds·선택 검사는 앞서 통과했으나 noTextOverflow 진입부의 minimi_loading 소멸30초 대기에서 멈춰 문자 넘침 판정에 도달하지 않았다. 원인은 미확정이다. [사후 소스 측정](app-review/2026-11-06-ui-ux-pro-max/android-review3-post-source-verification.json)은 원래 host1 시작 후 시점의 실측이며 실패 당시 snapshot이 아니다. 옛40+잔여16+native5=61 계획은 최종 결과가 아니고 잔여16 대기 작업은 시작 전에 중단했다. 새 제품에서는 기본56부터 모두 새로 검증했다.

[host1](app-review/2026-11-06-ui-ux-pro-max/host-review1.json)/[host2](app-review/2026-11-06-ui-ux-pro-max/host-review2.json)는 각각 Main8장 뒤 이미 선택된 남자의 clickable 검사 기대에서 전체실패했다. 첫 WORK 보완도 label/widget class 가정으로 checked ancestor를 놓쳤다. [실제 관측](app-review/2026-11-06-ui-ux-pro-max/selected-radio-ancestor-observation.json)의 TextView label/선택 View ancestor를 근거로 이미 선택된 성별의 geometry-only 예외만 보완했다. 성별 변경·탭 성공의 증거는 아니다. 두 시도는 추가조건/BG 미완이며 원래 XML/DB/통제 환경 복원을 보존했다.

[host3 중단 요청](app-review/2026-11-06-ui-ux-pro-max/host-review3-controlled-stop.json)은 a.SOURCE_MAPS/a.MAPS 오타의 정적 발견 후 owned collector에만 SIGINT를 보낸 기록이다. [실제 결과](app-review/2026-11-06-ui-ux-pro-max/host-review3.json)는 KeyboardInterrupt·사진/BG0·finally 복원이다. 마지막 관측 phase는 중단 순간 snapshot이 아니며 실제 RuntimeAttributeError나 제품 실패가 발생했다고 바꾸지 않는다.

[host4](app-review/2026-11-06-ui-ux-pro-max/host-review4.json)는 OS night=yes 실제 확인에서 실패했다. 기본8 재사용+새 큰 글씨/가로2사진, 가로 geometry만 true/시각검토false·큰 글씨 조건 미완·BG0과 실제 XML/DB/통제 환경 복원을 보존했다. [OS 진단](app-review/2026-11-06-ui-ux-pro-max/os-night-mode-diagnostic.json)은0제품검사/0사진으로 일반 setter 뒤 no/0x11, 임시 su0 뒤 yes/0x21, 원래 모드 복원 뒤 no/0x11을 확인했다. adbd·패키지 권한·정책 resource·앱 데이터 변경은 없다.

[host5 P1 시각 거절](app-review/2026-11-06-ui-ux-pro-max/host-review5-active-running-visual-rejection.json)은 실제 진행 중 가로/font2에서 본문86px(240dpi에서 약57dp), 제목 잘림·큰 수치 전체 한 줄을 볼 공간 부족을 확인했다. bounds 통과만으로 가독성을 보증할 수 없었다. 숫자 한 줄의 높이는 이 관측에서 별도로 측정하지 않았다. [중단 요청](app-review/2026-11-06-ui-ux-pro-max/host-review5-controlled-stop.json) 뒤 [실제 finally](app-review/2026-11-06-ui-ux-pro-max/host-review5.json)의 XML/논리 DB/통제 환경 복원을 확인한 후 제품 레이아웃과 가로 회귀를 바꾸고 새 pair로 다시 빌드했다. 옛 BG2는 전체 미완이며 부분 관측을 새 결과에 더하지 않는다.

[검은 프레임 거절](app-review/2026-11-06-ui-ux-pro-max/host-review5-main-running-visual-rejection.json)의1699bytes PNG는 root가 직접 보고 갤러리에서 제외했다. 원인은 미확정이다. [후속 timeout/Awake 관측](app-review/2026-11-06-ui-ux-pro-max/screen-timeout-later-observation.json)은 당시 전원 상태나 원인을 증명하지 않는다. 옛 알림 일시정지 첫 tap은 ACK가 없었고 두 번째 tap에서 PAUSED ACK, 재개는 첫 tap에서 RUNNING ACK였다. 이 재시도의 원인도 미확정이며 숨기거나 첫 tap 성공으로 바꾸지 않는다.

[새 AVD 최초 부팅 준비](app-review/2026-11-06-ui-ux-pro-max/installation3-boot-attempt1.json)는 launcher 확인 전 실시간 accessibility observer dump assertion에서 중단했다. APK 설치·설치 전 XML 백업·앱 데이터 reset 명령을 실행하기 전이며 제품 빌드 실패나 원인 확정으로 표시하지 않는다. 이후 최종 pair 설치와63회/새13사진/BG2 성공은 위 실제 최종 원문이 있을 때만 별도 인정한다.

[두 번째 부팅 준비](app-review/2026-11-06-ui-ux-pro-max/installation3-boot-attempt2.json)도 APK 설치·백업·앱 데이터 reset 전 observer/활성 window 준비에서 멈췄다. 별도 SystemUI ImageWallpaper/EGL native SIGSEGV 관측 뒤 [owned SystemUI 프로세스만 재시작](app-review/2026-11-06-ui-ux-pro-max/installation3-system-ui-restart.json)하고 [Launcher 활성 window 복구](app-review/2026-11-06-ui-ux-pro-max/installation3-system-ui-recovery.json)를 관측했다. Launcher/런모니 데이터는 지우지 않았고 제품검사0회다. 이 후속 환경 관측을 앞선 검은 프레임의 원인 증명이나 이후 모든 그래픽 안정성 보장으로 확대하지 않는다.

[landscape1](app-review/2026-11-06-ui-ux-pro-max/landscape1.json)는 새 build3의 가로 회전 준비 `Actual product group orientation`에서 중단했다. 제품 instrumentation 실행 전이므로 실제 제품검사0회·fixture0장이며 가로2개 검사 실패/부분통과로 세지 않는다. 원래 XML/프로필·통제 설정/권한/전원을 정확 복원했고, 당시 실제 기록된 Before 소스맵과 실행 driver/observer 원본을 보존한다. WORK runner가 MainActivity를 먼저 실행한 뒤 실제 회전을 요청하도록 보완됐으며, 최종 가로2는 위 marker가 가리키는 독립 실제 실행 원문으로만 집계한다. 제품 소스·APK 재빌드나 이미 끝난 기본56/native5의 재실행을 뜻하지 않는다.

[landscape2](app-review/2026-11-06-ui-ux-pro-max/landscape2.json)는 준비 시 실제 가로 viewport였으나 instrumentation의 두 메서드 모두 requireNativeWindow에서 configuration.orientation 기대2/실제1로 실패했다. 두 검사는 실행됐으며 완료 통과0회다. 본문·수치 가독성 검사에는 도달하지 않았다. 최상위 JSON의 후속 readiness metrics 수집 assertion은 아래 optional 파일 direct-cat 수집기 결함과 같은 경로이며 실제2실패 원문을 대체하지 않는다. MainActivity 종료→Launcher 세로 복귀 중 target context가 초기화되는 runner 환경 문제를 분리하고 XML/프로필/통제 환경을 정확 복원했다. 이 실패를0검사 준비 중단이나 layout 검증 통과로 바꾸지 않는다.

[landscape3](app-review/2026-11-06-ui-ux-pro-max/landscape3.json) ([중단 관측](app-review/2026-11-06-ui-ux-pro-max/landscape3-controlled-stop.json))는 effective bool만으로 default/disabled 정책을 구분할 수 없다는 검토 뒤 OS 변경 전에 중단했다. 실제 finally는 KeyboardInterrupt/completed=true·제품검사0회·restorationNotApplicableBeforeMutation=true다. 마지막 관측 created는 정확한 interrupt 순간의 phase 확정이 아니다. 원래 상태를 변경하기 전이므로 수행하지 않은 복원을 성공으로 표시하지 않는다. 이후 WORK 도구만 tri-state snapshot/복원을 보강했으며 최종 실행 reference는 위 실제 marker를 따른다.

[android-review5](app-review/2026-11-06-ui-ux-pro-max/android-review5.json) ([실제6개 통과 원문](app-review/2026-11-06-ui-ux-pro-max/android-review5-speed-recovery-data.txt))는 첫 data6개 원문이 OK(6 tests)/group passed=true였지만 optional minimi-readiness.txt 수집에서 전체실패했다. parent actualPassedProductMethods=0은 수집 assertion 전 마지막 저장값이며 raw6통과와 구분해 그대로 보존했다. 해당 데이터 검사는 UI 준비 trace를 만들지 않으므로 파일이 없었고, 옛 adb exec-out run-as cat/allow_failure 경로가 돌려준 cat 오류 stdout을 실제 trace로 검증하다 멈췄다. 오류 문자열을 실제 대기시간 측정값으로 내보내지 않는다. 원래 XML/프로필·통제 환경은 정확 복원했다. WORK collector만 quoted sh-if로 파일 존재를 확인한 뒤 읽도록 고쳤으며 앱/test 소스나 APK는 바꾸지 않았다. marker가 가리키는 최종 full56은 독립 새 실행이고 옛 raw6은 새63회에 더하지 않는다.

[host-review6](app-review/2026-11-06-ui-ux-pro-max/host-review6.json)는 UI13/조건4·active 세로/가로2를 실제 수집한 뒤 알림 종료/저장 시나리오 중 전체실패했다. 첫 알림 종료는 실제 확인창을 열었고 취소 뒤 러닝/FGS가 유지됐다. 두 번째 종료 tap 뒤에는 root가 확인한 실패 화면이 SystemUI 알림창에 남아 저장하고 마치기 행동을 찾지 못했다. 최상위 실패는 `Visible exact action: 저장하고 마치기`이며 tap 전달/누락 원인은 미확정이다. 이 관측을 앱 코드 결함으로 단정하지 않는다.

원래 source6의 전체false·부분 BG·미완 종료/저장/복구와 [실제 알림 관측](app-review/2026-11-06-ui-ux-pro-max/host-review6-activity-notification-observation.json)을 보존한다. 실제 finally의 원래 전체 XML·논리 DB baseline·통제 환경과 최종6개 소스맵 일치를 확인한 뒤, 같은 app/test4의15사진·조건 metadata만 최종 host에 명시적 원본으로 재사용했다. WORK 도구의 activity 열림 ACK를 보강한 별도 최종 host가 BG2를 새로 실행하며 제품 소스/재빌드나 이미 끝난 UI/63회 메서드의 재실행은 없다.

[host7](app-review/2026-11-06-ui-ux-pro-max/host-review7.json)은 첫 일시정지 tap에 PAUSED ACK를 얻지 못한 뒤 재시도에서 SystemUI ‘일시정지’ label을 찾지 못했다. [실제 실패 화면 관측](app-review/2026-11-06-ui-ux-pro-max/host-review7-system-ui-focus-observation.json)은 Main RUNNING·알림창이 닫힌 상태였으며, 그 상태에서 알림창을 다시 열지 않고 label을 탐색한 WORK 도구 흐름을 구분했다. 앞선 tap 누락/전달·focus timing의 원인은 확정하지 않는다. 앱 실패로 단정하거나 첫 tap 성공으로 바꾸지 않으며 실제 XML/DB/통제 복원·sourceFinal 일치·실행 도구 불변을 보존한다. 이 시도의 화면 끔 관측 prefix도 최종 BG2에 합산하지 않는다.

최종 WORK 도구는 매 재시도에 알림창을 열고 frame 치수를 먼저 읽은 뒤 새 hierarchy/SystemUI focus를 확인한다. 그 후 중간 PNG 촬영 없이 원래 노드 중심에 직접 입력하고 실제 phase/activity ACK를 관측한다. 최대3회 제한을 유지하며75초 polling은 도구의 관측창으로서 앱 응답속도 SLA가 아니다. 재탭 전에 늦게 나타난 예상 phase는 initialPollingExpired/acknowledgedBeforeRetap로 구분하고 과거 실패 상태를 보존한다. 취소는 확인창이 사라지고 실제 RUNNING/foreground가 유지된 ACK를 별도로 확인한다. 제품 코드·빌드나 메서드63회의 추가 실행은 없다.

|이전 BG 시도|원래 결과|실제 finally|집계 경계|
|---|---|---|---|
|[host-review7](app-review/2026-11-06-ui-ux-pro-max/host-review7.json)|전체false · Original exact SystemUI label: 일시정지|XML/DB/통제 복원·sourceFinal 일치|[실제 관측](app-review/2026-11-06-ui-ux-pro-max/host-review7-system-ui-focus-observation.json); BG 합산 제외|

|실행·집계 경계|바이너리 출처|원래 전체 결과|XML 복원|통제 설정 복원|
|---|---|---|---|---|
|[android-review3.json](app-review/2026-11-06-ui-ux-pro-max/android-review3.json) · 이전 시도·합산 제외|[binary-build2.json](app-review/2026-11-06-ui-ux-pro-max/binary-build2.json)|전체실패·중단(false), parent 계수40회|확인|확인|
|[native1.json](app-review/2026-11-06-ui-ux-pro-max/native1.json) · 성공4 포함·실패1 제외/별도 새1|[binary-build3.json](app-review/2026-11-06-ui-ux-pro-max/binary-build3.json)|전체실패·중단(false), parent 계수4회|확인|확인|
|[android-review1.json](app-review/2026-11-06-ui-ux-pro-max/android-review1.json) · 이전 시도·합산 제외|[binary-build1.json](app-review/2026-11-06-ui-ux-pro-max/binary-build1.json)|전체실패·중단(false), parent 계수21회|확인|확인|
|[android-review2.json](app-review/2026-11-06-ui-ux-pro-max/android-review2.json) · 이전 시도·합산 제외|[binary-build1.json](app-review/2026-11-06-ui-ux-pro-max/binary-build1.json)|전체실패·중단(false), parent 계수42회|확인|확인|
|[android-review5.json](app-review/2026-11-06-ui-ux-pro-max/android-review5.json) · 이전 시도·합산 제외|[binary-build3.json](app-review/2026-11-06-ui-ux-pro-max/binary-build3.json)|전체실패·중단(false), parent 계수0회, 원문 통과6회/parent 계수 미갱신|확인|확인|
|[chat-smoke1.json](app-review/2026-11-06-ui-ux-pro-max/chat-smoke1.json) · 단독 보조검사·합산 제외|[binary-build2.json](app-review/2026-11-06-ui-ux-pro-max/binary-build2.json)|통과, parent 계수1회|확인|확인|
|[landscape1.json](app-review/2026-11-06-ui-ux-pro-max/landscape1.json) · 이전 시도·합산 제외|[binary-build3.json](app-review/2026-11-06-ui-ux-pro-max/binary-build3.json)|전체실패·중단(false), parent 계수0회|확인|확인|
|[landscape2.json](app-review/2026-11-06-ui-ux-pro-max/landscape2.json) · 이전 시도·합산 제외|[binary-build3.json](app-review/2026-11-06-ui-ux-pro-max/binary-build3.json)|전체실패·중단(false), parent 계수0회|확인|확인|
|[landscape3.json](app-review/2026-11-06-ui-ux-pro-max/landscape3.json) · 이전 시도·합산 제외|[binary-build3.json](app-review/2026-11-06-ui-ux-pro-max/binary-build3.json)|전체실패·중단(false), parent 계수0회|변경 전 중단·비대상|변경 전 중단·비대상|

## 운영과 후속 확인

대시보드 [PR](https://github.com/wjdals988/mydashboard/pull/20)·[main CI](https://github.com/wjdals988/mydashboard/actions/runs/42296554519)·Production `7611194099` 성공과 main `8f0a2cf1100a0688ab1e327315944449fe116633`를 확인했다. canonical HTTP200에서 APK/버전/code/크기/SHA/프리뷰 안내 등13항목과 선택 PNG4장 응답 바이트를 대조했다. 다른 프로젝트·순서/sourceUrl·이전 내역/갤러리는 보존했다. [운영 근거](app-review/2026-11-06-ui-ux-pro-max/dashboard-production-verification.json).

현재 실제 metadata로 preview19/stable null을 준비한다. 문서·피드 PR 병합 뒤 canonical raw 전체 JSON 응답을 확인해 최종 기록에 남긴다. 이후 문서 커밋 D를 APK 출처 C로 바꾸지 않는다. 다음은 휴대폰/Android12이상·기존 데이터/같은 키 업데이트·TalkBack·알림/위치 거부·야외 GPS/잠금/장시간·Doze/OEM과 Google/운영 두 기기 검증이다. 미준비22개 원화와 크루 운영 작업도 별도로 이어간다.
