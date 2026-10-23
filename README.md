# 런모니 / Runmoni

Android에서 러닝 기록을 남기고, 초대된 친구들과 크루·파티를 운영하는 개발 프리뷰입니다. Android 앱 `0.1.1-dev`(versionCode2)와 로컬 API `0.1.4`는 별도로 버전을 관리합니다. GitHub 릴리스 APK는 별도 개발 서명을 적용한 release build이며, 실제 휴대폰 검증은 아직 없습니다.

## 지금 구현한 범위

| 영역 | 구현 | 확인해야 할 경계 |
|---|---|---|
| 러닝 | 위치 foreground service, 기기 내 Room 저장, 일시정지/재개, 중단 복구, 거리/시간/페이스/1km 구간, GPX 내보내기·삭제 | 야외 GPS 정확도·화면 끔·배터리·제조사 절전은 실기기 검증 필요 |
| 크루·파티 | 생성·탐색·가입, 2~20명 파티 정원, 참가 취소·주최자 취소, 크루장/DJ, 공지·채팅 | 로컬 서버가 필요. 채팅 3초 폴링·커뮤니티 15초 갱신 |
| 탐색 | NAVER Maps 연결 지점과 실제 경로 표시, 미니미 표시 | 지도 키 없으면 GPS 경로 캔버스 사용. 지도 배경 인증은 미검증 |
| 위치 공유 | 초대 성인 파일럿에 정밀 위치를 명시 동의 후 공유, 15초 게시·45초 만료, 종료·재시작 시 공유 해제 | 일반 대중에게 공개하는 지도와 미성년자 서비스는 후속 단계 |
| 음성·DJ 음악 | LiveKit 음성, 음악 별도 트랙·별도 음량, 자체 파일 재생, 6개 외부 음악 앱 캡처 요청, Opus bridge | 실제 서버·2대 기기 송출은 미검증. 외부 앱은 캡처를 거부할 수 있음 |
| 나·꾸미기 | 일/월/년 기록, 5슬롯×4종=20종 조합 아이템, 기본 아이템 5종, 구매·장착 | 유료 결제 없음. 기기 지갑과 서버 지갑은 별개 |

네트워크나 음악 실패로 GPS 기록을 멈추지 않는 구조입니다. OS 강제 종료 뒤에는 자동 연속 기록을 주장하지 않고, 저장된 지점부터 사용자 복구를 제공합니다. 빈 화면에 가짜 러너·가짜 GPS 기록을 넣지 않습니다.

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

서버 없이 러닝 기록·기기 미니미·자체 파일 재생이 가능합니다. 클라우드 NAVER Maps·Supabase·LiveKit 프로젝트는 아직 생성하지 않았습니다. Supabase 파일은 후속 이관용 설계이고 현재 앱의 실제 저장소는 Room+SQLite API입니다.

## 마일리지

거리 10m당 1P, 즉 1km당 100P입니다. 아이템 변형별 가격은 0/500/1000/2000P입니다. 서버는 GPS를 다시 계산하고 기본 하루 10000P 상한을 적용합니다. 위치 정확도 30m 초과, 60초 초과 공백, 8m/s 초과 이동, 다른 segment 연결은 거리에서 제외합니다. 서버 검사는 부정 이용을 완전히 막는 인증 수단이 아닙니다.

기기 지갑은 로컬 러닝 ID별 1회 지급하며 서버 계정 지갑으로 자동 이관하지 않습니다. 서버 동기화 기록은 재시도에도 1회 지급합니다. 기록 삭제는 이미 지급된 마일리지를 취소하지 않습니다.

## 검증·계획

- [검증 기록](docs/VALIDATION.md): 실행한 테스트와 미검증 항목
- [구현 계획](docs/IMPLEMENTATION.md): 탭 책임·후속 순서·출시 조건
- [2026-10-23 코드 감사](docs/AUDIT_2026-10-23.md): 결함 근거·이번 수정·출시 차단 항목
- [개발 로드맵](docs/ROADMAP.md): 다음 작업 순서와 단계별 완료 조건
- [변경 이력](CHANGELOG.md)
- [로컬 API 계약](backend/API.md)
- [음성·음악 구성](docs/AUDIO.md)
- [Supabase 이관 준비](supabase/README.md)
- [Firebase 무료 프로젝트 준비 가이드](docs/FIREBASE_SETUP.md): 콘솔 설정 절차이며 SDK·원격 연동은 아직 없습니다.
- [서명 APK 확인 기록](docs/RELEASE.md), [0.1.1-dev 릴리스 설명](docs/RELEASE_NOTES_0.1.1-dev.md)

현재 개발 APK는 소규모 기능 검증용입니다. 대중 서비스 완료 버전으로 취급하지 않습니다.

## DB와 인터넷 운영 상태

DB는 이미2종을 사용합니다. 휴대폰의 `runmoni-runs.db`(Room2개 테이블)는 개인 GPS·기록을 저장하고, FastAPI 서버의 SQLite(14개 테이블)는 계정·크루·파티·채팅·미니미 구매·위치를 저장합니다. 위치 공유는15초마다 사용자당 현재1행을 갱신하며45초 후 만료됩니다. GPS를1초당1점 받는다고 가정하면1시간3600점이지만 실제수신량 보장은 아닙니다.

GitHub 소스/APK 공개와 대시보드 등록은 클라우드 운영을 시작하는 작업이 아닙니다. 현재 상시 외부 HTTPS API·공유 DB·LiveKit 서버는 배포하지 않았습니다. APK만 설치한 친구들이 곧바로 서로의 크루/위치를 공유할 수는 없습니다.

기기 Room은 유지하고 서버는 Supabase Auth/PostgreSQL과 연결하는 경로를 우선 준비합니다. 기존 SQL을 실행하는 것 외에도 인증 교체·PostgreSQL adapter·기존 계정 이관·보상/정원 트랜잭션·RLS 실행 검증·HTTPS 배포가 필요합니다. Firebase Firestore도 대안이지만 두 DB를 동시에 주 저장소로 운영하지 않습니다. 음성/음악은 별도 LiveKit과 audio bridge가 맡습니다.

## 릴리스 서명

`scripts/build-release.ps1`은 `.secrets/signing.properties`가 있을 때만 서명 APK를 빌드합니다. 서명 키·비밀번호는 `.secrets/`에 보관하고 Git에서 제외합니다. 소유자는 이 폴더를 별도로 안전하게 백업해야 하며, 향후 업데이트는 같은 키를 사용해야 합니다. 첫0.1.0-dev 디버그 APK와 공개 서명 APK의 인증서는 다르므로 테스트 설치를 교체할 때는 기존 테스트 앱을 지우고 설치해야 합니다.
