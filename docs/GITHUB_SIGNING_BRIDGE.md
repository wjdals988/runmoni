# GitHub 암호화 공개키로 서명 설정 전달

공식 GitHub CLI 로그인을 wjdals988로 완료한 뒤에도 현재 클라우드의 Secrets 공개키 요청은
HTTP403 / integration_permission / repoOAuthScopeGranted=false로 거부됐다. 로그인 성공을
Secrets 등록 성공으로 해석하지 않는다. 정확한 중계 원인은 미확인이며 같은 OAuth 승인을 반복하지 않는다.

새 키와 서명된0.1.6 APK는 그대로 유지한다. 개인키를 다시 만들거나 이전 Windows 키를 올리지 않는다.
클라우드에서 토큰을 요구하거나 직접 연결/프록시/터널로 우회하지 않는다.
원래 PC의 정상 GitHub 로그인으로 승인된 저장소에 직접 등록한다.

## 현재 진행 상태 — 2026-10-29

사용자가 원래 PC의 공개 packet을 전달했고, 지정된 repository/scope와32바이트 공개키 형식을
검사했다. 보존한 새 배포키의 승인 인증서 일치를 확인한 뒤4개 실제 서명값을 암호화했다.
파일은 `work/signing-bridge/runmoni-github-secrets.encrypted.properties`에 있으며 Git에 넣지 않는다.
7,024bytes, SHA256 `51f07c5d35521111a07f500b992245bcaddb5e7102203a7c27768fee8c461801`.
이 작업의 네트워크 요청/secret 쓰기는0회다. 이제 아래3번의 원래 PC apply와4개 등록 receipt가
필요하다. 등록 성공과 GitHub CI의 실제 서명 성공은 별도로 확인한다.

## 1. 원래 Windows PC에서 공개 정보 조회

[`RunmoniGitHubSecretsBridge.java`](../scripts/RunmoniGitHubSecretsBridge.java)는 Java11이상,
기존 Git Credential Manager 로그인, Windows 기본 curl.exe를 사용한다. GitHub CLI 설치는 필요 없다.
기존 PC의 runmoni 프로젝트에서 실행한다.

```powershell
java work/RunmoniGitHubSecretsBridge.java prepare
```

실제 Java 소스를 먼저 위 경로에 내려받아야 한다. 전달된 명령은 고정 commit과 SHA-256을 확인한다.
이 명령은 GitHub repository Actions Secrets의 **암호화용 공개키 정보만** 출력한다.
출력 형식은 `repository`, `scope`, `keyId`, `publicKey`의 JSON 한 줄이다.
이 JSON은 채팅으로 전달할 수 있다. `.jks`/`.p12`나 비밀번호/토큰을 출력하거나 요구하지 않는다.
prepare 모드는 GitHub GET1회이고 secret 수정은0회다.

## 2. 클라우드에서 기존 서명값을 GitHub 앞으로 암호화

공개 JSON을 작업 파일로 보존한 뒤, 기존 소유자 전용 키 상태를 검증하고 실행한다.

```bash
python3 scripts/cloud/seal-signing-key.py \
  --packet-file <public-packet.json> \
  --state-file <private-state.json> \
  --output work/signing-bridge/runmoni-github-secrets.encrypted.properties
```

이미 설치된 libsodium의 `crypto_box_seal`을 사용한다. 출력 파일은 공개 메타데이터와
GitHub만 열 수 있는 sealed-box 암호문4개다. 개인키/비밀번호 평문은 포함하지 않는다.
Git·채팅에 평문을 넣지 않으며 암호화 작업의 네트워크 호출은0회다.
실제 GitHub 공개키 packet을 받기 전에는 production 서명값을 암호화/전송하지 않는다.

## 3. 원래 PC에서 암호문 등록

암호화된 properties 파일만 원래 PC에 내려받아 실행한다.

```powershell
java work\signing-bridge-9d0f0bd\RunmoniGitHubSecretsBridge.java apply "$env:USERPROFILE\Downloads\runmoni-github-secrets.encrypted.properties"
```

도구는 저장소/공개키ID/승인 인증서/필드 집합과 현재 GitHub 공개키를 재확인하고,
4개 암호문을 API에 보낸다. 원래 PC에는 서명키 평문이 전달되지 않는다.
각 PUT의201/204 뒤에만 연결 receipt를 출력한다. 그 receipt는 채팅으로 전달할 수 있다.
앱 업로드/공개는 하지 않으며, 기존 키/파일/GitHub Environment 설정도 수정하지 않는다.
등록 위치는 Settings → Secrets and variables → Actions → Repository secrets다.

## 검증 범위

Linux JDK21에서 Java11 타깃 컴파일·fake API 경계12개 성공.
Python47개에는 실제 libsodium fixture 왕복 복호화·정확한 바이트 보존·다른 개인키 거부가 포함된다.
Cloud에서 prepare를 호출하면 Windows 전용 보호가 먼저 거부해 cloud proxy 자격을 읽지 않음을 확인했다.
첫 fixture 컴파일의 lambda 캡처 오류는 테스트 변수 수정 후 해결했다.
원래 PC의 공개 packet은 사용자가 전달했으며 실제 Windows/GCM/GitHub 등록 성공은 아직 확인하지 않았다.
실제 등록 receipt가 필요하다.
토큰/키값/원문 API 오류는 로그에 기록하지 않는다. TLS 검증을 해제하거나 별도 서비스/포트를 열지 않는다.
