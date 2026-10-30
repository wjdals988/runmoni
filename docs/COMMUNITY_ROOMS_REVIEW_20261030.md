# 크루·파티 방 / 2026-10-30

요청: 코드를 받아 가입하는 방식 대신 방을 만들고 목록에서 찾아 입장한다. 이어서 공용 서버 생성도 요청했다.

## 동작

- 크루 탭에서 크루/파티 방·참여한 방과 이름·지역·페이스 검색을 제공한다. API는 종류별 최대100개를 반환하며 서버 검색/페이지는 후속 작업이다.
- 연결 없이 공개 목록을 둘러본다. 생성/입장할 때 닉네임과 기존 성인 확인으로 게스트를 연결한다. 초대코드는 입력하지 않는다. 세션은180일이며 연결 해제/기기 변경/재설치/만료 후 복구는 미지원이다. Google 인증/백업 연결로 표시하지 않는다.
- 크루는 지속 모임이며 입장 후 공지·채팅·멤버/DJ·파티로 이어진다. 방장은 현재 탈퇴할 수 없다. 파티는 크루 없이 또는 공개 크루 멤버가 생성한다. 참가가 크루 가입을 뜻하지 않는다. 현장/원격, 미래 일정1년, 거리/페이스/정원2~20명과 생성자1자리 규칙을 유지한다.
- 성공 응답 ID로 생성 결과를 표시한다. 실패하면 폼/내용을 보존하고 중복 제출을 막는다. 생성 성공 후 목록 갱신 실패도 구분한다.

## 보존과 권한

users.auth_mode, crews.visibility/room_kind를 추가한다. 기존 계정/방/원장/참여를 초기화하지 않고pilot으로 남긴다. 새 Android가 만든 크루에만open을 명시하며 이전 클라이언트가 생략하면pilot이다. 독립 파티의 내부party 그룹은 기존 NOT NULL 외래키를 보존하기 위한 저장 방식이며 일반 크루 목록/직접 가입에서는 제외한다.

게스트는 기존 파일럿 방을 코드/ID로 가입하거나pilot 정밀 위치를 조회할 수 없다. 이름을 제외한 로컬 기록/포인트/꾸미기 보유 내역은 로컬 지갑을 유지한다. RunSyncWorker는 게스트/토큰 변경을 확인한다. 실제 음성/음악은 기존 LiveKit/bridge가 별도로 필요하다.

## 검증과 배포

- 백엔드34개 통과/경고1개. 새7개: 게스트 발급/중복·만료, 공개 목록·가입, 기존 방·위치 보호, 독립 파티/크루 자동 가입 없음, 마지막 자리 경합, 멤버 생성/권한·취소·지난 일정, additive 데이터 보존.
- 샌드박스 TestClient 대기 종료 후 승인된 환경에서 확인. 새 health 키의 기대값을 수정했고 Android 테스트 Future 참조는 기존 Flow로 수정했다.
- 현재 기본 API는 로컬 주소다. 공용 서비스 생성 권한은 연결돼 있지 않다(환경 identity manifest 연결0개, 호스팅 관리 도구 없음). GitHub와 기존 정적 대시보드 배포는 API/영속 DB 생성 권한과 다르다. 기존 Firebase Spark 무료 설정을 임의로 유료 전환하지 않는다.
- 공용 서버에는 영속 DB/HTTPS가 필요하다. 외부 공개 터널·방화벽 예외를 사용하지 않는다. 실제 공용 URL 생성/응답·영속성 확인은 수행한 결과만 기록한다.

컨테이너의 첫 기동은 복사된 소스 파일 읽기 권한 오류로 실패했다. 이미지 내부 COPY 권한을 수정한 후 비root UID10001·호스트 loopback HTTP를 확인했고 컨테이너를 제거/재생성해도 세션·크루·파티·메시지가 유지됐다. [실제 결과](app-review/2026-10-30-rooms/container-review.json). 이는 Render 공용 서버 생성/원격 영속성 확인과 다르다. Render 설정은 공식 Blueprint 스키마를 통과했으며 아직 배포하지 않았다.

## Android 결과와 캡처

앱/테스트 APK 오프라인·작업자1개 빌드 성공. 기존 JVM40개 통과, lint오류0/경고35, 앱 의존성 추가0. [요약/빌드 당시 체크아웃](app-review/2026-10-30-rooms/verification.json) · [백엔드 원시 결과](app-review/2026-10-30-rooms/backend-tests.xml).

Android 첫 실행은9개 중8개 통과/1개 실패(203.961초). 큰 글씨 목록/상세/생성, 생성 실패 후 입력 유지, 크루 없는 파티, 초대 없는 연결, 기존 멤버 권한/정원/채팅 상태 보존8개는 통과했다. 실제 API 테스트는 방 생성·참여·메시지·자동 업로드 차단 뒤 탭 탐색에서 실패했다. 요약 화면 중첩 가능성을 고려해 테스트용 러닝 종료를 화면 검증 뒤로 옮겼지만1개 재실행도 실패했다(59.513초).

