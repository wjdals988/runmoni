# Firebase 설정 연결과0.2.1 서명 배포 준비

2026-11-01 소유자는 안내1~4번 완료,5번 Firestore 복합 색인 생성 중이라고 알렸다.

- [GitHub 연결 audit41321594248](https://github.com/wjdals988/runmoni/actions/runs/41321594248), main `567c37b4e55d7496f9da77f9a4c32fab64e95c75`, 완료 success.
- [원문 설정을 제외한 결과](github-connection-readiness.json): 기존 보관 DER 지문 일치, Android 프로젝트/패키지/Web OAuth/현재 서명 SHA-1 일치, 운영 Firestore 공개 방 목록 REST 읽기 성공. `ready=true`.
- 계정 생성·인증 토큰·비공개 읽기·방 쓰기는 실행하지 않았다. 익명 제공자/방 쓰기/실제 휴대폰/대화형 Google 로그인은 미검증이다. 빈 목록의 공개 읽기 통과를 콘솔의 색인 생성 완료로 해석하지 않는다.
- 이 audit는 APK를 빌드/업로드하지 않았다. 설정을 포함한 서명 후보는 이 기록을 main에 통합한 다음 `stage-draft/connected`로 별도 검사한다. 실제 APK 출처·서명·해시/크기·공개 자산·피드/대시보드 검증은 완료 후 기록한다.
- 기존 공개0.2.0 APK 출처 `e7b988af2b96c640fbcc40681d5201f74527b36a`와0.1.5/0.1.6 출처는 변경하지 않는다. 기존 AVD 규칙/SDK/UI 결과와 System UI ANR 제한은 [이전 검토](../2026-10-30-firebase/README.md)에 보존한다. 새 실제 휴대폰/Google 로그인 검증으로 합산하지 않는다.

## 서명 프리뷰 게시 완료

[CI41321774862](https://github.com/wjdals988/runmoni/actions/runs/41321774862)의 실제 출처는 `c6bc5739746fe176269fed3bf64360417b50875e`다. 설정 포함0.2.1-dev/code11의 JVM42개 실패/오류/skip0, release lint오류0/경고60, 실제 APK의 기존 서명·non-debuggable·버전/패키지·내장 출처 검사가 통과했다.3자산 서버 해시/크기와 작은 sidecar 본문을 대조한 뒤 [개발 prerelease](https://github.com/wjdals988/runmoni/releases/tag/v0.2.1-dev)로 공개했고 APK HEAD200/크기 일치를 확인했다. APK 재다운로드·자산 교체는 하지 않았다. [게시 근거](../../RELEASE_REVIEW_20261101.md). 실제 휴대폰·Google 로그인·운영 익명 방 쓰기는 미검증이며 색인 완료는 추가 소유자 보고가 없다.
