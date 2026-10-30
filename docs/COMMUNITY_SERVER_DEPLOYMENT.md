# 공용 방 서버 배포

현재 상태: 배포할 코드/설정은 준비하며 실제 공용 서비스·주소는 아직 생성하지 않았다. 서버 관리 계정/실행 권한이 이 환경에 연결되지 않았다. 기존 [Firebase Spark 무료 선택](FIREBASE_SETUP.md)을 변경하거나 결제 계정을 연결하지 않았다.

## 준비한 구성

- Python3.12/FastAPI0.1.5, 단일 Uvicorn worker, SQLite WAL/영속 디스크. `/health`와 공개 방 목록, 인증된 생성/참여/채팅을 제공한다.
- [render.yaml](../render.yaml)은 Singapore web service와1GB 영속 디스크의 배포 예시다. **starter/디스크는 유료이며 실제 플랜·비용을 소유자가 확인하고 승인해야 한다.** 무료 임시 파일시스템으로 운영해 방 내역이 사라지는 구성을 완료로 처리하지 않는다. 이 파일만 저장했다고 서비스/결제/주소가 생성되는 것은 아니다.
- [Dockerfile](../backend/Dockerfile)은 다른 VM/호스팅에서도 쓸 수 있다. 비root UID10001, 단일 worker, `/var/lib/runmoni` 영속 볼륨을 사용한다. 빌드 컨텍스트는 소스4개와 lockfile만 허용하며 `.secrets`/DB/서명키를 이미지에 넣지 않는다. 세션 CA는 BuildKit secret으로만 제공할 수 있다.
- 외부 공개 터널·임시 cloudflared 주소·방화벽 예외로 노출하지 않는다. 정상 호스팅의 HTTPS 주소를 사용한다.

## 소유자가 Render를 선택할 때

1. GitHub 저장소 접근과 Render 계정을 연결하고 Blueprint의 브랜치를 `codex/community-rooms`로 선택한다. 유료 서비스/디스크 비용을 확인한 뒤 생성한다. [Blueprint 생성 화면](https://dashboard.render.com/blueprint/new?repo=https://github.com/wjdals988/runmoni).
2. 플랫폼이 실제 발급한 HTTPS 주소에서 `/health`의 public_rooms/guest_sessions=true와 공개 목록을 확인한다. 예시 주소를 실제 배포 URL로 기록하지 않는다.
3. 재시작 뒤 테스트 방/참여/메시지 보존을 확인하고 영속 디스크에 DB와 WAL이 저장되는지 확인한다. 백업은 SQLite online backup으로 일관된 스냅샷을 만들고 복원도 확인한다. 디스크는 백업의 대체가 아니다.
4. 확인된 공개 주소를 GitHub Actions repository variable `RUNMONI_API_URL`에 설정한다. 값은 공개 URL이며 토큰/비밀번호를 채팅으로 보내지 않는다. 앱을 이 주소로 새 빌드하고 실제 휴대폰2대에서 같은 방 목록/입장/채팅을 확인한다. 기존 사용자 설정 주소는 임의로 바꾸지 않는다.
5. 앱 서명/새 APK 게시/다운로드 피드와 대시보드 반영은 별도 검증한다. 기존0.1.6 자산의 출처/해시를 다시 쓰지 않는다.

## 로컬 컨테이너 검증

```bash
docker build -f backend/Dockerfile -t runmoni-api:review .
docker volume create runmoni-room-data
docker run -d --name runmoni-api-review -p 127.0.0.1:8789:8080 -v runmoni-room-data:/var/lib/runmoni runmoni-api:review
```

클라우드 프록시가 필요한 빌드는 결합 CA를 `--secret id=proxy_ca,src=/etc/ssl/certs/ca-certificates.crt`로 제공한다. TLS 검증을 끄거나 프록시 자격을 Dockerfile에 저장하지 않는다. named volume은 이미지의UID10001 소유권을 복사한다. 호스트 디렉터리 mount는 운영자가 해당 UID에 쓰기 권한을 준비해야 한다. 검증한 컨테이너만 종료하고 실제 데이터 볼륨을 자동 삭제하지 않는다.
