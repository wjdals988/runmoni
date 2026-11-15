# 승인 원본 위의 세트·장비 호스트 검토

원본 PNG와 앱 사본의 SHA256은 `f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65` 그대로다. 아래 PNG는 **호스트 JVM에서 실제 생산 픽셀 코드를 실행한 출력**이다. Android 화면, 새 사용자 시각 승인, Aseprite 제작물이나 새 생성 모델 원화가 아니다. 거절된 v0.4 몸/관절/부품은 호출하지 않았다.

## 입력과 확인 범위

현재 `Models.kt`에서 `AvatarGender`/`AvatarStyle` 선언을 그대로 추출하고, 실제 `RunningKitColors.kt`, `AvatarCatalogue.kt`, `AvatarEquipmentLayers.kt`, `AvatarCostumeLayers.kt` 및 기존 Java 재질 클래스를 캐시 Kotlin2.0.21/JDK21로 컴파일했다. 새 패키지·네트워크·Gradle·AVD는 호스트 검사에서 사용하지 않았다. `work/companion-avatar-host/run-host.py`와 `VerifyCompanionAvatars.java`가 실행 경로다.

최종 픽셀 입력은 [host-avatar-checks-v6.json](host-avatar-checks-v6.json)의 해시로 고정했다. 세트 **48사례**(3세트×남녀×8셀), 장비 **272사례**, 세트+장비 **48사례**를 검사했다. 원본/fabric/coverage 배열, 실제 보호 마스크의 얼굴·머리·목·손, 의복 외 원래 몸 alpha와 프레임 래핑, 각 세트의 원본8포즈 유지, 앞팔 가림과 장비의 합성 alpha를 확인했다. 이 수는 Android 테스트 개수나 미적 점수가 아니다.

실제 앱 `ApprovedAvatarRenderer`/`WardrobeCoverage` 로더 연결, Android의 premultiply/합성 및 UI 선택은 이 호스트 실행 경로에 포함되지 않는다. 추가한 JVM 테스트3개와 기존 Android selector/렌더 검사 보강은 Root의 별도 앱 검증 대상이다.

## 반복에서 발견한 문제

- v1: 독립 hand-protection과 일부 fabric이 교차해 손 픽셀이 옷으로 바뀌었다. 기존 보호 PNG를 명시 입력으로 연결하고 모든 의복/장식에서 우선 제외했다. [원문](host-avatar-failure-v1.txt).
- v2: 기존 장비 Raster가 새 의복 확장부의 alpha를 원본 배경 fringe alpha로 되돌렸다. 원본에서 alpha>16인 실제 부착 면만 쓰고 합성 중인 alpha를 유지했다. [원문](host-avatar-failure-v2.txt).
- v3: 최초 직사각형 상단 보호 경계 검사에서 픽셀 변경이 검출됐다. 경계를 고정한 v4는 수치검사를 통과했으나 실제 천 어깨까지 보호해 민트 잔여를 남겼다. [원문](host-avatar-failure-v3.txt).
- v4: 수치는 통과했으나 직접 PNG를 열어 산타의 종이 같은 모자, 머리 옆의 큰 고리, 여자 민트 어깨 잔여를 발견해 **시각적으로 보류**했다. v4 PNG/JSON은 보존한다.
- v5: 이미 고정된 fabric1 천만 상단 색 변경을 허용하되 독립 보호 마스크로 다시 검사했고, 실제 목 픽셀의 잘못된 소매 coverage를 검출했다. 목 핵심 ROI를 추가로 보존했다. [원문](host-avatar-failure-v5.txt).
- v6: 기존 마스크를 넓히지 않고 실제 천과 보호된 정체성 영역을 구분했다. 수치검사와 직접 정지 이미지 검토를 마쳤다. 작업 중 Root의 diagnostic Gradle 시작 지시와 v6 호스트 실행이 교차했으며, 겹치지 않았다고 주장하지 않는다. v6 collector의 exit0을 확인한 후 추가 호스트를 실행하지 않았다.

실패·이전 후보 검사를 v6 성공 사례에 합산하지 않았다.

## 실제 열어 본 최종 호스트 출력

- [원본·3세트·장비 비교](host-costume-contact-v6.png)
- [산타 남녀16셀](host-costume1-v6.png)
- [곰 남녀16셀](host-costume4-v6.png)
- [골든 남녀16셀](host-costume5-v6.png)
- [장비와 기존 의복 남녀16셀](host-equipment-v6.png)
- [세트와 장비 동시 착용](host-costume-equipment-v6.png)

검토자와 Root가 최종 세트16셀/비교 이미지를 각각 열어 확인했다. v4의 큰 옆 고리와 어깨 잔여는 없어졌다. 산타는 **머리 뒤의 작은 접힌 모자**, 곰/골든은 **귀·크라운과 접힌 칼라가 있는 의복**이다. 원본 머리를 덮는 완전한 후드나 새 플러시 신체로 확대해서 설명하지 않는다. 같은 원본 얼굴·체형·포즈를 유지하며, 산타의 털 앞단/버클과 곰 배판/지퍼, 골든 귀 형태/별자수를 구분했다. 정지 셀 검토를 실제 재생 시간·움직임의 자연스러움 승인으로 표현하지 않는다.

기존 costume1/4/5만 개발 프리뷰에서 선택 가능하게 연결했다. costume2/3 수영복은 피부 underlay가 없어 숨김을 유지한다. 선택 가능 ID는 남43/여41이며, 전체58ID·13슬롯·9분류·0P·저장/보유권·포인트·부위 점유 규칙은 보존했다. 사용자 시각 승인과 실제 Android/휴대폰 결과는 별도다.
