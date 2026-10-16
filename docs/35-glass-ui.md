# 35 — 글래스 UI와 고급 효과 (`kinetica-ui`)

배경을 블러 처리해 비쳐 보이는 글래스 UI와 고급 애니메이션을 골라 붙여 쓰는 모듈이다. 앱에서 직접 만들기 까다로운 것들을 모았다. Compose 전용이며 다른 Kinetica 모듈 없이 쓸 수 있다.

```kotlin
implementation("com.naurylab:kinetica-ui:1.0.0-alpha01")
```

## 화면 구조

```kotlin
GlassScaffold(
    // style을 빼면 GlassTheme이 정한 스타일을 쓴다(테마가 없으면 GlassStyles.Liquid).
    background = { Image(painterResource(R.drawable.photo), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) },
    topBar = { GlassSurface(shape = CircleShape) { Text("제목") } },
    bottomBar = { GlassTabBar(tabs, selected, onSelect = { selected = it }) },
) { padding ->
    LazyColumn(contentPadding = padding) { items(...) { GlassSurface { ... } } }
}
```

- `GlassScaffold`는 화면을 배경, 배경과 콘텐츠, 화면 전체의 세 층으로 나눠 기록한다(배경 ⊂ 배경+콘텐츠 ⊂ 화면 전체). 콘텐츠 안의 글래스는 배경을,
  상단 바와 하단 바는 배경과 콘텐츠를, 다이얼로그·바텀 시트·토스트는 화면 전체를 블러 처리해 비춘다. 그래서 글래스를 어디에 두어도 자기 자신을 비추지 않는다.
- 콘텐츠는 바 아래까지 그려지므로 스크롤하면 바 뒤로 비쳐 보인다. 바 높이는 `padding`으로 받는다. 상태 표시줄 아래로 들어간 콘텐츠는
  `edgeFade`로 흐려지며 사라진다.
- 사진 위에 글래스를 올릴 때처럼 특정 이미지를 비추려면 그 이미지에 `Modifier.glassBackdrop(rememberGlassBackdrop())`를 붙이고,
  만든 backdrop을 글래스에 넘긴다(`GlassSurface(backdrop = photo)`).

## 글래스 스타일 `GlassStyle`

| 프리셋 | 특징 |
| --- | --- |
| `GlassStyles.Liquid` / `LiquidDark` | iOS 26 Liquid Glass. 맑고 선명하며 가장자리에 렌즈 굴절(API 33+)과 또렷한 글로우 테두리가 있다 |
| `GlassStyles.Acrylic` / `AcrylicDark` | Windows 11 Acrylic. 블러가 강하고 옅은 색이 덮이며 고운 노이즈가 있다 |
| `GlassStyles.OneUi` / `OneUiDark` | One UI. 블러가 부드럽고 바탕이 밝으며 테두리가 없다 |
| `GlassStyles.Vision` | visionOS. 어둡고 깊은 글래스에 은은한 글로우 테두리 |
| `GlassStyles.Material` / `MaterialDark` | Material 3 Expressive. 은은한 색조 표면에 테두리 없이 큰 그림자 |
| `GlassStyles.Frosted` | 예전 iOS의 반투명 유리. 블러가 아주 강하고 하얗게 덮인다 |
| `GlassStyles.Clear` | 블러만 적용. 다이얼로그 뒤 배경에 쓴다 |
| `GlassStyles.Mica` / `MicaDark` | Windows 11 Mica. 블러가 매우 크고 바탕색을 넉넉히 덮으며 테두리가 없다 |
| `GlassStyles.HarmonyOs` | HarmonyOS. 맑고 밝은 블러에 부드러운 빛 |
| `GlassStyles.Obsidian` | 아주 어둡고 진한 유리에 은은한 보라 테두리 |
| `GlassStyles.Pastel` | 옅은 분홍 틴트의 밝은 유리 |

`GlassStyle`의 값은 `blur`(블러), `saturation`(채도 배율), `brightness`(밝기), `tint`(덮는 색), `edgeLight`와 `edgeWidth`(글로우 테두리), `shadow`(바깥쪽에만 지는 그림자),
`grain`(노이즈), `refraction`(가장자리 굴절, 0..1), `fallback`(블러를 쓸 수 없을 때의 색), `onGlass`(글자와 아이콘 색)다. 바꾸려면 `copy`를 쓴다.
블러는 Android 12(API 31)부터, 굴절은 Android 13(API 33)부터 동작한다. 그보다 낮은 버전에서는 `fallback` 색의 반투명 표면으로 그린다.
다이얼로그와 바텀 시트는 `style.forPopup()`으로 블러를 더 강하게, 색을 더 진하게 덮어서 위에 놓인 글자가 잘 읽히게 한다.

## 테마 맞추기 `GlassTheme`

`GlassTheme`으로 글래스 스타일, 강조색, 글꼴을 한 번에 정한다. 화면 루트에 한 번 감싸면 그 안의 `GlassScaffold`와 모든 컴포넌트가 기본값으로 쓴다.
컴포넌트에 직접 넘긴 값이 있으면 그 값이 우선한다.

```kotlin
MaterialTheme(colorScheme = scheme, typography = typography) {
    GlassTheme(
        darkTheme = isSystemInDarkTheme(),            // 기본값. 앱이 다크 모드를 직접 정하면 그 값을 넘긴다
        colors = GlassColors(
            accent = MaterialTheme.colorScheme.primary,
            onAccent = MaterialTheme.colorScheme.onPrimary,
            destructive = MaterialTheme.colorScheme.error,
        ),
        textStyle = MaterialTheme.typography.bodyLarge, // 글꼴만 가져오고 크기와 줄 높이는 컴포넌트가 정한다
    ) { App() }
}
```

- `style`을 주지 않으면 `darkTheme`에 따라 `GlassStyles.Liquid`나 `LiquidDark`를 고른다. 다른 계열을 쓰려면 `style = if (dark) GlassStyles.OneUiDark else GlassStyles.OneUi`처럼 넘긴다.
- `GlassColors`의 `accent`는 버튼, 칩, 슬라이더, 진행 표시, 입력란 포커스, 달력 선택에, `positive`는 스위치와 밀어서 확인하기에,
  `destructive`는 배지, 삭제 메뉴, 입력 오류에 쓴다.
