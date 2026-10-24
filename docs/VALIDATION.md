# 검증 기록

빌드 통과, 합성 GPS 검증, 실기기 야외 정확도, 실제 클라우드 오디오 송출은 각각 다른 증거입니다. 아래에는 수행한 것만 완료로 기록합니다.

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

실제 release APK 설치·화면·원격 업데이트 피드 확인은 실행 후 아래에 별도 기록한다. Google 본인 로그인과 야외 GPS·햇빛 가시성·TalkBack 실사용·2대 음성/DJ는 미검증이다. 이전82개 또는 Rules89개를 이번 실계정 검증으로 합산하지 않는다.

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
