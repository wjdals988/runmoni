# 검증 기록

빌드 통과, 합성 GPS 검증, 실기기 야외 정확도, 실제 클라우드 오디오 송출은 각각 다른 증거입니다. 아래에는 수행한 것만 완료로 기록합니다.

## 2026-10-25 — 0.1.5-dev 원화 통합 서명 APK 검증 스냅샷

아래 APK 해시는 명시한 최초 소스 `3eacfed`의 검증본이다. AGP는 APK의 `META-INF/version-control-info.textproto`에 실제 소스 commit을 넣으므로, app tree가 같아도 최종 문서 commit 뒤 재패키징하면 APK 서명·해시가 달라질 수 있다. 최초 후보는 로컬 `outputs/snapshots/3eacfed`에 보존했다. 최종 게시 대상은 깨끗한 최종 commit으로 assemble·서명·내장 revision·63개 원화를 다시 대조하며, 정확한 배포 해시는 릴리스의 버전별 metadata와 SHA256SUMS를 기준으로 한다. 미게시 후보를 공개 피드에 넣지 않는다.

- 깨끗한 소스 `3eacfed7c7dc6f93e7ad0b657b116595fc9a4c14`에서 새 렌더러를 실제 Android 대상으로 컴파일·패키징했다. APK는 `0.1.5-dev/code6`, `com.runmoni.app`, min29/target36, non-debuggable, **210,775,779bytes**, SHA256 `fad6755cbd2d35f3ac4f553d0ce24fb0345a1359ef4469d391317e52c521cfa7`이다.
- `apksigner verify`의 APKv2 통과와 기존 인증서 SHA256 `2d7f724cd20b1ff2263eb34e8e02d5fec9db674488b10b813dd239838b975830` 일치를 직접 확인했다. 인증서 일치는 실제 휴대폰 업데이트·기록 보존 성공을 대신하지 않는다.
- APK ZIP의 `assets/avatars/v0.4.0`는 PNG63개+manifest 총64개다. PNG63개 전부 소스 SHA256과 일치, manifest SHA256 `fddcff84a253058242ae63fe613e7efb62a23783e3766db36af19288a668bafd` 일치다. release BuildConfig의 Firebase 활성화·기대 프로젝트 일치도 확인했다. 실제 계정 인증·원격 CRUD는 여전히0회다.
- 새 소스의 JVM은5개 suite·30/30이며 실패/오류/건너뜀0이다. 최종 lint는 오류0·경고26: UseKtx21,ApplySharedPref2,ModifierParameter1,DataExtractionRules1,ObsoleteSdkInt1. 기준 완화나 baseline 추가 없이 재분석했다.
- 환경 실패 이력: 기존 SDK에36 플랫폼 실물이 없어 첫 빌드는 컴파일 전23초에 중단했다. 공식 메타데이터의 `platform-36_r02.zip` 65,878,410bytes/SHA1 `2c1a80dd4d9f7d0e6dd336ec603d9b5c55a6f576`을 단일 연결·최대256KiB/s로252.125초에 받았고 체크섬 확인 후 `work/sdk36-local`에 추출했다. 전역 SDK 파일 변경0, 기존 Build Tools35/36·라이선스는 로컬 링크로 재사용했다. 빌드도구35 연결 전14.533초 중단도 별도 로그로 보존했다.
- 새 컴파일·APK·JVM이 실행된148.479초 검사에서는 직접 작성한 `local.properties` 드라이브 콜론 이스케이프1건으로 lint가 실패했다. 수정 후20.544초 검사가 이전 `UP-TO-DATE` 분석을 재사용한 것을 확인하고, 해당 lint 분석/보고서3개만 임시 로컬 init으로 재계산했다. 최종 **70.525초·82작업(4실행/78최신)**에 성공했다. 다운로드·AVD·다른 빌드와 겹치지 않았으며 전체 실패 로그를 `work`에 보존했다.
- 별도 게시 도구18/18·0.134초, 실제 로컬 APK plan 차단사유0·네트워크/GCM0회. 대시보드 기존 검사15/15·247.1497ms 통과와9개 중 런모니 설명/날짜2필드만 변경을 확인했다. 실제 GitHub 초안 업로드·공개 전환·대시보드 배포는 이 검사에 포함하지 않았다.
- 실제 Android 신규 렌더러 재생·성능·휴대폰 설치/업데이트·Google 로그인·원격 백업은 미확인이다. 이전0.1.4의 UI38/38과 실제 화면8장을 신규 원화의 런타임 증거로 합산하지 않는다. AVD는 실행하지 않았다.
- 후속 게시 실행에서 번들Python에httpx가없어credential/HTTP/업로드전중단했다. 이를별도설치없이nativecurl metadata API로대체해 확장23/23·0.139초, 실제인증저장소GET1건HTTP200·1.219초/변경0을확인했다. URL/메서드허용목록·응답1MiB·redirect차단·토큰stdin·원문미출력을유지한다. 실제Windows curl→일회성localhost수신기의 ASCII JSON바이트·한국어/emoji/개행/CR/quote/backslash보존도확인했고외부요청/실제credential0·서버종료다. 이결과는APK대량업로드완료의증거가아니다.

