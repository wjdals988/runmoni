# Google 완료 기록 요약 자동 백업

사용자 요청: 매번 기록 상세에서 백업을 누르지 않아도 연결된 계정에 자동 보관한다. 이 작업은 기존 `runmoni-cloud` Google/FirebaseAuth만 사용한다. 크루 익명 UID·Google 연결, HTTP pilot token·포인트와 서로 바꾸거나 합치지 않는다. 여기서 guest skip은 백업용 Google UID가 없는 상태를 뜻한다. 앱 첫 진입의 guest 선택이나 크루 guest flag로 별도 Google 세션의 UID를 만들거나 교체하지 않는다. 새 계정 생성·Rules 게시·키/Secret 작업·빌드/기기/네트워크 실행은 하지 않았다.

## 조사한 기존 경계

- FirebaseCloudAccount는 기존에 개인 users/UID/runSummaries/runId를 수동 set했다. Firestore cache는 memory이며 Room이 원본이다. 기존 RunSyncWorker는 HTTP pilot 전용이다.
- RunDatabase v1은 로컬 기록/좌표 두 테이블뿐이며 계정/수입 출처 정보가 없었다. 현재 production에서 Room에 쓰는 경로는 RunRepository의 로컬 생성·체크포인트다. 클라우드 요약이나 GPX/다른 사람 기록을 Room에 import하는 구현은 없다.
- 원래 summary whitelist는 schemaVersion/runId/시작·완료/활동시간/거리/GPS 측정 **개수**다. 실제 좌표·트랙 배열·포인트 잔액·마일리지·옷장은 포함하지 않는다. 기존 Rules의 필드 whitelist도 그대로 사용한다.
- Manifest는 allowBackup=false다. v2의 소유자 정보가 있는 DB가 별도로 복사되더라도 다른 UID로 재결속하지 않는다. v1 원본의 과거 수동 백업 계정을 추론할 근거는 없으므로, 기존 로컬 완료 기록은 업그레이드 뒤 처음 인식한 Google 계정에 한 번 결속한다. 이 정책을 과거 계정 복구로 설명하지 않는다.

## 구현

Room v2에 `cloud_backup_origins`를 추가한다. 기존 runs/track_points의 컬럼·행·좌표·마일리지·sync 상태는 바꾸지 않는다. migration1→2는 기존 v1의 로컬 runId별 빈 origin만 추가한다. 신규 로컬 생성과 origin은 한 transaction으로 저장한다. 앞으로 import 기능을 만들 때는 원본 소유권 확인 없이 이 local-origin 등록을 호출하면 안 된다. 단순 DAO.save로 저장한 외부 행에는 origin이 생기지 않으며 자동 대상에서도 제외된다.

Google에 연결된 상태로 시작한 기록은 그 UID에 영구 결속한다. guest로 시작한 기록은 완료 checkpoint에서 그때 연결된 Google UID를 빈 owner에만 결속한다. 첫 완료 시도의 owner snapshot을 retry 동안 유지한다. 미완료 guest를 periodic backfill이 선점하지 않도록 claim은 완료 행만 다룬다. Google에 연결되지 않은 완료 기록은 첫 연결 후 backfill에서 한 번 결속한다. 다른 계정의 origin을 새 계정으로 바꾸는 SQL은 없다.

완료 summary와 원본의 origin/ACK fingerprint/억제 표식이 durable queue다. 아직 ACK가 없으면 process restart 뒤에도 pending이다. CloudSummaryBackupWorker는 다음 두 경로로 깨운다.

1. Application.onCreate: 연결 조건이 있는15분 periodic reconcile를 KEEP으로 등록한다. 등록 자체는 Auth를 만들거나 로그인하지 않는다.
2. RunRepository: 최종 완료 checkpoint commit 뒤 즉시 one-time enqueue한다. ViewModel이나 lastFinished collector가 없어도 호출된다. 스케줄 실패는 GPS 저장·기존 wallet credit을 막지 않으며 periodic가 복구한다.

