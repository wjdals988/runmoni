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
