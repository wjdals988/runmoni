# 런모니 / Runmoni

현재 공개 APK는 **실기기 검증 전 테스트용 `0.3.0-dev`(code12)**입니다. [대시보드에서 받기](https://coldbrewventi.vercel.app/projects/runmoni) · [GitHub 릴리스](https://github.com/wjdals988/runmoni/releases/tag/v0.3.0-dev). 크루 아래 소속 파티·보존 삭제, 가로 카테고리 옷장·남녀 기본형과 전 아이템0P 선택 저장을 추가했습니다. 미준비 의상은 선택만 보관하고 승인 기본형을 표시합니다.0.1.6/0.2.0/0.2.1과 같은 보관 키입니다. [실제 게시 검증](docs/RELEASE_REVIEW_20261103.md) · [UI/원시 검토](docs/CREW_CLOSET_REVIEW_20261102.md).

**이어서 개발할 때:** [현재 상태와 다음 작업](docs/DEVELOPMENT_CONTINUATION.md) → [기능·UI/UX 우선순위와 완료 조건](docs/DEVELOPMENT_PRIORITIES.md). 소유자는 기존 Firebase 설정1~5번 완료를 보고했습니다. 이번0.3의 새 계층/삭제 규칙 게시와 실제 휴대폰·Google 로그인·운영 쓰기는 별도 확인이 필요합니다. 여자 기본형 선택은 이번 소스에 구현했으며 다음 원화 작업은 의복 레이어입니다.

**이번0.3 구현/검증:** 크루→파티 계층과 뒤로·삭제, 가로 카테고리 탭·정렬·큰 글씨 배치, 남녀 기본형 전환/저장·0P 선택을 실제 Android에서 확인했습니다. 운영 삭제에는 새 Firebase 규칙 게시가 필요하고 실제 휴대폰·Google/두 기기 검증은 별도입니다. [최종 사진/검증 범위](docs/app-review/2026-11-02-crew-ux/README.md).

캐릭터는 승인 v0.3 전신 원본의 남녀 두 행×8프레임을 공유 표시 경로에서 사용합니다. 거절된 v0.4 조립 그래픽을 재활성화하지 않습니다.58종 의복 레이어는 아직 준비 중이며 무료 선택을 저장해도 미준비 의상은 기본형을 표시합니다. [수영복 검토 시안](docs/design/avatars/v0.5.2/README.md)은 계속 미승인입니다. 이전 복구/실행 기록은 [2026-10-27 검토](docs/APP_REVIEW_20261027.md)에 보존합니다.

첫 미니미의 [준비·실패·재시도 실제 Android 검토](docs/AVATAR_LOADING_REVIEW_20261027.md)도 진행했습니다. 최신 기본8개/320dp·글씨200%에서4개 검사를 통과했으며, 원화8프레임과 데이터 계약을 보존했습니다. 전체 꾸미기 복구와는 별개입니다.

Android에서 러닝 기록을 남기고 크루 안에서 파티를 만드는 개발 프리뷰입니다. 개발 소스와 공개 다운로드는 `0.3.0-dev`(code12), 선택 가능한 HTTP 파일럿 API 소스는 `0.1.6`입니다. 기본 크루·파티는 기존 Firebase Spark Authentication/Firestore를 사용하며 별도 API 서버 주소나 유료 호스팅이 필요하지 않습니다. 기존 운영 공개 목록 읽기는 CI에서 통과했고, 제공자·규칙·색인 설정 완료는 소유자 보고입니다. 새 규칙 게시·실제 운영 생성/삭제·두 휴대폰 채팅·Google 로그인/백업·야외 GPS·음성은 미검증입니다. 기존 APK 출처·서명과 각 실행의 검증 결과는 구분해 보존합니다.

## 지금 구현한 범위

| 영역 | 구현 | 확인해야 할 경계 |
|---|---|---|
| 러닝 | 위치 foreground service, 기기 내 Room 저장, 일시정지/재개, 중단 복구, 거리/시간/페이스/1km 구간, GPX 내보내기·삭제 | 야외 GPS 정확도·화면 끔·배터리·제조사 절전은 실기기 검증 필요 |
| 크루·파티 | Firebase 익명 닉네임, 크루 목록/가입과 소속 파티 생성/참가/보존삭제, 정원·역할/공지·실시간 채팅 | 새 규칙 게시 필요. 목록 수동/작업 후 갱신, 열린 채팅만 구독. 운영 쓰기·두 휴대폰 확인 필요. 익명 계정 복구/방장 이양/신고는 후속 |
| 탐색 | NAVER Maps 연결 지점과 실제 경로 표시, 미니미 표시 | 지도 키 없으면 GPS 경로 캔버스 사용. 지도 배경 인증은 미검증 |
| 위치 공유 | 기존 HTTP 성인 파일럿의 명시 동의·15초 게시/45초 만료 코드 보존 | 기본 Firebase 방에서는 정확 위치를 전송하지 않음. 대중 공개 지도는 후속 |
| 음성·DJ 음악 | 기존 LiveKit·별도 음악 트랙/음량·외부 앱 캡처·Opus bridge 코드 보존 | Firebase 방은 음성/음악 방송 미지원. 실제 서버·2대 기기 송출은 미검증 |
| 나·꾸미기 | 일/월/년 기록, 남녀 기본형 전환/저장, 가로 카테고리와0P 획득·선택,9개 탭·13슬롯·58개 ID 보존 | 미준비 의상은 선택만 보관하고 승인 기본형 표시. 보유권·기존 선택·포인트 유지. 기기/서버 지갑은 별개 |
| Google 계정·요약 백업 | 설정 포함 APK, 별도 Google 세션, 선택한 기록1건의 요약 백업/조회·삭제, 연결·휴대폰 점검 | CI의 설정 일치와 실제 계정 선택/백업은 별개. 익명 방 계정과 자동 병합하지 않음. GPS 원본·마일리지 제외 |

네트워크나 음악 실패로 GPS 기록을 멈추지 않는 구조입니다. OS 강제 종료 뒤에는 자동 연속 기록을 주장하지 않고, 저장된 지점부터 사용자 복구를 제공합니다. 빈 화면에 가짜 러너·가짜 GPS 기록을 넣지 않습니다.

## 앱 정보와 업데이트

나 탭 하단의 버전 행 또는 설정의 ‘앱 정보 · 버전 · 지원’에서 설치 버전·업데이트 내역·대시보드·GitHub·148개 라이브러리/SDK 고지·문제 신고를 볼 수 있습니다. 새 공개 APK가 확인되면 레드닷과 안내를 표시하고, 사용자가 확인한 뒤 다운로드 링크를 엽니다. APK 자동 다운로드/설치는 하지 않습니다. 32KiB 이하 공개 정보만 요청하고 기본 6시간 캐시를 사용합니다.

마일리지는 미니미 꾸미기 전용 포인트입니다. 기본 1km=100P이며 기기/파일럿 서버 지갑은 별개입니다. 서버 지급에는 GPS 검증·일일 한도가 적용됩니다. 프로필 포인트 옆 안내 아이콘에서 사용 안내를 확인할 수 있습니다.

[디자인 기준](DESIGN.md) · [화면 감사](docs/DESIGN_AUDIT.md) · [앱 정보·코스 설계](docs/APP_INFORMATION_AND_COURSES.md). 코스 탐색·공개·파티 브리핑은 이번에는 설계 확정이며 실제 구현/배포는 후속입니다.

0.1.4 서명 APK의 실제 화면8장·아이콘1개와 이전1.3 화면16장은 [화면 확인 기록](docs/SCREENSHOT_REVIEW_20261025.md)에 정리했습니다. 배치·상태38/38과 JVM30/30을 통과했지만 본인 Google/Firebase 로그인·원격 백업 성공은 아직0회입니다. 새 Google 버튼은 기기 계정 추가 화면까지 열었으나 사용자가 Google시스템 오류를 보고했습니다. 이후 사용자 지시로 AVD를 제외하며 PC 브라우저 지연의 원인을 해결됐다고 표현하지 않습니다. [네트워크 진단 경계](docs/UPDATE_NETWORK_DIAGNOSIS.md).

0.1.5의 [미니미 GIF·검사 근거](docs/design/avatars/v0.4.0/README.md)는 거절된 v0.4 렌더러를 호스트 Java2D로 실행한 이력입니다. 실제 Android 캡처로 표시하지 않습니다. 당시 APK의 PNG63개·manifest·서명 확인 결과는 [이전 검증 기록](docs/VALIDATION.md)에 보존하며, 이번0.1.6 공개 프리뷰의 원화 승인이나 실행 결과로 합산하지 않습니다.

## 빌드

JDK 17 이상(현재 JDK 21), Android SDK 36, Gradle 8.13이 필요합니다. `local.properties`에 `sdk.dir`을 넣습니다. 지도는 선택 사항이며 `NAVER_MAP_CLIENT_ID`를 같은 파일 또는 환경변수로 넣습니다. 비밀 서버 키는 APK에 넣지 않습니다.

```powershell
# 기본: 다운로드 없이, 작업자 1개
.\scripts\build.ps1
# 최초 의존성 설치 또는 캐시 부족 때만
.\scripts\build.ps1 -Online -IncludeDeviceTests
```

스크립트는 이미 설치된 Gradle을 찾으며 오프라인 실행 중 배포판을 자동 다운로드하지 않습니다. 처음부터 만드는 PC에서는 Gradle 설치와 최초 온라인 빌드가 필요합니다. 앱 APK 기본 위치는 `app/build/outputs/apk/debug/app-debug.apk`입니다.

## 선택 가능한 로컬 HTTP 파일럿

기본 Firebase 방은 [공유 방 안내](docs/FIREBASE_COMMUNITY.md)를 따릅니다. 아래는 기존 HTTP API를 개발/검증할 때만 사용하는 별도 경로입니다. Firebase 인증 토큰을 HTTP 파일럿에 보내지 않습니다.

보안 제약(2026-10-24): `cloudflared` 및 이를 대체하는 외부 공개 터널을 사용하지 않습니다. 로컬·USB 연결을 기본으로 하며, 테스트를 위한 서버 외부 공개나 방화벽 예외를 추가하지 않습니다. 작업 지침은 `AGENTS.md`에 기록합니다.

```powershell
python -m venv backend/.venv
backend/.venv/Scripts/python.exe -m pip install -r backend/requirements.lock.txt
# 채팅에 비밀 키를 붙여 넣지 말고, backend/.env에 로컬 설정
Copy-Item backend/.env.example backend/.env
# RUNMONI_PILOT_INVITE를 원하는 테스트 코드로 설정 후
.\backend\run.ps1
```

고급 설정에서 HTTP 서버 주소를 명시적으로 선택한 경우의 파일럿 세션은 Firebase와 별개입니다. 기존 초대코드 방식은 제한된 파일럿 경로이며 공개 방의 기본 진입은 닉네임으로 목록/생성/입장입니다. 초대코드는 본인 인증이나 성인 인증을 제공하지 않습니다. 계정 복구는 후속 작업입니다.

- USB: `adb reverse tcp:8787 tcp:8787`, 앱 주소 `http://127.0.0.1:8787`
- 에뮬레이터: 위 reverse 또는 앱 주소 `http://10.0.2.2:8787`
- HTTP는 위 루프백/에뮬레이터 주소에만 허용. 외부 서비스는 HTTPS 필요

인터넷 없이 개인 러닝 기록·기기 미니미를 사용할 수 있습니다. Firebase 연결은 공개 방과 별도 Google 요약 백업에 사용합니다. 개인 GPS 원본·지갑/보유 내역은 기기에 유지하며 Firebase 방에서는 HTTP 자동 동기화/정확 위치 게시를 차단합니다. 지도 SDK의 실제 인증과 LiveKit 서버/두 기기 통화는 미검증입니다. 기존 Supabase 파일은 과거 검토 이력입니다.

## 마일리지

거리 10m당 1P, 즉 1km당 100P입니다. 아이템 변형별 가격은 0/500/1000/2000P입니다. 서버는 GPS를 다시 계산하고 기본 하루 10000P 상한을 적용합니다. 위치 정확도 30m 초과, 60초 초과 공백, 8m/s 초과 이동, 다른 segment 연결은 거리에서 제외합니다. 서버 검사는 부정 이용을 완전히 막는 인증 수단이 아닙니다.

기기 지갑은 로컬 러닝 ID별 1회 지급하며 서버 계정 지갑으로 자동 이관하지 않습니다. 서버 동기화 기록은 재시도에도 1회 지급합니다. 기록 삭제는 이미 지급된 마일리지를 취소하지 않습니다.

## 검증·계획

- [개발 이어가기](docs/DEVELOPMENT_CONTINUATION.md): 최신 공개 상태·확인 출처·바로 착수할 작업
- [기능·UI/UX 우선순위](docs/DEVELOPMENT_PRIORITIES.md): 작업별 완료 조건과 후속 순서
- [Firebase 공유 방·휴대폰 확인](docs/FIREBASE_COMMUNITY.md): 현재 기본 연결과 실기기 순서
- [검증 기록](docs/VALIDATION.md): 실행한 테스트와 미검증 항목
- [Firebase 실제 계정·백업 검증 순서](docs/FIREBASE_DEVICE_CHECK.md): 본인 Google 로그인 이후 수동 백업·조회·삭제·계정 전환
- [구현 계획](docs/IMPLEMENTATION.md): 탭 책임·후속 순서·출시 조건
- [변경 이력](CHANGELOG.md)
- [로컬 API 계약](backend/API.md)
- [음성·음악 구성](docs/AUDIO.md)
- [Supabase 이관 준비](supabase/README.md)
- [Firebase 무료 프로젝트 준비 가이드](docs/FIREBASE_SETUP.md): Android 설정·Google 제공자·소유자 전용 Rules 준비.
- [Firebase 첫 단계 구현과 검증 경계](docs/FIREBASE_IMPLEMENTATION.md), [Rules와 실제 Emulator 검증](firebase/README.md)
- [서명 APK 확인 기록](docs/RELEASE.md), [0.1.1-dev 릴리스 설명](docs/RELEASE_NOTES_0.1.1-dev.md)

현재 개발 APK는 소규모 기능 검증용입니다. 대중 서비스 완료 버전으로 취급하지 않습니다.

## DB와 인터넷 운영 상태

개인 GPS·기록은 기기의 Room을 사용합니다. 기본 공용 방의 닉네임 프로필·방·멤버·메시지는 Firebase Authentication/Firestore를 사용하고, Google 기록 요약은 별도 계정의 본인 전용 경로에 저장합니다. 원본 GPS·포인트·보유권을 공용 방으로 이전하지 않습니다. HTTP 파일럿의 SQLite는 선택 가능한 기존 개발 경로로 보존합니다.

0.2.1 APK에는 실제 프로젝트의 Android 설정을 포함했습니다. 소유자가2026-11-02(한국 시간) 제공자·지문·Secret·규칙·색인 설정1~5번 완료를 보고했고, CI에서 실제 보관 인증서·현재 Android OAuth·운영 공개 목록 읽기를 확인했습니다. 실제 익명 방 쓰기·두 휴대폰 채팅·Google 로그인/요약 백업은 [휴대폰 확인](docs/FIREBASE_COMMUNITY.md#휴대폰에서-확인하는-순서)이 남아 있습니다.

Firebase 방은 음성/음악 방송·실시간 정확 위치 공유를 지원하지 않습니다. 익명 방 계정과 Google 요약 계정을 자동 병합하지 않으며 연결 해제·재설치 후 방장 권한 복구도 후속 작업입니다. LiveKit·audio bridge 운영이나 별도 유료 서버는 이번 연결에 생성하지 않았습니다.

## 릴리스 서명

공식 배포는 GitHub Actions의 동일한 새 보관 키를 사용합니다. 현재 공개 인증서 정책은 [config/android-release-signing.json](config/android-release-signing.json)이며 환경마다 키를 만들지 않습니다. 아래 PowerShell 경로는 로컬 보관 키용입니다. 기존 Windows의 이전 키가 자동으로 새 배포 키와 같아지는 것은 아니므로 배포 절차는 [클라우드 릴리스](docs/CLOUD_ANDROID_RELEASE.md)를 따릅니다.

`scripts/build-release.ps1`은 `.secrets/signing.properties`가 있을 때만 서명 APK를 빌드합니다. 서명 키·비밀번호는 `.secrets/`에 보관하고 Git에서 제외합니다. 소유자는 이 폴더를 별도로 안전하게 백업해야 하며, 향후 업데이트는 같은 키를 사용해야 합니다. 첫0.1.0-dev 디버그 APK와 공개 서명 APK의 인증서는 다르므로 테스트 설치를 교체할 때는 기존 테스트 앱을 지우고 설치해야 합니다.
