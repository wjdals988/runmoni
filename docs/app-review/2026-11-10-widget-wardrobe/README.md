# 위젯·미니미·홈 날씨 검토 — 0.4.1-dev/code23

위젯 텍스트 잘림, 미니미 옆 날씨 배치와 기존 원본 기반 새 꾸미기를 검토한다. 아래 결과는 개별 근거의 범위를 따른다.

## 변경 범위와 보존

- 위젯은 실제 글꼴·폭·높이를 측정해 전체/축약/최소/아이콘 단계를 고른다. 한글 글리프와 전체 줄 폭·높이, 48dp 조작 영역, 작은 fallback 자체의 적합 여부를 확인하도록 보강했다. FGS의 비메인 호출은 메인에서 최신 상태를 다시 읽어 발행하며, 오래된 snapshot이 새 runId 상태를 덮지 않게 한다. 이 구현 설명은 실제 런처 검토 완료를 뜻하지 않는다.
- 홈은 기본 폭에서 미니미 옆에 기온·바람·강수와 짧은 옷차림을 두고, 좁은 화면·큰 글씨에서는 아래로 펼친다. 중복 기온을 정리하되 날짜/현재시각·예보 시각/제공자와 실패·이전 정보 경계는 유지한다. 실제 Main 출력 확인은 아래 후속 항목에 따로 기록한다.
- 양말은 크루(`socks:1`)·니삭스(`socks:2`) 두 종을 남녀의 승인 원본 프레임 위에 명시적 마스크로 표시한다. 여자 기본형의 타이츠와 이미 보이는 발목에 재질을 얹으며 숨겨진 맨다리나 새 몸통·팔다리를 만들지 않는다. 남자 그린·코랄 쇼츠(`bottom:2/3`)는 기존 쇼츠 원형의 팔레트 차이다. 여자 원본은 타이츠이므로 두 쇼츠를 준비 완료로 표시하지 않는다.
- 실제 준비 상태는 **남자 40개·여자 38개·합집합 40개 ID**다. 모든 성별에서 40개가 착용되거나 전체 58종이 완성됐다는 의미가 아니다. `socks:0`은 기존 원본 발목 양말 그대로이며, 무양말(`socks:3`), 캡·선바이저와 다른 미준비 원화는 이번에 완료하지 않았다.
- 승인 원본 SHA256 `f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65`, 기존 58개 ID·13개 내부 슬롯·9개 탭·0P 정책·성별·보유권·포인트·저장 선택을 보존한다. 새 얼굴·몸 리그, 거절된 v0.4 조립, 미승인 수영복을 사용하지 않았다. Aseprite 프레임/레이어 개념은 참고했지만 실제 Aseprite·이미지 생성 모델 제작물은 아니다.

## 현재 완료된 빌드·검사

|범위|실제 완료 근거|해석과 제한|
|---|---|---|
|첫 빌드|[build1.txt](build1.txt): `RunmoniDestinations.kt:50:51`의 `maxWidth` 암시적 receiver 컴파일 실패, 42초|새 전체 빌드나 Android 검사 성공으로 세지 않는다. 외부 `BoxWithConstraints` 범위에서 폭을 계산하도록 정정했다.|
|최종 제품 빌드|[binary-build2.json](binary-build2.json), [build2.txt](build2.txt): **새 JVM167개·실패/오류/skip0, lint Error/Fatal0·Warning45**|실제로 새 실행한 JVM 근거다. local TEST debug이며 보관 릴리스 키 서명 결과가 아니다.|
|첫 Android 시도|[android-review1.json](android-review1.json): 전체 `passed:false`, widget-factory **7개 통과 보존**|뒤의 expanded-wardrobe 그룹은 실패했다. 그 그룹 안의 부분 통과를 고유 통과 합계에 더하지 않는다.|
|검사 정정 빌드3|[source2-to3-continuity.json](source2-to3-continuity.json), [binary-build3.json](binary-build3.json)|`ExpandedWardrobeTest.kt`만 변경. 제품 APK는 build2와 비트 동일하며 JVM167은 build2 결과 재사용이다.|
|두 번째 Android 시도|[android-review2.json](android-review2.json): 전체 `passed:false`, **expanded4 + wardrobe-regression4 + approved-base3 = 11개 통과 보존**|뒤의 weather-layout 그룹은 실패했다. 그 그룹의 부분 1개 통과도 합계에 더하지 않는다.|
|검사 정정 빌드4|[source3-to4-continuity.json](source3-to4-continuity.json), [binary-build4.json](binary-build4.json)|`HomeWeatherLayoutTest.kt`만 변경. 제품 APK는 build2/3와 비트 동일하며 JVM167은 재사용이다.|
|후속 검사 완료|[android-review3.json](android-review3.json): 홈 배치2·기존 날씨 상태1·옷장1·메인 탭1 = **새 5개**, 전체 passed true|build4의 설치 쌍·전후 소스맵·XML/프로필·소유 설정/권한/전원 복원 true. 홈은320/360/411dp×font1/1.5/2 배치를 확인했다.|
|호스트 재질 검사|[host-material-final.json](host-material-final.json): **208사례**, alpha·마스크/입력·정체성/신발·독립 신발 보호 불변|호스트 순수 재질 검사다. Android 실행·실제 Main/런처·모션·사용자 시각 승인으로 바꾸지 않는다.|