- kinetica-ui는 Material 3에 의존하지 않으므로 Material 테마 값은 위처럼 호스트가 넘긴다.
- 직접 만든 컴포넌트를 같은 색으로 맞추려면 `GlassTheme.colors.accent`, `GlassTheme.style`, `GlassTheme.textStyle`을 읽는다.

## 눌림 표시 `GlassIndication`

kinetica-ui의 누를 수 있는 요소는 사각 리플 대신 `GlassIndication`을 쓴다. 요소 모양으로 잘린 옅은 오버레이가 덮였다가 사라지고,
빠르게 탭해도 110ms는 보인다. 키보드 포커스와 마우스 호버도 옅게 표시한다. '애니메이션 삭제' 설정이 켜져 있으면 페이드 없이 바로 켜고 끈다.
오버레이 색을 주지 않으면 `LocalGlassContentColor`를 보고 고른다. 글자가 어두우면(밝은 글래스) 어두운 오버레이를, 글자가 밝으면(어두운 글래스나 강조색 버튼) 흰 오버레이를 쓴다.

`GlassTheme`과 `GlassScaffold`는 `LocalIndication`에 `GlassIndication()`을 넣는다. 그래서 그 안에서 호스트가 쓴 일반 `Modifier.clickable`도
회색 사각 리플이 나오지 않는다. 호스트 리플을 그대로 쓰려면 `indication = null`을 넘긴다.

```kotlin
// 모양을 알려 주면 모서리 밖은 칠하지 않는다
Row(Modifier.clip(shape).glassClickable(shape, onClickLabel = "열기") { open() })
Box(Modifier.clickable(interactionSource = null, indication = GlassIndication(CircleShape)) { ... })

// 길게 누르기, 선택, 토글
Modifier.glassCombinedClickable(shape, onLongClick = ::showMenu) { open() }
Modifier.glassSelectable(selected, CircleShape, role = Role.Tab) { select() }
Modifier.glassToggleable(checked, shape, role = Role.Checkbox) { checked = it }

GlassTheme(indication = null) { App() } // 호스트 LocalIndication을 그대로 둔다
```

- `GlassIndication(shape, color, pressedAlpha, pressedScale)`: `pressedAlpha` 기본값은 0.1이다. `pressedScale`은 수정자 체인에서
  표시보다 뒤에 그려지는 내용만 줄이므로, 배경까지 함께 줄이려면 `Modifier.indication`을 배경보다 앞에 둔다.
- 버튼처럼 통째로 눌리는 카드는 `GlassSurface(onClick = { ... }, shape = ...)`를 쓰면 모양에 맞는 눌림 표시가 붙는다.
  아이콘만 있는 버튼은 `GlassIconButton(icon, "공유", onClick = ...)`을 쓴다. 지름 44dp 원 안에 22dp 아이콘을 그린다.

컴포넌트 치수는 다음 기준을 따른다.

| 항목 | 기준 |
| --- | --- |
| 터치 영역 | 44dp 이상, 목록 행과 탭은 48dp |
| 아이콘 버튼 | 지름 44dp, 아이콘 22dp |
| 글자 버튼 안 아이콘 | 20dp(Small 16dp), 글자와 간격 8dp(Small 6dp), 아이콘 쪽 여백은 4dp 줄여 시각적으로 가운데 |
| 탭 바와 레일 아이콘 | 22~24dp, 이름과 간격 2~4dp |
| 안쪽 모서리 | 바깥 반지름에서 안쪽 여백을 뺀 값(최소 4dp). 예: 22dp 메뉴 안 6dp 여백의 항목은 16dp |

## 다이얼로그, 바텀 시트, 토스트, 탭 바

| 컴포넌트 | 동작 |
| --- | --- |
| `GlassDialog(visible, onDismissRequest, origin = …)` | 누른 버튼(`Modifier.glassOrigin { origin = it }`)에서 패널이 튀어나오듯 커지고, 배경은 서서히 흐려지며 어두워진다. 바깥을 누르거나 뒤로 가기로 닫는다 |
| `GlassSheet(visible, onDismissRequest)` | iOS 시트처럼 아래에서 올라오고, 뒤쪽 콘텐츠는 조금 물러나며 모서리가 둥글어진다. 아래로 끌어서 닫고, 덜 끌고 놓으면 던진 속도를 이어받아 제자리로 돌아온다. 넓은 화면에서는 `GlassSheetDefaults.MaxWidth`(640dp)를 넘지 않고 가운데에 놓인다. `title`은 TalkBack이 읽는 창 이름이고 화면에는 보이지 않는다 |
| `GlassToastHost(state)` + `state.show("저장했어요")` | 위에서 미끄러져 내려오는 토스트. TalkBack이 내용을 읽어 준다 |
| `GlassTabBar(tabs, selectedIndex, onSelect)` | 화면 위에 떠 있는 탭 바. 선택한 탭의 인디케이터가 늘어났다 줄어들며 액체처럼 이동한다 |
| `GlassSurface(shape, style)` | 글래스 표면 하나. 안쪽 글자 색은 `LocalGlassContentColor`로 받는다 |
| `Modifier.glassContextMenu(items)` | 길게 누르면 햅틱과 함께 요소가 떠오르고 배경이 흐려지며, 요소 위나 아래에 글래스 메뉴(`GlassMenuItem`)가 뜬다. `destructive` 항목은 빨간 글자로 보인다. TalkBack에서는 사용자 정의 동작으로 제공한다 |
| `GlassSegmentedControl(options, selectedIndex, onSelect)` | 선택한 칸의 인디케이터가 늘어났다 줄어들며 이동하는 세그먼트 버튼 |
| `GlassSwitch(checked, onCheckedChange)` | 누르는 동안 손잡이가 늘어나고, 출렁이며 반대쪽으로 넘어가는 iOS 26 스타일 스위치 |
| `GlassSlider(value, onValueChange, valueRange, steps)` | 잡으면 트랙이 굵어지고 손잡이가 투명하게 커진다. 끝을 넘어 끌면 고무줄처럼 늘어났다가 튕겨 돌아온다. 가로로 움직여야 드래그로 보므로 세로 목록 스크롤을 막지 않는다. 방향키와 TalkBack으로도 조절할 수 있고 `enabled = false`로 끌 수 있다 |
| `GlassRefreshBox(refreshing, onRefresh) { LazyColumn { … } }` | 맨 위에서 당기면 콘텐츠가 저항감 있게 내려오고 글래스 물방울이 차오른다. 기준 거리를 넘으면 햅틱과 함께 물방울이 길쭉해지고, 놓으면 새로 고친다. TalkBack에서는 "새로 고침" 동작으로 제공한다 |