## 2026-10-25 — AVD 제외 후 설정·게시 도구 검증

- 공식 Firebase public project-config GET와 Google provider createAuthUri POST가 각각HTTP200. Android패키지/release SHA1/Webclient/FirebaseAppId 인수를 포함한 서버 검사, 프로젝트·기본authDomain·Google반환provider·GoogleHTTPS host·Webclient일치 확인. 본문146+851bytes, 전체프로세스2.093초. 사용자식별자전송/로그인/계정생성/DB쓰기0, 원문API응답/OAuth상태/API키저장0. 비공개콘솔/Rules/Android실인증/Firestore원격CRUD는 여전히미검증.
- `scripts/publish_android_preview.py`와 새 offline검사14/14·0.085초. 기본plan의 네트워크/GCM/하위프로세스 호출 차단, 경로·코드·패키지·크기·해시·서명선언/URL핀/기존자산 재사용, 3자산 검증후 public PATCH1회·digest/main 불일치/불완전 공개본 변경0을 fake transport로 확인. 이 fixture는 실제서명APK/실제GitHub게시/실기기검증이 아님.
- 당시 새 통합1.5/code6 소스는 준비 중이었다. 후속 서명 APK 결과는 위 항목에 별도로 기록했다. 기존1.4의 JVM30/30·UI38/38 결과를 새 AndroidCanvas 캐릭터의 런타임 검증으로 합산하지 않는다. AVD는 재실행하지 않았다.
- Firestore 공식 REST 익명 GET 1회는 curl35·HTTP0·본문0bytes·0.141초로 TLS 단계에서 종료했다. 실제UID/로그인/계정생성/DB쓰기0. 새 임의 경로만 사용했고 원문/임의경로/API키저장0. 익명 접근 거부·원격Rules·인증 후CRUD 검증은 성립하지 않았다. 반복 전송·프록시/보안 설정 변경 없이 중단했다.
- 공개 게이트 전 구체적인 검토본을 준비하도록 `--stage-draft`를 추가했다. 확장한 fake transport 검사 18/18·0.134초: 기본 plan 무전송, 최신 3자산 검증, 초안 모드의 공개 PATCH0회, 동일 초안 재사용, 기존 공개본 변경0회, 초안→공개 시 재업로드0회를 확인했다. 실제 GitHub 초안 업로드·공개 전환 검증은 아직 수행하지 않았다.
- 대시보드 로컬 JSON을 Git HEAD와 대조해 총9개 프로젝트 중 런모니 `description`/`updatedAt` 2개 필드만 변경, APK 메타데이터·다른8개 프로젝트 불변을 확인했다. 다운로드 교체·원격 푸시·Vercel 배포 성공과 구분한다.
- Firestore GET의 실행 환경을 작업 샌드박스 밖 호스트로 바꾼 후속1회는 HTTP403/PERMISSION_DENIED/표준권한거부문구·본문127bytes·0.343초였다. 임의 미사용 경로의 익명 목록 요청 거부만 관측했으며 실제UID·계정생성·DB쓰기·보안설정변경0. 초기TLS실패와별도보존, 원격Rules전체/인증후CRUD/AVD인터넷원인검증은아님.

## 2026-10-25 — 0.1.4-dev 모바일 재구성·기기 설정·Google 버튼

