# 연결 점검 검증 — 2026-10-30

대상0.2.1-dev/code11. Gradle은 오프라인·작업자1개, 보관 release 키를 가져오지 않는 debug 빌드다. 의존성/승인 원화/옷장ID/데이터 스키마는 변경하지 않았다.

- 최종 debug/JVM42개(실패·오류·skip0), lint오류0/기존 경고35. [구조화 결과](build-verification.json).
- Python 연결 검사9개, 서명/릴리스 경계14개, 게시 절차31개 통과. JSON/OAuth/인증서/서비스계정 거부/사설 주소/리다이렉트/개인정보 제외·연결 프로필 중단을 합성 fixture로 검사했다. 각 원문 보고서를 이 폴더에 보관한다.
- 공개 서버 생성·실제 휴대폰·대화형 Google 로그인은 미확인이다. FirebaseAndroid 파일/호스팅 계정이 없고 GitHub 설정 관리API403이다. APK release 키는 기존 GitHub Secrets4개를 사용한다. 키 값·관리자 자격을 채팅으로 받지 않는다.

Firebase 전 UI4개49.634초,320dp/font2 같은2개44.881초 통과는 Compose 의미/레이아웃/콜백 범위다. 전체 창 조작/깨끗한 실제 화면은 System UI/Quickstep ANR로 확인하지 못했다. 최신 Firebase SDK/규칙과 최종 화면의 한계는 [후속 검증](../2026-10-30-firebase/README.md)을 우선한다. 테스트 fixture를 실제 Google/공용 서버 성공으로 표시하지 않는다. 기존0.2.0 APK 출처는 변경하지 않는다.
