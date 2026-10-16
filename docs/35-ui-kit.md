# 35 — UI 키트와 고급 효과 (`kinetica-ui`)

다이얼로그, 바텀 시트, 토스트, 탭 바, 입력란 같은 컴포넌트와 고급 애니메이션을 골라 붙여 쓰는 모듈이다. 앱에서 직접 만들기 까다로운 것들을 모았다.
모든 컴포넌트는 표면 스타일(`SurfaceStyle`)을 따르므로, 같은 API로 배경이 비치는 글래스 UI도, 블러 없는 깔끔한 단색 UI도, 강조색을 섞은 톤 UI도 만들 수 있다.
Compose 전용이며 다른 Kinetica 모듈 없이 쓸 수 있다.

```kotlin
implementation("com.naurylab:kinetica-ui:1.0.0-alpha01")
```

이름 규칙은 다음과 같다. 테마와 골격은 `KineticaTheme`, `KineticaScaffold`, `KineticaColors`, 컴포넌트는 `K` 접두사(`KButton`, `KSheet`, `KDialog`, `KToastHost`, `KTabBar`, `KTextField`, `KSurface` 등),
수정자는 `kClickable`, `kBadge`처럼 `k`로 시작한다. 블러 효과 자체를 가리키는 `Modifier.glass`, `GlassStyle`, `GlassStyles`, `GlassBackdrop`, `glassBackdrop`만 글래스라는 이름을 쓴다.
1.0.0-alpha01 이전 이름(`Glass*`)에서 옮기는 방법은 [CHANGELOG](../CHANGELOG.md)에 있다.

## 화면 구조

```kotlin
KineticaScaffold(
    // surface를 빼면 KineticaTheme이 정한 표면을 쓴다(테마가 없으면 SurfaceStyles.Liquid).
    background = { Image(painterResource(R.drawable.photo), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) },
    topBar = { KSurface(shape = CircleShape) { Text("제목") } },
    bottomBar = { KTabBar(tabs, selected, onSelect = { selected = it }) },
) { padding ->
    LazyColumn(contentPadding = padding) { items(...) { KSurface { ... } } }
}
```

- 글래스 표면이면 `KineticaScaffold`가 화면을 배경, 배경과 콘텐츠, 화면 전체의 세 층으로 나눠 기록한다(배경 ⊂ 배경+콘텐츠 ⊂ 화면 전체). 콘텐츠 안의 글래스는 배경을,
  상단 바와 하단 바는 배경과 콘텐츠를, 다이얼로그와 바텀 시트, 토스트는 화면 전체를 블러 처리해 비춘다. 그래서 글래스를 어디에 두어도 자기 자신을 비추지 않는다.
- 단색이나 톤 표면이면 배경을 기록하지 않는다. 기록용 레이어도 만들지 않으므로 블러 비용이 없고, 표면의 `background` 색을 바탕에 칠한다.
- 콘텐츠는 바 아래까지 그려지므로 스크롤하면 바 뒤로 지나가는 모습이 보인다. 바 높이는 `padding`으로 받는다. 상태 표시줄 아래로 들어간 콘텐츠는
  `edgeFade`로 흐려지며 사라진다. 단색과 톤 표면에서는 바탕색으로 덮어 사라지게 한다.
- 사진 위에 글래스를 올릴 때처럼 특정 이미지를 비추려면 그 이미지에 `Modifier.glassBackdrop(rememberGlassBackdrop())`를 붙이고,
  만든 backdrop을 글래스에 넘긴다(`KSurface(backdrop = photo)`).

## 표면 스타일 `SurfaceStyle`

컴포넌트가 배경을 어떻게 칠할지는 `SurfaceStyle` 하나로 정한다. 종류는 셋이다.

| 종류 | 그리는 방법 | 프리셋 |
| --- | --- | --- |
| `SurfaceStyle.Glass(style)` | 배경을 블러 처리해 비춘다. 블러 값은 `GlassStyle`이 정한다 | `SurfaceStyles.Liquid`, `LiquidDark`, `Acrylic`, `AcrylicDark`, `OneUiGlass`, `OneUiGlassDark`, `Vision`, `Frosted`, `Mica`, `MicaDark`, `MaterialGlass`, `MaterialGlassDark`, `HarmonyOs`, `Obsidian`, `Pastel` |
| `SurfaceStyle.Solid(color, contentColor, background, border, borderWidth, elevation)` | 불투명한 단색 표면. 테두리와 그림자를 줄 수 있다 | `SurfaceStyles.CleanLight`, `CleanDark`, `OneUi`, `OneUiDark`, `Material`, `MaterialDark` |
| `SurfaceStyle.Tonal(accent, dark)` | 강조색을 옅게 섞은 표면. 같은 톤 표면끼리 겹쳐도 경계가 보이도록 옅은 강조색 테두리를 두른다 | `SurfaceStyles.Tonal`, `TonalDark`, `SurfaceStyles.tonal(색, dark)` |

```kotlin
// 앱 전체를 단색 표면으로
KineticaTheme(surface = if (dark) SurfaceStyles.CleanDark else SurfaceStyles.CleanLight) { App() }

// 컴포넌트 하나만 다른 표면으로
KSheet(visible, onDismiss, surface = SurfaceStyles.tonal(brand))

// 직접 만든 요소도 같은 표면으로
Box(Modifier.kSurface(shape = RoundedCornerShape(20.dp)).padding(16.dp)) { … }
```

- 모든 컴포넌트는 `surface: SurfaceStyle = KineticaTheme.surface` 파라미터를 받고, 표면은 `Modifier.kSurface(surface, shape)` 하나로 그린다.
- 글자와 아이콘 색(`contentColor`, 컴포넌트 안에서는 `LocalKineticaContentColor`), 눌림 표시 색, 시스템 바 아이콘 밝기(`isDark`)는 표면 스타일에서 정해진다.
- `KineticaScaffold`는 단색과 톤 표면일 때 시스템 바 아이콘을 표면 밝기에 맞춘다(`systemBarIcons`). 글래스는 호스트 배경이 비치므로 아이콘을 호스트에 맡긴다.
- 다이얼로그와 바텀 시트는 `surface.forPopup()`을 쓴다. 글래스는 블러를 더 강하게, 색을 더 진하게 덮고, 단색과 톤은 그림자를 키운다. 어두운 단색은 판을 조금 밝혀 바탕과 구분한다.
- 다이얼로그나 시트 안에 놓인 표면(입력란, 칩)은 판과 같은 색으로 묻히지 않도록 판 위에 얹힌 옅은 한 겹으로 그린다.
- XML 화면의 `KSurfaceView`는 `app:kinetica_surfaceStyle`에 글래스 값(`liquid`, `acrylic` 등)과 함께 `cleanLight`, `cleanDark`, `solidOneUi`, `solidOneUiDark`,
  `solidMaterial`, `solidMaterialDark`, `tonal`, `tonalDark`를 받는다. 코드에서는 `surfaceStyle = SurfaceStyles.CleanLight`처럼 넘긴다.

## 모양 변형 `KVariant`와 색 역할 `KTone`

버튼, 아이콘 버튼, 칩, 누를 수 있는 `KSurface`는 모양(`variant`)과 색 역할(`tone`)을 따로 고른다. 둘 다 표면 스타일과 독립이라 글래스 표면에서도 `Outlined` 버튼을 쓸 수 있다.

| `KVariant` | 모양 |
| --- | --- |
| `Filled` | 색 역할의 색으로 꽉 채운다 |
| `Tonal` | 색 역할의 색을 옅게 깔고 글자를 그 색으로 쓴다 |
| `Outlined` | 배경 없이 테두리만 두른다 |
| `Text` | 배경과 테두리 없이 글자만 쓴다. 누르는 동안에만 옅은 배경이 보인다 |
| `Elevated` | 표면 스타일로 그리고 그림자를 키운다 |
| `Glass` | 표면 스타일 그대로 그린다. 글래스 표면이면 블러가 비치고 단색 표면이면 단색이다 |

`KTone`은 `Accent`(강조색), `Neutral`(표면 글자 색), `Destructive`(삭제처럼 되돌리기 어려운 동작), `Positive`(저장, 완료) 넷이고, 색은 `KineticaColors`에서 가져온다.
`Glass`와 `Elevated`는 표면 글자 색을 쓰고, `Destructive`와 `Positive`일 때만 그 색으로 글자를 칠한다.

```kotlin
KButton("결제하기", onClick = ::pay)                                              // Filled + Accent
KButton("삭제", onClick = ::delete, variant = KVariant.Outlined, tone = KTone.Destructive)
KIconButton(Icons.Default.Share, "공유", onClick = ::share)                       // 기본값 Glass
KChipGroup(filters, selected, { selected = it }, variant = KVariant.Tonal)
KSurface(onClick = { open(item) }, variant = KVariant.Elevated) { … }
scope.launch { toast.show("저장했어요", tone = KTone.Positive) }                  // 글자 앞에 초록 점
```

이전의 `GlassButtonKind`는 `KVariant`로 바뀌었다(`Tinted` → `Tonal`, `Plain` → `Text`). 버튼의 `accent` 파라미터 대신 `tone`과 `KineticaColors`를 쓴다.

## 테마 맞추기 `KineticaTheme`

`KineticaTheme`으로 표면 스타일, 강조색, 글꼴, 눌림 표시를 한 번에 정한다. 화면 루트에 한 번 감싸면 그 안의 `KineticaScaffold`와 모든 컴포넌트가 기본값으로 쓴다.
컴포넌트에 직접 넘긴 값이 있으면 그 값이 우선한다.

```kotlin
MaterialTheme(colorScheme = scheme, typography = typography) {
    KineticaTheme(
        darkTheme = isSystemInDarkTheme(),            // 기본값. 앱이 다크 모드를 직접 정하면 그 값을 넘긴다
        surface = if (dark) SurfaceStyles.CleanDark else SurfaceStyles.CleanLight,
        colors = KineticaColors(
            accent = MaterialTheme.colorScheme.primary,
            onAccent = MaterialTheme.colorScheme.onPrimary,
            destructive = MaterialTheme.colorScheme.error,
        ),
        textStyle = MaterialTheme.typography.bodyLarge, // 글꼴만 가져오고 크기와 줄 높이는 컴포넌트가 정한다
    ) { App() }
}
```

- `surface`를 주지 않으면 `darkTheme`에 따라 `SurfaceStyles.Liquid`나 `LiquidDark`를 고른다.
- `KineticaColors`의 `accent`는 버튼, 칩, 슬라이더, 진행 표시, 입력란 포커스, 달력 선택에, `positive`는 스위치와 밀어서 확인하기에,
  `destructive`는 배지, 삭제 메뉴, 입력 오류에 쓴다. `onPositive`와 `onDestructive`는 그 색으로 채운 버튼 위 글자 색이다.
- kinetica-ui는 Material 3에 의존하지 않으므로 Material 테마 값은 위처럼 호스트가 넘긴다.
- 직접 만든 컴포넌트를 같은 색으로 맞추려면 `KineticaTheme.surface`, `KineticaTheme.colors.accent`, `KineticaTheme.textStyle`을 읽는다.
  `Modifier.glass`의 기본 스타일은 `KineticaTheme.glassStyle`이다. 글래스가 아닌 표면이면 표면 밝기에 맞춘 Liquid 스타일이다.

