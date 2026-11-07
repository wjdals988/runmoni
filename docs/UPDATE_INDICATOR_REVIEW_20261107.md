# 우상단 업데이트 표시 겹침 수정 — 2026-11-07 한국 시간

사용자 보고에 따라 새 버전 점을 버전 글자 바깥에 띄우던 배치에서 버튼 내부의 독립된 공간으로 옮겼다. 버전과 점 사이8dp·점6dp를 확보하고48dp 조작과 좁은 화면의 기존 별도 행·설정 버튼을 유지했다. 업데이트 상태는 버튼에서 한 번 안내한다. 버전을 누른 뒤 명시적으로 선택해야 다운로드 링크가 열리는 흐름은 같다.

대상은0.3.8-dev/code20의 로컬 TEST 서명/Firebase 미설정 debug다. 공개0.3.7-dev/code19의 실제 출처aa9f4e2와 별개이며 새 서명CI/APK/운영 결과는 실제 공개 후 게시 문서에 기록한다. 원화·DB/기록·포인트·계정·Rules·키·의존성은 변경하지 않았다.

## 실제 검증

- [build1](app-review/2026-11-07-update-indicator/binary-build1.json)의 새 JVM115/실패·오류·skip0, debug lint오류0/경고39를 확인했다. [test-only build2](app-review/2026-11-07-update-indicator/binary-build2.json)는 제품 APK 바이트가 같으며 JVM/lint UP-TO-DATE로 그 근거를 유지한다. 새115회를 추가로 실행했다고 세지 않는다.
- [기본9](app-review/2026-11-07-update-indicator/android-review3.json)+[실제320dp·글씨2배 native2](app-review/2026-11-07-update-indicator/native1.json)=11개 메서드가 같은 build2 앱/test pair에서 통과했다. 일반5탭·점 on/off·신뢰하는 새 버전·오류/이전 버전·확인/취소/선택 후 링크를 검사했다. 분리 컴포넌트의 font1/2와 LTR/RTL, 실제 native320/font2의 LTR/RTL을 구분한다. RTL은 CompositionLocal 방향 전환이며 OS 언어 변경 검증은 아니다.
- 점과 버전 글자 사이 최소4dp 이상, 점 전체의 버튼 내부 포함, 설정 버튼과의 비겹침, 버전 단일행의 실제 줄 경계를 확인했다. [측정12행 요약](app-review/2026-11-07-update-indicator/visible-version-layout-summary.json)과 각 group JSONL 원문을 보존했다. 내부 줄 경계는 모든 부모 clipping/실제 glyph 픽셀을 직접 증명하지 않으며 선택 화면을 별도로 보았다.
- 원래 전체 설정XML/프로필·소유한12설정키/WM·night mode·권한/flags·관찰 전원 상태를 복원했다. Android 전체 OS 동일성을 주장하지 않는다. [실행 뒤 별도 소스 대조](app-review/2026-11-07-update-indicator/post-runtime-source-verification.json)는 원래 수집기의 Before/After와 구분한다. 전용 AVD를 종료했다.

## 실제 Android 화면

모두 ComponentActivity의 실제 Android UI 픽스처다.99.0.0-dev는 새 버전 표시를 위한 합성 정보이며 공개99 릴리스가 아니다. MainActivity·공개 서명APK·실기기·Google 성공 사진이 아니다. [루트 시각 검토](app-review/2026-11-07-update-indicator/root-visual-review.json)는 직접 본 유효5장만 대상으로 하며 사용자 시각 승인은 false다.

- [일반 홈](app-review/2026-11-07-update-indicator/android-review3-version-shortcut-home-fixture.png)
- [실제 OS 글씨2배/LTR](app-review/2026-11-07-update-indicator/native1-update-header-native-320-font2-ltr-fixture.png): 버전 전체/점/설정이 분리된다. 미니미 준비 표시가 남은 순간이며 원화 준비 완료 증거로 사용하지 않는다.
- [실제 OS 글씨2배/RTL 방향](app-review/2026-11-07-update-indicator/native1-update-header-native-320-font2-rtl-fixture.png)
- [다운로드 확인](app-review/2026-11-07-update-indicator/android-review3-version-shortcut-confirmation-fixture.png)

설정 촬영 파일은 실제 본문 픽셀이 홈과 같아 설정 화면의 근거/갤러리에서 제외했다. 초기 빈 화면 판독은 정정했다. 원인은 확정하지 않으며 직전 장면 캡처 가능성을 제품의 지속적인 빈 화면 장애로 바꾸지 않는다. 앱정보 스크롤/오류 픽스처1장은 감사 범위에만 포함한다.

## 실패와 한계

첫 android-review1은 복사한 수집기의 코드19 상수 때문에 제품검사/변경 전에0개에서 중단됐다. [현재20 실제 관측과 도구 수정](app-review/2026-11-07-update-indicator/collector-version-code-correction.json)을 남겼고 실패 원문을 바꾸지 않았다.

android-review2는8개 중 새 raw hasVisualOverflow 기대1개가 실패했다. 그 그룹의7개 성공은 최종11에 합산하지 않는다. 같은 그룹의 다른 통과 검사에서 size56×18/문단428×18/실제 줄0..56·bottom18이 관측됐지만 정확한 실패 순간의 수치라고 하지 않는다. 기존 noTextOverflow와 같은 실제 줄 경계/1물리px 반올림·줄 수 초과 금지로 판정을 통일하고 진단 로그를 추가했다. 제품을 바꾸지 않은 [관측·수정 기록](app-review/2026-11-07-update-indicator/android-review2-text-layout-observation.json)을 보존했다. 새11은 그 수정 후 전부 새로 실행했다.

부팅의 SystemUI 응답 지연은 앱 검사 전 AOSP/TCG 환경 관측이며 앱 문제 해결로 표시하지 않는다. 실제 휴대폰·Google·운영 쓰기·야외GPS·장시간 Doze/OEM·Android12이상·공개 서명APK 실행·사용자 시각 승인은 미확인이다. 이전0.3.7의 BG2/Android63과 이번11을 합산하지 않는다. 대화상자 배경 구분/일부 한글 줄바꿈 P2는 유지한다.
