# 0.7.1-dev 착용 도트 검토

이 문서는 후보 구현/현재 실행 기록이다. 새로운 공개 APK 출처는 실제 병합과 connected 서명 CI·자산 검증 후 별도로 확정한다. 기존0.7.0 출처는ca54251a780f56b60aaecce0dbeb94a4f33061ed다.

[단계별 실행 근거](app-review/2026-11-16-pixel-couture/README.md)·[설계/스킬](design/avatars/PIXEL_COUTURE_20261116.md).

- 명시 머리 마스크/독립 얼굴 gate와 앞단·접촉 그림자로 캡/바이저가 실제 착용되게 한다. 기존 headwear1/2를 준비해 ready45남/43여, 총58ID/13슬롯/9탭/무료·지갑·보유를 유지한다.
- 안경·이어폰·워치·조끼/벨트의 원근·팔 가림과 세트·양말/기본 원단의 소재 명암을 다듬는다. 곰은 단순화한 후드/운동복 재질이며 완전한 새 털 인형 원화가 아니다. 작은 장비는80px에서 미세하다.
- 승인 원본 PNG/SHA·얼굴/몸/8프레임을 유지한다. 새몸통/관절 재생성이나 미승인 수영복 통합은 없다. 실제 Aseprite/생성모델을 사용하지 않았다.
- hostv2 수치 통과에도 실린더/균열/산타·곰귀 시각거절, v3 뒤 발견한 여자F1/F5 챙 절단을 보존한다. v4 새head96/costume48/gear288·원본불변·작은 출력 Root검토를 확인했다. HOST는 Android스크린샷이 아니다.
- input3 새JVM223/실패·오류·skip0, lint오류0·경고66. 앱 APK는input2와SHA까지같고 검사만3파일 바뀌었다. API29 TCG의 새cold1에서준비18.086초, 재생창1.357초 안8프레임 모두draw를 관측했다. 초기6초 성공/성능개선/정확90ms 무결손으로 부르지 않는다.
- input1 스레드/Main 예외와 준비포함6초 실패를 같은 원인으로 확정하지 않는다. input3는 준비30초와실재생6초를분리하고 원래 실패를보존한다. intentional-loading fixture의 닫힌gate/공통ready대기를해소했으나 텍스트 잘림 검사는 유지했다.

휴대폰·Google 로그인·운영 프로필/위치 공유 쓰기·실제 live 위젯/알림 제어·야외 GPS/절전/OEM/API31이상·SNS 전달·공개 서명 APK 런타임·사용자 시각 승인은 별도다.

최종로컬 input3 Android는완결phase ar7의4 +ar8의12=새고유16이다. 별도cold1은중복집계하지않는다. 전체22는이번0.7.1 input1의share6을구체적소스동등성으로보존한범위다. 같은APK22/이전0.7재사용이아니다. ar6은제품원문16OK여도관측기형식오류로parentfalse·after맵누락이므로채택하지않았다. ar7/ar8에서정상완료·3단계6맵·원래데이터/환경복원을확인했다.

실제Main live1 전체범위가완료됐다. 새 원본9장(홈·프로필IME·남녀cap/visor·남자Santa·여자bear/golden)을Root가직접열고개인정보/픽셀을검토했다. 선택상태·저장ID/성별·무료/기존지갑·보유유지를확인했다. 검토용가명·합성5.24km기록/사진준비fixture이며사진적용/기록공유/지도동선을새로실행하지않았다. 원래DB/XML/계정·관리환경복원을확인하고전용AVD3 PID17380을정확히종료했다. [시각검토](app-review/2026-11-16-pixel-couture/root-main-visual-review.json)·[복원/종료](app-review/2026-11-16-pixel-couture/session3-cleanup.json). 실제휴대폰·공개서명APK런타임/사용자승인은별도다.
