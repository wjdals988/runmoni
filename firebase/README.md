요약: Firebase 첫 단계는 **Google 계정과 본인 러닝 요약의 수동 백업**입니다. 이 폴더의 규칙은 본인 요약 1개 경로만 허용하며 크루·파티·위치·지갑·마일리지·GPS 원본 경로는 모두 거부합니다. **검증된 보상 적립은 0개 기능이며 원격 Rules 배포는 0건**입니다.

# Firebase 개인 기록 요약 규칙 / 0.1.0

Firebase가 클라우드 경로로 선택되었습니다. 기기 Room은 원본 기록을 유지하며 로컬 FastAPI/SQLite 크루 기능은 별도로 남습니다. 이 규칙을 추가했다는 이유로 클라우드 크루·실시간 위치·LiveKit 음성이 연결된 것으로 취급하지 않습니다. `docs/FIREBASE_SETUP.md`의 기존 콘솔 준비 안내는 앱 SDK 구현 상태를 대신하지 않습니다.

## 허용 경로와 데이터

`users/{Firebase UID}/runSummaries/{run ID}`만 허용합니다. Firebase 인증된 UID가 경로 UID와 정확히 같은 사용자는 자기 컬렉션 조회, 문서 읽기, 생성·갱신·삭제를 할 수 있습니다. 비로그인 요청, 다른 사용자, 컬렉션 그룹 전체 조회 및 다른 모든 경로는 거부합니다. 앱은 Google 로그인을 사용하지만 Rules의 소유자 검사는 특정 로그인 제공자가 아니라 인증된 UID를 기준으로 합니다.

| 필드 | 필수 조건 |
|---|---|
| `schemaVersion` | 정수 `1` |
| `runId` | 문서 ID와 동일한 문자열, ASCII 영문·숫자·`_`·`-`, 길이 1~128 |
| `startedAtMillis` | 정수, 0~253,402,300,799,999(9999년까지) |
| `finishedAtMillis` | 정수, 1~253,402,300,799,999, 시작 시각 이상. 완료 기록만 허용 |
| `activeDurationMillis` | 비음수 정수, 종료-시작 이하 |
| `distanceMeters` | 유한한 숫자, 0~10,000,000 |
| `pointCount` | 정수, 0~1,000,000 |
| `backupAt` | Firestore 서버 타임스탬프, `request.time`과 동일 |

8개 필드를 모두 보내야 하고 추가 필드는 허용하지 않습니다. 따라서 `wallet`, `mileage`, `rewardPoints`, 좌표, GPS 포인트 배열 등은 요약에 넣을 수 없습니다. 생성뿐 아니라 부분 갱신 뒤 완성된 문서도 같은 규칙을 통과해야 합니다. 재백업은 같은 run ID 문서와 새 `serverTimestamp()`를 사용합니다. 별도 composite index는 현재 필요하지 않아 `firestore.indexes.json`의 목록은 비어 있습니다.

비판 관점: 이 규칙은 소유자와 자료형·범위만 검증합니다. 사용자가 자기 거리나 시간을 범위 안에서 임의로 바꿀 수 있으므로 **진짜 러닝·정확한 GPS·부정 이용 방지·마일리지 계산의 증명이 아닙니다**. 서버가 원본을 검증하는 후속 API 전까지 백업 요약을 보상 원장으로 사용하지 않습니다.

긍정 관점: 본인 요약만 조회 가능하고 원본 좌표와 보상을 포함하지 않는 작은 단위여서, 오프라인 Room 기록을 유지하면서 계정별 백업과 재백업을 먼저 검증할 수 있습니다.

## 로컬 Emulator

`.firebaserc`는 실제 Firebase 프로젝트가 아닌 **`demo-runmoni`**를 기본값으로 사용합니다. 테스트도 이 demo ID를 고정합니다. 설정의 모든 서버는 loopback `127.0.0.1`에 바인딩합니다.

| 서비스 | 포트 | 용도 |
|---|---:|---|
| Authentication Emulator | 9099 | 후속 앱 인증 흐름 로컬 시험 |
| Firestore Emulator | 8080 | 규칙 실제 실행 |
| Emulator UI | 4000 | 수동 데이터/요청 확인 |
| Hub / Logging | 4400 / 4500 | CLI 내부 조정·로그 |

설치 환경은 Node 24.11.0, npm 11.6.1, JDK 21입니다. npm의 `@firebase/rules-unit-testing` 6.0.0은 Node 24.12 이상을 요구하므로 현재 환경과 맞는 **5.0.2**, JS Firebase **12.19.0**, Firebase CLI **15.33.0**을 exact pin하고 lockfile로 관리합니다. Android 앱의 Firebase BoM 버전과 이 JS 테스트 버전은 별개입니다.

