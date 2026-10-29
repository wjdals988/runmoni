# 기능·UI/UX 점검 / 2026-10-30 KST

대상: 0.1.7-dev/code8 개발 변경. 공개 APK0.1.6-dev의 검증 기록과 별개다.

| 구분 | 확인한 문제 | 이번 개선 |
|---|---|---|
| 기능 | 정지9초 뒤0.7m/s로 움직여도 이전 정지 시점이 남아 다음 정지에서 즉시 자동 정지할 수 있음 | 0.5m/s 이상 움직임·부정확/없는 속도·정확도·5초 초과 신호 공백에 관찰 시간을 초기화. 연속10초만 인정 |
| 기능 | GPS가 꺼진 채 시작하면 GPS listener를 등록하지 않아 나중에 켰을 때 복구 경로가 없음 | 존재하는 provider에는 비활성 상태에도 구독. Android test provider로 꺼진 상태 시작→활성화→기록 확인 |
| 기능 | 짧게 GPS를 껐다 켜면 이전/새 지점의 직선 거리가 연결될 수 있음 | provider 비활성/재활성화 경계에서 구간을 끊음. 속도 필터로도 통과할 수 있는4.4m 이동을 회귀 조건으로 사용 |
| 기능 | 자동 정지 중 옵션을 꺼도 자동 재개되지 않음 | 1초 ticker에서 자동 정지만 해제. 직접 멈춘 기록은 유지 |
| UI/UX | 자동/수동 일시정지 문구가 같고 재개 조건이 불명확 | 화면 제목·설명과 foreground 알림에 자동 정지 구분. 수동 정지에는 재개 버튼 안내 |
| UI/UX | 위치 권한 거부·GPS 꺼짐 뒤 설정 복구 행동이 없음 | 원인별 카드와 앱 권한/위치 설정 버튼. 설정 복귀·provider 변경 때 상태 갱신 |
| UI/UX | GPS 오류 메시지에 예전 정확도가 남아 정상 색상으로 보일 수 있음 | 오류 시 정확도 제거. 권한/GPS 비활성 때 정상 색상·이전 정확도 숨김 |

정확도 없는/비정상 좌표는 기록하지 않고 경로 구간을 끊는다. 기존 Room 스키마·완료 기록·포인트·의상 보유권·13슬롯/58개 ID는 변경하지 않는다. 중단 복구는 여전히 RECOVERY 상태에서 명시적으로 이어 달리며 자동 정지를 복원했다고 주장하지 않는다.

## 후속 우선순위

1. P1 실기기 야외 GPS, 화면 끔·배터리·권한 회수·프로세스 종료 후 복구 확인. 저장소 쓰기 실패 때의 기록 종료/지급 복구도 오류 주입 검증 필요(이번에 재현한 결함으로 표기하지 않음).
2. P1 여자 기본형 선택·원본 품질의 프레임별 의복/마스크·색상·수영복. 첫 행 전신8프레임 표시를 전체 꾸미기 완료로 해석하지 않는다. v0.4 거절/v0.5 미승인 상태 유지.
3. P2 실제 Google 인증/요약 백업, 2대 음성·음악. 테스트 계정이나 비밀 설정을 임의로 만들지 않는다.
4. P2 앱 정보/런처 아이콘 일관성. 앱 정보는 현재도 별도 `ic_runmoni` 리소스를 사용하므로 마스크별 실제 비교가 필요하다.
5. P3 실제 저장 경로 기반 코스 탐색·파티 브리핑. 가짜 코스/러너를 추가하지 않는다.
6. P2 다운로드 크기 점검. 공개0.1.6 APK는218,201,869바이트로, ABI별 배포 등 실제 설치 대상에 맞춘 축소를 검토한다. 이번에는 배포 형식/기존 다운로드를 변경하지 않는다.

## 검증

- `python3 scripts/cloud/runmoni-app.py build --tests --android-tests --lint`: 오프라인·작업자1개, debug 앱/테스트 APK 빌드 성공. JVM40개 모두 통과(새 자동 일시정지9개 포함), 실패/오류/건너뜀0. lint오류0/경고33. 기존 공개 릴리스 lint57개와는 실행 조건이 달라 경고24개를 수정했다고 해석하지 않는다.
- [원시 자동 정지 결과](app-review/2026-10-30/TEST-com.runmoni.app.domain.AutoPauseControllerTest.xml), [기존 거리 엔진 결과](app-review/2026-10-30/TEST-com.runmoni.app.domain.RunEngineTest.xml), [검증 요약](app-review/2026-10-30/verification.json).
- 승인 원본 SHA256 재확인: `f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65`. 이미지/캐릭터 렌더러 변경0. 수치 확인을 새 시각 승인으로 취급하지 않는다.
- 클라우드 `Runmoni_Cloud_29`는 API29/AOSP·TCG 소프트웨어 방식이다. KVM·실기기·Google 인증 검증이 아니다. 부팅 완료와 설치 완료를 별도로 확인하며 테스트는 순차 실행한다.

