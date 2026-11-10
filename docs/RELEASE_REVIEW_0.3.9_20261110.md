# 0.3.9-dev 게시 검증 — 2026.11.10 한국 시간

현재 세션을 확인하는 러닝 위젯, OSM 실제 배경·구간 분리 지도, GPX/계정 요약 백업/삭제에 접근하는 독립 기록 페이지와 홈·프로필 위계를 반영했다. 새 러닝/GPS 자동 시작이나 기존 기록·보유권·포인트 초기화는 추가하지 않았다. [구현 PR](https://github.com/wjdals988/runmoni/pull/39) · [APK](https://github.com/wjdals988/runmoni/releases/download/v0.3.9-dev/runmoni-0.3.9-dev.apk) · [릴리스](https://github.com/wjdals988/runmoni/releases/tag/v0.3.9-dev) · [운영 대시보드](https://coldbrewventi.vercel.app/projects/runmoni).

0.3.9-dev/code21, 219,456,174bytes, SHA256 `57895b0237d09ba91b6c73a47da42e67bd8485ccd31f0406cb0ea0a8c1d965a3`, 실제 APK 출처 C `fc08fe08bd0cb87770be56350684f55a41a37c85`. 같은 보관키 `a21359c657f6797f5fb8229c3098e48bc72a45f9d25269f8d41ff5122465df4a`의 실기기 검증 전 개발 프리뷰다. 후속 문서/피드 D로 APK 출처 C를 바꾸지 않는다. 서명 CI42771920281 JVM141·실패/오류/skip0, release lint오류0/경고73. [공개3자산/sidecar](app-review/2026-11-10-running-experience/publication-verification.json) · [서명 CI](app-review/2026-11-10-running-experience/signed-ci-verification.json) · [HEAD200/크기](app-review/2026-11-10-running-experience/public-apk-head-verification.json).

source6 로컬 TEST debug는 JVM141·실패/오류/skip0, lint오류0/경고43이다. Android는 source4 핵심16 보존과 source6 새3개(러닝1·5탭1·실제320dp/font2 상세1)를 구분한다. 단일 source6에서19개를 새 실행한 것이 아니다. source4→6은 라이브 빈 지도 문구·여백2줄뿐이며 기존 SHA 재구성으로 범위를 확인했다. [보존16/새3과 실패 원문](app-review/2026-11-10-running-experience/README.md) · [release C와 실행 입력 비교](app-review/2026-11-10-running-experience/release-source-comparison.json). base34fefff를 실제 APK 출처로 다시 기록하지 않는다.

live7의 기록 중심 제한 동선에서 실제 OS 위젯 추가·런처/진행 중 시스템 알림 표시·위젯 종료 확인의 취소/저장·기록 지도/하단 행동 표시·실제 삭제와 복원을 확인했다. 원래 DB/XML/위젯 캐시/기존 위젯 ID와 검사 소유 통제 환경을 복원했다. 전체 OS가 바이트 동일하다는 뜻이 아니다. 알림 일시정지/재개의 실제 탭과 ACK는 전체 실패·복원이 완료된 live6의 하위 관측만 보존한다. 최종 live7의 notificationRetainedFrom 파일/SHA256와 일치시키며, 최종 동선에서는 알림 제어를 다시 실행하지 않았다. [보존 알림 하위 관측](app-review/2026-11-10-running-experience/live6.json). 최종 제한 동선은 강제종료 뒤 오래된 위젯 재개 버튼의 실제 입력 거절을 새로 검증하지 않았다. 이전 실패 시도의 하위 관측만 별도로 보존한다: 실제 위젯 일시정지/재개(live2.json, live3.json, live4.json). 화면 끔 합성 GPS 진행(live1.json, live2.json, live3.json, live4.json). 이를 live7에서 다시 실행하거나 전체 시나리오 통과로 집계하지 않는다. 세션 네트워크 설정 정리·AVD 종료는 별도 루트 근거이며 이 문서는 없는 종료 증거를 만들지 않는다. [최종 기록 동선](app-review/2026-11-10-running-experience/live7.json).

실제 Main/런처/SystemUI는 Firebase를 비활성화하고 세션 전용 제공 CA·프록시를 쓴 local debug7/API29/AOSP/TCG·합성 GPS 근거다. 제품 배포 TLS 설정은 바꾸지 않았다. 새 네이티브 HTTPS 타일6개는 live2의 독립 관측이며 live7는 그 캐시를 재사용할 수 있다. 공개 서명 APK·일반 휴대폰의 TLS 실행을 대신하지 않는다. [실제 HTTPS6 관측](app-review/2026-11-10-running-experience/live2-native-map-network-observation.json). 지도 원본 타일을 번들/공개하지 않으며 타일 xyz·접속 정보로 조회 영역이 제공자에게 드러난다. Naver 실제 인증/부모 터치 전달은 별도 미검증이다.

루트가 직접 본4장과 실제 운영 PNG4장의 바이트를 대조했다. 각 사진의 실제 MainActivity·런처·SystemUI 출처는 [manifest](app-review/2026-11-10-running-experience/gallery-manifest.json)을 따른다. 사용자 시각 승인이 아니다.
- [홈 · 실제 앱](app-review/2026-11-10-running-experience/live7-main-home.png) — MainActivity
- [기록 지도·하단 조작 · 실제 앱 / 합성 GPS](app-review/2026-11-10-running-experience/live7-main-record-footer.png) — MainActivity
- [러닝 위젯 · 실제 Android 홈 화면](app-review/2026-11-10-running-experience/live7-launcher-running.png) — Launcher
- [러닝 알림 · 실제 시스템 화면](app-review/2026-11-10-running-experience/live7-systemui-running.png) — SystemUI

live1/live2/live3/live4/live5/live6는 탐색/수집 실패와 부분 관측을 보존하며 전체 통과로 합산하지 않는다. callback 검사 추상화 정정·확인창 원인 미확정·지도 로딩 취소 경계와 각 복원 과정은 검토 README/원문을 보존한다.

대시보드 [PR](https://github.com/wjdals988/mydashboard/pull/22) · [main CI](https://github.com/wjdals988/mydashboard/actions/runs/42774707059) · Production `7676047408` · main `32625f21248505790415a61531784f04514220e6`, canonical13항목과 PNG4장을 실제 검증했다. [운영 근거](app-review/2026-11-10-running-experience/dashboard-production-verification.json). preview21/stable null은 이 문서/피드 변경에서 준비하며 병합 뒤 canonical raw 전체 응답을 별도 대조한다.

직전0.3.8 APK 출처 `f55bdd6343770847e2284a75faf40063c22a5951`/SHA256 `c4b484900795defb6d2d5b47d7f9b3545ed99174a07445c2e52edf51937ac76c`와0.1.5 출처 `75c39ca900b140cfb02c1a47807a5955c8b8db7f`를 보존한다. 승인 원본·준비36/미준비22·58ID/9탭/13슬롯·0P/성별·Rules/보관키를 바꾸지 않았다. 휴대폰·Google 로그인·운영 쓰기·야외 GPS·Doze/OEM·API31이상·공개 서명 APK 직접 실행·사용자 시각 승인은 별도 미검증이다.
