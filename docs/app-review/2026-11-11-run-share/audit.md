# 러닝 결과 이미지 공유 사전 소스 검토

이 문서는 `/root/run_share_audit`의 읽기 전용 검토다. 빌드·테스트·AVD·네트워크·다운로드·Git 변경·앱/운영 데이터 변경은 실행하지 않았다. 허용된 WORK 문서만 기록했다. 본문 검증 항목은 권고이며 통과 결과가 아니다.

## 현재 구조

- `ui/RunRecordScreen.kt`: 루트 독립 페이지, `safeDrawing ∪ ime` 단독 소유, 고정64dp 헤더/48dp 뒤로와 세로 스크롤 본문. `key(record.id)`의 `rememberScrollState`는 editor로 페이지를 교체할 때 별도 보존이 필요하다.
- `ui/RunmoniApp.kt`: `selectedRecordId`가 있을 때 메인 하단 탭을 구성하지 않는다. `selectedRecord`는 canonical `state.records` 뒤 `recordSnapshot` fallback을 사용한다. fallback은 편집/공유할 현재 기록의 존재 증거로 삼으면 안 된다.
- 상세의 계정/삭제/GPX 콜백은 `pageAlive`, 현재 entity/context, 최신 busy/actions를 확인하고 `RecordActionSnapshot`으로 실제 오래된 람다 회귀 검사를 지원한다. 새 share 버튼에도 동등한 late-event 경계를 두는 것이 좋다.
- `AppViewModel.kt`의 기존 GPX는 `cacheDir/exports/run-{startedAtMillis}.gpx`와 기존 FileProvider를 사용한다. `MainActivity.kt`는 `UiRequest.ShareFile`을 GPX MIME으로 고정한다.
- `res/xml/file_paths.xml`: `<cache-path name="exports" path="exports/" />`; 새 공유 이미지는 이 하위 전용 폴더를 쓰면 provider 범위 확대가 필요 없다.
- 승인 미니미는 `ui/MiniMi.kt`의 `avatarBitmap`→`ApprovedAvatarRenderer.renderForDisplay`. 거절된 `AvatarSpriteRenderer`나 새 몸체 생성으로 교체하면 안 된다.

## P0: 진입·상태·저장 경계

1. 결과와 editor는 한 번에 하나만 구성한다. 뒤에 결과 UI/BackHandler를 그대로 쌓으면 숨은 동작·IME·하단 영역이 겹친다. result 전용 SaveableStateHolder 또는 상위 scroll hoist로 editor Back 시 원래 상세의 스크롤을 복원한다.
2. 공유 진입의 실제 클릭 시 현재 canonical records에서 같은 완료 기록이 존재하는지 다시 확인한다. 오래된 snapshot/제거된 기록/준비 전 데이터로 새 공유를 시작하지 않는다. editor 중 기록 변경·이탈에는 비동기 preview/load/export 결과를 ticket으로 무효화한다.
3. preview·export는 같은 record/draft/photo/AvatarStyle snapshot을 써야 한다. 가장 안전한 방법은 현재 보이는 완성 Bitmap을 공유/저장하는 것이다. 렌더/사진 로딩 중에는 준비 완료 전 공유를 막고, 실패는 값/직전 유효 사진을 유지하는 inline 안내로 보인다.
4. Photo Picker/SAF content URI를 읽고 이미지 방향/크기를 bounded decode한다. 추가 READ_MEDIA/READ_EXTERNAL_STORAGE 권한을 요구할 이유가 없다. provider decode/permission 실패·취소는 앱 크래시나 기존 사진 소실로 바꾸지 않는다.
5. 사진을 Bitmap→새 PNG로 합성하면 원본 EXIF/GPS metadata는 복사되지 않는다. 사진 픽셀 자체의 장소나 사용자가 켠 날짜/닉네임까지 숨긴다고 표현하면 안 된다. 원본 URI·경로·EXIF·raw GPS·UID·원본recordId는 공유 Intent/텍스트/파일명에 넣지 않는다. 파일명은 UUID 기반으로 한다.
6. MediaStore 갤러리 저장은 API29+ scoped storage와 IS_PENDING own row를 쓴다. URI 획득/stream쓰기/close/commit/실패delete를 같은 IO try/finally 소유 범위에 두어 `withContext` 반환 시 cancellation 때문에 URI를 잃어 orphan row가 남지 않게 한다. 전체 저장을 NonCancellable로 감싸지 말고 cleanup만 보호한다. commit 완료 후 UI acknowledgement가 취소되더라도 이미 저장된 파일을 실패로 삭제하지 않는다.
7. 공유는 `ACTION_SEND image/png`, `EXTRA_STREAM`, 같은 content URI의 `ClipData`, 임시 READ grant만 필요하다. 기존 GPX MIME/흐름을 바꾸지 않고 새 타입 또는 명시 MIME을 구분한다. prefix/write/persistable grant는 필요 없다. chooser를 열었다는 사실을 SNS 전달 완료로 표시하지 않는다.
8. 파일 준비 후 화면 이탈/다른 기록/앱 BG 뒤 늦게 chooser가 열리지 않도록 page/ticket/lifecycle를 확인한다. launched chooser용 캐시 파일은 화면 dispose 직후 삭제하지 않는다. cleanup은 새 기능이 소유한 오래된 파일/실패한 own MediaStore row만 처리한다.
9. 승인 renderer의 avatar Bitmap은 공유 캐시 객체일 수 있다. 변경하거나 recycle하지 않고 pixel filtering 없이 그대로 그린다. UI 입력 크기 확대는 유지하되 출력 이미지의1080px typography와 system fontScale은 별개임을 명확히 한다.

