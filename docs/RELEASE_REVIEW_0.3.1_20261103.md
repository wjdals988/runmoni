# 0.3.1-dev 게시 검증 — 2026-11-03 (한국 시간)

Instagram 분석과 awesome-design-md를 우선순위별로 적용한 UI를 [앱 PR21](https://github.com/wjdals988/runmoni/pull/21)로 main에 병합하고 같은 보관 키의 **실기기 검증 전 개발 prerelease**를 공개했다. 실제 APK 출처는 **`3074c0f40797a2452b36e4277dd559f94a31ac07`**다. 후속 피드/문서 main 커밋으로 이 출처를 바꾸지 않는다.

## 실제 공개 자산

- [GitHub 릴리스](https://github.com/wjdals988/runmoni/releases/tag/v0.3.1-dev) · [APK 다운로드](https://github.com/wjdals988/runmoni/releases/download/v0.3.1-dev/runmoni-0.3.1-dev.apk)
- 0.3.1-dev / code13, package `com.runmoni.app`.
- **218496781bytes**, SHA256 **`2f77849607140903de2bb80611bd4ce28a2c0d418c59fd0d7d74d880db56b16c`**.
- 기존 보관 인증서 SHA256 `a21359c657f6797f5fb8229c3098e48bc72a45f9d25269f8d41ff5122465df4a`. 이전0.1.6/0.2.0/0.2.1/0.3과 동일하며 새 키를 만들지 않았다.
- 실제 게시 시각 UTC `2026-11-03T07:04:09Z`, 한국 날짜 **2026-11-03**. UTC와 한국 날짜를 구분한다.

[공개 자산](app-review/2026-11-03-context-ui/publication-verification.json) · [서명 CI](app-review/2026-11-03-context-ui/signed-ci-verification.json) · [공개 HEAD200](app-review/2026-11-03-context-ui/public-apk-head-verification.json). GitHub 서버3자산의 digest/크기·작은 sidecar 본문과 실제 CI 서명 검증을 대조했다. 공개 HEAD200/크기는 확인했으며 클라우드 검토자가 APK 전체를 다시 다운로드해 독립 해시 검사한 것은 아니다.

## 코드·실행 검증 범위

[서명 CI41591649566](https://github.com/wjdals988/runmoni/actions/runs/41591649566) 성공. JVM45개/실패·오류·skip0, release lint오류0/경고60. 실제 APK 서명·non-debuggable·패키지/버전/code·내장 출처를 CI에서 검사했다. 서명·Android 설정 원문을 Git/채팅에 복사하지 않는다.

홈은 러닝 시작·실제 오늘 합계·예정 파티·최근 기록을 우선하고 크루/파티는 일정·거리·페이스·인원을 비교하는 목록으로 정리했다. 옷장은 작은 선택 타일과 명확한 원화 준비 상태를 사용한다. 기존5탭·남녀·무료 선택/포인트/보유권·9탭/13슬롯/58개 ID·승인 원본은 보존했다. [상호 검토](CONTEXT_UI_REVIEW_20261103.md) · [DESIGN 기준](../DESIGN.md).

실제 debug Android의 최종5탭/반응형2개106.224초·MainActivity/로컬HTTP1개87.99초·native320dp/글씨200%1개57.543초와 unclipped CTA/기준선·실제 사진은 [실행별 근거](app-review/2026-11-03-context-ui/README.md)에 기록했다. 초기10개 중1개 테스트 오류와 최초 통과 사진에서 발견한 홈 버튼 잘림, 수정/재실행 결과를 보존한다. 이 수치를 공개 서명 APK/실제 휴대폰/운영 Firebase 실행으로 합산하지 않는다.

[연결 준비 상태](app-review/2026-11-03-context-ui/connected-build-readiness.json)는 현재 인증서·Android/Web OAuth 설정/운영 공개 조회의 ready 결과다. 새 운영 Rules 버전·익명 쓰기·Google 대화형 로그인·실기기를 확인한 결과가 아니다. 새 Firestore 규칙 소유자 게시·실제 휴대폰·Google 대화형 로그인·운영 두 기기 쓰기는 별도 대기다. 미준비 의상은 선택만 저장하고 승인 기본형을 표시하며 실제 의복/색상 완성으로 표시하지 않는다.

## 이전 자산·다음 개발

이전0.3 APK는 출처 `01e37ab3f7ac13cc3b69d37576a6d623274de101`,218496781bytes/SHA256 `a080129a7ac2d2cb75e507d476e28f0e72308276ba70d3b4232125928d8af387`로 보존한다. [이전0.3 게시 검증](RELEASE_REVIEW_20261103.md)은 변경하지 않는다.0.1.5 출처 `75c39ca900b140cfb02c1a47807a5955c8b8db7f`를 포함한 과거 APK 출처·자산·검증 플래그도 재작성하지 않는다.

다음 독립 작업 F03은 승인 얼굴/체형을 유지하는 실제 의복·수영복 프레임 레이어/명시 마스크·색상이다. 사용자 시각 승인·TalkBack/다른 실기기·야외 GPS·계정 복구는 별도 후속이다.

## 피드·대시보드

대시보드 PR14/main `05202f65fe2b288cfe6943d74af477ef79e4dd39`·PR CI41594079975/main CI41594302891·Vercel Production7512608845 success와 실제 운영 HTTP200/0.3.1-dev(code13)/다운로드/크기/해시·PNG5장 원본 일치를 확인했다. Node23개/실패0·coverage lines92.69%/branches86.14%/functions100%다. [운영 검증 근거](app-review/2026-11-03-context-ui/dashboard-production-verification.json). 공개 APK 바이너리는 GitHub Release에만 두며 다른8프로젝트·기존 내역5개와 이전 자산을 보존한다.

[운영 페이지](https://coldbrewventi.vercel.app/projects/runmoni)의 실제 HTML 텍스트·다운로드 anchor·갤러리 원본 바이트를 대조했다. 브라우저 클릭/사용자 시각 승인이나 서명 APK/실기기/운영 Firebase 실행으로 확장하지 않는다.

공개 preview 피드는 [PR22](https://github.com/wjdals988/runmoni/pull/22)에 반영하며 stable은 null이다. main 통합 뒤 canonical raw 피드의 실제 응답 확인을 PR22 최종 게시 댓글에 남긴다. 후속 피드/문서 main 커밋으로 APK 출처3074c0f를 바꾸지 않는다.
