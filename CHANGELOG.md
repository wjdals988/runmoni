# 변경 이력

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
