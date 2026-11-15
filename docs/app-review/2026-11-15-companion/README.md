# 0.7.0-dev 공개 상태 — 2026-11-16

기존 보관키0.7.0-dev/code29, 실제 APK출처 `ca54251a780f56b60aaecce0dbeb94a4f33061ed`, 172,509,168bytes, SHA256 `79d006bffacec3adc377476d97de6881424cfba48fc7b41b677f6b991228a152`. 앱PR58·서명CI43761237483·대시보드PR30/main `9060d3661b70007ca189df12485bdae9075b9f57`와 운영13항목/새MainPNG4/새Node23개를 확인했다. [공개/연결CI](release-publication/proof.json)·[실제C와6맵](release-source-comparison.json)·[운영페이지13/PNG4](dashboard-production.json)·[배포도구 교정/실패](RELEASE_INTEGRATION_NOTES.md)·[게시 검토](../../RELEASE_REVIEW_0.7.0_20261116.md). 공개 서명 APK 본문은 다시 내려받지 않았고 실제 휴대폰/Google/운영 저장 성공은 별도다. canonical raw피드는 문서/피드 병합 뒤 확인한다. 아래는 같은 후보의 입력별 검토 이력이다.

# 러닝 동반자 개편 검토

후보0.7.0-dev/code29. 한국 시간2026-11-16에 UI·위젯 가독성과 여백, 동일UID 연결 유지, 출발 전 위치 공개 선택, 벡터 지도, 원본 포즈의 의복/장비를 검토한다. [계획](../../../design-system/runmoni/pages/companion-redesign.md).

공개0.6.0 APK의 실제 출처9a572de8da88ef42bf740d9424b22c7fa14e17cb는 바꾸지 않는다. 새 실행·서명·게시 전에는 기존 검사 결과를 새 입력의 결과로 합산하지 않는다. 서버ACK·로컬 캐시·실제 운영읽기·문서쓰기·Google 로그인은 구분한다.

운영 닉네임 오류는 소유자가 Rules를 게시하기 전 exact read403으로 재현했다. 게시 완료 응답 후 새 임시 익명 계정의 own profile/nickname exact read는 모두404/NOT_FOUND로 바뀌었으며 임시 Auth 계정 삭제200을 확인했다. 운영 profile/nickname 문서 쓰기는0회다. 정확한 배포 Rules 본문, 운영 저장 transaction 및 휴대폰 SDK 성공은 미검증이다. 비밀 값/토큰과 개인 계정UID는 공개 자료에 넣지 않는다.

원본SHA256 f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65를 보존한다. 호스트 출력과 실제 Android 원본 스크린샷, 사용자 승인을 분리한다. 첫 산타 호스트 검사에서 손 보호 영역 침범을 발견했으며 실패를 보존하고 보호 입력을 추가한다. 검사를 완화해 통과시키지 않는다.

현재 최종 로컬 입력은 [binary-build6](binary-build6.json)이며 새 JVM219개·lint 오류0/경고66과 TEST APK pair를 확인했다. 실제 Main·Launcher·같은 키 서명·공개 APK·대시보드는 아직 후속 단계다.

## 입력별 실패와 보존 범위

