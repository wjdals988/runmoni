# 러닝 옷장 벤치마킹과 적용 — 2026-10-25

공식 문서 4개 제품을 비교했다. 실제 계정으로 앱의 모든 화면을 조작한 비교는 아니며, 아래 기능 사실과 런모니의 설계 판단을 구분한다.

| 제품·공식 근거 | 확인한 기능 | 런모니 적용 판단 |
|---|---|---|
| [Zwift Customizing Your Avatar](https://support.zwift.com/en_us/customizing-your-avatar-SJhtYrj3F), [Unlocks in Zwift](https://support.zwift.com/en_us/unlocks-in-zwift-SkPReVYTZg) | Garage에서 외모·장비 변경, 선택한 성별과 무관하게 모든 머리/헤어 제공, 러닝 셔츠·쇼츠·신발 장비 존재 | 러닝 장비 중심, 헤어와 의상 선택에 성별 잠금을 두지 않음. 모든 체형·행사에 대한 호환을 검증했다는 뜻은 아님 |
| [Bitmoji Change my Outfit](https://support.bitmoji.com/hc/en-us/articles/360037603511-Change-my-Bitmoji-Outfit) | 원피스·상의·하의·신발·양말·겉옷을 개별 선택. 일부 프리셋은 개별 꾸미기 불가하고 개별 의상 선택 시 프리셋 해제 | 상하의·양말·신발 분리. 특수 코스튬이 덮는 부위의 일반 옷을 선택하면 코스튬 해제 |
| [ZEPETO Category Guidelines](https://docs.zepeto.me/studio-guide/category-guidelines), [Wrong Category](https://support.zepeto.me/hc/en-us/articles/6666949800345--Studio-Item-It-was-rejected-due-to-Wrong-Category) | 헤어·헤드웨어·아이웨어·주얼리·의류 구분. 양말은 Low Cut부터 Knee High·Thigh High 등 길이별 분류. 다른 분류 아이템의 묶음과 외모 변경 아이템을 구분 | 겉으로는 액세서리 한 탭, 내부는 귀·눈·손목·허리·몸통 장비 5슬롯. 양말 4선택(발목·크루·니삭스·없음). 분류 규칙 자체를 복제한 것이 아님 |
| [Roblox Avatar](https://create.roblox.com/docs/avatar), [AccessoryType](https://create.roblox.com/docs/reference/engine/enums/AccessoryType), [Attachment specifications](https://create.roblox.com/docs/avatar/layered-accessories/specifications) | 액세서리 부위와 의복 타입을 구분하고 부착점·레이어 구조를 명시 | 얼굴·목·어깨·골반·관절의 공통 좌표를 유지. 부위별 1아이템, 부위가 다르면 동시 착용. 이 제한은 런모니의 규칙이며 Roblox 전체 제한을 설명하는 것이 아님 |

## 이번 앱의 확정 구조

- 표시 탭 9개: 헤어 / 모자 / 얼굴 / 상의 / 하의 / 세트 / 양말 / 신발 / 액세서리.
- 내부 선택 슬롯 13개: hair, headwear, face, top, bottom, costume, socks, shoes, ear, eyewear, wrist, waist, hydration. 기존 accessory는 과거 저장 데이터 호환용으로 유지한다.
- 액세서리 탭 아래 귀 장비 / 선글라스 / 워치 / 러닝 벨트 / 수분 조끼 5개 분류. 모자·이어폰·안경·워치·벨트·조끼를 함께 선택할 수 있다.
- 상의 6종: 싱글렛·반팔·긴팔·바람막이·스포츠 브라탑·크롭 기능성 티. 하의 6종: 기본/그린/코랄 쇼츠·타이츠·바이커쇼츠·스플릿 쇼츠.
- 체형을 이미지마다 다르게 잘라 붙이지 않는다. 얼굴·목·몸을 같은 리그로 그리고 헤어·의상·장비를 올린다. 양말은 다리를, 워치는 손목을, 조끼는 몸통을 따라 움직인다.
- 일반 스타일에는 성별 잠금을 두지 않고 의복 디자인으로 차이를 제공한다. 브라탑·바이커쇼츠는 실제 운동복 용도의 디자인이다.

## 세트는 특수 의상

사용자가 일반 러닝복 묶음 대신 재미있는 특수 복장을 요청해 변경했다. 산타 / 스포츠 원피스 수영복 / 스포츠 비키니 / 곰인형 / 오리지널 골든 마스코트 5종이며 세트 없음까지 6선택이다.

| 세트 | 덮는 일반 부위 | 유지되는 선택 |
|---|---|---|
| 산타·곰인형·골든 마스코트 | 모자·상의·하의·양말·신발 | 얼굴·헤어·귀·선글라스·워치·벨트·조끼 |
| 수영복·비키니 | 상의·하의 | 얼굴·헤어·모자·양말·신발·기타 장비 |

덮인 일반 의상 데이터는 삭제하지 않아 세트 해제 시 돌아온다. 덮인 부위의 일반 아이템을 선택하면 세트가 해제되고 그 부품을 착용한다. 같은 위치 두 개를 동시에 보이게 하지 않는다. 새로운 계절 복장·완주 보상으로 확장하기 쉽지만, 새 의상마다 모든 포즈의 겹침을 검토해야 한다.

피카츄 같은 특정 IP의 앱 사용권은 확인하지 않았다. 이번 앱에는 해당 캐릭터 원화를 넣지 않고 짧은 둥근 귀·자체 가슴 배지의 오리지널 황금색 마스코트를 사용한다.

## 제작 기준

[Aseprite](https://github.com/aseprite/aseprite)의 레이어·프레임·스프라이트 출력 개념을 참고했다. 실제 앱은 기존 코드 기반 MiniMi를 확장한 네이티브 픽셀 렌더러이며 Aseprite 실행/컴파일이나 .aseprite 원본 제작을 수행했다고 표시하지 않는다. 예전 생성형 시안의 얼굴 포함 이미지를 잘라 붙이는 방식은 앱에 사용하지 않는다.

전체 앱의 토큰·조작·접근성 기준은 [DESIGN.md](../../../../DESIGN.md)의 기존 기준을 따른다. 상단의 큰 캐릭터와 재생 버튼은 고정하고 목록만 스크롤한다. 아이템이 많아지면 탭을 더 늘리는 대신 액세서리와 같은 분류 안에서 확장한다.