## 눌림 표시 `KIndication`

kinetica-ui의 누를 수 있는 요소는 사각 리플 대신 `KIndication`을 쓴다. 요소 모양으로 잘린 옅은 오버레이가 덮였다가 사라지고,
빠르게 탭해도 110ms는 보인다. 키보드 포커스와 마우스 호버도 옅게 표시한다. '애니메이션 삭제' 설정이 켜져 있으면 페이드 없이 바로 켜고 끈다.
오버레이 색을 주지 않으면 `LocalKineticaContentColor`를 보고 고른다. 글자가 어두우면(밝은 표면) 어두운 오버레이를, 글자가 밝으면(어두운 표면이나 강조색 버튼) 흰 오버레이를 쓴다.

`KineticaTheme`과 `KineticaScaffold`는 `LocalIndication`에 `KIndication()`을 넣는다. 그래서 그 안에서 호스트가 쓴 일반 `Modifier.clickable`도
회색 사각 리플이 나오지 않는다. 호스트 리플을 그대로 쓰려면 `KineticaTheme(indication = null)`을 넘긴다. 이때 그 안의 `KineticaScaffold`도 테마를 따라 호스트 값을 그대로 둔다.

```kotlin
// 모양을 알려 주면 모서리 밖은 칠하지 않는다
Row(Modifier.clip(shape).kClickable(shape, onClickLabel = "열기") { open() })
Box(Modifier.clickable(interactionSource = null, indication = KIndication(CircleShape)) { ... })

// 길게 누르기, 선택, 토글
Modifier.kCombinedClickable(shape, onLongClick = ::showMenu) { open() }
Modifier.kSelectable(selected, CircleShape, role = Role.Tab) { select() }
Modifier.kToggleable(checked, shape, role = Role.Checkbox) { checked = it }

KineticaTheme(indication = null) { App() } // 호스트 LocalIndication을 그대로 둔다
```

- `KIndication(shape, color, pressedAlpha, pressedScale)`: `pressedAlpha` 기본값은 0.1이다. `pressedScale`은 수정자 체인에서
  표시보다 뒤에 그려지는 내용만 줄이므로, 배경까지 함께 줄이려면 `Modifier.indication`을 배경보다 앞에 둔다.
- 버튼처럼 통째로 눌리는 카드는 `KSurface(onClick = { ... }, shape = ...)`를 쓰면 모양에 맞는 눌림 표시가 붙는다.
  아이콘만 있는 버튼은 `KIconButton(icon, "공유", onClick = ...)`을 쓴다. 지름 44dp 원 안에 22dp 아이콘을 그린다.

Material 3와 함께 쓸 때 알아 둘 점이 있다.

- Material 3 `Card(onClick)`, `Surface(onClick)`, `Button`, `ListItem`의 눌림은 `LocalIndication`이 아니라 컴포넌트가 직접 만든 `ripple()`을 쓰므로
  `KineticaTheme` 안에서도 리플이 나온다. 리플 없이 쓰려면 `onClick` 없는 `Card` 안에 `Modifier.kClickable(shape)`을 붙이거나 `KSurface(onClick = …)`를 쓴다.
- `MaterialTheme`은 `LocalIndication`을 리플로 다시 넣는다. 그래서 `KineticaTheme` 안쪽에서 `MaterialTheme`을 다시 감싸면 그 아래는 리플로 돌아간다.
  `MaterialTheme`은 늘 `KineticaTheme` 바깥에 두고, 화면 일부만 Material 색을 바꿀 때는 `MaterialTheme` 바로 안에 `KineticaTheme`을 한 번 더 감싼다.
- `KineticaEditor`는 편집기 색을 쓰려고 안에서 `MaterialTheme`을 다시 감싸지만, 바깥의 `LocalIndication`을 다시 넣으므로 `toolbarEnd`나 슬롯에 넣은 호스트 버튼은 편집기 밖과 같은 눌림 표시를 쓴다.

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
| `KDialog(visible, onDismissRequest, origin = …)` | 누른 버튼(`Modifier.kOrigin { origin = it }`)에서 패널이 튀어나오듯 커지고, 배경은 서서히 흐려지며 어두워진다. 바깥을 누르거나 뒤로 가기로 닫는다 |
| `KSheet(visible, onDismissRequest)` | iOS 시트처럼 아래에서 올라오고, 뒤쪽 콘텐츠는 조금 물러나며 모서리가 둥글어진다. 아래로 끌어서 닫고, 덜 끌고 놓으면 던진 속도를 이어받아 제자리로 돌아온다. 넓은 화면에서는 `KSheetDefaults.MaxWidth`(640dp)를 넘지 않고 가운데에 놓인다. `title`은 TalkBack이 읽는 창 이름이고 화면에는 보이지 않는다 |
| `KToastHost(state)` + `state.show("저장했어요", tone = KTone.Positive)` | 위에서 미끄러져 내려오는 토스트. `tone`을 주면 글자 앞에 그 색의 점을 찍는다. TalkBack이 내용을 읽어 준다 |
| `KTabBar(tabs, selectedIndex, onSelect)` | 화면 위에 떠 있는 탭 바. 선택한 탭의 인디케이터가 늘어났다 줄어들며 액체처럼 이동한다 |
| `KSurface(shape, surface)` | 표면 하나. `onClick`을 주면 카드처럼 눌리고, `variant`로 모양을 바꾼다. 안쪽 글자 색은 `LocalKineticaContentColor`로 받는다 |
| `Modifier.kContextMenu(items)` | 길게 누르면 햅틱과 함께 요소가 떠오르고 배경이 흐려지며, 요소 위나 아래에 글래스 메뉴(`KMenuItem`)가 뜬다. `destructive` 항목은 빨간 글자로 보인다. TalkBack에서는 사용자 정의 동작으로 제공한다 |
| `KSegmentedControl(options, selectedIndex, onSelect)` | 선택한 칸의 인디케이터가 늘어났다 줄어들며 이동하는 세그먼트 버튼. 이름은 `maxLines`(기본 2)줄까지 줄을 바꾸고 큰 글꼴에서는 컨트롤이 그만큼 높아진다. `sizing = KSegmentSizing.Proportional`이면 이름 길이에 비례해 칸 폭을 나눈다. `tone`으로 인디케이터 색을 고르고(`KTone.Neutral`은 옅은 표면 글자 색, 나머지는 그 색으로 채움), `role` 기본값은 `Role.RadioButton`이다. 아래 내용 영역을 통째로 바꾸는 탭처럼 쓰면 `Role.Tab`을 넘긴다 |
| `KSwitch(checked, onCheckedChange)` | 누르는 동안 손잡이가 늘어나고, 출렁이며 반대쪽으로 넘어가는 스위치 |
| `KSlider(value, onValueChange, valueRange, steps)` | 잡으면 트랙이 굵어지고 손잡이가 투명하게 커진다. 끝을 넘어 끌면 고무줄처럼 늘어났다가 튕겨 돌아온다. 가로로 움직여야 드래그로 보므로 세로 목록 스크롤을 막지 않는다. 방향키와 TalkBack으로도 조절할 수 있고 `enabled = false`로 끌 수 있다 |
| `KRefreshBox(refreshing, onRefresh) { LazyColumn { … } }` | 맨 위에서 당기면 콘텐츠가 저항감 있게 내려오고 글래스 물방울이 차오른다. 기준 거리를 넘으면 햅틱과 함께 물방울이 길쭉해지고, 놓으면 새로 고친다. TalkBack에서는 "새로 고침" 동작으로 제공한다 |

## 화면 이동

| 컴포넌트 | 동작 |
| --- | --- |
| `KExpandHost(state) { key -> 상세 }` + `Modifier.expandSource(state, key)` + `state.expand(key)` | App Store의 투데이 탭처럼 카드가 제자리에서 화면 가득 커지며 상세 화면이 된다. 배경은 흐려지고, 상세 화면을 아래로 끌면 작아지면서 모서리가 둥글어지며, 놓으면 원래 카드 위치로 돌아간다. 상세 화면 위쪽을 카드와 똑같이 꾸미면 끊김 없이 이어진다 |
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
| `KButton(text, onClick, variant, tone, size, icon, loading)` | `KVariant` 여섯 모양과 `KTone` 네 색 역할, 세 크기. 누르면 젤리처럼 눌리고, `loading`이면 폭을 유지한 채 링이 돈다 |
| `KStepper(value, onValueChange, range, step)` | −/+ 캡슐. 숫자가 굴러가며 바뀌고, 길게 누르면 점점 빨라진다. 끝에 닿으면 그쪽 버튼이 흐려진다 |
| `KChipGroup(chips, selected, onSelectedChange, selection, variant, tone)` | 하나 또는 여러 개를 고르는 칩. 고르면 체크 표시가 그려지며 폭이 부드럽게 늘어난다. `variant`로 선택되지 않은 칩 모양(`Glass`, `Tonal`, `Outlined`)을 고른다 |
| `KProgressBar(progress, wave)` / `KProgressRing(progress, wave) { … }` | 용수철로 따라가는 진행 표시. `wave`를 켜면 액체가 차오르듯 출렁이고, `progress = null`이면 진행 정도를 모르는 상태를 보여 준다 |
| `Modifier.kBadge(count, dot)` | 오른쪽 위(RTL이면 왼쪽 위) 빨간 배지. 숫자가 바뀌면 톡 튄다. 99를 넘으면 `99+` |
| `KWheelPicker(items, selectedIndex, onSelect, cyclic)` | iOS 휠 피커. 원통에 붙은 것처럼 위아래 항목이 기울고, 항목을 지날 때마다 가볍게 진동하며 가운데에 맞춰 멈춘다 |
| `KDatePicker(date, onDateChange, range)` / `KTimePicker(hour, minute, onTimeChange)` | 휠로 만든 날짜·시간 선택기. 년·월·일 순서와 오전/오후 위치는 언어를 따른다 |
| `KSearchBar(query, onQueryChange, onSearch)` | 누르면 끝쪽에서 "취소" 버튼이 밀려 들어오고(`showCancel = false`로 끈다), 글자가 있으면 지우기 버튼이 나온다. 포커스를 받으면 강조색 테두리와 옅은 링이 나타난다 |
| `KTextField(value, onValueChange, label, error)` | 포커스되면 라벨이 위로 떠오르고 강조색 테두리가 부드럽게 나타난다. 글자가 있으면 지우기 버튼이 나오고, `placeholder`, `supportingText`, `leadingIcon`, `trailingIcon`을 더할 수 있다. 오류가 생기면 테두리가 빨개지며 한 번 흔들린다. 키보드가 올라오면 아래 문구까지 보이도록 스크롤한다 |

## 화면 구성과 알림

