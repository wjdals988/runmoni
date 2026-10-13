# 검증 기록 — 2026-10-13

빌드 통과, 합성 GPS 검증, 실기기 야외 정확도, 실제 클라우드 오디오 송출은 각각 다른 증거입니다. 아래에는 수행한 것만 완료로 기록합니다.

## 진행 이력

1. Gradle8.13/AGP8.11.1/Kotlin2.0.21/SDK36 의존성 구성.
2. SDK package.xml 샌드박스 읽기 제한으로 초기 빌드 실패. SDK 읽기 권한을 사용하고 출력·캐시는 프로젝트 work에 유지.
3. LiveKit2.29.0의 AudioSwitch JitPack 의존성 추가([공식 설치 문서](https://github.com/livekit/client-sdk-android/blob/v2.29.0/README.md)).
4. KSP 다운로드 시간 초과와 Maven DNS 실패 확인. 네트워크 원인 측정 전 Gradle/가상기기가 원인이라고 단정하지 않음.
5. 사용자가 작업 시간 중 연결 장애를 보고하여 다운로드·Runmoni 에뮬레이터 종료. 오프라인 작업자1개로 전환.
6. 오프라인 compile에서 Compose compiler 미캐시를 확인. 사용자가 인터넷 재개를 허용한 뒤 순차 빌드 재개.
7. Closet Row 닫는 괄호, 오디오 EOF protocol·MediaProjection null 처리를 컴파일로 검증/수정 중.

## 서버

현재 backend 로컬 pytest **23개 통과**(API0.1.4): 마지막 파티 자리 경쟁, 원자적 구매, 중복 finalize 1회 지급, 다중 기록 일일 상한, GPS 순번 충돌 롤백, segment 공백, 권한·차단·TTL45, 소수 초1/999/5401ms 기록, 201개 메시지 latest100/cursor100.

Supabase: 142개 SQL문/20개 PLpgSQL본문 구문 파싱 통과 보고. 23개 pgTAP 실행은 Docker/PostgreSQL/Supabase CLI 부재로 미실행. 구문 검증을 RLS 실동작 증거로 취급하지 않음.

## Android 및 오디오

최종 컴파일·단위 테스트·lint·기기 내 instrumentation 결과는 실행 후 이 파일에 추가합니다. 작성된 테스트 숫자를 통과 숫자로 세지 않습니다. Python bridge 초기21개 테스트에는 실제 Opus encode/decode와 native AudioSource 로컬 호출을 포함했고 실제 방 접속은 0회입니다. EOF 보완 뒤 다시 실행해야 합니다.

## 미검증

- 실제 휴대폰 GPS·야외 거리/페이스·화면 끔·강제 종료·이어폰·배터리/발열.
- NAVER 지도 키/실제 지도 인증, Supabase 배포·RLS 실행.
- LiveKit 실제 서버 연결 및 2대 voice/music 수신·단독 음량.
- YouTube Music/Samsung Music/Spotify/Melon/Genie/FLO 각각 캡처 호환성.
- 미성년자 서비스, 일반 공개 위치 지도, 대중 계정·복구, 신고·차단 UI, 푸시 알림, 유료 결제.

자동 테스트용 합성 GPS·fixture 닉네임·임시 테스트 지갑은 제품에서 실제 사용자의 활동으로 표시하지 않습니다.
