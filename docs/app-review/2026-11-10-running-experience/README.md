# 러닝 위젯·지도·기록 페이지 검토 — 2026-11-10 한국 시간

이 폴더는 로컬 TEST 서명 debug의 실제 입력/검사 근거다. 공개 보관키 서명 APK·실제 휴대폰·Google 로그인·운영 쓰기·야외 GPS·Doze/OEM·API31이상 실행이나 사용자 시각 승인으로 표시하지 않는다. [변경·공식 출처·제공자 정책](../../RUNNING_EXPERIENCE_20261110.md).

## 최종 로컬 검토 결과

source6의 JVM 141개/실패·오류·skip 0, lint 오류 0/경고 43. Android는 source4 핵심 16개 보존과 source6 새 3개를 구분한다. live7은 실제 위젯 종료 취소·저장 → 독립 기록 지도 → 하단 전체 조작 영역 → 실제 삭제·DB 원상 복원과 위젯/설정 복원을 통과했다. 알림 제어는 live6의 실제 Pause/Resume ACK를 SHA로 연결해 보존하며 live7에서 재실행한 것으로 세지 않는다. 재개는 두 번째 실제 탭에서 ACK를 관측했다. live1~6 전체 실패는 그대로 보존한다.

live6은 지도·알림·저장까지 확인했지만, 수집기가 부분 노출 GPX에서 스크롤을 멈춘 뒤 Delete를 찾다가 timeout으로 종료했다. footer6 사진은 지도 사진과 같은 본문이며 전체 하단 조작 증거가 아니다. live7은 Delete까지 스크롤하고 전체 버튼 경계를 확인했다. 시도 중 원인을 확정하지 못한 실패와 수집기 보강은 원문 JSON/실행 소스에 남긴다. 부분 running 지도 PNG는 출처가 잘려 로컬에만 보존하고 공개 파일·갤러리에서 제외했다.

실제 지리 배경의 첫 네이티브 HTTPS 6타일 관측은 live2, 이후 사진은 그 캐시를 사용할 수 있다. 로컬 TEST debug7은 Firebase 비활성·세션 CA overlay 구성이다. 공개 서명 APK 실행과 같지 않으며 휴대폰/Google/야외 GPS/장시간 절전/API31+는 별도다. guest 프록시 5키와 검토 데이터/설정/위젯을 복원했고 전용 AVD를 종료했다. 최종 갤러리 네 장은 Root가 원본 PNG를 직접 보았으며 사용자 시각 승인은 아직 없다.

## 현재 유효한 검증

- 최종 제품 입력 source6: 로컬 JVM 141개, 실패·오류·skip 0, lint 오류 0/경고 43.
- Android는 source4의 지도·기록·탐색·위젯 **16개 성공을 보존**하고, source6에서 러닝·5탭·실제 320dp/글씨 2배 상세 **3개를 새로 통과**했다. 단일 바이너리의 새 19회로 합산하지 않는다. 두 입력 사이의 변경은 라이브 빈 지도 문구·여백 두 줄이며 `source4-to6-scoped-change.json`으로 확인한다.
- 별도 debug7은 제품 소스를 source6와 유지하고 로컬 세션 전용 CA를 debug source set에만 연결했다. 이 입력의 네이티브 HTTPS 타일 수신·실제 지리 배경 관측은 공개 서명 APK 실행 검증과 다르다.
- live1~3은 전체 시나리오 실패이며 일부 실제 위젯·알림·합성 GPS·지도 관측만 보존한다. live3의 기존 DB·XML·위젯 cache·통제 환경과 원래 위젯 ID 집합은 모두 복원을 확인했다. 후속 실제 결과는 위 최종 로컬 검토와 live4~7 원문을 따른다.

아래는 개별 시도와 실패·수정·복원의 원문 이력이다.

## 입력과 빌드

- `source-freeze1/2/3.json`은 parent main34fefff 위 미커밋 작업 트리의 실제 전체 소스/테스트/리소스/자산/구성 해시다. 이 parent를 실제 APK 소스 커밋이라고 바꾸어 기록하지 않는다. 후속 커밋과 입력 일치는 별도로 확인한다.
- `binary-build1/2/3.json`과 `build1/2/3.txt`는 해당 로컬 바이너리의 해시·크기, XML 집계와 Gradle 로그다. build1(2분35초)·build2(1분50초)·build3(1분44초)는 각각 새 JVM141개/실패·오류·skip0이다. 실행 사이 결과를423개로 합산하지 않는다. lint는1차 오류0/경고46,2·3차 오류0/경고43이다. 3차 lint report의 UP-TO-DATE는 로그에 그대로 남긴다.
- 승인 원본/앱 원본/검토 원본의 SHA256 f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65와 기존 원화·36개 준비/22개 미준비·58ID/13슬롯/0P/성별·과거 APK 출처는 보존한다.

