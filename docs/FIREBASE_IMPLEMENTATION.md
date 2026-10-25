# Firebase 첫 단계 — 0.1.4-dev / 2026-10-25

0.1.4는 Google 버튼에서 계정 추가를 지원하고 고정 오류코드로 단계를 구분한다. 실제 Google/Firebase 인증·원격 CRUD 성공은0회이며 최신 사용자 지시로 AVD 검증은 제외한다. 상세 절차·결과는 [기기 검증](FIREBASE_DEVICE_CHECK.md)과 [현재 검증 기록](VALIDATION.md)에 분리한다.

AVD 제외 후 공식 client API의 공개 설정 및 Google 인증URI 생성2건은HTTP200/200을 확인했다. Android패키지/release SHA1/Webclient/FirebaseAppId 검증 인수와 반환Webclient 일치는 통과했지만 실제 사용자 인증·원격CRUD 검증을 대신하지 않는다. 비밀번호·사용자식별자·토큰은 보내지 않았고 API키·OAuth상태·원문응답은 기록하지 않았다.

사용자가 무료 Spark를 선택했습니다. 공용 계정·DB의 목표는 Firebase Auth/Firestore입니다. 이번 변경은 Google 계정 세션과 본인 러닝 요약 백업의 코드·로컬 검증이며 원격 크루 DB 전환 완료가 아닙니다.

## 구현 경계

| 항목 | 이번 변경 |
|---|---|
| 계정 | Credential Manager Google 선택 → Firebase Auth. SDK 세션 복구·로그아웃. 파일럿 계정과 별도 |
| 백업 | 사용자가 고른 완료 기록 1건, 현재 Google 계정 확인 후 UID 경로에 저장 |
| 조회·삭제 | 서버에서 최근 50개 요약 조회. 클라우드 요약 삭제가 Room 기록을 삭제하지 않음 |
| 데이터 | `schemaVersion`, `runId`, 시작·완료·활동 시간, 거리, GPS 포인트 수 + 서버 `backupAt` |
| 제외 | 원본 좌표·경로·마일리지·보상·서버 검증 여부·아이템 소유 정보 |
| 현재 기록 | 원본과 기기 마일리지는 Room/로컬 지갑에 유지. 기존 `synced`의 의미를 변경하지 않음 |
| 커뮤니티 | 기존 SQLite API에 유지. Firebase 로그인만으로 파일럿 위치·크루 권한을 얻지 않음 |

Room 기록에는 계정 소유 UID가 없습니다. 다른 계정으로 로그인했다고 기기의 전체 기록을 자동 업로드하지 않습니다. 확인창에 대상 Google 계정을 표시하며 계정이 바뀌면 확인을 다시 받습니다. 같은 run ID를 재백업하면 문서를 추가하지 않고 같은 문서를 갱신합니다. 요약은 사용자가 수정할 수 있는 자가 기록으로 실제 러닝이나 마일리지의 증명이 아닙니다.

Firestore SDK는 메모리 캐시를 사용하고 조회는 `Source.SERVER`로 수행합니다. 각 요청은 UID·세션 epoch·작업 순서를 캡처해 이전 계정의 완료 결과를 새 계정 화면에 반영하지 않습니다. 네트워크 응답 제한은 15초입니다. 시간 초과가 이미 요청한 쓰기의 취소를 보장하지 않으므로 화면에서 미확인 상태를 알리고 서버 요약을 다시 조회하도록 안내합니다.

## 설정과 버전

사용자가 Spark 프로젝트 `runmoni-3c08e`를 만들고 `.secrets/firebase/google-services.json`을 저장했습니다. 2026-10-25 로컬 검사에서 프로젝트 ID·`com.runmoni.app` 패키지·Web OAuth·보관용 release 인증서 등록 정보의 4개 항목이 일치했습니다. 설정 포함 debug/release 빌드와 release `FIREBASE_ENABLED=true`도 확인했습니다. 파일 전체나 API 키는 출력하지 않았고 Git에서 계속 제외합니다. 콘솔 제공자·Rules 설정 완료는 사용자 보고이며 실제 Google 로그인·원격 저장 성공을 의미하지 않습니다.

파일이 없으면 APK를 빌드할 수 있지만 Google 로그인 버튼은 비활성화됩니다. 잘못된 패키지·다른 프로젝트 ID·서비스계정 키·Google OAuth 설정 누락은 빌드에서 거부합니다. 서버 비밀키는 APK에 들어가지 않습니다. 이후 실제 확인 순서는 [계정·백업 검증 절차](FIREBASE_DEVICE_CHECK.md), 콘솔 설정은 [설정 가이드](FIREBASE_SETUP.md)를 따릅니다.

