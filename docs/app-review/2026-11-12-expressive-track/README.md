# 0.4.4-dev 트랙·선택 강조 검토

2026-11-12 한국 시간. 사용자 후속 요청으로 PR51의 앱 반영과 같은 키 개발 APK·운영 대시보드/피드 배포까지 진행한다. [우선순위·이유](../../UI_UX_EXPRESSIVE_PRIORITIES_20261112.md)와 원문/입력별 결과를 구분한다.

## 최종 입력과 새 Android 검사

`source-freeze4.json`/`binary-build4.json`은 동일 제품103/test88/resources29/avatar83/approved1/config7의 입력을 묶었다. 최종 새 JVM189(실패/오류/skip0),lint오류0·경고57,TEST debug 앱/검사APK를 확인했다. 빌드 당시 HEAD와 실제 APK출처는 서로 다를 수 있으며 공개 C와 전체 입력 SHA를 별도로 대조한다. 기존 초기 build-result.json/2/3의 성공을 합산하지 않는다.

`android-review1.json` 일반13과 `native1.json` 실제320dp/font2 옷장3=새16개는 모두 통과·복원했다. actual method selector/frozen FQCN·원문 OK개수/해시·설치APK쌍·전/후/복원후6맵을 대조했다. system animation scale3개를 실제로 읽어0인 상태에서 검사했다. 실제150ms 시간·모든 중간 프레임·다른 배율을 측정한 검증이 아니다. 이전0.4.3의70/Launcher 결과는 이번 새 실행에 포함하지 않는다.

## 시작/관측 실패 보존

AVD 첫 시도는 이전 잠금 정보로,두 번째는 명시적인 TCG 허용 환경값 누락으로 시작되지 않았다. 실행 중인 qemu가 없음을 확인하고 소유 boot wait와 해당 AVD의 두 잠금만 정리했다. 하드웨어 가속/사용자 PC AVD를 사용한 것으로 쓰지 않는다. 세 번째 클라우드 전용 AVD는 명시 TCG로 부팅했다. 최초 시스템UI 응답 지연의 Wait 관측은 제품 검사 통과와 구분한다.

`live1.json`은 실제 Main 첫 검토 FAILED다. 하의 탭은 실제 selected=true였지만 검토 도구가 선택된 비클릭 semantic ancestor를 click_targets 필터에서 찾도록 잘못 구성돼 TimeoutExpired로 종료됐다. private 계층의 selected=true/clickable=false를 확인한 사실과 collector 보완을 `live1-failure-analysis.json`에 기록했다. 원래DB행/전체XML/프로필/account·소유환경과검토출력을복원했다. 앱코드/검사APK/타임아웃을바꿔통과시키지않는다. 원래FAIL을성공으로덮어쓰지않고 같은입력의새nonce로live2를재검토한다.

## 현재 경계

새 실제 Main4/Root원본검토·정확한AVD종료·같은키서명CI/자산게시·운영대시보드/피드는 후속 실제 근거를 따른다. Main은 홈/옷장/기본1:1 공유 왕복/지도에 한정하며,변경없는사진Picker·IME/갤러리·별도UID export전체동선을재실행하지않는다. 휴대폰·Google·운영쓰기·SNS·사용자시각승인·공개서명APK실행을추정하지않는다. 공개0.4.3의실제출처eb32a30...를새문서/후보커밋으로바꾸지않는다.

## 실제 Main 재검토 완료

`live2.json`은 같은 최종APK쌍의 단일 전체PASS다. system scale3개 실제0관측/하의→상의 탭의selected관측,홈/옷장/기본1:1 공유 탭·Back·재진입draft일치/지도밖조작과원본4장을확인했다. Root가4원본을열어개인정보를검토했으며사용자시각승인은아니다. 원래DB행/전체XML/프로필/account·소유설정/권한/전원을복원하고전/후/복원후6맵·설치APK쌍/driver불변을확인했다. `session-cleanup.json`에서정확한소유AVD961746종료를확인했다. 첫Main실패는그대로보존하며0.4.3사진Picker/갤러리/SNS전송/별도UID export를이번새성공으로부르지않는다.