| 구성 요소 | 하는 일 |
| --- | --- |
| `KLargeTitleBar(rememberKLargeTitleState(), "제목")` | One UI·iOS식 큰 제목. 목록을 스크롤하면 큰 제목이 줄어들며 위 바의 작은 제목으로 바뀐다. 목록에 `Modifier.nestedScroll(state.connection)`을 붙인다. `bottom`에 탭이나 검색창을 주면 큰 제목과 함께 올라가 작은 바 밑에 붙고, `contentMaxWidth` 안에 가운데 정렬된다. 접힌 바의 배경은 아래로 옅어진다 |
| `KCarousel(pageCount) { page -> }` + `KPageIndicator(pagerState)` | 옆 카드가 살짝 보이고 멀어질수록 작아지며 기우는 캐러셀. 점 인디케이터는 넘기는 동안 액체처럼 늘어난다 |
| `SwipeCardStack(items, onSwipe, overlay) { item -> }` | 틴더식 카드 더미. 끌면 기울며 따라오고 던지면 날아간다. `state.undo()`로 되돌린다 |
| `KSpeedDial(expanded, onExpandedChange, items)` | 떠 있는 둥근 버튼. 누르면 +가 ×로 돌고 항목이 차례로 튀어 오르며 뒤가 흐려진다 |
| `KIslandHost(state)` + `state.show(KIslandContent(…))` | 다이내믹 아일랜드식 알림. 작은 캡슐이 부풀어 알림이 되고, `compactText`가 있으면 좁은 캡슐(예: 타이머)로 시작해 누르면 펼쳐진다. 위로 쓸어 닫는다 |
| `KSnackbarHost(state)` + `state.show(message, actionLabel)` | 아래에서 떠오르는 스낵바. "되돌리기" 같은 동작 버튼과 남은 시간을 보여 주는 링이 있고, 옆으로 쓸어 닫는다. 결과를 `Action`/`Dismissed`로 돌려준다 |

## 목록, 차트, 달력

| 구성 요소 | 하는 일 |
| --- | --- |
| `SwipeActionsRow(endActions, startActions = …) { 행 }` + `SwipeAction(label, color, removesRow)` | iOS 메일처럼 행을 밀면 뒤에서 동작 버튼이 드러나고, 끝까지 밀면 첫 동작이 바로 실행된다. `removesRow`면 행이 줄어들며 사라진다. `rememberSwipeActionsGroup()`으로 한 번에 한 행만 열리게 한다. 둥근 카드 행이면 `shape`에 같은 모양을 넘겨 버튼도 그 모서리로 자른다 |
| `ReorderableItem(rememberReorderableListState(listState, onMove), key) { … }` | LazyColumn에서 길게 누르면 항목이 떠오르고 끌어서 순서를 바꾼다. 다른 항목은 비켜 주고, 가장자리에서는 자동으로 스크롤된다 |
| `KExpandable(expanded, onExpandedChange, header) { … }` / `KExpandableGroup` | 아코디언. 화살표가 돌고 내용 높이가 용수철로 펼쳐진다 |
| `KSlideToConfirm(text, onConfirm)` | 손잡이를 끝까지 밀어야 확정되는 버튼(결제, 잠금 해제) |
| `KLineChart(points 또는 series, labels)` | 선이 그려지며 나타나고, 손가락을 대고 옆으로 움직이면 값 말풍선이 따라온다. 데이터가 바뀌면 이전 모양에서 부드럽게 바뀐다 |
| `KBarChart(values, labels)` / `KDonutChart(slices)` / `Sparkline(points)` | 막대는 차례로 자라고 누르면 강조된다. 도넛 조각은 누르면 튀어나오며 가운데에 값을 보여 준다 |
| `ActivityRings(rings)` | Apple Watch 활동 링. 목표를 넘으면 겹쳐 돈다 |
| `KCalendar(rememberKCalendarState(), selection, onSelectionChange, events)` | 월 달력. 좌우로 넘기고, 하루(`Single`) 또는 기간(`Range`)을 고르며, 날짜 아래에 일정 점을 찍는다 |
| `FlipDigits(text)` / `FlipClock()` / `rememberCountdown(target)` | 기계식 플립 카드 숫자. 바뀐 자리만 위 반쪽이 접혀 내려간다 |
| `KRangeSlider(range, onRangeChange, valueRange)` | 손잡이 두 개짜리 범위 슬라이더. 잡으면 값 말풍선이 뜬다 |

## 미디어, 내비게이션, 안내

| 구성 요소 | 하는 일 |
| --- | --- |
| `KMiniPlayer(rememberKMiniPlayerState(), artwork, collapsed, expanded)` | 아래 떠 있는 재생 막대를 누르거나 위로 끌면 전체 화면 플레이어로 커지고, 앨범 아트가 이어지며 커진다. 아래로 끌거나 뒤로 가기로 접는다 |
| `KMiniPlayerBar(state, artwork) { 막대 내용 }` + `KMiniPlayerFullScreen(state, artwork) { 플레이어 }` | 막대와 전체 화면을 나눈 `KMiniPlayer`. 막대는 글래스 캡슐 없이 내용만 그리므로 `KFloatingTabBar`의 `accessory`처럼 다른 컴포넌트 안에 넣는다. 펼치면 막대가 놓인 자리에서 앨범 아트가 이어지며 커지고, 접으면 그 자리로 돌아간다 |
| `PlayPauseIcon` / `MenuCloseIcon` / `PlusCloseIcon` | 모양이 매끄럽게 바뀌는 아이콘 |
| `KRating(rating, onRatingChange, allowHalf)` / `LikeButton(liked, onLikedChange, count)` | 별점과 하트 좋아요. 좋아요를 누르면 하트가 튀며 입자가 터지고 숫자가 굴러간다 |
| `AvatarStack(avatars, max)` / `Modifier.skeleton(visible)` / `KSkeletonText(lines)` | 겹친 아바타와 "+N", 불러오는 동안 빛이 흐르는 자리 표시 |
| `KDetentSheet(rememberKDetentSheetState(detents), header) { … }` | Apple 지도처럼 여러 높이(`Detent.Peek`·`Fraction`·`Full`)에 멈추는 바텀 시트. 안의 목록 스크롤과 자연스럽게 이어지고, 손을 떼면 던진 속도를 보고 가까운 높이로 정착한다. 폴더블을 접고 펴도 같은 높이 단계를 유지한다 |
| `KTopTabs(tabs, pagerState)` | 페이저를 넘기는 동안 인디케이터가 액체처럼 따라가는 상단 탭 |
| `Modifier.kTooltip(text, visible, onDismiss)` / `Modifier.tooltipOnLongPress(text)` | 화살표가 달린 유리 말풍선 |
| `CoachMarkHost(state, steps)` + `Modifier.coachMarkTarget(state, id)` | 처음 쓰는 사람을 위한 기능 안내. 화면이 어두워지고 대상 둘레만 밝게 뚫린다 |
| `KDrawer(drawerContent, state) { 화면 }` | 가장자리에서 끌어 여는 서랍. 열리면 뒤 화면이 밀리며 작아지고 둥글어진다 |

## 입력 2차

| 구성 요소 | 하는 일 |
| --- | --- |
| `KCodeInput(value, onValueChange, length, mask, error, onComplete)` | 한 칸에 한 자리씩 받는 인증번호, PIN 입력란. 붙여 넣기와 SMS 자동 채우기를 받고, `error`가 켜지면 칸이 빨개지며 흔들린다. `imeAction`과 `keyboardActions`로 키보드 동작 키를 바꾼다 |
| `KTagInput(tags, onTagsChange, placeholder, suggestions)` | 쉼표나 완료 키로 칩을 만드는 태그 입력란. 같은 태그는 다시 넣지 않고 그 칩을 흔든다. 빈 입력란에서 지우기를 두 번 누르면 마지막 칩이 지워진다. 입력 중인 글자가 있으면 동작 키가 칩을 만들고, 빈 입력란에서 누르면 `keyboardActions`로 넘기므로 `imeAction = ImeAction.Next`로 두면 다음 입력란으로 갈 수 있다. 추천 목록은 아래 내용을 밀어내지 않고 겹쳐 뜨며, 아래 공간이 모자라면(키보드가 올라왔을 때 등) 입력란 위로 뜬다 |
| `KColorPicker(color, onColorChange, presets, showAlpha, showHexInput)` | 색조 고리와 채도·명도 사각형, 투명도 슬라이더, HEX 입력란, 최근 색과 프리셋 칩이 있는 색 선택기. 키보드로는 Tab으로 고리, 사각형, 투명도 슬라이더를 오가며 방향키로 조절한다 |
| `KSignaturePad(modifier, rememberSignaturePadState())` | 속도에 따라 굵기가 바뀌는 서명란. `state.toImageBitmap()`이나 `state.toSvg()`로 내보내고, 되돌리기와 지우기 버튼이 있다 |
| `KPasswordField(value, onValueChange, label, showStrength, showChecklist)` / `passwordStrength(password)` | 눈 아이콘으로 보이거나 숨기는 비밀번호 입력란. 강도 막대가 약함, 보통, 강함 색으로 차오르고 조건 체크리스트를 보여 줄 수 있다. `imeAction`으로 동작 키를 Next 등으로 바꾼다 |

`KPasswordField`와 `KTagInput`의 `modifier`는 입력란과 칩, 강도 막대를 감싼 바깥 묶음에 붙는다. `testTag`나 `focusRequester`처럼 입력란 자체에 걸어야 하는 것은 `fieldModifier`로 준다. `KCodeInput`은 `modifier`가 곧 입력란이다.

XML 화면의 `NestedScrollView`, `ViewPager2`, `RecyclerView` 안에 `ComposeView`로 넣어도 색상 고리와 사각형, 서명란은 끄는 동안 바깥 View가 터치를 가로채지 않는다. 투명도 슬라이더, `BeforeAfterSlider`, `FlipCard`는 자기 방향(가로 슬라이더는 가로)으로 끌 때만 붙잡고, 반대 방향으로 끌기 시작하면 바깥 스크롤에 넘긴다.

## 미디어와 피드백

| 구성 요소 | 하는 일 |
| --- | --- |
| `KImageViewer(rememberKImageViewerState(), images)` + `Modifier.imageViewerSource(state, index)` | 전체 화면 사진 뷰어. 썸네일에서 커지며 열리고, 핀치로 1~5배 확대, 두 번 탭 확대, 위아래로 끌어 닫기를 지원한다. 확대한 사진이 가장자리에 닿은 뒤 바깥쪽으로 더 끌면 확대를 풀지 않고 옆 사진으로 넘어간다. 사진 대신 원하는 내용을 넣는 `pageCount` 버전도 있다. `images`의 Painter는 `intrinsicSize` 비율대로 맞춰 그리고, `BrushPainter`처럼 크기가 없으면 화면 전체를 채운다. 열린 채 목록이 비면 바로 닫히고 상태 없는 버전은 `onDismiss`를 부른다. 열려 있는 동안 시스템 바 아이콘을 밝게 바꾸고 닫으면 되돌린다 |
| `BeforeAfterSlider(before, after)` / `BeforeAfterSlider(position, onPositionChange, before, after)` | 두 이미지를 겹쳐 놓고 가운데 손잡이를 끌어 비교한다. 가로 드래그만 받아 세로 스크롤을 막지 않는다. 손잡이 위치를 호스트가 저장하거나 바꾸려면 `position`을 받는 버전을 쓴다 |
| `KResultMark(state)` / `KResultDialog(visible, state, title, onDismissRequest)` | `KResult.Loading`이면 링이 돌고, 결과로 바뀌면 원이 닫힌 뒤 체크, ×, 느낌표가 획 순서대로 그려진다. 성공이면 파티클이 튄다 |
| `KOnboarding(pages, onFinish)` + `OnboardingPage(title, body, background, art)` | 첫 실행 안내. 그림이 시차를 두고 따라오고 배경색이 섞이며, 마지막 페이지에서 버튼이 '시작하기'로 늘어난다 |
| `KEmptyState(title, message, art, action)` / `KEmptyIcon(KEmptyIconKind)` | 빈 목록, 오프라인, 오류 화면. 글래스 원판 위 그림이 천천히 떠 있다. 기본 그림은 NoResults, Offline, EmptyInbox, Error |