[실패 캡처](app-review/2026-10-30-rooms/native-failure.png)와 UI XML에서 **System UI isn't responding** 대기창이 앱을 가린 것을 확인했다. 앱 응답 없음 창을 닫은 것이 아니다. System UI의 Wait를 선택한 뒤 **같은 앱/테스트 APK**에서 네이티브1개가52.698초에 통과했다. 앱 APK 해시는 테스트 순서 수정 전후 동일하다. 최종 확인은 **서로 다른9개 사례 성공(처음8개+기기 준비 후1개)**이며 ‘9개가 한 번에 모두 통과’로 표시하지 않는다. [초기9개](app-review/2026-10-30-rooms/instrumentation-initial.txt) · [첫 재실패](app-review/2026-10-30-rooms/instrumentation-native-rerun.txt) · [최종1개](app-review/2026-10-30-rooms/instrumentation-native-final.txt) · [기기 준비](app-review/2026-10-30-rooms/device-preflight.json).

네이티브 검증은 실제 MainActivity/AppViewModel/localhost API에서 게스트 연결→공개 크루 생성→독립 파티 생성→두 번째 테스트 프로필의 참가·중복 참가→메시지→실제 방/채팅 표시를 확인했다. 게스트 연결 전후 로컬 포인트/보유/아바타를 비교하고, 로컬 완료 기록과 WorkManager 실행 뒤 원격 기록0건·로컬 synced=false를 확인했다. 사용자 데이터 대신 별도 임시 DB/테스트 프로필을 사용했으며 로컬 더미 기록을 삭제하고 이전 지갑을 복원했다.

| 실제 Android 전체 화면 (소유한 loopback API의 테스트 데이터) | 범위 |
|---|---|
| [방 목록](app-review/2026-10-30-rooms/native-room-list.png) | 실제 생성한 크루. 목록 재조회 중 캡처여서 생성 버튼은 잠시 비활성 |
| [크루 상세](app-review/2026-10-30-rooms/native-crew-room.png) | 실제 방장·채팅·파티/멤버 행동 |
| [파티 상세](app-review/2026-10-30-rooms/native-party-room.png) | 크루 없는 파티/참여2명 |
| [파티 채팅](app-review/2026-10-30-rooms/native-party-chat.png) | 다른 테스트 프로필의 실제 API 메시지 |

| 실제 Android Compose 상태 주입 fixture | 범위 |
|---|---|
| [360dp 목록](app-review/2026-10-30-rooms/rooms-list-360-100.png) | 생성/검색/입장 분류 |
| [320dp·200% 목록](app-review/2026-10-30-rooms/rooms-list-320-200.png) | 스크롤·생성 조작48dp 이상 |
| [320dp·200% 상세](app-review/2026-10-30-rooms/rooms-crew-detail-320-200.png) | 파티/나가기 조작 |
| [320dp·200% 생성](app-review/2026-10-30-rooms/rooms-create-crew-320-200.png) | 폼 스크롤/글자 잘림 |
| [파티 상세 fixture](app-review/2026-10-30-rooms/rooms-party-dialog-360-100.png) | 독립 파티 생성 성공 후 상세 |

상태 주입 화면은 공용 서버의 실제 모집 목록이 아니다. 호스트 GIF/합성 목업을 Android 캡처로 사용하지 않았다. 승인 원본 SHA256 `f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65`와 캐릭터 렌더러를 보존했다. 새 그래픽/여자 기본형/의복 통합을 이번 검증으로 승인하지 않는다.

전용 AVD `Runmoni_Cloud_29`/API29/AOSP/TCG에서만 실행했다. 크기480×854/density160/fontScale1, 애니메이션3종1.0 복원·reverse8789 제거·소유 API 종료를 확인하고 AVD PID192248을 종료했다. [설정 결과](app-review/2026-10-30-rooms/device-after-review.json). 실제 휴대폰2대/Google 인증/음성/공용 HTTPS·원격 영속성은 미검증이다. 여러 기기 계정 복구·방장 위임/방 삭제·서버 검색/페이지·생성 timeout 후 커밋 확인은 후속 항목이다.

## 원격 반영

구현/검증/배포 설정 커밋 `39b24f38ef474853b68519ac81b556520120cafd`, 원격 `codex/community-rooms`, [PR10](https://github.com/wjdals988/runmoni/pull/10). 앞선 PR9가 아직 열려 있으므로 기준 브랜치는 `codex/run-readiness-and-auto-pause`다. main 병합·새 서명 APK 업로드·공개 피드/대시보드 변경은 수행하지 않았다. 공용 서비스는 호스팅 계정 연결/비용 승인 전이며 URL을 발급받았다고 기록하지 않는다.