- [input3 일반 실행](android-review1.json)은 전체 false다. 홈 정렬과 옷장 스레드 예외가 난 두 그룹의 부분 성공은 채택하지 않는다. 원래 데이터·관리한 환경 복원과 [전용 AVD 종료](session1-cleanup.json)를 보존한다. 실제 새 의복 renderer15개를 포함한 변경 없는77개는 최종 소스 동등성 검토 후에만 보존한다.
- [input4 일반36](android-review2.json)은 통과했다. [native1](native1.json)의 큰 글씨 홈 CTA/nav4.67dp 겹침은 실제 배치 실패이며 해당12개 그룹의 부분 성공은 제외한다. [landscape1](landscape1.json)의 제품1개 성공과 정책 복원 실패, [별도 복원-only0개](landscape1-rotation-recovery.json)를 분리한다.
- [지도 SDK4](map-sdk1.json)는 실패 처리/배치 검사다. [온라인1](map-online1.json)은 이전 프록시 연결 실패, [온라인2](map-online2.json)는 실제 지도 렌더·드래그 뒤 핀치 관측 조건 실패다. 두 온라인 그룹의 최종 채택 수는0이다. [부분 장면 검토](map-online2-root-partial-review.json)는 Main/GPS나 전체 제스처 성공이 아니다.
- [Firebase SDK1](firebase-sdk1.json)은 실패했다. [SDK2](firebase-sdk2.json)의 로컬 demo3개는 통과했고, 외부 프록시 wrapper 미완료와 [원래5개 설정의 후속 복원-only](firebase-sdk2-network-proxy-restoration-only.json)를 분리한다. 운영 프로필 쓰기·Google 로그인은 이 결과가 아니다.
- [AVD2 종료](session2-cleanup.json)는 원문 실패와 후속 복원들을 연결한다. 당시 input5의 작은 화면·큰 글씨·지도 온라인 검사를 새 전용 AVD에서 진행 중이며 계획137을 최종 성공 수로 발표하지 않는다. 동일 APK137개 통과로 표시하지 않는다.


## 새 로컬 입력3

[source-freeze3](source-freeze3.json)와[binary-build3](binary-build3.json): 새JVM209, lint오류0·경고64, 로컬TEST APK pair 생성 성공. 첫진단상수오류·input1 테스트java.desktop 컴파일실패·input2 캐시누락과부분JVM·warm-helper변형선택실패를보존한다. input3만최종로컬빌드로세며 아직Android실행결과가아니다. 실제Android·Main/Launcher·서명·게시근거는후속검증이다.

## input6 최종 지도·범위

[새 범위137](android-scope-summary.json)은 현재 후보의 input3/4/5/6에서77/29/26/5개다. 동일 APK 전체137개가 아니다. [정적 동등성](INPUT5_TO_INPUT6_VALIDATION_SCOPE_REVIEW.md)·[독립 순서 검토](NATIVE_MARKER_INPUT6_PEER_REVIEW.md)·[새 빌드](binary-build6.json)·[SDK4](map-sdk2.json)·[실제 픽셀1](map-online5.json)·[Root 사진 검토](root-map-input6-visual-review.json)를 분리한다.

새 실제 픽셀 검사와 두 시스템 원본에서 초기 산타·교체 후 곰 의상 및 한글 이름·경로를 확인했다. 이전 input5 두 자동PASS의 시각 거절은 남긴다. [클라우드 그래픽/프록시](../../CLOUD_ANDROID_RENDERING.md)와 실제 휴대폰·Google/운영쓰기·공개서명APK 실행·사용자 승인은 다른 범위다. Main1 관측기 실패/부분2장·원래 데이터 복원을 보존하고 새 Main2/Launcher 및 배포를 진행한다.

## 실제 Main·Launcher와 종료

[Main2](live2.json)는 전체 동선·새 원본7장·원래 DB/XML/계정/소유 환경 복원을 통과했다. [Root 원본 검토](root-main-visual-review.json)에서 별칭/합성 기록과 실제 지도·의복·키보드/공유 동작을 구분한다. Main1의 관측기 오류/부분2장은 최종 사진으로 재사용하지 않는다.

[위젯1](widget1.json)은 실제 Launcher의 독립 idle font1/font2 배치2개와 원래 widget ID/cache/DB/소유 환경 복원을 통과했다. [Root 위젯 검토](root-launcher-widget-visual-review.json). 실제 live 제어/모든 크기나 같은 위젯 resize를 확인했다고 쓰지 않는다. [전용 AVD5의 정확 종료](session5-cleanup.json) 뒤 같은 키 서명·게시·운영 대시보드를 이어간다.