## 적응형 레이아웃

폴더블과 태블릿에서 화면 폭에 따라 배치를 바꾼다. 기준 폭은 `KAdaptiveDefaults.TwoPaneMinWidth`(600dp)이고, 컴포저블이 받은 폭으로 판단하므로 멀티 윈도우에서도 맞게 동작한다.

| 구성 요소 | 하는 일 |
| --- | --- |
| `KListDetail(selected, onBack, list = { … }) { item -> 상세 }` | 좁으면 상세가 옆에서 밀려 들어오고, 넓으면 목록과 상세를 두 칸으로 놓는다. 반쯤 접은 폴더블에서는 두 칸 경계를 힌지에 맞춘다. 접고 펼쳐도 선택과 스크롤 위치가 유지된다. 상세 상태는 지금 고른 항목 것만 기억하므로 예전 항목을 다시 고르면 처음 상태로 열린다. 두 칸 전환에 쓰려고 붙들어 두는 상세 내용도 최근 8개 항목까지라 많이 열어도 쌓이지 않는다. `detailBackground`에 목록과 같은 배경을 한 번 주면 상세마다 배경을 칠하지 않아도 된다 |
| `KNavigationRail(items, selectedIndex, onSelect, header)` | 화면 시작 쪽에 세우는 내비게이션 레일. 인디케이터가 세로로 늘어났다 줄며 이동한다 |
| `KAdaptiveNavigation(tabs, selectedIndex, onSelect) { padding -> }` | 좁으면 아래 탭 바, 넓으면 레일로 바꾼다. 바뀌어도 내용을 다시 만들지 않는다 |
| `KFloatingTabBar(tabs, selectedIndex, onSelect, state, accessory)` | 화면 아래 떠 있는 탭 바. 목록에 `state.nestedScrollConnection`을 붙이면 아래로 스크롤할 때 선택한 탭 아이콘만 남은 캡슐로 줄어든다. `accessory`는 펼친 동안 탭 바 위에 붙어 있다가 접히면 캡슐 옆으로 내려온다. 여기에 `KMiniPlayerBar`를 넣으면 그 자리에서 전체 화면 플레이어가 펼쳐진다 |

샘플의 메일 앱(앱 화면 예 → 메일)이 `KListDetail`을, 음악 앱이 `KFloatingTabBar`와 그 `accessory`에 넣은 `KMiniPlayerBar`를 쓴다.

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
    .glowBorder(shape)             // 2. 겉에 그리는 효과(glowBorder, sparkle, kBadge): 잘리지 않게 clip보다 앞에 둔다
    .glass(shape = shape)          // 3. 배경(glass, background)
    .clip(shape)                   // 4. 자르기
    .clickable { … }               // 5. 입력(clickable, kContextMenu, tooltipOnLongPress)
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
| 액체 모양 | `Modifier.liquidMorph(color)` / `KBlobButton(text, onClick)`. 누른 자리가 오목하게 들어갔다가 출렁이며 돌아온다 |
| 방울 묶음 | `KBlobGroup { Modifier.blob() }`. 가까운 방울끼리 메타볼처럼 녹아 붙는다. Android 12 이상에서 이어지고, 그보다 낮으면 따로 그린다 |
| 홀로그램 | `Modifier.holographic(shape, useSensor)`. 누른 위치나 기기 기울기에 따라 무지개빛 광택이 움직인다. 누르는 동안 기울기만 주려면 `Modifier.tiltOnPress()` |

## 호스트 앱에서 쓸 때

**어디서 부를 수 있나.** `KineticaScaffold` 안에서 부르면 다이얼로그·시트·메뉴·토스트가 scaffold 위층에 그려지고 뒤 화면 전체를 블러로 비춘다.
호스트가 자기 Scaffold(Material 등)나 그냥 `Column` 안에서 불러도 된다. 이때 다이얼로그·시트·메뉴·펼침 메뉴·기능 안내·펼친 카드는 전체 화면 Dialog 창으로,
토스트·스낵바·아일랜드는 뒤 화면 터치를 막지 않는 Popup 창으로 뜨므로 부른 자리의 배치를 깨뜨리지 않는다. `KMiniPlayer`, `KMiniPlayerFullScreen`, `KDetentSheet`는 부른 자리에 그리므로 화면을 채우는 `Box` 안에 둔다.

**높이와 스크롤.**
- 시트는 내용 높이에 맞추되 최대 높이는 위쪽 안전 영역에서 `KSheetDefaults.TopGap`(48dp) 아래까지다. 내용이 더 길면 시트 안에 `LazyColumn`이나 `verticalScroll`을 넣는다. 목록이 맨 위일 때 아래로 끌면 시트가 내려간다.
- 다이얼로그는 안전 영역 안쪽 28dp 여백까지, 너비 280~420dp다. `fillMaxSize`·`weight`는 그 최대 크기를 채운다.
- 시트·다이얼로그·여러 높이 시트·서랍·펼친 카드·미니 플레이어 안에 `LazyColumn`을 넣어도 무한 높이 오류가 나지 않는다.

**인셋(상태 표시줄, 내비게이션 바, 키보드, 컷아웃).**
- 시트·다이얼로그·여러 높이 시트는 `contentWindowInsets`로 스스로 피한다. 기본값은 시트가 `KSheetDefaults.windowInsets`(아래쪽과 양옆의 safeDrawing), 다이얼로그가 `WindowInsets.safeDrawing`이다.
  안에 입력란이 있으면 키보드가 올라올 때 함께 올라온다. 호스트가 직접 처리하려면 `contentWindowInsets = { WindowInsets(0) }`을 준다.
- 토스트·아일랜드는 위쪽, 스낵바는 아래쪽(키보드 포함)과 양옆의 안전 영역을 피한다. 여러 높이 시트의 `Detent.Peek`·`Fraction` 높이는 내비게이션 바와 키보드 위에서 잰다.
- edge-to-edge를 쓰지 않는 화면에서는 인셋이 0이 되므로 여백이 두 번 들어가지 않는다.
- 사진 뷰어, 펼친 미니 플레이어, 온보딩은 떠 있는 동안 시스템 바 아이콘을 배경에 맞게 바꾸고 닫으면 되돌린다. 펼친 카드 상세처럼 배경을 호스트가 정하는 화면에는 `KSystemBarIcons(light = true)`를 둔다.

**포커스와 TalkBack.**
- 다이얼로그·시트·메뉴·기능 안내·서랍이 열리면 뒤 화면은 TalkBack에서 빠지고, 키보드 Tab 이동도 모달 밖으로 나가지 않는다. 닫으면 연 버튼으로 포커스가 돌아온다.
- 키보드로 쓰는 중일 때만 포커스를 모달 안으로 옮긴다. 터치로 열었을 때는 시트 안 입력란이 저절로 키보드를 띄우지 않는다.
- 토스트·스낵바·툴팁은 뒤 화면을 가리지 않는다.

**별도 창으로 뜬 팝업의 뒤 화면.** `KineticaScaffold` 밖에서 연 다이얼로그·시트는 호스트 화면을 기록할 수 없으므로, Android 12 이상에서 시스템 창 블러(`blurBehindRadius`)로 뒤를 흐린다.
기기 설정이나 절전 모드로 창 블러가 꺼져 있으면(일부 기기는 기본으로 꺼져 있다) 블러 없이 어둡게만 하고, 판은 뒤 아이콘이 비치지 않는 불투명한 색으로 칠한다.
뒤 화면까지 같은 유리 느낌을 내려면 화면을 `KineticaScaffold`로 감싼다.

**팝업 안의 글래스.** 다이얼로그·시트·여러 높이 시트·서랍·미니 플레이어 안에 놓은 글래스(입력란, 칩, 버튼 등)는 판 뒤 화면을 다시 비추지 않고, 판 위에 얹힌 한 겹으로 옅게 그린다.
다른 배경을 비추게 하려면 `backdrop`을 직접 넘긴다.

호스트가 `Modifier.glass`나 `Modifier.kSurface`로 직접 만든 판 안에 글래스 카드를 놓으면, 카드는 판을 건너뛰고 판 뒤 배경을 비춰 판에 구멍이 난 것처럼 보인다.
판 안쪽 내용을 `KGlassLayer { }`로 감싸면 kinetica-ui 팝업과 같이 판 위 한 겹으로 그린다. 판 자신의 수정자는 `KGlassLayer` 바깥에 둔다.

```kotlin
Box(Modifier.glass(shape = RoundedCornerShape(28.dp)).padding(16.dp)) {
    KGlassLayer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.glass(shape = RoundedCornerShape(16.dp)).padding(12.dp)) { Text("카드") }
            KSegmentedControl(listOf("켜기", "끄기"), mode, { mode = it })
        }
    }
}
```

**시트가 콘텐츠를 밀어낼 때 상태 표시줄.** 바텀 시트가 열려 콘텐츠가 뒤로 물러나면 `KineticaScaffold`의 `backdropColor`(기본 검정)가 상태 표시줄 뒤에 드러난다.
콘텐츠가 절반 넘게 물러난 동안에는 `systemBarIcons` 값과 상관없이 상태 표시줄 아이콘을 `backdropColor` 밝기에 맞추고(검정이면 밝은 아이콘), 시트가 내려가면 되돌린다.
내비게이션 바 아이콘은 건드리지 않는다. 그 사이 호스트가 창의 아이콘 설정을 직접 바꾸면 그 값을 지킨다. `backdropColor`가 투명에 가까우면(알파 0.5 미만) 무엇이 비칠지 모르므로 바꾸지 않는다.

**닫을 때 키보드.** 팝업 안 입력란이 포커스를 가진 채 닫히면 포커스를 거두고 키보드를 내린다.

**호스트 View를 넣을 때(AndroidView).** EditText, RecyclerView, ScrollView, WebView, TextureView는 글래스 안에서 터치·스크롤·입력이 그대로 동작하고, 배경에 두면 블러로 비친다.
SurfaceView(지도, 영상, 카메라)는 창과 다른 표면에 그려져 기록되지 않는다. 그 위의 글래스는 블러 없이 색만 덮이므로, 블러가 필요하면 TextureView를 쓰거나 `backdrop = null`로 fallback 색을 쓴다.

**XML(View) 화면에서.** Compose 코드 없이 `com.naury.kinetica.ui.view`의 진입점을 쓴다.

