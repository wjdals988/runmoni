# 0.4.1-dev 게시 검증 — 2026.11.11 한국 시간

<!-- verified-release-0.4.1-v1 -->
위젯의 한글/긴 숫자 잘림을 줄이도록 전체·세로·최소형을 실제 크기/글자 설정으로 측정한다. 충분한 공간에서는 거리·시간·제어를 유지하고 작은 크기/큰 글씨에서는 앱 열기로 연결한다. 최초/크기 미상 프리뷰는 아이콘 행동이며 글자 크기를 강제로 줄이지 않는다. 홈은 미니미 옆 기온·바람·강수·옷차림을 보여주고 좁거나 큰 글씨에서는 러닝 시작 뒤로 펼친다. 크루 양말·니삭스는 남녀 원본 프레임에, 그린·코랄 쇼츠는 남자 원본에만 연결했다. 여자 양말은 기존 타이츠 위 의복이며 숨은 피부를 생성하지 않는다. [구현 PR](https://github.com/wjdals988/runmoni/pull/43) · [APK](https://github.com/wjdals988/runmoni/releases/download/v0.4.1-dev/runmoni-0.4.1-dev.apk) · [릴리스](https://github.com/wjdals988/runmoni/releases/tag/v0.4.1-dev) · [대시보드](https://coldbrewventi.vercel.app/projects/runmoni).

0.4.1-dev/code23, 219,772,021bytes, SHA256 `4866a905339fe50763a30bbfe9df4954cbf74cbfbdbc2be112bd25bef9e7614b`, 실제 APK 출처 C `1eff94141c9449c6f707bfae6ff2472cd68e2386`. 같은 보관 서명키 `a21359c657f6797f5fb8229c3098e48bc72a45f9d25269f8d41ff5122465df4a`로 공개한 실기기 검증 전 개발 프리뷰다. 후속 문서/피드 D로 출처 C를 바꾸지 않는다. 서명 CI42861574401의 **새 JVM167·실패/오류/skip0**, release lint오류0/경고75를 확인했다. [공개3자산/sidecar](app-review/2026-11-10-widget-wardrobe/publication-verification.json) · [서명 CI](app-review/2026-11-10-widget-wardrobe/signed-ci-verification.json) · [HEAD200/크기](app-review/2026-11-10-widget-wardrobe/public-apk-head-verification.json).

[이번 로컬 검토](app-review/2026-11-10-widget-wardrobe/README.md)는 build2의 새 JVM167/lint오류0·경고45와 build3/4의 JVM 재사용을 구분한다. Android는 source2의 위젯7개 보존, source3의 확장 의복4·의복 회귀4·승인 기본3=11개 보존, source4의 홈 배치2·날씨 상태1·옷장1·탭1=새5개다. 고유23개 범위지만 동일 제품 APK에 서로 다른 테스트 APK를 썼으며 단일 최신 쌍에서23개를 새 실행한 결과가 아니다. source2→3은 기존 ear/headwear null의 codec 정규화0 기대, source3→4는 이전 예보 접미사의 substring 검사만 고쳤다. 두 원래 전체 실패와 실패 그룹 내부의 부분 통과는 보존하며 합계에 넣지 않는다. 초기 build1의 Kotlin DSL receiver 참조 실패도 성공으로 바꾸지 않는다. 이전0.4의 Rules160·SDK·Android 합계/사진을 이번 새 검증으로 합산하지 않는다.

호스트 최종208사례는 material/alpha·마스크 바깥/입력·독립 신발 보호 불변 검사다. [최종 결과](app-review/2026-11-10-widget-wardrobe/host-material-final.json) · [정지 출력 검토](app-review/2026-11-10-widget-wardrobe/host-final-visual-review.json). 첫 시안의 여자 발목 틈/밝은 조각과 두 번째 시안의 접힌 다리 허벅지 범위 누출은 각각 시각 미채택으로 보존했다. 최종 양말/장비 가림 보강을 새 사용자 원화 승인이나 전체8프레임 동작의 시각 승인으로 바꾸지 않는다. Aseprite 실제 실행/제작물이라고 표시하지 않는다.

[실제 Main4장](app-review/2026-11-10-widget-wardrobe/gallery.json)은 Root가 직접 본 로컬 TEST debug/API29/TCG 화면이다. 실제 선택7회의 prefs 저장/잔액·보유/다른 필드 보존, 홈 native MET 출력과 날짜/시각·기온·바람·강수의 CTA 위 bounds, 원래 XML/profile/DB rows·소유 환경 복원을 확인했다. 제공 debug CA/guest proxy·서울 합성 GPS이며 공개 서명 APK/휴대폰 실행이 아니다. 새 HTTP status와 요청 좌표는 독립 측정하지 않았다. [Main 실행](app-review/2026-11-10-widget-wardrobe/live2.json)의 사진/좌표와 장식 미니미 옆 배치의 Root 시각 판단은 별개다. [배포 C와 전체 실행 입력 비교](app-review/2026-11-10-widget-wardrobe/release-source-comparison.json).

- `live1.json`: 원래 전체 passed=False, 완료=True. 사진1장/선택 관측2회는 이력으로만 보존하며 완료 Main4장/7회에 합산하지 않는다.

[별도 실제 Launcher2장](app-review/2026-11-10-widget-wardrobe/widget2.json)은 font1/font2에서 **서로 다른 OS pin 배치**의 idle 위젯이다. 같은 위젯 리사이즈·실제 러닝 제어·notification 조작·glyph ink 자동 측정으로 확대하지 않는다. 글립/텍스트·48dp 검사는 별도 factory7에 있다. 기존 위젯 ID/캐시 존재와 바이트·폰트/앱 XML/profile/DB rows·소유 설정 복원을 확인했다. Main 사진4장과 Launcher 사진2장의 출처를 합치지 않는다. 사용자 시각 승인은 아직 별도다.

대시보드 [PR](https://github.com/wjdals988/mydashboard/pull/24) · [main CI](https://github.com/wjdals988/mydashboard/actions/runs/42866079843) · Production `7689395596` · main `6465261db8190ea154987d2c907f1ad35fb1a297`. canonical13항목과 원본 Main PNG4장 바이트, 로컬 Node23개를 확인했다. 기존 다른8프로젝트·이전15개 릴리스노트·이전4장 PNG를 보존했다. [운영 근거](app-review/2026-11-10-widget-wardrobe/dashboard-production-verification.json). preview23/stable null을 이 문서/피드에서 준비하며 병합 뒤 canonical raw 전체 바이트를 별도 확인한다.

이번 버전은 Firebase 인증/Rules/색인·앱 의존성·58개 아이템 ID/9탭/13슬롯·0P·기존 기록/포인트·보관키를 바꾸지 않는다. 기존0.4/source `eafdb48d80558114c7322072319c709c67fc3e82`, 0.3.9/source `fc08fe08bd0cb87770be56350684f55a41a37c85`, 0.1.5/source `75c39ca900b140cfb02c1a47807a5955c8b8db7f`는 유지한다. 운영 Firebase·실제 두 기기 공유·Google·휴대폰 런처·야외 GPS/장시간·Doze/OEM·API31이상·공개 서명 APK 직접 실행·사용자 시각 승인은 별도다. 지난0.4의 운영 설정 안내를 새 테스트 성공으로 바꾸거나 같은 설정을 이번 변경이라고 재요청하지 않는다.
