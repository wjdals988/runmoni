# 야간 완료 감사 — 2026-10-25

사용자 지시: 순차로 마무리하고 다른 세션과 조율한다. 일반적인 결정은 서브에이전트와 정하고, 정말 크리티컬한 결정만 본인에게 남긴다. 완료 여부는 이전 완료 메시지가 아니라 현재 소스·테스트·실제 화면으로 판단한다.

## 이번 감사에서 발견한 추가 검증 공백

| 요구 | 기존 근거 | 감사 판정 | 조치 / 필요한 최종 근거 |
|---|---|---|---|
| 헤어·옷 변경 시 얼굴/목 연결 | AvatarArtTest의 1,152조건 목 픽셀/연결 경로 및 실제 AVD 화면 | 확인됨 | 테스트 범위를 전체 조합/실기기 검증으로 확대 해석하지 않음 |
| 독립 옷·동시 장비·특수 세트 | AvatarIntegrationTest 3개 + RunmoniUiTest 17개, 최종 20/20 | 기본 AVD 조건 확인됨 | 기존 지갑/포인트 복원, 장비6종 동시 착용, 세트→일반 상의 전환 포함 |
| 작은 폭·큰 글씨에서 새 9탭/5개 장비 조작 | 갱신한 largeFontSettingsAndClosetKeepSingleCloseAndSelectableSlots | 최종320dp/fontScale2.0 그룹5/5 통과 | work/design-ui-native-large-final.log의 해당 테스트 STATUS_CODE 0과 최종 OK(5 tests) 확인. 9탭·5하위 슬롯 선택, 미리보기 bounds 고정, 세트→일반 상의 해제와 텍스트 overflow 검사 포함. 첫 실패 실행과 최종 통과를 구분 |
| 실제 시간 기반 8프레임 재생 | 새 AvatarPlaybackTest 4개, frameOverride 없이 검사 | 최종4/4 통과 | work/design-ui-final.log: 정상8프레임 방문/캡처, 옷장 정지·재개, Lifecycle 중단·재개, 시스템 애니메이션0 정지·복원 통과. 실제 draw0 도달과 Compose 스케줄러 정착 후5회 캡처 동일성 확인 |
| 과거 아이템과 서버 데이터 | backend/tests/test_avatar.py 등 전체27/27, 로컬 지갑 복원 계측 | 로컬 근거 확인됨 | 새 앱은 새 백엔드 필요. 이전 서버의 새 필드422 가능성 및 원격 게시 미검증 경계 유지 |
| 앱 정보의 실제 업데이트 피드 | AppUpdateRepository의 고정 GitHub raw URL, 로컬 updates/android.json | 공개 경로 미완료 | GitHub 연결 도구로 main/updates/android.json 읽기 결과404. 로컬 미추적 피드는 기존 공개0.1.1-dev/code2만 포함. 기존 공개 APK 정보 대조 후 작은 피드 게시·원격 재읽기 필요. 새 미검증 APK를 피드에 올리는 요구가 아님 |

## 검토와 결정

- 읽기 감사 에이전트가 큰 글씨 테스트 태그 불일치와 정상 재생 검증 공백을 확인했다. 다른 에이전트는 실제 SystemClock을 쓰므로 Compose 가상 시간만 전진시키는 테스트로 대체하면 안 된다고 검토했다.
- 최소 재생 검증은 4개로 분리한다: 정상 8프레임 방문 / 실제 옷장 정지·재개 / Lifecycle 중단·재개 / 시스템 애니메이션 비활성·복원.
- 캡처가 느린 AVD에서 8개 해시를 모두 맞추는 불안정성을 피하기 위해, 내부 선택적 그리기 관찰자로 실제 프레임0..7 방문을 확인하고 화면 캡처 변화도 별도로 검증한다. frameOverride는 사용하지 않는다.
- 테스트가 바꾼 animator_duration_scale은 finally에서 원래 값으로 복원한다. 원래 설정이 없었다면 삭제하여 복원한다. 사용자 비밀번호·Google 인증을 대신 입력하지 않는다.

## 순서와 소유 범위

앱 정보/디자인/Firebase 채팅이 현재 앱 소스·빌드·AVD 순서를 소유한다. 캐릭터 채팅은 앱 경로를 동시에 수정하거나 다른 빌드를 시작하지 않는다. 테스트 초안 `work/AvatarPlaybackTest.kt`와 `work/mini-playback-observer.patch`를 전달했고 해당 세션이 실제 앱에 반영했다. 통합 debug/test APK 빌드가 `work/design-information-debug-build-20261025.log`에서 2분4초에 성공했으며 첫 기기 검사까지 진행됐다.