```kotlin
val sheet = KSheets.show(this, R.layout.sheet_share, KPopupOptions(title = "공유"))
sheet.contentView?.findViewById<Button>(R.id.close)?.setOnClickListener { sheet.dismiss() }
KDialogs.show(this, confirmView)
KToasts.show(this, "저장했습니다")
```
```java
KPopupHandle sheet = KSheets.show(this, R.layout.sheet_share);
```
```xml
<com.naury.kinetica.ui.view.KSurfaceView
    android:layout_width="wrap_content" android:layout_height="wrap_content"
    app:kinetica_surfaceStyle="acrylic" app:kinetica_cornerRadius="20dp"> … </com.naury.kinetica.ui.view.KSurfaceView>
```

- 액티비티는 `ComponentActivity`(AppCompatActivity, FragmentActivity 포함)여야 한다. Fragment에서는 `requireActivity()`와 `KPopupOptions(lifecycleOwner = viewLifecycleOwner)`를 넘긴다.
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

`sample-showcase`의 **UI 키트** 화면에서 표면 종류(글래스, 단색, 톤)와 프리셋을 바꿔 보고 다이얼로그, 바텀 시트, 토스트, 탭 바, 컨트롤, 슬라이더, 당겨서 새로 고침을 확인할 수 있다.
**화면 이동**에서는 카드가 커지는 상세 화면과 push 전환, 가장자리에서 끌어 뒤로 가기를, **모션 효과**에서는 고급 효과를 확인한다.

## 폼과 목록

설정 화면과 입력 폼을 만드는 기본 컴포넌트다. 모두 `surface`를 받으므로 글래스, 단색, 톤 표면에서 같은 코드로 쓴다.
쇼케이스 **컴포넌트** 화면의 **입력** 탭 맨 위에 설정 앱처럼 꾸민 예가 있다.

| 구성 요소 | 하는 일 |
| --- | --- |
| `KCheckbox(checked, onCheckedChange, shape)` / `KTriStateCheckbox(state, onClick)` | 켜지면 상자가 튀듯 차오르고 체크 표시가 획 순서대로 그려진다. `shape`는 `Square`와 `Circle`, 미정 상태는 가로 막대다. 콜백이 `null`이면 누를 수 없는 표시만 그린다 |
| `KRadio(selected, onClick)` / `KRadioGroup(options, selectedIndex, onSelect)` | 선택되면 가운데 점이 살짝 튀며 커진다. 묶음은 행 전체를 누를 수 있고 TalkBack이 한 묶음으로 읽는다 |
| `KSwitch(checked, onCheckedChange, variant)` | `KSwitchVariant.Capsule`(기본, 손잡이가 늘어나고 출렁임), `Check`(켜지면 손잡이가 커지며 체크가 그려짐), `Slim`(얇은 트랙에 테두리 두른 손잡이). `accent`를 넘기지 않으면 `Capsule`은 `positive`, 나머지는 `accent` 색이다. RTL에서는 켜진 손잡이가 왼쪽에 간다 |
| `KListItem(headline, supporting, overline, leading, trailing, onClick)` | 1~3줄 목록 행. 앞쪽은 `KListLeading.Icon`(색 사각형 안 아이콘 가능), `Avatar`, `Thumbnail`, `Custom`, 끝쪽은 `KListTrailing.Value`, `Switch`, `Checkbox`, `Radio`, `Disclosure`, `Custom`이다. 끝쪽이 스위치, 체크박스, 라디오면 행 전체가 그 컨트롤 하나가 된다 |
| `KListSection(header, footer, style) { item { … } }` | 행 묶음. `InsetGrouped`는 표면 하나에 행을 담아 첫 행 위와 끝 행 아래만 둥글게 하고, 눌림 표시도 그 모서리로 자른다. `Plain`은 표면 없이 놓는다. 행 사이 구분선은 행 글자가 시작하는 곳부터 긋는다 |
| `KDivider(startInset)` / `KVerticalDivider()` | 기본 두께는 화면 픽셀 하나다 |
| `KSwitchRow`, `KCheckboxRow`, `KRadioRow`, `KValueRow`, `KDisclosureRow` | 설정 화면 행 프리셋. `KListItem` 위에 얹은 것이라 섹션 안에서 구분선과 모서리가 맞는다 |
| `KTextField(…, variant, prefix, suffix, maxLength, minLines)` | `KTextFieldVariant`로 모양을 고른다. `Surface`(기본, 표면 상자), `Outlined`(떠오른 라벨이 위 테두리에 걸치고 그 자리 테두리가 끊김), `Filled`(옅은 바탕과 아래 선), `Underline`(아래 선만), `Inline`(라벨 왼쪽 고정, 값 오른쪽 정렬, 섹션 행 안에 넣는다). 접두와 접미 글자는 라벨이 떠오를 때 나타나고, `maxLength`를 주면 넘는 입력을 자르고 아래 끝에 글자 수를 보여 준다 |
| `KTextArea(value, onValueChange, label, minLines, maxLines, maxLength)` | 여러 줄 입력란. 최소 줄 수만큼 높이를 잡고 최대 줄 수까지 자란다. 라벨은 첫 줄 자리에서 떠오른다 |

```kotlin
KListSection(header = "연결", footer = "비행기 모드를 켜면 모든 무선 연결이 끊깁니다.") {
    item { KSwitchRow("비행기 모드", airplane, { airplane = it }, leading = KListLeading.Icon(Icons.Plane, container = Orange)) }
    item { KDisclosureRow("Wi-Fi", onClick = ::openWifi, value = "Home") }
}
KTextField(price, { price = it }, "가격", variant = KTextFieldVariant.Outlined, prefix = "₩", maxLength = 10)
```

기존 `KTextField`와 `KSwitch` 호출은 그대로 컴파일된다. 새 파라미터는 모두 끝에 기본값과 함께 붙었고, `KSwitch`의 `onCheckedChange`는 `null`을 받을 수 있게 넓어졌다.
`KSwitch`의 `accent` 기본값은 `Color.Unspecified`로 바뀌었지만 iOS 모양에서는 전처럼 `positive` 색을 쓴다.

섹션 안 행은 한꺼번에 구성된다. 행이 많으면 `LazyColumn` 안에서 `kListSection(items, key, header, footer, sectionKey) { item -> … }`을 쓴다.
머리글, 행, 꼬리글이 각각 지연 항목이 되어 보이는 행만 구성하고, 행마다 표면을 따로 그리되 이어지는 위아래 가장자리는 잘라 `KListSection`과 같은 모양이 된다.
행 사이가 붙어야 하므로 그 `LazyColumn`에는 `verticalArrangement` 간격을 주지 않는다.
`LazyColumn`에 행 300개를 놓아도 가만히 있는 동안이나 스위치가 넘어가는 동안 행을 다시 구성하지 않는 것을 `KFormsTest`가 확인한다.

## 버튼, 칩, 카드

버튼과 칩, 카드는 모두 `variant`(모양), `tone`(색 역할), `surface`(표면 스타일)를 따로 받는다. 글래스 표면에서 `Outlined` 버튼을 쓰거나 단색 표면에서 `Glass` 카드를 써도 된다.
글자가 표면 위에 놓이는 `Tonal`, `Outlined`, `Text` 모양에서는 색 역할의 색이 표면과 대비 3:1이 안 되면(흰 표면 위 초록 글자처럼) 글자 색을 조금 어둡게, 어두운 표면이면 밝게 고쳐 쓴다.

| 컴포넌트 | 쓰임 |
| --- | --- |
| `KButton` | 기존 파라미터에 `trailingIcon`(뒤 아이콘), `fullWidth`(받은 폭 채우기), `success`(로딩 뒤 원으로 줄며 체크 표시)가 더해졌다. 크기는 `Small`, `Medium`, `Large` |
| `KIconButton`, `KIconToggleButton` | 아이콘 버튼에 `shape`(원, 둥근 사각형)가 더해졌고, 크기는 `KIconButtonSizes.Small`(36dp), `Medium`(44dp), `Large`(56dp)를 쓴다. 토글은 켜질 때 색이 가운데에서 퍼지고 `checkedIcon`으로 바뀐다 |
| `KFab`, `KExtendedFab` | 떠 있는 주 동작 버튼. 크기 `Small`, `Regular`, `Large`. 확장형은 `expanded = rememberFabExpanded(listState)`를 넘기면 목록을 내릴 때 아이콘만 남게 접힌다 |
| `KSplitButton` | 주 동작과 꺾쇠 버튼을 붙인 분할 버튼. 꺾쇠를 누르면 `onMenuClick`이 불리고, `menuExpanded`면 꺾쇠 버튼이 원으로 바뀐다. 메뉴는 호출한 쪽이 띄운다 |
| `KButtonGroup`, `KToggleGroup` | 가로 버튼 묶음. `KGroupStyle.Connected`(맞붙은 칸)와 `Separated`(떨어진 캡슐)가 있고, 누르고 있는 칸이 넓어지며 양옆 칸이 비켜 준다. 묶음 전체 폭은 그대로다. 토글 묶음은 `KGroupSelection.Single`, `Multiple` |
| `KFilterChip`, `KInputChip`, `KAssistChip`, `KSuggestionChip` | 칩 단독 컴포넌트. 필터 칩은 켜면 체크가 그려지고, 입력 칩은 아바타와 지우기 버튼이 있다. 동작 칩은 앞 아이콘을 강조색으로, 제안 칩은 글자만 쓴다 |
| `KChipRow`, `KChipGroup` | `KChipRow`는 칩을 한 줄에 놓고 가로로 스크롤한다. `KChipGroup`은 이름 목록으로 필터 칩 묶음을 만들고, `wrap = false`면 같은 방식으로 한 줄에 둔다 |
| `KCard` | `Glass`(기본), `Elevated`, `Filled`, `Tonal`, `Outlined`, `Text` 카드. `Filled`에 `KTone.Accent`를 주면 강조색으로 채운 판이 된다 |
| `KSelectableCard` | 요금제, 배송 방법처럼 큰 선택지. 고르면 테두리가 굵어지고 위 끝 모서리에 체크 원이 튀어나온다. 하나만 고르면 `role = Role.RadioButton` |
| `KMediaCard`, `KHorizontalCard` | 사진 위 글자 카드(아래쪽 스크림으로 흰 글자 대비 확보)와 썸네일+글자 가로 카드. 썸네일 모서리는 카드와 동심원으로 깎는다 |
| `KBadge`, `KStatusBadge` | `KBadge(count)`는 줄 안 숫자 배지, `KBadge("NEW")`는 글자 배지, `KStatusBadge(text, KStatus.Success)`는 상태 배지다. 다른 요소 모서리에 겹치는 배지는 그대로 `Modifier.kBadge` |
| `KAvatar` | 사람 한 명의 아바타. `KAvatar(KAvatar("민지"), size = KAvatarSize.Large, status = KAvatarStatus.Online)`처럼 `AvatarStack`에 넣는 값을 그대로 넘긴다. 크기 5단계, 원이나 둥근 사각형 |