## 화면 이동

| 컴포넌트 | 동작 |
| --- | --- |
| `GlassExpandHost(state) { key -> 상세 }` + `Modifier.expandSource(state, key)` + `state.expand(key)` | App Store의 투데이 탭처럼 카드가 제자리에서 화면 가득 커지며 상세 화면이 된다. 배경은 흐려지고, 상세 화면을 아래로 끌면 작아지면서 모서리가 둥글어지며, 놓으면 원래 카드 위치로 돌아간다. 상세 화면 위쪽을 카드와 똑같이 꾸미면 끊김 없이 이어진다 |
| `PushNavigation(stack, onBack) { screen -> … }` | iOS식 push 전환. 새 화면이 옆에서 들어오고 아래 화면은 조금 물러나며 어두워진다. 왼쪽 가장자리에서 끌거나 예측 뒤로 가기 제스처를 하면 손가락을 따라 되돌아간다. 아래 화면의 `rememberSaveable` 상태(스크롤 위치 등)는 유지되고, 스택에서 빠진 화면의 상태는 지운다. 같은 화면을 두 번 쌓을 수 없다. 호스트가 `onBack`에서 스택을 바꾸지 않으면 화면이 제자리로 돌아온다. RTL 언어에서는 방향이 반대가 된다. 모든 화면이 같은 ViewModelStoreOwner를 쓰므로 화면별 ViewModel은 `viewModel(key = …)`로 나눈다 |

```kotlin
var stack by rememberSaveable { mutableStateOf(listOf("home")) }
PushNavigation(stack, onBack = { stack = stack.dropLast(1) }) { screen ->
    when (screen) {
        "home" -> Home(open = { stack = stack + it })
        else -> Detail(screen)
    }
}
```

예측 뒤로 가기에서 손가락을 따라 움직이는 미리보기는 Android 14부터 동작한다. targetSdk 36 이상인 앱은 기본으로 켜져 있고, 그보다 낮으면 manifest에 `android:enableOnBackInvokedCallback="true"`를 넣어야 한다. 꺼져 있으면 뒤로 가기를 눌렀을 때 바로 닫히는 애니메이션만 나온다.

## 컨트롤과 입력

| 구성 요소 | 하는 일 |
| --- | --- |
| `GlassButton(text, onClick, kind, size, icon, loading)` | 채움(`Filled`), 옅은 색(`Tinted`), 유리(`Glass`), 글자만(`Plain`) 네 종류와 세 크기. 누르면 젤리처럼 눌리고, `loading`이면 폭을 유지한 채 링이 돈다 |
| `GlassStepper(value, onValueChange, range, step)` | −/+ 캡슐. 숫자가 굴러가며 바뀌고, 길게 누르면 점점 빨라진다. 끝에 닿으면 그쪽 버튼이 흐려진다 |
| `GlassChipGroup(chips, selected, onSelectedChange, selection)` | 하나 또는 여러 개를 고르는 칩. 고르면 체크 표시가 그려지며 폭이 부드럽게 늘어난다 |
| `GlassProgressBar(progress, wave)` / `GlassProgressRing(progress, wave) { … }` | 용수철로 따라가는 진행 표시. `wave`를 켜면 액체가 차오르듯 출렁이고, `progress = null`이면 진행 정도를 모르는 상태를 보여 준다 |
| `Modifier.glassBadge(count, dot)` | 오른쪽 위(RTL이면 왼쪽 위) 빨간 배지. 숫자가 바뀌면 톡 튄다. 99를 넘으면 `99+` |
| `GlassWheelPicker(items, selectedIndex, onSelect, cyclic)` | iOS 휠 피커. 원통에 붙은 것처럼 위아래 항목이 기울고, 항목을 지날 때마다 가볍게 진동하며 가운데에 맞춰 멈춘다 |
| `GlassDatePicker(date, onDateChange, range)` / `GlassTimePicker(hour, minute, onTimeChange)` | 휠로 만든 날짜·시간 선택기. 년·월·일 순서와 오전/오후 위치는 언어를 따른다 |
| `GlassSearchBar(query, onQueryChange, onSearch)` | 누르면 끝쪽에서 "취소" 버튼이 밀려 들어오고(`showCancel = false`로 끈다), 글자가 있으면 지우기 버튼이 나온다. 포커스를 받으면 강조색 테두리와 옅은 링이 나타난다 |
| `GlassTextField(value, onValueChange, label, error)` | 포커스되면 라벨이 위로 떠오르고 강조색 테두리가 부드럽게 나타난다. 글자가 있으면 지우기 버튼이 나오고, `placeholder`, `supportingText`, `leadingIcon`, `trailingIcon`을 더할 수 있다. 오류가 생기면 테두리가 빨개지며 한 번 흔들린다. 키보드가 올라오면 아래 문구까지 보이도록 스크롤한다 |

## 화면 구성과 알림

