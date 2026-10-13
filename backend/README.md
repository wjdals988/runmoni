# 런모니 로컬 파일럿 서버

요약: v0.1.4는 성인임을 확인한 초대 테스터를 위한 FastAPI + SQLite 서버입니다. 현재 인증은 **초대 코드 파일럿 인증**이며 이메일 OTP/본인 인증은 구현하지 않았습니다. Android는 `adb reverse tcp:8787 tcp:8787` 후 `http://127.0.0.1:8787`에 연결합니다. API 계약은 [API.md](API.md)에 있습니다.

## 설치와 실행

Python 3.12 이상으로 저장소 루트에서 실행합니다. 의존성은 2026-10-13 공식 PyPI 페이지로 확인하고 실제 설치했습니다. `requirements.lock.txt`는 이 Windows Python 3.12 환경에서 설치된 전체 버전입니다.

```powershell
python -m venv backend/.venv
& backend/.venv/Scripts/python.exe -m pip install -r backend/requirements.lock.txt
Copy-Item backend/.env.example backend/.env
```

`backend/.env`의 `RUNMONI_PILOT_INVITE`를 8자 이상의 별도 초대 코드로 채웁니다. 다른 값은 공백 없이 `KEY=value` 형태를 사용합니다. 파일에 저장한 값과 발급된 bearer token을 공유 저장소에 올리지 않습니다. 기존 코드를 바꿔도 이미 발급한 30일 세션은 유지되며, 세션 삭제 API로 폐기할 수 있습니다.

```powershell
powershell -NoProfile -File backend/run.ps1
```

스크립트는 항상 **127.0.0.1:8787**에 바인딩합니다. 생성되는 DB는 `backend/data/pilot.sqlite3`입니다. 한 서버 프로세스에서 운영하는 지인 파일럿이 대상이며 외부 배포 설정은 포함하지 않습니다. `GET /health`는 기능 설정 여부를 반환합니다. LiveKit 및 audio bridge 자격 증명이 없으면 음성/음악은 503 `capability_not_configured`로 실패합니다. 설정값 존재는 실제 서비스 연결 검증과 다릅니다.

직접 시작하는 경우 저장소 루트에서 실행합니다.

```powershell
$env:RUNMONI_PILOT_INVITE = 'operator-chosen-invite-code'
& backend/.venv/Scripts/python.exe -m uvicorn backend.main:app --host 127.0.0.1 --port 8787
```

## 검증과 운영 경계

```powershell
& backend/.venv/Scripts/python.exe -m pytest backend/tests -q -p no:cacheprovider --basetemp backend/.testtmp-verification
```

2026-10-13 로컬 검증은 **23개 테스트 통과, 9.68초, 경고 1개**입니다. 실제 SQLite 파일과 동시 요청으로 마지막 파티 자리 경쟁, GPS 중복/충돌 롤백, 단일 보상, 3개 러닝의 일별 상한 경쟁, 아이템 중복 결제, 권한, 차단, 45초 TTL, 오디오 JWT를 확인합니다. 추가로 201개 메시지의 최신/다음 100개 페이지, 0.001·0.999·5.401초 시간 저장, 기존 INTEGER-affinity DB 호환성 및 러닝 UUID 메타데이터 충돌을 검증했습니다. 경고는 Starlette의 HTTPX TestClient 사용 중단 예고입니다. LiveKit 토큰 테스트는 서명/권한 확인이며 실제 음성 연결·음악 청취는 별도 서비스와 Android 기기 검증이 필요합니다. Supabase SQL은 142개 문장/20개 함수 본문 문법만 통과했고 23개 pgTAP 검사는 실행하지 못했습니다. 상세 이력은 [CHANGELOG.md](CHANGELOG.md), DB 검증 범위는 [../supabase/README.md](../supabase/README.md)에 있습니다.

| 항목 | 파일럿 동작 / 한계 |
|---|---|
| 계정 | 초대 코드 + 고유 닉네임 + 성인 확인, 30일 bearer token SHA-256만 DB 저장. 성인 확인은 자가 확인이며 신분증 검증이 아님 |
| 파티 | **현장/원격 모두 크루장만 생성**. 2–20명, 생성자가 1자리 차지. 크루 회원만 참가 |
| 채팅 | 크루/파티 범위 권한 및 공지 권한 검증, 메시지당 2,000자. 활성 화면에서 3초 polling |
| 위치 | 본인 active run + 정확한 위치 명시 동의 + 허가된 범위, server TTL 45초. `pilot`은 모든 초대 인증 성인 테스터가 보는 범위 |
| 보상 | 100점/km. accuracy ≤30m, speed ≤8m/s, gap ≤60s, 동일 segment만 거리 계산. 기본 일별 상한 10,000점은 조정 가능한 악용 방지 설정 |
| 시간 | 밀리초 UTC 시작/종료 시각을 소수점 초로 변환. 0 ≤ 활동 시간 ≤ 경과 시간 ≤ 86,400초. 기존 완료 기록 재전송은 저장된 요약 반환 |
| 꾸미기 | 5슬롯 × 4종 = 20 SKU, 비용 0/500/1,000/2,000점, 비보유 유료 아이템 착용 거부 |
| 기록 삭제 | GPS 삭제 + 이력/통계 숨김. 기존 보상을 유지하며 재적립 방지용 summary tombstone/ledger 보존 |
| 오디오 | 음성 입장 JWT 5분, DJ music lease 15분, 방당 방송 1개, bridge 5초 lease 확인. 기술 테스트 확인 플래그가 음악 사용권 증명이 되지 않음 |

비판 관점: GPS 필터는 차량/모의 위치를 완전히 판별하지 못합니다. `pilot` 범위는 초대 테스터 사이에 정확한 위치를 공개하므로 이를 수락한 테스터만 켭니다. 현재 로그인 실패 제한은 프로세스 메모리에 있고 정상/실패 모두 IP당 분당 10회이며 재시작 시 초기화됩니다. 기기에서 세션을 잃으면 운영자 복구가 필요하고, 앱의 계정 삭제는 파일럿 운영자 대응 범위입니다. 기존 LiveKit 접속자는 차단·탈퇴·파티 취소 시 자동 강제 퇴장까지 연동하지 않았습니다. 새 토큰 발급은 거부하고 음악 lease 확인은 비활성 처리합니다.

긍정 관점: 동일 run UUID와 GPS seq를 재전송해도 완료 보상/구매 중복 처리가 발생하지 않아, Android 오프라인 큐와 재연결을 검증할 수 있습니다. 이 검증을 통과한 뒤 Supabase 이메일 인증과 Postgres adapter를 붙일 수 있도록 테이블/RLS/RPC 기반을 준비했습니다.

운영 로그는 계정 생성 UUID, 완료 run UUID와 적립량만 기록하고 bearer token/초대 코드/GPS 좌표는 출력하지 않습니다. FastAPI access log는 HTTP 경로/상태를 기록하므로 OAuth/음성 JWT를 URL 쿼리에 넣지 않습니다. bridge WebSocket lease token query logging은 bridge에서 별도 보호합니다. 앱에서 직접 전송하지 않는 추정 거리/가져온 기록에는 보상을 지급하지 않습니다.

할루시네이션 확률: 정량 산정 불가. 이 문서는 구현된 동작과 별도 검증이 필요한 범위를 구분합니다.
