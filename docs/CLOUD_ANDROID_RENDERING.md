# 클라우드 Android 실행 메모 — 2026-11-16

빌드 도구 준비는 `scripts/cloud/prepare-android.py`를 사용한다. `--build-only`는 에뮬레이터 다운로드를 생략한다. 저장된 Windows SDK·서명키·캐시가 있다고 가정하지 않는다. 이번 머신의 JDK21/Gradle8.13/SDK는 `work/cloud-android/`에 있지만 새 환경에서는 존재 여부부터 확인한다.

이 머신은 KVM 없이 전용 `Runmoni_Cloud_29`를 API29·TCG로 실행했다. 한 번에 headless AVD1개만 쓰며 빌드·설치·대량 전송을 겹치지 않는다. 종료는 소유한 PID·프로세스 시작 시각·AVD 이름을 대조한 뒤 수행한다. 다른 에뮬레이터 프로세스는 종료하지 않는다.

## 그래픽과 실제 표시

캐시된 emulator37.3.5의 `-help-gpu`는 `swangle`을 지원한다. `-accel off -gpu swangle -no-window -no-audio -no-boot-anim -no-snapshot -feature -Vulkan` 구성에서 실제 SDK가 ANGLE/SwiftShader임을 관측했다. 이 설정은 소프트웨어 실행이며 휴대폰 GPU 검증이 아니다.

같은 APK의 기존 `swiftshader` 화면에서는 도로/건물은 그려졌지만 글자와 미니미가 보이지 않았다. `swangle` 비교에서는 한국어 지명과 초기 미니미가 보였다. 캐시·외부 응답 시점은 같지 않으므로 GPU만이 유일한 원인이라고 확정하지 않는다. 의상 전환 직후 장면은 따로 시각 검토를 통과해야 한다. [원본 비교](app-review/2026-11-15-companion/input5-graphics-backend-visual-comparison.json).

`Style.getImage()`는 등록된 이미지의 readback이며 실제 symbol 표시를 보장하지 않는다. Map 완료와 Frame 완료 이벤트도 다르다. 실제 카메라·제스처·Native texture·시스템 스크린샷을 확인하고, ComponentActivity 합성 장면을 MainActivity·실제 GPS·휴대폰으로 표시하지 않는다.

## 프록시와 로컬 Firebase

보관 AVD에 이전 머신의 프록시 IP가 남아 스타일 요청이 연결 실패했다. Host의 상속 HTTP(S)_PROXY/NO_PROXY와 CA를 보존한 상태에서 같은 HTTPS 스타일은200이었다. 전용 AVD 설정만 현재 상속 프록시의 해석된 주소로 갱신했다. 공개 터널·방화벽 예외·TLS 검증 해제는 사용하지 않았다.

로컬 Firebase SDK 검사는 `demo-runmoni`의 localhost Auth9099/Firestore8080과 AVD의 `10.0.2.2`를 사용한다. 이 요청이 외부 프록시로 향하지 않게 별도 테스트 범위를 관리하고 종료 뒤 원래 설정5개를 정확히 복원한다. 빈 문자열도 shell 인자로 보존하도록 `shlex.join`을 사용해야 한다. 실제 빈 인자 복원 실패와 후속 복원-only는 [기록](app-review/2026-11-15-companion/firebase-sdk2-network-proxy-restoration-only.json)에 분리되어 있다.

현재 클라우드의 앱별 TEST debug CA overlay와 release TLS 설정은 별개다. 운영 키·비밀번호·토큰을 출력하거나 새 키를 만들지 않는다. 로컬 SDK 성공·공개 Rules 읽기·운영 프로필 저장·Google 로그인·서명 APK 실행은 각각 다른 검증이다.
