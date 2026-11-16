# 실시간 재생 검사: 준비·재생 분리 검토

읽기 전용 검토 및 WORK patch 준비다. 제품/원래 검사 소스·장치·빌드·Git·네트워크·helper pin은 변경하거나 실행하지 않았다. Root의 채택·새 input3 빌드·실행 결과는 아직 없다.

## 판단과 반대 조건

**준비와 실제 motion 관측을 분리하는 데 찬성한다.** 다만 기존 `시작 후6초 안 준비+8프레임` 조건을 유지하는 변경은 아니다. 그 end-to-end 기준을 완화하는 점은 명시하고 원래 실패를 보존해야 한다. 새 계약은 **별도 준비30초 + 준비 뒤 실제6000ms에서8프레임 재관측 + 기존 실제 pixel capture3종**이다. 초기 로딩6초 성공·성능 개선·원래 실패 해결을 주장할 수 없다.

근거는 공개 원문 `docs/app-review/2026-11-16-pixel-couture/android-review4-clock-observations.txt`다. input2 warm에서는 첫0 draw3,387ms, 첫 nonzero1 draw5,497ms, 그 뒤2=5,589/4=5,707/5=5,836/6=5,934/7=6,055ms, observation 종료6,323ms였다. 첫 nonzero 이후 관측 여유는826ms뿐이다. 2→4의118ms 간격은90ms frame3을 한 번 놓칠 수 있는 측정과 맞는다. 이는 frame3의 원본 데이터가 없거나 재생이 영구 정지했다는 증거가 아니다. cold ar3의 seen=[]/10,294ms도 준비 전 관측이 끝난 경우이며, 이 실제 종료 시간을 정확히6초라고 고쳐 쓰지 않는다.

현재 MiniMi는 첫 frame0만 준비되면 길이1인 frames를 그린다. full8 준비 뒤에만 playing=true이며 `frameOverride` 없는 이 메서드에서 nonzero draw는 실제 전체 clip 재생 진입을 의미한다. 따라서 첫0 대신 첫 positive frame을 readiness로 선택할 소스 근거가 있다. animator 비활성/미시작 lifecycle/멈춘 재생을 readiness로 통과시키지 않는다.

반대로, 사용자에게 보이는 준비 지연 자체가 문제가 아니라는 결론은 아니다. warm 첫 draw3.387초·첫 active5.497초와 cold의 준비 지연은 별도 성능 개선 대상으로 남는다. 사용자 startup SLA가6초였다면 기존 검사를 별도 유지하거나 실패 상태로 공개해야 하며, 이 patch만으로 그 요구를 충족했다고 할 수 없다.

## 준비한 patch

- `work/pixel-couture/clock-readiness.patch`
- patch SHA256: `0b4432fa6d4cc854486325529681dc2f74c79160930b4f0a3b5f7b4f3e34c447`
- 적용 대상: `app/src/androidTest/java/com/runmoni/app/ui/AvatarPlaybackTest.kt`의 realClock 메서드 한 곳
- 현재 대상 SHA256: `4e7605babbfc115170d39dfdec55c3123cf4fe36a1797c71d134fd388e2d77ee`
- 메모리상 제안 결과 SHA256: `5a860418558531d2c7acc7a6bad0262601510ada4c2fd5e300704b0d056e93c0`

새 준비 budget은 setContent 전 started부터30초이며 남은 시간만 waitUntil에 전달한다. 첫 positive frame을 기다리고 Main의 두 draw 사이에서 준비 samples를 clear한 뒤 playbackStarted를 기록한다. clear/phase/timestamps/seen은 같은 monitor로 보호한다. 따라서 준비 때 그린0도 motion window에서 다시 그려야 한다.

callback은 준비/재생 phase와 각 phase의 실제 경과 시간을 기록한다. motion sample은 실제 window 시작 뒤6000ms 이내만 채택하므로 늦은 test polling으로 deadline 이후의 frame이 섞여 PASS하는 것을 막는다. 준비 positive나 boundary 대기 때문에30초를 넘긴 경우도 실패한다. 최종 로그는 준비·motion 관측 시간을 따로 기록하며 `assertMoving`의 후속 capture 시간을 test 전체 종료 시간으로 혼동하지 않는다.

원래6000ms의8프레임 조건·정확한0..7 집합 assertion·기존 `assertMoving`의 실제 bitmap3종 조건은 유지한다. 새 frameOverride, clock advance, fake frame callback, 추가 retry, test 재실행 자동화는 넣지 않는다. 다른 pause/lifecycle/system animator 메서드·제품 코드·기존 withAnimators 동작은 그대로다. `compose.mainClock.autoAdvance=true`는 원래 공통 fixture 설정이며 새 수동 clock 이동을 추가한 것은 아니다.

이 patch는 Python difflib로 메모리에서 만든 제안이며 Kotlin 컴파일/실행을 하지 않았다. 원래 검사 소스가 바뀌면 위 hash를 확인하고 다시 검토해야 한다.

## 채택 뒤 확인할 사항

1. Root가 복원·소유 AVD 종료를 완료한 뒤 patch를 검토하고 실제 test-only input3로 동결한다. 제품/리소스/83자산/승인 원본/설정은 input2와 같음을 맵으로 확인한다. 실제 APK pair의 바이트 동일성은 측정 전 가정하지 않는다.
2. 실제 cold process에서 이 메서드를 먼저 실행하여 준비 시간/phase 경계/새 window의0..7 및3종 실제 capture를 확인한다. warm 결과는 따로 남기고 cold 실패를 덮지 않는다.
3. 그룹 실행에서도 같은 기준을 확인한다. 실패 그룹의 일부 통과를 전체 PASS로 세지 않고 기존 ar1/ar2/ar3/ar4 원문을 보존한다. 전체 source freeze/build/설치 pair와 새 원문·복원 근거를 묶는다.
4. 이 검사는 각 프레임의 실제 draw와 pixel 변화에 대한 API29 소프트웨어 환경 관측이다. 정확한90ms FPS 안정성·모든 cycle의 무결손·휴대폰/실사용 자연스러움·의복 사용자 승인을 증명하지 않는다.

현재 비교 helper는 input2 pin 그대로다. input3 실제 freeze/build SHA를 받기 전에 미래 pin이나 성공 JSON을 만들지 않는다.
