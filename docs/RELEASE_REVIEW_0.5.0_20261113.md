# Runmoni0.5.0-dev 화면 구성 개편 게시 검증

frontend-design/ui-ux-pro-max와 기존 사용자 피드백에 따라 홈의 출발 장면과 독립 러닝 행동, 러닝의 수치/안내/경로, 프로필의 캐릭터 관리와 기간별 기록, 옷장 선택 작업영역, 크루/소속 파티, 기록 상세와 공유 편집의 구성을 재정리했다. 색만 일괄 교체하거나 각 화면을 동일한 카드 목록으로 만들지 않는다. 승인 원본과 실제 데이터/저장/권한/계정 경계는 보존한다. [구성 계획/자체 비평](../design-system/runmoni/pages/layout-renewal.md).

## 공개 APK와 출처

- 2026-11-13 한국 시간,같은 보관키의 실기기 검증 전 개발 prerelease0.5.0-dev/code27. [APK](https://github.com/wjdals988/runmoni/releases/download/v0.5.0-dev/runmoni-0.5.0-dev.apk) · [릴리스](https://github.com/wjdals988/runmoni/releases/tag/v0.5.0-dev) · [구현 PR](https://github.com/wjdals988/runmoni/pull/53) · [릴리스 노트 보완 PR](https://github.com/wjdals988/runmoni/pull/54).
- 실제 APK출처 C df09c233f711dc5a0573daf4e030e53af51dddf8. 크기 220,181,621bytes,SHA256 006086f4786e209bcce46d4815ad9ee8891ca5b9d2ac108c56b946ce25f813c5,인증서 SHA256 a21359c657f6797f5fb8229c3098e48bc72a45f9d25269f8d41ff5122465df4a. 후속 문서/피드 커밋 D로 C를 바꾸지 않는다.
- [서명 CI](https://github.com/wjdals988/runmoni/actions/runs/43416932734) 새 JVM189(실패/오류/skip0),release lint오류0·경고87. non-debuggable/패키지·버전·내장 C/보관 인증서·연결 설정·임시 파일 정리와 공개3자산/sidecar/digest·HEAD200/크기를 확인했다. readiness는 설정 확인이며 휴대폰·Google·운영 쓰기 성공이 아니다. Root가 공개 APK 본문을 다시 다운로드하거나 실행했다고 쓰지 않는다.

첫 서명 CI43414902441/출처9a4ef0eba5b0a981b880dfa81b296e96f80b9699는 빌드·서명과 임시 설정 정리를 마쳤으나 필수 RELEASE_NOTES_0.5.0-dev.md 누락으로 초안 업로드 전에 실패했다. [첫 실패 기록](app-review/2026-11-13-layout-renewal/ci-attempt1-failure.json)을 보존한다. PR54에서 문서만 보완하고 앱/검사/리소스/구성의 전체 입력 일치를 확인한 뒤 새 C에서 다시 빌드·서명했다. 이전 서명 APK의 출처를 새 C로 수정한 것이 아니다.

## 최종 로컬 실행 범위

- 최종 입력 [binary-build3.json](app-review/2026-11-13-layout-renewal/binary-build3.json) / source-freeze3.json: 새 JVM189·실패/오류/skip0,lint오류0·경고57. 초기 입력1/2와 그 원문은 보존하고 새 실행에 더하지 않는다. 빌드 당시 HEAD 5880f48575d33465a2f5a16ff8286b918bd3aa5a를 공개 APK출처 C로 바꾸지 않으며,[실제 전체 입력 SHA와 C 대조](app-review/2026-11-13-layout-renewal/executed-source-vs-release-C.json)를 별도로 확인했다. TEST debug의 로컬 검토 CA/Firebase 비활성과 connected release 설정은 다르다.
- [Android 범위](app-review/2026-11-13-layout-renewal/android-scope-summary.json): 최종 같은 입력3 APK쌍의 android-review2 정상5그룹51개를 유지하고,android-review3에서 옷장5개를 독립 그룹으로 재검사한 뒤 native1의13개를 더해 고유 메서드69개를 확인했다. android-review2는 옷장 묶음 실패로 전체 passed=false이며 정상51개만 채택했다. 실패한5개 묶음 속 일부 통과는 채택하지 않았다. android-review3의 Consumer1/Colors2/Navigation2는 각각 별도 instrumentation 프로세스이며 같은5개 method를 대체한다. 일반56개가 단일 phase에서 모두 통과했다고 쓰지 않는다.
- 범위 JSON은 includedGroups/excludedFailedGroups,각 원문 SHA와 source3 동일 입력,sameInputPassedGroupsRetained=true/earlierInputResultsReused=false를 명시한다. frozen FQCN/method와 selector·원문 OK개수·설치쌍·전/후/복원후6맵을 대조해51+5+13의 중복 없는69개임을 확인했다. 입력2의27부분 통과·이전0.4.4의16개/0.4.3의70개·Launcher는 합산하지 않았다. native13 중12개는 실제320dp/systemfont2이고1개는 실제480dp/font1의 긴 공유 문구/IME 검사이며,13개 전부를320/font2로 표시하지 않는다.
- 이69개는 ComponentActivity/in-memory/inert-map fixture다. 일반의 가상 viewport와 실제 OS 설정을 사용하는 native 검사를 구분한다. 선택한 정상 그룹·분리 재검사·native proof가 세 system animation scale을 실제로 읽어0임을 확인하고 그 설정에서 조작/레이아웃을 검사했다. 전체 페이지 Main 촬영·휴대폰·Google/운영 백엔드 검증이 아니다. [정적 endpoint 대비](app-review/2026-11-13-layout-renewal/contrast.json)는 frozen 색 역할10조합을 확인했으며,필수 Primary/Muted의 Paper/Cream/MintWash와 RouteBlue의 Paper/Cream 조합을 포함한다. 전체 픽셀/지도 배경/중간 애니메이션 프레임/실제150ms 시간이나 모든 모션 배율을 측정한 것은 아니다.
- [실제 Main 단일 PASS](app-review/2026-11-13-layout-renewal/live1.json): nonce 합성 완료 기록으로 홈·옷장·기록 결과·기본1:1 공유 편집·지도5화면을 관측했다. 이 제한된5화면을 전체 페이지 실제 Main 검증이라고 표시하지 않는다. Main에서도 세 system animation scale의 실제0을 읽고,옷장 하의→상의 탭의 selected 상태와 기존 기록/미니미/배경 도구 탭을 탭했다. 도구 탭의 selected 속성을 관측했다고 하지 않는다. 기록으로 Back·재진입 뒤 기본 draft 설명 일치와 지도 밖 조작을 확인했다. 승인 sprite/기본1:1·기록/지갑·위치 정책을 유지한다.
- 이번 Main은 변경된 구성의 일부와 기본 공유 진입/왕복에 한정했다. 합성 사진 fixture는 준비/정리했으나 사진Picker·IME 문구 입력·사진 이동·갤러리 저장·공유창·별도 UID receiver/PNG export를 재실행하지 않았다(priorShareExportFlowReexecuted=false). 앞서 완료한 export는 [0.4.3 게시 검증](RELEASE_REVIEW_0.4.3_20261112.md)의 이전 입력 이력이며 이번 새 Main 성공으로 합산하지 않는다. native의 별도 UI/IME fixture가 실제 Main의 사진/export 실행을 대신하지 않는다. 실제 SNS 전달도 별도다.
- [Root 원본 검토](app-review/2026-11-13-layout-renewal/root-main-visual-review.json): 실제 Main 원본5장(홈/옷장/기록 결과/기본1:1/지도)을 열어 내용과 개인정보를 확인했다. 운영 갤러리4장은 이 중 선택한 원본이며 사용자 시각 승인·공개 서명 APK 실행이나 나머지 페이지의 실제 Main 검증은 아니다.
- 실패한 android-review2를 포함한 각 formal proof의 원래 XML/프로필/account·소유 설정/권한/전원과 실제 전/후/복원후6맵·설치 APK쌍을 확인했다. Main의 원래 DB행/전체XML/프로필/account·소유 환경을 복원하고 nonce fixture를 정리했다. [정확한 소유 AVD 종료](app-review/2026-11-13-layout-renewal/session-cleanup.json)는 android-review2의 원래 false와 android-review3/native/Main의 true를 보존한4개 복원 근거를 따른다. 전체 OS 파일/원래 GPS fix/picker 캐시 전체 복원을 주장하지 않는다.

## 보존한 이전 실패와 중단

[입력2 android-review1 원문](app-review/2026-11-13-layout-renewal/android-review1.json)은 부분 통과27개 뒤 전체 passed=false/KeyboardInterrupt로 종료했다. 그중 [ConsumerClosetTest 실패](app-review/2026-11-13-layout-renewal/android-review1-closet.txt)는 ComponentActivity fixture에서 HomeClock의 onDispose가 removeObserver를 Main 스레드 밖에서 호출해 IllegalStateException이 발생한 제품코드의 수명 정리 위반을 보였다. 실제 Main/휴대폰에서 같은 예외를 관측했다고 하지 않는다. [Root 중단 이유](app-review/2026-11-13-layout-renewal/input2-stop-reason.json)에 따라 정확한 collector에 SIGINT를 보내고 finally의 원래 XML/프로필/account·소유 환경과 복원후6맵을 확인한 뒤 소스를 바꿨다. 중단된 시도에 전체 그룹 종료후 sourceMapsAfter가 있다고 만들지 않는다.

이후 시계/수신기 수명을 Main.immediate와 STARTED의 repeatOnLifecycle에 묶은 입력3으로 새 빌드했다. 입력1/2·27개 부분 통과·실패한 Consumer 검사와 Root 중단은 최종 고유69개에 더하거나 성공으로 고치지 않았다. 새 검사가 모든 생명주기/경합 가능성을 측정했다는 뜻은 아니다.

입력3의 [android-review2 옷장 묶음](app-review/2026-11-13-layout-renewal/android-review2-closet.txt)에서는 Consumer의 원래 Clock 경로는 통과했지만,RunningKitColorsUiTest의 L181에서 미니미 Canvas semantics 노드를30초 내 찾지 못해5실행/1실패였다. renderer 예외나 계정 변경을 이 원문에서 관측한 것은 아니며 묶음/캐시 등 원인은 미확정이다. 제품과30초 predicate를 바꾸지 않고 같은 APK에서 [Consumer](app-review/2026-11-13-layout-renewal/android-review3-closet.txt)·[Colors](app-review/2026-11-13-layout-renewal/android-review3-colors.txt)·[Navigation](app-review/2026-11-13-layout-renewal/android-review3-closet-navigation.txt)을 분리 실행해5개 통과를 확인했다. 분리 조건에서의 결과가 원인을 확정하거나 원래 묶음 실패를 없애지는 않는다. 전체 실패인 android-review2 원문과 그중 정상51개 유지/실패묶음 제외를 명시적으로 보존한다.

## 운영 대시보드와 남은 확인

[운영 대시보드](https://coldbrewventi.vercel.app/projects/runmoni) · [대시보드 PR](https://github.com/wjdals988/mydashboard/pull/28) · [main CI](https://github.com/wjdals988/mydashboard/actions/runs/43420525767) · Production 7764515365 · main c89ba07ea7b5458a6b603bf5fba45f37a3f758af. canonical13항목과 검토한 원본 PNG4장의 운영 bytes,Node23개를 확인했다. 다른8프로젝트·이전19개 내역·이전4개 PNG를 보존했다. [운영 근거](app-review/2026-11-13-layout-renewal/dashboard-production-verification.json).

preview27/stable null은 이 문서/피드 변경으로 준비하며 canonical raw 전체 bytes는 문서 PR 병합 뒤 별도 확인한다.0.4.4 실제 APK출처 7d921417e63d41bac5942645f2d2a50f4e1d308a,0.4.3 eb32a30d20b0223d436e80ca09c7c17e25d60c69,0.4.2 176a9cbe1112efa05e9fc55d913738ee1778a57f,0.1.5 75c39ca900b140cfb02c1a47807a5955c8b8db7f를 후속 커밋으로 바꾸지 않는다.

승인 원본/58ID/13슬롯/9탭/0P·기록/지갑/보유권·100m 동의/FGS/runId·프로필ACK·export/lifecycle/IME/삭제 확인은 유지했다. 새 원화·키·Rules·의존성을 추가하지 않았다. 휴대폰/Google/운영 Firestore/네이버 인증·야외GPS/장시간BG·Doze/OEM/API31+·SNS전달·사용자 시각 승인·공개 서명 APK 런타임과 실제150ms 시간/모든 중간 프레임·다른 배율 검증은 미확인이다. 다음은 같은 키 휴대폰 업데이트/기존 데이터 보존과 실제 행동 확인,나머지 실제 Main 화면 및 큰 글씨/가로/IME·각 오류 상태의 지속 검토다.
