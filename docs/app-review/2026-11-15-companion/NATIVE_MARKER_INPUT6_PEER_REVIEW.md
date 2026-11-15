# 입력6 네이티브 미니미 교체 — 독립 소스 검토

검토 대상은 `app/src/main/java/com/runmoni/app/ui/MapLibreMapPane.kt`, 최종 동결 SHA256 `b5ea1f0eafa5fbda8df770ef49f6c0d15be3978966ebc4f1054c2eab7d1c42fd`다. 커뮤니티 담당자의 동결 통보 뒤 실제 파일 해시를 대조했다. 이 검토자는 제품/검사 소스 편집·빌드·검사 실행·ADB·네트워크·Git 작업을 하지 않았다.

## 유일한 지적과 최종 변경

첫 검토 SHA `80de3ccabfe1744515ceebe63a47dae03508bb89a8c2facfed1004cb62ee93c5`에서는 runner source의 비Sync `setGeoJson()` 제출 직후 이전 image를 제거했다. 호출 순서만으로는 native source 적용 완료를 보장하지 않으므로 이전 feature가 참조 중인 image를 먼저 제거할 경계가 남아 있었다. 이는 정적 순서 문제이며 입력5의 실제 첫 교체 프레임 소실을 유일하게 설명하는 원인으로 확정하지 않았다.

최종 소스는 runner FeatureCollection만 `setGeoJsonSync()`로 적용한 뒤 unused image를 제거한다. route/endpoints의 비Sync 갱신은 유지한다. 최종 파일에서 이 호출과 설명 주석 두 줄만 역변환한 메모리상의 텍스트가 첫 검토 SHA와 정확히 같음을 확인했다. 따라서 첫 검토 이후 추가 제품 변경은 이 최소 수정뿐이다.

## 캐시 SDK API 확인

로컬 MapLibre Android SDK12.0.1 AAR의 `classes.jar`와 javap 입력이 byte 단위로 같음을 확인했다(classes.jar SHA256 `5d32c62880f431174c6c685b3ed20fbf2d1a795d1b3d22a0e4c271c5bf79813b`). 로컬 JDK의 javap으로 다음을 확인했다.

- `public final void setGeoJsonSync(FeatureCollection)`가 존재하고 `checkThread()` 후 `nativeSetFeatureCollectionSync`를 호출한다. 일반 `setGeoJson(FeatureCollection)`은 `nativeSetFeatureCollection`을 호출한다.
- `Source.checkThread()`는 `ThreadUtils.checkThread()`에 위임하며 debug에서는 MainLooper 밖 호출을 거부한다. 현재 호출은 Main의 LaunchedEffect 본문에 있어 이 스레드 조건에 맞는다.

이 확인은 동기 native source 적용 경로와 호출 스레드에 한정된다. GPU atlas upload 완료, 모든 렌더 프레임의 무공백, 모든 기기/드라이버의 동일 동작을 보장하지 않는다. 실제 교체 뒤 body pixel 가시성은 Root의 새 native 실행 결과로 따로 판정해야 한다.

## 취소·참조·등록 수 검토

- image key에는 runner ID와 전체 AvatarStyle의 SHA256이 들어가며 이름/위치는 들어가지 않는다. 이미 등록된 동일 key는 다시 업로드하지 않는다.
- 비동기 artwork가 준비되기 전에는 같은 runner의 마지막 등록 image key를 유지한다. 최신 runner ID/avatar와 맞는 결과만 교체에 사용한다.
- 이미지 생성은 withContext의 취소 가능한 경계를 통해 produceState에 전달된다. 검토한 경로에서 취소된 이전 결과를 별도 native callback으로 뒤늦게 채택하는 코드는 찾지 못했다. CPU 렌더링 자체의 즉각 중단 시간은 측정하지 않았다.
- 정상 갱신 완료 뒤 image registry는 현재 runner별 참조 image만 유지한다. 사라진 runner와 교체 이전 image는 동기 source 적용 뒤 제거한다. 현재 입력의 runner 수와 별개인 무한 이력 registry는 추가하지 않았다. 모든 입력 크기의 메모리 상한/성능을 실측했다는 뜻은 아니다.
- provider의 FillExtrusionLayer를 숨기는 정책은 소스에서 확인했다. 2D 도로/이름과 별도 attribution은 유지한다. 이 조치가 입력5 소실의 원인 해결임을 주장하지 않는다.

이 범위에서 추가적인 구체적 취소·등록 수 결함은 발견하지 못했다. 최종 소스 해시가 바뀌면 이 검토는 새 파일의 동등성을 자동으로 보장하지 않는다.