## 상호 검토와 실패

위젯·지도·기록·색상 담당 에이전트가 서로 읽기 검토했고 루트만 빌드/AVD/전송을 실행했다. 좁은 위젯의150% 글씨에서 고정 기준만으로 버튼이 잘릴 수 있다는 공통 반론을 받아 RemoteViews 실제 측정 후 대체 레이아웃을 선택하게 했다. 삭제 ACK에는 초기화/목록 오류 상태를 추가하고 effect key도 보강했다. 수동 일시정지는 잠든 서비스의 같은 live marker에서 재개할 수 있게 하며 자동 일시정지의 heartbeat와 구분한다.

첫 Android 묶음 `android-review1`은 지도3개 통과 뒤 기록3개 중1개가 새 백업 확인창 누락으로 실패했다. 단독 재현 `android-review2`도 동일 위치에서 실패했다. 실패 원문을 지우거나 나머지 미실행 항목을 통과로 세지 않았다. 두 시도 모두 원래 XML/프로필과 통제 환경을 복원했다.

별도 정적 검토에서 이전 callback의 불변 UID/record snapshot 비교가 최신 확인이 아님을 찾았다. 최신 ID/UID/허용 상태/actions, context identity와 열 때마다 새 request identity, 화면 생존을 확인하게 했다. 취소/재열기·뒤늦은 dismiss·화면 폐기 뒤 callback을 거절한다. 테스트는 옛 Semantics OnClick 직접 호출 회귀와 실제 새 터치 검사를 구분한다. 기존 확인창 누락의 원인을 이 callback 문제로 단정하지 않는다. Dialog semantics 제거와 실제 Activity 포커스 반환을 구분하고 fresh touch 전 enabled/displayed·실제 bounds·포커스를 관측한다.

지도 요청 취소 후 돌아오는 이전 HTTP read가 새 viewport의 로딩 상태를 지우지 못하도록 현재 요청 identity와 coroutine 취소를 확인한다. 새 검사는 NonCancellable fake 지연을 제어하며 실제 OSM에 요청하지 않는다.

## 실행과 복원 경계

`Runmoni_Cloud_29` 한 대의 AOSP API29/TCG를 headless로 사용한다. 설치 전 SystemUI ‘응답 없음’에서 정확한 Wait를 선택한1차/3차 부팅 관측은 work의 원본 캡처와 installation JSON에 기록한다. 환경 재연결로 중단된1차 부팅 보조 프로세스와 build3 수정을 위해 설치 전 중단한2차 부팅은 제품 검사 성공/앱 실패가 아니다. PC의 AVD나 외부 터널을 사용하지 않는다.

`installation1/3.json`은 -r 설치와 실제 APK 쌍/입력 확인을 연결한다. 앱 데이터 초기화/원래 DB 복사 덮어쓰기를 하지 않는다. 공개 검사 JSON은 원래 사용자 XML/좌표/계정 값을 싣지 않는다. XML과 환경/SQLite 사본은 work 아래0600으로 보관하고 공개 폴더에는 해시·개수·통제 범위만 남긴다.

UI fixture는 ComponentActivity·합성 AppState/콜백·unavailable 타일 source다. 실제 Android에서 그렸다는 사실과 실제 MainActivity/온라인 지도/GPS 성공은 다르다. native320dp/font2 검사는 OS navigation/IME/safe viewport와 하단 행동의 전체 측정 영역을 비교한다. 원본 XML/프로필, 검사 소유12개 설정·런타임 권한/flags·관측 전원 복원은 전체 OS 파일/런처 상태가 바이트 동일하다는 뜻이 아니다.

실제 MainActivity·런처 위젯·SystemUI와 합성 GPS는 별도 `live` 근거로 구분한다. 기존 기록이 있는 홈 사진은 공개하지 않으며 별도 합성 홈 화면을 사용할 때 출처를 명시한다. 실제 타일 조회/캐시와 Naver SDK 인증, 공개 서명 APK 실행을 서로 대체하지 않는다. 최종 실행 결과·실패·실측 캡처는 완료 후 아래에 이어 기록한다.

