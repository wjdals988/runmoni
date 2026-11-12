# Runmoni0.4.4-dev 트랙·선택 강조 게시 검증

frontend-design/ui-ux-pro-max의 제안과 기존 사용자 피드백을 참고해 미니미 주변의 정적 민트/라임/클레이 트랙,홈4dp 색상 띠,공통 분류·옷장 탭의 선택 색면/150ms 전환을 반영했다. 승인 sprite는 그대로이며 그림을 실제 GPS 경로나 달성 성과로 표시하지 않는다. 모든 제안이 완료된 것은 아니며,P1/P2 공유 테마·큰 의복 비교·다크 테마 등은 별도 작업이다. [우선순위/설계 비평](UI_UX_EXPRESSIVE_PRIORITIES_20261112.md).

## 공개 APK와 출처

- 2026-11-12 한국 시간,같은 보관키의 실기기 검증 전 개발 prerelease0.4.4-dev/code26. [APK](https://github.com/wjdals988/runmoni/releases/download/v0.4.4-dev/runmoni-0.4.4-dev.apk) · [릴리스](https://github.com/wjdals988/runmoni/releases/tag/v0.4.4-dev) · [구현 PR](https://github.com/wjdals988/runmoni/pull/51).
- 실제 APK출처 C `7d921417e63d41bac5942645f2d2a50f4e1d308a`. 크기 `220,148,853`bytes,SHA256 `5302f2a8275ac5e32533d7ca18d6b23033ef97a06d506042e9a63a84413ba48f`,인증서 SHA256 `a21359c657f6797f5fb8229c3098e48bc72a45f9d25269f8d41ff5122465df4a`. 후속 문서·피드 커밋 D로 C를 바꾸지 않는다.
- [서명 CI](https://github.com/wjdals988/runmoni/actions/runs/43179710268) 새 JVM189(실패/오류/skip0),release lint오류0·경고87. non-debuggable/패키지·버전·내장 C/보관 인증서·연결 설정·임시 파일 정리와 공개3자산/sidecar/digest·HEAD200/크기를 확인했다. readiness는 설정 확인이며 휴대폰·Google·운영 쓰기 성공이 아니다. Root가 공개 APK 본문을 다시 다운로드하거나 실행했다고 쓰지 않는다.

## 최종 로컬 실행 범위

- 최종 입력 [binary-build4.json](app-review/2026-11-12-expressive-track/binary-build4.json) / `source-freeze4.json`: 새 JVM189·실패/오류/skip0,lint오류0·경고57. 초기0.4.4의 build-result.json/build-result2.json/build-result3.json과 각 원문은 이전 입력으로 보존하며189를 더하거나 최종 새 결과로 바꾸지 않는다. 빌드 당시 HEAD `2528dabcfe6b707ee18b6a9146417ac7b16101fc`를 공개 APK출처 C로 덮어쓰지 않으며,[전체 입력 SHA와 C의 대조](app-review/2026-11-12-expressive-track/executed-source-vs-release-C.json)를 별도로 확인했다. TEST debug의 로컬 검토 CA/Firebase 비활성과 connected release 설정은 다르다.
- [Android 범위](app-review/2026-11-12-expressive-track/android-scope-summary.json): 최종 APK쌍의 일반13/native옷장3=새16개가 모두 통과했다. 일반은 옷장1·Studio동선2·러닝/5탭2·첫 진입4·홈 날씨 배치2·색 선택2이며,native는 실제320dp/systemfont2에서 옷장 기본형/분류·마지막 품목/팔레트·색 선택 각1이다. 실제 원문 OK개수·selector·SHA가 맞는 frozen FQCN의 method를 대조해 서로 다른16개임을 확인했다. 이전0.4.3의70개나 Launcher 결과를 이번 새 실행에 합산하지 않았다.
- 이16개는 ComponentActivity/in-memory/inert-map fixture다. 일반/native phase가 세 system animation scale을 실제로 읽어0임을 확인하고 그 설정에서 조작/레이아웃을 검사했다. 전체 페이지 Main 촬영·휴대폰·Google/운영 백엔드 검증이 아니다. [정적 endpoint 대비](app-review/2026-11-12-expressive-track/contrast.json)는 foreground/background6조합4.5이상이며 전체 픽셀/중간 애니메이션 프레임/실제150ms 시간이나 모든 모션 배율 검증을 뜻하지 않는다. 좌표의 height 제한과 Canvas clip 보강은 원문/입력별 기록을 따른다.
- [실제 Main 단일 PASS](app-review/2026-11-12-expressive-track/live2.json): nonce 합성 완료 기록으로 홈·옷장·기본1:1 공유 편집·지도4장을 관측했다. Main에서도 세 system animation scale의 실제0을 읽고,옷장의 하의→상의 탭을 탭한 뒤 selected 속성을 확인했다. 기존 기록/미니미/배경 도구 탭을 실제로 탭하고,기록으로 Back·재진입 뒤 기본 draft 설명 일치와 지도 밖 조작을 확인했다. 도구 탭의 selected 속성을 관측하거나 이번 새 선택 색면이 이 도구에도 적용됐다고 주장하지 않는다. 기본1:1과 기존 기록/지갑·위치 정책을 유지한다.
- 이번 Main은 변경된 시각 영역과 기존 기본 공유 진입/왕복에 한정했다. 합성 사진 fixture는 준비/정리했으나 사진Picker·IME 문구 입력·사진 이동·갤러리 저장·공유창·별도 UID receiver/PNG export를 재실행하지 않았다(`priorShareExportFlowReexecuted=false`). 해당 성공은 [이전0.4.3 게시 검증](RELEASE_REVIEW_0.4.3_20261112.md)의 이전 입력 이력이며0.4.4 새 성공으로 표시하지 않는다. 실제 SNS 전달도 별도다.
- [Root 원본 검토](app-review/2026-11-12-expressive-track/root-main-visual-review.json): 실제 Main 원본4장(홈/옷장/기본1:1/지도)을 열어 내용과 개인정보를 확인했다. 운영 갤러리4장은 이 원본이며,사용자 시각 승인·공개 서명 APK 실행이나 모든 페이지의 실제 Main 검증은 아니다.
- 각 formal phase의 원래 XML/프로필/account·소유 설정/권한/전원과 실제 전/후/복원후6맵·설치 APK쌍을 확인했다. Main의 원래 DB행/전체XML/프로필/account·소유 환경을 복원하고 nonce fixture를 정리했다. [정확한 소유 AVD 종료](app-review/2026-11-12-expressive-track/session-cleanup.json)를 확인했다. 전체 OS 파일/원래 GPS fix/picker 캐시 전체 복원을 주장하지 않는다.

## 운영 대시보드와 남은 확인

[운영 대시보드](https://coldbrewventi.vercel.app/projects/runmoni) · [대시보드 PR](https://github.com/wjdals988/mydashboard/pull/27) · [main CI](https://github.com/wjdals988/mydashboard/actions/runs/43183130873) · Production `7732191548` · main `e23fb79ac8489acc91f4fbcb707b69386e8e93b5`. canonical13항목과 검토한 원본 PNG4장의 운영 bytes,Node23개를 확인했다. 다른8프로젝트·이전18개 내역·이전4개 PNG를 보존했다. [운영 근거](app-review/2026-11-12-expressive-track/dashboard-production-verification.json).

preview26/stable null은 이 문서/피드 변경으로 준비하며,canonical raw 전체 bytes는 문서 PR 병합 뒤 별도 확인한다.0.4.3 실제 APK출처 `eb32a30d20b0223d436e80ca09c7c17e25d60c69`,0.4.2 `176a9cbe1112efa05e9fc55d913738ee1778a57f`,0.1.5 `75c39ca900b140cfb02c1a47807a5955c8b8db7f`를 후속 커밋으로 바꾸지 않는다.

승인 원본/58ID/13슬롯/9탭/0P·기록/지갑/보유권·100m 동의/FGS/runId는 유지했다. 새 원화·키·Rules·의존성을 추가하지 않았다. 휴대폰/Google/운영 Firestore/네이버 인증·야외GPS/장시간BG·Doze/OEM/API31+·SNS전달·사용자 시각 승인·공개 서명 APK 런타임과 실제150ms 시간/모든 중간 애니메이션 프레임·다른 배율 검증은 미확인이다. 다음은 휴대폰 같은키 업데이트/홈·옷장 선택·기록 유지,계획의P1/P2와 추가 프레임/다른 배율 검증이다.
