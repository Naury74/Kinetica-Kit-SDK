# 36 — 빠른 시작

Compose 앱에 Kinetica UI 키트와 효과를 붙이는 가장 빠른 방법을 정리했습니다. 전체 컴포넌트는 [35 UI 키트](35-ui-kit.md)에, 누름 효과와 등장 효과는 [33 모션 효과](33-effects.md)에 있습니다.

## 1. 의존성

```kotlin
dependencies {
    implementation("com.naurylab:kinetica-ui:1.0.0-alpha02")             // UI 컴포넌트와 효과 수정자. 보통은 이것만 있으면 됩니다
    implementation("com.naurylab:kinetica-render-compose:1.0.0-alpha02") // 필요하면: Modifier.kineticaEffects, 화면 전환, 숫자 애니메이션
    implementation("com.naurylab:kinetica-presets:1.0.0-alpha02")        // 필요하면: EffectPresets
}
```

`kinetica-ui`는 Compose foundation 위에 만들었고 Material 3에 의존하지 않습니다. Material 3을 쓰는 앱에 함께 넣어도 됩니다.

## 2. 테마와 표면 스타일

화면 루트를 `KineticaTheme`으로 한 번 감쌉니다. 여기서 강조색과 글꼴을 앱에 맞추고 표면 스타일을 고릅니다.

```kotlin
MaterialTheme(colorScheme = scheme, typography = typography) {
    KineticaTheme(
        colors = KineticaColors(accent = MaterialTheme.colorScheme.primary, onAccent = MaterialTheme.colorScheme.onPrimary),
        textStyle = MaterialTheme.typography.bodyLarge, // 글꼴만 가져옵니다
    ) { App() }
}
```

`surface`를 주지 않으면 다크 모드 여부에 따라 `SurfaceStyles.Liquid`나 `LiquidDark` 글래스 표면을 씁니다. 블러 없는 단색이나 브랜드 색을 섞은 톤 표면을 쓰려면 아래 값을 넘깁니다.

| 원하는 모양 | `KineticaTheme(surface = …)` |
| --- | --- |
| 배경이 비치는 글래스 | `SurfaceStyles.Liquid`, `Acrylic`, `OneUiGlass`, `Vision` 등. 어두운 화면에는 `LiquidDark`처럼 이름 끝에 `Dark`가 붙은 스타일 |
| 깔끔한 단색 | `SurfaceStyles.CleanLight`, `CleanDark`, `OneUi`, `Material` 등 |
| 브랜드 색을 옅게 섞은 톤 | `SurfaceStyles.tonal(brandColor, dark = isDark)` |

글래스 표면에서 다이얼로그, 바텀 시트, 토스트 뒤로 배경이 블러로 비치게 하려면 화면을 `KineticaScaffold`로 감쌉니다. 감싸지 않아도 동작하지만, 이때는 별도 창에 반투명 색으로 그립니다.

```kotlin
KineticaScaffold(background = { Image(painterResource(R.drawable.photo), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }) { padding ->
    LazyColumn(contentPadding = padding) { /* … */ }
}
```

### 형태 고르기

표면 스타일이 색과 블러를 정한다면, 디자인 언어 `design`은 모서리, 깊이, 밀도, 컨트롤 모양 같은 형태를 정합니다. 둘은 따로 고릅니다.
기본값은 `KineticaDesigns.Cupertino`입니다.

```kotlin
KineticaTheme(surface = SurfaceStyles.CleanLight, design = KineticaDesigns.Fluent) { App() }
```