```kotlin
KButton("장바구니에 담기", onClick = ::add, loading = adding, success = added, fullWidth = true, size = KButtonSize.Large)
KToggleGroup(listOf(KGroupItem("일"), KGroupItem("주"), KGroupItem("월")), setOf(period), { period = it.first() }, Modifier.fillMaxWidth())
KChipRow(contentPadding = PaddingValues(horizontal = 20.dp)) { filters.forEach { f -> KFilterChip(f.name, f.on, { toggle(f) }) } }
KSelectableCard(plan == Yearly, { plan = Yearly }, role = Role.RadioButton) { Text("연간 결제") }
```

기존 `KButton`, `KIconButton`, `KChipGroup`, `kBadge`, `AvatarStack` 호출은 그대로 컴파일된다. 새 파라미터는 모두 기본값이 있고, `KChipGroup`의 칩은 `KFilterChip`과 같은 모양으로 그린다.
'애니메이션 삭제' 설정에서는 성공 모핑, 토글 채우기, 선택 카드 체크, 확장 FAB 접기가 바로 끝나고 버튼 묶음은 비켜 주지 않는다.
`LazyColumn`에 버튼, 필터 칩, 배지, 아바타, 토글 버튼을 담은 행 300개를 놓아도 로딩 링이 도는 동안 행을 다시 구성하지 않는 것을 `KButtonsChipsCardsTest`가 확인한다.
쇼케이스 **컴포넌트** 화면의 **버튼·칩·카드** 탭에서 쇼핑과 프로필 장면, 모양과 색 역할 조합을 볼 수 있다.

## 내비게이션, 앱 바, 오버레이

새 컴포넌트는 모두 `surface`를 받고 `Modifier.kSurface`로 그린다. 메뉴, 선택 목록, 다이얼로그, 시트, 검색 화면은 `KineticaScaffold` 안에서는 오버레이 레이어에 그려 글래스 표면에 배경이 비치고, 밖에서는 기존 규칙대로 창을 따로 띄운다(모달은 대화 상자 창, 메뉴와 자동 완성은 팝업 창).

| 컴포넌트 | 쓰임 |
| --- | --- |
| `KTopAppBar` | 상단 앱 바. `KTopAppBarVariant.Small`(제목 시작 쪽), `CenterAligned`(가운데), `Medium`(26sp 제목이 스크롤하면 위 줄 작은 제목으로 접힘). `rememberKTopAppBarState(listState)`를 넘기면 맨 위에서는 배경이 없다가 콘텐츠가 바 아래로 지나갈 때 배경과 경계선이 나타난다. 높이가 고정이라 콘텐츠 패딩이 바뀌지 않는다 |
| `KBottomAppBar` | 화면 아래 도구 막대. 시작 쪽 `actions`, 끝 쪽 `floatingActionButton` |
| `KFloatingToolbar` | 떠 있는 캡슐 도구 막대. `rememberKFloatingToolbarState()`의 `connection`을 목록에 붙이면 아래로 스크롤할 때 접히고 위로 스크롤하면 펼쳐진다. FAB는 캡슐 옆에 남는다 |
| `KTopTabs(indicator = …)` | 인디케이터 `KTabIndicator.Pill`(기본), `Underline`(강조색 밑줄), `Segmented`(트랙 위 밝은 칸). 어느 모양이든 페이지를 넘기는 동안 손가락을 따라 늘어났다 줄어든다 |
| `KTab(badge, dot)` | 탭 배지. `KTabBar`, `KFloatingTabBar`는 아이콘 모서리에, 아이콘 없는 탭과 `KTopTabs`는 이름 옆에 붙는다 |
| `KSegmentedControl(segments: List<KSegment>, …)` | 아이콘 칸, 아이콘과 글자 칸. `selected: Set<Int>`를 받는 오버로드는 여러 칸을 함께 켜고(체크 표시가 밀려 들어옴), `requireSelection`이면 마지막 칸을 끌 수 없다 |
| `KDropdownMenu` | 버튼에 붙는 메뉴. `KMenuEntry.Action`(아이콘, 끝 글자, 위험), `Check`(누르면 메뉴를 열어 둔 채 켜고 끔), `Submenu`(같은 판 안에서 넘어감), `Section`, `Divider`. 앵커 아래 공간이 모자라거나 키보드가 올라와 있으면 위로 뒤집고, 화면 가장자리에서는 안으로 밀어 넣는다. 키보드로 열면 첫 항목에 초점이 가고 방향키로 옮기며, 끝 방향키로 하위 메뉴에 들어가고 시작 방향키나 Esc로 나온다 |
| `KSelect` | 입력란 모양의 선택 상자. 상자 폭과 같은 목록이 펼쳐지고 고른 항목에 체크가 붙는다. TalkBack은 고른 값을 상태로 읽는다 |
| `KAutocomplete` | 글자를 치면 제안이 뜨는 입력란. 초점과 키보드는 입력란에 남고, 아래 방향키와 Enter로 고르며 Esc로 닫는다. 같은 부분은 굵게 표시한다 |
| `KAlertDialog` | 제목, 본문, 아이콘, 버튼 1~3개(`KDialogAction`과 `KDialogActionStyle.Default`, `Preferred`, `Destructive`, `Cancel`). 짧은 두 버튼은 나란히(취소가 시작 쪽), 셋이거나 글자가 길면 세로로 쌓고 취소를 맨 아래에 둔다 |
| `KActionSheet` | iOS 동작 시트. 동작 판과 떨어진 취소 판, 아래로 끌어 닫기(손을 떼면 닫힘이나 제자리로 스프링 정착). `presentation = KSheetPresentation.Auto`면 600dp 이상 창에서 버튼 옆 팝오버로 바뀐다(취소는 바깥 누르기로 대신) |
| `KDrawer(mode = KDrawerMode.Modal)` | 모달 서랍. 기존 서랍(`Push`)은 뒤 화면을 밀어내고, `Modal`은 뒤 화면을 그 자리에서 어둡게 덮는다. 끌기, 뒤로 가기 예측, 초점 처리는 같다 |
| `Modifier.kRichTooltip` | 제목, 설명, 글자 버튼이 있는 툴팁. 저절로 닫히지 않고 바깥을 누르거나 버튼을 누르면 닫힌다 |
| `KSearchView` | 누르면 캡슐이 커져 검색 화면이 된다. 좁은 창은 화면 전체, 600dp 이상은 캡슐 자리에서 펼쳐지는 판(최대 720dp). 비어 있으면 최근 검색(지우기, 모두 지우기), 글자가 있으면 제안(끝 화살표로 검색어 칸에만 채우기) |

```kotlin
val bar = rememberKTopAppBarState(listState)
KineticaScaffold(topBar = { KTopAppBar("사진", state = bar, onBack = ::finish) }) { padding -> /* 목록 */ }

Box {
    KIconButton(MoreIcon, "더 보기", { menu = true })
    KDropdownMenu(menu, { menu = false }, listOf(KMenuEntry.Check("숨긴 파일", hidden) { hidden = it }, KMenuEntry.Action("삭제", destructive = true) { delete() }))
}
KAlertDialog(asking, { asking = false }, "삭제할까요?", listOf(KDialogAction("취소", KDialogActionStyle.Cancel) {}, KDialogAction("삭제", KDialogActionStyle.Destructive, onClick = ::delete)))
```

`KDropdownMenu`, `KActionSheet`의 팝오버, `KSearchView`는 부른 자리의 부모 레이아웃을 앵커로 쓰므로, 여는 버튼과 함께 `Box`로 감싸 부른다. 보이는 동안 크기 0인 요소가 그 자리에 하나 놓인다.
큰 제목 바 `KLargeTitleBar`는 그대로 두었다. iOS와 One UI 화면처럼 34sp 큰 제목이 필요하면 큰 제목 바를, 상세 화면이나 편집 화면에는 `KTopAppBar`를 쓴다.
'애니메이션 삭제' 설정에서는 메뉴와 시트, 검색 화면이 바로 열리고 닫히며, 하위 메뉴와 세그먼트 체크도 움직이지 않고 바뀐다.
쇼케이스 **컴포넌트** 화면의 **내비게이션·오버레이** 탭에서 모두 눌러 볼 수 있다. 동작은 `KOverlaysTest`가 확인한다.

## 피드백, 로딩, 상태

알림, 로딩, 빈 화면처럼 앱이 사용자에게 상태를 알리는 컴포넌트다. 모두 `surface`를 받고 `Modifier.kSurface`로 그리므로 단색과 톤 표면에서는 배경을 기록하지 않는다.
종류는 배지와 같은 `KStatus`(`Neutral`, `Info`, `Success`, `Warning`, `Error`)로 고르고, 종류마다 `KStatusIcon`(원 안의 i, 체크, 느낌표, 경고는 둥근 삼각형)과 색이 정해진다.

