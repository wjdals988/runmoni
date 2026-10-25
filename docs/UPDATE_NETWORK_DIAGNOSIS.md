# 전용 AVD 연결 진단 — 2026-10-25

PC의 HTTP 성공과 Android 앱의 성공은 별도다. 사용자 인터넷 지연의 근본 원인은 확정하지 못했으며 순차 작업으로 해결됐다고 표현하지 않는다. 공개 터널·프록시·방화벽 예외·호스트 DNS 변경은 하지 않았다.

## 관측한 범위

- PC에서 공개 업데이트 raw 및 GitHub content API는 HTTP200·810UTF8bytes. 원격 APK 본문 재다운로드 없음.
- 전용 Runmoni_API36의 Wi-Fi가 켜져 있어도 초기 기본 네트워크는 없었다. 고정 가상 AP AndroidWifi/open을 명시적으로 연결한 뒤 default100·IPv410.0.2.16을 확인했다.
- 그 이후에도 실제 설치 앱의 공개 피드 조회는 실패했다. 오류 상태를 최신 버전으로 바꾸지 않았다.
- 별도의 shell UID 진단은 raw/API에서 DNS와 일부 TCP 연결 이후 TLS SocketTimeout을 관측했다. 진단용 30초 확대 및 수신64→128KiB/s 비교도 성공하지 못했다. 제품의 connect3/read5/call10초 제한·주소 순서·고정 raw 출처는 변경하지 않았다. shell UID 결과를 앱 UID의 TLS 내부 관측이라고 표현하지 않는다.
- emulator35.6.11의 콘솔1024000bits/s는125KB/s 환산값이며 실제 처리속도 측정이 아니다. -netspeed는 Ethernet/Cellular용, guest ingress는 인터넷 가능 네트워크의 수신 설정이다. 어느 쪽도 PC 전체나 Wi-Fi 양방향 속도 보장이 아니다.
- SettingsProvider 복원 직후 종료하면 이후 부팅에 이전 값이 남은 사례가 있었다. 2초대기+sync를 추가하고 별도 cold boot2회에서 65536→null의 유지 확인을 기록했다. 원인을 SDK/보안제품으로 단정하지 않았다.

## Google 로그인 오류와의 구분

사용자는 계정 선택창 이전 실패를 보고했다. 0.1.3은 다양한 로그인 예외를 같은 문구로 표시했다. 후속 오프라인 실측에서 Google계정0개·font_scale2.0·ingress65536을 확인했다. 0.1.4는 명시적 Google 버튼 흐름과 고정 오류코드로 단계를 나눈다. 이 구현 변경은 실제 Firebase 인증·Firestore 백업 성공 증거가 아니다.

이전 GitHub TLS 관측만으로 Google 실패도 같은 원인이라고 확정하지 않는다. Firestore Rules·서버 타임아웃을 계정 선택 이전 오류의 직접 원인으로 삼지 않는다. 사용자 로그인은 본인이 수행하며 비밀번호·인증번호·ID토큰·이메일·UID를 진단 로그에 남기지 않는다.

새0.1.4에서 사용자가 Google계정추가 시스템화면까지 진입한 뒤 Something went wrong을 보고했다. 사용자는 AVD실행시 PC브라우저도 느리거나 불통, 닫으면 회복한다고 확인하고 직접창을 닫았다. 이후 읽기진단은 기기0대로 Google실패로그를 확보하지 못했으며 원인을 확정하지 못했다. AVD는 다시 켜지 않았고 로그인대기 도우미도 종료했다. 강제창종료 때문에 마지막guest수신설정 원복은 미확인이다. 다음 허가된 AVD실행 전 원복부터 확인하며 폰트100%와 이전오프라인검증의원복성공을 마지막온라인세션복원증거로 혼용하지 않는다.

## 증거 위치

사용자 요청의 제한 재현은 첫 스크립트가 부팅 완료 전 wifi서비스를 호출해 즉시 종료됐으므로 무효다. 호출 순서 수정 후 부팅 직후 Wi-Fi/dataOFF·비행기모드ON을 확인했고21회 상태를 기록했다. 온라인 전환 플래그는0회이며 사용자가 창을 닫아 종료됐다. phase는 스크립트가 요청한 상태를 나타내며 매회 실제 네트워크 상태를 완전 감사한 값은 아니다. 마지막 QEMU working set3929MiB(약3.84GiB), CPU값31.27은 누적CPU초로 CPU사용률31.27%가 아니다. 이 값만으로 PC 지연의 원인을 메모리/네트워크 중 하나로 확정할 수 없다. 최신 사용자 지시대로 AVD를 제외하며 다시 실행하지 않는다.

후속 브라우저 읽기 확인도 Computer Use가 Windows Chrome의 현재URL을 안전하게 확정할 수 없어 종료됐다. Firebase콘솔의 제공자·Rules를 직접 대조한 결과로 취급하지 않는다. 브라우저 보안 정책을 우회하지 않는다.

로컬 비공개 진단은 ignored work/update-probe의 wifi-trace30·wifi-128k·api-profile·app-live 로그와 outputs/validation/update-live-20261025에 있다. 글꼴·계정수 실측은 outputs/validation/mobile-reset-20261025/font-baseline.json이다. 이 경로의 진단용 작업물과 최신 미게시 APK를 공개 업데이트 피드로 쓰지 않는다.

기준: [Android 에뮬레이터 옵션](https://developer.android.com/studio/run/emulator-commandline), [Google 로그인 버튼과 계정 없음 동작](https://developer.android.com/identity/sign-in/credential-manager-siwg).
