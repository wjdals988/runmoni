# 러닝 공유 이미지 검토 — 0.4.3-dev/code25

사용자 요청: 러닝 결과에서 사진·저장된 기록·본인 미니미로 공유 이미지를 꾸민다. 결과→별도 편집→결과의 기존 스크롤을 유지한다. 4:5/9:16, 3색 테마, 사진 crop/zoom, 기록 상·하단/문구/날짜·닉네임, 미니미 표시/위치·크기를 제공한다.

## 출력과 개인정보

미리보기와 export는 동일한 완성 Bitmap이다. 완료된 canonical record의 whitelist DTO만 렌더에 넘기며 거리·active duration·pace·날짜만 들어간다. raw GPS·UID·recordId·포인트·원본URI/EXIF는 카드에 넣지 않는다. 닉네임은 기본 OFF다. 사진 입력은 OS picker이고 broad storage permission 추가는 없다. 공유는 기존 FileProvider의 해당 UUID PNG 한 장의 READ 권한이며 자동 SNS 게시는 없다.

새 renderer는 본인 현재 옷차림의 기존 ApprovedAvatarRenderer 프레임을 빌린다. 승인 원본·의복·58ID/13슬롯/9탭/보유·포인트·키/Rules/과거APK 출처는 바꾸지 않는다. 원본 f9035f29… 해시와 모든 asset을 동결표로 확인한다. 거절된 관절 조립 renderer를 재사용하지 않으며 새 원화 승인으로 취급하지 않는다.

## 설계 참고와 상호 검토

기존 MASTER와 awesome-design-md의 고정 Apple 분석을 참고했다(제3자 분석, 공식 Apple규격 아님). 캐시된 ui-ux-pro-max의 실제 사진 편집/공유 검색 2회는 marketing page/보라색 등의 제품 불일치로 거절했다. drag 접근성 검색의 single-pointer alternative를 적용했고 Compose IME 검색 0건은 검증된 검색결과로 꾸미지 않았다. 시스템폰트·기존녹색·48dp·작은창 단일스크롤 기준을 실제 검사한다.

renderer/파일 IO/실제Main support fixture/UI 테스트/읽기 감사 역할을 나눠 상호 검토했다. Root만 build/AVD/install/instrumentation/network delivery를 순차 실행한다. 캐시 사진 복원 실패 제거·short-window footer·미니미 bounds/drag deadzone·canonical/late callback·gallery cancellation·same-turn edit fence를 보완했다.

## 근거의 경계

- `source-freezeN.json`은 그 시도의 실제 full source maps와 당시 local HEAD를 보존한다. 미커밋 빌드의 당시 HEAD를 새 APK 출처로 고쳐 쓰지 않는다.
- build1/2 컴파일 실패, build3 JVM189 통과/lint 실패의 원문을 보존한다. 시도별189를 합산하지 않는다.
- Android Canvas 출력 fixture는 앱 스크린샷이 아니다. ComponentActivity/주입 UI·실제 MainActivity·시스템 사진 선택기/chooser·별도 테스트 receiver를 구분한다.
- cloud API29/TCG TEST debug 실행은 공개 서명 APK 실행/실제 휴대폰/Google/야외GPS·절전/API31+·사용자 시각 승인을 대체하지 않는다.
- 다른 UID 테스트 receiver가 PNG를 읽어도 SNS 게시 성공은 아니다. 운영 Firestore 규칙/프로필 연결 성공도 이번 범위가 아니다.

실제 실행 결과·원문·복원·최종 배포 출처는 아래에 완료 후 기록한다.

## 로컬 빌드

`binary-build4.json`/`source-freeze4.json`: 앱·Android 검사 APK 조립, 새 JVM189(실패/오류/skip0), lint 오류0/경고57. 오프라인/작업자1이며 production103/test87/resource28/avatars83/approved1/config6의 SHA와 당시 미커밋 HEAD를 보존한다. 이 TEST debug에는 로컬 검토 CA overlay가 포함되고 Firebase는 비활성이다. 공개 서명 APK의 실행 증거로 표시하지 않는다. `installation1.json`은 두 APK install-r와 원래 설정 바이트 보존을 확인하며 uninstall/data clear는 없다. 부팅의 SystemUI Wait1회는 cloud TCG 관측이다.

최종 입력은 `source-freeze5.json` / `binary-build5.json`이다. Android1 실패 뒤 검사1파일만 바뀌어 제품 APK SHA는 build4와 완전히 같다. JVM189는 build4 통과를 재사용하며 build5의 새189개로 세지 않는다(`build5-reuse.json`). AndroidTest lint analysis를 실행했고 최종 lint는 오류0/경고57이다.

## 추가 요청과 최종 검증 입력

사용자는 기본1:1 공유·깔끔한 위젯·지도 위 버튼 정리를 추가 요청했다. `source-freeze7.json` / `binary-build7.json`이 이 범위의 첫 전체 입력이다. product103/test87/resources29/avatars83/approved1/config7을 동결했으며 선택적 NAVER 변수 전달 workflow도 config에 포함한다. 이전 source5/6 결과를 이 입력의 실행으로 합산하지 않는다.

build7은 새 JVM189(실패/오류/skip0)와 lint 오류0/경고57, 앱·검사 APK 조립을 통과했다. AndroidDefault38은 renderer4/storage4/editor4/controller1/record4/navigation2/tabs1/widget7/map6/discovery5, 별도 native2는 실제320dp/font2의 IME/기록이다. 실제 실행 결과는 각 원문/복원 proof를 따른다. 시도별189를 합산하지 않는다.

위젯 전용 skill 검색은 검증된 일치를 찾지 못했다. 원문과 재검색을 보존했고 적합한 native 조작/Compose typography 결과·공식 Android widget 지침(2026-05-12갱신)을 적용했다. 공식 지도 유형 문서와 MapLibre README를 확인했지만 기존 제공자를 새로 계약/교체하거나 온라인 인증을 완료한 것은 아니다. Variables 읽기403·docs루트404도 성공으로 바꾸지 않는다. [지도 선택·연결 조건](../../MAP_PROVIDER_OPTIONS.md).

위젯3장은 Android RemoteViews 합성 snapshot 렌더 fixture이며 Launcher/Main 스크린샷이 아니다. renderer4장은 Android Canvas export fixture다. actualMain/Launcher가 생성되면 원본 PNG를 별도로 검토한다.
