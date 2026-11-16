# input3 검사 경계 교정

제품/리소스/자산/승인 원본/설정은 input2와 동일하다. Android 검사 소스만 바뀐다. 새 빌드·실행 성공은 실제 후속 근거로 별도 기록한다.

- ar1/ar3/ar4의 시작부터6000ms 내 준비+전체 프레임 조건 실패를 보존한다. ar4에서 준비 후 실제 관측은826ms였으므로, input3는 준비≤30초와 별도 실제 재생6000ms로 분리한다. 준비 때의 프레임은 비우고 실제 시간창 안에서0..7을 다시 그려야 한다. 기존 실제 pixel capture3종도 유지한다. 초기6초 로딩 성공이나 성능 개선으로 부르지 않는다. [독립 검토](CLOCK_READINESS_ADVISORY.md).
- ar4 delayedRead 검사는 닫힌 I/O gate의 로딩 문구를 감사하면서 공통 helper가 로딩 종료를 기다리는 상호대기였다. 이 의도적 로딩 호출만 awaitAvatarReady=false로 하고 동일한 텍스트 줄/잘림 검사는 유지한다. 그 밖의 모든 호출은 기본true로 기존 준비 조건을 유지한다. 제품 로딩 오류를 숨기는 timeout 증가나 검사 제거가 아니다.
- input1의 worker/Main 예외와 input2의 준비 지연은 같은 원인으로 확정하지 않는다. 휴대폰 성능/매90ms 무결손/사용자 승인도 별도다.

## ar6 관측기 형식 실패

실제 원문은kit4/bounded1/identity+playback7/loading4 모두OK였다. 그러나 마지막loading의새 `awaitAvatarReady=false;` 기록을구형관측기정규식이거절해parent=false, 집계counter12에서중단했다. sourceMapsAfter는그지점에서미생성이고, before/afterRestoration6맵/데이터복원은실제로모두맞았다. 이필드를있는것처럼추가하거나parent를PASS로바꾸지않는다. 관측기만literal true/false필드를허용하고, 새완료phase로loading4와나머지12를분리확인한다. 특수예외로ar6를최종성공에편입하지않는다. 제품/검사APK/6개source map은그대로다.
