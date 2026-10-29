# 0.1.6-dev GitHub 서명·초안 검증 — 2026-10-29

현재 상태는 **실기기 검증 전 테스트용 공개 prerelease**다. 사용자가 실제 휴대폰 확인이 가능하다고 한 뒤 APK를 대시보드에 올리도록 요청했다. 이 요청을 이번 검토 APK의 배포 방식 변경으로 적용했으며 설치/로그인 성공으로 기록하지 않는다. [공개 릴리스](https://github.com/wjdals988/runmoni/releases/tag/v0.1.6-dev)와 [서버 공개 검증 기록](release-review/2026-10-29/publication.json)을 따른다.

| 항목 | 실제 결과 |
|---|---|
| APK | 0.1.6-dev / versionCode7, com.runmoni.app, non-debuggable |
| 빌드 출처 | 9dd409f00bab54f9ebf7f0ce3f85f36ff54b1750, sourceDirty=false |
| 크기 | 218,201,869bytes |
| SHA256 | de19ed6cc9337fce39bc321818e8979df80da7462dca4d1e584aadc03fda34cf |
| 인증서 | a21359c657f6797f5fb8229c3098e48bc72a45f9d25269f8d41ff5122465df4a |
| 빌드 | GitHub cold build6분30초,81작업(77실행/4캐시) |
| JVM | 실제 XML 기준31개 검사, 실패/오류/skip0 |
| release lint | 오류0, 경고57. 이 중 도구/의존성 버전 알림30개 |
| 전체 workflow | [40858081567](https://github.com/wjdals988/runmoni/actions/runs/40858081567), 성공 |
| 서버 자산 | APK·metadata·SHA256SUMS3개, 최신 digest/size/정확한 초안 URL 일치 |
| 작은 sidecar | 본문 다운로드 후 실제 digest·APK 정보·체크섬 일치 |
| runner 키 | 임시 키 삭제 단계 성공 |

이번 CI의 XML5개·lint XML·정제된 build log와 [기계 판독 기록](release-review/2026-10-29/verification.json)을 보존했다. 이 클라우드에서 APK 본문은 다시 다운로드하지 않았고 CI의 실제 apksigner/패키지/내장 revision 검사와 GitHub 서버 digest를 대조했다. 과거 호스트 후보의 lint27과 이번 CI의57을 섞지 않는다.

PR2와 CI 후속 PR3~7은 사용자 승인 범위에서 정상 병합했다. 초기 실패는 원문 진단 부족, 실제 SDK 경로 충돌, Actions 토큰의 사용자용 push 힌트 가정이었다. 실제 미공개 tag404/임시 untagged URL도 검증해 재시도 중복과 URL 불일치를 방지했다. 기존0.1.5 초안과 출처75c39ca…, 이전639b1f9/de49bb7 후보의 출처·해시는 그대로 보존했다.

이 서명 APK의 Android 실행·실제 휴대폰·Google 로그인 검증은 아직 없다. 기존 debug 화면·Android 테스트를 이번 APK 실행으로 합산하지 않는다. 여자 기본형·의상58종·수영복 옷장 통합과 새 원화 시각 승인은 남아 있다. 원화/앱 UI를 추가 편집하지 않았으며 DESIGN의 기존 awesome-design-md/Aseprite 참고 범위를 유지한다.

실제 Android 기기에서 설치/실행/로그인/변경 기능 확인은 남아 있다. 이전 키 설치본은 새 키로 덮어쓰지 못하므로 기기 기록을 먼저 내보내고 재설치한다. 홈 기본 미니미, 나/옷장 표시, 앱 정보0.1.6-dev/code7, 미준비 아이템의 구매 중단과 보유 내역/포인트 보존을 확인한다. Google 설정 미비나 로그인 실패는 성공으로 기록하지 않는다. 실기기 확인 결과를 받으면 성공/실패를 따로 기록해 후속 수정한다. 현재 배포는 사용자 요청에 따른 검증 전 테스트용 공개다.

공개 시점 main과 새 v0.1.6 태그는 실제 APK 출처9dd409f와 일치했다. 이후 공개 피드/문서/테스트 fixture 커밋을 병합해도 APK의 sourceRevision은9dd409f로 유지한다.

대시보드 PR9는 main f0fe80a에 병합했고 Vercel 운영 배포 success를 확인했다. [실제 페이지](https://coldbrewventi.vercel.app/projects/runmoni)에서 최신 개발 설명·기존0.1.1 APK 링크·미게시0.1.6 링크 없음까지 확인했다. 새 APK 공개 다운로드 교체와 구분한다.

초안 검증 당시 verification.json의 draft/미공개 표시는 당시 관찰 기록이다. 이후 공개 상태와 canonical URL·동일 자산 확인은 publication.json에 별도로 기록했다. 자산 재업로드/교체·새 키 생성·새 Android 빌드는 하지 않았다. 공개 피드에는 실제0.1.6/code7·서명 지문·해시·physicalDeviceTests0과 미검증 안내를 반영한다.

이후 대시보드 PR10을34f2924에 병합했고 운영 Vercel success와 실제 페이지의0.1.6 APK href/설명/미검증 표시/SHA256을 확인했다. 기존 Node23개·서버 테스트/lint/build를 통과했고 다른 프로젝트/순서/과거 이력은 보존했다. 공개 APK HEAD는200/218201869bytes이며 본문은 받지 않았다.