| 컴포넌트 | 쓰임 |
| --- | --- |
| `KToaster` + `rememberKToasterState()` | 종류별 아이콘이 붙은 토스트를 위(`KToastPosition.Top`)나 아래(`Bottom`)에 쌓는다. 새 토스트가 앞에 오고 뒤 토스트는 작아지며 끝만 비친다. 쌓인 토스트를 누르면 펼치고, 펼친 동안에는 사라지기 타이머가 멈춘다. 옆으로 쓸면 닫힌다. `maxVisible`(기본 3)을 넘는 토스트는 앞의 것이 닫힐 때까지 기다린다. `richColors`면 바탕에 종류 색을 옅게 깐다 |
| `KToasterState.show`, `loading`, `update`, `dismiss`, `promise` | `show`는 id를 돌려준다. `loading`은 돌아가는 아이콘이 붙고 저절로 사라지지 않으며, `update(id, …)`로 결과를 알린다. `promise(loading, success, error) { … }`는 로딩 토스트를 띄우고 작업이 끝나면 같은 토스트를 성공이나 실패로 바꾼 뒤 `Result`를 돌려준다 |
| `KToastHost(tone = …)` | 기존 토스트. 색 점 대신 상태 아이콘을 단다. `Positive`는 성공, `Destructive`는 오류, `Accent`는 정보 아이콘이다 |
| `KSnackbarState.show(…, status)` | 스낵바에 종류 아이콘이 붙는다. 문구는 두 줄까지 보이고, 동작 버튼 글자가 길거나 문구 자리가 좁으면 버튼을 문구 아래 줄 끝으로 내린다. 오른쪽 카운트다운 링이 닫기 버튼이다 |
| `KBanner(visible, message, status, title, actions, onDismiss)` | 화면 위쪽에 머무는 배너. 저절로 사라지지 않고 레이아웃 안에 자리를 차지하며, `visible`이 바뀌면 높이가 펼쳐지고 접힌다. 글자 버튼(`KBannerAction`)과 닫기 버튼을 둘 수 있다 |
| `KAlert(message, status, title, variant)` | 본문 안 알림 상자. `KVariant.Tonal`(기본), `Outlined`, `Filled`, `Glass`. 제목은 TalkBack에서 제목으로 읽히고, 아이콘은 상태 이름으로 읽힌다 |
| `KNoticeBar(text, status)` | 한 줄 공지 막대. 글이 막대보다 길면 글의 방향으로 흘러가고 끝쪽 가장자리를 흐리게 지운다. 화면 밖에서는 멈춘다. `scroll = false`거나 '애니메이션 삭제'면 줄을 바꿔 전부 보여 준다 |
| `KLoadingIndicator(style)` | `KLoadingStyle.Dots`(점 튀기기), `Bars`(물결 막대), `Morph`(Material 3 Expressive식 도형 모핑, 기본), `Orbit`(궤도), `Pulse`(퍼지는 고리). `contained`면 표면 원판 위에 올린다. '애니메이션 삭제'면 멈춘 모래시계 아이콘과 "불러오는 중…" 글자로 바뀐다 |
| `Modifier.borderSpinner(active, shape)` | 빛 한 줄기가 꼬리를 끌며 테두리를 따라 돈다. 둘레 길이를 따라 같은 속도로 움직여 긴 카드의 모서리에서 빨라지지 않는다. 꺼져 있으면 시계도 멈춘다 |
| `KSegmentedProgressBar`, `KBufferProgressBar`, `KStepProgressBar` | 스토리식 칸 막대, 미리 받은 구간이 보이는 막대, 단계 점이 있는 막대. 단계 막대는 TalkBack에서 "5단계 중 2단계"처럼 읽힌다. 모두 RTL에서 오른쪽부터 찬다 |
| `KSkeletonListItem`, `KSkeletonCard`, `KSkeletonAvatar` | 스켈레톤 프리셋. 목록 행 프리셋은 `KListItem`과 높이와 여백이 같아 불러온 뒤 내용이 튀지 않는다. `Modifier.skeleton`과 `KSkeletonText`에도 `style = KSkeletonStyle.Pulse`(깜박임)가 생겼다 |
| `KEmptyIconKind` | 빈 화면 그림에 `NoPermission`(자물쇠), `Search`(빈 돋보기), `Success`(완료 체크), `Maintenance`(공사 고깔)를 더해 8가지다 |
| `KRefreshBox(indicator = …)` | 당겨서 새로 고침 표시를 `KRefreshIndicator.Drop`(기본, 글래스 물방울), `Arrow`(Material식 원 화살표), `Dots`(점 세 개)에서 고른다 |
| `KHaptics` / `rememberKHaptics()` | 키트와 같은 규칙의 햅틱. `KHapticType.Selection`, `Tick`, `Success`, `Warning`, `Error`, `Edge`, `LongPress`. 새 상수가 없는 옛 기기에서는 느낌이 비슷한 상수로 바꿔 낸다. `LocalKineticaHaptics provides false`로 앱 전체에서 끈다 |

```kotlin
val toaster = rememberKToasterState()
KineticaScaffold(topBar = { … }) { padding ->
    KBanner(offline, "인터넷에 연결되어 있지 않아요", status = KStatus.Warning, actions = listOf(KBannerAction("다시 시도", ::retry)))
    if (loading) repeat(6) { KSkeletonListItem() } else KListSection { … }
    KToaster(toaster)
}
scope.launch { toaster.promise("사진을 올리는 중…", success = { n: Int -> "사진 ${n}장을 올렸어요" }, error = { "올리지 못했어요" }) { upload() } }
```

TalkBack은 토스트, 스낵바, 배너가 나타나거나 내용이 바뀌면 읽어 준다. `KStatus.Error`는 읽던 것을 끊고 바로 읽고(assertive), 나머지는 읽던 것이 끝난 뒤 읽는다(polite).
토스트의 표시 시간은 접근성 설정의 '조치를 취할 시간'이 더 길면 그 값을 따른다.
'애니메이션 삭제' 설정에서는 토스트와 배너가 바로 나타나고 사라지며, 로딩 표시는 멈춘 아이콘과 글자로, 테두리 로딩은 고르게 칠한 테두리로 바뀐다.
펼친 토스트는 실제 높이로 늘어놓으므로 글자 2배와 RTL에서도 겹치지 않는다. 이것과 함께 `LazyColumn` 300행에 로딩 표시, 스켈레톤, 테두리 로딩을 놓아도 애니메이션 중에 행을 다시 구성하지 않는 것을 `KFeedbackTest`가 확인한다.
쇼케이스 **컴포넌트** 화면의 **피드백·로딩** 탭에서 모두 눌러 볼 수 있다.

## 데이터 표시와 도메인 블록

대시보드, 커머스, 금융, 일정 화면에서 자주 쓰는 블록이다. 모두 `surface`를 받고 `Modifier.kSurface`로 그린다.

| 컴포넌트 | 쓰임 |
| --- | --- |
| `KStatCard` | 제목, 큰 값, 변화율, 스파크라인을 담은 숫자 카드. 값은 자릿수가 굴러가며 바뀐다. 변화율에는 화살표와 부호를 붙이고, `increaseIsGood = false`면 오를 때 빨간색으로 칠한다 |
| `KGauge` | 반원(`KGaugeShape.Semicircle`)이나 270도 원호(`Arc`) 게이지. `KGaugeSegment`로 구간 색과 이름을 주면 트랙에 구간을 옅게 깔고 지금 구간 색으로 채운다 |
| `KAreaChart` | 여러 계열 영역 차트. `stacked = true`(기본)면 아래부터 쌓고 띠 사이에 2dp 틈을 비우며, `false`면 계열마다 선 아래를 옅게 채워 겹친다 |
| `KMultiBarChart` | 여러 계열 막대. `KBarMode.Stacked`(누적, 음수 불가)와 `Grouped`(묶음, 음수 가능) |
| `KPieChart` | 파이 차트. 조각 안에 8% 이상인 조각의 비율을 적고, 누르면 조각이 튀어나온다. 넓은 자리에서도 320dp를 넘지 않는다 |
| `KHeatmap`, `KCalendarHeatmap` | 행×열 격자 히트맵과 요일×주 달력 히트맵(잔디). 달력 히트맵은 기간이 길면 가로로 스크롤하고 처음에 최근 주를 보인다 |
| `KChartPalettes` | 차트 색. `categorical(surface)`는 색각 이상에도 이웃 색이 구별되는 8색(밝은 표면, 어두운 표면 따로), `sequential(surface, levels)`는 크기 단계 색, `Good`, `Warning`, `Serious`, `Critical`은 상태 색이다 |
| `KTimeline` | 점과 선으로 잇는 타임라인. `KTimelineStatus.Done`, `Current`(점이 숨 쉬듯 퍼짐), `Upcoming`, `Error`. 세로(`Vertical`)와 가로(`Horizontal`) |
| `KStepIndicator` | 단계 표시. `KStepStyle.Numbered`(번호 원과 이름), `Dots`(지금 단계 점이 캡슐로 늘어남), `Bar`(나눈 막대와 'n/전체') |
| `KWizard`, `rememberKWizardState` | 단계 표시, 넘어가는 내용, 이전과 다음 버튼을 묶은 마법사. 첫 단계가 아니면 뒤로 가기가 앞 단계로 돌아간다 |
| `KProfileHeader` | 커버 위에 아바타를 겹친 프로필 머리. 숫자 칸(`KProfileStat`)과 버튼 줄, 스크롤에 따른 접힘(`collapse`) |
| `KPriceTag`, `KPricingCard`, `KBillingPeriodToggle` | 할인율과 취소선 정가가 있는 가격표, 추천 배지가 걸리는 요금제 카드(라디오), 월간과 연간 전환. `KPriceDefaults.format`, `discountPercent` 도우미 |
| `KQuantityStepper` | 쇼핑 수량 스테퍼. `KQuantityStyle.Outlined`(테두리 칸)와 `Collapsible`(0이면 + 하나, 1에서 빼기가 휴지통) |
| `KKeypad`, `KPinDots` | 숫자 키패드(`KKeypadMode.Pin`, `Numeric`, `Decimal`)와 PIN 자리 점. 지우기 키를 길게 누르면 `onClear`, PIN 모드 아래 줄 시작 칸은 생체 인증 버튼 자리다 |
| `KAmountInput` | 키패드로만 입력하는 금액 입력. 통화 형식(천 단위, 기호 위치, 소수 자릿수)을 따르고 빠른 금액 칩(`KAmountPreset`)을 둔다. 값은 숫자 문자열이고 `KAmountDefaults.toBigDecimal`로 바꾼다 |
| `KDateRangeDialog`, `KTimeRangeDialog` | 빠른 선택(`KRangePresets.dates()`, `times()`)이 있는 기간, 시간 범위 다이얼로그. 적용을 눌러야 결과를 넘기고, 시간 범위는 밤을 넘기면 안내한다 |
| `KWeekStrip` | 한 주를 한 줄로 보이고 좌우로 넘기는 주간 달력 띠. 일정 점은 3개까지 |

```kotlin
val colors = KChartPalettes.categorical(KineticaTheme.surface)
KStatCard("이번 주 방문", "12,480", change = 0.124f, trend = visits, caption = "지난주 대비")
KAreaChart(listOf(ChartSeries(app, colors[0], "앱"), ChartSeries(web, colors[1], "웹")), Modifier.height(200.dp), labels = days)
KMultiBarChart(series, Modifier.height(200.dp), labels = months, mode = KBarMode.Grouped)
KTimeline(listOf(KTimelineItem("주문 접수", time = "10:02", status = KTimelineStatus.Done), KTimelineItem("배송 중", status = KTimelineStatus.Current)))
KAmountInput(amount, { amount = it }, Currency.getInstance("KRW"), label = "보낼 금액")
```

기존 차트와의 관계: 계열 하나의 흐름은 `KLineChart`(채우기를 주면 영역처럼 보인다), 여러 계열의 합과 비중은 `KAreaChart`, 막대 하나짜리는 `KBarChart`, 여러 계열 막대는 `KMultiBarChart`를 쓴다.
가운데에 합계를 크게 보일 때는 `KDonutChart`, 비율 자체가 중요하고 조각이 다섯 개 안팎이면 `KPieChart`를 쓴다. 모두 같은 `ChartSeries`와 `ChartSlice`를 받는다.

차트 색은 `KChartPalettes`를 앞에서부터 차례로 쓴다. 계열이 8개를 넘으면 돌려 쓰지 말고 작은 것을 '기타'로 묶는다. 계열이 둘 이상이면 범례가 붙어 색만으로 구별하지 않게 한다.
기존 `KChartDefaults.Palette`는 그대로 두었다. 새 화면에는 표면 밝기에 맞춰 고르는 `KChartPalettes`를 권한다.

TalkBack은 차트를 표로 읽는다. 영역 차트와 여러 계열 막대는 가로 항목 하나를 한 행으로 두고 '1월: 앱 3, 웹 5, 합계 8'처럼 계열 값과 합계를 차례로 읽으며, 행 번호를 알린다(`CollectionInfo`, `CollectionItemInfo`).
히트맵은 행과 열 번호가 있는 진짜 표로 읽고, 파이는 조각마다 값과 비율을 읽는다. 행에서 두 번 탭하면 그 항목의 말풍선을 연다.

넓은 화면: 아래 축 이름은 가장 긴 이름과 간격이 들어가도록 건너뛰며 놓으므로, 600dp와 840dp 폭에서는 더 많은 이름이, 좁은 화면이나 글자 2배에서는 적은 이름이 보인다.
히트맵 칸은 폭에 맞춰 `maxCell`까지만 커지고, 게이지와 파이는 각각 360dp, 320dp를 넘지 않는다.

