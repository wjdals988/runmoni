# ar6 후처리 실패와 관측기 예외 검토

검토일: 2026-11-16 UTC. 이 WORK 문서만 작성했다. 장치·Git·빌드·네트워크·제품/검사/관측기 소스·기존 proof JSON은 변경하거나 실행하지 않았다.

## 최종 판단

**특수 observer 예외를 validator에 추가하지 않는다.** Root의 최종 결정에 따라 같은 final input3에서 수정된 observer로 ar7 loading4와 ar8 나머지3그룹12를 각각 새로 실행해 완료된 근거를 얻는 쪽을 채택한다. 이 문서는 미래 실행 성공을 판정하지 않는다.

ar6의 제품 raw와 복원 근거는 보존 가치가 있지만, false parent/누락 sourceMapsAfter를 예외로 해석할 필요가 없는 새 실행이 더 명확하다. ar6를 최종 제품 통과 수에서 제외하고, 새 ar7/ar8이 whole PASS·정상 source/복원 기록까지 완료했을 때 그 실행을 채택한다. 원래 ar6의 passed/actual counter/필드 존재 여부는 고치지 않는다.

## ar6에서 실제 확인한 사실

기준 경로는 `docs/app-review/2026-11-16-pixel-couture/`다.

- `android-review6.json` SHA256: `46033627873eb9e345baf041f6ecc8a4bc9379522dcc340cd8d99e0573cde04f`
- completed=true / passed=false / plannedProductMethods=16 / actualPassedProductMethods=12
- failureType=`AssertionError`
- 정확한 failure: `Only actual static-context readiness metrics may be exported`
- source-freeze3 SHA256: `e715f1b1e292bc861ae14dc3c0176df1d9e4528583292e76e534b63b952824d8`
- binary-build3 JSON SHA256: `26150f58714b9cbd848cc512bf253e5672a452ffce234e47b7e08cb3d8a9a6e2`

실제 installed app/test pair는 binary-build3의 두 SHA와 일치한다. 각 제품 raw의 SHA와 `OK (N tests)`를 직접 확인했다.

| ar6 그룹 | raw actual | raw SHA256 | 최종 scope 채택 |
| --- | ---: | --- | --- |
| kit-renderer | 4 | 9336213c9741eac54756564cd30e640655e1ff869434575cc73bbcd4e8cf2b0d | ar8 새 실행으로 대체 |
| bounded-cap-renderer | 1 | 46bf62bfe56182a3c5499364d93ceacbe2d603d77da8c2e8a240d9f87eb3a51d | ar8 새 실행으로 대체 |
| avatar-identity-playback | 7 | 7361fd8a77f6c2870b8731a6fc14ddf2c57735914ec22e196ad5d2b2c60c2718 | ar8 새 실행으로 대체 |
| avatar-loading | 4 | 703d284a8af5722b1d5b213a653357fd302c146940bb001ba68e243faf0b885d | ar7 새 실행으로 대체 |

즉 제품 instrumentation raw에는16개 전체 OK가 있다. 그러나 마지막 loading 그룹의 후처리에서 예외가 났고 aggregate counter 갱신 전이므로 JSON에는12가 남았다. 이를16으로 수정하거나 이 차이만으로 raw4를 추가 채택하지 않는다. 마지막 loading 그룹에는 retained 앞선 그룹들과 달리 originalProfilePreserved 필드도 기록되지 않았다. 전역 finally 복원 성공과 개별 그룹 완료 단계를 섞지 않는다.

원래 sourceMapsAfter는 **존재하지 않는다.** sourceMapsBefore와 sourceMapsAfterRestoration은 product115/test100/res34/avatar83/approved1/config11의6개 범주가 모두 frozen input3와 일치한다. driverSourceUnchanged/observerHelperSourceUnchanged/binaryEvidenceUnchanged, preferences/profile/communityAccount 및 controlled settings/permissions/power 복원은 모두 true이고 restorationCommandFailures는 빈 배열이다. 이는 실제 마지막 복원 뒤 상태가 일치한다는 근거이며, 존재하지 않는 sourceMapsAfter checkpoint를 생성했다는 의미는 아니다.

## 후처리 원인 근거와 수정 범위

ar6 실행 driver SHA256은 `40d874eb8ea2be319b26a6c4fc7ef364fc96b8f7a43abf05b6c9534aaeb7350e`다. 해당 finally는 제품 raw를 이미 읽어 actual/passed/status를 채운 뒤, readiness metric을 export하기 전에 다음 형태만 허용했다.

`context: waitMillis=<integer>; timeoutMillis=120000`