앞선 통과7개는 source2, 다음11개는 source3, 최종 새5개는 source4에서 실행했다. 고유23개는 같은 제품 APK에 대한 서로 다른 테스트 APK의 범위이며, 단일 source4 쌍에서23개를 전부 새 실행한 것이 아니다. JVM167·host208·Android 방법 수·뷰포트 조합은 합산하지 않는다.

제품 APK는 build2/3/4 모두 238,822,405bytes, SHA256 `96491f2a85bf5ed534e81b9337ff29aa8b6194d38f2f4051d968ceaed120cbc8`다. 테스트 APK는 각각 아래처럼 다르다.

|설치 쌍|테스트 APK SHA256|
|---|---|
|build2|`305eaf526fa8f319dd99d5bbd86d39ebccd2c6891d088846aa40c9b1e0f2b0d2`|
|build3|`21a6a809c072d826f3de2c37f5a10957d4ebbb7780ddfe6958b7edc9e4e24367`|
|build4|`8f51b860c0e10bc050a535fa81155b202efc9748784e7262ec799d14fec33b32`|

실행 당시 Git parent `d01fc877d203f93cc20f0bc4343ae472fd258400`는 미커밋 입력의 기존 상태이며 새 공개 APK 출처가 아니다. 실제 입력은 각 `source-freeze2/3/4.json`의 여섯 source map으로 연결한다. 새 공개 APK 출처는 앞으로 검토·병합한 실제 커밋 C와 서명 CI 메타데이터로 별도 확정한다. 검토 시작 당시 공개0.4.0 APK의 출처 `eafdb48d80558114c7322072319c709c67fc3e82`와 과거 0.1.5 출처 `75c39ca900b140cfb02c1a47807a5955c8b8db7f`를 새 문서 커밋으로 바꾸지 않는다.

## 실패와 정정의 실제 범위

첫 Android의 실패 원문은 [android-review1-expanded-wardrobe.txt](android-review1-expanded-wardrobe.txt)다. 새 저장 검사가 `ear:null`을 그대로 복원할 것으로 기대했지만, 기존 JSON codec은 이미 유효 기본값 `ear:0`으로 정규화하고 있었다. root는 기존 저장 계약에 맞춰 검사 기대만 정정했다. 제품의 귀 장비 동작·새 ID·저장 데이터를 바꾸거나 실패를 통과로 덮지 않았다. 변경 전 검사는 [retained-expanded-test2.kt](retained-expanded-test2.kt)에 남겼다.

두 번째 Android의 실패 원문은 [android-review2-weather-layout.txt](android-review2-weather-layout.txt)다. 실제 화면에는 `현재 위치 근처 · … 예보 · 이전 정보`가 있었으나, 검사가 긴 문구에 대해 `assertTextContains("이전 정보")`의 기본 전체 일치를 사용했다. root는 `substring=true`로 검사 의도를 바로잡았다. 제품의 예보 상태나 안내 문구를 변경하지 않았다. 변경 전 검사는 [retained-home-test3.kt](retained-home-test3.kt)에 남겼다.

