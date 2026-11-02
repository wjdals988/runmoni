# 런모니 개발 이어가기 — 2026-11-02 (한국 시간)

사용자 요청: ‘끝나면 이어서 추가로 진행할 부분 업데이트 해서 계속 진행할 수 있도록 해’. 이 문서는 다음 작업의 시작점이다. 상세 상태/완료 조건은 [개발 우선순위](DEVELOPMENT_PRIORITIES.md)의 F/U ID를 사용한다. 작업 종료 시 현재 상태·검증·커밋/PR·배포·다음 작업을 함께 갱신한다.

## 현재 상태와 확인 출처

| 항목 | 현재 상태 | 근거/검증 경계 |
|---|---|---|
| 공개 앱 | **0.2.1-dev / code11**, 실기기 검증 전 개발 prerelease | [릴리스](https://github.com/wjdals988/runmoni/releases/tag/v0.2.1-dev), [배포 검증](RELEASE_REVIEW_20261101.md) |
| APK 출처 | `c6bc5739746fe176269fed3bf64360417b50875e` | 빌드/내장 출처. 후속 main/문서 커밋과 구분 |
| APK 바이트 | 218431245bytes, SHA256 `2b74ee38f56be2fcfbe88a5b74b2a11c84c8817c961da467a5f7151f12833218` | CI의 실제 APK·GitHub3자산·공개 HEAD 대조 완료 |
| 서명 | 기존0.1.6/0.2.0과 동일 보관 키 | `config/android-release-signing.json`, 새 키 생성 불필요 |
| 다운로드 | 공개 업데이트 피드와 [운영 대시보드](https://coldbrewventi.vercel.app/projects/runmoni) 반영 완료 | 앱 PR15/16/17, 대시보드 PR12. 대시보드 main `1eba844093970da85fc386513f85bd80b8a1e3e9` |
| Firebase 구성 | 기본 `runmoni-3c08e`, Spark, 익명 방 계정과 Google 요약 계정 별도 | CI의 실제 보관 인증서·현재 Android/Web OAuth·운영 공개 방 목록 읽기 통과 |
| Firebase 콘솔 | **설정1~5번 소유자 확인 완료** | 소유자가 ‘5번도 완료됐음 굳굳’이라고 보고. 제공자/규칙/색인 관리 API 직접 조회 결과는 아님 |
| 실제 휴대폰/Google/운영 방 쓰기 | **미검증·결과 대기** | [휴대폰 확인 순서](FIREBASE_COMMUNITY.md#휴대폰에서-확인하는-순서). 콘솔 완료를 앱 동작 성공으로 바꾸지 않음 |

이전 CI의 JVM42개·release lint오류0/경고60, demo 규칙110개 및 Android 검사 결과는 각 실행의 근거다. 이번 문서 갱신에서 새 앱 실행을 수행한 것은 아니다. 이전 전체 화면에는 System UI ANR가 겹쳤으므로 의미/콜백 검사 통과를 가려지지 않은 앱 화면/실기기 성공으로 표시하지 않는다. [기존 제한](app-review/2026-10-30-firebase/README.md).

색인은 서버 설정이다. 완료 확인만으로 APK 재빌드·키 생성·Secret 재등록을 반복하지 않는다. 과거 `indexCompletionVerified=false` 등 CI/배포 JSON은 당시 증거로 보존하고, 최신 소유자 보고는 이 문서에 별도로 기록한다.

## 다음 구현: F02+U01 여자 기본형 선택 연결

현재 코드를 직접 확인했다. [ApprovedAvatarRenderer.kt](../app/src/main/java/com/runmoni/app/avatar/ApprovedAvatarRenderer.kt)는2048×768 원본에서 `y=0`의 첫 행만 잘라8개 프레임을 캐시한다. [MiniMi.kt](../app/src/main/java/com/runmoni/app/ui/MiniMi.kt)의 `avatarBitmap`은 현재 `AvatarStyle`을 표시 선택에 사용하지 않는다. 즉 여자 기본형 선택 연결은 아직 없다. 아래 내용은 **다음 구현 범위**이며 완료된 기능이 아니다.

1. 승인 원본의 두 행을 기본형 선택으로 연결한다. 파일 `docs/design/avatars/v0.3.0/running-motion-art-source.png`와 번들 사본을 수정/덮어쓰지 않는다. SHA256 `f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65`, 셀256×384, 각 행8프레임을 그대로 사용한다. 헤어로 성별을 추측하거나 새 신체/관절 부품을 생성하지 않는다.
2. 기본형은 기존9개 옷장 탭/13개 장비 슬롯/58개 아이템 ID와 별도 선택으로 설계한다. 모델 필드가 필요하면 기존 저장 JSON에 없을 때 첫 행을 기본값으로 두고 잘못된 값도 정규화한다. 필드 이름/표현은 구현 시 결정하되 예전 데이터·가격·보유권·포인트를 보존한다.
3. 두 행의 캐시를 구분하고 홈·나·옷장·지도 마커의 공유 렌더링 경로를 연결한다. 정지0번 프레임/8프레임 재생, 비동기 준비·실패/재시도·빠른 변경 뒤 최신 선택 표시를 유지한다.
4. 옷장에서는 탭 위 기본형 선택과 실제 원본 미리보기를 제공한다. 새 구매 항목이나10번째 탭을 만들지 않는다. 큰 글씨/작은 화면에서 선택·미리보기·재생 조작에 접근할 수 있게 한다.
5. 저장/프로세스 재시작·기존 필드 없는 JSON·HTTP 파일럿 왕복을 확인한다. HTTP 서버 모델이 기본형 필드를 누락/초기화하는지 검사하고 실제 변경 범위를 정한다. Firebase 방에 개인 꾸미기/지갑/GPS를 새로 업로드하지 않는다.
6. 여자 기본형 표시를 의상58종·수영복·여자 양팔 동작 보정 완료로 표시하지 않는다. 비기본 의상 구매/착용 제한은 프레임 원화가 준비될 때까지 유지한다. 다음 원화 작업은 F03에서 별도로 검토한다.

주요 수정/확인 경로:

| 경로 | 역할 |
|---|---|
| [Models.kt](../app/src/main/java/com/runmoni/app/domain/Models.kt), [AppActions.kt](../app/src/main/java/com/runmoni/app/domain/AppActions.kt), [AppViewModel.kt](../app/src/main/java/com/runmoni/app/AppViewModel.kt) | 선택 모델·행동·상태/저장 연결 |
| [LocalWallet.kt](../app/src/main/java/com/runmoni/app/data/LocalWallet.kt), [PilotClient.kt](../app/src/main/java/com/runmoni/app/data/PilotClient.kt), [backend/models.py](../backend/models.py) | 기존 프로필 JSON/선택 HTTP 경로의 호환 확인 |
| [ApprovedAvatarRenderer.kt](../app/src/main/java/com/runmoni/app/avatar/ApprovedAvatarRenderer.kt), [MiniMi.kt](../app/src/main/java/com/runmoni/app/ui/MiniMi.kt), [MapPane.kt](../app/src/main/java/com/runmoni/app/ui/MapPane.kt) | 원본 행/프레임·캐시·공통 표시 |
| [RunmoniDialogs.kt](../app/src/main/java/com/runmoni/app/ui/RunmoniDialogs.kt), [RunmoniDestinations.kt](../app/src/main/java/com/runmoni/app/ui/RunmoniDestinations.kt) | 옷장 선택·홈/나 표시 |
| [ApprovedAvatarRenderingTest.kt](../app/src/androidTest/java/com/runmoni/app/ui/ApprovedAvatarRenderingTest.kt), [MiniMiLoadingTest.kt](../app/src/androidTest/java/com/runmoni/app/ui/MiniMiLoadingTest.kt), [AvatarIntegrationTest.kt](../app/src/androidTest/java/com/runmoni/app/ui/AvatarIntegrationTest.kt) | 두 행의 원본/캐시·저장·불러오기 회귀를 확장할 대상 |

완료 기준은 두 행×8프레임의 원본 픽셀 대조, 선택/저장·재시작·오래된 JSON 호환, 캐시 혼선 방지, 기존 보유권/점유 규칙 보존이다. UI는320dp/글씨200%·키보드/스크롤·48dp 행동·애니메이션 비활성 설정을 확인하고 실제 출력과 한계를 기록한다. 테스트 통과와 사용자 시각 승인은 구분한다. [DESIGN.md](../DESIGN.md)의 awesome-design-md 참고 기준을 따르고, 원화에는 Aseprite 레이어/프레임 개념을 참고하되 실제 실행 여부를 정확히 기록한다.

## 휴대폰 결과가 오면

F01에서 실제 실패가 보고되면 다음 기능보다 먼저 재현/수정한다. 성공 보고도 어떤 버전에서 설치/Google 계정 선택/온라인 세션/방 생성/두 기기 채팅/백업을 각각 수행했는지 구분한다. ‘5번 완료’는 콘솔 색인 완료 보고이며 위 앱 동작 성공 보고가 아니다. 결과는 비밀번호·토큰·설정 JSON·개인 GPS를 제외한 앱의 점검 공유 텍스트 또는 오류 문구로 받는다.

## 작업을 마칠 때

기본 Gradle은 캐시 우선/오프라인/작업자1개다. 설치·빌드·전송·이미지 생성은 겹치지 않는다. 앱 변경에 맞는 JVM/Android·저장 호환 검사를 실행하고 새 의존성이 없으면 고지 목록을 불필요하게 재생성하지 않는다. AVD가 필요하면 클라우드 `Runmoni_Cloud_29` 하나만 headless/TCG로 검증 후 종료한다. System UI 팝업이 앱을 가리면 그 상태를 기록하고 깨끗한 앱 화면으로 소개하지 않는다. 사용자 PC의 AVD·공개 터널/방화벽 예외는 사용하지 않는다.

다음 실제 앱 변경 묶음의 버전/code와 번들 내역·CHANGELOG를 함께 정한다. 현재 공개0.2.1/code11·출처·해시를 새 문서 커밋으로 바꾸지 않는다. 커밋·푸시·PR·검증 결과를 남기고, 새 APK는 기존 보관 키/CI로 서명·실제 패키지/버전/내장 출처/서버 자산 검증 후 게시한다. 게시한 APK에 한해 공개 피드/대시보드를 갱신한다. 유료 호스팅·새 키·설정 원문/관리 자격을 임의로 추가하거나 요청하지 않는다.

다음 세션에서는 `AGENTS.md` → 이 문서 → `DEVELOPMENT_PRIORITIES.md` → `DESIGN.md` → `CHANGELOG.md`를 읽고 최신 main/작업 트리를 확인한다. F01 결과가 없다면 F02+U01을 구현·검증하고 커밋/푸시/PR까지 진행한다. 결과 대기를 이유로 독립 개발을 멈추지 않는다.