| 구성 요소 | 하는 일 |
| --- | --- |
| `GlassLargeTitleBar(rememberGlassLargeTitleState(), "제목")` | One UI·iOS식 큰 제목. 목록을 스크롤하면 큰 제목이 줄어들며 위 바의 작은 제목으로 바뀐다. 목록에 `Modifier.nestedScroll(state.connection)`을 붙인다. `bottom`에 탭이나 검색창을 주면 큰 제목과 함께 올라가 작은 바 밑에 붙는다 |
| `GlassCarousel(pageCount) { page -> }` + `GlassPageIndicator(pagerState)` | 옆 카드가 살짝 보이고 멀어질수록 작아지며 기우는 캐러셀. 점 인디케이터는 넘기는 동안 액체처럼 늘어난다 |
| `SwipeCardStack(items, onSwipe, overlay) { item -> }` | 틴더식 카드 더미. 끌면 기울며 따라오고 던지면 날아간다. `state.undo()`로 되돌린다 |
| `GlassSpeedDial(expanded, onExpandedChange, items)` | 떠 있는 둥근 버튼. 누르면 +가 ×로 돌고 항목이 차례로 튀어 오르며 뒤가 흐려진다 |
| `GlassIslandHost(state)` + `state.show(GlassIslandContent(…))` | 다이내믹 아일랜드식 알림. 작은 캡슐이 부풀어 알림이 되고, `compactText`가 있으면 좁은 캡슐(예: 타이머)로 시작해 누르면 펼쳐진다. 위로 쓸어 닫는다 |
| `GlassSnackbarHost(state)` + `state.show(message, actionLabel)` | 아래에서 떠오르는 스낵바. "되돌리기" 같은 동작 버튼과 남은 시간을 보여 주는 링이 있고, 옆으로 쓸어 닫는다. 결과를 `Action`/`Dismissed`로 돌려준다 |

## 목록, 차트, 달력

| 구성 요소 | 하는 일 |
| --- | --- |
| `SwipeActionsRow(endActions, startActions = …) { 행 }` + `SwipeAction(label, color, removesRow)` | iOS 메일처럼 행을 밀면 뒤에서 동작 버튼이 드러나고, 끝까지 밀면 첫 동작이 바로 실행된다. `removesRow`면 행이 줄어들며 사라진다. `rememberSwipeActionsGroup()`으로 한 번에 한 행만 열리게 한다. 둥근 카드 행이면 `shape`에 같은 모양을 넘겨 버튼도 그 모서리로 자른다 |
| `ReorderableItem(rememberReorderableListState(listState, onMove), key) { … }` | LazyColumn에서 길게 누르면 항목이 떠오르고 끌어서 순서를 바꾼다. 다른 항목은 비켜 주고, 가장자리에서는 자동으로 스크롤된다 |
| `GlassExpandable(expanded, onExpandedChange, header) { … }` / `GlassExpandableGroup` | 아코디언. 화살표가 돌고 내용 높이가 용수철로 펼쳐진다 |
| `GlassSlideToConfirm(text, onConfirm)` | 손잡이를 끝까지 밀어야 확정되는 버튼(결제, 잠금 해제) |
| `GlassLineChart(points 또는 series, labels)` | 선이 그려지며 나타나고, 손가락을 대고 옆으로 움직이면 값 말풍선이 따라온다. 데이터가 바뀌면 이전 모양에서 부드럽게 바뀐다 |
| `GlassBarChart(values, labels)` / `GlassDonutChart(slices)` / `Sparkline(points)` | 막대는 차례로 자라고 누르면 강조된다. 도넛 조각은 누르면 튀어나오며 가운데에 값을 보여 준다 |
| `ActivityRings(rings)` | Apple Watch 활동 링. 목표를 넘으면 겹쳐 돈다 |
| `GlassCalendar(rememberGlassCalendarState(), selection, onSelectionChange, events)` | 월 달력. 좌우로 넘기고, 하루(`Single`) 또는 기간(`Range`)을 고르며, 날짜 아래에 일정 점을 찍는다 |
| `FlipDigits(text)` / `FlipClock()` / `rememberCountdown(target)` | 기계식 플립 카드 숫자. 바뀐 자리만 위 반쪽이 접혀 내려간다 |
| `GlassRangeSlider(range, onRangeChange, valueRange)` | 손잡이 두 개짜리 범위 슬라이더. 잡으면 값 말풍선이 뜬다 |

## 미디어, 내비게이션, 안내

| 구성 요소 | 하는 일 |
| --- | --- |
| `GlassMiniPlayer(rememberGlassMiniPlayerState(), artwork, collapsed, expanded)` | 아래 떠 있는 재생 막대를 누르거나 위로 끌면 전체 화면 플레이어로 커지고, 앨범 아트가 이어지며 커진다. 아래로 끌거나 뒤로 가기로 접는다 |
| `GlassMiniPlayerBar(state, artwork) { 막대 내용 }` + `GlassMiniPlayerFullScreen(state, artwork) { 플레이어 }` | 막대와 전체 화면을 나눈 `GlassMiniPlayer`. 막대는 글래스 캡슐 없이 내용만 그리므로 `GlassFloatingTabBar`의 `accessory`처럼 다른 컴포넌트 안에 넣는다. 펼치면 막대가 놓인 자리에서 앨범 아트가 이어지며 커지고, 접으면 그 자리로 돌아간다 |
| `PlayPauseIcon` / `MenuCloseIcon` / `PlusCloseIcon` | 모양이 매끄럽게 바뀌는 아이콘 |
| `GlassRating(rating, onRatingChange, allowHalf)` / `LikeButton(liked, onLikedChange, count)` | 별점과 하트 좋아요. 좋아요를 누르면 하트가 튀며 입자가 터지고 숫자가 굴러간다 |
| `AvatarStack(avatars, max)` / `Modifier.skeleton(visible)` / `GlassSkeletonText(lines)` | 겹친 아바타와 "+N", 불러오는 동안 빛이 흐르는 자리 표시 |
| `GlassDetentSheet(rememberGlassDetentSheetState(detents), header) { … }` | Apple 지도처럼 여러 높이(`Detent.Peek`·`Fraction`·`Full`)에 멈추는 바텀 시트. 안의 목록 스크롤과 자연스럽게 이어지고, 손을 떼면 던진 속도를 보고 가까운 높이로 정착한다. 폴더블을 접고 펴도 같은 높이 단계를 유지한다 |
| `GlassTopTabs(tabs, pagerState)` | 페이저를 넘기는 동안 인디케이터가 액체처럼 따라가는 상단 탭 |
| `Modifier.glassTooltip(text, visible, onDismiss)` / `Modifier.tooltipOnLongPress(text)` | 화살표가 달린 유리 말풍선 |
| `CoachMarkHost(state, steps)` + `Modifier.coachMarkTarget(state, id)` | 처음 쓰는 사람을 위한 기능 안내. 화면이 어두워지고 대상 둘레만 밝게 뚫린다 |
| `GlassDrawer(drawerContent, state) { 화면 }` | 가장자리에서 끌어 여는 서랍. 열리면 뒤 화면이 밀리며 작아지고 둥글어진다 |

