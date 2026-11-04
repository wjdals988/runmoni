# 0.3.4-dev 게시 검증 — 2026-11-05 한국 시간

[앱 구현 PR](https://github.com/wjdals988/runmoni/pull/29)을 main에 통합하고 같은 보관키의 실기기 검증 전 개발 prerelease를 공개했다. 실제 APK 출처는 **`e2edc2a7ae349422763fc9727aa4e5360a4214e7`**다. 후속 피드/문서 커밋이나 새 main으로 바꾸지 않는다.

## 실제 공개 자산

- [개발 릴리스](https://github.com/wjdals988/runmoni/releases/tag/v0.3.4-dev) · [APK](https://github.com/wjdals988/runmoni/releases/download/v0.3.4-dev/runmoni-0.3.4-dev.apk) · [운영 대시보드](https://coldbrewventi.vercel.app/projects/runmoni).
- 0.3.4-dev/code16, `com.runmoni.app`, non-debuggable, **219017510bytes/SHA256 `a1b078587c9357cef41aeaa884c41ee2735a6c83768f614e0354b757ba671947`**.
- 기존 인증서 SHA256 `a21359c657f6797f5fb8229c3098e48bc72a45f9d25269f8d41ff5122465df4a` 유지. 새키·Secret 변경 없음.
- [서명 CI41885534878](https://github.com/wjdals988/runmoni/actions/runs/41885534878) 성공: JVM84/실패·오류·skip0, release lint오류0·Fatal0·경고64. 실제 APK의 서명·패키지/버전·non-debuggable·내장 출처를 CI에서 검사했다.
- 실제 게시 UTC `2026-11-04T18:39:42Z`. [3자산·sidecar](app-review/2026-11-04-closet-redesign/publication-verification.json) · [서명 CI](app-review/2026-11-04-closet-redesign/signed-ci-verification.json) · [실제 공개 HEAD](app-review/2026-11-04-closet-redesign/public-apk-head-verification.json).

GitHub 서버의 APK digest/크기와 작은 sidecar 본문을 대조했고 공개 HEAD200/Content-Length를 확인했다. 검토자가 전체 APK를 다시 내려받아 독립 해시를 계산한 것은 아니다. 연결 readiness의 휴대폰/Google/익명 제공자·운영 쓰기 false를 성공으로 바꾸지 않았다.

## 변경과 검증

옷장 마지막 품목/색상 선택과 Back, 골라도 그림이 같거나 미리보기가 다르던 문제를 개선했다. 루트 전체 화면·단순 목록과 공통8dp행동/12dp표면·섹션 구획을 적용하고 제품 원화/수영복 비교 진입을 제거했다. 승인 원본·58ID/9분류/13슬롯·0P·성별·보유권·포인트를 보존하며36개 ID(기본13+비기본23)를 표시한다. 미준비22개와 새 출력 사용자 승인은 별도다. [구현 검토](CLOSET_REDESIGN_REVIEW_20261104.md).

최종 debug build7 JVM84/lint0오류34경고, Android **build6 기준4+7+10+5=26회 + build7 native8회**와 build7 실제 MainActivity 외부 입력·6장 사진·프로세스 재시작/원문·설정 복원을 확인했다. 제품 변경은 채팅1파일, 검사 변경은 native2파일이고 나머지 소스/렌더 자산은 같음을 확인해 기준26회를 유지했다. 같은 바이너리에서34회를 새 실행한 것은 아니며 ConsumerCloset 환경별 반복도 고유 메서드 수가 아니다. build6 native2실패를 제외하고 작은 창 채팅 이동 버튼은 제품 수정, 고정 선택기 스크롤/접힌 필터 기대는 검사 수정으로 구분했다. 이전 준비 실패·중단·시도4/5실패·팝업 관찰/별도 진단도 [실행별 근거](app-review/2026-11-04-closet-redesign/README.md)에 보존한다. 팝업을 확정 원인으로 단정하지 않으며 공개 서명 CI를 debug Android 실행과 합치지 않는다.

사진6장과 운영 갤러리4장은 API29/AOSP/TCG·로컬 검토 프로필·TEST서명·Firebase미설정 debug MainActivity다. 호스트 PNG는 별도 설계 자료이며 테스트 컴포넌트 fixture·공개 서명 APK·실기기·Google·운영 데이터 실행 사진으로 표시하지 않는다. 사용자 시각 승인은 대기 중이다.

## 운영 대시보드와 피드

대시보드 [main CI](https://github.com/wjdals988/mydashboard/actions/runs/41889846055)와 Production `7554678316` 성공, canonical HTTP200/실제 다운로드·버전/크기/해시·개발프리뷰/출처 문구와 새 PNG4장의 응답 바이트 일치를 확인했다. 다른8프로젝트와 순서/sourceUrl, 기존8개 업데이트 내역·이전 갤러리3개 파일 바이트를 보존했다. [실제 검증](app-review/2026-11-04-closet-redesign/dashboard-production-verification.json). 브라우저 클릭이나 사용자 승인으로 확대하지 않는다. [이전 자료 보존 근거](app-review/2026-11-04-closet-redesign/dashboard-preservation-verification.json)도 확인했다. 초기 버전 문자열 검사는 React 텍스트 노드 사이 공백 때문에 실패했으며, 실제 다운로드 구역에서 버전/code를 공백에 독립적으로 비교해 재검증했다. [파서 수정 경계](app-review/2026-11-04-closet-redesign/dashboard-verification-parser-failure.json).

이번 게시 PR에서 preview16으로 갱신하며 stable은null이다. main canonical raw HTTP200/전체 JSON 일치는 PR 최종 게시 검증에 별도로 남긴다. 이 문서 생성 시점에 아직 게시하지 않은 피드의 원격 반영을 성공으로 기록하지 않는다.

## 남은 경계와 이전 출처

미준비22개 원화·헤어/표정/양말/모자/세트/수영복·새 의복 사용자 시각 검토가 남았다. 실제 휴대폰 업데이트/기록 보존·야외GPS/화면 끔/배터리, Google 연결·취소·충돌/다른 기기 프로필 복원/요약 백업, 운영 두 기기 파티/채팅/정원/삭제 권한은 미검증이다. 최신 Rules 게시 완료는 소유자 보고이며 새 Rules 변경은 없다. 기본 Firebase 옷장·색상은 기기 저장이고 Google 연결이 원본GPS·포인트·전체 옷장 백업은 아니다.

이전0.3.3의 실제 출처 `abee084b115abfb8693a9fe92c03e024c9adb29d`·218882079bytes/SHA256 `6fa1b77a70b022eeee92ab1033d5aa0bd287ae3f503f178bd0ced4beea1c612e`는 [이전 검증](RELEASE_REVIEW_0.3.3_20261104.md)에 그대로 보존했다.0.1.5 출처 `75c39ca900b140cfb02c1a47807a5955c8b8db7f`도 새 main으로 바꾸지 않는다.