RTL 정책은 두 갈래다.

- 시간 축이 있는 차트(`KAreaChart`, `KMultiBarChart`, `KHeatmap`, `KCalendarHeatmap`)는 기존 `KLineChart`, `KBarChart`와 같이 RTL에서도 왼쪽이 처음이고 오른쪽이 최근이다. 아랍어와 히브리어 앱의 시계열 그래프도 대부분 왼쪽에서 오른쪽으로 읽고, 숫자도 왼쪽부터 쓰기 때문이다. 범례와 말풍선 글자는 앱 방향을 따른다.
- 원 그래프(`KPieChart`, `KGauge`)는 시계와 계기판 관례대로 방향을 바꾸지 않는다.
- 단계의 흐름(`KTimeline` 가로, `KStepIndicator`, `KWizard`)과 달력(`KWeekStrip`, 기간 다이얼로그의 달력)은 읽는 방향을 따라 RTL에서 오른쪽부터 놓고, 마법사는 진행 방향 반대로 밀려 들어온다.
- 키패드 숫자 배치는 전화 키패드 관례대로 바꾸지 않고, 지우기 키 화살표만 뒤집는다.

'애니메이션 삭제' 설정에서는 차트가 처음부터 최종 모양을 그리고, 게이지와 단계 표시는 바로 옮겨 가며, 타임라인의 지금 단계 점과 금액 입력 커서는 움직이지 않는다. 마법사는 옅어지기만 한다.
반복 애니메이션(타임라인 점, 커서)은 화면에 보이는 동안만 돈다. `LazyColumn` 300행에 숫자 카드, 게이지, 여러 계열 막대를 놓아도 시간이 흐르는 동안 행을 다시 구성하지 않는 것을 `KDataChartsTest`가, 도메인 블록의 동작과 접근성은 `KDomainBlocksTest`가 확인한다.
쇼케이스 **컴포넌트** 화면의 **데이터·도메인** 탭에서 대시보드, 배송 추적, 결제 단계, 요금제, 송금, PIN, 일정 장면을 볼 수 있다.

## 소셜, 미디어, 모션 확장

새 컴포넌트는 모두 `surface`를 받고 `Modifier.kSurface`로 그린다. 단색과 톤 표면에서는 배경을 기록하지 않는다.

| 컴포넌트 | 쓰임 |
| --- | --- |
| `KChatBubble` | 채팅 말풍선. 보낸 말풍선(`KChatSide.Sent`)은 강조색으로 채우고 끝 쪽에, 받은 말풍선은 표면 스타일로 시작 쪽에 붙는다. `position`(`chatGroupPosition(앞과 같은 사람, 뒤와 같은 사람)`으로 구한다)에 따라 묶음 안쪽 모서리를 작게 깎고, 마지막 말풍선에만 꼬리를 단다. 꼬리는 RTL에서 반대로 붙는다. 폭은 받은 폭의 78%(`maxWidthFraction`)를 넘지 않아 글자를 키워도 비율이 그대로다. `time`, `status`(보내는 중, 보냄, 전달됨, 읽음, 실패)는 내용 아래에 작게 붙고 TalkBack은 상태로 읽는다 |
| `KTypingIndicator` | 받은 말풍선 모양 안에서 점 세 개가 차례로 튄다. 나타나면 TalkBack이 '입력 중'을 알린다 |
| `KChatInput` | 첨부 버튼, 최대 `maxLines`줄까지 높아지는 입력란, 보내기 버튼. 글자가 없으면 마이크 버튼(`onVoice`)이고 글자를 치면 강조색 원이 차오르며 위 화살표로 바뀐다. 하드웨어 키보드에서는 Enter로 보내고 Shift+Enter로 줄을 바꾼다 |
| `KStoryRing` | 아바타 둘레 스토리 링. 안 본 스토리는 그라디언트, 본 스토리(`seen`)는 가는 회색 링이며, `loading`이면 점선으로 나뉘어 돈다 |
| `KStoryViewer` | 전체 화면 스토리 뷰어. 분할 진행 막대, 시작 쪽 3분의 1을 누르면 이전, 나머지는 다음(RTL은 반대), 길게 누르면 멈춤, 아래로 끌거나 예측형 뒤로 가기로 닫기. 상태는 `rememberKStoryViewerState(pageCount)` |
| `KAttachmentChip` | 첨부 파일 한 개. 올리는 중이면 바탕이 진행률만큼 차고 끝 취소 버튼 둘레에 진행 링이 돈다. 실패하면 다시 시도 버튼으로 바뀐다. 긴 이름은 가운데를 줄여 확장자를 남긴다 |
| `KFileDropZone` | 점선 테두리의 파일 영역. 누르면 `onClick`(파일 선택기), `onDrop`을 주면 분할 화면이나 데스크톱 모드에서 다른 앱 파일을 끌어 놓을 수 있다. 끌어 온 파일이 위에 있으면 테두리가 강조색 실선이 된다 |
| `KIndexBar` | 연락처 색인 막대. 끌면 손가락 아래 글자마다 햅틱과 함께 `onSelect`가 불리고 시작 쪽에 큰 글자 말풍선이 따라온다. 자리가 모자라면 글자를 건너뛰고 점을 찍는다. `KIndexBarDefaults.Hangul`, `Latin`, `HangulAndLatin`, `keyOf("김민지")`(초성) |
| `KPagination` | `KPaginationStyle.Numbers`(칸 수가 늘 같아 폭이 바뀌지 않음)와 `Compact`(`5 / 20`). 폭이 모자라면 양옆 번호 수를 줄인다 |
| `KBreadcrumb` | 이동 경로. 마지막은 지금 위치라 누를 수 없고, 길면 사이를 '…'으로 접었다가 누르면 펼친다 |
| `KMapPin` | 지도 핀. `label`이면 가격 태그, `count`가 2 이상이면 묶음 핀, 그 밖은 물방울 핀. 처음 나타날 때 떨어지며 튀고 고르면 커진다. 아래 가운데가 지도 좌표다 |

화면 전환과 모션 도우미는 다음과 같다. 전환은 모두 예측형 뒤로 가기 제스처를 따라간다.

| API | 쓰임 |
| --- | --- |
| `KFullScreenModal` | 아래에서 올라와 화면을 덮는 모달. 뒤 화면은 살짝 물러난다. 아래로 끌거나(안쪽 목록이 맨 위면 이어서) 던지면 닫히고, 덜 끌면 제자리로 정착한다 |
| `KCircularReveal`, `Modifier.circularReveal` | `origin`(화면 좌표, `Modifier.kOrigin`으로 얻음)에서 원이 퍼지며 드러난다. `onBack`을 주면 뒤로 가기 제스처 동안 원이 줄어든다 |
| `KSharedTransitionHost`, `Modifier.kSharedElement`, `kSharedBounds` | Compose `SharedTransitionLayout`을 감싼 공유 요소 전환. `onBack`과 `backTarget`을 주면 뒤로 가기 제스처 동안 전환을 되감는다 |
| `Modifier.staggerIn(index, state)` | 항목이 순서대로 떠오르며 등장한다. `rememberKStaggerState()`를 넘기면 지연 목록에서 다시 들어온 항목은 등장하지 않는다 |
| `Modifier.scrollTransform(listState, index, effect)` | 지연 목록 가장자리 항목에 `KScrollEffect.ScaleFade`, `Wheel`, `TopStack` 효과를 준다 |
| `KFlyToHost`, `Modifier.flyToSource`, `flyToTarget` | `rememberKFlyToState().launch(key) { 썸네일 }`로 상품이 포물선을 그리며 장바구니로 날아가고, 도착하면 장바구니가 튄다 |
| `Modifier.holdToConfirm` | 누르고 있는 동안 테두리를 따라 선이 그려지고 다 차야 실행한다. TalkBack에서는 두 번 탭으로 바로 실행한다 |
| `KCopyButton` | 클립보드에 복사하고 아이콘이 체크로 바뀐다. TalkBack이 '복사됨'을 알린다 |
| `Modifier.floatUp`, `rememberKFloatUpState().emit("+1")` | 요소 위로 글자가 톡 튀어나와 떠오른다 |
| `MorphIcon(from, to, atEnd)` | 선 아이콘 사이 모핑. `KMorphIcons`의 다섯 쌍(검색↔닫기, 더하기↔체크, 메뉴↔뒤로, 하트↔채운 하트, 소리 작게↔크게)과 직접 만든 `KMorphGlyph`를 쓴다. 진행을 직접 정하는 오버로드도 있다 |
| `AuroraBackground`, `StarfieldBackground` | 오로라 띠와 반짝이는 별(가끔 별똥별) 배경 |
| `rememberImagePalette`, `imagePaletteOf`, `PaletteBackground` | 이미지에서 대표색(`dominant`, `vibrant`, `muted`, `dark`)을 뽑고, 그 색으로 물든 배경을 그린다 |

```kotlin
KChatBubble(msg.text, KChatSide.Sent, position = chatGroupPosition(prevSame, nextSame), time = "오후 2:14", status = KChatStatus.Read)
KChatInput(draft, { draft = it }, onSend = { send(draft); draft = "" }, onAttach = ::pick, onVoice = ::record)
KStoryRing(seen = story.seen, onClick = { open = true }) { KAvatar(KAvatar(story.name), size = KAvatarSize.Large) }
KFullScreenModal(composing, { composing = false }, title = "새 게시물") { ComposeScreen() }
KFlyToHost(fly) { /* … */ KButton("담기", { fly.launch(product.id, onArrive = { count++ }) { Thumb(product) } }) }
```

- 배경 효과와 반복 애니메이션(입력 중 점, 로딩 링, 스토리 링 회전)은 화면 밖으로 스크롤되면 멈춘다. 배경 효과는 앱이 백그라운드로 가도(Lifecycle STARTED 미만) 멈춘다.
- '애니메이션 삭제' 설정에서는 모달, 스토리 뷰어, 원형 드러내기, 공유 요소 전환이 바로 바뀌고, 핀은 떨어지지 않으며, 차례 등장과 +1 떠오름, 날아가기는 생략한다. 스토리 자동 넘김은 그대로이며 '조치를 취할 시간' 접근성 설정만큼 늘어난다.
- 구글 지도처럼 SurfaceView로 그리는 지도 위에서는 글래스 표면이 지도를 블러로 비추지 못하므로 `KMapPin`에 단색 표면을 쓴다.

`LazyColumn` 300행에 말풍선, 입력 중 점, 스토리 링, 첨부 칩, 차례 등장, 스크롤 변형을 함께 놓아도 시간이 흐르는 동안 행을 다시 구성하지 않는 것을 `KMotionExtrasTest`가, 소셜 컴포넌트의 동작과 접근성은 `KSocialTest`가 확인한다.
쇼케이스 **컴포넌트** 화면의 **소셜·모션** 탭에서 채팅, 스토리, 파일 첨부, 연락처 색인, 지도, 화면 전환, 마이크로 인터랙션, 배경 효과 장면을 볼 수 있다.