## 입력 2차

| 구성 요소 | 하는 일 |
| --- | --- |
| `GlassCodeInput(value, onValueChange, length, mask, error, onComplete)` | 한 칸에 한 자리씩 받는 인증번호, PIN 입력란. 붙여 넣기와 SMS 자동 채우기를 받고, `error`가 켜지면 칸이 빨개지며 흔들린다. `imeAction`과 `keyboardActions`로 키보드 동작 키를 바꾼다 |
| `GlassTagInput(tags, onTagsChange, placeholder, suggestions)` | 쉼표나 완료 키로 칩을 만드는 태그 입력란. 같은 태그는 다시 넣지 않고 그 칩을 흔든다. 빈 입력란에서 지우기를 두 번 누르면 마지막 칩이 지워진다. 입력 중인 글자가 있으면 동작 키가 칩을 만들고, 빈 입력란에서 누르면 `keyboardActions`로 넘기므로 `imeAction = ImeAction.Next`로 두면 다음 입력란으로 갈 수 있다. 추천 목록은 아래 내용을 밀어내지 않고 겹쳐 뜨며, 아래 공간이 모자라면(키보드가 올라왔을 때 등) 입력란 위로 뜬다 |
| `GlassColorPicker(color, onColorChange, presets, showAlpha, showHexInput)` | 색조 고리와 채도·명도 사각형, 투명도 슬라이더, HEX 입력란, 최근 색과 프리셋 칩이 있는 색 선택기. 키보드로는 Tab으로 고리, 사각형, 투명도 슬라이더를 오가며 방향키로 조절한다 |
| `GlassSignaturePad(modifier, rememberSignaturePadState())` | 속도에 따라 굵기가 바뀌는 서명란. `state.toImageBitmap()`이나 `state.toSvg()`로 내보내고, 되돌리기와 지우기 버튼이 있다 |
| `GlassPasswordField(value, onValueChange, label, showStrength, showChecklist)` / `passwordStrength(password)` | 눈 아이콘으로 보이거나 숨기는 비밀번호 입력란. 강도 막대가 약함, 보통, 강함 색으로 차오르고 조건 체크리스트를 보여 줄 수 있다. `imeAction`으로 동작 키를 Next 등으로 바꾼다 |

`GlassPasswordField`와 `GlassTagInput`의 `modifier`는 입력란과 칩, 강도 막대를 감싼 바깥 묶음에 붙는다. `testTag`나 `focusRequester`처럼 입력란 자체에 걸어야 하는 것은 `fieldModifier`로 준다. `GlassCodeInput`은 `modifier`가 곧 입력란이다.

XML 화면의 `NestedScrollView`, `ViewPager2`, `RecyclerView` 안에 `ComposeView`로 넣어도 색상 고리와 사각형, 서명란은 끄는 동안 바깥 View가 터치를 가로채지 않는다. 투명도 슬라이더, `BeforeAfterSlider`, `FlipCard`는 자기 방향(가로 슬라이더는 가로)으로 끌 때만 붙잡고, 반대 방향으로 끌기 시작하면 바깥 스크롤에 넘긴다.

## 미디어와 피드백

| 구성 요소 | 하는 일 |
| --- | --- |
| `GlassImageViewer(rememberGlassImageViewerState(), images)` + `Modifier.imageViewerSource(state, index)` | 전체 화면 사진 뷰어. 썸네일에서 커지며 열리고, 핀치로 1~5배 확대, 두 번 탭 확대, 위아래로 끌어 닫기를 지원한다. 확대한 사진이 가장자리에 닿은 뒤 바깥쪽으로 더 끌면 확대를 풀지 않고 옆 사진으로 넘어간다. 사진 대신 원하는 내용을 넣는 `pageCount` 버전도 있다. `images`의 Painter는 `intrinsicSize` 비율대로 맞춰 그리고, `BrushPainter`처럼 크기가 없으면 화면 전체를 채운다. 열린 채 목록이 비면 바로 닫히고 상태 없는 버전은 `onDismiss`를 부른다 |
| `BeforeAfterSlider(before, after)` / `BeforeAfterSlider(position, onPositionChange, before, after)` | 두 이미지를 겹쳐 놓고 가운데 손잡이를 끌어 비교한다. 가로 드래그만 받아 세로 스크롤을 막지 않는다. 손잡이 위치를 호스트가 저장하거나 바꾸려면 `position`을 받는 버전을 쓴다 |
| `GlassResultMark(state)` / `GlassResultDialog(visible, state, title, onDismissRequest)` | `GlassResult.Loading`이면 링이 돌고, 결과로 바뀌면 원이 닫힌 뒤 체크, ×, 느낌표가 획 순서대로 그려진다. 성공이면 파티클이 튄다 |
| `GlassOnboarding(pages, onFinish)` + `OnboardingPage(title, body, background, art)` | 첫 실행 안내. 그림이 시차를 두고 따라오고 배경색이 섞이며, 마지막 페이지에서 버튼이 '시작하기'로 늘어난다 |
| `GlassEmptyState(title, message, art, action)` / `GlassEmptyIcon(GlassEmptyIconKind)` | 빈 목록, 오프라인, 오류 화면. 글래스 원판 위 그림이 천천히 떠 있다. 기본 그림은 NoResults, Offline, EmptyInbox, Error |

## 적응형 레이아웃

폴더블과 태블릿에서 화면 폭에 따라 배치를 바꾼다. 기준 폭은 `GlassAdaptiveDefaults.TwoPaneMinWidth`(600dp)이고, 컴포저블이 받은 폭으로 판단하므로 멀티 윈도우에서도 맞게 동작한다.