## 첫 통합 기기 검사와 재검증 기준

기본32개 중2개 실패, native320dp/font2.0의5개 중1개 실패로 첫 결과는 34/37이다. 실패는 시스템 애니메이션 비활성 직후 캡처 안정화, 레드닷의 merged semantics 조회, 채팅 IME 대기다. 37개 전체를 통과했다고 표시하지 않는다.

시스템 애니메이션 실패의 두 해시 중 어느 것이 frame0인지는 로그만으로 알 수 없다. 읽기 리뷰에서는 설정 flag=false 도착과 다음 delay(90)에서 frame0을 실제 그리는 시점 사이의 정착 부족 가능성을 확인했다. `Thread.sleep`은 실제 시간만 흐르며 Compose 테스트 스케줄러를 진행시킨다는 보장이 없다. `onFrameRendered`의 가장 최근 실제 프레임을 관찰하고, 설정0 → waitForIdle → latestRenderedFrame==0 도달 후 기존5회 픽셀 동일성 검사를 유지하는 최소 보강안을 전달했다. 과거 프레임 집합에0이 있다는 사실로 대체하거나, 대기시간만 임의 증가하거나, 실제 재생 검사를 frameOverride로 바꾸지 않는다. 도달 후에도 픽셀이 바뀌면 제품 코드/추가 draw 원인을 더 조사한다.

최종 근거가 확보되면 이 표와 verification.json/README/CHANGELOG를 갱신한다. Google 본인 로그인·실기기·원격 서버/릴리스 공개를 자동화 테스트 결과로 대신하지 않는다. 사용자 본인 조작이 필요한 항목은 명시해 인계하고 로그인 대기용 AVD는 밤새 켜두지 않는다.

## 수정 후 통합 검사 진행

채팅을 Activity 전체 화면으로 옮긴 후 `work/design-chat-screen-debug-build2-20261025.log`에서 오프라인 debug/JVM/lint/test APK 빌드가 1분57초에 성공했다. 최종 실행은 `work/design-ui-final.log`의 기본33/33(72.071초) + `work/design-ui-native-large-final.log`의native320dp/font2.0 5/5(18.998초), 합계38/38 통과다. 재생4개 전부 STATUS_CODE 0과 최종 OK를 직접 읽어 확인했다. 이전37개 실행의 실패3개 원본은 `work/design-information-first-normal.log`, `work/design-information-first-native.log`에 보존한다.

JVM XML을 직접 합산해 28/28(AvatarArt6, AppRelease7, CloudRunSummary9, RunEngine6), debug lint XML/텍스트에서 오류0/경고24를 확인했다. 초기26개/경고28개와 구분한다. backend27개는 이번 야간에 다시 실행하지 않았으며 최초 아바타 통합의 로컬 근거다. AVD 소유 세션은 emulator-5564의 Runmoni_API36 이름 확인 후 outer finally에서 종료했다고 보고했다. 최종 서명 release·공개 피드 확인은 이 scoped 커밋 후 해당 세션이 순차 진행한다.

## 다른 세션 문서의 읽기 교차 검토

`docs/APP_INFORMATION_AND_COURSES.md`와 LocalWallet/AppViewModel/backend/CloudRunSummary를 대조해, 꾸미기 포인트는 현재 모드에 따라 기기/파일럿 지갑을 사용함, 1km=100P는 기본 환산이고 서버는 GPS 검증/일일 한도에 따라 제한함, 파일럿 원장·구매 중복 방지는 이미 존재하지만 Google UID와 두 지갑 연결은 별도임을 명확히 하도록 전달했다. 현재 Android 기록 모델은 고도/노면을 저장하지 않지만 백엔드에는 optional altitude_m가 있다는 구분도 전달했다. 앱 소스와 해당 문서는 검토 요청대로 직접 수정하지 않았다.

코스 범위는 다른 채팅의 실제 사용자 답변으로 확인했다: “디자인·앱 정보·업데이트를 먼저 완성하고 코스는 설계 확정”. 따라서 코스 공개/브리핑 API를 이번 완료 조건으로 추가하지 않는다. 개인 코스 + 작성자가 공개에 동의한 코스만 탐색한다는 별도 사용자 선택도 유지한다.
