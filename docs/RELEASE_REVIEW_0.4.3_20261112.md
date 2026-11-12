# Runmoni0.4.3-dev 전체 화면 UI·공유 게시 검증

frontend-design와 ui-ux-pro-max를 참고해 홈/최초 로그인/탐색/러닝/크루·파티·대화/나·옷장/기록·공유/설정·양식·앱정보·점검의 정보 위계와 조작을 정리했다. 승인 미니미와 정적 트랙, 기존 녹색과 제한된 route blue를 사용하고 과도한 카드·반복 문구를 줄였다. 스킬 추천을 최신 시장 조사나 사용성 개선율로 바꾸지 않는다. [계획·자체 비평](../design-system/runmoni/pages/studio-refresh.md).

## 공개 APK와 실제 출처

- 2026-11-12 한국 시간, 같은 보관키의 실기기 검증 전 개발 prerelease0.4.3-dev/code25. [APK](https://github.com/wjdals988/runmoni/releases/download/v0.4.3-dev/runmoni-0.4.3-dev.apk) · [릴리스](https://github.com/wjdals988/runmoni/releases/tag/v0.4.3-dev) · [구현 PR](https://github.com/wjdals988/runmoni/pull/49).
- 실제 APK출처 C `eb32a30d20b0223d436e80ca09c7c17e25d60c69`. 크기 `220,148,853`bytes, SHA256 `47f0f70a18a64ba84fe59f7d7a6466287721b29ee77bc2fccf1fb3c60f1f9c07`, 인증서 SHA256 `a21359c657f6797f5fb8229c3098e48bc72a45f9d25269f8d41ff5122465df4a`. 후속 문서·피드 커밋 D로 C를 바꾸지 않는다.
- [서명 CI](https://github.com/wjdals988/runmoni/actions/runs/43155385740) 새 JVM189(실패/오류/skip0),release lint오류0·경고87. non-debuggable/패키지·버전·내장 C/보관 인증서/연결 설정·임시 설정 정리와 공개3자산·sidecar/digest·HEAD200/크기를 확인했다. readiness는 설정 확인이며 휴대폰·Google·운영 쓰기 성공이 아니다. Root가 공개 APK 본문을 재다운로드하거나 실행했다고 쓰지 않는다.

## 공유를 사용하는 위치

‘나 → 러닝 기록 → 완료 기록 선택 → 공유’에서 별도 편집 화면으로 이동한다. 기본1:1과4:5·9:16,사진·문구·기록 배치·현재 미니미 위치/크기를 지원하며 미리보기와 같은 PNG를 저장하거나 Android 공유창으로 보낸다. Back은 기록으로 돌아간다. 사진 선택/갤러리 저장·SNS 전달/권한은 실제 휴대폰에서도 별도 확인한다.

## 실행과 검토 범위

- 최종 local TEST debug [binary-build3.json](app-review/2026-11-11-studio-ui/binary-build3.json): 새 JVM189·실패/오류/skip0,lint오류0·경고57. 빌드 당시 HEAD `8a50228c7815234089a0c41011561fb3edaef513`를 공개 APK출처 C로 덮어쓰지 않으며,[전체 입력 SHA와 C의 대조](app-review/2026-11-11-studio-ui/executed-source-vs-release-C.json)를 따로 확인했다. 로컬 검토 CA/Firebase 비활성과 connected release 설정은 다르다.
- 최종 source3 APK쌍에서 Android 일반2/native4=새6개를 실행했다. 앞선 입력의 보존64개와 합쳐 서로 다른70개 method의 통과를 관측했으며,70개 모두를 최종 바이너리에서 새로 실행한 것이 아니다. [범위/선택 원문](app-review/2026-11-11-studio-ui/android-scope-summary.json)와[실제 frozen FQCN method·설치쌍·source snapshot 대조](app-review/2026-11-11-studio-ui/scope-root-audit.json)를 따른다. 처음 전체실패와 source2 native전체실패를 성공으로 바꾸지 않는다.
- 새6은 보통 글씨 채팅과 현재 위치 공유 동의의 일반2,실제320dp/font2의 대화상자4다. 채팅 최신 본문 전체 bounds/기존 독자 스크롤/실제 IME 입력·Back,현재 루트의100m·75초 고지/수동확인/본문 scroll/전체48dp 안전영역/취소 후 불변을 확인했다. 전체70개는 ComponentActivity/in-memory/inert-map fixture이며 실제 Main·휴대폰·Firebase 성공은 별도다.
- [실제 Main 단일 실행](app-review/2026-11-11-studio-ui/live1.json)은 전체 PASS다. nonce 합성 완료 기록/사진으로 홈·옷장·기본1:1/9:16 공유·실제 OS 사진 선택·Morning Miles 키 입력·미니미 조작·갤러리 저장·공유창·다른 UID의 READ·Back/재진입·지도 밖 조작을 확인했다. 저장·cache·수신 PNG bytes를 비교했다. 저장 안내는 실제 추가 스크롤 1회 뒤 확인했다. 접근성 설명의 비율·문구·미니미 포함 복원을 관측했으며 모든 위치 수치를 추정한 것이 아니다. 실제 SNS 게시나 새 지도 제공자 인증은 아니다.
- [Root 원본 PNG 검토](app-review/2026-11-11-studio-ui/root-main-visual-review.json): 실제 Main 원본6장(홈/옷장/기본1:1/사진9:16/미니미 편집/지도)을 열어 내용과 개인정보를 확인했다. 모든 페이지의 실제 Main 촬영이나 사용자 시각 승인은 아니다. 운영 갤러리는 이 가운데 검증한4장이다. 이전 Launcher 결과는 baseline이며 이번 새 실행으로 요구하거나 계상하지 않는다.
- 원래 DB행/전체XML/프로필/account·소유 설정/권한/전원과 전/후/복원후6맵을 확인하고 nonce 출력만 정리했다. [정확한 소유 AVD 종료](app-review/2026-11-11-studio-ui/session-cleanup.json)를 확인했다. 전체 OS 데이터·원래 GPS fix·picker 캐시 전체 복원을 주장하지 않는다.

## 실패 보존과 미확인 범위

처음 큰 글씨 러닝 지도 Text높이0/미니미 준비 timeout을 보존하고 큰 글씨 지도 높이와 장식 Stage의 loading semantics를 보완했다. 기존120초/전체loading조건을 완화하지 않았고 timeout의 구체 원인은 미확정이다. legacy 동의 fixture를 현재 root/run/nonce 동선으로 갱신하고 큰 글씨 채팅의 실제 본문·IME 측정을 강화했다. [실패/수정/원문](app-review/2026-11-11-studio-ui/README.md)을 따른다. 이전 job0/startup_failure는 보존된 과거 실행이며 이번 실제 서명 CI 성공으로 그 원인을 확정하지 않는다.

승인 원본/58ID/13슬롯/9탭/0P·기존 기록/지갑/보유권·100m 동의/FGS/runId를 유지했다. 새 원화·키·Rules·의존성을 추가하지 않았다. 휴대폰·Google/운영 프로필/Firestore 쓰기·야외GPS/장시간 BG·Doze/OEM/API31+·네이버 운영 인증·카카오톡/Instagram 전달·사용자 시각 승인과 공개 서명 APK 런타임은 미검증이다. [지도 연결](MAP_PROVIDER_OPTIONS.md)·[프로필 점검](FIREBASE_PROFILE_CONNECTION_CHECK.md)을 따른다.

## 운영 대시보드와 업데이트 피드

[운영 대시보드](https://coldbrewventi.vercel.app/projects/runmoni) · [대시보드 PR](https://github.com/wjdals988/mydashboard/pull/26) · [main CI](https://github.com/wjdals988/mydashboard/actions/runs/43157941150) · Production `7728819352` · main `b83448d966d66118914e7d10598a37bba09e191e`. canonical13항목과 검토한 PNG4장의 실제 운영 bytes,Node 23개를 확인했다. 다른8프로젝트·이전17개 내역·이전4개 PNG를 보존했다. [운영 근거](app-review/2026-11-11-studio-ui/dashboard-production-verification.json).

preview25/stable null은 이 문서/피드 변경으로 준비한다. canonical raw 전체 bytes는 후속 문서 PR 병합 뒤 별도 확인한다. 기존0.4.2 실제 APK출처 `176a9cbe1112efa05e9fc55d913738ee1778a57f`와0.1.5 실제 APK출처 `75c39ca900b140cfb02c1a47807a5955c8b8db7f`는 그대로다. 다음 작업은 휴대폰 같은키 업데이트·공유/위젯/알림·야외GPS/절전·Google/프로필 확인과 남은 승인 원본 기반 의복·크루 운영 기능이다.
