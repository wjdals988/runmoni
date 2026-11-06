# 전체 UI 적용 검증 — 진행 중

0.3.7-dev/code19. 사용자 요청 ui-ux-pro-max의 native/Compose 기준을 전체 제품 화면에 적용했다. [설계](../../../design-system/runmoni/MASTER.md) · [실제 검색/채택 판단](../../design/ui-ux-pro-max/README.md).

현재 [build1](binary-build1.json)은 오프라인·Gradle 작업자1로 성공했다. 현재 소스의 JVM115개(실패/오류/skip0)를 새로 실행했고 debug lint오류0/경고39, 대비 토큰12조합을 확인했다. [빌드 원문](build1.txt). 현재 app/test APK 해시는 해당 JSON을 따른다.

Android56개와 별도 native5개·실제 Main/SystemUI 통합은 아직 실행 전이다. 이전 BG 후보115/33을 이번 새 전체UI 검증으로 합산하지 않는다. 최종 화면/실패/복원 근거는 실행 후 이 문서에 갱신한다. 로컬 debug는 TEST서명/Firebase미설정/API29 AOSP/TCG이며 공개 서명APK·휴대폰·Google·운영쓰기·야외GPS/Doze/OEM·Android12이상 실행/사용자 시각 승인은 별도다.

이전 host5는 환경 단절로 finally가 끝나지 않았다. [후속 정리](interrupted-host5-cleanup.json)에서 실제 제품 UI로 해당 검토행1건을 삭제하고 원래 DB0행/0점·전체XML 바이트를 확인했다. 복구 작업의 시작 설정은 복원했으나 원래 pre-host5 시스템 설정 스냅샷은 없어 전체 과거 설정 정확복원이라고 주장하지 않는다. [정리 때 실행 helper](interrupted-host5-cleanup-executed-helper.py).