- 최종 Android instrumentation **7/7 통과**,138.643초. 새 UI2개, 실제 service1개(꺼진 상태 시작→활성화→백그라운드8지점→GPS 단절/재활성화→수동 정지/재개→11지점 저장), DB/포인트/보유권 보존3개, 러닝 조작9개 viewport/font 조합1개다. [최종 원시 로그](app-review/2026-10-30/instrumentation.txt). GPS 공백 뒤4.4m를 거리로 더하지 않고 새 구간에 저장하는 조건을 포함한다.
- [1차7개 결과](app-review/2026-10-30/instrumentation-initial.txt)는 GPS 공백 보강 전 실행이다. 최종7개와 합산해14개 기능 검증이라고 표시하지 않는다.

## 캡처와 실행 경계

`run-*.png`는 실제 Android에서 `RunmoniApp`에 상태를 주입한 Compose fixture 캡처다. 정상/320dp·fontScale2 배치와 행동 연결을 검증하며 실제 야외 러닝 기록이 아니다. viewport 너비/글자 배율은 CompositionLocal로 지정했고 기기 전체 글자 설정을 변경한 결과로 표시하지 않는다. 호스트 GIF/합성 목업은 사용하지 않았다.

| 실제 Android fixture | 확인 영역 |
|---|---|
| [GPS 꺼짐360dp/100%](app-review/2026-10-30/run-gps-disabled-360-100.png) | 원인·설정 버튼과 고정 시작 |
| [GPS 꺼짐320dp/200%](app-review/2026-10-30/run-gps-disabled-320-200.png) | 스크롤 후 설정/시작 접근 |
| [권한320dp/200%](app-review/2026-10-30/run-permission-320-200.png) | 권한 설정과 시작 영역 |
| [자동 정지360dp/100%](app-review/2026-10-30/run-auto-paused-360-100.png) | 정지 이유·재개 안내 |
| [자동 정지320dp/200%](app-review/2026-10-30/run-auto-paused-320-200.png) | 재개/종료 하단 고정 |

첫 MainActivity 실행의 `am start -W`는11004ms에 timeout을 반환했다. 이후 **System UI isn't responding** 대기 창을 관찰하고 Wait 선택 뒤 실제 MainActivity resumed와 앱 화면을 확인했다. 첫 권한 설정 자동 확인은 UIAutomator의 null-root 출력 뒤 assert 실패로 미확인이었다. 오래된 XML을 재사용하지 않고 화면 준비를 기다리는 호스트 검증으로 재확인한다. 이 관찰을 실제 휴대폰 속도 문제나 앱 회귀의 원인 확정으로 확대하지 않는다.

최종 APK의 실제 MainActivity에서도 두 동선을 확인했다. ‘위치 설정 열기’는 `LocationSettingsActivity`를 열며 GPS를 켜고 돌아오면 안내 카드가 사라졌다. 정밀 위치 권한 회수 후 표시된 ‘앱 권한 설정’은 `APPLICATION_DETAILS_SETTINGS`/`package:com.runmoni.app`의 `InstalledAppDetails`를 열었다. [실제 동선/재시도 기록](app-review/2026-10-30/manual-checks.json). 재확인 중에도 System UI 대기 창1회·UIAutomator 현재 XML 없음3회를 관찰했고, Wait/현재 화면 재조회 뒤 두 동선 모두 완료했다. 이를 대기 창이 없었던 실행으로 기록하지 않는다.

| 실제 MainActivity 전체 화면 | 확인 영역 |
|---|---|
| [GPS 꺼짐](app-review/2026-10-30/main-gps-disabled.png) | 실제 기기 설정에 따른 안내/고정 시작 |
| [위치 설정 복귀](app-review/2026-10-30/main-gps-ready.png) | 안내 해제. GPS 신호 수신 성공을 의미하지 않음 |
| [위치 권한 회수](app-review/2026-10-30/main-permission-required.png) | 실제 권한 상태와 앱 권한 설정 버튼 |

설치 앱의 `0.1.7-dev/code8`, API29,480×854/density160/fontScale1을 확인했다. 위치 모드3·정밀 위치 권한 허용·애니메이션3종1.0으로 복원했다. [기기 상태](app-review/2026-10-30/device-after-review.json). 소유한 AVD PID61224는 종료했고 실행 중이 아님을 확인했다(종료 후 Z 상태로 회수 대기). 사용자 PC AVD는 사용하지 않았다.

## 배포와 미확인 범위

이번 APK는 로컬 테스트 키의 debug 빌드이며 미게시다. [해시와 빌드 당시 체크아웃 정보](app-review/2026-10-30/verification.json)의 `checkoutHeadAtBuild`는9c3df09+미커밋 변경 상태를 뜻하며 공개 APK의 `sourceRevision`으로 사용하지 않는다. 이후 커밋은 검토 가능한 구현 소스를 보존한다. 공개0.1.6의 sourceRevision9dd409f와 기존0.1.5의75c39ca를 바꾸지 않았다. 서명/공개 업데이트 피드/대시보드 다운로드는 이번에 변경하지 않았다.

실제 휴대폰 설치/로그인·야외 GPS·장시간 화면 끔/배터리·저장소 쓰기 실패·Google 인증·2대 음성은 미검증이다. 본문은 스크롤되며 모든 정보를 한 화면에 동시에 보인다고 주장하지 않는다. 검토 화면과 사용자 시각 승인도 구분한다.
