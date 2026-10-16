# 36 — 5분 시작

Compose 앱에 Kinetica의 효과와 글래스 컴포넌트를 붙이는 가장 짧은 길입니다. 자세한 설명은 [35 글래스 UI](35-glass-ui.md)와 [33 모션 효과](33-effects.md)를 보세요.

## 1. 의존성

```kotlin
dependencies {
    implementation("com.naurylab:kinetica-ui:1.0.0-alpha01")             // 글래스 컴포넌트, 효과 수정자
    implementation("com.naurylab:kinetica-render-compose:1.0.0-alpha01") // Modifier.kineticaEffects, 화면 전환, 숫자 애니메이션
    implementation("com.naurylab:kinetica-presets:1.0.0-alpha01")        // EffectPresets
}
```

`kinetica-ui`는 Compose foundation만 쓰고 Material 3에 의존하지 않습니다. Material 3 앱과 함께 써도 됩니다.

## 2. 테마 맞추기

화면 루트를 `GlassTheme`으로 한 번 감쌉니다. 다크 모드에 따라 글래스 스타일이 바뀌고, 강조색과 글꼴이 앱과 같아집니다.

```kotlin
MaterialTheme(colorScheme = scheme, typography = typography) {
    GlassTheme(
        colors = GlassColors(accent = MaterialTheme.colorScheme.primary, onAccent = MaterialTheme.colorScheme.onPrimary),
        textStyle = MaterialTheme.typography.bodyLarge, // 글꼴만 가져옵니다
    ) { App() }
}
```

다이얼로그, 시트, 토스트가 배경을 블러로 비추게 하려면 화면을 `GlassScaffold`로 감쌉니다. 감싸지 않아도 동작하며, 그때는 별도 창에 반투명 색으로 그립니다.

```kotlin
GlassScaffold(background = { Image(painterResource(R.drawable.photo), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }) { padding ->
    LazyColumn(contentPadding = padding) { /* … */ }
}
```

## 3. 자주 쓰는 10가지

**효과 붙이기.** 아무 Composable에 수정자 한 줄로 붙습니다. 클릭과 스크롤은 그대로 동작합니다.

```kotlin
Button(onClick = ::buy, modifier = Modifier.kineticaEffects(EffectPresets.button, CircleShape)) { Text("구매") }
Card(Modifier.jellyPress().glassBadge(unread)) { /* … */ }
Image(painter, null, Modifier.motionTilt().glowBorder(RoundedCornerShape(16.dp)))
```

**바텀 시트.**

```kotlin
var open by remember { mutableStateOf(false) }
GlassSheet(open, onDismissRequest = { open = false }, title = "공유") {
    Text("링크 복사")
}
```

**다이얼로그.**

```kotlin
GlassDialog(visible = confirm, onDismissRequest = { confirm = false }, title = "주문 확인") {
    Text("결제할까요?")
    GlassButton("결제", onClick = { confirm = false })
}
```

**토스트.** `show`는 suspend 함수입니다.

```kotlin
val toast = rememberGlassToastState()
val scope = rememberCoroutineScope()
GlassToastHost(toast)
GlassButton("저장", onClick = { scope.launch { toast.show("저장했어요") } })
```

**탭 바.**

```kotlin
var tab by rememberSaveable { mutableIntStateOf(0) }
GlassTabBar(listOf(GlassTab("홈", Icons.Default.Home), GlassTab("검색", Icons.Default.Search)), tab, onSelect = { tab = it })
```

**슬라이더.**

```kotlin
var volume by remember { mutableFloatStateOf(0.4f) }
GlassSlider(volume, onValueChange = { volume = it }, Modifier.fillMaxWidth())
```

**차트.**

```kotlin
GlassLineChart(points = listOf(3f, 5f, 4f, 8f, 6f), Modifier.fillMaxWidth().height(160.dp), labels = listOf("월", "화", "수", "목", "금"))
```

**달력.**

```kotlin
var selection by remember { mutableStateOf<GlassCalendarSelection>(GlassCalendarSelection.Single()) }
GlassCalendar(rememberGlassCalendarState(), selection, onSelectionChange = { selection = it })
```

**스와이프 행.** 왼쪽으로 밀면 동작 버튼이 나오고, 끝까지 밀면 첫 동작을 실행합니다.

```kotlin
SwipeActionsRow(endActions = listOf(SwipeAction("삭제", Color.Red, removesRow = true) { delete(mail) })) {
    MailRow(mail)
}
```

**여러 높이에 멈추는 바텀 시트.** 지도 앱처럼 화면 아래에 늘 떠 있습니다. 화면 높이를 쓰므로 스크롤 목록 안이 아니라 화면 루트에 둡니다.

```kotlin
val sheet = rememberGlassDetentSheetState(listOf(Detent.Peek(120.dp), Detent.Fraction(0.5f), Detent.Full))
GlassDetentSheet(sheet, title = "주변 장소") { PlaceList() }
```

## 4. 권장 수정자 순서

순서를 바꿔도 클릭은 동작합니다. 다만 아래 순서를 따라야 움직임이 배경과 리플까지 함께 적용되고, 겉에 그리는 효과가 잘리지 않습니다.

