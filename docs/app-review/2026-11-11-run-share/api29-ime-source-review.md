# Classic fitting에서 Compose IME 재적용 가능성

SOURCE10은 동결된 상태이며 제품/테스트/실행을 변경하지 않았다. 공개 출시 Core1.16/Compose Foundation Layout1.7 소스를 WORK에 읽어 정적 우려만 확인했다. Root의 실제 Native5가 최종 관측이다.

## 확인한 구현

`core116-WindowInsetsCompat.java`170~179에서 `toWindowInsetsCompat(platformInsets,view)`는 하위 뷰에 전달된 insets를 감싸면서 raw rootWindowInsets와 rootView visible bounds도 복사한다.979~998의 IME heuristic은 다음 순서다.

1. overridden IME가 있으면 사용한다.
2. systemWindow.bottom>rootStable.bottom이면 systemWindow.bottom을 IME로 쓴다.
3. 그렇지 않아도 rootVisible.bottom>rootStable.bottom이면 rootVisible.bottom을 IME로 쓴다.

따라서 DecorView가 child system-window insets를0으로 소비해도 root visible441px을 근거로 IME441px을 다시 만들 수 있다. 소비된 child Insets만으로 IME0은 보장되지 않는다.

`compose17-WindowInsets.android.kt`430/448~449는 safeDrawing 자체가 systemBars.union(ime).union(displayCutout)임을 보여준다.541~560은 일반 경로에서 전달받은 WindowInsetsCompat의 IME를 그대로 holder에 반영한다. testInsets 기본은false이며 앱/검사 소스에 이를 켜는 코드는 찾지 못했다. safeDrawing에서 명시적 `.union(ime)`만 빼는 것으로는 해결되지 않는다.

## 소유권 권고

실제 Activity Window가 있는 API29에서 classic fitting 정책을 적용한 경우 framework가 system bars/IME 공간을 맡도록 하고,Editor의 Compose padding은 명시적으로 `WindowInsets(0,0,0,0)`을 사용한다. API30+ 및 Window를 찾을 수 없어 정책을 적용하지 않은 preview/host는 기존 `safeDrawing.union(ime)`을 유지한다. raw root inset bridge는 사용하지 않는다.

예시 의도(제품 패치 아님):

```kotlin
val frameworkFitsEditor = LegacyRunShareWindowPolicy() // API29 && 실제 window를 찾아 정책 적용
val editorInsets = if (frameworkFitsEditor) WindowInsets(0, 0, 0, 0)
    else WindowInsets.safeDrawing.union(WindowInsets.ime)
```

API29라는 이유만으로 무조건0을 쓰지 말고 실제 window policy 적용 여부와 묶는다. raw offset,IME 숨김,Native 검사 완화는 필요하지 않다. scoped owned flags/softInputMode 복원 계약은 그대로 유지한다. Native5의 실제 header/저장/공유 geometry와 실제 Main show/hide/복원을 확인해야 한다. 이 문서는 중복이 실제 발생했다는 실행 결과나 후보의 통과를 주장하지 않는다.
