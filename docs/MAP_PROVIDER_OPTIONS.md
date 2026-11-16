# 런모니 지도 선택

2026-11-16 사용자 선택: **현재 무료 지도를 먼저 개선한다.** 현재 앱은 MapLibre Native12.0.1과 OpenFreeMap Liberty 벡터 스타일을 사용한다. 이전 네이버 SDK 연결이 현재 활성이라는 설명은 유효하지 않다. Google Maps SDK·API 키·결제 연결은 이번에 추가하지 않는다.

## 현재 제공자에서 반영할 내용

- 실제 지도 도형과 지명·도로·교통 단서는 유지하고 지면/물/공원/건물·일반 POI의 대비를 정리한다. 실제 경로와 미니미를 먼저 읽게 한다.
- 화면 이동/주기 조회 실패에는 기존 지도·확인된 마커를 유지한다. 공개 마커의 기존 freshness/만료 기준은 연장하지 않는다. 수동 새로고침 실패만 지도 밖에 짧게 안내한다.
- OpenFreeMap·OpenMapTiles·OpenStreetMap 출처와 실제 오류 fallback을 유지한다. 공개 무료 서비스의 가용성/SLA나 모든 지역 지도 정확도를 보장하지 않는다.
- 기존 HTTPS 호스트 제한·동시 요청4개·24MiB HTTP 캐시·요청 timeout을 유지한다. 대량 타일 저장/사전 수집이나 외부 공개 터널을 추가하지 않는다.

## Google Maps를 나중에 선택한다면

Android용 Google 지도를 정식 SDK로 연결할 수는 있다. 현재 앱에 Google 지도 사용 가능 키가 있다고 가정하지 않는다. Google 타일 URL을 MapLibre에 임의로 넣는 방식도 사용하지 않는다.

1. 소유자가 Google Cloud 프로젝트, Maps SDK for Android 사용 설정, 현재 요금/이용조건과 Billing 연결을 확인하고 승인해야 한다. 이번 작업은 결제·프로젝트·크레딧을 만들지 않는다.
2. 지도 API 키를 Android 앱용으로 제한하고 패키지 `com.runmoni.app`과 **동일 보관 서명 인증서의 SHA-1**을 등록한다. 기존 `a213…` SHA-256 지문은 보관 인증서를 식별하는 참고이며 SHA-1 필드를 대신하지 않는다. 기존 인증서에서 필요한 지문을 확인하며 새 서명키를 만들지 않는다. Google Play 배포를 선택하면 실제 앱 서명 인증서도 별도로 확인한다.
3. API 키의 API 제한을 Maps SDK for Android로 좁히고 소유자가 사용량/쿼터·비용 알림 정책을 확인한다. 예산 알림이 과금의 강제 상한이라는 보장은 하지 않는다. 키·서비스 계정·서명 비밀번호를 채팅에 보내지 않는다.
4. SDK/빌드 설정·라이선스 고지와 제공자별 카메라/경로/marker/출처 구현을 변경하고 별도 검증한다. 기존 무료 제공자 실행 성공을 Google 지도 검증으로 재사용하지 않는다. 실제 서명 APK에서 인증·한국어 지명·드래그/핀치·경로·미니미·작은 화면/큰 글씨를 확인해야 한다.

공식 조건을 확인할 때 사용할 문서이며 이번 담당자가 새로 온라인 조회하거나 콘솔 설정을 완료했다는 뜻은 아니다.

- [Google Maps Android API 키](https://developers.google.com/maps/documentation/android-sdk/get-api-key)
- [Google Maps API 키 제한](https://developers.google.com/maps/api-security-best-practices)
- [Google Maps 요금](https://developers.google.com/maps/billing-and-pricing/overview)
- [OpenFreeMap](https://openfreemap.org/quick_start/)
- [MapLibre Native](https://github.com/maplibre/maplibre-native)
