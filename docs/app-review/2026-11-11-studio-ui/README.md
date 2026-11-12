# Runmoni 전체 화면 UI 개편 검토

사용자 요청의 frontend-design/ui-ux-pro-max를 적용한 미게시0.4.3-dev/code25 후보다. 폴더 날짜는 작업 시작 UTC 기준이며 재개일은2026-11-12 한국 시간이다. 구현·로컬 실행·실제 서명 APK·운영 다운로드를 구분한다. 공개 다운로드는 검증한0.4.2/code24를 유지한다.

## 방향과 적용

[화면 계획/자체 비평](../../../design-system/runmoni/pages/studio-refresh.md)을 따른다. 홈/프로필은 승인 미니미와 정적 트랙, 러닝은 실제 기록과 명확한 제어, 탐색은 넓은 지도, 크루/파티는 사람과 일정의 읽기 쉬운 계층으로 구성했다. 로그인·옷장·설정·프로필 양식·기기 점검·앱정보/내역/라이선스·확인창도 정리했다. 공유 진입에는 눈에 보이는 ‘공유’ 이름을 추가했다.

frontend-design은 anthropics/skills의 실제 pinned 원문을 읽고 주제 기반 계획→비평→구현을 적용했다(`frontend-design-source.json`). ui-ux-pro-max 고정b7d5353의 실제 검색 원문은 이 폴더에 있다. 시스템 검색의 AI/보라·영상 홍보/CTA 반복 추천은 제품 맥락과 달라 채택하지 않았다. disclosure 재검색은0결과여서 검증된 DB 일치로 부르지 않으며 native 기본 지침을 보완했다. Compose typography 결과와 기존 native 접근성·안전영역 원칙을 적용했다. 새 시장 조사·사용성 향상 수치·새 Compose 버전 적용을 주장하지 않는다.

공통 토큰과 화면별 구성·글자 위계·새 mint/route blue·자연 높이 탭·단일 조작 영역을 사용한다. 기존 원화/58ID/13슬롯/9탭/0P·기록/지갑/계정·100m 동의·FGS/runId·프로필 ACK·공유 export/lifecycle/API29 창 정책을 유지한다. 새 원화/키/Rules/의존성은 없다. 기존 다크 테마 구현으로 확대하지 않는다.

6개 담당의 소스 상호 검토에서 공통 탭의200% 글씨 고정 높이 위험을 발견해 자연 높이 selectable tab으로 보완했다. 소스 검토는 실행 검증이 아니다. 전체 페이지와 주요 상태의 검사 후보는 `qa-inventory.md`를 따른다.

## 보존된 빌드 결과

`source-freeze1.json`/`binary-build1.json`: 오프라인·작업자1로 앱/검사 APK, 새 JVM189(실패/오류/skip0), lint 오류0/경고57을 통과했다. JVM task 자체를 다시 실행했고 로그와 보고서 생성 시각을 확인했다. 앱241404003bytes·검사5522084bytes이며 실제 SHA와 source103/test88/resources29/avatar83/approved1/config7을 보존했다. TEST debug/로컬 검토 CA·Firebase 비활성이며 공개 보관키 APK가 아니다. 빌드 당시 HEAD를 이후 커밋으로 덮어쓰지 않는다.

`contrast.json`: 텍스트·조작 경계16조합을 검사했다. 전체 화면의 모든 픽셀 대비나 사용자 시각 승인으로 확대하지 않는다. 승인 원본 SHA f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65는 그대로다.

## 환경 중단과 재개

최초 클라우드 AVD 시작 뒤 exec-server 연결이 끊겼고 usable environment가 없다는 오류로 설치/검사 수집기를 시작하지 못했다. 원인은 미확정이다. 재개 시 파일/6개 소스맵/빌드 기록이 같음을 확인했고,남아 있던 정확한 소유 AVD PID887490을 확인해 종료했다(`resume-observation.json`). 이 중단을 Android 검사 실패나 전체 복원 완료로 표시하지 않는다.

준비된 검사 수집기의 두 APK guard가 method count 치환에 함께 바뀐 문제는 실행 전에 원래2개 조건으로 바로잡았다. 제품 수정이나 실행 실패가 아니며 원래 설치 SHA 검사를 완화하지 않는다.

## 검증/배포 경계

`planned-groups.json`의 일반56/native13은 계획이며 실행 수치가 아니다. 후속 원문과 복원 proof가 생성된 뒤 실제 결과만 기록한다. 이전 source12의189/Android7+12+22·Main 공유 단계 묶음/Launcher는 baseline으로 보존하며 이번 UI의 새 실행으로 합산하지 않는다.

Android ComponentActivity/in-memory/inert-map fixture는 실제 MainActivity/휴대폰/Google·운영 쓰기·실제 SNS 전송이 아니다. Root가 실제 원본 PNG를 연 뒤 시각 검토 여부를 기록한다. AVD 설치/검사 뒤 원래 XML/프로필/account·소유 설정/권한·전원을 확인하고 정확한 소유 프로세스를 종료한다. 미확인 상태는 그대로 남긴다.

커밋·푸시·PR은 구현 보존 단계다. 실제 같은 키 서명 CI·APK/sidecar/digest·공개 HEAD가 확인되기 전에는 업데이트 피드/대시보드를 새 버전으로 바꾸지 않는다. 기존 공개0.4.2 실제 APK출처176a9cbe...와0.1.5 출처75c39ca...는 새 UI/문서 커밋으로 바꾸지 않는다.
