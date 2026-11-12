# ui-ux-pro-max 적용 근거

- 요청 스킬: https://github.com/nextlevelbuilder/ui-ux-pro-max-skill
- 확인 revision: `b7d5353e0130feb8c5574ef1251f8a1db1873ccb` (2026-11-06 한국 시간)
- [원본 SKILL.md](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill/blob/b7d5353e0130feb8c5574ef1251f8a1db1873ccb/.claude/skills/ui-ux-pro-max/SKILL.md), SHA256 `9aa50bd44076db28624ea976056d915905ab9357748f90263a004fa0ef087147`
- [native 체크리스트](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill/blob/b7d5353e0130feb8c5574ef1251f8a1db1873ccb/.claude/skills/ui-ux-pro-max/references/pro-rules.md), [Compose 데이터](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill/blob/b7d5353e0130feb8c5574ef1251f8a1db1873ccb/src/ui-ux-pro-max/data/stacks/jetpack-compose.csv)

Python 표준 라이브러리 도구·관련 CSV·reference19개/737272bytes만 순차 수신하고 Git blob SHA와 바이트 수를 대조했다. CLI/npm/전역 패키지 설치는 하지 않았다. 실제 검색 스크립트는 ignored `work/ui-ux-pro-max/vendor/scripts/search.py`에서 실행했으며 네트워크나 개인 데이터 없이 공개적인 제품 설명만 질의했다. upstream MIT 코드를 앱에 포함하거나 새 런타임 의존성으로 추가하지 않는다.

|실제 질의|결과/판단|
|---|---|
|`running fitness mobile --design-system`|Fitness 계열이지만 marketing Feature-Rich Showcase·주황/Barlow·CTA 반복을 추천. 기존 사용자 요청과 native 맥락에 맞지 않아 배색/레이아웃/폰트를 채택하지 않음|
|`running tracker minimal native --design-system`|한 번 더 좁혔으나 AI-Native/보라·product video로 잘못 연결. 검증된 제품 방향 일치를 찾지 못했음을 기록하고 자동 생성 결과는 MASTER로 저장하지 않음|
|`touch target accessibility --stack jetpack-compose`|2결과, contentDescription/semantics를 실제 Compose의 역할/선택·단독 아이콘에 적용|
|`insets adaptive layout --stack jetpack-compose`|3결과, 공통 layout 재사용/깊은 중첩 회피/토큰 테마를 적용. dark-mode 추천을 새 다크 테마 검증으로 바꾸지 않음|
|`focus not obscured --domain ux`|3결과는 Web WCAG 기준. Android 적합한 단위·API로 safe-area/키보드에 맞게 해석하며 WCAG 인증을 주장하지 않음|
|`safe area keyboard --domain web`|app-interface.csv의 native safe-area 결과를 확인. Android48dp·Compose insets로 적용, RN 예제 코드를 복사하지 않음|

자동 추천의 제품 방향 일치는 확인하지 못했다. 따라서 색/서체/스타일은 기존 DESIGN.md와 사용자 요청을 유지하고, 실제 적합한 Compose 결과 및 스킬 기본 native 접근성/조작/위계 원칙을 종합한 [MASTER](../../../design-system/runmoni/MASTER.md)를 작성했다. 검색 결과에 표시된 Compose 버전·Verified At는 upstream 메타데이터이며 런모니의 실제 의존성 버전이나 이번 사용성 측정이 아니다.

awesome-design-md의 기존 pinned Apple/Nike/Airbnb 참조는 유지한다. 새 스킬이 기존 승인 원본·기록/포인트/보유권·실제 APK 출처·서명키·Firebase 경계를 변경하는 권한을 주지 않는다. 실제 실행/실패/미확인/페이지별 결과는 CHANGELOG와 후속 검토 문서에 기록한다.

## 2026-11-11: 공유·위젯·지도 보완

기본1:1 공유의 위계, 밝은 위젯의 거리 중심 구성, 지도 바깥의 보조 조작을 적용했다. 실제 검색의 원문/재검색/공식 자료는 [공유 검토 근거](../../app-review/2026-11-11-run-share/README.md)에 보존한다. `glanceable widget information`의 일치는0건이었고 재검색도 제품 맥락에 맞지 않아 공식 Android 위젯 지침으로 보완했다. `contextual actions`의 NoGestureOnlyActions와 Compose typography/flat layout 결과는 적용했다. 결과에 기록된 upstream 버전/확인일을 런모니의 최신 시장 유행 측정이나 실제 의존성 업그레이드로 주장하지 않는다.

## 2026-11-12: 전체 화면 Studio 개편

기존 고정판을 유지해 시스템 검색/Compose typography/disclosure 재검색과 native 기본 체크를 적용했다. AI/보라·영상 홍보와 CTA 반복의 자동 시스템 추천은 채택하지 않았다. disclosure 재검색0결과를 검증된 일치라고 부르지 않으며 기존 native 기본 지침으로 보완했다. 디자인 계획/스킬 원문·실제 결과는 [새 검토 폴더](../../app-review/2026-11-11-studio-ui/README.md)에 보존한다. 현재 시장 유행이나 사용성 개선율을 측정한 결과가 아니다.