프리셋은 `Cupertino`, `Material`, `OneUi`, `Fluent`, `Brutal`, `Soft` 여섯 가지이고, `KineticaDesigns.Cupertino.copy(…)`로 일부만 바꿀 수 있습니다.
컴포넌트에 `shape`를 직접 넘기면 디자인 언어보다 그 값이 우선합니다. 자세한 내용은 [35 UI 키트의 디자인 언어와 형태](35-ui-kit.md#디자인-언어와-형태)에 있습니다.

### 화면 전환 고르기

| 쓰는 곳 | 넣는 값 |
| --- | --- |
| `PushNavigation`으로 화면을 쌓을 때 | `transition = KTransitions.Push`(기본), `CardStack`, `SheetStack`, `Zoom`, `cover(KTransitionEdge.Bottom)` 등 |
| 탭이나 단계처럼 내용만 바꿀 때 | `KTransitionContent(tab, transition = KTransitions.TabSlide, isForward = { a, b -> b > a })` |
| Navigation Compose `NavHost` | `rememberKNavTransition(KNavTransitions.Push)`의 `enter`, `exit`, `popEnter`, `popExit` |
| 카드가 상세 화면으로 커질 때 | `KExpandHost(transform = KContainerTransform.Card)` |
| 서버 장면 사이 | 장면 JSON의 `transition`, 또는 `RemoteKineticaPlayer(bundle, sceneId, transition = …, forward = …)` |

모든 전환은 뒤로 갈 때 거꾸로 재생되고 예측형 뒤로 가기 제스처를 따라 움직입니다. '애니메이션 삭제' 설정이 켜져 있으면 애니메이션 없이 바로 바뀝니다. 전체 목록은
[35 UI 키트의 화면 이동 전환](35-ui-kit.md#화면-이동-전환)과 [34 장면 JSON의 장면 전환](34-scene-json.md#장면-전환-transition)에 있습니다.

## 3. 자주 쓰는 예

**효과 붙이기.** 어떤 Composable이든 수정자 한 줄로 붙습니다. 클릭과 스크롤은 그대로 동작합니다.

```kotlin
Button(onClick = ::buy, modifier = Modifier.kineticaEffects(EffectPresets.button, CircleShape)) { Text("구매") }
Card(Modifier.jellyPress().kBadge(unread)) { /* … */ }
Image(painter, null, Modifier.motionTilt().glowBorder(RoundedCornerShape(16.dp)))
```

**바텀 시트.**

```kotlin
var open by remember { mutableStateOf(false) }
KSheet(open, onDismissRequest = { open = false }, title = "공유") {
    Text("링크 복사")
}
```

**다이얼로그.** `title`은 화면에 나오지 않고 TalkBack이 창 이름으로 읽습니다. 화면에 보일 제목은 내용에 직접 넣습니다.

```kotlin
KDialog(visible = confirm, onDismissRequest = { confirm = false }, title = "주문 확인") {
    Text("결제할까요?")
    KButton("결제", onClick = { confirm = false })
}
```

**토스트.** `show`는 suspend 함수입니다.

```kotlin
val toast = rememberKToastState()
val scope = rememberCoroutineScope()
KToastHost(toast)
KButton("저장", onClick = { scope.launch { toast.show("저장했어요") } })
```

**탭 바.**

```kotlin
var tab by rememberSaveable { mutableIntStateOf(0) }
KTabBar(listOf(KTab("홈", Icons.Default.Home), KTab("검색", Icons.Default.Search)), tab, onSelect = { tab = it })
```

**슬라이더.**

```kotlin
var volume by remember { mutableFloatStateOf(0.4f) }
KSlider(volume, onValueChange = { volume = it }, Modifier.fillMaxWidth())
```

**차트.**

```kotlin
KLineChart(points = listOf(3f, 5f, 4f, 8f, 6f), Modifier.fillMaxWidth().height(160.dp), labels = listOf("월", "화", "수", "목", "금"))
```

**달력.**

```kotlin
var selection by remember { mutableStateOf<KCalendarSelection>(KCalendarSelection.Single()) }
KCalendar(rememberKCalendarState(), selection, onSelectionChange = { selection = it })
```

**스와이프 행.** 왼쪽으로 밀면 동작 버튼이 나오고, 끝까지 밀면 첫 번째 동작을 실행합니다.

```kotlin
SwipeActionsRow(endActions = listOf(SwipeAction("삭제", Color.Red, removesRow = true) { delete(mail) })) {
    MailRow(mail)
}
```

**여러 높이에 멈추는 바텀 시트.** 지도 앱처럼 화면 아래에 늘 떠 있습니다. 화면 높이를 기준으로 크기를 정하므로 스크롤 목록 안이 아니라 화면 루트에 둡니다.

```kotlin
val sheet = rememberKDetentSheetState(listOf(Detent.Peek(120.dp), Detent.Fraction(0.5f), Detent.Full))
KDetentSheet(sheet, title = "주변 장소") { PlaceList() }
```

**통화 화면.** 부품을 하나씩 조합해도 되고, 완성된 `KCallScreen`에 `KCallState`와 `KCallActions`만 넘겨도 됩니다. 영상은 호스트가 `video` 슬롯에 렌더러를 넣습니다. `KCallActions`에서 넘기지 않은 콜백의 버튼은 화면에 나오지 않습니다. 음량 `audioLevel`은 람다로 넘겨야 값이 바뀔 때마다 화면 전체를 다시 구성하지 않습니다. 부품 목록과 직접 조합하는 예는 [35 UI 키트의 통화](35-ui-kit.md#통화)에 있습니다.

```kotlin
KCallScreen(
    state = KCallState(KCallPhase.Connected, participants, startedAtMillis = connectedAt),
    actions = KCallActions(onEnd = vm::hangUp, onToggleMute = vm::toggleMute, onToggleCamera = vm::toggleCamera),
    video = { p -> VideoRenderer(p.id) },
    audioLevel = { id -> levels.value[id] ?: 0f },
)
```

## 4. 권장 수정자 순서

순서를 바꿔도 클릭은 동작합니다. 다만 아래 순서를 지켜야 배경과 리플이 움직임을 함께 따라가고, 바깥에 그리는 효과가 잘리지 않습니다.

```kotlin
val shape = RoundedCornerShape(20.dp)
Box(
    Modifier
        .kineticaEffects(EffectPresets.card, shape) // 1. 움직임: kineticaEffects, jellyPress, magnetic, motionTilt, shake
        .glowBorder(shape)                          // 2. 바깥에 그리는 효과: glowBorder, sparkle, kBadge, skeleton
        .glass(shape = shape)                       // 3. 배경: glass, kSurface, background
        .clip(shape)                                // 4. 자르기
        .clickable { open(item) }                   // 5. 입력: clickable, kClickable, kContextMenu, tooltipOnLongPress
        .padding(16.dp),
) { Text(item.title) }
```

`expandSource`와 `coachMarkTarget`은 위치만 알리므로 어디에 둬도 됩니다.

## 5. 흔한 실수와 해결

| 증상 | 원인과 해결 |
| --- | --- |
| 글래스에 블러가 없고 반투명 색으로만 보인다 | 비칠 배경이 없습니다. `KineticaScaffold`의 `background`에 사진이나 그라디언트를 넣습니다. Android 12 미만 기기는 블러를 쓸 수 없어 늘 반투명 색으로 칠합니다. |
| Material `Card`에 `Modifier.glass()`를 붙였는데 글래스가 안 보인다 | `Card`가 자기 배경색으로 글래스를 덮습니다. `CardDefaults.cardColors(containerColor = Color.Transparent)`를 주거나 `Box`에 붙입니다. |
| 다크 모드인데 표면이 밝다 | 화면을 `KineticaTheme`으로 감싸지 않았거나, `KineticaTheme(surface = …)`나 `KineticaScaffold(surface = …)`에 밝은 표면을 직접 넘겼습니다. 단색과 톤 표면은 다크 모드를 따라가지 않으므로 `CleanDark`나 `tonal(색, dark = true)`처럼 어두운 스타일을 따로 넘깁니다. |
| 버튼 색이 앱 색과 다르다 | `KineticaTheme(colors = KineticaColors(accent = …))`로 맞춥니다. 컴포넌트마다 `tone = KTone.Destructive`처럼 색 역할을 바꿀 수도 있습니다. |
| Material `Card(onClick)`를 누르면 리플이 나온다 | Material 컴포넌트는 `LocalIndication` 대신 자기 리플을 씁니다. 이럴 때는 `KSurface(onClick = …)`나 `Modifier.kClickable(shape)`을 씁니다. `KineticaTheme` 안에서 `MaterialTheme`을 다시 감싸도 리플이 돌아오므로 `MaterialTheme`은 늘 바깥에 둡니다. |
| 배지나 반짝이의 일부가 잘린다 | 요소 바깥까지 그리는 효과입니다. `clip`보다 앞에 두고, 부모가 잘라 내지 않도록 여백을 줍니다. |
| 목록 아래쪽 항목이 늦게 나타난다 | `EffectPresets.listItem(index)`에 목록 전체 위치를 넘겼습니다. 처음 보이는 순서를 넘기거나 `listItem(0)`을 씁니다. |
| 움직임 효과를 붙였는데 배경은 그대로다 | 효과를 `background`나 `glass` 뒤에 붙였습니다. 앞으로 옮깁니다. |
| 테스트에서 애니메이션이 멈추지 않아 기다리다 시간이 초과된다 | 반복 효과는 무한 애니메이션입니다. `compose.mainClock.autoAdvance = false`로 두고 `advanceTimeBy`로 시간을 진행합니다. |
| `Can't represent a width … and height of 2147483639` 오류가 난다 | `KDetentSheet`처럼 화면 높이를 기준으로 하는 컴포넌트를 `verticalScroll` 안에 넣었습니다. 스크롤 목록 밖으로 옮깁니다. |
| 화면 아래 내비게이션 바 자리에 흰 띠가 보인다 | 삼성 기기처럼 일부 기기에서는 기본 `enableEdgeToEdge()`가 제스처 내비게이션에서도 반투명 스크림을 깝니다. `enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))`로 부르고 API 29 이상에서 `window.isNavigationBarContrastEnforced = false`로 둡니다. |
| 애니메이션이 하나도 움직이지 않는다 | 기기의 '애니메이션 삭제' 접근성 설정이 켜져 있으면 반복 효과는 의도적으로 멈춥니다. |

## 6. 화면 배치 팁

- **스낵바와 하단 바**: `KineticaScaffold` 안에서는 스낵바가 `bottomBar` 위에 자동으로 뜹니다. Material `NavigationBar`를 쓴다면 `KSnackbarHost(state, bottomOffset = 80.dp)`처럼 바 높이를 넘깁니다.
- **`KExpandHost`로 펼친 카드**: 상세 화면은 위쪽 사진이 화면 끝까지 닿도록 상태 표시줄 뒤까지 그립니다. 글자나 버튼이 상태 표시줄에 가리지 않게 하려면 그 요소에 `Modifier.statusBarsPadding()`을 붙입니다.
- **미니 플레이어와 여러 높이 시트를 함께 쓸 때**: 시트가 가장 낮은 `Detent.Peek` 높이에 있으면 시트 제목이 플레이어 바에 가립니다. `KMiniPlayer(barBottomPadding = 피크 높이)`로 바를 시트 위에 띄우거나, 시트가 펼쳐진 동안 플레이어를 숨깁니다.
- **`KSheet`와 `KDialog`의 `title`**: 화면에 보이는 제목이 아니라 TalkBack이 읽는 창 이름입니다. 보이는 제목은 내용에 직접 넣습니다.

## 7. 테스트하는 법

- 토스트, 스낵바, 아일랜드는 표시 시간을 Compose 시계로 잽니다. `waitForIdle()`이나 `waitUntil`을 쓰면 시계가 끝까지 흘러 표시 시간이 곧바로 지나 버립니다. `compose.mainClock.autoAdvance = false`로 시계를 멈춘 뒤 `advanceTimeBy(ms)`로 시간을 넘기며 확인합니다.
- 시계를 멈춘 채 상태를 바꿨다면 `compose.waitForIdle()`로 재구성을 먼저 끝낸 뒤 `advanceTimeBy`를 부릅니다.
- 시계를 멈춘 상태에서 스낵바 동작 버튼은 `performClick()` 대신 `performSemanticsAction(SemanticsActions.OnClick)`으로 누릅니다.
- `KMiniPlayer` 바 안의 `testTag`는 병합된 트리에 나오지 않으므로 `onNodeWithTag(tag, useUnmergedTree = true)`로 찾습니다.
- 다이얼로그와 시트를 `KineticaScaffold` 밖에서 열면 별도 창에 뜹니다. Compose UI 테스트는 여러 창의 노드를 함께 찾으므로 `onNodeWithText` 같은 함수를 그대로 쓰면 됩니다.
- `rememberKWindowLayout()`을 쓰는 화면은 창 크기를 읽습니다. 계측 테스트에서 넓은 화면을 흉내 내려면 `ForcedSize`가 아니라 `WindowSize`로 크기를 바꿉니다.
