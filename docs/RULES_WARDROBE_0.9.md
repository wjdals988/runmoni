# 새 의복·장비 39종의 온라인 표시 준비

runnerAvatar의13개 의상 ID 상한만 늘린다. 기존61ID를 바꾸지 않으며 상한은 hair/headwear/face/ear/eyewear/wrist/waist/hydration6,top9,bottom/costume8,socks/shoes7이다. 위치 정밀도·UID·성인 동의·현재 러닝 토큰·시간 제한·문서 읽기/쓰기 권한은 바꾸지 않는다.

로컬 demo-runmoni Firestore 에뮬레이터의 새189검사가 통과했고 종료를 확인했다. 실제 운영 게시·관리 API 버전·운영 쓰기 검증은 아니다. [검사](app-review/2026-11-17-expressive-refresh/rules-test1.json). 이 묶음은 앱 코드·버전·기존 공개APK/피드를 바꾸지 않는다. 별도 0.9 후보의 새 ID를 온라인에서 보이게 하려면 소유자가 이 전체규칙을 운영 콘솔에 게시해야 한다. 앞서 완료한0.8의3ID 게시를 다시 요청하는 것이 아니다.
