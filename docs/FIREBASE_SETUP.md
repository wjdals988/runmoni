요약: **Firebase Spark 프로젝트·Android 앱 등록·Google 로그인 제공자·빈 Firestore DB는 사용자가 무료로 준비할 수 있습니다.** 현재 Runmoni APK에는 Firebase SDK가 0개이고 이번 작업의 Firebase 원격 연동·프로젝트 생성은 각각 0건입니다. 콘솔 준비를 마쳐도 기존 APK의 크루·위치 기능이 Firebase에 연결되지는 않습니다. 클라우드 DB를 Firebase로 전환할지는 아직 미확정입니다.

# Runmoni Firebase 무료 준비 가이드

- 공식 문서 확인일: **2026-10-13, 한국 시간 기준**
- 가이드 버전: **0.1.0**
- 대상 Android application ID: **`com.runmoni.app`**
- 범위: 사용자가 콘솔에서 설정할 절차와 후속 설계. SDK 추가·Rules 배포·서버 교체·외부 프로젝트 생성은 수행하지 않았습니다.

## 1. 현재 저장소와 Firebase 준비의 관계

| 기능 | 현재 실제 저장소 | 이 가이드를 마친 뒤 |
|---|---|---|
| 개인 러닝·GPS 원본 | 기기 Room 2개 테이블 | Room을 계속 사용. Firebase 동기화는 미구현 |
| 계정·크루·파티·채팅·마일리지·공유 위치 | 로컬 FastAPI + SQLite 14개 테이블 | 기존 API를 계속 사용. Firestore adapter는 미구현 |
| 클라우드 설계 | Supabase SQL/RLS/RPC 준비, 미배포 | Firebase와 Supabase 중 최종 선택은 미확정 |

현재 코드는 [Room DB](../app/src/main/java/com/runmoni/app/data/RunDatabase.kt), [로컬 API](../backend/main.py), [Supabase 준비 상태](../supabase/README.md)에 있습니다. Supabase SQL은 Firestore에 가져올 수 있는 파일이 아니며, Firebase 프로젝트를 만들어도 SQLite 계정·기록이 자동 이관되지 않습니다.

## 2. Spark에서 되는 것과 결제가 필요한 것

무료 한도는 **사용자별이 아니라 프로젝트 전체**에 적용됩니다. 아래 Firestore 수치는 **Standard edition** 기준입니다.

| 항목 | 무료 Spark 범위 / 요구 사항 |
|---|---|
| Google 로그인 | Spark에서 준비 가능. 인증·악용 방지 한도는 별도 적용 |
| Firestore 저장 | **1 GiB**, 메타데이터·인덱스 사용량도 고려 |
| 문서 읽기 | **50,000회/일** |
| 문서 쓰기 | **20,000회/일** |
| 문서 삭제 | **20,000회/일** |
| 외부 데이터 전송 | **10 GiB/월** |
| 무료 Firestore DB | 프로젝트당 **1개** |
| Firestore 자동 TTL 삭제·PITR·백업/복원·복제 | 무료 사용 대상이 아니며 결제 활성화 필요 |
| Cloud Storage for Firebase | **2026-02-03부터 Blaze 필요**. Spark만으로 새 이미지·음악 파일 저장소를 운영할 수 없음 |
| Cloud Functions 배포 | **Blaze 필요**. 로컬 Emulator 실행은 가능 |

