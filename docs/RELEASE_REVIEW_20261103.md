# 0.3.0-dev 게시 검증 — 2026-11-03 (한국 시간)

사용자가 요청한 서브에이전트 상호 검토·수정·커밋/푸시/PR과 검토 APK/대시보드 반영을 진행했다. [앱 PR19](https://github.com/wjdals988/runmoni/pull/19)를 main에 병합하고 기존 보관 키의 **실기기 검증 전 개발 prerelease**를 공개했다. 실제 APK 출처는 `01e37ab3f7ac13cc3b69d37576a6d623274de101`이며 후속 피드/문서 커밋으로 바꾸지 않는다.

## 실제 공개 자산

- [GitHub 릴리스](https://github.com/wjdals988/runmoni/releases/tag/v0.3.0-dev) · [APK 다운로드](https://github.com/wjdals988/runmoni/releases/download/v0.3.0-dev/runmoni-0.3.0-dev.apk)
- 0.3.0-dev / code12, package `com.runmoni.app`.
- 218496781bytes, SHA256 `a080129a7ac2d2cb75e507d476e28f0e72308276ba70d3b4232125928d8af387`.
- 기존 보관 인증서 SHA256 `a21359c657f6797f5fb8229c3098e48bc72a45f9d25269f8d41ff5122465df4a`.0.1.6/0.2.0/0.2.1과 일치하며 새 키를 만들지 않았다.
- 게시 시각(UTC): `2026-11-02T19:42:05Z`. 한국 날짜와 UTC 날짜를 구분한다.

[공개/3자산 검증](app-review/2026-11-02-crew-ux/publication-verification.json) · [공개 HEAD200/크기](app-review/2026-11-02-crew-ux/public-apk-head-verification.json). 실제 CI 파일의 서명·해시와 GitHub 서버 digest/작은 sidecar 본문을 대조했다. 클라우드 검토자가208MiB APK 본문을 다시 내려받아 독립 해시 검사한 것은 아니다.

## 검증 범위

[서명 CI41508813724](https://github.com/wjdals988/runmoni/actions/runs/41508813724) 성공. JVM45개/실패·오류·skip0, release lint오류0/경고60. 실제 APK의 기존 서명·non-debuggable·패키지/버전/code·내장 출처를 CI에서 검사하고 임시 서명/Android 설정 파일 제거 단계도 통과했다. [CI 근거](app-review/2026-11-02-crew-ux/signed-ci-verification.json).

현재 인증서·Android/Web OAuth 설정과 운영 공개 목록/고정 합성 부모의 자식 조회는 `ready=true`다. [준비 상태](app-review/2026-11-02-crew-ux/connected-build-readiness.json). 이는 새 운영 Rules 버전·익명 쓰기·Google 대화형 로그인·실기기를 확인한 결과가 아니다.

실제 debug Android의 MainActivity/로컬HTTP·지갑1개110.626초, native320dp/font2 프로필1개83.866초와 하단 라벨 기준선 차이0px 등은 [원시 결과/실제 사진](app-review/2026-11-02-crew-ux/README.md)에서 실행별로 구분한다. 실제 휴대폰·공개 서명 APK 실행·Google/운영 두 기기 쓰기는 미검증이다. 원화는 승인 원본 두 행×8프레임을 그대로 사용하며 미준비 의상은0P 선택만 저장하고 기본형을 표시한다. [엄격 검토](CREW_CLOSET_REVIEW_20261102.md).

## 규칙·데이터·이전 출처

새 부모 소속/삭제 권한 Rules의 소유자 운영 게시가 필요하다. 현재 클라우드에는 관리 연결이 없고 게시 완료 응답도 없다. [최신 전체 규칙/소유자 절차](FIREBASE_COMMUNITY.md). 기존 설정1~5번 완료 보고를 이번 규칙 게시로 바꾸지 않는다. 개인 러닝·포인트·보유권·13슬롯/58개 ID와 삭제한 파티의 대화 문서/러닝 참조를 보존한다.

이전0.2.1의 출처 `c6bc5739746fe176269fed3bf64360417b50875e`/SHA256 `2b74ee38f56be2fcfbe88a5b74b2a11c84c8817c961da467a5f7151f12833218`,0.1.5의 `75c39ca900b140cfb02c1a47807a5955c8b8db7f`와 이전 서명/검증 기록은 변경하지 않는다. 공개 결정 설명에 템플릿의0.2.1 문구가 남은 것은0.3.0-dev로 교정했으며 실제 APK/sidecar 메타데이터·바이트·출처 변경은 아니다.

## 피드·대시보드

검증한0.3 metadata를 [피드/문서 PR20](https://github.com/wjdals988/runmoni/pull/20)으로 반영한다. stable은 null이며 공개 프리뷰만 갱신한다. main 통합 뒤 공개 raw 피드의 실제 응답 확인은 PR20의 최종 게시 댓글에 남긴다. 피드/문서 커밋을 APK 출처로 사용하지 않는다.

[대시보드 PR13](https://github.com/wjdals988/mydashboard/pull/13)은 main `d65028627f01cad235ca0c553201631fe7f3e8f2`에 병합됐고 PR CI41512884899·main CI41513392797의 test/audit/lint/build와 Vercel Production7501775796 success를 확인했다. 로컬 Node23개/실패0, coverage lines92.69%/branches86.14%/functions100%다.

[운영 페이지](https://coldbrewventi.vercel.app/projects/runmoni)의 실제 HTTP200 응답에서0.3.0-dev/code12·APK 링크·218496781bytes·SHA256·미검증 안내·갤러리5개 경로를 확인했다. 운영 PNG5장도 원본과 바이트 SHA256이 일치한다. [운영 검증 근거](app-review/2026-11-02-crew-ux/dashboard-production-verification.json). 사진은 debug MainActivity/로컬HTTP이며 공개 서명 APK/실기기/운영 Firebase 실행으로 표시하지 않는다. 공개 APK 바이너리는 GitHub Release에만 두고 다른 프로젝트/기존 이력을 보존한다.