- 전용 AVD 실측 font_scale2.0,1080×2400,density420,Google계정0개. 글씨100%·화면/density 기본값 복원과 sync 후 종료. 앱 전역 글꼴배율 제한 없음.
- 오프라인 단일작업자 빌드2분59초·130작업(62실행/1캐시/67최신), 별도 앱정보 아이콘까지 반영한 증분 빌드1분24초·124작업(19실행/1캐시/104최신). JVM30/30·실패/오류/건너뜀0, release lint0오류/19경고, 대비9/9. 의존성 변경0.
- 첫 UI검사는 기본33중4개·native5중5개에서 기존 외부 검증이미지 경로의 EACCES/캡처실패. 원본 실패 로그를 보존하고 앱 기록 삭제 없이 테스트 이미지·글자 측정 로그만 앱 cacheDir로 옮김. 검사APK만 오프라인 재빌드33초·50작업(5실행/45최신).
- 재검사 기본33/33·76.344초, native320dp/font2 5/5·21.881초, 합계38/38. 실제 주행·종료·기록·동의·권한·앱정보 상태 검사이며 반복 뷰포트를 테스트 개수에 추가 합산하지 않음. debug/test APK를 로컬에서 기존 release키로 재서명하여 install-r, 실제 앱/기록 삭제0. 이어 non-debuggable 서명release를 같은 방식으로 복원.
- 최종 로컬APK0.1.4-dev/code5·209946142bytes·SHA256 aaddecd9436c2357d7973f513085553961da3624ccb6d81d4a789184dda8d829. 기존 release인증서 일치/APKv2 검증. 실제서명APK 화면8장과 동일리소스 앱아이콘 PNG1개, 글씨100% 캡처. outputs/design-review/20261025/mobile-reset에 보존. 신규 캐릭터 원화는 포함하지 않음.
- 사용자 직접 Google 버튼 실행 후 Google시스템 계정추가 화면의 Something went wrong을 보고. 새 버튼이 계정추가화면을 연 것과 Firebase 인증 성공을 분리한다. 실제 Google/Firebase 로그인·Firestore 원격CRUD0회. 콘솔 설정을 정상/오류라고 단정하지 않음.
- 사용자가 AVD를 켜면 PC 브라우저도 느리거나 불통, 닫으면 회복한다고 보고하고 직접 종료했다. AVD 재실행·추가대량전송 중단. 대기 도우미도 종료. 사용자가 먼저 창을 닫았으므로 마지막 세션의 guest ingress 원복은 확인 불가(다음 허가된 실행 전 점검), 폰트100%는 앞선 실제 캡처로 확인. 네트워크 원인·영향 해결로 보고하지 않음.

## 2026-10-25 — 디자인·앱 정보·설정 포함 서명 개발본

| 대상 | 확인 결과 | 경계 |
|---|---|---|
| Firebase 설정 | 프로젝트·패키지·Web OAuth·release 인증서4/4 일치 | 실제 Google 로그인/원격 CRUD/Rules 원문 대조0회 |
| 배치·상태 instrumentation | 38/38, 기본33개72.071초+native5개18.998초 | 글씨1/1.5/2·폭320/360/411, 실제320dp/systemfont2 별도. 반복 조건을 테스트 수로 중복 합산하지 않음 |
| 실제 아바타 재생 | 4/4 | 실제0..7프레임 그리기/픽셀 변화·옷장 정지/재개·STOP/START·시스템애니메이션0/1. frameOverride 대체 없음 |
| 업데이트 상태 | 7개 JVM+3개 제어+앱정보 화면 검증 | 수치code/채널/URL/서명메타,6h/30s/중복/실패복구,확인전외부실행0/후정확한링크1. 실제 파일 다운로드/설치 시험은 아님 |
| 최종 release 빌드 | 2분27초,82작업(36실행/1캐시/45최신) | offline/작업자1, 그 전38개 이후 색상 역할만 정규화 |
| JVM | 28/28,실패/오류/건너뜀0 | Engine6+Cloud9+Avatar6+AppRelease7 |
| lint | debug0오류/24경고, 최종release0오류/20경고 | baseline으로 경고 숨기지 않음 |
| 코드 색상 대비 | 9/9 | 최종 브랜드 불투명 토큰. 실제 햇빛/모든지도배경/모든합성색 검증 아님 |
| 라이선스 고지 | 실제 runtime148개,POM이름누락0,bundled134286bytes | 오프라인 캐시에서 추출, SDK 별도약관 구분. 법적 준수 인증 아님 |