## 3차 검사 대상 정정과4차 입력

3차 Android는 지도4개를 통과한 뒤 기록4개 중1개가 추가한 옛 SemanticsAction 검사에서 실패했다. 원문/정확한 통제 범위 복원은 `android-review3`에 보존한다. Foundation1.7.0의 실제 캐시 bytecode(`AbstractClickableNode.applySemantics`, `$applySemantics$1.invoke`, `updateCommon`)를 읽어 해당 action이 당시 함수 사본이 아니라 node의 현재 getOnClick을 읽는 wrapper임을 확인했다. 그 실패만으로 옛 business callback이 guard를 통과했다고 결론 내리지 않는다.

4차 입력은 Button.onClick과 동일한 실제 함수값을 내부 `RecordActionSnapshot` typed semantics 값에 전달하여 회귀검사 대상을 바로잡는다. 별도 native 접근성 action이나 label은 추가하지 않는다. 실제 새 터치는 기존 Foundation 경로 그대로 유지하며 단계별 합성 tuple·node id·Activity 포커스/버튼 bounds도 기록한다. 기존 확인창 누락과3차 callback 실패는 원인이 확정되지 않았고 구분해 보존한다. `key(record.id, UID)`로 page/Dialog owner를 폐기하고 외부 record ScrollState를 유지하는 보강도 상호 검토했다.

## 실제 실행·네트워크 경계 갱신

제품 입력 source6는 source4의 라이브 빈 지도 문구/여백2줄만 바꿨다. source4의 해당 없는16개 성공을 보존하고 source6에서 영향받는 러닝/5탭2개와 실제320dp/font2 상세1개를 새 실행했다. `source4-to6-scoped-change.json`의 역방향 SHA 재구성이 변경 범위를 확인한다. source4의 실패한 러닝1개와 source6의 새 통과를 같은 실행으로 바꾸지 않는다.

live1의 위젯/알림/화면 끔 합성GPS 확인은 종료 버튼 탐색 실패 전의 하위 관측이다. 기본 HOME 페이지와 위젯이 있는 페이지가 달랐다. 원래 DB0/0·XML/위젯cache/통제 환경은 별도 data repair로, 위젯ID[2]는 실제 Remove 동작 후 서비스 ID가 정리될 때까지 기다려 복원을 확인했다.

기본 Android 게스트에는 제공된 클라우드 프록시/CA가 없었다. 호스트 HTTPS의 검사 인증서 issuer와 게스트 설정을 대조했고, 임시 writable-system의 정상 remount도 실패했다. 해당 시도는 CA/프록시 추가 전에 중단하고 enable-verity 성공/SDK 임시 디스크 종료를 기록했다. source7 로컬 TEST debug만 work의 별도 init/res source set으로 제공된 CA를 debug-overrides에 연결했다. 실제 APK의 raw CA SHA·컴파일된 debug-overrides를 확인했으며 제품 main/배포 보안 XML·서명키는 변하지 않았다. TLS 검증을 끄거나 호스트의 proxy/NO_PROXY를 바꾸지 않았다.

`live2-native-map-network-observation.json`은 앱의 네이티브 HTTPS 요청으로 현재 viewport 타일6개를 받은 근거다. 실제 지도 그림을 확인했고 cache metadata savedAt은 모두 해당 실행 이후다. 호스트가 타일을 넣지 않았으며 원본 타일은 공개하지 않는다. source7의 TLS 설정 차이는 로컬 검토 설정이므로 공개 서명 APK 실행/휴대폰 인증 성공과 구분한다.

live2는 위젯/화면 끔 기록·실제 지도 관측 후 접힌 알림 카드에서 일시정지 버튼을 찾지 못해 실패했다. 이 실패를 앱의 실제 버튼 실행 실패로 단정하지 않는다. 기록/설정/위젯cache/12개 통제 환경·기존 위젯ID집합은 정상 finally에서 복원했다. 다음 검사에서는 실제 카드의 펼침 동작을 거친다. 모든 실패 원문과 관측을 보존한다.

부분적으로만 지도가 보이는 running 스크린샷은 독립 갤러리로 사용하지 않는다. 공개 지도 사진은 실제 출처 표기가 함께 보이는 전체 기록 지도 사진으로 선택한다.
