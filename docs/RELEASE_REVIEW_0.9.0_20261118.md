# 0.9.0-dev APK 공개와 앱 업데이트 피드 연결

실기기 검증 전 개발 프리뷰다. [앱 PR66](https://github.com/wjdals988/runmoni/pull/66)의 실제 APK 출처는 `3d48c39102f33c36828be224a22a3d95b1018c7e`다. 후속 문서·피드 커밋으로 이 출처를 바꾸지 않는다.

- 버전 `0.9.0-dev` / code32, 패키지 `com.runmoni.app`.
- 공개 APK 174,915,411bytes, SHA256 `2c8061338d9b25917d46b5dacec3fcc9bc7412c99429e96bbf20d1cb01d2c5ae`.
- 기존 보관 인증서 SHA256 `a21359c657f6797f5fb8229c3098e48bc72a45f9d25269f8d41ff5122465df4a`.
- 실제 연결 서명 [CI44232478964/attempt1](https://github.com/wjdals988/runmoni/actions/runs/44232478964): 새 JVM266/실패0·오류0·건너뜀0, release lint오류0·경고99. 원본40JUnit+1lint XML의 해시와 합계를 별도로 확인했다.
- [공개 릴리스](https://github.com/wjdals988/runmoni/releases/tag/v0.9.0-dev)는 2026-11-18 04:05:26 UTC에 게시했다. APK·metadata·checksum 자산 ID761785152/761816424/761816407과 SHA를 유지했다. APK 본문 재다운로드·재업로드는 하지 않았다.

## 레드닷 누락 원인과 이번 변경

APK 게시 뒤 `updates/android.json`을0.8/code31에 남겨둬, 같은 code31의 설치 앱이 새 버전을 발견하지 못했다. 대시보드 배포와 앱 업데이트 피드를 한 순서로 묶어둔 게시 절차의 누락이다. 이번 변경은 이미 공개·검증된0.9/code32의 metadata와 다운로드 URL을 preview 피드에 연결한다. stable=null, schemaVersion1, 기존 보관키·실제 APK출처·실기기 검사0 경계는 유지한다. 새로운 APK나 버전을 만들지 않는다.

앱은 성공한 자동 확인 결과를6시간 캐시한다. **설정 → 앱 정보와 업데이트 → 업데이트 확인** 또는 **버전 정보 다시 확인**은 강제 확인이며, 기존30초 재시도 제한은 유지한다.0.8/code31은 신뢰하는32를 받으면 새 버전 대상이고, 이미0.9/code32를 설치했다면 레드닷이 없는 것이 정상이다. 이번 피드 게시에서 실제 휴대폰·공개 서명 APK의 레드닷을 관측한 것은 아니다. main 병합 뒤 실제 canonical raw URL 전체 바이트와 병합된 피드 파일을 별도로 대조한다.

[사전 게시 검증](app-review/2026-11-17-expressive-refresh/feed-publication-preflight.json)은 실제 공개 sidecar 원문·자산 ID·서명/버전/해시와 새 피드의 일치를 확인했다. [실제 공개 근거](app-review/2026-11-17-expressive-refresh/connected-release-public.json)·[소스 동등성](app-review/2026-11-17-expressive-refresh/release-source-comparison.json)과 원래0.8/0.1.5의 출처 이력은 보존한다.

## 대시보드는 별도 미완료

[대시보드 PR34](https://github.com/wjdals988/mydashboard/pull/34)는 아직 main에 병합하지 않았다. 로컬 새 검사24·lint/build는 통과했으나 [CI44235724792](https://github.com/wjdals988/mydashboard/actions/runs/44235724792)의 필수 npm audit가 기존 의존성에서 실패해 후속 검사와 병합을 차단했다. 운영 대시보드의0.9 반영이나 새 Production 배포 성공을 주장하지 않는다.

2026-11-18 05:09 UTC 재확인에서 [GHSA-fc5q-39xc-vjpf](https://github.com/advisories/GHSA-fc5q-39xc-vjpf)는 picomatch<=4.0.7에 대해 first_patched_version=null이며, npm latest도4.0.7이다. 새 upstream PR267/268/269는 모두 미병합·미발행 후보라 검증된 보안 패치로 취급하지 않는다. 감사 생략·기준 상향·패치로 가장한 버전·lint 규칙 축소로 우회하지 않는다. 실제 수정 버전이 확인되면 최신 대시보드 기준으로 재검증한다. 이전에 확인한8개 다른 프로젝트/순서·24개 이전 버전 기록·4개 PNG의 보존도 유지해야 한다.

첫 Node 검사24개 자체는 통과했지만 관측기가 TAP 형식만 읽어 결과 JSON 작성에 실패했다. 원본을 보존하고 Node spec의ℹ 요약도 받도록 한 줄을 보완한 뒤 새24개를 확인했다.48개로 합산하지 않는다. 최초 eslint 없음/exit127 뒤 잠금 의존성 npm ci362개와 새 lint/build의 exit0도 보존한다. 이 결과는 실패한 보안 감사의 통과를 뜻하지 않는다.

## 그래픽·실행과 남은 확인

frontend-design의8개 UI 영역, 실제 Aseprite39종(13슬롯×3), 환경 지수·다크모드·공유 편집은 [요구별 작업표](app-review/2026-11-17-expressive-refresh/README.md)를 따른다. Android117은 최종 입력11의 새103과 변경 없는 순수 렌더러 입력9의14를 소스 동등성 검토로 보존한 범위이며 같은 APK 전체117개가 아니다. 실제 Main6 원본7장의 Root 개별 검토·원래 데이터/설정 복원·AVD7 정확 종료와 이전 실패/부분 결과 제외를 유지한다.

새39ID Firestore 운영 규칙 게시 완료 보고, 휴대폰·Google 로그인/백업·운영 프로필/위치 공유 쓰기·SNS 전달·야외GPS/OEM/API31+·공개 서명 APK 런타임·사용자 원화 승인은 별도다. 전체100ID의 준비 완료나 상업 품질 인증을 주장하지 않는다.
