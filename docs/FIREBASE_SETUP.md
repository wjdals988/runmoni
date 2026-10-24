요약: **Firebase Spark로 진행하기로 확정했습니다. 아래 7단계로 콘솔을 준비하면 됩니다.** 첫 연결 범위는 Google 로그인과 **본인 완료 러닝 요약의 수동 백업**입니다. 크루·위치·지갑 전체를 클라우드로 옮기는 단계는 아닙니다.

# Runmoni Firebase 설정 — 순서대로 따라하기

기준일 **2026-10-25** · 문서 **0.3.0** · Android 패키지 **`com.runmoni.app`**

대상 프로젝트는 **[`runmoni-3c08e`](https://console.firebase.google.com/u/0/project/runmoni-3c08e/overview)** 입니다. 사용자는 Spark 생성·Android/Google 로그인·Firestore/Rules 설정과 파일 저장을 완료했다고 알려주셨습니다. 로컬 파일에서 프로젝트 ID·패키지·Web OAuth·릴리스 인증서 정보의 일치를 확인했습니다. 콘솔 내부 설정과 실제 로그인·원격 CRUD를 직접 검증했다는 뜻은 아닙니다. 아래 1~7단계는 재설정과 문제 해결에도 사용할 수 있도록 남깁니다.

| 구분 | 현재 확인 상태 |
|---|---|
| 기존 `0.1.1-dev` 서명 APK | Firebase 기능 없음 |
| Firebase 개발 코드 | Auth·Firestore SDK **2개**와 Google 계정·요약 백업 코드 있음. 이전 `0.1.2-dev`는 설정 없는 빌드·JVM **15개**·UI **17개** 통과. 현재 디자인 개편은 `0.1.3-dev`이며 새 결과는 별도 검증 |
| 실제 Firebase 연결 | 설정 파일 구조 검사 통과. Rules 게시 등 콘솔 설정은 사용자 완료 보고. 실제 Google 로그인·원격 CRUD·원격 Rules 직접 검증 **0건** |
| 기존 저장소 | 개인 GPS는 Room, 크루·파티·채팅·위치·지갑은 로컬 FastAPI + SQLite **14개 테이블** 유지 |

Supabase 파일은 이전 설계 이력입니다. 현재 선택은 Firebase이며, 기존 SQLite 계정과 Firebase 계정이 자동으로 합쳐지지는 않습니다.

## 1. 무료 Spark 프로젝트 만들기

1. [Firebase Console](https://console.firebase.google.com/)에 본인 Google 계정으로 로그인하고 **프로젝트 만들기**를 누릅니다.
2. 이름은 예를 들어 `Runmoni Pilot`로 정하고, **프로젝트 ID**를 확인합니다. ID는 생성 후 바꿀 수 없습니다.
3. 이번에는 **Google Analytics 사용 안 함**으로 진행합니다. 기본 로그인·요약 DB 준비에는 필수가 아닙니다.
4. 생성 후 요금제가 **Spark**인지 확인합니다. 무료 범위를 유지하려면 결제 계정을 연결하지 않습니다. [공식 프로젝트 설정](https://firebase.google.com/docs/android/setup), [Spark 요금제](https://firebase.google.com/docs/projects/billing/firebase-pricing-plans).

## 2. Android 앱 등록하기

1. 프로젝트 개요의 **Android 아이콘 / 앱 추가**를 누릅니다.
2. Android 패키지 이름에 **`com.runmoni.app`**을 정확히 입력합니다. 닉네임은 `Runmoni Android`처럼 정해도 됩니다.
3. **앱 등록**을 누릅니다. 패키지 이름은 대소문자를 구분하며 등록 후 변경할 수 없습니다. 콘솔의 **SDK 추가·Gradle 코드 안내는 건너뛰어도 됩니다.** 앱 코드 작업은 개발 단계에서 처리합니다. [공식 Android 등록](https://firebase.google.com/docs/android/setup).

## 3. 공개 서명 지문 2개 등록하기

**프로젝트 설정 → 일반 → 내 앱 → Android 앱 → SHA 인증서 지문 추가**에서 아래 두 값을 각각 등록합니다. 2026-10-24 현재 release APK의 인증서로 재확인한 값입니다.

| 종류 | 그대로 붙여넣을 값 |
|---|---|
| SHA-1 | `61:27:E1:0B:16:3A:79:2C:53:97:5B:EA:3A:F8:03:60:9D:84:A7:80` |
| SHA-256 | `2D:7F:72:4C:D2:0B:1F:F2:26:3E:B3:4E:8E:02:D5:FE:C9:DB:67:44:88:B1:0B:81:3D:D2:39:83:8B:97:58:30` |

Google 로그인에는 **SHA-1이 필요**합니다. 두 값은 공개 인증서 지문이며 APK 파일 체크섬이나 서명 비밀키가 아닙니다. 향후 debug·Play 서명 APK로 테스트할 때는 해당 인증서도 별도 등록합니다. [공식 SHA-1 요구 사항](https://firebase.google.com/docs/auth/android/google-signin).

## 4. Google 로그인 켜기

1. **Authentication → 시작하기 → 로그인 방법 / Sign-in method**로 이동합니다.
2. **Google**을 선택하고 **사용 설정**을 켭니다.
3. 지원 이메일을 요구하면 본인이 관리하는 이메일을 선택한 뒤 **저장**합니다.
4. 이번에는 전화번호/SMS나 다른 로그인 제공자를 추가하지 않습니다. 이 설정만으로 기존 APK가 바뀌지는 않습니다. [Google 로그인 공식 절차](https://firebase.google.com/docs/auth/android/google-signin).

## 5. Firestore 만들기

1. **Firestore Database → 데이터베이스 만들기**를 누릅니다.
2. 선택 화면이 나오면 **Standard edition**, 첫 DB **`(default)`**를 사용합니다.
3. 위치는 한국 사용자를 기준으로 **`asia-northeast3` / Seoul**을 선택합니다. 생성 후 위치를 변경할 수 없으므로 선택값을 확인한 뒤 생성합니다.
4. 시작 규칙은 **Production mode / 프로덕션 모드**를 선택합니다. 생성 직후에는 모바일·웹 읽기/쓰기가 모두 거부됩니다. 다음 6단계에서 본인 요약 경로만 허용합니다. [공식 생성 절차](https://firebase.google.com/docs/firestore/quickstart), [Seoul 위치·변경 불가 조건](https://firebase.google.com/docs/firestore/locations).

## 6. 본인 요약 전용 Rules 게시하기

1. 이 저장소의 [firebase/firestore.rules](../firebase/firestore.rules)를 열고 **파일 전체**를 복사합니다.
2. 콘솔 **Firestore Database → 규칙 / Rules** 탭에서 기존 내용을 전체 교체합니다.
3. **게시 / Publish**를 누르고 오류 없이 반영됐는지 확인합니다. 이 단계는 사용자가 자신의 프로젝트에 적용하는 단계입니다. 문서 작성자가 원격으로 게시한 것은 아닙니다.
4. `allow read, write: if true;` 같은 전체 허용 규칙으로 바꾸지 않습니다. 기존 데이터가 있는 다른 프로젝트라면 덮어쓰기 전에 프로젝트 ID를 다시 확인합니다.

이 규칙은 **`users/{uid}/runSummaries/{runId}`의 소유자만** 조회·쓰기·삭제를 허용합니다. 다른 사용자·비로그인·크루·파티·공유 위치·지갑 경로는 거부합니다. 로컬 Emulator에서 **89/89 통과, 실패 0·건너뜀 0**을 2026-10-14 확인했으며, 이것이 실제 프로젝트에 이미 배포됐다는 뜻은 아닙니다. [검증 범위](../firebase/README.md), [공식 Rules 안내](https://firebase.google.com/docs/firestore/quickstart#secure_your_data).

## 7. 최신 설정 파일 내려받아 저장하기

1. Google 로그인을 켠 **뒤에**, **프로젝트 설정 → 일반 → 내 앱 → Android 앱**에서 최신 **`google-services.json`**을 내려받습니다.
2. 이 PC에서 아래 경로에 정확히 저장합니다. 폴더가 없으면 만듭니다. 파일명이 `google-services (1).json`처럼 바뀌지 않았는지 확인합니다.

```text
C:\Users\USER\Documents\runmoni\.secrets\firebase\google-services.json
```

3. 이 프로젝트의 지정 위치는 위 경로입니다. [app/build.gradle.kts](../app/build.gradle.kts)가 파일을 읽어 `BuildConfig`를 만들고, [FirebaseCloudAccount.kt](../app/src/main/java/com/runmoni/app/data/FirebaseCloudAccount.kt)가 `FirebaseOptions`로 초기화합니다. **Google Services Gradle 플러그인은 사용하지 않습니다.** 콘솔 마법사의 Gradle 코드를 직접 추가하지 않아도 됩니다.
4. 파일에는 Google 로그인용 **Web OAuth client 정보**도 있어야 합니다. 누락 오류가 나면 4단계 활성화·저장 후 파일을 다시 받습니다. [공식 최신 설정 파일 안내](https://firebase.google.com/docs/auth/android/google-signin).
5. 완료하면 채팅에는 **프로젝트 ID와 “파일 저장 완료”만** 알려주세요. 파일 전체나 서비스계정 비밀키를 붙여넣을 필요가 없습니다. Android 설정 파일은 앱 식별 정보이고, 서비스계정 JSON의 `private_key`와는 다릅니다. 이번 단계에서 서비스계정 키는 생성하지 않습니다. [설정 정보와 API 키의 성격](https://firebase.google.com/docs/projects/api-keys).

저장 후 개발 단계에서는 `scripts/check-firebase-config.ps1`로 프로젝트 ID·Android 패키지·Web OAuth·릴리스 인증서 포함 여부를 검사합니다. **2026-10-25 네 항목 모두 일치**했습니다. API 키나 파일 전체를 출력하지 않습니다. 앱 빌드도 `runmoni-3c08e`가 아닌 프로젝트 설정을 거부합니다. 이 검사는 원격 로그인·DB 권한·Rules 게시를 대신하지 않습니다.

## 여기까지 끝나면 다음에 할 일

설정을 포함한 새 APK를 빌드하고, 실기기에서 **설정 → Google로 로그인 → 완료 기록 상세 → 이 기록 요약을 계정에 백업 → 백업한 기록 요약 보기**를 확인합니다. 이어서 계정 전환 시 이전 계정의 목록이 비워지고 타인 경로 접근이 거부되는지, 요약 삭제가 기기 기록을 지우지 않는지 검증합니다. 실기기에 설치할 파일은 위 SHA-1에 해당하는 보관용 release 키로 서명합니다. 기본 debug 키로 Google 로그인을 시험하려면 debug SHA-1도 콘솔에 별도 등록해야 합니다. 기존 `0.1.1-dev` APK에 설정 파일만 넣어 기능이 추가되지는 않습니다.

백업 필드는 **8개**입니다: `schemaVersion`, `runId`, `startedAtMillis`, `finishedAtMillis`, `activeDurationMillis`, `distanceMeters`, `pointCount`, 서버 시각 `backupAt`. GPS 좌표·전체 경로·마일리지·보상·아이템은 보내지 않습니다. 요약을 읽어도 원본 GPS 경로가 복원되는 것은 아닙니다.

| Spark에서 준비 가능한 범위 | 무료 한도 / 제약 |
|---|---|
| Google 로그인 + Firestore 요약 | 프로젝트 전체 **1 GiB**, 읽기 **50,000/일**, 쓰기 **20,000/일**, 삭제 **20,000/일**, 전송 **10 GiB/월**, 무료 DB **1개** |
| Cloud Storage 파일 업로드 | **2026-02-03부터 Blaze 필요** |
| Cloud Functions 서버 배포 | **Blaze 필요** |

가격·한도는 2026-10-24 공식 문서로 재확인했습니다. [Firestore 한도](https://firebase.google.com/docs/firestore/quotas), [Storage 결제 조건](https://firebase.google.com/docs/storage/faqs-storage-changes-announced-sept-2024), [Functions 배포 조건](https://firebase.google.com/docs/functions/get-started).

긍정 관점: 결제 계정 없이 계정별 요약 백업을 작게 시작하고 Room의 원본 GPS를 유지할 수 있습니다. 비판 관점: 이 요약은 사용자가 입력값을 바꿀 수 있는 자기 기록이므로 보상·부정 이용 방지 근거가 아닙니다. 크루·위치·지갑의 Firebase 이관과 신뢰 가능한 서버 검증은 후속 작업입니다.

개발·기기 연결은 **localhost 또는 USB `adb reverse`**를 사용합니다. 외부 공개 터널이나 방화벽 예외를 테스트 절차에 추가하지 않습니다.

## 문서 변경 이력

| 날짜 | 버전 | 목적·확인·남은 경계 |
|---|---|---|
| 2026-10-13 | 0.1.0 | 무료 콘솔 준비 안내 작성. 당시 Firebase SDK 미추가 상태 기록 |
| 2026-10-24 | 0.2.0 | Firebase 선택 확정, APK와 소스 구분, 콘솔 7단계·Rules 게시·실제 설정 경로로 수정. Gradle/초기화 코드와 공식 문서 확인. 이번 문서 작업의 원격 설정·배포·커밋·푸시는 **0건**; 실제 Google 로그인·원격 CRUD·새 APK 공개는 미확인 |
| 2026-10-24 | 0.2.1 | 공개 APK의 인증서 지문 2개를 apksigner로 대조. Google 로그인·Spark·Firestore 한도·Storage/Functions 결제 조건을 공식 문서로 재확인. 설정 없는 debug 빌드·JVM 15개 통과와 실제 클라우드 검증 0건을 구분 |
| 2026-10-25 | 0.3.0 | 사용자가 생성한 Spark 프로젝트 `runmoni-3c08e`로 대상을 고정. 남은 콘솔 2~7단계·설정 검사 스크립트·이전 개발본 검증과 현재 디자인 개편 버전을 구분. 원격 설정은 직접 확인하지 않음 |

할루시네이션 확률: 정량 산정 불가. 현재 코드·공식 문서·과거 Emulator 검증과 실제 클라우드 미검증 범위를 구분했습니다.