```kotlin
val shape = RoundedCornerShape(20.dp)
Box(
    Modifier
        .kineticaEffects(EffectPresets.card, shape) // 1. 움직임: kineticaEffects, jellyPress, magnetic, motionTilt, shake
        .glowBorder(shape)                          // 2. 겉에 그리는 효과: glowBorder, sparkle, glassBadge, skeleton
        .glass(shape = shape)                       // 3. 배경: glass, background
        .clip(shape)                                // 4. 자르기
        .clickable { open(item) }                   // 5. 입력: clickable, glassContextMenu, tooltipOnLongPress
        .padding(16.dp),
) { Text(item.title) }
```

`expandSource`와 `coachMarkTarget`은 위치만 알리므로 어디에 둬도 됩니다.

## 5. 흔한 실수와 해결

| 증상 | 원인과 해결 |
| --- | --- |
| 글래스가 흐리지 않고 반투명 색으로만 보인다 | 비출 배경이 없습니다. `GlassScaffold`의 `background`에 사진이나 그라디언트를 넣거나, API 31 미만 기기인지 확인합니다. |
| Material `Card`에 `Modifier.glass()`를 붙였는데 글래스가 안 보인다 | `Card`가 자기 배경색으로 덮습니다. `CardDefaults.cardColors(containerColor = Color.Transparent)`를 주거나 `Box`에 붙입니다. |
| 다크 모드인데 글래스가 밝다 | 화면을 `GlassTheme`으로 감싸지 않았거나, `GlassScaffold(style = …)`에 밝은 스타일을 직접 넘겼습니다. |
| 버튼 색이 앱 색과 다르다 | `GlassTheme(colors = GlassColors(accent = …))`로 맞춥니다. 컴포넌트마다 `accent`로 바꿀 수도 있습니다. |
| 배지나 반짝이 일부가 잘린다 | 요소 바깥까지 그리는 효과입니다. `clip`보다 앞에 두고, 부모가 자르지 않게 여백을 줍니다. |
| 목록 아래쪽 항목이 늦게 나타난다 | `EffectPresets.listItem(index)`에 목록 전체 위치를 넘겼습니다. 처음 보이는 순서를 넘기거나 `listItem(0)`을 씁니다. |
| 움직임 효과를 붙였는데 배경은 그대로다 | 효과를 `background`나 `glass` 뒤에 붙였습니다. 앞으로 옮깁니다. |
| 테스트에서 애니메이션이 멈추지 않아 기다리다 시간이 초과된다 | 반복 효과는 무한 애니메이션입니다. `compose.mainClock.autoAdvance = false`로 두고 `advanceTimeBy`로 시간을 진행합니다. |
| `Can't represent a width … and height of 2147483639` 오류가 난다 | `GlassDetentSheet`처럼 화면 높이를 쓰는 컴포넌트를 `verticalScroll` 안에 넣었습니다. 스크롤 목록 밖으로 옮깁니다. |
| 화면 아래 내비게이션 바 자리에 흰 띠가 보인다 | 기본 `enableEdgeToEdge()`는 기기에 따라(삼성 등) 제스처 내비게이션에서도 반투명 막을 깝니다. `enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))`로 부르고 API 29 이상에서 `window.isNavigationBarContrastEnforced = false`로 둡니다. |
| 애니메이션이 하나도 안 움직인다 | 기기의 '애니메이션 삭제' 접근성 설정이 켜져 있으면 반복 효과는 일부러 멈춥니다. |

## 6. 화면 배치 팁

- **스낵바와 하단 막대**: `GlassScaffold` 안이면 스낵바가 `bottomBar` 위에 저절로 뜬다. Material `NavigationBar`를 쓰면 `GlassSnackbarHost(state, bottomOffset = 80.dp)`처럼 막대 높이를 준다.
- **펼친 카드(GlassExpandHost)**: 상세 화면은 상태 표시줄 뒤까지 그린다(위쪽 사진이 화면 끝까지 닿게). 글자나 버튼이 상태 표시줄에 가리지 않게 상세 안의 해당 요소에 `Modifier.statusBarsPadding()`을 붙인다.
- **미니 플레이어와 여러 높이 시트를 함께 쓸 때**: 시트가 낮은 높이(`Detent.Peek`)일 때 제목이 플레이어 막대에 가리지 않도록 `GlassMiniPlayer(barBottomPadding = 피크 높이)`로 막대를 시트 위에 띄우거나, 시트가 펼쳐진 동안 플레이어를 숨긴다.
- **`GlassSheet`·`GlassDialog`의 `title`**: 화면에 보이는 제목이 아니라 TalkBack이 읽는 창 이름이다. 보이는 제목은 내용에 직접 넣는다.

## 7. 테스트하는 법

- 토스트·스낵바·아일랜드는 표시 시간을 Compose 시계로 잰다. `waitForIdle()`이나 `waitUntil`은 시계를 끝까지 흘려 표시 시간이 바로 지나 버리므로, `compose.mainClock.autoAdvance = false`로 멈춘 뒤 `advanceTimeBy(ms)`로 넘기며 확인한다.
- 시계를 멈춘 상태에서 스낵바 동작 버튼은 `performClick()` 대신 `performSemanticsAction(SemanticsActions.OnClick)`로 누른다.
- `GlassMiniPlayer` 막대 안의 `testTag`는 병합된 트리에 나오지 않으므로 `onNodeWithTag(tag, useUnmergedTree = true)`로 찾는다.
- 다이얼로그·시트를 `GlassScaffold` 밖에서 열면 별도 창에 뜬다. Compose UI 테스트는 여러 창의 노드를 함께 찾으므로 그대로 `onNodeWithText` 등을 쓰면 된다.

