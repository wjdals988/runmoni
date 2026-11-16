# 0.7.1 입력1 공유 PNG 6개 → 입력3 동등성 검토

**판정: 입력1에서 실제로 통과한 공유 PNG 6개를 입력3 소스의 동등성 보존 결과로 유지할 수 있다.** 입력3의 새 실행 성공이나 전체 최신 입력의 단일 PASS 판정이 아니다. 검사한 freeze3와 실제 파일 해시가 유지된다는 범위의 정적 판단이다.

이 검토에서는 제품/검사/기존 proof 편집, Git, 빌드, 기기, 네트워크 작업을 하지 않았다. 이 텍스트만 작성했다. 새 Android 성공·binary/CI·배포 커밋은 추정하지 않는다.

## 실제 hash 기준

근거 디렉터리는 `docs/app-review/2026-11-16-pixel-couture/`다.

| 입력 파일 | 실제 SHA256 |
| --- | --- |
| source-freeze1.json | `4b1c940e466ce8e123b9027287e092d59a94f4eb95d0f19b7e308832508866e7` |
| source-freeze2.json | `adfc447032901a7e703f81f01c69aba07dfef7962eee590aff3f81243f18218a` |
| source-freeze3.json | `e715f1b1e292bc861ae14dc3c0176df1d9e4528583292e76e534b63b952824d8` |

세 입력의 전체 6개 source map을 실제로 비교했다. 검토 시점에는 freeze3에 기록된 모든 실제 파일의 SHA256도 일치했다.

| map | 세 입력의 파일 수 | 입력1→3 차이 | 입력2→3 차이 |
| --- | ---: | --- | --- |
| productSources | 115 | 기존 검토의 생산4개 | 없음 |
| testSources | 100 | 검사5개 | 검사3개 |
| rendererAssets | 83 | 없음 | 없음 |
| resources | 34 | 없음 | 없음 |
| configurationSources | 11 | 없음 | 없음 |
| approvedSourceCopies | 1 | 없음 | 없음 |

입력1→3 생산 차이는 다음 4개뿐이며 각 입력3 SHA는 입력2와 같다. 경로는 `app/src/main/java/com/runmoni/app/` 기준이다.

| 생산 파일 | 입력1 SHA256 | 입력2/3 SHA256 |
| --- | --- | --- |
| avatar/ApprovedAvatarRenderer.kt | `2c223e5343addc4c46514903282b0bbd66b83798bae6ff46b3f07f942383a7ea` | `079d69297199332033957b388fc2b3dd069e3e8a2f6ff1c9c469695e1b0070be` |
| avatar/AvatarHeadwearLayers.kt | `4449160d6e13d1fd65af874afe07984f4eeac155f6b2f44377e85c2b436c2091` | `f22b131bad5f8775ac87402e0323985dfd1d5839bac719121cbd356d88e1990e` |
| avatar/RunningKitArtwork.kt | `bc552acaec6e8f75f57741bb447bb461babef43c75dd62db7553c5b3bed97e21` | `b9ebfad37c86e09fb1647d7272763713ede93bf9fb6b563151b848bf12d696b9` |
| ui/MiniMi.kt | `011d1c8b30cd3bf785a55880d7925a8f30b310382dadb1e696c6e3875f3a2fe5` | `b594b9ad01dc0f8543af97767a4c8c3e662daf81150dc9e6f34bf4a2b65191a2` |

입력2→3의 변경은 Android 검사3개 파일뿐이다. 경로는 `app/src/androidTest/java/com/runmoni/app/ui/` 기준이다.

| 검사 파일 | 입력2 SHA256 | 입력3 SHA256 |
| --- | --- | --- |
| AvatarPlaybackTest.kt | `4e7605babbfc115170d39dfdec55c3123cf4fe36a1797c71d134fd388e2d77ee` | `5a860418558531d2c7acc7a6bad0262601510ada4c2fd5e300704b0d056e93c0` |
| MiniMiLoadingTest.kt | `6b92c6dbdd6aa0c684db95bb15599cead062dff89cdd1aa5f647c942538eb853` | `561ebc7990fd863d28728e81d3b0439a82dcee655205f9ad71576ecc25a25fbd` |
| RunmoniDesignTest.kt | `7fba6cd34a9b64462753b9fe1dff03eb521b112ebd3a95fec93c81f7f3b0847a` | `081d0914db64c938a89d1341f4f55efb8e7ecc08a91eb411c2e7ac25ed16ec95` |

입력1→3 검사 차이5개는 위3개와 앞서 입력2에서 바뀐 `WardrobeRendererTest.kt`, `AvatarHeadwearLayersTest.kt`다. 입력3에서 이 두 파일의 SHA는 입력2와 같다.

## 공유 6개에 적용되는 생산 경계

이전 전체 검토 `work/pixel-couture/share-retention-review.md`의 SHA256은 `cbf46d3e4b8de9e701832f357db2fa7eb8076b3c960d1bc9163652c2cb07b79f`다. 그 검토의 생산4개 분석을 입력3에 그대로 적용할 수 있다. 실제 생산 map이 입력2와 같기 때문이다.

