# 공유 이미지 구현 중 읽기 검토

`/root/run_share_audit`의 pre-freeze 소스 검토다. Root/다른 담당자가 아직 편집 중이며 이 문서가 최종 코드의 실행 성공을 뜻하지 않는다. 소스 수정·빌드·테스트·AVD·네트워크 요청은0회다.

## 읽은 범위

- ui/RunShareScreen.kt, ui/RunRecordScreen.kt의 새 shareAction, ui/RunmoniApp.kt의 결과/편집 전환
- domain/RunShareCard.kt, domain/RunShareAccess.kt
- sharing/RunShareCardRenderer.kt, RunSharePhotoLoader.kt, RunShareImageStore.kt
- test/domain/RunShareCardTest.kt(선언9개), androidTest/sharing/RunShareCardRendererTest.kt(선언3개), RunShareImageStoreTest.kt(선언4개)

## 확인한 경계

- 현재 공유 입력은 read-only whitelist DTO이며 raw recordId/GPS/UID/포인트가 없다. activeDuration 저장값을 사용하고 elapsed0/시계 역행 완료 기록을 새 제약으로 거절하지 않는다.
- Root의 실제 VM canShareRun과 RunShareAccess는 현재 canonical record 존재·완료·가용 상태·같은 출력 metrics를 확인한다. 결과 snapshot fallback이 공유 권한으로 쓰이지 않도록 별도 경계가 있다. 원래 결과의 SaveableStateHolder와 stale share callback 경계도 추가됐다.
- Canvas는 동일 완성 Bitmap을 preview/export에 사용하며 사진 crop overflow/pinch anchor를 제한한다. 미니미는 승인 renderer 입력의 투명 여백만 제거하고 nearest draw한다. 기존 사진·avatar 입력을 recycle/recolor하지 않는다. long caption/stat ribbon 영역을 피해 전체 sprite를 배치한다.
- PhotoLoader는 content URI만 읽고 private staging의 byte/header/decoded dimension을 제한한다. ImageDecoder의 방향 적용·software SRGB 출력 뒤 새 PNG 합성으로 원본 EXIF를 전달하지 않는다. staging은 FileProvider exports 범위 밖이며 finally 정리한다.
- ImageStore는 UUID 파일명과 기존 exports 하위 디렉터리, single content URI/ClipData/READ만 사용한다. own pending/gallery row와 own cache file을 획득 직후 추적해 handoff 실패/취소 시 정리한다. save의 isCurrent를 insert/publish/handoff 경계에서 다시 검사한다. MediaStore IS_PENDING0 이후라도 호출자 handoff가 취소되면 own row를 정리하는 ‘method handoff가 최종 commit’ 계약이다.
- 실제 chooser 열기를 전달 성공으로 표시하지 않는다. 기본 닉네임은 표시하지 않고 날짜/이름은 명시적 옵션이다. 출력파일명에 원본 기록시각을 넣지 않는다.

## 발견·전달한 보완

1. **복원된 URI의 사진 실패 후 clear 불가**: photo bitmap이 없으면 사진 빼기가 숨겨져 저장을 다시 할 수 없었다. Root가 `photoClearable`을 별도로 전달해 URI/error 상태에서도 제거할 수 있도록 보완한 소스를 확인했다. 아직 실행 결과는 없다.
2. **아주 짧은 IME 창**: 기존 고정 header/footer의 최소 높이보다 가용 높이가 작은 경우는 body-only compact 판정으로 해결되지 않는다. Root에게 footer 본문 스크롤 fallback 또는 해당 높이 검사를 권고했다. Native 실측 실패라고 판정한 것이 아니다.
3. **취소된 렌더 출력의 소유권**: withContext(Default) 결과 handoff에서 새8MB Bitmap이 채택되지 않으면 GC까지 남을 수 있다. 영구 누수/실측 OOM으로 판정하지 않았으며, Root에게 produced/adopted flag로 미채택 출력만 정리하는 작은 자원 보완을 권고했다. 현재 photo/avatar/채택된 preview bitmap을 recycle하면 안 된다.

읽은 domain/renderer/storage에서 추가 P0 차단 요소는 찾지 못했다. 위 내용은 소스 검토이며 9/3/4개 검사 선언은 Root가 실행한 통과 수가 아니다. 실제 OS picker 선택·MediaStore 동작·제3자 수신·실기기·성능/지원 여부는 실행 없이 추론하지 않았다.

## Root 요청에 따른 신규 검증 소스 작성

이후 Root가 소스 작성 범위를 명시적으로 추가했다. 신규 `androidTest/ui/RunShareUiTest.kt`4개 메서드와 `RunShareControllerTest.kt`1개 메서드만 작성했다. 기존 제품/테스트 파일은 변경하지 않았다.

- UI4:9개 폭/글씨 조합×3탭의 조작·TextLayout/48dp,320×240/font2의 footer스크롤과 고정Back, bitmap 없는 실패URI clear 회복, 실제 Root의 상세 스크롤→편집→Back/하단탭부재/실제 stable snapshot callback의 dispose·same-turn 기록 제거 차단.
- Controller1:실제 승인 renderer/editor/store+synthetic 완료 기록. AtomicBoolean=false는 재구성을 일으키지 않으므로 오래된 footer callback이 현재 authorizer를 보는지 확인한다. true 상태에서 같은 UI turn Save2회와Share1회를 재생해 실제 own MediaStore PNG1개(1080×1350·pending0)만 생성되는지 확인한다. dispose 뒤 true authorizer로 옛 콜백을 재생해 행/chooser 시도 증가가 없는지 확인한다.
- chooser는 잘못된 호출이 실제 OS UI를 열지 않도록 blocking Instrumentation ActivityMonitor를 두고0hits를 요구한다. 따라서 이 메서드는 실제 chooser 선택·SNS 전달 성공을 주장하지 않는다. Root의 별도 실제Main 동선에서 OS 공유창을 관측해야 한다.
- Controller finally는 기존 row집합을 보존하고 이 serial case동안 생긴 해당package/Runmoni앨범/UUID PNG의 ID delta만 지운다. cache도 기존basename을 보존한 새UUID PNG만 정리한다. 원래 갤러리·기록DB·지갑·Auth에는 쓰지 않는다.

이 문서 시점까지 컴파일·테스트 실행은0회다. 선언5개를 통과한 검사 수로 쓰면 안 된다. Root만 순차 빌드/실행하며 결과·실패/정리 여부를 실제 측정해야 한다. Controller/Root UI의 Asset 준비 대기 시간은 SLA가 아니다.