38개 구성은 기존UI17+디자인2+Avatar통합3+실제재생4+앱정보4+업데이트제어3+nativeDialog4+nativeAbout1이다. 처음37개34통과/3실패 및 수정은 DESIGN_AUDIT에 남겼다. `work/design-ui-final.log`, `work/design-ui-native-large-final.log`, `work/design-chat-screen-debug-build2-20261025.log`, `work/design-information-release-build-20261025.log`를 보존했다.

### 최종 서명 APK 설치·화면·공개 피드 확인

- 코드 커밋 `e757cb7a3065433a423f64c366d5943f068d8374`를 GitHub main에 일반 푸시하고 원격 SHA 일치를 확인했다. 새 APK는 이 코드로 빌드한 로컬 개발본이며 GitHub 자산에 게시하지 않았다.
- `outputs/runmoni-0.1.3-dev.apk`를 전용 Runmoni_API36에 설치하고 실제 MainActivity의 COLD 실행을 확인했다. Android 측 TotalTime 2,201ms, WaitTime 2,242ms이며 실기기 성능 측정은 아니다.
- 최종 브랜드 색상으로 5탭·앱 정보·버전 이력·지원 링크·라이선스·마일리지·옷장·계정 설정 및 실제320dp/글씨200% 화면14장을 촬영했다. 피드 재확인과 큰 글씨 상품 스크롤 후 화면2장을 더해 원본 PNG는16장이다. [화면 확인 기록](SCREENSHOT_REVIEW_20261025.md).
- 큰 글씨 옷장에는 분류와 상품이 같은 스크롤 영역에 있다. 초기 캡처만으로 조작 불가라고 판단한 검토 의견은 철회했다. 스크롤 후 미니미를 유지하면서 ‘민트 러닝 반팔’·500P 가격·구매 버튼 전체가 같은 화면에 보이는 것을 추가 캡처했다. 기기 잔액0P여서 버튼은 정상적으로 비활성화되며 실제 구매를 수행하지 않았다.
- Google 계정0개, 설정 화면의 Google 로그인 버튼 활성화를 확인했다. 본인 로그인·원격 백업/조회/삭제·계정 전환은0회다.
- 게시 후 고정 raw 피드를 GitHub 연결 도구와 PC의 제한된 GET으로 확인했다. PC 응답 HTTP200/810bytes, schema1·공개 preview0.1.1-dev/code2이며 APK 본문 다운로드0bytes다. 게시 전404 이력과 구분한다.
- **AVD 앱의 실제 피드 조회는 초기 및 재확인 모두 실패했다.** DNS는 주소를 해석했지만 이것만으로 HTTPS 연결을 보장하지 않는다. PC GET 성공·테스트용 제어 상태 통과를 AVD의 실제 원격 조회 성공으로 표시하지 않는다. 앱은 실패/재확인을 표시하며 최신 버전이라고 단정하지 않았다. 원인은 미확정이며 프록시·방화벽·외부 터널 설정을 변경하지 않았다.
- 각 순차 검증의 finally에서 화면 크기·글씨를 복원하고 이름을 확인해 AVD를 종료했다. 최종 연결 기기0대·emulator/qemu 프로세스0개를 확인한다. 로그인 대기용 AVD를 켜두지 않는다.

로그: `work/release-review-execution.log`, `work/release-review-cold-launch.log`, `work/release-feed-recheck.log`, `work/release-wardrobe-review.log`. 요약은 `outputs/design-review/20261025/release-review.json`, `wardrobe-viewport.json`에 보존한다. Google 본인 로그인과 야외 GPS·햇빛 가시성·TalkBack 실사용·2대 음성/DJ는 미검증이다. 이전82개 또는 Rules89개를 이번 실계정 검증으로 합산하지 않는다.

## 2026-10-24 추가 검증 — Firebase 개발 소스와 공개 APK

