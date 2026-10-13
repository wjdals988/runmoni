# 검증 기록 — 2026-10-13

빌드 통과, 합성 GPS 검증, 실기기 야외 정확도, 실제 클라우드 오디오 송출은 각각 다른 증거입니다. 아래에는 수행한 것만 완료로 기록합니다.

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