실제 frozen input3의 공통 text audit은 `awaitAvatarReady=true/false; `를 함께 기록한다. 특히 닫힌 I/O gate를 감사하는 intentional-loading 호출에는 false가 명시돼 있다. 제품 검사가 완료된 뒤 새 라벨을 옛 regex가 거절하는 순서와 정확한 failure가 일치한다. 이는 ar1~ar4의 이전 제품/준비시간/검사 gate 문제를 모두 같은 원인으로 바꾸는 근거는 아니다.

ar7 실행 driver SHA256 `bea5eb6cbbca01dca9816539d814db73020ba211ddd279d869c9e9007bea0ec1`와 ar6 driver의 텍스트 차이를 직접 비교했다. 차이는 readiness regex 한 줄뿐이며 optional literal `awaitAvatarReady=(true|false); `를 허용한다. 기존 길이/정수/time limit·제품 assertions·제품 실행 timeout·source/설치/복원 guard는 바뀌지 않았다. observer helper 자체와 제품/test APK의 동일성은 새 완료 proof에서도 확인해야 한다.

false metric은 intentional-loading text audit에서 readiness 대기를 하지 않았다는 뜻이다. 이를 로딩 준비 완료나 startup SLA 통과로 바꾸면 안 된다. 수정된 observer는 라벨을 포함한 raw를 그대로 남기며, arbitrary 문자열을 통과시키거나 readiness check를 전체 비활성화하지 않는다.

## 예외를 채택하지 않는 이유

처음 고려한 좁은 예외라면 ar6의 exact SHA/failure/최종 loading 제외·같은 final binary의 새 loading4 전체 PASS·before/afterRestoration6맵·모든 복원·관측 코드 불변을 각각 요구해야 했다. sourceMapsAfter의 부재를 afterRestoration 관측으로 대체했다는 한계도 별도 표시해야 한다. 모든 그룹 passed=true인 false parent를 보통의 ‘실패 그룹 제외’ 규칙으로 통과시키는 것은 부적절하다.

그 경로는 ‘관측기 실패라면 일부 증거 누락을 허용한다’는 일반 예외로 확대될 위험과, 후처리 실패를 제품16 전체 PASS로 발표할 위험이 있다. 특히 향후 다른 failure 문구/누락 복원/변경 APK에도 적용되면 실제 실패를 숨길 수 있다. 관측기가 제품 파일을 쓰지 않는다는 소스 검토와 복원 뒤 맵 일치는 유용하지만, 그 이유로 누락 checkpoint가 생겼다고 취급해서는 안 된다.

Root가 선택한 ar8의12개 새 독립 실행은 이 복잡한 예외를 없앤다. validator의 기존 false-parent/missing-field guard를 그대로 두고 완료된 정상 근거를 사용할 수 있다. ar6는 원래 false 상태의 진단 자료로 보존한다. 이 문서는 observer 예외 schema 구현을 승인하지 않는다.

## 최종 채택 조건과 계획 집합

계획은 ar7 avatar-loading4 + ar8 kit4/bounded-cap1/identity-playback7 + input1 sharePNG6 =22개다. 기존 planned method ID를 펼쳤을 때 앞선16과 share6의 고유 합22·중복0임을 확인했다. 이는 현재 method 집합의 산술이며 새 ar7/ar8 성공 주장이 아니다.

1. ar7/ar8 각각 completed/pass true, 실제 whole-group raw OK 및 expected=actual, 실제 method ID를 확인한다. ar6의12/16은 이 합계에 다시 더하지 않는다.
2. 두 새 phase는 binary-build3/source-freeze3의 동일한 실제 installed pair를 사용해야 한다. sourceMapsBefore/sourceMapsAfter/sourceMapsAfterRestoration6개 맵과 실행 source/binary 불변, 원래 데이터 및 controlled 복원·새 raw/driver SHA가 정상 기록돼야 한다. 새 실행에서 같은 누락이 생기면 이 문서로 면제하지 않는다.
3. input1 sharePNG6의 보존은 별도의 source-equivalence 검토와 Root 승인에 결합해야 한다. source1과3의 제품 해시가 전부 같다는 가정이나 이 observer 검토만으로 이를 채택하지 않는다. 서로 다른 입력22개를 동일 APK22개 통과로 표기하지 않는다.
4. 원래 ar6·이전 실패·관측 metric은 보존하고, 실기기/Google/공개 서명 APK runtime/사용자 시각 승인으로 확대하지 않는다.

이 문서 작성자는 ar7/ar8 실행·validator 변경·새 성공 JSON 생성·helper pin 변경을 수행하지 않았다.
