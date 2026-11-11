# 런모니 지도 선택과 연결

2026-11-11 확인. 이번 변경은 지도 위의 버튼을 지도 아래 ‘지도 조작’ 메뉴로 모으고 화면을 정리한다. 지도 제공자를 바꾼 것으로 표시하지 않는다.

한국 지역 러닝 앱에는 **기존 네이버 지도 연동을 활성화하는 방향**을 우선 권한다. 현재 앱에 NAVER Maps SDK 3.24.0과 `com.naver.maps.map.NCP_KEY_ID` 연결이 이미 있다. 설정이 비어 있거나 인증이 실패하면 기존 OpenStreetMap 배경으로 돌아간다. 네이버의 국내 지명·공원·도로 표기 적합성은 실제 지역과 기기로 추가 확인해야 한다.

|선택|장점|추가 조건|
|---|---|---|
|기존 네이버 SDK|현재 코드 활용, 국내 지도 제공|소유자의 Naver Cloud Maps 애플리케이션·Android 패키지 등록·사용 조건/요금 확인 필요|
|MapLibre Native + 별도 벡터 지도 제공자|지도 색상·레이블 밀도·경로 강조를 세밀하게 설계 가능|렌더러는 무료 오픈소스지만 지도 데이터/타일·스타일 제공 계약은 별개. 제공자 선정·라이선스·키·비용 검토와 새 SDK 실행 검증 필요|
|현재 OSM 래스터 배경|추가 서비스 설정 없이 기존 동작 유지|레이블·색상을 앱에서 임의로 재스타일링하기 어렵고 표준 서버는 best effort. 대량 타일 저장/사전 수집은 하지 않음|

## 기존 네이버 경로를 연결할 때

1. 소유자가 Naver Cloud Maps의 Mobile Dynamic Map과 앱 사용 조건·요금을 확인한다. 이번 작업은 새 계정/서비스나 과금 설정을 만들지 않는다.
2. 콘솔의 Android 앱 패키지에 `com.runmoni.app`을 등록하고 해당 SDK용 식별자를 확인한다. 키·비밀번호·서비스 계정 파일을 채팅에 보내지 않는다.
3. GitHub 저장소 **Settings → Secrets and variables → Actions → Variables**에 기존 빌드 설정 이름인 `NAVER_MAP_CLIENT_ID`를 등록한다. 식별자는 Android Manifest/앱에 포함되는 값이며 비공개 서명키와는 다르다.
4. 새 CI 빌드부터 선택적 변수를 기존 Gradle 설정/Manifest로 전달한다. 변수 이름의 `CLIENT_ID`는 기존 코드 호환 이름이며 Manifest 키는 신규 `NCP_KEY_ID`다.
5. 실제 서명 APK에서 네이버 로고/지도·인증 오류·OSM fallback·드래그/핀치·경로/러너 위치를 확인한다. 빌드 설정 전달 성공만으로 네이버 온라인 지도 실행을 통과 처리하지 않는다.

현재 환경에서 Repository Variables 읽기는403으로 거절됐으므로 실제 등록 여부를 확인하지 못했다. API 식별자나 인증 성공을 추정하지 않는다. MapLibre의 공개 demo style을 운영용 데이터 계약으로 간주하지 않는다. 네이버의 지원 종료 예정 Lite mode도 신규 적용하지 않는다.

확인한 공식 자료:

- [네이버 Android SDK 지도 유형·디스플레이 옵션](https://navermaps.github.io/android-map-sdk/guide-ko/2-3.html)
- [MapLibre Native 공식 저장소](https://github.com/maplibre/maplibre-native)
- [Android 위젯 디자인](https://developer.android.com/design/ui/mobile/guides/widgets)

공식 자료를 읽은 사실과 실제 서비스/기기 검증은 구분한다. HTTP 원문·실패와 확인 시점은 검토 근거에 보존한다.
