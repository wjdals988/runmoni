# 0.3 크루·옷장 Android 검토

클라우드 전용 `Runmoni_Cloud_29`, API29/AOSP/TCG에서 실행한 debug 검토다. 실제 휴대폰·하드웨어 가속·Google/운영 Firebase 쓰기 검증이 아니다. 공개 APK 출처와 debug 검토 바이너리의 dirty 출처/해시는 구분한다. [빌드 근거](build-verification.json) · [토론](../../CREW_CLOSET_REVIEW_20261102.md).

## 확인한 실행

| 실행 | 결과 | 경계 |
|---|---|---|
| 오프라인 JVM |45개 통과|실제 기기 실행과 별개|
| debug lint |오류0/경고35|경고는 남아 있음|
| HTTP |38개 통과|로컬 API/기존 Starlette 경고1|
| demo Firestore Rules |119개 통과|[GitHub CI 근거](rules-ci.json), 운영 게시 아님|
| Python 연결/게시 정책 |55개 통과|모의 검증|
| Android Firebase SDK |1개/31.619초 통과|[보고서](firebase-sdk.txt), 한 AVD의 익명 클라이언트3개·로컬 demo 프로젝트|
| 수정 후 계층/뒤로/UI |7개/215.622초 통과|[보고서](focused-native-ui.txt), 실제 키 주입·목적 화면 검사/Android fixture|
| native320dp/글씨200% 옷장 |2개/166.121초 통과|[보고서](focused-native-320dp-font2.txt), 무료 선택·성별·닫기/재열기. 상단/탭 정렬 후속 수정과 구분|

첫 Android 실행은 UI19개 중2개 실패, native320/font2의2개 중1개 실패였다. Back helper의1초 접근성 이벤트 판정과 부분 문구의 전체 일치 검사를 고쳤으며 첫 실패를 성공으로 덮어쓰지 않는다. [첫 실행 분석](first-run-analysis.json). [실행/설정 복원](focused-android-verification.json)을 기록하고 전용AVD PID528139를 종료했다. 실제 MainActivity/loopback과 최종 문구/상단 정렬 빌드는 후속 기록한다.

## 실제 Android 출력

![공개 파티 상세를 Android Dialog에서 출력한 검토 fixture](party-public-android-fixture.png)

480×854 전체 기기 캡처다. 앱 컴포넌트를 Android에서 실제 출력했지만 크루/파티는 `FakeActions`의 테스트 데이터이며 실제 MainActivity/운영 Firebase 화면이나 사용자의 시각 승인이 아니다. 첫 잘못된 파티 캡처는 배경 Activity였으므로 보관 근거에 쓰지 않는다.

선택 범위 밖에서320dp/글씨200%의 워드마크와 하단 러닝 탭 글자가 분리되는 문제를 실제 관찰했다. 그 수정과 해당 화면 재검증이 끝나기 전에는 전체 큰 글씨 UI가 완전하다고 표시하지 않는다. 앱 실행 전 런처의 System UI ANR도 관찰했으며 원인 해결로 기록하지 않는다. 가려진 화면은 갤러리에 사용하지 않는다.

승인 원본 두 행×8프레임/픽셀을 그대로 사용하고 미준비 의상은 선택만 저장한다. 파티 삭제에는 소유자의 새 Rules 운영 게시가 필요하다. 실제 Google 로그인·휴대폰 업데이트/야외 GPS·운영 두 기기 생성/삭제·채팅은 별도 미확인이다.