| 구성 요소 | 하는 일 |
| --- | --- |
| `GlassListDetail(selected, onBack, list = { … }) { item -> 상세 }` | 좁으면 상세가 옆에서 밀려 들어오고, 넓으면 목록과 상세를 두 칸으로 놓는다. 반쯤 접은 폴더블에서는 두 칸 경계를 힌지에 맞춘다. 접고 펼쳐도 선택과 스크롤 위치가 유지된다. 상세 상태는 지금 고른 항목 것만 기억하므로 예전 항목을 다시 고르면 처음 상태로 열린다. 두 칸 전환에 쓰려고 붙들어 두는 상세 내용도 최근 8개 항목까지라 많이 열어도 쌓이지 않는다. `detailBackground`에 목록과 같은 배경을 한 번 주면 상세마다 배경을 칠하지 않아도 된다 |
| `GlassNavigationRail(items, selectedIndex, onSelect, header)` | 화면 시작 쪽에 세우는 내비게이션 레일. 인디케이터가 세로로 늘어났다 줄며 이동한다 |
| `GlassAdaptiveNavigation(tabs, selectedIndex, onSelect) { padding -> }` | 좁으면 아래 탭 바, 넓으면 레일로 바꾼다. 바뀌어도 내용을 다시 만들지 않는다 |
| `GlassFloatingTabBar(tabs, selectedIndex, onSelect, state, accessory)` | 화면 아래 떠 있는 탭 바. 목록에 `state.nestedScrollConnection`을 붙이면 아래로 스크롤할 때 선택한 탭 아이콘만 남은 캡슐로 줄어든다. `accessory`는 펼친 동안 탭 바 위에 붙어 있다가 접히면 캡슐 옆으로 내려온다. 여기에 `GlassMiniPlayerBar`를 넣으면 그 자리에서 전체 화면 플레이어가 펼쳐진다 |

샘플의 메일 앱(앱 화면 예 → 메일)이 `GlassListDetail`을, 음악 앱이 `GlassFloatingTabBar`와 그 `accessory`에 넣은 `GlassMiniPlayerBar`를 쓴다.

## 고급 효과

| 효과 | 사용법 |
| --- | --- |
| 글로우 테두리(Siri, Apple Intelligence 스타일) | `Modifier.glowBorder(shape, colors, width, glow)`. 화면 전체 `Modifier`에 붙이면 "듣는 중"을 나타내는 화면 가장자리 효과가 된다 |
| 젤리 누름 | `Modifier.jellyPress()`. 납작해졌다가 출렁이며 돌아온다. 클릭은 그대로 동작한다 |
| 자석 | `Modifier.magnetic(strength)`. 끌면 손가락을 따라오다가 놓으면 튕기며 제자리로 돌아온다 |
| 기울이면 3D | `Modifier.motionTilt(maxDegrees, shine)`. 중력 센서로 기기 기울기를 읽어 요소가 따라 기울고 빛이 흐른다 |
| 숫자 굴리기 | `RollingText("1,284,000원")`. 바뀐 자릿수만 위아래로 굴러간다 |
| 타자기 | `TypewriterText("…")`. 깜박이는 커서와 함께 한 글자씩 나타난다 |
| 색종이 | `ConfettiHost(state)` + `state.burst(origin)` |
| 움직이는 배경 | `MeshGradientBackground(palette = MeshGradients.Sunset)`. 색 덩어리가 천천히 떠다니며 섞인다(API 33+는 AGSL 셰이더). 프리셋 Sunset, Ocean, Aurora, Candy, Midnight |
| 글자 효과 | `ShimmerText`(빛이 흐르는 글자), `GradientText`(색이 흐르는 글자), `ScrambleText`(뒤섞였다 맞춰지는 글자) |
| 흔들기 | `Modifier.shake(trigger)`. `trigger`가 바뀔 때마다 좌우로 흔든다(오류 표시) |
| 반짝이 | `Modifier.sparkle()`. 요소 주위에 작은 별이 반짝였다 사라진다 |
| 스크롤 시차 | `Modifier.parallaxScroll(scrollState, rate, pullState)`. 헤더가 느리게 움직이고, 맨 위에서 당기면 커진다 |
| 스포트라이트 | `Modifier.spotlight(shape = …)`. 손가락을 대고 움직이면 그 자리에 빛이 따라다니고 테두리가 빛난다 |

모든 효과 수정자는 Material `Button`, `Card`, `Image`, `LazyColumn` 항목 같은 호스트 Composable에 그대로 붙는다. 터치를 소비하지 않으므로 클릭, 리플,
TalkBack 역할은 그대로다. 수정자 순서는 다음을 권한다.

```kotlin
Modifier
    .jellyPress()                  // 1. 움직임(jellyPress, magnetic, motionTilt, shake, kineticaEffects): 배경과 리플까지 함께 움직인다
    .glowBorder(shape)             // 2. 겉에 그리는 효과(glowBorder, sparkle, glassBadge): 잘리지 않게 clip보다 앞에 둔다
    .glass(shape = shape)          // 3. 배경(glass, background)
    .clip(shape)                   // 4. 자르기
    .clickable { … }               // 5. 입력(clickable, glassContextMenu, tooltipOnLongPress)
    .padding(16.dp)
```

순서를 바꿔도 클릭은 동작하지만, 움직임 효과를 `background` 뒤에 두면 배경은 그대로 있고 안쪽 내용만 움직인다.

시스템의 '애니메이션 삭제' 설정이 켜져 있으면 반복, 기울기, 색종이 효과는 모두 멈춘다. 반복 효과(`glowBorder`, `sparkle`, `skeleton`, 글자 효과, 움직이는 배경)는
요소가 창 밖으로 스크롤되어 나가 있는 동안에도 멈추고, `motionTilt`는 앱이 백그라운드로 가면 센서를 놓는다. 리플, 터지기, 두근거림 같은 누름·등장·반복 효과는 [docs/33](33-effects.md)에서 다룬다.

## 고급 효과 2차

