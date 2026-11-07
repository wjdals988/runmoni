# 0.3.8 업데이트 표시 — 실행별 근거

[변경·판정·실제 사진·실패/미확인](../../UPDATE_INDICATOR_REVIEW_20261107.md)을 먼저 읽는다.

- binary-build1/build1: 새 JVM115/debug lint0/39. source-freeze1은 당시 해시 스냅샷이다.
- binary-build2/build2: 단일 검사 파일만 수정, 제품 APK 동일, JVM/lint UP-TO-DATE 근거 재사용. source-freeze2와6개 소스맵을 보존한다.
- android-review1: 이전 도구의 코드19 고정 검사로 변경 전0개 실패. android-review2:8개 중1개 raw 문단 overflow 기대 실패/정확 복원, 이전 성공은 합산하지 않는다.
- android-review3 기본9/native1 실제320dp·글씨2배2: 같은 build2 pair에서 새11 통과, 원래 XML/프로필/통제 환경 복원.
- group별 indicator-layout.jsonl: 실제 버전 줄 경계12행, signed/release 실행 근거가 아니다.
- root-visual-review: 직접 본 유효5장, 설정 촬영본 상태 불일치와 초기 빈 화면 판독 정정을 기록. userVisualApproval=false.
- post-runtime-source-verification: 원래 수집기에 없는 Final 필드를 만들지 않고 종료/복원 뒤 별도로 측정한 소스 일치다.

서명 CI/공개 자산/HEAD/운영 근거는 실제 확인 후 추가한다. 비공개 설정 XML/권한 백업은 ignored work의0600 파일로만 보존하며 Git에 넣지 않는다.
