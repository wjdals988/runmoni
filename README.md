# 런모니 / Runmoni

현재 공개 APK는 **실기기 검증 전 테스트용 `0.1.6-dev`(code7)**입니다. [대시보드에서 받기](https://coldbrewventi.vercel.app/projects/runmoni) · [GitHub 릴리스](https://github.com/wjdals988/runmoni/releases/tag/v0.1.6-dev). 사용자 요청으로 검토용 다운로드를 제공하며 설치·실행·로그인 성공 확인은 아직 없습니다. 상단 설치 버전명의 새 버전 알림과 확인 후 APK 다운로드를 연결했습니다. [검증 기록](docs/RELEASE_REVIEW_20261029.md) · [남은 작업](docs/DEVELOPMENT_PRIORITIES.md) · [이번 APK 설명](docs/RELEASE_NOTES_0.1.6-dev.md).

캐릭터 최신 상태(2026-10-27): 홈에 남아 있던 거절된 v0.4 조립 그래픽을 승인 v0.3 전신 원화로 교체했습니다. [전체 런모니 앱의 실제 화면·검증·남은 작업](docs/APP_REVIEW_20261027.md)을 확인하세요. 클라우드에서 전체 앱 빌드와 실행이 가능해졌습니다. 현재 첫 행 캐릭터의 기본 러닝복만 복구했으며, 58종 레이어와 여자 기본형 선택 연결은 남아 있습니다. 준비되지 않은 아이템은 구매·착용을 잠시 막고 보유권·포인트·기존 선택을 보존합니다. [수영복 검토 시안](docs/design/avatars/v0.5.2/README.md)은 계속 미승인입니다.

첫 미니미의 [준비·실패·재시도 실제 Android 검토](docs/AVATAR_LOADING_REVIEW_20261027.md)도 진행했습니다. 최신 기본8개/320dp·글씨200%에서4개 검사를 통과했으며, 원화8프레임과 데이터 계약을 보존했습니다. 전체 꾸미기 복구와는 별개입니다.

Android에서 러닝 기록을 남기고 크루·파티 방을 만드는 개발 프리뷰입니다. 현재 개발 소스는 `0.2.0-dev`(versionCode10), 공개 다운로드는 `0.1.6-dev`(code7), API 소스는 `0.1.5`입니다. 새 소스는 소비자 앱 중심 UI와 초대코드 없는 방 목록/닉네임 참여/독립 파티, 자동 일시정지·위치 복구 개선을 포함합니다. **공용 방 서버는 아직 없습니다.** [화면 개편 검토](docs/CONSUMER_REDESIGN_REVIEW_20261030.md). 기존 공개0.1.6의 서명·출처·검증 결과와 새 개발본 검증은 각각 구분합니다. 실제 휴대폰·Google 로그인·원격 백업·야외 GPS·음성은 미검증입니다.

## 지금 구현한 범위

| 영역 | 구현 | 확인해야 할 경계 |
|---|---|---|
| 러닝 | 위치 foreground service, 기기 내 Room 저장, 일시정지/재개, 중단 복구, 거리/시간/페이스/1km 구간, GPX 내보내기·삭제 | 야외 GPS 정확도·화면 끔·배터리·제조사 절전은 실기기 검증 필요 |
| 크루·파티 | 생성·탐색·가입, 2~20명 파티 정원, 참가 취소·주최자 취소, 크루장/DJ, 공지·채팅 | 로컬 서버가 필요. 채팅 3초 폴링·커뮤니티 15초 갱신 |
| 탐색 | NAVER Maps 연결 지점과 실제 경로 표시, 미니미 표시 | 지도 키 없으면 GPS 경로 캔버스 사용. 지도 배경 인증은 미검증 |
| 위치 공유 | 초대 성인 파일럿에 정밀 위치를 명시 동의 후 공유, 15초 게시·45초 만료, 종료·재시작 시 공유 해제 | 일반 대중에게 공개하는 지도와 미성년자 서비스는 후속 단계 |
| 음성·DJ 음악 | LiveKit 음성, 음악 별도 트랙·별도 음량, 자체 파일 재생, 6개 외부 음악 앱 캡처 요청, Opus bridge | 실제 서버·2대 기기 송출은 미검증. 외부 앱은 캡처를 거부할 수 있음 |
| 나·꾸미기 | 일/월/년 기록, 9개 옷장 탭·13개 저장 슬롯·58개 ID 보존, 승인 전신 기본형 표시 | 비기본 의상 레이어 준비 전 구매·착용 중단. 보유권·기존 선택·포인트 유지. 기기/서버 지갑은 별개 |
| Google 계정·요약 백업 | Firebase Auth/Firestore SDK, 별도 계정 세션, 선택한 기록 1건의 요약 백업, 최근 50건 서버 조회·요약 삭제 | 이전 작업 PC의 설정 준비 기록과 현재 공개 빌드를 구분. 공개 APK는 Firebase 설정 없음. 새 서명 지문 연결·실제 로그인/저장 미검증. GPS 원본·마일리지 제외 |

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

## 로컬 서버와 연결

보안 제약(2026-10-24): `cloudflared` 및 이를 대체하는 외부 공개 터널을 사용하지 않습니다. 로컬·USB 연결을 기본으로 하며, 테스트를 위한 서버 외부 공개나 방화벽 예외를 추가하지 않습니다. 작업 지침은 `AGENTS.md`에 기록합니다.

```powershell
python -m venv backend/.venv
backend/.venv/Scripts/python.exe -m pip install -r backend/requirements.lock.txt
# 채팅에 비밀 키를 붙여 넣지 말고, backend/.env에 로컬 설정
Copy-Item backend/.env.example backend/.env
# RUNMONI_PILOT_INVITE를 원하는 테스트 코드로 설정 후
.\backend\run.ps1
```

앱 설정에서 서버 주소와 초대코드·새 닉네임으로 가입합니다. 초대코드는 접근을 제한하는 파일럿 수단이고 본인 인증이나 성인 인증을 제공하지 않습니다. 현재 테스트는 성인 지인용입니다. 새 계정 생성 방식이라 같은 닉네임으로 재로그인할 수 없으며, 로그아웃 후 기존 계정을 복구하는 로그인은 후속 작업입니다.

- USB: `adb reverse tcp:8787 tcp:8787`, 앱 주소 `http://127.0.0.1:8787`
- 에뮬레이터: 위 reverse 또는 앱 주소 `http://10.0.2.2:8787`
- HTTP는 위 루프백/에뮬레이터 주소에만 허용. 외부 서비스는 HTTPS 필요

서버 없이 러닝 기록·기기 미니미·자체 파일 재생이 가능합니다. 공용 DB·로그인은 Firebase 무료 Spark로 결정했습니다. 프로젝트 `runmoni-3c08e`의 Android 설정 파일은 작업 PC에 준비했고 식별자·OAuth·릴리스 인증서 정보의 일치를 확인했습니다. 콘솔 설정·Rules 게시 완료는 사용자 보고이며 실제 Google 로그인·원격 CRUD는 미검증입니다. 지도·LiveKit 서버는 미배포입니다. 개인 원본은 Room, 커뮤니티는 SQLite API에 남아 있고 Firebase 첫 단계는 본인 요약 백업입니다. 기존 Supabase 파일은 과거 검토 이력으로 보관하며 현재 연결하지 않습니다.

## 마일리지

거리 10m당 1P, 즉 1km당 100P입니다. 아이템 변형별 가격은 0/500/1000/2000P입니다. 서버는 GPS를 다시 계산하고 기본 하루 10000P 상한을 적용합니다. 위치 정확도 30m 초과, 60초 초과 공백, 8m/s 초과 이동, 다른 segment 연결은 거리에서 제외합니다. 서버 검사는 부정 이용을 완전히 막는 인증 수단이 아닙니다.

기기 지갑은 로컬 러닝 ID별 1회 지급하며 서버 계정 지갑으로 자동 이관하지 않습니다. 서버 동기화 기록은 재시도에도 1회 지급합니다. 기록 삭제는 이미 지급된 마일리지를 취소하지 않습니다.

## 검증·계획

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

DB는 이미2종을 사용합니다. 휴대폰의 `runmoni-runs.db`(Room2개 테이블)는 개인 GPS·기록을 저장하고, FastAPI 서버의 SQLite(14개 테이블)는 계정·크루·파티·채팅·미니미 구매·위치를 저장합니다. 위치 공유는15초마다 사용자당 현재1행을 갱신하며45초 후 만료됩니다. GPS를1초당1점 받는다고 가정하면1시간3600점이지만 실제수신량 보장은 아닙니다.

GitHub 소스/APK 공개와 대시보드 등록은 클라우드 운영을 시작하는 작업이 아닙니다. 현재 상시 외부 HTTPS API·공유 DB·LiveKit 서버는 배포하지 않았습니다. APK만 설치한 친구들이 곧바로 서로의 크루/위치를 공유할 수는 없습니다.

기기 Room은 유지하고 공용 계정·DB의 목표는 Firebase Auth/Firestore입니다. 첫 단계는 독립 Google 로그인과 본인 기록 요약의 수동 백업입니다. Firebase 로그인은 기존 파일럿 세션이나 위치 공유 권한을 열지 않습니다. 요약은 자가 기록이며 보상 근거로 사용하지 않습니다. 크루·파티·차단·정원·DJ·보상을 이전하려면 현재 API의 43개 경로·87개 SQL 실행 지점에서 저장소를 분리하고 Firestore 트랜잭션과 Firebase 토큰 검증을 연결해야 합니다. 신뢰 가능한 HTTPS API와 음성/음악의 LiveKit·audio bridge 운영은 별도 작업입니다.

## 릴리스 서명

공식 배포는 GitHub Actions의 동일한 새 보관 키를 사용합니다. 현재 공개 인증서 정책은 [config/android-release-signing.json](config/android-release-signing.json)이며 환경마다 키를 만들지 않습니다. 아래 PowerShell 경로는 로컬 보관 키용입니다. 기존 Windows의 이전 키가 자동으로 새 배포 키와 같아지는 것은 아니므로 배포 절차는 [클라우드 릴리스](docs/CLOUD_ANDROID_RELEASE.md)를 따릅니다.

`scripts/build-release.ps1`은 `.secrets/signing.properties`가 있을 때만 서명 APK를 빌드합니다. 서명 키·비밀번호는 `.secrets/`에 보관하고 Git에서 제외합니다. 소유자는 이 폴더를 별도로 안전하게 백업해야 하며, 향후 업데이트는 같은 키를 사용해야 합니다. 첫0.1.0-dev 디버그 APK와 공개 서명 APK의 인증서는 다르므로 테스트 설치를 교체할 때는 기존 테스트 앱을 지우고 설치해야 합니다.
