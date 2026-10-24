# 변경 이력

## 2026-10-24 — 순차 작업으로 GitHub·대시보드 게시 완료

- 사용자 지시에 따라 로컬 작업을 한 번에 1개씩 진행. 에뮬레이터·대량 패키지 설치 없이 최신 개발 코드 `d98001c`를 `wjdals988/runmoni` main에 일반 푸시하고 로컬/원격 커밋 일치를 확인.
- 대시보드 최신 원격 `3a2b35e`를 병합해 여긴얼마 2.5.4, 다른 프로젝트 8개 객체와 전체 배열 순서를 보존. `b5a9b76`을 `wjdals988/mydashboard` main에 일반 푸시.
- 대시보드의 런모니 APK 다운로드·릴리스 링크·64자리 SHA-256·200,693,280bytes 정보를 반영. 총 프로젝트 9개, 다운로드 제공 6개. APK 파일 교체·태그 이동·200MB 재다운로드는 하지 않음.
- 기존 필수 보안 검사를 통과하도록 Next.js·eslint-config-next를 16.3.8에서 16.4.3으로 맞추고 전이 Zod 잠금 버전을 4.3.6에서 4.6.7로 갱신. PC에서는 연결 1개·요청당 15초 제한의 잠금 정보 갱신만 실행(약 5초·1초). 다른 패키지 버전은 유지.
- 로컬 카탈로그/업데이터 23/23 및 새 잠금 정보의 npm audit 취약점 0개 확인. [GitHub CI 40064465734](https://github.com/wjdals988/mydashboard/actions/runs/40064465734)에서 새 의존성 설치·audit·test·lint·build 전체 성공. CI 우회·권한 완화 없음.
- 동일 `b5a9b76`의 Vercel 커밋 상태 success/Deployment has completed 확인. [실제 런모니 페이지](https://coldbrewventi.vercel.app/projects/runmoni) HTTP 200, 공개 APK/릴리스 앵커·버전·바이트 크기·체크섬 일치. APK HEAD HTTP 200·Content-Length 일치, APK 본문 다운로드 0bytes.
- 페이지 검증은 연결 1개·본문 상한 1MiB·텍스트 읽기 128KiB/s 제한으로 순차 실행. 실제 페이지 HTML 98,791bytes(디코딩 후) 확인. Vercel 관리 API가 사용 불가여서 GitHub 배포 상태와 공개 페이지를 함께 대조했으며 런타임 오류 로그는 조회하지 않음.
- 공개 APK는 `0.1.1-dev`, 최신 개발 소스는 Firebase 첫 단계가 있는 `0.1.2-dev`. 실제 Google 로그인·원격 백업·Rules 배포·실기기 검증 0건 경계 유지. 순차 작업 지침은 AGENTS.md에 기록.

## 2026-10-24 — GitHub APK 공개·Firebase 개발본 검증

- 사용자 GitHub 인증 완료 후 기존 소스 이력 보존 커밋 `b72eec7`을 `wjdals988/runmoni` main에 게시.
- `v0.1.1-dev` 서명 APK를 개발 프리릴리스로 공개. 200,693,280bytes, versionCode 2. APK·JSON 메타데이터·SHA256SUMS 3개 파일의 GitHub API 크기·해시를 로컬 파일과 대조.
- 체크섬 문서에 옮길 때 생긴 1글자 오타를 자동 계산한 64자리 SHA-256으로 정정. 공개 APK 자체와 태그는 변경하지 않고 설명·보조 파일 2개만 수정.
- 공개 APK는 Firebase 미포함. 개발 소스 `0.1.2-dev`의 설정 없는 debug/test APK 온라인·오프라인 빌드 통과, JVM 15/15, UI 17/17(78.299초), lint 오류 0·경고 47개 확인. 실제 Google 로그인·원격 CRUD·Rules 배포·실기기 러닝은 0건.
- Firebase 콘솔 가이드를 7단계로 갱신. Spark·패키지·서명 지문 2개·Google 제공자·Firestore Seoul·소유자 전용 Rules·지정 설정 경로를 명시. `.secrets/`와 서비스계정 키는 게시 대상에서 제외.
- 과거 82개 파일럿 테스트를 이번 Firebase 원격 검증으로 합산하지 않음. 독립 읽기 리뷰에서 계정 경계·Rules 필드 일치·실패 격리의 P1/P2 결함은 발견되지 않았으며 원격 동작 보증은 아님.
- 대시보드 APK 등록 데이터는 로컬 준비, 테스트 23개·lint·25페이지 빌드 통과. 기존 의존성 2개 패키지의 보안 검사 실패로 최소 갱신을 시작했으나 PC 인터넷 지연 보고에 따라 다운로드·게시를 중단. package/lockfile 변경 없이 보존, 대시보드 새 커밋·푸시·배포는 아직 없음.

### PC 인터넷 지연 관찰과 작업 축소

- 사용자 보고: 작업 중 이 PC만 인터넷이 느려지고, 같은 Wi-Fi의 휴대폰은 영향이 없음. 작업의 패키지 다운로드와 전용 에뮬레이터를 함께 중단한 후 속도가 돌아왔다고 확인.
- 대시보드 패키지 설치 354개·39초, 공개 APK 200,693,280bytes. 지연 당시 전송 속도·CPU·메모리는 측정하지 않아 어느 작업이 원인인지 확정하지 않음. 후속 npm 설치는 `next@16.4.3` 메타데이터 요청에서 대기 중이었으며 전송 바이트는 미계측.
- 해당 npm 프로세스와 부모 셸만 소유 관계 확인 후 종료. 테스트용 AVD도 `Runmoni_API36` 이름 확인 후 정상 종료. 다른 프로젝트 프로세스·보안 프로그램·방화벽은 변경하지 않음.
- 중단 후 .NET 인터페이스 통계로 Wi-Fi 5초 표본 수신 0.097Mbps·송신 0.007Mbps 관측. 이는 중단 후 표본이며 지연 당시 회선 점유율이나 보안 이벤트 유무를 증명하지 않음.
- CIM 기반 추가 읽기 진단은 자동 승인 검토 시간 초과로 미실행. 계측 실패를 보안 정책 위반 판정으로 해석하지 않음.
- 후속 작업은 캐시된 오프라인 빌드·한 번에 1개 작업을 기본으로 하고 테스트 AVD를 상시 실행하지 않음. 대량 패키지 다운로드와 배포는 이번 지연 관찰 중에는 재개하지 않음.

## 미출시 — 2026-10-24 (외부 공개 터널 사용 금지)

- 보안부서 연락을 받은 사용자 지시에 따라 cloudflared 사용 금지와 다른 외부 공개 터널을 통한 우회 금지를 AGENTS.md·README에 기록.
- 개발 연결 기본값은 localhost·USB `adb reverse`. 테스트 목적의 서버 외부 공개·방화벽 예외 추가 금지.
- 확인 당시 cloudflared 실행 프로세스 0개·등록 서비스 0개. 변경 전 저장소 텍스트 검색에서 터널 관련 참조 없음.
- 프로세스 명령줄의 CIM 조회는 접근 거부되어 Get-Process로 실행 여부를 확인. Windows 예약 작업·보안 솔루션 이벤트 이력은 확인하지 않음. 보안 이벤트 0건을 보장하지 않음.
- 문서와 작업 지침만 수정하므로 앱 버전은 유지. 기존 미커밋 앱·Firebase 변경은 보존.

## 0.1.2-dev — 2026-10-14 (Firebase 첫 단계, 원격 미연결)

- 사용자 선택으로 공용 계정·DB 방향을 Firebase 무료 Spark로 확정. Supabase 초안은 과거 이력으로 보관.
- versionCode 3. Kotlin 2.0 도구 체인과 맞는 Firebase BoM 33.16.0의 Auth/Firestore main modules와 Credential Manager를 추가.
- `.secrets/firebase/google-services.json`을 검증해 공개 Android 설정만 BuildConfig에 적용. 파일이 없으면 Google 연결을 비활성화하고 기기 기록을 유지.
- 파일럿과 분리한 Google 계정 세션·복구, 선택한 완료 기록의 요약 백업, 서버 최근 50건 조회·요약 삭제 코드 추가.
- 백업 확인 계정이 바뀌면 확인창을 초기화. 비동기 완료에는 UID·세션 epoch를 검사하고 Firestore 메모리 캐시를 사용.
- GPS 원본·마일리지·서버 `synced`·꾸미기 소유 정보는 백업하지 않음. 기기 삭제와 클라우드 요약 삭제를 구분.
- 본인 UID 경로만 열고 그 외 크루·위치·지갑 경로를 닫는 Rules 및 실제 Emulator 테스트 89개 통과.
- 원격 Firebase 프로젝트 생성·Rules 배포·Google 로그인 실기기 검증은 0건. 크루·파티·보상은 현재 SQLite API를 계속 사용.
- APK 게시 인증은 사용자가 나중에 진행하기로 하여 보류. 이전 서명 APK와 체크섬은 그대로 보관.

## 0.1.1-dev — 2026-10-13 (소스 게시·서명 APK 준비)

- versionCode2로 올리고 디버그 키와 다른 보관용 개발 서명을 release build에 적용.
- 키/비밀번호/로컬 설정을 Git에서 제외하고 키 부재 시 릴리스 빌드를 중단하는 스크립트 추가.
- Room2개/서버SQLite14개 테이블과未클라우드 운영 상태를 README에 명시.
- GitHub 소스·APK prerelease, 대시보드 개발 프리뷰 등록을 위한 메타데이터 준비.
- 소스 92개 파일 게시와 대시보드 등록·Vercel 배포 확인. APK 업로드는 인증 미완료로 수행하지 않음.
- 개인GPS·서버 API·오디오의 기능 변경은 없으며 실기기/클라우드 미검증 경계를 유지.

## 0.1.0-dev — 2026-10-13 (첫 Android 개발 버전)

- 빈 프로젝트에 Kotlin/Compose Android 앱·Room·foreground GPS 서비스를 구성.
- 홈/탐색/러닝/크루/나 5탭, 실제 기록·일/월/년·GPX, 5슬롯×4종 미니미.
- 일시정지·복구 segment, 정확도/속도/공백 필터, km 구간·활동시간.
- Room parent REPLACE 대신 Upsert로 누적 GPS child 삭제 회귀 방지.
- 지속 로컬 지급 ledger로 복구/이벤트 재생 중복 마일리지 방지.
- 서버 재시도 시 이미 finalized된 기록은 재업로드하지 않고 기존 지급 결과 사용.
- 15초 정밀 presence 게시, 45초 만료, 종료·서버 변경·프로세스 재시작 해제.
- AndroidKeyStore 세션 보관, 루프백 외 HTTP 제한, 파일 공유 provider.
- LiveKit 연결·마이크·음악/음성 독립 음량, 파일 decoder/Opus bridge, 외부6앱 캡처 요청.
- 로컬 API 0.1.4: 초대 성인 테스트, 크루/DJ/파티/채팅, 정원·지갑 원자성, 마이크 token·DJ lease.
- 채팅 초기 latest100/양수 cursor next100, 소수 초 기록 sync 경계 수정.
- Supabase 이관 SQL과 권한 테스트 초안. 실제 PostgreSQL 실행은 미검증.
- 반복 인터넷 단절 후 온라인 빌드를 중단. 기본 오프라인·작업자1개 빌드 스크립트 추가.
- 실행 테스트·실기기 미검증은 docs/VALIDATION.md에 분리 기록.
- 기기 실제HTTP 검증으로 파티 요청의 불필요한 필드 제거 및 취소 목록 계약 확인.
- 시작 전 캐시GPS 제외, 권한 갱신에 따른 DJ 송출·방 연결 정리.
- 최종 82개 자동 테스트 통과: JVM6/API23/bridge32/Android21. lint0오류/41경고.