| 효과 | 사용법 |
| --- | --- |
| 물결 왜곡 | `Modifier.rippleDistortion(strength, wavelength)`. 누른 자리에서 물결이 퍼지며 내용이 굴절된다. Android 13 이상은 AGSL 셰이더로, 그보다 낮으면 밝은 고리로 대신한다. 물결은 최대 4개까지 겹친다 |
| 파티클 | `ParticleField(ParticlePreset.Snow)`. 프리셋은 눈, 불꽃놀이, 거품, 불꽃. `rememberParticleFieldState()`의 `launch(at)`로 원하는 자리에서 터뜨린다 |
| 뒤집는 카드 | `FlipCard(flipped, onFlippedChange, front = { … }, back = { … })`. 끌면 손을 따라 돌고, 놓으면 속도와 각도를 보고 가까운 면에 멈춘다. `axis`로 가로축, 세로축을 고른다. 기본으로는 보이지 않는 면을 컴포지션에서 빼므로 그 면의 입력 중인 글자나 스크롤 위치가 뒤집을 때 처음으로 돌아간다. `keepBothFaces = true`면 두 면을 모두 남겨 상태를 유지하고, 보이지 않는 면은 그리지도 터치나 TalkBack에 드러내지도 않는다 |
| 액체 모양 | `Modifier.liquidMorph(color)` / `GlassBlobButton(text, onClick)`. 누른 자리가 오목하게 들어갔다가 출렁이며 돌아온다 |
| 방울 묶음 | `GlassBlobGroup { Modifier.blob() }`. 가까운 방울끼리 메타볼처럼 녹아 붙는다. Android 12 이상에서 이어지고, 그보다 낮으면 따로 그린다 |
| 홀로그램 | `Modifier.holographic(shape, useSensor)`. 누른 위치나 기기 기울기에 따라 무지개빛 광택이 움직인다. 누르는 동안 기울기만 주려면 `Modifier.tiltOnPress()` |

## 호스트 앱에서 쓸 때

**어디서 부를 수 있나.** `GlassScaffold` 안에서 부르면 다이얼로그·시트·메뉴·토스트가 scaffold 위층에 그려지고 뒤 화면 전체를 블러로 비춘다.
호스트가 자기 Scaffold(Material 등)나 그냥 `Column` 안에서 불러도 된다. 이때 다이얼로그·시트·메뉴·펼침 메뉴·기능 안내·펼친 카드는 전체 화면 Dialog 창으로,
토스트·스낵바·아일랜드는 뒤 화면 터치를 막지 않는 Popup 창으로 뜨므로 부른 자리의 배치를 깨뜨리지 않는다. `GlassMiniPlayer`, `GlassMiniPlayerFullScreen`, `GlassDetentSheet`는 부른 자리에 그리므로 화면을 채우는 `Box` 안에 둔다.

**높이와 스크롤.**
- 시트는 내용 높이에 맞추되 최대 높이는 위쪽 안전 영역에서 `GlassSheetDefaults.TopGap`(48dp) 아래까지다. 내용이 더 길면 시트 안에 `LazyColumn`이나 `verticalScroll`을 넣는다. 목록이 맨 위일 때 아래로 끌면 시트가 내려간다.
- 다이얼로그는 안전 영역 안쪽 28dp 여백까지, 너비 280~420dp다. `fillMaxSize`·`weight`는 그 최대 크기를 채운다.
- 시트·다이얼로그·여러 높이 시트·서랍·펼친 카드·미니 플레이어 안에 `LazyColumn`을 넣어도 무한 높이 오류가 나지 않는다.

**인셋(상태 표시줄, 내비게이션 바, 키보드, 컷아웃).**
- 시트·다이얼로그·여러 높이 시트는 `contentWindowInsets`로 스스로 피한다. 기본값은 시트가 `GlassSheetDefaults.windowInsets`(아래쪽과 양옆의 safeDrawing), 다이얼로그가 `WindowInsets.safeDrawing`이다.
  안에 입력란이 있으면 키보드가 올라올 때 함께 올라온다. 호스트가 직접 처리하려면 `contentWindowInsets = { WindowInsets(0) }`을 준다.
- 토스트·아일랜드는 위쪽, 스낵바는 아래쪽(키보드 포함)과 양옆의 안전 영역을 피한다. 여러 높이 시트의 `Detent.Peek`·`Fraction` 높이는 내비게이션 바와 키보드 위에서 잰다.
- edge-to-edge를 쓰지 않는 화면에서는 인셋이 0이 되므로 여백이 두 번 들어가지 않는다.

**포커스와 TalkBack.**
- 다이얼로그·시트·메뉴·기능 안내·서랍이 열리면 뒤 화면은 TalkBack에서 빠지고, 키보드 Tab 이동도 모달 밖으로 나가지 않는다. 닫으면 연 버튼으로 포커스가 돌아온다.
- 키보드로 쓰는 중일 때만 포커스를 모달 안으로 옮긴다. 터치로 열었을 때는 시트 안 입력란이 저절로 키보드를 띄우지 않는다.
- 토스트·스낵바·툴팁은 뒤 화면을 가리지 않는다.

**별도 창으로 뜬 팝업의 뒤 화면.** `GlassScaffold` 밖에서 연 다이얼로그·시트는 호스트 화면을 기록할 수 없으므로, Android 12 이상에서 시스템 창 블러(`blurBehindRadius`)로 뒤를 흐린다.
기기 설정이나 절전 모드로 창 블러가 꺼져 있으면(일부 기기는 기본으로 꺼져 있다) 블러 없이 어둡게만 하고, 판은 뒤 아이콘이 비치지 않는 불투명한 색으로 칠한다.
뒤 화면까지 같은 유리 느낌을 내려면 화면을 `GlassScaffold`로 감싼다.

**팝업 안의 글래스.** 다이얼로그·시트·여러 높이 시트·서랍·미니 플레이어 안에 놓은 글래스(입력란, 칩, 버튼 등)는 판 뒤 화면을 다시 비추지 않고, 판 위에 얹힌 한 겹으로 옅게 그린다.
다른 배경을 비추게 하려면 `backdrop`을 직접 넘긴다.

**닫을 때 키보드.** 팝업 안 입력란이 포커스를 가진 채 닫히면 포커스를 거두고 키보드를 내린다.