두 Android 시도는 `completed:true`여도 전체 `passed:false`를 유지한다. 각 시도의 원래 XML/프로필과 검사 소유 설정·권한·관측 전원 복원은 true이며 `restorationCommandFailures`는 빈 배열이다. 전체 OS의 모든 자동 변경이 바이트 동일하다는 뜻은 아니다. 각 실행의 raw SHA, 설치 앱/검사 APK SHA, 실제 실행 driver/helper SHA와 변경 전후 소스 맵은 원래 JSON을 따른다.

[installation1.json](installation1.json)은 최초 앱/테스트 APK 교체 설치, [installation2.json](installation2.json)은 같은 앱 APK를 유지하고 테스트 APK만 바꾼 근거다. 두 설치에서 원래 XML을 보존했고 삭제 설치·데이터 초기화는 하지 않았다. 부팅의 `System UI not responding`에서 선택한 Wait는 부팅 관측이며 제품 검사 통과 수가 아니다.

## 호스트 시각 실패 두 번과 최종 정지 검토

첫 후보는 수치상 208사례를 통과했어도 여자 양말이 작은 흰 패치와 발목 간격으로 보여 시각적으로 거절했다. [first-host-visual-review.json](first-host-visual-review.json), 첫 마스크·manifest·crew/knee/mixed PNG를 그대로 보존한다. 이 결과를 완성·승인 상태로 바꾸지 않는다.

두 번째 후보는 폭을 넓히고 이미 보이는 발목과 독립 신발 보호를 추가했지만, 여자 접힌 1/2/5/6 프레임의 허벅지에 흰 패치가 생겨 다시 거절했다. [second-host-visual-review.json](second-host-visual-review.json)과 두 번째 원문·PNG를 보존한다. 최종 데이터는 프레임별 fabric 최소 Y와 발목 양성 예외, 독립 신발·밑창 보호를 적용했다. 소스 고정 회귀 좌표 F1 `(150,265)`, F2 `(145,260)/(150,260)`, F5 `(150,265)`, F6 `(145,265)`도 보호한다. 이 좌표는 원본에서 발견한 회귀 seed이며 추정 리그나 완전한 다리 영역이 아니다.

최종 마스크 SHA256은 `dd1dbf5f762e9d219d347d5879a2eeedac7de4397275dbd19645eda8fe4b64a5`다. [host-final-visual-review.json](host-final-visual-review.json)은 루트·독립 검토자의 최신 crew/knee 16셀 **정지 호스트 출력** 검토에서 추가 차단 문제가 발견되지 않았다는 범위다. 수치 검사는 최종208사례이며 앞선 후보208들을 더해624개 최종 사례로 쓰지 않는다. 사용자 승인·실제 Android 사진·8프레임 모션의 자연스러움 확인과는 별개다.

## 실제 Main·Launcher와 복원

[실제 Main 완료](live2.json)는 설치 build4 쌍에서 실제 옷장 UI 선택7회와 prefs 저장·포인트/보유·다른 필드 보존, 남녀 전환 시 기존 남자 쇼츠ID 유지 후 명시적 기본 하의 선택을 확인했다. native MET 예보·날짜/시각·기온/바람/강수는 Run CTA 위에 있었다. 합성 서울 GPS와 UTC 기기 시각이며 실제 한국 휴대폰의 날씨/시각 측정이 아니다. 새 HTTP status·요청 좌표는 독립 기록하지 않았다.

- [홈](live2-home-weather.png): 미니미 오른쪽 기온·바람·강수·옷차림, 아래 CTA와 예보 출처.
- [남자 크루 양말](live2-male-crew.png), [여자 니삭스](live2-female-knee.png), [남자 코랄 쇼츠](live2-male-coral.png): 실제 선택과 같은 미니미 표시.

네 장은 실제 focused/resumed MainActivity의 원본 PNG이며 root와 독립 avatar_item_review가 `view_image`로 직접 보았다. [픽셀/개인정보 검토](main-visual-review.json)에서 미니미 옆 배치와 정지 화면의 추가 차단 이슈 없음·실제 계정/이름/기록/원시좌표 없음은 확인했다. 사용자 승인·모든8프레임 모션·화면 아래 마지막 색상 행의 실제 스크롤 접근성을 이 사진만으로 판단하지 않았다. source map·설치 APK·driver/helper SHA와 XML/profile/DB rows·소유 환경 복원은 live2 원문을 따른다.