| 대상 | 이번에 확인한 결과 | 확인 범위 |
|---|---|---|
| `0.1.2-dev` debug/test APK 온라인 빌드 | 성공, 4분 54초, 83개 Gradle 작업 | 작업자 1개, 29개 실행·54개 최신 상태 |
| 동일 소스 오프라인 빌드 | 성공, 1분 26초, 83개 Gradle 작업 | 5개 실행·78개 최신 상태. 추가 다운로드 없이 재현 |
| JVM 테스트 | 15/15, 실패·오류·건너뜀 0 | 기존 러닝 6개 + 신규 CloudRunSummary 9개. 실제 Google 인증은 수행하지 않음 |
| 신규 APK UI instrumentation | 17/17, 실패 0, 78.299초 | 기존 UI 14 + Firebase 경계 3. 설정 없음·파일럿 위치 권한 분리·계정 변경 시 백업 재확인 |
| debug lint | 오류 0개·경고 47개 | 경고를 숨기는 baseline을 추가하지 않음 |
| 공개 `0.1.1-dev` APK | GitHub 게시, 로컬/API 크기·SHA-256 일치 | 200,693,280bytes, 서명 APK + 체크섬 파일 2개. 실제 보조 파일 다운로드 내용까지 대조 |

온라인 빌드 중 Windows Gradle immutable transform 이동 오류가 재현됐습니다. 생성이 완료된 임시 변환 디렉터리와 목적지가 프로젝트 `work/gradle-home` 내부인지 확인하고, Java 프로세스가 없고 목적지도 없는 상태에서 해당 1개 디렉터리만 이동했습니다. 캐시 전체 삭제나 다른 프로젝트 프로세스 종료는 하지 않았습니다. 일부 권한 검토 시간 초과는 명령 미실행으로 구분했으며 빌드 실패로 집계하지 않았습니다.

`0.1.2-dev`는 `.secrets/firebase/google-services.json`이 없어 **FIREBASE_ENABLED=false**로 빌드했습니다. 실제 프로젝트 설정을 넣은 빌드·Google 로그인·원격 CRUD·Rules 배포·실기기 검증은 **0건**입니다. 공개 `0.1.1-dev` APK에는 Firebase SDK가 없습니다. 과거 Firestore Emulator **89/89**는 로컬 Rules 검증이며 원격 인증 증거가 아닙니다.

이번 로그는 Git에서 제외한 `work/firebase-build-online-20261024-recovered.log`, `work/firebase-build-offline-20261024.log`에 보관합니다. APK 게시 기록은 [RELEASE.md](RELEASE.md), Firebase 구현·설정 경계는 [FIREBASE_IMPLEMENTATION.md](FIREBASE_IMPLEMENTATION.md), [FIREBASE_SETUP.md](FIREBASE_SETUP.md)에 기록합니다.

UI 로그는 `work/firebase-ui17-20261024.log`, 집계는 `work/firebase-validation-20261024.json`에 보관합니다. 테스트 후 전용 `Runmoni_API36` 1개만 이름 확인 후 종료했습니다. 실제 Google 로그인·원격 Firestore·야외 GPS 증거로 확대하지 않습니다.

## 진행 이력

