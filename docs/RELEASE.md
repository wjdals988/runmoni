# 서명 APK 개발 프리뷰 게시

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
