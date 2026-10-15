# 35 — 글래스 UI와 고급 효과 (`kinetica-ui`)

배경을 블러 처리해 비쳐 보이는 글래스 UI와 고급 애니메이션을 골라 붙여 쓰는 모듈이다. 앱에서 직접 만들기 까다로운 것들을 모았다. Compose 전용이며 다른 Kinetica 모듈 없이 쓸 수 있다.

```kotlin
implementation("com.naurylab:kinetica-ui:1.0.0-alpha01")
```

## 화면 구조

```kotlin
GlassScaffold(
    style = GlassStyles.Liquid,                       // 이 화면에서 쓸 글래스의 기본 스타일
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

`GlassStyle`의 값은 `blur`(블러), `saturation`(채도 배율), `brightness`(밝기), `tint`(덮는 색), `edgeLight`와 `edgeWidth`(글로우 테두리), `shadow`(바깥쪽에만 지는 그림자),
`grain`(노이즈), `refraction`(가장자리 굴절, 0..1), `fallback`(블러를 쓸 수 없을 때의 색), `onGlass`(글자와 아이콘 색)다. 바꾸려면 `copy`를 쓴다.
블러는 Android 12(API 31)부터, 굴절은 Android 13(API 33)부터 동작한다. 그보다 낮은 버전에서는 `fallback` 색의 반투명 표면으로 그린다.
다이얼로그와 바텀 시트는 `style.forPopup()`으로 블러를 더 강하게, 색을 더 진하게 덮어서 위에 놓인 글자가 잘 읽히게 한다.

## 다이얼로그, 바텀 시트, 토스트, 탭 바

| 컴포넌트 | 동작 |
| --- | --- |
| `GlassDialog(visible, onDismissRequest, origin = …)` | 누른 버튼(`Modifier.glassOrigin { origin = it }`)에서 패널이 튀어나오듯 커지고, 배경은 서서히 흐려지며 어두워진다. 바깥을 누르거나 뒤로 가기로 닫는다 |
| `GlassSheet(visible, onDismissRequest)` | iOS 시트처럼 아래에서 올라오고, 뒤쪽 콘텐츠는 조금 물러나며 모서리가 둥글어진다. 아래로 끌어서 닫는다 |
| `GlassToastHost(state)` + `state.show("저장했어요")` | 위에서 미끄러져 내려오는 토스트. TalkBack이 내용을 읽어 준다 |
| `GlassTabBar(tabs, selectedIndex, onSelect)` | 화면 위에 떠 있는 탭 바. 선택한 탭의 인디케이터가 늘어났다 줄어들며 액체처럼 이동한다 |
| `GlassSurface(shape, style)` | 글래스 표면 하나. 안쪽 글자 색은 `LocalGlassContentColor`로 받는다 |
| `Modifier.glassContextMenu(items)` | 길게 누르면 햅틱과 함께 요소가 떠오르고 배경이 흐려지며, 요소 위나 아래에 글래스 메뉴(`GlassMenuItem`)가 뜬다. `destructive` 항목은 빨간 글자로 보인다. TalkBack에서는 사용자 정의 동작으로 제공한다 |
| `GlassSegmentedControl(options, selectedIndex, onSelect)` | 선택한 칸의 인디케이터가 늘어났다 줄어들며 이동하는 세그먼트 버튼 |
| `GlassSwitch(checked, onCheckedChange)` | 누르는 동안 손잡이가 늘어나고, 출렁이며 반대쪽으로 넘어가는 iOS 26 스타일 스위치 |
| `GlassSlider(value, onValueChange, valueRange, steps)` | 잡으면 트랙이 굵어지고 손잡이가 투명하게 커진다. 끝을 넘어 끌면 고무줄처럼 늘어났다가 튕겨 돌아온다. TalkBack으로도 값을 조절할 수 있다 |
| `GlassRefreshBox(refreshing, onRefresh) { LazyColumn { … } }` | 맨 위에서 당기면 콘텐츠가 저항감 있게 내려오고 글래스 물방울이 차오른다. 기준 거리를 넘으면 햅틱과 함께 물방울이 길쭉해지고, 놓으면 새로 고친다. TalkBack에서는 "새로 고침" 동작으로 제공한다 |

## 화면 이동

| 컴포넌트 | 동작 |
| --- | --- |
| `GlassExpandHost(state) { key -> 상세 }` + `Modifier.expandSource(state, key)` + `state.expand(key)` | App Store의 투데이 탭처럼 카드가 제자리에서 화면 가득 커지며 상세 화면이 된다. 배경은 흐려지고, 상세 화면을 아래로 끌면 작아지면서 모서리가 둥글어지며, 놓으면 원래 카드 위치로 돌아간다. 상세 화면 위쪽을 카드와 똑같이 꾸미면 끊김 없이 이어진다 |
| `PushNavigation(stack, onBack) { screen -> … }` | iOS식 push 전환. 새 화면이 옆에서 들어오고 아래 화면은 조금 물러나며 어두워진다. 왼쪽 가장자리에서 끌거나 예측 뒤로 가기 제스처를 하면 손가락을 따라 되돌아간다. 아래 화면의 `rememberSaveable` 상태(스크롤 위치 등)는 유지된다. RTL 언어에서는 방향이 반대가 된다 |

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

시스템의 '애니메이션 삭제' 설정이 켜져 있으면 반복, 기울기, 색종이 효과는 모두 멈춘다. 리플, 터지기, 두근거림 같은 누름·등장·반복 효과는 [docs/33](33-effects.md)에서 다룬다.

## 성능

GPU 가속 에뮬레이터(Apple M5 호스트)에서 글래스 20개 남짓과 움직이는 배경이 있는 쇼케이스 화면을 재 보면 프레임 p50이 17 ms(60fps)이고 끊김은 1% 미만이다.
글래스 하나마다 블러 층을 하나 더 그리므로, 작은 버튼마다 글래스를 쓰기보다 큰 카드나 바에 쓰는 편이 낫다. 소프트웨어 렌더러(SwiftShader 에뮬레이터)에서는 매우 느리다.

## 확인

`sample-showcase`의 **글래스 UI** 화면에서 스타일을 바꿔 보고 다이얼로그, 바텀 시트, 토스트, 탭 바, 컨트롤, 슬라이더, 당겨서 새로 고침을 확인할 수 있다.
**화면 이동**에서는 카드가 커지는 상세 화면과 push 전환, 가장자리에서 끌어 뒤로 가기를, **모션 효과**에서는 고급 효과를 확인한다.
