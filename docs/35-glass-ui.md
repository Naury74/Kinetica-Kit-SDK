# 35 — 글래스 UI와 고급 효과 (`kinetica-ui`)

다른 앱에서 직접 만들기 어려운 "뒤를 흐리게 비추는 유리" UI와 고급 움직임을 옵션처럼 붙여 쓰는 모듈이다. Compose만 쓰며 다른 Kinetica 모듈이 필요 없다.

```kotlin
implementation("com.naurylab:kinetica-ui:0.1.0-SNAPSHOT")
```

## 화면 뼈대

```kotlin
GlassScaffold(
    style = GlassStyles.Liquid,                       // 이 화면 유리의 기본 모양
    background = { Image(painterResource(R.drawable.photo), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) },
    topBar = { GlassSurface(shape = CircleShape) { Text("제목") } },
    bottomBar = { GlassTabBar(tabs, selected, onSelect = { selected = it }) },
) { padding ->
    LazyColumn(contentPadding = padding) { items(...) { GlassSurface { ... } } }
}
```

- `GlassScaffold`는 화면을 세 겹으로 기록한다: 바탕 ⊂ 바탕+내용 ⊂ 화면 전체. 내용 안의 유리는 바탕을, 위·아래 막대는 바탕+내용을,
  팝업·시트·알림은 화면 전체를 흐리게 비춘다. 그래서 유리를 어디에 두어도 자기 자신을 비추지 않는다.
- 내용은 막대 아래까지 그려지고(막대 뒤로 스크롤되어 비침) 막대 높이는 `padding`으로 받는다. 상태 표시줄 아래로 들어간 내용은
  흐려지며 사라진다(`edgeFade`).
- 사진 위 유리처럼 특정 그림을 비추려면 그 그림에 `Modifier.glassBackdrop(rememberGlassBackdrop())`를 붙이고 유리에 그 뒤 화면을 넘긴다
  (`GlassSurface(backdrop = photo)`).

## 유리 모양 `GlassStyle`

| 미리 만든 모양 | 느낌 |
| --- | --- |
| `GlassStyles.Liquid` / `LiquidDark` | iOS 26 Liquid Glass: 맑고 선명, 가장자리 렌즈 굴절(API 33+), 또렷한 빛 테두리 |
| `GlassStyles.Acrylic` / `AcrylicDark` | Windows 11 Acrylic: 많이 흐리고 옅은 색, 고운 알갱이 |
| `GlassStyles.OneUi` / `OneUiDark` | One UI: 부드럽게 흐리고 밝은 바탕, 테두리 없음 |
| `GlassStyles.Vision` | visionOS: 어둡고 깊은 유리, 은은한 빛 테두리 |
| `GlassStyles.Material` / `MaterialDark` | Material 3 Expressive: 은은한 색조 표면, 테두리 없이 큰 그림자 |
| `GlassStyles.Frosted` | 예전 iOS 서리 유리: 아주 많이 흐리고 하얗게 덮음 |
| `GlassStyles.Clear` | 흐림만(팝업 뒤 배경용) |

값: `blur`(흐림), `saturation`(채도 배율), `brightness`, `tint`(덮는 색), `edgeLight`·`edgeWidth`(빛 테두리), `shadow`(바깥에만 지는 그림자),
`grain`(알갱이), `refraction`(가장자리 굴절 0..1), `fallback`(흐림을 못 쓸 때 색), `onGlass`(글자·아이콘 색). `copy`로 고쳐 쓴다.
흐림은 Android 12(API 31)부터, 굴절은 13(API 33)부터이며 그보다 낮으면 `fallback` 색의 반투명 표면이다.
팝업·시트는 `style.forPopup()`으로 더 흐리고 진하게 덮어 판 위 글자가 잘 읽힌다.

## 팝업·시트·알림·탭 막대

| 구성 요소 | 하는 일 |
| --- | --- |
| `GlassDialog(visible, onDismissRequest, origin = …)` | 누른 버튼(`Modifier.glassOrigin { origin = it }`)에서 판이 튀어나오듯 커지고, 뒤가 서서히 흐려지고 어두워진다. 바깥·뒤로 가기로 닫는다 |
| `GlassSheet(visible, onDismissRequest)` | 아래에서 올라오며 뒤 내용이 물러나고 둥글어진다(iOS 시트). 끌어서 닫는다 |
| `GlassToastHost(state)` + `state.show("저장했어요")` | 위에서 미끄러져 내려오는 알림. 화면 읽기가 읽는다 |
| `GlassTabBar(tabs, selectedIndex, onSelect)` | 떠 있는 탭 막대. 고른 탭 알약이 늘어났다 줄며 옮겨 간다(액체처럼) |
| `GlassSurface(shape, style)` | 유리 표면 하나. 안의 글자 색은 `LocalGlassContentColor` |
| `Modifier.glassContextMenu(items)` | 길게 누르면(진동) 요소가 떠오르고 뒤가 흐려지며 그 아래·위에 유리 메뉴(`GlassMenuItem`, `destructive`면 빨간 글자). 화면 읽기에서는 사용자 정의 동작 |
| `GlassSegmentedControl(options, selectedIndex, onSelect)` | 고른 칸 알약이 늘어났다 줄며 옮겨 가는 세그먼트 버튼 |
| `GlassSwitch(checked, onCheckedChange)` | 누르는 동안 손잡이가 늘어나고 출렁이며 넘어가는 스위치(iOS 26 느낌) |

## 고급 효과

| 효과 | 쓰는 법 |
| --- | --- |
| 빛 테두리(Siri·Apple Intelligence) | `Modifier.glowBorder(shape, colors, width, glow)`. 화면 전체 `Modifier`에 붙이면 "듣는 중" 가장자리 |
| 젤리 누름 | `Modifier.jellyPress()` — 납작해졌다 출렁이며 돌아온다. 클릭은 그대로 |
| 자석 | `Modifier.magnetic(strength)` — 끌면 손가락을 따라왔다 튕기며 제자리 |
| 기울이면 3D | `Modifier.motionTilt(maxDegrees, shine)` — 기기를 기울이면 따라 기울고 빛이 흐른다(중력 센서) |
| 숫자 굴리기 | `RollingText("1,284,000원")` — 바뀐 자리만 위아래로 굴러간다 |
| 타자기 | `TypewriterText("…")` — 한 글자씩, 깜박이는 커서 |
| 색종이 | `ConfettiHost(state)` + `state.burst(origin)` |

모두 시스템 "애니메이션 삭제"가 켜져 있으면 반복·기울기·색종이를 멈춘다. 누름·나타남·반복 효과(리플·터지기·두근 등)는 [docs/33](33-effects.md).

## 성능

GPU 가속 에뮬레이터(Apple M5 호스트)에서 유리 20개 남짓과 움직이는 바탕이 있는 쇼케이스 화면이 프레임 p50 17 ms(60fps), 끊김 1% 미만이다.
유리 하나는 흐린 층 하나를 더 그리므로, 작은 버튼마다 유리를 쓰기보다 큰 카드·막대에 쓰는 편이 낫다. 소프트웨어 렌더러(SwiftShader 에뮬레이터)에서는 매우 느리다.

## 확인

`sample-showcase` → **글래스 UI**(스타일 바꾸기, 팝업·시트·알림·탭 막대), **모션 효과**.