1. Gradle8.13/AGP8.11.1/Kotlin2.0.21/SDK36 의존성 구성.
2. SDK package.xml 샌드박스 읽기 제한으로 초기 빌드 실패. SDK 읽기 권한을 사용하고 출력·캐시는 프로젝트 work에 유지.
3. LiveKit2.29.0의 AudioSwitch JitPack 의존성 추가([공식 설치 문서](https://github.com/livekit/client-sdk-android/blob/v2.29.0/README.md)).
4. KSP 다운로드 시간 초과와 Maven DNS 실패 확인. 네트워크 원인 측정 전 Gradle/가상기기가 원인이라고 단정하지 않음.
5. 사용자가 작업 시간 중 연결 장애를 보고하여 다운로드·Runmoni 에뮬레이터 종료. 오프라인 작업자1개로 전환.
6. 오프라인 compile에서 Compose compiler 미캐시를 확인. 사용자가 인터넷 재개를 허용한 뒤 순차 빌드 재개.
7. Closet Row 닫는 괄호, 오디오 EOF protocol·MediaProjection null 처리를 수정하고 컴파일 통과.
8. 기기 테스트 첫 실행 21개 중18개 통과. 실제 파티 POST의 불필요한 `pace_min_per_km` 필드를 제거, 테스트 좌표 주입을 실제1초 GPS 간격에 맞춤, 중복45초 문구 선택을 명확히 함.
9. 서버 계약에 따라 취소된 파티가 일정 목록에서 제거되는 것을 검증. 시작 이전 캐시 GPS를 제외하고, DJ권한/참가권한 갱신 시 음악·음성 상태를 갱신.
10. 최종 기본 오프라인 빌드 스크립트 통과. 설치한 최종 APK로 Android instrumentation 21/21 통과.

## 서버

현재 backend 로컬 pytest **23개 통과**(API0.1.4): 마지막 파티 자리 경쟁, 원자적 구매, 중복 finalize 1회 지급, 다중 기록 일일 상한, GPS 순번 충돌 롤백, segment 공백, 권한·차단·TTL45, 소수 초1/999/5401ms 기록, 201개 메시지 latest100/cursor100.

Supabase: 142개 SQL문/20개 PLpgSQL본문 구문 파싱 통과 보고. 23개 pgTAP 실행은 Docker/PostgreSQL/Supabase CLI 부재로 미실행. 구문 검증을 RLS 실동작 증거로 취급하지 않음.

## Android 및 오디오

| 검증 | 결과 | 실제 확인 범위 |
|---|---|---|
| Android debug APK·test APK | 빌드 성공 | SDK36/JDK21/Gradle8.13, 앱0.1.0-dev |
| Android lint | 오류0/경고41 | 경고는 버전 업데이트·KTX 스타일·동기 세션 저장 등에 관한 항목. 회피 baseline 없음 |
| 러닝 JVM | 6/6 통과 | 거리·시간·중단·이상 GPS·평균페이스·복구 segment |
| Android instrumentation | 21/21 통과,49.245초 | UI14, Room·지갑3, 실제 HTTP1, native GPS service1, 로컬 오디오2 |
| Python API | 23/23 통과,5.19초 | 권한·트랜잭션·커서·소수초 경계 |
| Python 음악 bridge | 32/32 통과,1.74초 | 실제 PyAV Opus왕복·native AudioSource 큐, EOF/revoke 등. 실제 LiveKit방 접속0회 |

합계 **82개 테스트 통과**, 미실행 pgTAP는 포함하지 않습니다. 가상기기는 프로젝트 전용 Runmoni_API36이며 숨김/호스트 오디오 비활성으로 실행했습니다.

실제 HTTP는 Android AppViewModel→OkHttp→ADB reverse→127.0.0.1:8787 uvicorn→SQLite→응답 경로로 계정/크루/파티/공지/채팅/프로필/취소를 확인했습니다. GPS 테스트는 시스템 test provider의 합성 좌표10개를 실제 foreground service로 저장하고 앱이 홈 뒤로 간 동안에도 타이머와 경로가 진행되는지 확인했습니다. 시작 이전 캐시 좌표는 저장하지 않고 일시정지 구간은 거리 연결을 하지 않았습니다. 실제 야외 GPS 증거가 아닙니다.

로컬 오디오2개는 Android extractor/decoder/AudioTrack/foreground service를 실제 호출하여2초 WAV 자연 종료·별도 음량·중지 후 새 파일 유지·서비스 정리를 검증했습니다. 실제 이어폰 소리나 MediaProjection·LiveKit 송출 증거가 아닙니다.

재현 로그는 ignored `outputs/validation/`, APK/sha256는 `outputs/build-manifest.json`에 저장합니다. APK는218431165바이트(약218.4MB)인 universal debug 파일입니다. 여러CPU의 LiveKit/NAVER native 라이브러리와 개발 리소스를 포함하며 최종 스토어 배포 크기가 아닙니다.

## 미검증

- 실제 휴대폰 GPS·야외 거리/페이스·화면 끔·강제 종료·이어폰·배터리/발열.
- NAVER 지도 키/실제 지도 인증, Supabase 배포·RLS 실행.
- LiveKit 실제 서버 연결 및 2대 voice/music 수신·단독 음량.
- YouTube Music/Samsung Music/Spotify/Melon/Genie/FLO 각각 캡처 호환성.
- 미성년자 서비스, 일반 공개 위치 지도, 대중 계정·복구, 신고·차단 UI, 푸시 알림, 유료 결제.

자동 테스트용 합성 GPS·fixture 닉네임·임시 테스트 지갑은 제품에서 실제 사용자의 활동으로 표시하지 않습니다.