새 Google 연결·저장된 Google 세션 복귀도 AppViewModel cloud collector에서 one-time backfill을 요청한다. one-time REPLACE는 깨우기 요청만 교체한다. 원래 pending 행은 Room에 남으며, 오래된 네트워크 backoff가 새 완료 기록을 가로막지 않도록 한다. Worker는 configured/실제 Google provider/현재 UID를 확인한 후 DB를 연다. guest/계정 불일치/설정 없음에서는 업로드하지 않는다. 별도 worker 프로세스를 선언하지 않았다.

Worker가 쓰는 Application 공유 runDatabase DAO는 고정 summary 필드만 읽는다. pointCount는 migration 및 최종 완료 transaction에서 한 번 계산해 origin에 보관한다. UI/Worker의 query는 track_points를 구독하지 않으며 진행 중 GPS checkpoint마다 과거 포인트 수를 다시 세지 않는다. RunRepository를 초기화해 eager 경로 배열·wallet credit flow를 돌리지 않는다. 같은 DB 인스턴스를 UI와 공유해 ACK 상태의 invalidation도 전달한다. 한 번에50개의 pending을 처리하고 성공한 batch의 잔여는 다음 one-time work를 APPEND하여 지수 backoff 없이 계속한다. 실제 네트워크/SDK retry에서는 첫 실패 뒤 batch를 멈춰 불필요한 미완료 Task50개를 만들지 않는다.

## UID·재전송·삭제

FirebaseCloudAccount는 Activity/ViewModel이 없어도 Auth listener를 갖는다. lease는 accepted Google UID와 session epoch에 묶인다. interactive login/로그아웃/다른 UID/동일 UID 재로그인 뒤 과거 lease를 채택하지 않는다. worker는 queue의 expected UID를 현재 계정으로 바꾸지 않는다. 각 remote action 앞·transaction 재시도 본문·응답 뒤에 lease를 다시 확인하고, ACK SQL도 고정 UID와 suppressed=false를 요구한다. UI의 Room 관측은 해당 cloud 상태 객체가 여전히 최신일 때만 적용한다.

온라인 Firestore transaction은 기존 문서를 먼저 읽는다. 동일한 검증된 요약이면 쓰지 않고 ACK하며, 같은 runId에 다른 내용/잘못된 schema가 있으면 덮어쓰지 않는다. 형식 오류도 conflict로 분류한다. conflict는 자동 재시도에서 제외하고 ‘확인 필요’로 보여 주며 명시적 재시도에서만 다시 확인한다. 새 문서는 기존 summary whitelist와 serverTimestamp만 저장한다. RPC 완료만으로 다른 계정의 목록/ACK/소유권을 바꾸지 않는다.

await timeout/worker 취소가 Firebase의 실제 Task를 취소하지는 않는다. 실제 미완료 Task를 runId/UID별로 유지하며 새 동일 run 쓰기를 겹치지 않는다. 클라우드 삭제 전 Room에 억제 표식을 먼저 저장하고 이 client의 해당 run을 revoke한다. 아직 살아 있는 이전 Task가 있으면 완료를 기다린 뒤 삭제한다.15초 안에 확정되지 않으면 삭제 완료로 표시하지 않고 재시도를 안내한다. 삭제 뒤 이 기기의 periodic가 해당 요약을 재생성하지 않는다. 기기 기록과 포인트는 그대로다.

이미 전송된 요청의 서버 반영을 로그아웃이 롤백한다고 보장하지 않는다. 다른 기기에 아직 ACK되지 않은 오래된 queue까지 영구 삭제하려면 서버 tombstone 등 별도 운영 규약이 필요하다. 이번 수정은 다른 기기의 대기열을 제어하거나 새 서버 Rules를 게시하지 않는다. 로컬 ACK/고정 UID·현재 기기 삭제 억제의 범위로 기록한다.

## UI/UX

