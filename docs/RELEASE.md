# 서명 APK 개발 프리뷰 게시

## 0.1.3-dev / versionCode4 — 로컬 서명 개발본, 2026-10-25

- 파일 `runmoni-0.1.3-dev.apk`, 209,946,174bytes, minSdk29/targetSdk36, DEBUGGABLE=false.
- SHA-256 `d8d64d076e6b19b75888dc7edc90d7a00015280f001e6b4c053bb66e109aa676`. 인증서 SHA-256 `2d7f724cd20b1ff2263eb34e8e02d5fec9db674488b10b813dd239838b975830`로 기존 공개0.1.1과 같다. apksigner APK v2 검증 성공.
- 설정 포함 release Firebase 활성화·대상 runmoni-3c08e 일치. 빌드2분27초·JVM28/28·release lint0오류/20경고.
- 레이아웃/상태38/38은 마지막 Material3 표면 색상만 바꾸기 전의 debug 검사다. 최종 release 화면은 별도로 확인한다.
- 새 APK는 outputs에 로컬 보존했고 공개 GitHub 자산/대시보드 다운로드를 교체하지 않았다. updates/android.json도 기존 공개0.1.1-dev/code2만 알린다.
- 실제 Google 로그인·원격 백업·실기기 야외 GPS0회. 본인 로그인은 FIREBASE_DEVICE_CHECK 절차로 후속 확인한다.

## 0.1.1-dev / versionCode2

개인 러닝 기록·미니미와 로컬 서버 기반 크루 기능을 시험하는 개발 프리뷰입니다. GitHub 게시가 외부 서버·DB·지도·LiveKit 배포를 뜻하지 않습니다. Room2개 테이블과 로컬 서버SQLite14개 테이블은 이미 사용하며 Supabase는 미배포 이관 준비입니다.

## 서명과 갱신

- 공개 APK는 Android release build에 보관용 개발 키로 서명합니다. debug/unsigned 파일을 대시보드에 연결하지 않습니다.
- `.secrets/runmoni-release.jks`와 `.secrets/signing.properties`는 Git에서 제외합니다. 소유자가 별도 안전한 위치에 백업해야 합니다.
-0.1.0-dev debug 파일과0.1.1-dev 공개 APK는 인증서가 달라 덮어쓰기할 수 없습니다. 테스트 앱에서 필요한 GPX를 먼저 내보낸 뒤 재설치합니다.
- 향후 같은 공개 앱의 업데이트는 동일 키를 유지하고 versionName/versionCode를 올립니다.
- `scripts/build-release.ps1`은 키가 없으면 새 키를 임의 생성하지 않고 중단합니다.

## 게시 전 확인

1. release 빌드·JVM·lint 결과 확인.
2. apksigner verify 및 APK 버전/패키지/인증서 확인.
3. 별도 에뮬레이터에 설치·실행 smoke test. 실기기 검증은미수행으로 표시.
4. GitHub asset 바이트크기와 SHA256 대조.
5. 대시보드의 development preview 상태·제약·다운로드 메타데이터 확인.

이번 프리뷰는 기존82개 자동 검증(JVM6/API23/bridge32/Android21)을 근거로 하며, Android21개는0.1.0-dev debug 실행에서 확인했습니다. 새서명 APK의 install/launch는별도확인하고 실제야외GPS/블루투스/음악앱캡처/2대송출 성공을추정하지않습니다.

## 2026-10-13 서명 APK 확인 결과

- `assembleRelease`, JVM 6개, `lintRelease` 통과. release lint는 오류 0개·경고 17개입니다.
- 패키지 `com.runmoni.app`, 버전 `0.1.1-dev`, versionCode `2`, minSdk `29`, targetSdk `36`입니다.
- APK 파일 `runmoni-0.1.1-dev.apk`: `200,693,280` bytes.
- SHA-256: `25772671956108deed9bde887104d83b4b7eefd7d2dbd5c7b4223d143db78de9`.
- Android APK Signature Scheme v2 검증 통과, 서명자 1명, RSA 3072비트입니다.
- 인증서 SHA-256: `2d7f724cd20b1ff2263eb34e8e02d5fec9db674488b10b813dd239838b975830`.
- 전용 `Runmoni_API36` 에뮬레이터에서 기존 debug 앱을 제거한 뒤 설치 성공, cold launch `Status: ok`, 실행 프로세스를 확인했습니다. 앱 패키지 flags에 `DEBUGGABLE`이 없습니다.
- 실제 휴대폰 설치·야외 러닝·2대 음성/DJ 송출은 0회입니다.

## 2026-10-24 GitHub 게시 결과

- Git Credential Manager 인증 후 소스 이력 보존 커밋 `b72eec72a05108374b6a4605f7958dfc869b1df0`을 main에 게시했습니다.
- [v0.1.1-dev 개발 프리릴리스](https://github.com/wjdals988/runmoni/releases/tag/v0.1.1-dev)를 공개했습니다. draft=false, prerelease=true이며 기존 APK 바이트와 서명은 유지했습니다.
- [APK 다운로드](https://github.com/wjdals988/runmoni/releases/download/v0.1.1-dev/runmoni-0.1.1-dev.apk), JSON 메타데이터, `SHA256SUMS.txt` 3개 파일을 게시했습니다. GitHub API의 APK 크기·SHA-256이 로컬 파일과 일치합니다.
- 체크섬을 문서에 옮길 때 생긴 1글자 오타를 파일에서 계산한 64자리 값으로 정정했습니다. 실제 APK는 변경하지 않았고 체크섬 파일 2개와 릴리스 설명만 수정했습니다.
- 이 공개 APK에는 Firebase SDK가 없습니다. `0.1.2-dev` 개발 소스의 Google 로그인·요약 백업은 실제 설정 파일·Rules 배포·원격 로그인 검증 후 별도 APK로 공개해야 합니다.

Firebase 준비 가이드는 클라우드 연동 완료를 뜻하지 않습니다. 대시보드 반영과 개발본 검증은 각각의 변경·검증 기록에서 구분합니다.

## 2026-10-24 대시보드 다운로드 반영 완료

- 최신 개발 소스 `d98001c`를 runmoni main에 게시했습니다. 공개 `0.1.1-dev` 태그와 APK를 최신 개발본으로 바꿔치기하지 않았습니다.
- 대시보드 `b5a9b76`을 main에 게시하고 GitHub CI의 의존성 설치·보안 검사·테스트·lint·빌드 전체 성공 및 Vercel 배포 완료 상태를 확인했습니다.
- [실제 다운로드 페이지](https://coldbrewventi.vercel.app/projects/runmoni)에서 APK 앵커, 릴리스 링크, 버전, 크기, SHA-256이 공개 자산 정보와 일치했습니다. 페이지 HTTP 200, APK HEAD HTTP 200이며 APK 본문은 다시 받지 않았습니다.
- 기존 다른 프로젝트 8개와 순서를 보존했습니다. 대시보드 전체 9개 중 다운로드 제공은 6개입니다. 확인 근거와 순차 작업 범위는 [변경 이력](../CHANGELOG.md)에 기록합니다.
