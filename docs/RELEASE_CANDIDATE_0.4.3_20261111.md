> 후속 공개 상태: [2026-11-12 전체 화면 UI·공유 게시 검증](RELEASE_REVIEW_0.4.3_20261112.md). <!-- verified-studio-release-0.4.3-v1 --> 아래의 미게시/시작 실패는 이전 후보 시점의 보존 이력이며,새 실제 APK출처는 게시 검증의 C를 따른다.

# 0.4.3-dev 구현 검토 — APK 게시 보류

기본 1:1 공유 이미지, 밝은 위젯, 지도 밖 조작 메뉴를 [PR47](https://github.com/wjdals988/runmoni/pull/47)로 main에 반영했다. 앱 설정은0.4.3-dev/code25지만 새 APK는 아직 생성되지 않았다. 서명 CI가 job 생성 전 `startup_failure`로 종료돼 APK·대시보드·피드 게시가 보류됐다.

|구분|현재 상태|
|---|---|
|구현/검토 후보 소스|`bd4c10e643d40e0fe3b382f4bd4f34db629e4be7`, PR47 main 병합|
|새 서명 APK|미생성. 후보 커밋을 실제 APK 출처로 확정하지 않음|
|공개 다운로드/피드|0.4.2-dev/code24 유지|
|기존 공개 APK 실제 출처|`176a9cbe1112efa05e9fc55d913738ee1778a57f` 유지|
|실제 휴대폰·Google·SNS 전송·운영 지도 인증|별도 미확인|

## 반영 내용

- 결과 페이지에서 별도 공유 편집기로 이동한다.1080×1080/1:1이 기본이며4:5·9:16, 사진·문구·기록 배치·현재 미니미 위치/크기를 지원한다. 미리보기와 동일한 PNG를 갤러리에 저장하거나 Android 공유창으로 보낸다. 완료 기록을 재확인하고 원본 사진의 EXIF/GPS 경로를 PNG에 복사하지 않는다.
- 위젯은 밝은 표면·거리 중심 위계·필요한 조작으로 정리하고 대기 상태의 빈 수치를 숨긴다. 기존 runId/살아 있는 서비스 게이트·사용자 글씨 크기·48dp·모든 크기 fallback을 유지한다.
- 지도 위 앱 버튼을 지도 아래 ‘지도 조작’ 메뉴로 옮겼다. 드래그·핀치·접근성 조작과 출처·100m 동의 정책을 유지한다. 제공자를 임의 교체하거나 온라인 네이버 인증을 통과 처리하지 않는다. [지도 선택/연결 조건](MAP_PROVIDER_OPTIONS.md).
- ui-ux-pro-max 검색의 적합한 native 조작·글자 위계를 적용하고, 위젯 검색의 부족한 일치는 공식 Android 지침으로 보완했다. 실제 시장 유행이나 사용성 향상 수치를 측정했다고 하지 않는다.

## 검증 범위

[검토 원문](app-review/2026-11-11-run-share/README.md)과 [검사 범위](app-review/2026-11-11-run-share/android-scope-summary.json)를 따른다.

- 최종 source12: 새 로컬 JVM189, 실패/오류/skip0, lint 오류0/경고57. 로컬 TEST debug이며 공개 보관키 APK가 아니다.
- 같은 최종 APK쌍의 Android 새7: 공유 UI4·controller1·실제320dp/font2 IME1·480dp/font1 실제 키 입력/창 복원1. 이전 입력의 지도/탐색/탭12와 독립22는 보존 결과다. 서로 다른41개 범위를41개 최종 새 실행으로 합산하지 않는다. 기록 관련7은 이전 baseline이다.
- [Main 단계 근거 묶음](app-review/2026-11-11-run-share/main-review-bundle.json): 공유 동선 하위 관측3장과 같은 APK쌍의 별도 지도 성공1장. 공유 실행의 마지막 지도 수집 selector 실패는 전체 FAILED로 보존한다. 단일 Main 전체 성공이 아니다. OS 사진 선택·문구/미니미 조작·갤러리/공유창/다른 UID의 READ를 관측했고 저장·캐시·수신 PNG가 같았다. 실제 SNS 전송 성공은 별도다.
- [실제 Launcher](app-review/2026-11-11-run-share/widget2.json): 보통/200% 글씨에서 독립 OS pin2회와 idle 화면2장. 러닝 서비스 제어나 같은 위젯 resize 검증으로 확대하지 않는다. 첫 큰 글씨 수집 도구의 스와이프 실패도 보존했다.
- Root가 Main4/Launcher2 원본 PNG를 열어 내용과 개인정보를 확인했다. 사용자 시각 승인은 별도다. 원래 XML/profile/account/DB행/cache/위젯 IDs·소유 환경을 복원하고 정확한 소유 AVD를 종료했다. 휴대폰/Google/야외GPS·절전/API31+·공개 서명 APK 실행은 별도다.
- [후보 C와 전체 입력 SHA 대조](app-review/2026-11-11-run-share/executed-source-vs-release-C.json)는 소스 일치 근거다. 새 서명 APK가 존재한다는 증거가 아니다. gallery/context JSON도 배포 준비 자료이며 운영 게시 완료 자료가 아니다.

## GitHub 시작 실패

1. [43095642325](https://github.com/wjdals988/runmoni/actions/runs/43095642325): 최초 실행과 같은 작업의 rerun 후에도 job0/startup_failure. [최초 관측](app-review/2026-11-11-run-share/ci-startup-attempt1.json) · [rerun 관측](app-review/2026-11-11-run-share/ci-startup-rerun-observation.json). API의 run_attempt 값을 임의로 증가시켜 기록하지 않았다.
2. 기존 작업이 종료된 뒤 같은 C로 요청한 [새 실행43097606451](https://github.com/wjdals988/runmoni/actions/runs/43097606451)도 job0/startup_failure. [최종 관측](app-review/2026-11-11-run-share/ci-fresh-startup-failure.json).

실행 페이지의 진단은 “An unexpected error has occurred and we've been automatically notified.”다. 세부 원인은 미확정이고, 공식 상태 페이지의 Actions 정상 표시는 이 개별 실패의 원인을 설명하지 않는다. 빌드·JVM·서명·업로드가 실행되지 않았으므로 성공 수치나 새 APK SHA를 만들지 않는다. 실패한 두 run과 같은 run의 rerun을 구분한다. 새 키/Secrets 재등록·유료 서비스·공개 터널로 우회하지 않았다.

[공개 상태 재확인](app-review/2026-11-11-run-share/publication-blocked-state.json): canonical raw 피드 전체 bytes가 저장소의 preview24/stable null과 같고, 운영 대시보드에는 기존0.4.2 APK 링크가 남아 있다.0.4.3 릴리스/자산은 없고 현재 대기·실행 중인 run도 없다. 대시보드 저장소는 미변경이다. 기존 APK 출처와 과거 릴리스 기록은 그대로다.

## 재개 순서

1. GitHub Actions가 실행 가능해지면 후보 C의 기존 release workflow를 같은 키/connected/stage-draft로 재시도하고 실제 결과를 확인한다. 중복 실행이나 AVD/빌드/전송 동시 실행은 피한다. 서명 전 후보 C와 main을 다시 대조한다.
2. CI에서 새 JVM189/lint·non-debuggable·패키지/버전/내장 실제 revision·보관 인증서·연결 readiness·임시 파일 정리를 확인한다. 시작 실패를 실행 검사로 세지 않는다. 준비된 WORK 검증기는 실제 C/run을 입력받으며 APK 본문 재다운로드 없이 sidecar·원격 digest를 확인한다.
3. 실제 서명 APK가 검증된 뒤 개발 prerelease를 게시하고 HEAD/크기를 확인한다. 그 실제 metadata로 대시보드의0.4.3 다운로드와 원본 Main4장을 준비해 Node 검사·PR·main/Production·canonical13항목/PNG4 bytes를 확인한다.
4. 위 검증 후에만 게시 문서/preview25 피드를 생성한다. 실제 APK 출처 C와 후속 문서 커밋을 구분하고,canonical raw 피드 전체 bytes를 확인한다. 이 근거 PR은 게시 사실을 보강한 뒤 병합할 초안이며,현재 main의 후보 C를 유지한다.

서명키·비밀번호·토큰을 채팅에 요구하거나 노출하지 않는다. 승인 원본·58ID/13슬롯/9탭·0P·보유/포인트/기록·과거 APK 출처를 유지한다. 휴대폰 검증이 없더라도 독립 의복/크루 작업의 검토는 이어갈 수 있다.
