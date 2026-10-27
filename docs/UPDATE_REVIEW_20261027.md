# 버전 노출·업데이트 내역·우상단 알림 — 2026-10-27

사용자 확인 요청에서 버전/내역은 기존 코드에 연결됐지만, 메인 우상단 설정 아이콘의 점은 빠진 것을 확인했다. 0.1.6-dev/code7 개발본에서 이를 보완했다. 공개 APK는 여전히0.1.1-dev/code2이며 이 문서가 새 APK 공개를 뜻하지 않는다.

## 동작과 실제 Android 화면

5개 탭의 공용 우상단 설정 아이콘은 `AppUpdateUiState.updateAvailable`을 사용한다. 이 값은 패키지/서명 인증서/자산 링크 등이 신뢰 조건을 만족하고 새 `versionCode`가 설치값보다 클 때만 true다. 설정을 열면 맨 위에 새 버전 안내가 있고 앱 정보의 업데이트 확인으로 이어진다. 다운로드는 사용자 확인 후 링크만 연다. 나중에 선택은 공개 새 버전의 존재를 지우지 않는다.

다음은 **실제 Android에서 앱 UI를 렌더링한 테스트 fixture**다. ‘새 공개 버전이 있음’ 상태를 합성 데이터로 주입했다. 실제 새 릴리스 게시나 원격 피드 조회 성공을 뜻하지 않는다. 호스트 합성 이미지가 아니다.

| 우상단 설정 점 | 설정 상단의 새 버전 안내 |
|---|---|
| ![새 버전 상태 테스트 화면](app-review/2026-10-27/update/home-update-fixture.png) | ![설정 업데이트 안내 테스트 화면](app-review/2026-10-27/update/settings-update-fixture.png) |

버전 정보는 BuildConfig의 실제 설치 APK 버전과 함께 표시하며 Android 검사에서 PackageManager의 설치값과 비교한다. ‘나’의 앱 정보 행과 설정의 앱 정보 → 업데이트 내역으로 볼 수 있다. 개발0.1.6은 bundledAppHistory에도 ‘개발 중 · 미게시’로 기록했다. 이전0.1.5 APK의 sourceRevision과 당시 화면은 그대로 보존한다.

## 확인 결과

- 전체 debug/Android-test APK 빌드 성공, JVM30개 통과, lint오류0/경고57.
- 기본480dp/font1에서 Android9개 통과. [원시 실행 로그](app-review/2026-10-27/update/android-normal.txt).
- 우상단 점과5탭·설정 맨 위 안내·확인창·취소 후 점 유지, 현재/과거 버전·미조회/조회 실패·불신 서명 정보에서 점 없음, 검증된 캐시와 재조회 실패 상태를 확인했다.
- 실제 설치 버전/내역/라이선스/외부 링크 진입·다운로드 확인 전 외부 링크0회/확인 후1회도 검사했다. 네트워크 호출 없이 테스트 URL 관찰자를 사용한 검사다.
- 기존 업데이트 controller의6시간 캐시·시계 역행·30초 재시도·동시 확인 요청 방지도 포함한다. 실제 원격 Google 계정이나 APK 다운로드/설치 성공으로 표시하지 않는다.
- 실제 시스템320dp/font2에서 우상단/설정 경로와 긴 앱 정보·다운로드 확인2개가 통과했다. [원시 실행 로그](app-review/2026-10-27/update/android-native.txt), [320dp 설정 안내](app-review/2026-10-27/update/settings-320-font2-fixture.png), [큰 글씨 앱 정보](app-review/2026-10-27/update/about-320-font2-fixture.png)를 보존한다. 폰트 강제 축소 없이 줄바꿈/스크롤을 확인했으며 전체 TalkBack 실사용 검증은 아니다.
- 테스트 후 실제 `com.runmoni.app/.MainActivity`가 실행 중이고 설치값0.1.6-dev/code7임을 확인했다. fontScale/애니메이션1.0·화면480×854로 복원, 앱 crash 버퍼0바이트, 전용 PID10348 종료를 확인했다. [버전/출처/설정 복원 근거](app-review/2026-10-27/update/verification.json). KVM 없는 소프트웨어 실행의 성능 한계는 이전 검증과 같다.
- 앱 정보 검사에서 Android PackageManager가 읽은 [실제 패키지 아이콘](app-review/2026-10-27/update/android-loaded-package-icon.png)을 Android Canvas로 그린 결과도 보존했다. 런처 홈/다양한 아이콘 마스크의 실제 표시 검증과 구분한다.

