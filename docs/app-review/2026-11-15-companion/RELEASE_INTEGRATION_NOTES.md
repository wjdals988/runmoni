# 0.7 공개 검증 도구의 교정

실제 앱 소스/원화/실행 APK를 바꾸지 않은 배포 검증 도구 교정이다.

- 공개 소비 manifest의 파일 참조를 저장소루트 상대에서 manifest 디렉터리 상대 basename으로 고쳤다. 원본 Main/소스 증거는 바꾸지 않았다.
- C의 실제 노트 문구 `실기기 검증 전 개발 프리뷰`를 검사하도록 도구의 문구 조건을 맞췄다. 불변 릴리스 노트는 수정하지 않았다.
- Actions 보고서 ZIP에 octet-stream Accept를 붙인 첫 측정 요청은 HTTP415로 거절됐다. Actions ZIP/log에는 기본 Accept를 사용하고 릴리스 자산에만 octet-stream을 사용했다. 첫 요청에서 새 검사 수/성공 자료는 생성되지 않았다.
- 첫 draft 검증 verify1은 CI 확인 뒤 pinned asset URL 조건으로 실패했다. 실제 draft의 tag_name은 v0.7.0-dev이지만 html_url과 asset URL은 같은 untagged 임시 태그였다. 실제 GitHub 임시 URL과 같은 저장소·tag를 대조하도록 교정했고 자산을 교체/재업로드하지 않았다. 원문 failed proof와 당시 driver를 보존한다.
- verify2는 검증만 통과했고 publish1은 같은3개 자산을 개발 prerelease로 공개하고 공개 HEAD/두 sidecar 원문을 대조했다. 새로운 제품 테스트로 세지 않는다.

CI43761237483의 새 JVM219·실패/오류/skip0, release lint오류0·경고97·Information0이다. 로컬 input6 경고66과 구분한다. 실제 APK출처는 ca54251a780f56b60aaecce0dbeb94a4f33061ed다. 공개 서명 APK의 휴대폰 실행/Google/운영 저장 성공은 별도다.