[공식 가격표](https://firebase.google.com/pricing), [Firestore 한도](https://firebase.google.com/docs/firestore/quotas), [Firestore 과금 기준](https://firebase.google.com/docs/firestore/pricing), [Storage 결제 변경](https://firebase.google.com/docs/storage/faqs-storage-changes-announced-sept-2024), [Functions 배포 요구 사항](https://firebase.google.com/docs/functions/get-started)을 확인했습니다.

Firestore 일별 한도는 태평양 시간 자정 무렵 초기화됩니다. 한국 시간으로는 미국 서머타임에 따라 대략 **16시 또는 17시**입니다. Spark 한도를 넘기면 해당 서비스 요청이 제한될 수 있으므로 무료 한도를 안정적인 처리 용량으로 간주하지 않습니다. Cloud Billing 계정을 연결하면 Blaze로 전환됩니다. 무료 준비 단계에서는 결제 계정을 연결하지 않습니다. [공식 한도](https://firebase.google.com/docs/firestore/quotas), [요금제 전환](https://firebase.google.com/docs/projects/billing/firebase-pricing-plans).

## 3. 사용자가 콘솔에서 준비할 순서

### A. Firebase 프로젝트 생성

1. [Firebase Console](https://console.firebase.google.com/)에 소유자 Google 계정으로 로그인합니다.
2. **프로젝트 만들기**를 선택합니다. 표시 이름은 예를 들어 `Runmoni Pilot`로 정합니다.
3. 프로젝트 ID를 확인합니다. ID는 전역 고유 값이고 생성 후 바꿀 수 없으므로 사용자가 선택합니다.
4. Google Analytics는 이번 기본 인증·DB 준비에 필수인 단계가 아니므로 필요 없으면 생략합니다.
5. 생성 완료 후 요금제가 **Spark**인지 확인합니다. Storage·Functions·Cloud Run을 추가하거나 Billing 연결을 진행하지 않습니다.

공식 절차: [Android 프로젝트 등록](https://firebase.google.com/docs/android/setup), [Spark 요금제](https://firebase.google.com/docs/projects/billing/firebase-pricing-plans).

### B. Android 앱 등록과 공개 인증서 지문

1. 프로젝트 개요에서 **Android 앱 추가**를 선택합니다.
2. 패키지 이름에 정확히 **`com.runmoni.app`**을 입력합니다. 대소문자를 구분하며 등록 후 해당 Firebase Android 앱의 패키지 이름은 변경할 수 없습니다.
3. 앱 닉네임은 예를 들어 `Runmoni Android Pilot`로 정합니다.
4. **프로젝트 설정 → 일반 → 내 앱 → SHA 인증서 지문**에 현재 release APK의 아래 두 지문을 등록합니다. Google 로그인에는 **SHA-1이 필요**하며 SHA-256만 등록하면 준비가 끝난 것이 아닙니다.

| 인증서 | 공개 지문 |
|---|---|
| release SHA-1 | `61:27:E1:0B:16:3A:79:2C:53:97:5B:EA:3A:F8:03:60:9D:84:A7:80` |
| release SHA-256 | `2D:7F:72:4C:D2:0B:1F:F2:26:3E:B3:4E:8E:02:D5:FE:C9:DB:67:44:88:B1:0B:81:3D:D2:39:83:8B:97:58:30` |

두 값은 현재 Runmoni 서명 APK에서 확인한 **인증서 지문**이며 APK 파일의 SHA-256 체크섬과 다릅니다. 공개해도 서명 비밀키를 복구할 수 있는 값이 아닙니다. debug APK를 나중에 Google 로그인 테스트에 사용하면 그 debug 인증서도 별도 등록합니다. Play App Signing을 도입하면 사용자가 내려받는 APK의 Play 앱 서명 인증서도 확인·등록해야 합니다. 서명 키·비밀번호는 업로드하지 않습니다. [Google 클라이언트 인증](https://developers.google.com/android/guides/client-auth), [Firebase Google 로그인 SHA-1 요구 사항](https://firebase.google.com/docs/auth/android/google-signin).

5. `google-services.json`을 내려받습니다. 이번에는 **`.secrets/firebase/google-services.json`에 임시 보관**하고 SDK 추가 단계는 건너뜁니다. 실제 연동 작업을 승인한 뒤 사용할 정상 Android 모듈 위치는 `app/google-services.json`입니다. 지금 파일을 옮겨도 현재 Gradle·SDK가 Firebase를 사용하지 않습니다. [Android 설정 파일 위치](https://firebase.google.com/docs/android/setup).

### C. Google 로그인 제공자 준비

1. 콘솔의 **Authentication → 로그인 방법/Sign-in method**를 엽니다.
2. **Google** 제공자를 활성화합니다. 화면에서 지원 이메일을 요구하면 사용자가 관리하는 이메일을 선택하고 저장합니다.
3. 제공자 활성화 뒤 **최신 `google-services.json`을 다시 내려받아** B 단계의 임시 파일을 교체합니다. Google OAuth 클라이언트 정보가 반영된 파일이어야 합니다.
4. 전화번호/SMS 인증은 이번 무료 준비 범위에 포함하지 않습니다.

후속 앱 구현은 Credential Manager로 받은 Google ID 토큰을 Firebase Auth에 연결하는 절차입니다. 현재 `/v1/auth/pilot`의 초대 코드 bearer token은 Firebase ID 토큰이 아니므로 그대로 Firebase 인증에 사용할 수 없습니다. **제공자 활성화만으로 현재 APK에 Google 로그인 버튼이나 기존 계정 복구가 생기지 않습니다.** [Google 로그인 공식 절차](https://firebase.google.com/docs/auth/android/google-signin).

### D. 빈 Firestore DB 준비

1. **Firestore Database → 데이터베이스 만들기**를 선택합니다.
2. 선택 화면이 나오면 **Standard edition**, 무료 대상인 첫 DB **`(default)`**를 사용합니다.
3. 한국 사용자 중심의 새 프로젝트는 **`asia-northeast3`(Seoul)**을 선택하는 안을 권합니다. DB 위치는 생성 후 변경할 수 없으므로 사용자가 확인하고 생성합니다. 기존 프로젝트에서 위치가 이미 정해졌으면 그 상태를 먼저 확인합니다. [지역 목록·변경 불가 조건](https://firebase.google.com/docs/firestore/locations).
4. Security Rules 시작 모드는 **Production mode**를 선택합니다. 이번 준비에서는 Android·웹 클라이언트 접근을 모두 거부하는 기본 규칙을 유지합니다.
5. **Rules** 화면에서 아래와 같이 기본 거부 상태인지 확인합니다. 이 예시는 준비용이며 크루 권한 규칙 구현이 아닙니다.

```text
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

Production mode는 모바일·웹 클라이언트의 읽기/쓰기를 거부합니다. 관리자 IAM 권한과 서버 SDK 접근은 별도입니다. Google 로그인을 활성화해도 위 규칙은 자동으로 열리지 않습니다. Test mode는 누구나 읽고 덮어쓸 수 있으므로 이 빈 준비 DB에는 사용하지 않습니다. [Firestore 생성·기본 규칙](https://firebase.google.com/docs/firestore/quickstart).

## 4. 20명 위치 공유의 무료 한도 계산

아래는 **한 사용자당 현재 위치 문서 1개를 갱신**하고, 모든 사용자가 1시간 동안 지정한 주기로 게시·구독한다는 설계 계산입니다. 실제 Firebase 계측 결과가 아닙니다. 초기 조회·재연결·Security Rules의 추가 문서 읽기·채팅·기록 동기화는 제외했습니다.

| 적용 전 설계 예시 | 위치 쓰기/시간 | 위치 갱신 읽기/시간 | 하루 무료 한도 사용률 |
|---|---:|---:|---|
| 20명, 15초 게시, 각자 나머지 19명 구독 | `20 × 240 = 4,800` | `20 × 240 × 19 = 91,200` | 쓰기 **24%**, 읽기 **182.4%** |
| 20명, 60초 게시, 각자 나머지 19명 구독 | `20 × 60 = 1,200` | `20 × 60 × 19 = 22,800` | 쓰기 **6%**, 읽기 **45.6%** |
| 대표 1명만 15초 게시, 20명이 대표 1명 구독 | `1 × 240 = 240` | `1 × 240 × 20 = 4,800` | 쓰기 **1.2%**, 읽기 **9.6%** |

15초마다 **사용자 20명의 기존 문서를 갱신**하므로 저장 문서가 매시간 4,800개 쌓이는 구조는 아닙니다. 다만 upsert도 쓰기 요청 수에 포함됩니다. 각 구독자가 자기 자신까지 포함한 20명을 보면 갱신 읽기는 **96,000회/시간(192%)**이고 초기 20문서 × 20구독자 조회는 별도로 최대 **400회**입니다. 구독자는 문서 갱신마다 읽기가 계산되고 재연결·Rules 조회에 추가 읽기가 발생할 수 있습니다. [Firestore listener 과금](https://firebase.google.com/docs/firestore/pricing).

무료 파일럿에서 위의 **20명 전원·15초 전원 구독**을 그대로 Firestore로 옮기는 것은 부적합합니다. 60초안과 대표 위치안은 사용자가 동작 방식을 선택한 뒤 구현·측정할 대안입니다. 대표 위치안은 나머지 19명의 정확한 위치를 공유하지 않는 방식이며 개인 GPS 원본은 각자의 Room에 남길 수 있습니다.

현재 SQLite 공유 위치는 **15초 게시·45초 만료**입니다. 60초 게시로 바꾸면서 45초 만료를 그대로 두면 다음 갱신 전 위치가 만료됩니다. 만료 시간·표시 정확도·동의 해제 즉시 삭제·통신 실패 처리를 함께 다시 설계해야 합니다. Firestore `expiresAt` 필드를 쓰는 것만으로 삭제가 실행되지 않으며 Spark에서는 결제 필수인 자동 TTL 삭제를 사용할 수 없습니다. 현재의 45초 접근 만료와 DB 자동 삭제는 다른 기능입니다. [현재 위치 API](../backend/API.md), [Firestore TTL 한도](https://firebase.google.com/docs/firestore/quotas).

### 러닝 GPS 원본을 매초 즉시 클라우드에 쓰지 않는 이유

이번 무료 파일럿 설계에서는 **1초 GPS 포인트마다 Firestore 문서 1개를 즉시 쓰는 방식을 사용하지 않습니다.** GPS가 실제로 매초 1점 들어온다는 가정이면 1명은 **3,600쓰기/시간(일 한도의 18%)**, 20명은 **72,000쓰기/시간(360%)**입니다. 위치 공유·크루·채팅 쓰기는 이 계산에 포함되지 않았습니다. 현재 GPS 요청 간격은 1초이지만 OS·수신 상태·필터 때문에 실제 포인트 수는 달라집니다.

GPS 원본은 Room에 계속 저장하고, 클라우드 기록은 완료 요약과 검증된 배치/압축 업로드를 별도 설계합니다. 포인트 배열을 하나의 문서에 무제한 합치는 우회도 하지 않습니다. Firestore 문서는 **최대 1 MiB**이며 배열 갱신·인덱스·읽기 사용량을 검토해야 합니다. 원본 이관은 Storage의 Blaze 요구와 서버 저장 경로까지 포함해 선택해야 합니다. [Firestore 문서 크기·한도](https://firebase.google.com/docs/firestore/quotas).

## 5. 설정 파일과 비밀키를 구분하기

| 파일·값 | 성격 | 처리 |
|---|---|---|
| `google-services.json`, Firebase 프로젝트 ID, 앱 ID, OAuth client ID | 앱 설정·프로젝트 식별값. 설정 파일은 비밀키가 아님 | 사용자는 설정 파일을 로컬에 보관하고 후속 연동 때 전달 가능 |
| 위 SHA-1·SHA-256 인증서 지문 | 공개 인증서 식별값 | Firebase 콘솔에 등록 가능 |
| Firebase Admin 서비스계정 JSON의 `private_key` | 서버 권한을 가진 비밀키 | 이번 단계에서 생성할 필요 없음. APK·Git·채팅에 넣지 않음 |
| `.secrets/runmoni-release.jks`, 서명 비밀번호 | 앱 업데이트 서명 비밀정보 | 별도 안전한 백업. 공유·게시하지 않음 |
| Firebase ID 토큰·refresh token·기존 pilot bearer token | 계정 세션 정보 | 앱/서버 세션 저장소에서 관리하며 로그·문서에 넣지 않음 |

Firebase용 API 키는 일반적으로 인증 비밀값이 아니며 데이터 보호는 Security Rules·서버 IAM·App Check에 의존합니다. 다른 유료 API용 키를 같은 공개 키에 섞지 않습니다. 서버 연동이 결정되면 서비스계정 파일 대신 실행 환경의 자격 증명도 검토합니다. **`google-services.json`을 숨기는 것만으로 DB 접근 권한이 안전해지지는 않습니다.** [앱 설정 파일](https://firebase.google.com/docs/android/setup), [Firebase API 키 구분](https://firebase.google.com/docs/projects/api-keys), [Admin 자격 증명](https://firebase.google.com/docs/admin/setup).

### `.gitignore` 현재 상태와 추가 검토

현재 `.gitignore`는 `.secrets/`, `.env`, `*.jks`, `*.keystore`, `signing.properties`와 `google-services.json`, `*-firebase-adminsdk-*.json`, `service-account*.json`을 제외합니다. 앱 설정 파일 제외는 공개 저장소에 특정 프로젝트 설정을 넣지 않는 운영 선택이며, 설정 파일이 비밀키라는 뜻은 아닙니다. 위 임시 파일을 `.secrets/firebase/`에 두면 현재 규칙으로 제외됩니다. 다음 추가 규칙은 연동 전에 검토할 제안이며 이 문서 작업에서는 적용하지 않았습니다.

```gitignore
# camelCase 이름으로 따로 내려받은 서버 비밀키도 제외
**/serviceAccount*.json

# Firebase CLI 및 emulator 실행 산출물
.firebase/
firebase-debug.log*
firestore-debug.log*
ui-debug.log*
firebase-export-*/
```

파일 이름 규칙만으로 모든 키를 막을 수 없으므로 서버 키를 생성한다면 `.secrets/` 안에서만 관리합니다. 후속 구현의 `firebase.json`, `firestore.rules`, `firestore.indexes.json`은 검토 가능한 소스로 버전 관리합니다. `.firebaserc`는 프로젝트 ID 매핑이며 비밀키가 아니므로 팀 운영 방침에 따라 관리합니다. 이미 Git에 들어간 파일은 `.gitignore`를 추가해도 추적이 해제되지 않습니다.

## 6. 실제 연결 전 남은 작업과 결정

Firebase 전환을 선택하면 현재 14개 SQL 테이블 관계를 문서·하위 컬렉션·트랜잭션으로 다시 설계해야 합니다. 단순히 URL이나 키를 바꾸는 작업이 아닙니다.

1. **계정:** Firebase Auth SDK·Credential Manager 로그인·세션 복구를 구현하고 기존 SQLite 사용자와 Firebase UID의 이관 방식을 결정합니다. Google 로그인도 성인/본인 인증을 대신하지 않습니다.
2. **크루 권한:** 가입·탈퇴·크루장/DJ·2~20명 파티 정원·차단을 Firestore Rules와 트랜잭션으로 구현·테스트합니다. 인증된 모든 사용자를 읽기/쓰기에 허용하는 규칙으로 대체하지 않습니다.
3. **GPS·마일리지:** 신뢰 가능한 서버에서 원본 경로·거리·중복 업로드·하루 상한을 검증하고 원장을 갱신합니다. 클라이언트가 보상 값을 직접 쓰게 하면 임의 마일리지 적립이 가능합니다. 현재 SQLite의 보상 검증·트랜잭션은 Firestore에 자동 이식되지 않습니다.
4. **서버 운영:** Spark의 Firestore/Auth 준비만으로 HTTPS 검증 API가 생기지 않습니다. 기존 FastAPI를 Firebase 토큰 검증·Firestore adapter로 확장해 외부 운영하거나, 별도 승인 후 Blaze Functions 등을 선택해야 합니다.
5. **위치:** 본인 동의·허가된 크루/파티 범위·조회 수·만료·앱 종료·차단·탈퇴 처리를 Emulator와 실제 두 계정에서 확인합니다. 현재 전체 초대 파일럿 지도 권한을 일반 공개 권한으로 옮기지 않습니다.
6. **음성·DJ:** Firebase DB는 실시간 오디오 송출 서버가 아닙니다. 기존 **LiveKit + audio bridge** 서버·입장 권한·송출/재생·권한 철회 검증이 별도로 필요합니다. Cloud Storage는 오디오 스트리밍 대체 수단이 아닙니다.

사용자 확인이 필요한 선택은 **① Firebase를 최종 DB로 전환할지, ② 20명 개별 위치/60초 갱신/대표 위치 중 무엇을 시험할지, ③ 신뢰 가능한 외부 API 운영 또는 Blaze를 허용할지**입니다. 이 결정 전에는 Supabase 준비 파일을 삭제하거나 현재 Room·SQLite를 교체하지 않습니다.

긍정 관점: Spark 콘솔 준비만 먼저 끝내면 결제 계정 없이 패키지·인증서·로그인 제공자·DB 지역을 확정할 수 있습니다. Room 기반 오프라인 기록을 유지하면서 5명 또는 대표 위치 방식으로 읽기 사용량을 먼저 측정할 수 있습니다.

비판 관점: 20명의 15초 전원 위치 구독은 1시간만으로 무료 읽기 한도를 약 **1.82배** 사용합니다. Storage·Functions가 필요한 기능까지 무료라고 묶어 설명하거나, Firebase 설정 파일을 받았다는 이유로 계정·크루·위치가 동작한다고 주장할 수 없습니다.

## 확인 기록

| 날짜 | 가이드 버전 | 확인·변경 | 실행 경계 |
|---|---|---|---|
| 2026-10-13 | 0.1.0 | 공식 Spark/Firestore/Storage/Functions 문서 확인, Android 패키지 확인, 서명 APK의 공개 SHA-1/SHA-256 반영, 읽기/쓰기 계산, 현재 저장소와 이관 과제 구분 | **Firebase SDK 추가 0개, 원격 연동 0건, 프로젝트 생성 0건, Rules 배포 0건**. 콘솔 UI·실제 Firebase 로그인·DB 권한 테스트는 미수행 |

할루시네이션 확률: 정량 산정 불가. 공식 문서와 현재 코드로 확인한 사실, 주기 기반 추정 계산, 미적용 설계와 미실행 검증을 구분했습니다.