## UX 권고

결과의 거리/시간 근처에 ‘기록 이미지 만들기’ 진입을 두고, 경로 GPX/삭제와 분리한다. editor는 미리보기→배경/기록/미니미의 간단 탭→고정 저장/공유를 사용한다. body 하나만 세로 스크롤하고 헤더/48dp 뒤로를 고정한다.320dp/font2와 IME에서는 footer 행동을 세로 재배치해 잘림을 피한다. 사진/색을 택하지 않아도 기록+미니미만으로 저장할 수 있게 하고 guest도 로그인을 요구하지 않는다. 개별 pill/카드 반복보다 짧은 라벨·선택선·간격을 쓴다. 출력 필수 수치에는 말줄임/축소로 조작한 값을 넣지 않는다. caption 상한은 UTF16 surrogate를 자르지 않도록 한다.

## Root 실행 검증 권고

- JVM: cover-crop 빈 경계 없음, pinch/drag clamp/ratio 변경, 날짜숨김, 0거리·0시간 pace—, caption codepoint/control boundary. 기존 기록의 elapsed default0을 새 제약으로 거절하지 않는다.
- Android renderer/storage:1080×1350/1920, 사진/승인 미니미 합성, preview와 저장 동일 Bitmap, PNG EXIF 없음/원본 사진 hash 불변, MIME/ClipData/READ single URI grant, IS_PENDING 실패·취소 시 own row 삭제와 commit 완료 판정.
- UI:320/font2·IME를 포함해 뒤로/저장/공유48dp, 각 탭의 마지막 조작 접근, 결과→editor→Back 원래 스크롤·하단탭 부재, old share callback 재생/기록 변경·삭제, picker 취소/오류 후 입력 유지, 중복 tap/로딩/이탈 gate.
- 실제 Main: 완료 기록→editor→선택/꾸미기→실제 MediaStore 저장 URI/PNG크기·hash조회→시스템 chooser 열기/취소→상세 Back. chooser와 제3자 SNS 전달 성공, emulator와 실기기는 구분한다.
- 사진은 synthetic color/EXIF JPEG fixture를 쓰고, 실제 사용자 사진/경로·원래 갤러리 항목을 지우거나 공개 증거로 사용하지 않는다. cleanup은 검증이 만든 URI만 한다. 실제 OS picker 선택과 테스트 injected URI는 별도 표시한다.