- 공유 검사에서 실제 renderer를 부르는 세 곳은 `RunShareCardRendererTest.kt:122-123`, `:162-163`, `:262-263`이며 frame2/running=true와 top1/bottom1/socks2/shoes2 및 남녀만 지정한다. 기본값과 variantFor 해석으로 headwear0/accessory0/costume0/waist0/hydration0이다.
- CAP/VISOR의 bounded brim은 이 스타일에서 호출되지 않는다. head VERSION은 renderer cache key에만 들어가며 검사마다 생성한 renderer의 해당 픽셀 계산을 바꾸지 않는다. compiled class 바이트 동일성을 뜻하지 않는다.
- handProtection 인자가 null이 되는 차이는 waist/hydration draw가 없는 body Raster에서만 발생한다. 이 스타일의 픽셀 쓰기는 달라지지 않는다. top1/bottom1 때문에 기존 coverage/hand 무결성 로드는 계속 실행된다.
- RunningKitArtwork의 제거된 최초 generation 발행과 MiniMi의 제거된 Compose 구독/key는 직접 Bitmap 검사에서 소비하지 않는다. 동기 mask 반환·프레임 추출·팔레트·공유 compositor·원화/마스크 자원은 같은 검토 경계를 유지한다.
- 나머지 공유 세 메서드는 photo/theme/showAvatar=false/null 또는 직접 만든 mutable Bitmap을 사용한다.

다음 공유 경로 파일은 입력1/2/3 모두 같은 SHA다.

| 파일 | 공통 SHA256 |
| --- | --- |
| sharing/RunShareCardRenderer.kt | `3b5813897c5fc89557abe34eb530912979395d0669585eba97101042a49bd68d` |
| androidTest sharing/RunShareCardRendererTest.kt | `4c1d2be2e8cc6a2f1151bd0540fced1174e66e107f4edfca4782cdd31ea4a923` |
| avatar/AvatarEquipmentLayers.kt | `333341ff0055e8f081ddacf121489bc4925a251b6298a8cc09499311d5b5608e` |
| avatar/WardrobeCoverage.kt | `fcb6c2262faca8b4b7d7c7a9bdc64e4859197a84be5995ada2e3028465181624` |

## 새 검사 변경이 공유 경로를 바꾸지 않는 이유

공유 검사는 `@RunWith(AndroidJUnit4::class)`의 독립 `RunShareCardRendererTest`다. RunmoniDesignTestBase를 상속하거나 Compose rule, MiniMi, frame callback, 재생 준비 gate를 사용하지 않는다. 변경된 ui 검사 클래스/도우미를 import하거나 호출하지도 않는다. 공유 group의 대상도 이 공유 클래스 하나다. 따라서 세 ui 검사 본문/도우미 변경을 공유6개에 실행되는 공통 harness 변경으로 볼 근거가 없다. 실제 설정/runner map도 입력2와 같다.

현재 `AvatarPlaybackTest.kt:99-119`는 준비 상태를 별도로 기다린 뒤 새 재생 관측 구간에서 6000ms를 적용한다. 이 경계는 이전 준비를 포함한 실패 시간과 같은 성능 지표로 비교할 수 없다. `MiniMiLoadingTest.kt:52-55`와 `RunmoniDesignTest.kt:57`의 readiness 선택도 공유6개가 소비하지 않는 UI 검사 경계다. 이 문서는 새 UI 검사가 통과했거나 timeout/로딩 원인이 해결됐다고 판정하지 않는다.

## 유지할 결과와 실패 경계

입력1의 `android-review1.json` share-png-renderer group은 passed/actual6이고 원문 `android-review1-share-png-renderer.txt`의 `OK (6 tests)`와 SHA256 `263408281ccf77735542cfedf93b3971b84431d459fda22e8d8b511684b04a28`가 일치함을 다시 확인했다. 이전0.7 결과를 가져온 것이 아니라 현재0.7.1 입력1의 새 실행 결과다.

- **입력1 실행·입력3 소스 동등성으로 보존한 공유6개**로 기록한다. 입력3 새 실행으로 세지 않는다.
- 입력1의 6000ms 실패, 실패 묶음의 부분 성공, 분리 retry 및 이후 실패/미완료 원문은 보존한다. 이 판정으로 삭제하거나 최종 성공 개수로 승격하지 않는다.
- CAP/VISOR 착용 공유 PNG의 변경된 bounds/배치, Compose 로딩/재생, 새 UI 검사의 준비 시간·성능, 실제 휴대폰/Google/새 binary 실행은 이 보존6개의 범위가 아니다.
- 새 Android 성공 근거는 아직 이 문서에 없다. Root가 실제 binary 및 별도 실행 근거를 확인해야 한다. freeze3 뒤 소스/자원/검사/설정이 달라지면 이 판정을 자동 적용하지 않는다.

위 범위에서 공유6개 보존을 거부할 구체적인 출력 차이는 발견하지 못했다.
