# Firebase 설정 연결과0.2.1 서명 배포 준비

2026-11-01 소유자는 안내1~4번 완료,5번 Firestore 복합 색인 생성 중이라고 알렸다.

- [GitHub 연결 audit41321594248](https://github.com/wjdals988/runmoni/actions/runs/41321594248), main `567c37b4e55d7496f9da77f9a4c32fab64e95c75`, 완료 success.
- [원문 설정을 제외한 결과](github-connection-readiness.json): 기존 보관 DER 지문 일치, Android 프로젝트/패키지/Web OAuth/현재 서명 SHA-1 일치, 운영 Firestore 공개 방 목록 REST 읽기 성공. `ready=true`.
- 계정 생성·인증 토큰·비공개 읽기·방 쓰기는 실행하지 않았다. 익명 제공자/방 쓰기/실제 휴대폰/대화형 Google 로그인은 미검증이다. 빈 목록의 공개 읽기 통과를 콘솔의 색인 생성 완료로 해석하지 않는다.
- 이 audit는 APK를 빌드/업로드하지 않았다. 설정을 포함한 서명 후보는 이 기록을 main에 통합한 다음 `stage-draft/connected`로 별도 검사한다. 실제 APK 출처·서명·해시/크기·공개 자산·피드/대시보드 검증은 완료 후 기록한다.
- 기존 공개0.2.0 APK 출처 `e7b988af2b96c640fbcc40681d5201f74527b36a`와0.1.5/0.1.6 출처는 변경하지 않는다. 기존 AVD 규칙/SDK/UI 결과와 System UI ANR 제한은 [이전 검토](../2026-10-30-firebase/README.md)에 보존한다. 새 실제 휴대폰/Google 로그인 검증으로 합산하지 않는다.