**호스트 View를 넣을 때(AndroidView).** EditText, RecyclerView, ScrollView, WebView, TextureView는 글래스 안에서 터치·스크롤·입력이 그대로 동작하고, 배경에 두면 블러로 비친다.
SurfaceView(지도, 영상, 카메라)는 창과 다른 표면에 그려져 기록되지 않는다. 그 위의 글래스는 블러 없이 색만 덮이므로, 블러가 필요하면 TextureView를 쓰거나 `backdrop = null`로 fallback 색을 쓴다.

**XML(View) 화면에서.** Compose 코드 없이 `com.naury.kinetica.ui.view`의 진입점을 쓴다.

```kotlin
val sheet = GlassSheets.show(this, R.layout.sheet_share, GlassPopupOptions(title = "공유"))
sheet.contentView?.findViewById<Button>(R.id.close)?.setOnClickListener { sheet.dismiss() }
GlassDialogs.show(this, confirmView)
GlassToasts.show(this, "저장했습니다")
```
```java
GlassPopupHandle sheet = GlassSheets.show(this, R.layout.sheet_share);
```
```xml
<com.naury.kinetica.ui.view.GlassSurfaceView
    android:layout_width="wrap_content" android:layout_height="wrap_content"
    app:kinetica_glassStyle="acrylic" app:kinetica_cornerRadius="20dp"> … </com.naury.kinetica.ui.view.GlassSurfaceView>
```

- 액티비티는 `ComponentActivity`(AppCompatActivity, FragmentActivity 포함)여야 한다. Fragment에서는 `requireActivity()`와 `GlassPopupOptions(lifecycleOwner = viewLifecycleOwner)`를 넘긴다.
- 시트·다이얼로그는 액티비티 화면 위에 겹친 레이어에 그려지고 그 아래 View를 블러로 비춘다(API 31 이상). 키보드가 올라오면 함께 올라오고, 뒤로 가기로 닫힌다(`cancelable = false`면 닫히지 않음).
- 화면 회전 등으로 액티비티가 다시 만들어지면 열린 창은 애니메이션 없이 정리된다. 회전 뒤에도 열어 두려면 `onSaveInstanceState`에 열림 여부를 저장했다가 다시 띄운다.
- 메인 스레드에서만 부른다.

**뒤로 가기.** 가장 안쪽이 먼저 받는다. 시트 안에서 호스트가 `BackHandler`를 두면 그것이 먼저, 그다음 시트가 닫히고, 그다음 화면 순서다.

## 성능

실기기(Galaxy Z Fold7 메인 화면, 1968×2184, 120Hz, release 빌드)에서 쇼케이스 화면을 위아래로 스크롤해 잰 값이다.

| 화면 | 프레임 p50 | p90 | 끊김(기한 놓침) |
| --- | --- | --- | --- |
| 첫 화면(글래스 카드 9개, 움직이는 배경) | 13 ms | 14 ms | 0.34% |
| 글래스 UI(바·탭 바·카드·사진 위 글래스) | 13 ms | 15 ms | 0.22% |
| 모션 효과 | 12 ms | 14 ms | 0.22% |
| 컴포넌트(글래스 컨트롤 30여 개, 휠 피커, 캐러셀) | 13 ms | 17 ms | 0.39% |
| 컴포넌트 효과 탭 스크롤 | 12 ms | 14 ms | 0.09% |
| 물결 왜곡을 누르고 끄는 중 | 12 ms | 12 ms | 0% |
| 파티클(눈, 불꽃놀이, 거품, 불꽃) | 13 ms | 13~14 ms | 0% |
| 방울 묶음 펼치기와 접기 | 13 ms | 13 ms | 0% |
| 홀로그램 카드를 끄는 중 | 17 ms | 18 ms | 0~0.45% |
| 앱 화면 예(음악·건강) | 7~13 ms | 9~14 ms | 0~0.11% |
| 메일 두 칸(목록, 본문 스크롤) | 17 ms | 18~19 ms | 0~0.08% |

p50이 8.3 ms(120Hz 한 프레임)보다 큰 것은 입력부터 화면 표시까지의 시간이라서다. 위 측정에서 모두 초당 118프레임 안팎으로 그렸다.
메일 두 칸과 홀로그램 카드는 GPU 시간 p50이 11~12 ms로 다른 화면보다 길지만, 앞 프레임과 겹쳐 그려지므로 끊김으로 이어지지는 않았다.

글래스는 배경을 블러 반경에 맞춰 낮은 해상도(블러 12px 미만은 원래 해상도, 48px 미만은 1/2, 그 이상은 1/3)로 기록해 흐린 뒤 늘려 그린다.
흐린 결과라 화질 차이는 거의 없고, 목록에 글래스가 많을 때 새 항목이 들어오며 생기던 GPU 메모리 할당이 줄어 컴포넌트 화면 끊김이 5.15%에서 0.1% 안팎으로 줄었다.
페이저, 지연 목록, 이동 애니메이션은 글래스를 다시 그리지 않고 부모 레이어만 옮긴다. 그래서 글래스는 위치가 바뀌면 배경을 옮겨 놓는 레이어의 이동량만
고쳐서, 다시 그리지 않고도 놓인 자리의 배경을 비춘다.
그래도 글래스 하나마다 블러 층을 하나 더 그리므로, 작은 버튼마다 글래스를 쓰기보다 큰 카드나 바에 쓰는 편이 낫다.
효과 값은 그리기 단계에서 읽으므로 애니메이션 중에 재구성하지 않는다. `LazyColumn` 항목 300개에 효과, 글래스, 배지를 붙여도 항목이 다시 구성되지 않는 것을
`HostUsageTest`가 확인한다. 소프트웨어 렌더러(SwiftShader 에뮬레이터)에서는 매우 느리다.

## 확인

`sample-showcase`의 **글래스 UI** 화면에서 스타일을 바꿔 보고 다이얼로그, 바텀 시트, 토스트, 탭 바, 컨트롤, 슬라이더, 당겨서 새로 고침을 확인할 수 있다.
**화면 이동**에서는 카드가 커지는 상세 화면과 push 전환, 가장자리에서 끌어 뒤로 가기를, **모션 효과**에서는 고급 효과를 확인한다.