```powershell
Set-Location C:\Users\USER\Documents\runmoni\firebase
$env:FIREBASE_EMULATORS_PATH = Join-Path (Get-Location) '.cache/emulators'
# 첫 설치 / lockfile 재현. 네트워크 동시 연결은 1개로 제한.
npm.cmd ci --ignore-scripts --no-audit --no-fund --maxsockets=1 --cache .cache/npm
# Firestore를 켜고 테스트 종료 후 자동 종료.
npm.cmd test
# Auth + Firestore + UI를 수동으로 켜는 별도 명령.
npm.cmd run emulators
```

`npm test`는 Firestore만 시작합니다. `@firebase/rules-unit-testing`에서 mock UID와 익명 요청을 만들므로 실제 Google 로그인·Auth Emulator·Android SDK UI를 검증하는 테스트가 아닙니다. 포트가 다른 프로세스에 사용 중이면 그 프로세스를 임의로 종료하지 않고 충돌을 해결해야 합니다.

테스트는 `FIRESTORE_EMULATOR_HOST`가 `localhost` 또는 `127.0.0.1`인지 검사하고 실제 클라우드 호스트에서는 실행하지 않습니다. Firebase 로그인, 서비스계정 JSON, 실제 프로젝트 ID, API 비밀키는 필요하지 않습니다. `npm run emulators`는 필요한 Emulator 파일이 캐시에 없으면 최초 다운로드할 수 있습니다.

## 검증 범위와 기록

- Firestore Emulator **1.22.0**에서 **89개 통과, 실패 0개, 건너뜀 0개, 테스트 7.1968927초**를 실제 실행했습니다. `npm test`는 exit 0으로 끝났으며 Emulator도 자동 종료했습니다. 시간은 Node 테스트 집계이며 설치·다운로드·Emulator 시작 시간은 제외합니다.
- 소유자의 생성·재백업·조회·정렬·삭제, 비로그인/다른 사용자 거부, run ID·자료형·숫자 범위·필드 허용 목록, 서버 시각, 컬렉션 그룹 및 제외 경로를 검증합니다.
- `NaN`, 양/음의 무한대도 실제 JS SDK 요청으로 전송해 규칙 거부 여부를 확인합니다.
- 테스트 데이터는 Rules를 우회하는 **Emulator 전용** setup에서 만들고 각 테스트 전에 지웁니다. 제품 서버의 접근 제어를 우회하는 경로를 앱에 넣지 않습니다.

| 날짜 | 규칙 버전 | 실행 결과 | 경계 |
|---|---|---|---|
| 2026-10-14 | 0.1.0 | Node 24.11.0/npm 11.6.1/JDK 21, CLI 15.33.0, Firestore Emulator 1.22.0. **89/89 통과, 0 fail, 0 skip**, `npm test` exit 0 | Firestore 8080 + Hub/Logging 실제 기동·자동 종료. Auth 9099/UI 4000은 설정만 준비했고 미기동. 원격 배포 0건. 실제 Google 로그인·실기기 백업·클라우드 데이터 저장 미검증 |

첫 설치는 npm dev dependency **730개**를 받았습니다. 하위 의존성의 deprecated 경고 4개가 있었으나 설치는 exit 0으로 끝났습니다. 거부 테스트의 `PERMISSION_DENIED` 로그는 예상 결과이며 실패 수에 포함되지 않습니다. 전체 로컬 로그는 Git에서 제외되는 `test-results/rules-test-20261014.log`에 있습니다.

## 공식 근거

확인일: **2026-10-14**. 필드 허용 목록과 자료형은 [Firestore 필드 검증](https://firebase.google.com/docs/firestore/security/rules-fields), 정규식은 [Rules String.matches](https://firebase.google.com/docs/reference/rules/rules.String), 서버 시각 동등성은 [Rules Request.time](https://firebase.google.com/docs/reference/rules/rules.firestore.Request)을 기준으로 작성했습니다. 조회 허용은 [Security Rules의 쿼리 평가](https://firebase.google.com/docs/firestore/security/rules-query), 실행 방법과 mock 인증의 범위는 [Rules 단위 테스트](https://firebase.google.com/docs/rules/unit-tests), demo 프로젝트와 loopback Emulator는 [Emulator 설치·설정](https://firebase.google.com/docs/emulator-suite/install_and_configure), [Emulator 프로젝트 연결](https://firebase.google.com/docs/emulator-suite/connect_and_prototype)을 확인했습니다.

할루시네이션 확률: 정량 산정 불가. 구현된 규칙·실제 테스트 실행·미배포 클라우드 경계를 구분합니다.