frontend-design 지침을 적용해 자동 보관이 기본이라는 짧은 상태 문구를 기록 상세와 설정에 둔다. 보관이 끝난 기록에는 누를 백업 버튼이 없다. 대기/실패에만 보조 TextButton ‘자동 백업 다시 시도’를 남기며 기존 record/UID confirmation fence를 유지한다. 완료/대기/충돌/제외를 server ACK·Room 값에 맞춰 보여 준다. fake UI 상태를 실제 서버 성공이라고 설명하지 않는다.

새 장식 카드/gradient를 추가하지 않았다. 현재 앱의 Chalk #F7F9FA / Paper #FFFFFF / Ink #24343D / Muted #5B6D73 / Ocean #356F78 / Mist #E7EFF1 역할과 기존 body/label typography를 따른다. 정렬은 기존 기록/설정 본문 흐름을 유지하고48dp 조작 영역을 보존한다. 사용자에게 WorkManager/UID/fingerprint를 노출하지 않는다. ‘전체 기록·경로·옷장 복원’으로 쓰지 않는다.

## 검토와 검사 준비

독립 소스 검토자는 malformed document가 일반 RETRY로 남는 경계를 지적했다. auto transaction에서도 parse 실패를 MalformedSummary로 좁혀 conflict/자동 중지로 교정했다. guest 완료 owner 정책을 선택하면서 claim을 완료 행으로 제한하고 대응 검사 기대값도 함께 고쳤다. 최종 UID/epoch·동일요약 ACK·실제 Task/삭제·Room·VM 없는 scheduling 경계에서 추가 명확한 P0/P1은 발견하지 못했다는 소스 검토를 받았다. 이는 실행 성공이 아니다.

준비한 meaningful 검사 선언은 다음과 같다. 아직 실행하지 않았다.

- JVM CloudBackupReconcilerTest11: guest/old queue, backfill/외부 origin 제외, coordinator 재생성 후 retry, 늦은 ACK/계정 전환·동일 UID 재로그인, claim 경합, 삭제 억제, conflict 중지, batch 잔여, cancellation, summary-only fingerprint.
- Android CloudBackupPersistenceTest6: 실제 임시 v1 SQLite→Room2 migration의 완료/미완료/좌표/잔액 필드 보존, reopen 뒤 owner/pending/ACK, foreign·late ACK와 삭제 억제, VM 없는 완료 후 enqueue hook, guest 완료 결속·기존 owner 유지, 완료 쓰기 실패 뒤 첫 UID를 유지한 재시도/commit 전 enqueue 금지. 테스트는 자기 UUID 이름의 임시 DB만 사용하고 원본 DB/Auth를 reset하지 않는다.
- RunRecordScreenUiTest 추가1: 자동 상태가 WAITING→SAVED/EXCLUDED로 바뀔 때 필수 수동 백업 없이 표시하고 해당 버튼을 없애는 fixture. 기존 record/UID stale callback 검사도 유지한다.

기존 RunDao.saveCheckpoint의2인자 API와 fault-injection 경로는 보존했다. Google owner가 있는 완료만 별도 atomic saveOwnedCompletion을 쓴다. 기존 ‘최초 생성 commit 뒤 ACK 실패에도 같은 runId 복구’ 검사는 새 atomic createLocal 경계에서 commit-then-throw하도록 주입 위치만 옮겼다. 동일한 복구 assertions/조건은 유지한다.

아직 확인하지 않은 범위: Kotlin/KSP/Room migration 실제 실행, 새 JVM/lint/Android 검사, WorkManager 실제 background/재시도와 일반 process 재시작, 실제 Google·운영 Firestore transaction, 휴대폰/Doze/OEM/API31+, 계정 전환·삭제의 실기기 경합. OS force-stop 뒤 자동 실행이나 즉시 전송 SLA를 보장하지 않는다. Root가 새 빌드/기기 검사와 각 원문을 별도 기록해야 한다.