앞선 [live1 전체 실패](live1.json)는 실제 선택2회·남자 사진1장 이후 수집기가 visible text인 닫기를 content-description으로 찾아 중단한 것이다. 제품 변경 없이 수집기만 정정했다. 그 원래 실패/2관측/사진을 보존하고 live2의 완료7회/4장에 합산하지 않는다. 원래 XML/profile/DB rows·소유 환경은 live1에서도 정확 복원했다.

[실제 Launcher 완료](widget2.json)는 system font1/font2에서 **서로 다른 OS pin 위젯 두 개를 각각 추가·촬영·제거**했다. [보통 글씨](widget2-idle-font1.png)·[2배 글씨](widget2-idle-font2.png)를 root가 실제 보고 [정지 픽셀/개인정보 검토](launcher-visual-review.json)를 남겼다. visible text와 앱 열기 버튼이 잘리지 않는다. 실제 idle 위젯이므로 같은 위젯의 live resize·실제 러닝 제어·알림 조작·glyph ink 측정으로 확대하지 않는다. glyph/텍스트·48dp·144상태/크기/font 조합은 별도 factory7의 native 검사다. 위젯 ID·캐시 존재/바이트·원래 font/XML/profile/DB rows·소유 환경은 모두 복원했다.

[첫 widget1 guard 실패](widget1.json)는 기존 로컬 TEST 위젯 캐시64bytes가 XML이 아니어서 임시 fixture/폰트/권한 변경·위젯 pin 전에 중단한 것이다. [변경 전 상태 관측](widget1-guard-observation.json)에서 XML·환경·캐시 바이트와 프로세스/recorder 부재를 확인했다. 기존 캐시 내용의 발생 원인은 확정하지 않았다. 원래 캐시를 opaque bytes로 보관/복원하는 수집기만 보완했고 제품/원래 캐시를 수리하지 않았다. widget2는 유효한 임시 idle XML만 사용하고 기존 불완전 캐시도 정확히 복원했다.

[프록시 소유5키 복원](debug-session-network-restoration.json)과 [전용 AVD 종료](session-cleanup.json)를 확인했다. 종료한 소유 PID는672492, 관측 상태Z다. 다른 AVD 종료나 모든 OS 파일 바이트 동일성은 주장하지 않는다. 제공 CA는 앱 전용 로컬 TEST debug override였으며 시스템 신뢰 저장소와 release TLS 설정은 바꾸지 않았다.

## 배포 출처

위 결과는 로컬 검토 단계다. 공개 서명 APK는 검토한 실제 main C에서 별도 CI로 만들고 서명·버전·embedded C·3자산/sidecar/HEAD와 대시보드·canonical 피드의 실제 완료 근거를 후속 게시 문서에 기록한다. 로컬 build 당시의 기존 Git parent와 미커밋 source map을 새 APK 출처로 바꾸어 쓰지 않는다.

## 해석의 경계

- 현재 Android 근거는 전용 클라우드 `Runmoni_Cloud_29`·API29·TCG다. TEST debug의 actual Android bitmap, Compose/RemoteViews fixture, 실제 Main 화면, 실제 런처 위젯, 호스트 정지 출력은 각각 구분한다.
- 실제 휴대폰·Google 로그인·운영 쓰기/두 기기 공유·야외 GPS·장시간/Doze/OEM·API31이상·공개 서명 APK 직접 실행·사용자 시각 승인은 별도 미확인이다.
- 이 변경은 새 Firestore Rules/색인을 추가하는 작업이 아니다. 이전 0.4.0 운영 설정의 미확인 상태를 이번 로컬 성공으로 바꾸거나 같은 콘솔 설정을 새 요구로 반복하지 않는다.
- 원본 계정·GPS·비공개 XML/DB 사본은 WORK에만 두며 공개 검토 자료에 넣지 않는다. 복원 성공은 해당 JSON에 명시한 앱/검사 소유 범위만을 뜻한다.