Android는 Kotlin 2.0.21/AGP 8.11.1을 유지하면서 Firebase BoM **33.16.0**, Auth **23.2.1**, Firestore **25.1.4**, Credential Manager **1.3.0**, Google ID **1.1.1**을 고정했습니다. 현재 최신 BoM을 사용했다는 주장은 하지 않습니다. 출시 전 SDK·도구 체인을 함께 갱신하고 실제 기기 재검증을 수행해야 합니다. [공식 BoM 이력](https://firebase.google.com/support/release-notes/android).

## 규칙과 무료 사용량

`users/{uid}/runSummaries/{runId}`의 본인 접근만 허용합니다. 8개 필드, 시간·거리·ID·개수, 서버 시각을 검증하고 다른 경로는 거부합니다. [규칙 코드](../firebase/firestore.rules)와 [89개 실제 Emulator 검증](../firebase/README.md)을 확인해 주세요. 실제 프로젝트의 게시 완료 여부는 사용자 보고이며 개발 도구로 원격 Rules를 읽어 대조하지는 않았습니다.

20명이 하루에 기록 요약을 1개씩 백업하면 **20쓰기/일**, 하루 무료 20,000쓰기의 **0.1%**라는 설계 계산입니다. 각 사용자가 하루에 50개 요약을 한 번 읽으면 **1,000읽기/일**, 무료 50,000읽기의 **2%**입니다. 재백업·재조회·Rules 추가 읽기·재연결·다른 기능은 제외한 계산이며 실제 계측이 아닙니다. [공식 한도](https://firebase.google.com/docs/firestore/quotas).

위치 15초 전원 구독과 채팅 3초 polling을 Firestore로 그대로 옮기지 않습니다. 다음 단계는 현재 FastAPI **43개 경로·87개 SQL 실행 지점**에서 저장소와 권한을 분리하는 작업입니다. Firebase UID를 현재 UUID에 안전하게 매핑하고 정원·보상·DJ lease의 원자성을 Firestore Emulator에서 다시 검증해야 합니다. 신뢰 서버는 클라이언트의 마일리지 값을 받아 그대로 원장에 쓰지 않습니다.

Spark는 무료 Auth/Firestore 준비에 사용합니다. Cloud Functions 배포와 Cloud Storage 사용은 Blaze가 필요하며 LiveKit/audio bridge도 별도 운영 대상입니다. 이번 작업에서 결제 계정·유료 서비스·원격 서버를 만들지 않았습니다.

## 검증 기록

- 실제 Firestore Emulator 1.22.0: 89/89 통과, 0 fail·0 skip, Node 테스트 7.1968927초. Auth/UI는 이 실행에서 기동하지 않았습니다.
- 모델 JUnit 9개와 계정 경계 UI 3개를 추가했습니다. Android 빌드·실행 최종 결과는 [검증 기록](VALIDATION.md)에 별도 기입합니다.
- 실제 Google 로그인·Google 계정 전환·원격 백업의 직접 검증은 **0회**입니다. Rules 콘솔 설정은 사용자 보고와 구분합니다. Mockito나 실제 Google 인증으로 위 89개를 실행했다는 주장을 하지 않습니다.
- 초기 다운로드는 직렬로 진행했습니다. PowerShell Gradle 인자 오류, 전이 JAR 캐시 누락, SDK getter 이름 오류를 고친 뒤 Windows immutable transform 이동 오류는 파일 삭제 없이 재시도했습니다.
- 2026-10-24 설정 없는 `0.1.2-dev` debug/test APK의 온라인·오프라인 빌드가 통과했습니다. JVM 15/15(요약 모델 9 + 러닝 6), lint 오류 0·경고 47개입니다. 실제 설정이 없으므로 `FIREBASE_ENABLED=false`이며 Google 인증·원격 백업의 성공 증거가 아닙니다.
- 새 APK UI 17/17(기존 14 + Firebase 경계 3)이 전용 API36 에뮬레이터에서 78.299초에 통과했습니다. 실제 인증을 대체하는 테스트는 아닙니다. 테스트용 에뮬레이터는 검증 후 종료했습니다.
- 이전 `0.1.1-dev` 서명 APK는 200,693,280bytes/SHA-256 `25772671956108deed9bde887104d83b4b7eefd7d2dbd5c7b4223d143db78de9`로 [GitHub에 공개](https://github.com/wjdals988/runmoni/releases/tag/v0.1.1-dev)했습니다. 이 APK에는 Firebase SDK가 없습니다.
- 2026-10-25 `0.1.3-dev`/code4의 설정 포함 release 빌드·APK v2 서명 검증을 통과했습니다. 기존 공개 APK와 인증서 SHA-256이 같습니다. 이 결과는 원격 인증·기록 저장·실기기 검증이나 새 GitHub APK 게시의 증거가 아닙니다.
- 전용 `Runmoni_API36` AVD에는 Google 계정 0개임을 확인했습니다. 계정 이름·토큰은 출력하지 않았습니다. 사용자가 화면 검증 후 로그인용 AVD 창을 열어 직접 로그인하는 절차를 선택했습니다.

긍정적 활용: 무료 프로젝트를 준비하면 계정 소유권과 소량 요약 백업부터 시험할 수 있습니다. 비판적 검토: 이 단계로 크루·실시간 위치·음성·DJ가 인터넷에서 동작하는 것은 아니며, 자가 기록 요약은 보상 검증을 대체하지 않습니다.