설정/버전 UI는 awesome-design-md Nike 제3자 분석의 절제된 의미 강조를 참고했다. 점에는 접근성 설명이 있고, 색만으로 안내하지 않도록 설정의 텍스트 카드로 연결한다. 버튼48dp와 앱 정보 스크롤·다운로드 확인을 유지한다.

## 공개와 이전 APK의 경계

소스에 기능이 있어도 과거 설치 APK가 자동으로 수정되지 않는다. 업데이트 확인 기능을 포함한 앱은 새 공개 APK의 피드를 읽어 점을 표시한다. 그 기능이 없는 이전0.1.1 APK에는 레드닷 코드를 소급 적용할 수 없다.

현재 클라우드에는 기존 릴리스 서명키가 없고 GitHub 서명 설정 조회도403으로 제한됐다. 로컬 테스트 키 APK를 정식 업데이트처럼 공개하거나 대시보드 다운로드에 연결하지 않는다. GitHub 소스 PR과 APK 게시/대시보드 배포의 상태를 분리한다. [우선순위와 게시 조건](DEVELOPMENT_PRIORITIES.md)을 따른다.

## 전달 결과

- 앱 구현 커밋 `4113bb9255207bd00bd8969d9c05516c591fe68e`를 원격 브랜치에 푸시하고 [앱 PR2](https://github.com/wjdals988/runmoni/pull/2)를 생성·현재 작업에 첨부했다. 초안/OPEN/병합 가능을 확인했다. 앱 저장소에는 원격 CI 검사 항목이 없어 로컬 검증과 구분한다.
- 깨끗한 위 커밋에서 offline 재빌드를 확인했고, Android에서 검사한 APK와 바이트가 동일했다. 로컬 보존 파일은 `outputs/cloud-review/runmoni-0.1.6-dev-cloud-debug.apk`, 236,794,116bytes, SHA256 `07f5d880cc0227c2f45933dc2c9338e926a9e82937aee183a3511a9be3486e6f`다. debug 인증서 SHA256은 `264eca67962faa6e51d44c050772ef7b836315344cc25d91a5ac41447736b267`다. metadata에는 sourceRevision을 위 구현 커밋으로 고정하며 이후 문서 커밋으로 바꾸지 않는다. 기존 공개 인증서의 업데이트용 APK가 아니다.
- [대시보드 PR9](https://github.com/wjdals988/mydashboard/pull/9)는 소개 문구·개발 상태 정정만 포함한다. 원격 커밋 `8d4a578dac2082ab908a1c30e75f6095dedf2040`에서 다른 프로젝트/순서·기존 APK 메타데이터·공개 릴리스 내역 불변을 다시 비교했다. 기존 Node검사23개 통과, GitHub의 `Test, lint, and build`와 Vercel preview 상태SUCCESS를 확인했다. main 병합·운영 페이지 교체 완료를 뜻하지 않는다.
- 새 정식 APK 업로드·공개 피드·대시보드 다운로드 교체는 기존 서명키 부재로 완료하지 못했다. 기존0.1.1 다운로드를 유지했고0.1.5 APK의 원본 커밋도 바꾸지 않았다. 여자 기본형·58종 의상 레이어·실기기/로그인 검증은 우선순위 문서의 미완료 항목이다.
- 재사용 클라우드 실행 지침도0.1.6와 두 PR/검증 범위에 맞춰 환경 초안에 저장했다(`saved`/`requires_publish=true`). 적용하려면 환경 설정에서 검토·저장 후 게시해야 한다. 환경 초안 저장을 게시나 새 작업 복원 성공으로 표시하지 않는다.
