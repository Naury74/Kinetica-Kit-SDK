# 35 — UI 키트 (`kinetica-ui`)

앱 화면에 바로 쓰는 Compose 컴포넌트와 모션 효과 모음이다. 버튼과 입력란부터 다이얼로그, 바텀 시트, 토스트, 차트, 채팅, 사진 뷰어까지 들어 있다.
모든 컴포넌트는 표면 스타일(`SurfaceStyle`)을 따른다. 배경이 비치는 글래스, 블러 없는 단색, 강조색을 옅게 섞은 톤 가운데 하나를 앱 전체에 정하고, 필요하면 컴포넌트마다 바꾼다.
다른 Kinetica 모듈 없이 이 모듈 하나만 써도 된다.

처음이라면 [36 빠른 시작](36-quick-start.md)을 먼저 보고, 여기서는 필요한 컴포넌트를 찾아 쓰면 된다.

**목차**

- [시작하기](#시작하기)
- [테마와 표면 스타일](#테마와-표면-스타일)
- [변형과 톤](#변형과-톤)
- [디자인 언어와 형태](#디자인-언어와-형태)
- [터치 피드백](#터치-피드백)
- [버튼, 입력, 선택](#버튼-입력-선택)
- [목록과 카드](#목록과-카드)
- [내비게이션과 앱 바](#내비게이션과-앱-바)
- [오버레이](#오버레이)
- [피드백과 로딩](#피드백과-로딩)
- [데이터 표시](#데이터-표시)
- [미디어와 소셜](#미디어와-소셜)
- [효과와 모션](#효과와-모션)
- [넓은 화면과 폴더블](#넓은-화면과-폴더블)
- [호스트 앱에서 쓸 때](#호스트-앱에서-쓸-때)
- [성능](#성능)
- [쇼케이스에서 확인하기](#쇼케이스에서-확인하기)

## 시작하기

```kotlin
implementation("com.naurylab:kinetica-ui:1.0.0-alpha01")
```

Compose foundation 위에 만들었고 Material 3에는 의존하지 않는다. Material 3 앱에 함께 넣어도 된다. 폴더블 정보를 읽으려고 `androidx.window`를 함께 가져온다.

이름은 다음 규칙을 따른다.

- 테마와 화면 골격: `KineticaTheme`, `KineticaScaffold`, `KineticaColors`, `LocalKinetica…`
- 컴포넌트: `K` 접두사(`KButton`, `KSheet`, `KDialog`, `KTabBar`, `KTextField`, `KSurface` 등). 상태와 기본값도 같은 규칙이다(`rememberKToastState`, `KSheetDefaults`).
- 수정자: `k`로 시작한다(`Modifier.kClickable`, `kBadge`, `kSurface`, `kContextMenu`).
- 블러 효과 자체를 가리키는 `Modifier.glass`, `GlassStyle`, `GlassStyles`, `GlassBackdrop`, `glassBackdrop`만 글래스라는 이름을 쓴다.

알파 초기의 `Glass*` 이름에서 옮기는 방법은 [CHANGELOG의 이름 대응표](../CHANGELOG.md)에 있다.

### 화면 골격 `KineticaScaffold`

```kotlin
var selected by rememberSaveable { mutableIntStateOf(0) }
val listState = rememberLazyListState()
KineticaScaffold(
    background = { Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF8EC5FC), Color(0xFFE0C3FC))))) },
    topBar = { KTopAppBar("사진", state = rememberKTopAppBarState(listState)) },
    bottomBar = { KTabBar(listOf(KTab("홈", Icons.Rounded.Home), KTab("검색", Icons.Rounded.Search)), selected, onSelect = { selected = it }) },
) { padding ->
    LazyColumn(state = listState, contentPadding = padding) {
        items(30) { i -> KCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { Text("사진 $i") } }
    }
}
```

- 콘텐츠는 바 아래까지 그려지므로 스크롤하면 바 뒤로 지나가는 모습이 보인다. 바 높이만큼의 여백은 `padding`으로 받는다.
- 상태 표시줄 아래로 들어간 콘텐츠는 `edgeFade`로 흐려지며 사라진다. 단색과 톤 표면에서는 바탕색으로 덮는다.
- 글래스 표면이면 화면을 배경, 배경과 콘텐츠, 화면 전체의 세 층으로 나눠 기록한다. 콘텐츠 안의 글래스는 배경을, 상단 바와 하단 바는 배경과 콘텐츠를,
  다이얼로그와 바텀 시트, 토스트는 화면 전체를 블러 처리해 비춘다. 그래서 글래스를 어디에 두어도 자기 자신을 비추지 않는다.
- 단색이나 톤 표면이면 배경을 기록하지 않고 기록용 레이어도 만들지 않는다. 블러 비용이 없고, 표면의 `background` 색을 바탕에 칠한다.
- 다이얼로그, 시트, 토스트는 `KineticaScaffold` 밖에서 불러도 동작한다. 차이는 [호스트 앱에서 쓸 때](#호스트-앱에서-쓸-때)에 있다.

## 테마와 표면 스타일

### `KineticaTheme`

표면 스타일, 강조색, 글꼴, 터치 피드백을 한 번에 정한다. 화면 루트에 한 번 감싸면 그 안의 `KineticaScaffold`와 모든 컴포넌트가 이 값을 기본값으로 쓴다.
컴포넌트에 직접 넘긴 값이 있으면 그 값이 우선한다.

```kotlin
MaterialTheme(colorScheme = scheme) {
    KineticaTheme(
        darkTheme = dark,
        surface = if (dark) SurfaceStyles.CleanDark else SurfaceStyles.CleanLight,
        colors = KineticaColors(
            accent = MaterialTheme.colorScheme.primary,
            onAccent = MaterialTheme.colorScheme.onPrimary,
            destructive = MaterialTheme.colorScheme.error,
        ),
        textStyle = MaterialTheme.typography.bodyLarge,
    ) { App() }
}
```

| 파라미터 | 뜻 |
| --- | --- |
| `darkTheme` | 기본값은 시스템 다크 모드. 앱이 다크 모드를 직접 정하면 그 값을 넘긴다 |
| `surface` | 기본 표면 스타일. 주지 않으면 `darkTheme`에 따라 `SurfaceStyles.Liquid`나 `LiquidDark`를 쓴다 |
| `colors` | `KineticaColors(accent, onAccent, positive, destructive, onPositive, onDestructive)` |
| `textStyle` | 글꼴 관련 값(fontFamily 등)만 가져오고, 크기와 굵기, 줄 높이는 컴포넌트가 정한다 |
| `indication` | 안쪽 `LocalIndication`. 기본값은 `KIndication()`이고, `null`이면 호스트 값을 그대로 둔다 |

`KineticaColors`의 `accent`는 버튼, 칩, 슬라이더, 진행 표시, 입력란 포커스, 달력 선택에 쓰인다. `positive`는 스위치와 밀어서 확인하기에,
`destructive`는 배지, 삭제 메뉴, 입력 오류에 쓰인다. `onPositive`와 `onDestructive`는 그 색으로 채운 버튼 위의 글자 색이다.

직접 만든 컴포넌트를 키트와 맞추려면 `KineticaTheme.surface`, `KineticaTheme.colors`, `KineticaTheme.textStyle`을 읽는다.
`Modifier.glass`의 기본 스타일은 `KineticaTheme.glassStyle`이고, 표면이 글래스가 아니면 표면 밝기에 맞춘 Liquid 스타일이다.

### 표면 스타일 `SurfaceStyle`

| 종류 | 그리는 방법 | 프리셋 |
| --- | --- | --- |
| `SurfaceStyle.Glass(style)` | 배경을 블러 처리해 비춘다. 블러 값은 `GlassStyle`이 정한다 | `SurfaceStyles.Liquid`, `LiquidDark`, `Acrylic`, `AcrylicDark`, `OneUiGlass`, `OneUiGlassDark`, `Vision`, `Frosted`, `Mica`, `MicaDark`, `MaterialGlass`, `MaterialGlassDark`, `HarmonyOs`, `Obsidian`, `Pastel` |
| `SurfaceStyle.Solid(color, contentColor, background, border, borderWidth, elevation)` | 불투명한 단색 표면. 테두리와 그림자를 줄 수 있다 | `SurfaceStyles.CleanLight`, `CleanDark`, `OneUi`, `OneUiDark`, `Material`, `MaterialDark` |
| `SurfaceStyle.Tonal(accent, dark, elevation)` | 강조색을 옅게 섞은 표면. 톤 표면끼리 겹쳐도 경계가 보이도록 옅은 강조색 테두리를 두른다 | `SurfaceStyles.Tonal`, `TonalDark`, `SurfaceStyles.tonal(색, dark)` |

```kotlin
// 컴포넌트 하나만 다른 표면으로
KSheet(open, onDismissRequest = { open = false }, surface = SurfaceStyles.tonal(Color(0xFF2E7D32))) { Text("배송 정보") }

// 직접 만든 요소도 같은 표면으로
Box(Modifier.kSurface(shape = RoundedCornerShape(20.dp)).padding(16.dp)) { Text("직접 만든 카드") }

// 프리셋에 없는 단색 표면
val paper = SurfaceStyle.Solid(
    color = Color.White, contentColor = Color(0xFF1B1E26), background = Color(0xFFF3F4F8),
    border = Color(0x14000000), borderWidth = 1.dp, elevation = 4.dp,
)
```

- 모든 컴포넌트는 `surface: SurfaceStyle = KineticaTheme.surface`를 받고, 표면은 `Modifier.kSurface(surface, shape)` 하나로 그린다. `kSurface`는 콘텐츠를 자르지 않으므로 터치 피드백까지 모양 안에 가두려면 `clip(shape)`을 이어 붙인다.
- 글자와 아이콘 색(`contentColor`, 컴포넌트 안에서는 `LocalKineticaContentColor`), 터치 피드백 색, 시스템 바 아이콘 밝기(`isDark`)는 표면 스타일에서 정해진다.
- `KineticaScaffold`는 단색과 톤 표면일 때 시스템 바 아이콘을 표면 밝기에 맞춘다(`systemBarIcons`). 글래스는 호스트 배경이 비치므로 아이콘을 호스트에 맡긴다.
- 다이얼로그와 바텀 시트는 `surface.forPopup()`을 쓴다. 글래스는 블러를 더 강하게, 색을 더 진하게 덮고, 단색과 톤은 그림자를 키운다. 어두운 단색은 판을 조금 밝혀 바탕과 구분한다.
- 다이얼로그나 시트 안에 놓인 표면(입력란, 칩)은 판과 같은 색으로 묻히지 않도록 판 위에 얹힌 옅은 한 겹으로 그린다.

### 특정 이미지 위에 글래스 올리기

글래스는 기본으로 `KineticaScaffold`가 기록한 배경을 비춘다. 사진 한 장처럼 특정 요소를 비추려면 그 요소를 `glassBackdrop`으로 기록하고 글래스에 넘긴다.

```kotlin
val photo = rememberGlassBackdrop()
Box(Modifier.fillMaxWidth().height(240.dp)) {
    Image(painter, null, Modifier.fillMaxSize().glassBackdrop(photo), contentScale = ContentScale.Crop)
    KSurface(Modifier.align(Alignment.BottomCenter).padding(16.dp), surface = SurfaceStyles.Liquid, backdrop = photo) {
        Text("사진 위 글래스", Modifier.padding(16.dp))
    }
}
```

`GlassStyle`의 값은 `blur`, `saturation`, `brightness`, `tint`, `edgeLight`, `edgeWidth`, `shadow`, `grain`, `refraction`(API 33 이상에서 가장자리 굴절), `fallback`(블러를 쓸 수 없을 때 칠할 색), `onGlass`(글자 색)다.
`GlassStyles`에는 위 표의 글래스 프리셋과 함께, 색과 테두리 없이 블러만 주는 `Clear`가 있다(다이얼로그 뒤 배경을 흐릴 때 쓴다). 블러는 Android 12(API 31) 이상에서만 그려지고, 그 아래에서는 `fallback` 색을 칠한다.

`KineticaScaffold` 콘텐츠 안의 글래스는 배경 블러를 한 번만 하고 함께 쓴다. 굴절도 화면에 있는 글래스 32개까지는 셰이더 하나가 한 번에 걸고, 그보다 많으면 넘친 글래스만 따로 건다. 그래서 움직이는 배경 위에 카드와 버튼이 많아도 배경이 바뀌는 프레임의 GPU 비용은 글래스 수와 거의 상관없다. 상단 바, 탭 바, 사진마다 따로 둔 `GlassBackdrop` 위 글래스는 비추는 대상이 달라 각자 블러를 건다.

### 치수 기준

| 항목 | 기준 |
| --- | --- |
| 터치 영역 | 44dp 이상, 목록 행과 탭은 48dp |
| 아이콘 버튼 | 지름 44dp, 아이콘 22dp |
| 글자 버튼 안 아이콘 | 20dp(Small 16dp), 글자와 간격 8dp(Small 6dp). 아이콘 쪽 여백은 4dp 줄여 시각적으로 가운데에 맞춘다 |
| 탭 바와 레일 아이콘 | 22~24dp, 이름과 간격 2~4dp |
| 안쪽 모서리 | 바깥 반지름에서 안쪽 여백을 뺀 값(최소 4dp). 예: 22dp 메뉴 안에 6dp 여백으로 놓인 항목은 16dp |

### 아이콘 `KIcons`

앱 화면에서 자주 쓰는 선 아이콘 63개를 `ImageVector`로 제공한다. 24 격자에 1.8 굵기 선으로 그렸고 기본 크기는 24dp다. 색은 쓰는 쪽의 글자 색이나 `tint`를 따르므로 `KIconButton`, `KButton`의 `icon`, `KListLeading.Icon`, `Icon`에 그대로 넘긴다. 방향이 있는 `ChevronBack`, `ChevronForward`, `ArrowBack`, `ArrowForward`, `Send`는 RTL에서 좌우가 뒤집힌다. 아이콘은 처음 읽을 때 한 번만 만든다. Material Icons를 함께 써도 된다.

| 묶음 | 아이콘 |
| --- | --- |
| 기본 동작 | `Home`, `Search`, `Settings`, `Plus`, `Minus`, `Close`, `Check`, `More`, `MoreVertical`, `Menu`, `Refresh`, `Filter` |
| 방향 | `ChevronBack`, `ChevronForward`, `ChevronUp`, `ChevronDown`, `ArrowBack`, `ArrowForward`, `ArrowUp`, `ArrowDown` |
| 편집과 공유 | `Share`, `Edit`, `Trash`, `Copy`, `Link`, `Download`, `Upload`, `Send`, `Bookmark`, `Heart`, `Star` |
| 상태 | `Eye`, `EyeOff`, `Lock`, `Bell`, `Info`, `Sun`, `Moon` |
| 소통 | `Mail`, `Message`, `Inbox`, `Archive`, `Call`, `CallEnd`, `Dialpad`, `Mic`, `MicOff`, `Video`, `VideoOff`, `Speaker` |
| 미디어와 일정 | `Camera`, `Image`, `File`, `Calendar`, `Clock`, `Location` |
| 사람과 쇼핑 | `Person`, `Users`, `Cart`, `Bag`, `Card`, `Grid`, `Chart` |

```kotlin
KIconButton(KIcons.Share, "공유", onClick = ::share)
KButton("장바구니에 담기", onClick = ::add, icon = KIcons.Cart)
```

이름과 아이콘 짝 목록은 `KIcons.all`에 있다. 아이콘 고르기 화면을 만들 때 쓴다.

## 변형과 톤

버튼, 아이콘 버튼, 칩, 카드, 누를 수 있는 `KSurface`는 모양(`variant: KVariant`)과 색 역할(`tone: KTone`)을 따로 고른다. 둘 다 표면 스타일과 독립이라 글래스 표면에서 `Outlined` 버튼을 쓰거나 단색 표면에서 `Glass` 카드를 써도 된다.

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
글자가 표면 위에 놓이는 `Tonal`, `Outlined`, `Text`에서는 색 역할의 색이 바탕과 WCAG 대비 4.5:1(글자 없는 아이콘 버튼과 FAB은 3:1)이 안 되면 기준을 막 넘는 만큼만 밝은 표면에서는 어둡게, 어두운 표면에서는 밝게 고쳐 쓴다. 흰 표면 위 기본 강조색 `#0A84FF`(3.6:1)는 조금 짙은 파랑이 되고, 대비가 충분한 브랜드 색은 그대로 쓴다.

```kotlin
KButton("결제하기", onClick = ::pay)                                              // Filled + Accent
KButton("삭제", onClick = ::delete, variant = KVariant.Outlined, tone = KTone.Destructive)
KIconButton(Icons.Rounded.Share, "공유", onClick = ::share)                        // 기본값 Glass
KChipGroup(listOf("전체", "사진", "동영상"), filter, { filter = it }, variant = KVariant.Tonal)
KSurface(onClick = ::open, variant = KVariant.Elevated) { Text("열기", Modifier.padding(16.dp)) }
KButton("저장", onClick = { scope.launch { toast.show("저장했어요", tone = KTone.Positive) } }) // 글자 앞에 성공 아이콘
```

## 디자인 언어와 형태

표면 스타일이 색과 블러, 그림자를 정한다면 디자인 언어(`KineticaDesign`)는 컴포넌트의 형태를 정한다. 모서리 모양과 크기, 표면을 띄워 보이는 방식,
테두리 두께, 높이와 여백, 글자 굵기, 탭 인디케이터, 스위치와 슬라이더 손잡이 모양, 모션 성격이 함께 바뀐다. 두 값은 따로 고르므로
글래스 표면에 브루탈 형태를 얹거나 단색 표면에 One UI 형태를 쓸 수 있다.

```kotlin
KineticaTheme(surface = SurfaceStyles.CleanLight, design = KineticaDesigns.Material) { App() }

// 한 부분만 다른 형태로
KineticaTheme(design = KineticaDesigns.Brutal) { PromoBanner() }

// 프리셋을 고쳐 쓰기
val design = KineticaDesigns.Cupertino.copy(
    shapes = KineticaShapes(corner = KCornerStyle.Squircle, button = KShapeToken.Medium),
    indicator = KTabIndicator.Underline,
)
```

기본값은 `KineticaDesigns.Cupertino`이고 지금까지의 모양과 같다. 컴포넌트의 `shape`, `variant`, `thumb`, `indicator` 같은 파라미터에 값을 넘기면 그 값이 우선한다.

### 프리셋

| 프리셋 | 모서리 | 깊이 | 밀도 | 탭 인디케이터 | 목록 | 컨트롤 | 모션 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `Cupertino` | 캡슐 버튼과 칩, 24dp 카드, 32dp 시트 | 부드러운 그림자 | 보통 | 옅은 캡슐 | 판 하나, 글자 시작부터 구분선 | 캡슐 스위치, 가로 캡슐 손잡이 | 통통 튐 |
| `Material` | 캡슐 버튼, 8dp 칩, 12dp 카드, 4dp 입력란, 28dp 다이얼로그 | 평면(떠 있는 판만 그림자) | 보통 | 강조색 채움 | 행마다 따로 놓인 판, 2dp 틈 | 체크 스위치, 세로 막대 손잡이 | 빠르고 살짝 튐 |
| `OneUi` | 캡슐 버튼, 26dp 카드와 목록 판, 캡슐 입력란, 원 체크박스 | 평면 | 넉넉함 | 옅은 캡슐 | 판 하나 | 얇은 스위치, 원 손잡이 | 차분함 |
| `Fluent` | 4dp 버튼과 칩, 8dp 카드와 다이얼로그 | 테두리 | 조밀함 | 밑줄 | 판 하나, 끝에서 끝까지 구분선 | 테두리 스위치, 링 손잡이 | 빠름 |
| `Brutal` | 각진 모서리(아바타와 라디오만 원) | 글자 색 테두리와 단단한 오프셋 그림자 | 보통 | 강조색 채움 | 판 하나, 끝에서 끝까지 구분선 | 각진 스위치, 정사각형 손잡이 | 빠름 |
| `Soft` | 스쿼클, 12dp 버튼, 8dp 칩, 32dp 카드, 40dp 시트 | 부드러운 그림자 | 넉넉함 | 밝은 칸이 미끄러지는 트랙 | 구분선 없는 판 | 캡슐 스위치, 원 손잡이 | 차분함 |

`KineticaDesigns.all`은 이름과 프리셋 목록이라 고르기 화면에 그대로 늘어놓을 수 있다.

### 모양 토큰 `KineticaShapes`

모서리 체계(`corner`), 크기 단계(`extraSmall`, `small`, `medium`, `large`, `extraLarge`, `huge`), 그리고 컴포넌트 역할마다 쓸 단계(`KShapeToken`)로 이루어진다.
역할은 `button`, `chip`, `card`, `sheet`, `dialog`, `field`, `search`, `menu`, `toast`, `tabBar`, `indicator`, `listSection`, `avatar`, `checkbox`, `radio`, `badge`, `image`가 있다.

| `KCornerStyle` | 모양 |
| --- | --- |
| `Round` | 원호 모서리 |
| `Squircle` | 직선에서 서서히 휘는 연속 곡률 모서리. `KSquircleShape`로 직접 쓸 수도 있다 |
| `Cut` | 비스듬히 잘라 낸 모서리 |
| `Square` | 각진 모서리. 반지름을 무시한다 |

`KShapeSize.Full`은 짧은 변의 절반이라 버튼과 칩은 캡슐, 정사각형은 원이 된다. 역할 하나만 다른 모서리 모양을 쓰려면 `KShapeToken(KShapeSize.Full, KCornerStyle.Round)`처럼 준다.
직접 만든 컴포넌트는 `KineticaTheme.shapes.shape(KineticaTheme.shapes.card)`로 같은 모양을 얻고, 안쪽 요소는 `shapes.inner(token, inset)`으로 동심원 반지름을 맞춘다.

### 깊이, 밀도, 글자, 모션

- `KDepth.Shadow`는 표면 스타일 그대로 그림자를 그린다. `Border`는 그림자 대신 `borderWidth` 두께의 옅은 테두리를 두르고, `Flat`은 색 차이로만 구분한다. 둘 다 다이얼로그와 시트처럼 떠 있는 판에는 그림자를 남긴다. 글래스는 팝업도 같은 그림자를 쓰므로 그림자를 없애지 않고 줄인다.
- `KDepth.Offset`은 글자 색 테두리와 오른쪽 아래(RTL이면 왼쪽 아래)로 비낀 단단한 그림자를 그린다. `Filled`와 `Tonal` 버튼도 같은 테두리와 그림자를 둘러 카드와 형태가 맞는다.
- `KDensity`는 버튼, 칩, 입력란, 세그먼트, 탭 바, 목록 행의 높이와 카드, 다이얼로그, 시트의 안쪽 여백을 바꾼다. `Compact`는 데스크톱 앱처럼 촘촘한 화면에 쓰고, 이때 버튼 높이가 44dp 밑으로 내려가므로 주변 터치 여백을 둔다.
- `labelWeight`는 버튼, 칩, 탭 이름의 굵기, `titleWeight`는 다이얼로그와 앱 바 제목의 굵기다. 제목은 컴포넌트가 정한 굵기를 기본값(Bold)과의 차이만큼 옮긴다.
- `KineticaMotion`은 인디케이터 이동, 스위치 손잡이, 다이얼로그 열림, 버튼 눌림 깊이에 쓰는 스프링이다. `Bouncy`면 컴포넌트마다 맞춰 둔 값을 그대로 쓴다. '애니메이션 삭제' 설정이면 어느 성격이든 바로 바뀐다.

### 컨트롤과 인디케이터

- `KineticaControls.switch`는 `KSwitch`와 스위치 행의 기본 모양이다. `KSwitchVariant`에 `Outline`(테두리 트랙)과 `Block`(각진 트랙과 손잡이)이 더해졌다. `KListTrailing.Switch`의 기본값 `Capsule`은 디자인 언어의 스위치를 따른다.
- `KineticaControls.sliderThumb`는 `KSlider`와 `KRangeSlider`의 손잡이 모양이다(`Pill`, `Circle`, `Bar`, `Ring`, `Square`). `Bar`는 손잡이 양옆을 비운 두꺼운 트랙을, `Square`는 테두리 두른 각진 트랙을 함께 그린다.
- 체크박스 상자는 `shapes.checkbox`, 라디오 테두리는 `shapes.radio`를 따른다. `KCheckboxShape.Square`는 '디자인 언어의 상자'라는 뜻이다.
- `indicator`는 `KTopTabs`, `KTabBar`, `KFloatingTabBar`, `KSegmentedControl`의 선택 표시다. `KTabIndicator.Filled`가 더해졌다. 밑줄과 채움 모양에서는 고른 탭 글자를 강조색(채움은 강조색 위 글자 색)으로 쓴다.
- `listSeparator`는 `KListSection`과 `kListSection` 행 사이를 나누는 방식이다(`Inset`, `Full`, `None`, `Gap`).

## 터치 피드백

키트의 누를 수 있는 요소는 사각 리플 대신 `KIndication`을 쓴다. 요소 모양으로 잘린 옅은 오버레이가 덮였다가 사라지고, 빠르게 탭해도 110ms는 보인다.
키보드 포커스와 마우스 호버도 옅게 표시한다. '애니메이션 삭제' 설정이 켜져 있으면 페이드 없이 바로 켜고 끈다.
오버레이 색을 주지 않으면 `LocalKineticaContentColor`를 보고 고른다. 밝은 표면처럼 글자가 어두우면 어두운 오버레이를, 어두운 표면이나 강조색 버튼처럼 글자가 밝으면 흰 오버레이를 쓴다.

`KineticaTheme`과 `KineticaScaffold`는 `LocalIndication`에 `KIndication()`을 넣는다. 그래서 그 안에서 호스트가 쓴 일반 `Modifier.clickable`도 회색 사각 리플 대신 이 표시가 나온다.
호스트 리플을 그대로 쓰려면 `KineticaTheme(indication = null)`을 넘긴다. 그 안의 `KineticaScaffold`도 테마를 따라 호스트 값을 둔다.

```kotlin
val shape = RoundedCornerShape(16.dp)
// 모양을 알려 주면 모서리 밖은 칠하지 않는다
Row(Modifier.clip(shape).kClickable(shape, onClickLabel = "열기") { open() }.padding(16.dp)) { Text("설정") }
Box(Modifier.size(48.dp).clickable(interactionSource = null, indication = KIndication(CircleShape)) { open() })

// 길게 누르기, 선택, 토글
Box(Modifier.kCombinedClickable(shape, onLongClick = ::showMenu) { open() })
Box(Modifier.kSelectable(selected = tab == 0, shape = CircleShape, role = Role.Tab) { tab = 0 })
Box(Modifier.kToggleable(checked, shape, role = Role.Checkbox) { checked = it })
```

- `KIndication(shape, color, pressedAlpha, pressedScale)`: `pressedAlpha` 기본값은 0.1이다. `pressedScale`은 수정자 체인에서 표시보다 뒤에 그려지는 내용만 줄이므로, 배경까지 함께 줄이려면 `Modifier.indication`을 배경보다 앞에 둔다.
- 카드처럼 통째로 눌리는 요소는 `KSurface(onClick = …, shape = …)`나 `KCard(onClick = …)`를 쓰면 모양에 맞는 터치 피드백이 붙는다.
- Material 3 컴포넌트와 함께 쓸 때 주의할 점은 [Material 3와 함께](#material-3와-함께)에 있다.

## 버튼, 입력, 선택

### 버튼

| 컴포넌트 | 하는 일 | 주요 파라미터 |
| --- | --- | --- |
| `KButton` | 누르면 젤리처럼 눌린다. `loading`이면 폭을 유지한 채 링이 돌고, `success`가 되면 원으로 줄며 체크 표시가 그려진다 | `variant`, `tone`, `size`(`Small`, `Medium`, `Large`), `icon`, `trailingIcon`, `loading`, `success`, `fullWidth`, `enabled` |
| `KIconButton` | 아이콘만 있는 버튼. 기본 지름 44dp 원 안에 22dp 아이콘을 그린다 | `contentDescription`, `variant`, `tone`, `size`(`KIconButtonSizes.Small` 36dp, `Medium` 44dp, `Large` 56dp), `shape` |
| `KIconToggleButton` | 켜질 때 색이 가운데에서 퍼지고 아이콘이 `checkedIcon`으로 바뀐다 | `checked`, `onCheckedChange`, `checkedIcon` |
| `KFab`, `KExtendedFab` | 떠 있는 주 동작 버튼. 확장형은 `expanded = rememberFabExpanded(listState)`를 넘기면 목록을 내릴 때 아이콘만 남게 접힌다 | `size`(`Small`, `Regular`, `Large`), `expanded` |
| `KSplitButton` | 주 동작과 꺾쇠 버튼을 붙인 분할 버튼. 꺾쇠를 누르면 `onMenuClick`이 불리고, `menuExpanded`면 꺾쇠 버튼이 원으로 바뀐다. 메뉴는 호출한 쪽이 띄운다 | `onMenuClick`, `menuExpanded` |
| `KButtonGroup`, `KToggleGroup` | 가로 버튼 묶음. 누르고 있는 칸이 넓어지고 양옆 칸이 비켜 주며, 묶음 전체 폭은 그대로다 | `style`(`KGroupStyle.Connected`, `Separated`), `selection`(`KGroupSelection.Single`, `Multiple`) |
| `KBlobButton` | 누른 자리가 오목하게 들어갔다가 출렁이며 돌아오는 액체 버튼 | `color`, `contentColor` |

```kotlin
KButton("장바구니에 담기", onClick = ::add, loading = adding, success = added, fullWidth = true, size = KButtonSize.Large)
KIconToggleButton(liked, { liked = it }, Icons.Rounded.Heart, "좋아요", checkedIcon = Icons.Rounded.HeartFilled)
KExtendedFab("새 글", Icons.Rounded.Add, onClick = ::newPost, expanded = rememberFabExpanded(listState))
KToggleGroup(listOf(KGroupItem("일"), KGroupItem("주"), KGroupItem("월")), setOf(period), { period = it.first() }, Modifier.fillMaxWidth())
```

'애니메이션 삭제' 설정에서는 성공 표시, 토글 채우기, 선택 카드 체크, 확장 FAB 접기가 바로 끝나고 버튼 묶음은 비켜 주지 않는다.

### 선택 컨트롤

| 컴포넌트 | 하는 일 | 주요 파라미터 |
| --- | --- | --- |
| `KSwitch` | `KSwitchVariant.Capsule`(기본, 누르는 동안 손잡이가 늘어나고 출렁이며 넘어감), `Check`(켜지면 손잡이가 커지며 체크가 그려짐), `Slim`(얇은 트랙에 테두리 두른 손잡이). RTL에서는 켜진 손잡이가 왼쪽에 간다 | `variant`, `accent`(주지 않으면 `Capsule`은 `positive`, 나머지는 `accent` 색), `onCheckedChange`가 `null`이면 표시만 |
| `KCheckbox`, `KTriStateCheckbox` | 켜지면 상자가 튀듯 차오르고 체크 표시가 획 순서대로 그려진다. 미정 상태는 가로 막대다 | `shape`(`KCheckboxShape.Square`, `Circle`), 콜백이 `null`이면 표시만 |
| `KRadio`, `KRadioGroup` | 선택되면 가운데 점이 살짝 튀며 커진다. 묶음은 행 전체를 누를 수 있고 TalkBack이 한 묶음으로 읽는다 | `options`, `selectedIndex` |
| `KSegmentedControl` | 선택한 칸의 인디케이터가 늘어났다 줄어들며 이동한다. `KSegment`로 아이콘 칸을 만들고, `selected: Set<Int>`를 받는 오버로드는 여러 칸을 함께 켠다(체크 표시가 밀려 들어옴) | `tone`(`Neutral`은 옅은 표면 글자 색, 나머지는 그 색으로 채움), `role`(기본 `Role.RadioButton`, 탭처럼 쓰면 `Role.Tab`), `maxLines`(기본 2), `sizing`(`KSegmentSizing.Proportional`이면 이름 길이에 비례), `requireSelection` |
| `KFilterChip`, `KInputChip`, `KAssistChip`, `KSuggestionChip` | 필터 칩은 켜면 체크가 그려지고, 입력 칩은 아바타와 지우기 버튼이 있다. 동작 칩은 앞 아이콘을 강조색으로, 제안 칩은 글자만 쓴다 | `variant`, `tone`, `leadingIcon` |
| `KChipRow`, `KChipGroup` | `KChipRow`는 칩을 한 줄에 놓고 가로로 스크롤한다. `KChipGroup`은 이름 목록으로 필터 칩 묶음을 만든다. 고르면 체크가 그려지며 폭이 부드럽게 늘어난다 | `selection`(`KChipSelection.Single`, `Multiple`), `wrap`(`false`면 한 줄), `variant`(`Glass`, `Tonal`, `Outlined`) |
| `KSlider` | 잡으면 트랙이 굵어지고 손잡이가 투명하게 커진다. 끝을 넘어 끌면 고무줄처럼 늘어났다가 튕겨 돌아온다. 가로로 움직여야 드래그로 보므로 세로 목록 스크롤을 막지 않는다. 방향키와 TalkBack으로도 조절한다 | `valueRange`, `steps`, `enabled`, `onValueChangeFinished`, `accent` |
| `KRangeSlider` | 손잡이 두 개짜리 범위 슬라이더. 잡으면 값 말풍선이 뜬다 | `valueRange`, `steps`, `valueLabel` |
| `KStepper` | −/+ 캡슐. 숫자가 굴러가며 바뀌고, 길게 누르면 점점 빨라진다. 끝에 닿으면 그쪽 버튼이 흐려진다 | `range`, `step` |
| `KQuantityStepper` | 쇼핑 수량. `KQuantityStyle.Outlined`(테두리 칸)와 `Collapsible`(0이면 + 하나만, 1에서 빼기가 휴지통) | `style`, `range` |
| `KRating` | 별점. `onRatingChange`가 `null`이면 표시만 | `max`, `allowHalf` |
| `KSlideToConfirm` | 손잡이를 끝까지 밀어야 확정되는 버튼(결제, 잠금 해제) | `onConfirm`, `confirmed` 또는 `rememberKSlideToConfirmState()` |

```kotlin
KSwitch(wifi, { wifi = it })
KSegmentedControl(listOf("지도", "위성"), mode, { mode = it })
KChipRow(contentPadding = PaddingValues(horizontal = 20.dp)) {
    tags.forEach { tag -> KFilterChip(tag, tag in picked, { on -> picked = if (on) picked + tag else picked - tag }) }
}
KSlider(volume, { volume = it }, Modifier.fillMaxWidth())
KRangeSlider(price, { price = it }, valueRange = 0f..100f)
KQuantityStepper(count, { count = it }, style = KQuantityStyle.Collapsible)
```

### 글자 입력

| 컴포넌트 | 하는 일 | 주요 파라미터 |
| --- | --- | --- |
| `KTextField` | 포커스되면 라벨이 위로 떠오르고 강조색 테두리가 부드럽게 나타난다. 글자가 있으면 지우기 버튼이 나온다. 오류가 생기면 테두리가 빨개지며 한 번 흔들린다. 키보드가 올라오면 아래 문구까지 보이도록 스크롤한다 | `label`, `error`, `placeholder`, `supportingText`, `leadingIcon`, `trailingIcon`, `variant`, `prefix`, `suffix`, `maxLength`, `minLines`, `keyboardOptions` |
| `KTextArea` | 여러 줄 입력란. 최소 줄 수만큼 높이를 잡고 최대 줄 수까지 자란다 | `minLines`, `maxLines`, `maxLength` |
| `KSearchBar` | 누르면 끝쪽에서 "취소" 버튼이 밀려 들어오고, 글자가 있으면 지우기 버튼이 나온다. 포커스를 받으면 강조색 테두리와 옅은 링이 나타난다 | `onSearch`, `showCancel` |
| `KSelect` | 입력란 모양의 선택 상자. 상자 폭과 같은 목록이 펼쳐지고 고른 항목에 체크가 붙는다. TalkBack은 고른 값을 상태로 읽는다 | `options`, `selectedIndex` |
| `KAutocomplete` | 글자를 치면 제안이 뜬다. 초점과 키보드는 입력란에 남고, 아래 방향키와 Enter로 고르며 Esc로 닫는다. 입력과 같은 부분은 굵게 보인다 | `suggestions`, `onSuggestionSelect` |
| `KCodeInput` | 한 칸에 한 자리씩 받는 인증번호, PIN 입력란. 붙여 넣기와 SMS 자동 채우기를 받고, `error`가 켜지면 칸이 빨개지며 흔들린다 | `length`, `mask`, `error`, `onComplete`, `imeAction` |
| `KTagInput` | 쉼표나 완료 키로 칩을 만든다. 같은 태그는 다시 넣지 않고 그 칩을 흔든다. 빈 입력란에서 지우기를 두 번 누르면 마지막 칩이 지워진다. 추천 목록은 아래 내용을 밀어내지 않고 겹쳐 뜨며, 아래 공간이 모자라면 입력란 위로 뜬다 | `suggestions`, `imeAction`, `keyboardActions`, `fieldModifier` |
| `KPasswordField` | 눈 아이콘으로 보이거나 숨긴다. 강도 막대가 약함, 보통, 강함 색으로 차오르고 조건 체크리스트를 보여 줄 수 있다. 강도만 필요하면 `passwordStrength(password)` | `showStrength`, `showChecklist`, `imeAction`, `fieldModifier` |
| `KAmountInput` | 키패드로만 입력하는 금액 입력. 통화 형식(천 단위, 기호 위치, 소수 자릿수)을 따르고 빠른 금액 칩(`KAmountPreset`)을 둔다. 값은 숫자 문자열이고 `KAmountDefaults.toBigDecimal`로 바꾼다 | `currency`, `presets`, `maxDigits` |
| `KKeypad`, `KPinDots` | 숫자 키패드(`KKeypadMode.Pin`, `Numeric`, `Decimal`, `Phone`)와 PIN 자리 점. 지우기 키를 길게 누르면 `onClear`가 불리고, PIN 모드 아래 줄 시작 칸은 생체 인증 버튼 자리(`extraKey`)다. `Phone`은 `*`, `0`, `#` 줄 아래 가운데에 통화 버튼 자리(`extraKey`)를 두고, 0을 길게 누르면 `+`를 넘긴다 | `mode`, `onClear`, `extraKey` |
| `KColorPicker` | 색조 고리와 채도·명도 사각형, 투명도 슬라이더, HEX 입력란, 최근 색과 프리셋 칩이 있는 색 선택기. 색 바퀴는 최대 320dp다. 키보드로는 Tab으로 고리, 사각형, 슬라이더를 오가며 방향키로 조절한다 | `presets`, `showAlpha`, `showHexInput` |
| `KSignaturePad` | 속도에 따라 굵기가 바뀌는 서명란. 되돌리기와 지우기 버튼이 있고 `state.toImageBitmap()`이나 `state.toSvg()`로 내보낸다 | `state = rememberSignaturePadState()`, `inkColor`, `showControls` |

`KTextFieldVariant`는 `Surface`(기본, 표면 상자), `Outlined`(떠오른 라벨이 위 테두리에 걸치고 그 자리 테두리가 끊김), `Filled`(옅은 바탕과 아래 선), `Underline`(아래 선만),
`Inline`(라벨은 왼쪽에 고정되고 값은 오른쪽 정렬, 목록 섹션 행 안에 넣는다)이다. 접두와 접미 글자는 라벨이 떠오를 때 나타나고, `maxLength`를 주면 넘는 입력을 자르고 아래 끝에 글자 수를 보여 준다.

`KPasswordField`와 `KTagInput`의 `modifier`는 입력란과 칩, 강도 막대를 감싼 바깥 묶음에 붙는다. `testTag`나 `focusRequester`처럼 입력란 자체에 걸어야 하는 것은 `fieldModifier`로 준다.
`KCodeInput`은 `modifier`가 곧 입력란이다. `KTagInput`은 입력 중인 글자가 있으면 동작 키로 칩을 만들고, 빈 입력란에서 누르면 `keyboardActions`로 넘기므로 `imeAction = ImeAction.Next`로 두면 다음 입력란으로 갈 수 있다.

```kotlin
KTextField(email, { email = it }, "이메일", error = emailError, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
KTextField(price, { price = it }, "가격", variant = KTextFieldVariant.Outlined, prefix = "₩", maxLength = 10)
KPasswordField(password, { password = it }, "비밀번호", showChecklist = true)
KCodeInput(code, { code = it }, onComplete = ::verify)
KAmountInput(amount, { amount = it }, Currency.getInstance("KRW"), label = "보낼 금액")
```

통화 화면처럼 자주 다시 그려지는 화면에서는 `KTextField`, `KTextArea`, `KChatInput`의 `TextFieldState` 오버로드를 씁니다. String 방식은 값이 늦게 돌아오면 입력 중인 글자를 잃을 수 있습니다. `TextFieldState` 쪽은 `maxLength`가 붙여 넣은 글의 넘치는 끝부분만 버리고, 호스트가 넘긴 `inputTransformation` 뒤에 이어 붙습니다.

```kotlin
val draft = rememberTextFieldState()
KChatInput(draft, onSend = { send(draft.text.toString()); draft.clearText() }, maxLength = 500, showCounter = true)
```

### 날짜와 시간

| 컴포넌트 | 하는 일 | 주요 파라미터 |
| --- | --- | --- |
| `KWheelPicker` | iOS 휠 피커. 원통에 붙은 것처럼 위아래 항목이 기울고, 항목을 지날 때마다 가볍게 진동하며 가운데에 맞춰 멈춘다 | `items`, `cyclic`, `visibleCount` |
| `KDatePicker`, `KTimePicker` | 휠로 만든 날짜, 시간 선택기. 년·월·일 순서와 오전/오후 위치는 언어를 따른다 | `range`, `is24Hour` |
| `KCalendar` | 월 달력. 좌우로 넘기고, 하루(`KCalendarSelection.Single`)나 기간(`Range`)을 고르며, 날짜 아래에 일정 점을 찍는다 | `rememberKCalendarState(initialMonth, range)`, `events`, `colors` |
| `KWeekStrip` | 한 주를 한 줄로 보이고 좌우로 넘기는 주간 달력 띠. 일정 점은 3개까지 | `events`, `range` |
| `KDateRangeDialog`, `KTimeRangeDialog` | 빠른 선택(`KRangePresets.dates()`, `times()`)이 있는 기간, 시간 범위 다이얼로그. 적용을 눌러야 결과를 넘기고, 시간 범위가 밤을 넘기면 안내한다 | `presets`, `onConfirm` |

```kotlin
var selection by remember { mutableStateOf<KCalendarSelection>(KCalendarSelection.Range()) }
KCalendar(rememberKCalendarState(), selection, onSelectionChange = { selection = it })
KDatePicker(birthday, { birthday = it })
KDateRangeDialog(picking, { picking = false }, start, end, onConfirm = { s, e -> start = s; end = e })
```

### XML 화면 안에서 끌기

XML 화면의 `NestedScrollView`, `ViewPager2`, `RecyclerView` 안에 `ComposeView`로 넣어도 색상 고리와 사각형, 서명란은 끄는 동안 바깥 View가 터치를 가로채지 않는다.
투명도 슬라이더, `BeforeAfterSlider`, `FlipCard`는 자기 방향(가로 슬라이더는 가로)으로 끌 때만 붙잡고, 반대 방향으로 끌기 시작하면 바깥 스크롤에 넘긴다.

## 목록과 카드

### 목록 행과 섹션

| 컴포넌트 | 하는 일 | 주요 파라미터 |
| --- | --- | --- |
| `KListItem` | 1~3줄 목록 행. 끝쪽이 스위치, 체크박스, 라디오면 행 전체가 그 컨트롤 하나가 된다 | `supporting`, `overline`, `leading`(`KListLeading.Icon`, `Avatar`, `Thumbnail`, `Custom`), `trailing`(`KListTrailing.Value`(글자 색 `color`), `Switch`, `Checkbox`, `Radio`, `Disclosure`, `Custom`), `onClick` |
| `KListSection` | 행 묶음. `InsetGrouped`는 표면 하나에 행을 담아 첫 행 위와 끝 행 아래만 둥글게 하고 터치 피드백도 그 모서리로 자른다. `Plain`은 표면 없이 놓는다. 행 사이 구분선은 행 글자가 시작하는 곳부터 긋는다 | `header`, `footer`, `style` |
| `LazyListScope.kListSection` | 행이 많을 때 `LazyColumn` 안에서 쓰는 섹션. 머리글, 행, 꼬리글이 각각 지연 항목이 된다 | `items`, `key`, `sectionKey` |
| `KSwitchRow`, `KCheckboxRow`, `KRadioRow`, `KValueRow`, `KDisclosureRow` | 설정 화면 행 프리셋. `KListItem` 위에 얹은 것이라 섹션 안에서 구분선과 모서리가 맞는다 | `subtitle`, `leading` |
| `KDivider`, `KVerticalDivider` | 기본 두께는 화면 픽셀 하나다 | `startInset`, `endInset` |
| `SwipeActionsRow` | 행을 밀면 뒤에서 동작 버튼(`SwipeAction`)이 드러나고, 끝까지 밀면 첫 동작이 바로 실행된다. `removesRow`면 행이 줄어들며 사라진다 | `endActions`, `startActions`, `fullSwipe`, `state = rememberSwipeActionsState(rememberSwipeActionsGroup())`(한 번에 한 행만 열기), `shape`(둥근 카드 행이면 같은 모양) |
| `ReorderableItem`, `Modifier.reorderable` | `LazyColumn`에서 길게 누르면 항목이 떠오르고 끌어서 순서를 바꾼다. 다른 항목은 비켜 주고 가장자리에서는 자동으로 스크롤한다. TalkBack에서는 '위로 이동', '아래로 이동' 동작으로, 키보드에서는 Alt+위/아래로 옮긴다 | `rememberReorderableListState(listState, onMove)`, `key` |
| `KExpandable`, `KExpandableGroup` | 아코디언. 화살표가 돌고 내용 높이가 용수철로 펼쳐진다. 그룹은 `singleOpen`이면 한 번에 하나만 펼친다 | `rememberKExpandableGroupState(singleOpen)` |

```kotlin
KListSection(header = "연결", footer = "비행기 모드를 켜면 모든 무선 연결이 끊깁니다.") {
    item { KSwitchRow("비행기 모드", airplane, { airplane = it }, leading = KListLeading.Icon(Icons.Rounded.Plane, container = Color(0xFFFF9500))) }
    item { KDisclosureRow("Wi-Fi", onClick = ::openWifi, value = "Home") }
}

LazyColumn {
    kListSection(mails, key = { it.id }, header = "받은편지함") { mail ->
        SwipeActionsRow(endActions = listOf(SwipeAction("삭제", Color(0xFFFF3B30), removesRow = true) { delete(mail) })) {
            KListItem(mail.from, supporting = mail.subject, onClick = { open(mail) })
        }
    }
}

val reorder = rememberReorderableListState(listState) { from, to -> todos = todos.toMutableList().apply { add(to, removeAt(from)) } }
LazyColumn(state = listState) {
    items(todos, key = { it }) { todo -> ReorderableItem(reorder, key = todo) { dragging -> KListItem(todo) } }
}
```

`kListSection`은 행마다 표면을 따로 그리되 이어지는 위아래 가장자리를 잘라 `KListSection`과 같은 모양을 만든다. 행 사이가 붙어야 하므로 그 `LazyColumn`에는 `verticalArrangement` 간격을 주지 않는다. `Arrangement.spacedBy(12.dp)`처럼 간격을 주면 행마다 표면이 끊겨 사이로 바탕이 비친다. 섹션 사이나 다른 카드 사이 여백이 필요하면 그 자리에 `item { Spacer(Modifier.height(12.dp)) }`를 넣는다.

```kotlin
LazyColumn(contentPadding = PaddingValues(16.dp)) { // verticalArrangement 간격은 주지 않는다
    item { SummaryCard() }
    item { Spacer(Modifier.height(16.dp)) }
    kListSection(mails, key = { it.id }, header = "받은편지함") { mail -> KListItem(mail.from) }
}
```
`KListSection`은 안의 행을 한꺼번에 구성하므로 행이 수십 개를 넘으면 `kListSection`을 쓴다.

### 카드

| 컴포넌트 | 하는 일 | 주요 파라미터 |
| --- | --- | --- |
| `KSurface` | 표면 하나. `onClick`을 주면 카드처럼 눌리고, 안쪽 글자 색은 `LocalKineticaContentColor`로 받는다 | `shape`, `variant`, `tone`, `backdrop` |
| `KCard` | `Glass`(기본), `Elevated`, `Filled`, `Tonal`, `Outlined`, `Text` 카드. `Filled`에 `KTone.Accent`를 주면 강조색으로 채운 판이 된다 | `onClick`, `variant`, `tone`, `contentPadding` |
| `KSelectableCard` | 요금제, 배송 방법처럼 큰 선택지. 고르면 테두리가 굵어지고 위 끝 모서리에 체크 원이 튀어나온다. 하나만 고르면 `role = Role.RadioButton` | `selected`, `onSelectedChange`, `role` |
| `KMediaCard` | 사진 위 글자 카드. 아래쪽 스크림으로 흰 글자의 대비를 확보한다 | `image`, `title`, `subtitle`, `eyebrow`, `aspectRatio` |
| `KHorizontalCard` | 썸네일과 글자를 가로로 놓은 카드. 썸네일 모서리는 카드와 동심원으로 깎는다 | `thumbnail`, `trailing` |
| `KCarousel`, `KPageIndicator` | 옆 카드가 살짝 보이고 멀어질수록 작아지며 기우는 캐러셀. 점 인디케이터는 넘기는 동안 액체처럼 늘어난다 | `peek`, `pageSpacing`, `maxVisible` |
| `SwipeCardStack` | 틴더식 카드 더미. 끌면 기울며 따라오고 던지면 날아간다. `state.undo()`로 되돌린다 | `onSwipe`, `directions`, `overlay` |

```kotlin
KCard(onClick = ::openPlan, variant = KVariant.Elevated) { Text("이번 달 요약") }
KSelectableCard(plan == "yearly", { plan = "yearly" }, role = Role.RadioButton) { Text("연간 결제") }
val pager = rememberPagerState { 5 }
KCarousel(5, Modifier.height(180.dp), state = pager) { page -> KCard { Text("배너 $page") } }
KPageIndicator(pager)
```

### 아바타와 배지

| 컴포넌트 | 하는 일 | 주요 파라미터 |
| --- | --- | --- |
| `KAvatar` | 사람 한 명의 아바타. 데이터는 `KAvatarData(name, color, image)` 값으로 넘긴다 | `size`(`KAvatarSize`, 5단계), `shape`, `status`(`KAvatarStatus.Online` 등) |
| `AvatarStack` | 겹친 아바타와 "+N" | `max`, `size` |
| `Modifier.kBadge` | 다른 요소의 오른쪽 위(RTL이면 왼쪽 위)에 겹치는 배지. 숫자가 바뀌면 톡 튀고, 99를 넘으면 `99+` | `count`, `dot`, `max` |
| `KBadge` | 줄 안에 놓는 배지. `KBadge(count)`는 숫자, `KBadge("NEW")`는 글자 배지다 | `tone`, `variant` |
| `KStatusBadge` | 상태 배지 | `status`(`KStatus`), `dot` |

```kotlin
KAvatar(KAvatarData("민지"), size = KAvatarSize.Large, status = KAvatarStatus.Online)
AvatarStack(listOf(KAvatarData("민지"), KAvatarData("준호"), KAvatarData("서연")), max = 3)
KIconButton(Icons.Rounded.Bell, "알림", onClick = ::openInbox, modifier = Modifier.kBadge(unread))
KStatusBadge("배송 중", KStatus.Info)
```

## 내비게이션과 앱 바

### 앱 바와 탭

| 컴포넌트 | 하는 일 | 주요 파라미터 |
| --- | --- | --- |
| `KTopAppBar` | 상단 앱 바. `rememberKTopAppBarState(listState)`를 넘기면 맨 위에서는 배경이 없다가 콘텐츠가 바 아래로 지나갈 때 배경과 경계선이 나타난다. 높이가 고정이라 콘텐츠 패딩이 바뀌지 않는다 | `variant`(`Small`, `CenterAligned`, `Medium`(26sp 제목이 스크롤하면 접힘)), `state`, `onBack`, `actions` |
| `KLargeTitleBar` | One UI와 iOS식 34sp 큰 제목. 목록을 스크롤하면 큰 제목이 줄어들며 위 바의 작은 제목으로 바뀌고, 접힌 바의 배경은 아래로 옅어진다. 목록에 `Modifier.nestedScroll(state.connection)`을 붙인다 | `rememberKLargeTitleState()`, `bottom`(탭이나 검색창. 큰 제목과 함께 올라가 작은 바 밑에 붙음), `contentMaxWidth` |
| `KBottomAppBar` | 화면 아래 도구 막대 | `actions`, `floatingActionButton` |
| `KFloatingToolbar` | 떠 있는 캡슐 도구 막대. `rememberKFloatingToolbarState()`의 `connection`을 목록에 붙이면 아래로 스크롤할 때 접히고 위로 스크롤하면 펼쳐진다. FAB는 캡슐 옆에 남는다 | `expanded`, `floatingActionButton` |
| `KTabBar` | 화면 아래에 떠 있는 탭 바. 선택한 탭의 인디케이터가 늘어났다 줄어들며 액체처럼 이동한다 | `tabs: List<KTab>`, `selectedIndex` |
| `KFloatingTabBar` | 목록에 `state.nestedScrollConnection`을 붙이면 아래로 스크롤할 때 선택한 탭 아이콘만 남은 캡슐로 줄어든다. `accessory`는 펼친 동안 탭 바 위에 붙어 있다가 접히면 캡슐 옆으로 내려온다 | `state = rememberKFloatingTabBarState()`, `accessory` |
| `KTopTabs` | 페이저를 넘기는 동안 인디케이터가 손가락을 따라 늘어났다 줄어드는 상단 탭 | `pagerState`, `indicator`(`KTabIndicator.Pill`, `Underline`, `Segmented`), `scrollable` |
| `KTab` | 탭 하나. 배지는 `KTabBar`와 `KFloatingTabBar`에서는 아이콘 모서리에, 아이콘 없는 탭과 `KTopTabs`에서는 이름 옆에 붙는다 | `label`, `icon`, `badge`, `dot` |

```kotlin
val bar = rememberKTopAppBarState(listState)
KTopAppBar("사진", state = bar, onBack = ::finish, actions = { KIconButton(Icons.Rounded.Share, "공유", onClick = ::share) })

val title = rememberKLargeTitleState()
Column {
    KLargeTitleBar(title, "설정", bottom = { KSearchBar(query, { query = it }, onSearch = {}) })
    LazyColumn(Modifier.nestedScroll(title.connection)) { items(40) { KListItem("항목 $it") } }
}

val pages = rememberPagerState { 3 }
KTopTabs(listOf(KTab("추천"), KTab("인기"), KTab("새 글", badge = 3)), pages, indicator = KTabIndicator.Underline)
```

`KLargeTitleBar`는 iOS와 One UI 화면처럼 큰 제목이 필요한 목록 화면에, `KTopAppBar`는 상세 화면이나 편집 화면에 쓴다.

### 화면 이동

| 컴포넌트 | 하는 일 | 주요 파라미터 |
| --- | --- | --- |
| `PushNavigation` | 화면 스택. 기본은 iOS식 push 전환이라 새 화면이 옆에서 들어오고 아래 화면은 조금 물러나며 어두워진다. `transition`으로 [화면 이동 전환](#화면-이동-전환)을 고른다. 왼쪽 가장자리에서 끌거나 예측 뒤로 가기 제스처를 하면 손가락을 따라 전환이 되감긴다 | `stack`, `onBack`, `swipeBack`, `transition`, `paneTitle` |
| `KExpandHost` + `Modifier.expandSource` | App Store 투데이 탭처럼 카드가 제자리에서 화면 가득 커지며 상세 화면이 된다. 배경은 흐려지고, 상세를 아래로 끌거나 예측 뒤로 가기 제스처를 하면 작아지면서 둥글어지며, 놓으면 원래 카드 위치로 돌아간다. `transform`으로 목록 행이나 플로팅 버튼에서 여는 모습을 고른다 | `rememberKExpandState()`, `state.expand(key)`, `cornerRadius`(기본은 디자인 언어의 card 모서리), `transform` |
| `KDrawer` | 가장자리에서 끌어 여는 서랍. `KDrawerMode.Push`(기본)는 뒤 화면을 밀어내며 작아지고 둥글어지게 하고, `Modal`은 뒤 화면을 그 자리에서 어둡게 덮는다 | `drawerContent`, `state = rememberKDrawerState()`, `mode` |
| `KSearchView` | 누르면 캡슐이 커져 검색 화면이 된다. 좁은 창은 화면 전체, 600dp 이상은 캡슐 자리에서 펼쳐지는 판(최대 720dp)이다. 비어 있으면 최근 검색, 글자가 있으면 제안을 보여 준다. 제안 끝의 화살표는 검색어 칸에만 채운다 | `suggestions`, `recent`, `onRemoveRecent`, `onClearRecent` |
| `KStepIndicator` | 단계 표시. `KStepStyle.Numbered`(번호 원과 이름), `Dots`(지금 단계 점이 캡슐로 늘어남), `Bar`(나눈 막대와 'n/전체') | `steps`, `current`, `onStepClick` |
| `KWizard` | 단계 표시, 넘어가는 내용, 이전과 다음 버튼을 묶은 마법사. 첫 단계가 아니면 뒤로 가기가 앞 단계로 돌아간다 | `rememberKWizardState(stepCount)`, `nextEnabled`, `indicator` |
| `KPagination` | `KPaginationStyle.Numbers`(칸 수가 늘 같아 폭이 바뀌지 않음)와 `Compact`(`5 / 20`). 폭이 모자라면 양옆 번호 수를 줄인다 | `style`, `siblings` |
| `KBreadcrumb` | 이동 경로. 마지막 항목은 지금 위치라 누를 수 없고, 길면 사이를 '…'으로 접었다가 누르면 펼친다 | `maxVisible` |
| `KIndexBar` | 연락처 색인 막대. 끌면 손가락 아래 글자마다 햅틱과 함께 `onSelect`가 불리고 큰 글자 말풍선이 따라온다. 자리가 모자라면 글자를 건너뛰고 점을 찍는다 | `KIndexBarDefaults.Hangul`, `Latin`, `HangulAndLatin`, `keyOf("김민지")`(초성) |

```kotlin
var stack by rememberSaveable { mutableStateOf(listOf("home")) }
PushNavigation(stack, onBack = { stack = stack.dropLast(1) }) { screen ->
    when (screen) {
        "home" -> KButton("상세 열기", onClick = { stack = stack + "detail" })
        else -> Text("상세")
    }
}

val expand = rememberKExpandState()
KCard(Modifier.expandSource(expand, "today"), onClick = { expand.expand("today") }) { Text("오늘의 추천") }
KExpandHost(expand) { key -> Text("$key 상세", Modifier.statusBarsPadding()) }
```

`PushNavigation`은 아래 화면의 `rememberSaveable` 상태(스크롤 위치 등)를 유지하고, 스택에서 빠진 화면의 상태는 지운다. 같은 화면을 두 번 쌓을 수 없고,
호스트가 `onBack`에서 스택을 바꾸지 않으면 화면이 제자리로 돌아온다. RTL 언어에서는 방향이 반대다. 모든 화면이 같은 ViewModelStoreOwner를 쓰므로 화면별 ViewModel은 `viewModel(key = …)`로 나눈다.

예측 뒤로 가기에서 손가락을 따라 움직이는 미리보기는 Android 14부터 동작한다. targetSdk 36 이상인 앱은 기본으로 켜져 있고, 그보다 낮으면 manifest에 `android:enableOnBackInvokedCallback="true"`를 넣는다. 꺼져 있으면 뒤로 가기를 눌렀을 때 닫히는 애니메이션만 나온다.

`KExpandHost`의 상세 화면은 상태 표시줄 뒤까지 그린다. 글자나 버튼이 상태 표시줄에 가리지 않게 상세 안의 해당 요소에 `Modifier.statusBarsPadding()`을 붙이고, 상세 배경이 어두우면 `KSystemBarIcons(light = true)`를 둔다.
상세 화면 위쪽을 카드와 똑같이 꾸미면 카드에서 상세로 끊김 없이 이어진다.

`KExpandHost`의 `transform`은 원래 요소에서 상세 화면으로 커지는 모습(Material container transform)을 정한다.

| 값 | 모습 | 어울리는 곳 |
| --- | --- | --- |
| `KContainerTransform.Card`(기본) | 상세 화면을 카드 너비로 줄여 그린 채 커진다 | 상세 위쪽이 카드와 같은 투데이 카드, 큰 배너 |
| `KContainerTransform.listRow(color)` | 행 자리에서 `color` 상자가 커지고, 상세 내용은 상자가 반쯤 커진 뒤 원래 크기로 나타난다 | 메일, 설정, 연락처 목록에서 상세로 |
| `KContainerTransform.fab(color)` | 동그란 버튼이 버튼 색 화면으로 커지며 모서리가 펴지고, 내용은 뒤따라 나타난다 | 새 글 쓰기, 새 항목 만들기 |

사진을 눌러 크게 보는 전환은 `KImageViewer`와 `Modifier.imageViewerSource`를 쓴다.

### 화면 이동 전환

`PushNavigation(transition = …)`과 `KTransitionContent(transition = …)`에 넣는 전환은 `KTransitions`에 있다. 모든 전환은 진행률 하나로
두 화면의 모습을 정하므로 뒤로 가기는 같은 전환을 되감고, 예측 뒤로 가기 제스처를 하면 손가락을 따라 되감긴다. 가로 방향은 RTL에서 뒤집히고,
'애니메이션 삭제' 설정이 켜져 있으면 전환 없이 바로 바뀐다. 진행률은 그리기 단계에서만 읽으므로 전환하는 동안 화면을 다시 구성하지 않는다.
전환하는 동안 가려지는 화면은 터치를 받지 않고 TalkBack에도 드러나지 않는다.

| 전환 | 모습 | 어울리는 곳 |
| --- | --- | --- |
| `Push`(기본), `push(parallax, dim, from)` | 새 화면이 옆에서 밀려 들어오고 아래 화면은 30% 물러나며 어두워진다 | iOS식 목록에서 상세 |
| `Parallax` | 아래 화면이 절반쯤 물러나며 작아지는 깊은 패럴랙스 | 사진, 쇼핑 상세 |
| `Slide`, `slide(from)` | 두 화면이 함께 밀린다 | 단계가 이어지는 가입 화면 |
| `Cover`, `cover(from)` | 새 화면이 덮고 아래 화면은 제자리에서 어두워진다. `cover(KTransitionEdge.Bottom)`은 전체 화면 다이얼로그처럼 아래에서 올라온다 | 작성 화면, 전체 화면 다이얼로그 |
| `Uncover`, `uncover(from)` | 기존 화면이 비켜나며 아래에 있던 새 화면을 드러낸다 | 온보딩을 마치고 홈으로 |
| `Zoom` | 새 화면이 조금 작은 크기에서 커지며 나타난다 | Android 시스템 앱 느낌 |
| `Fade`, `FadeThrough` | 크로스페이드, Material fade through | 서로 관련 없는 화면, 하단 탭 |
| `SharedAxisX`, `SharedAxisY`, `SharedAxisZ` | Material shared axis. 가로나 세로로 조금 밀리거나 앞으로 다가오며 바뀐다 | 단계, 위아래 순서, 목록에서 상세 |
| `TabSlide` | 너비의 4분의 1만 밀리며 겹쳐 바뀐다 | 상단 탭, 세그먼트 |
| `SheetStack` | 새 화면이 아래에서 올라오고 아래 화면은 작아지며 모서리가 둥글어지고 어두워진다 | iOS 모달 시트를 겹쳐 여는 흐름 |
| `CardStack` | 새 화면이 둥근 카드로 옆에서 들어오고 아래 화면은 작아지며 뒤로 물러난다 | 설정 하위 화면, 카드형 앱 |
| `Cube`, `Flip` | 정육면체의 이웃한 면처럼 돌거나, 카드를 뒤집듯 돌다가 반쯤 지나 새 화면이 나타난다 | 스토리, 게임 메뉴, 앞뒷면 카드 |
| `CircleReveal`, `reveal(shape, center)` | 원, 마름모, 별(`KRevealShape`) 모양이 퍼지며 새 화면을 드러낸다. `center`에 `Modifier.kOrigin`으로 얻은 위치를 주면 누른 자리에서 퍼진다 | 테마 전환, 검색, 축하 화면 |
| `BlurDissolve` | 기존 화면이 블러로 흐려지며 사라지고 새 화면이 선명해진다. Android 12 미만은 페이드만 한다 | 미디어 앱, 잠금 해제 |
| `Wipe`, `wipe(from)` | 가장자리에서 경계선이 쓸고 지나간다 | 비교 화면, 게임 |
| `None` | 바로 바꾼다 | |

```kotlin
var buttonCenter by remember { mutableStateOf<Offset?>(null) }
PushNavigation(
    stack,
    onBack = { stack = stack.dropLast(1) },
    transition = KTransitions.reveal(KRevealShape.Circle) { buttonCenter },
    paneTitle = { screen -> screenTitle(screen) },   // 화면이 바뀌면 TalkBack이 새 화면 이름을 읽는다
) { screen -> … }

// 탭: 오른쪽 탭으로 가면 끝 쪽에서, 왼쪽 탭으로 가면 시작 쪽에서 들어온다
KTransitionContent(tab, transition = KTransitions.TabSlide, isForward = { from, to -> to > from }) { page -> TabPage(page) }
```

전환도 디자인 언어를 따른다. `SheetStack`은 sheet 모서리, `CardStack`과 `KExpandHost`의 카드는 card 모서리, `KFullScreenModal`을 끌 때 둥글어지는
모서리와 그래버는 sheet 모서리를 쓰고, 기본 스프링은 `KineticaMotion`에 맞춘다. 기본 디자인 언어(`Cupertino`)에서는 디자인 언어가 생기기 전과
같은 모양(스택 전환 20dp, 전체 화면 모달 28dp)이다. 시간을 직접 정한 전환과 `KNavTransitions`는 디자인 언어와 상관없이 같다.

직접 만들려면 `KScreenTransition`을 상속해 `enter(frame, progress)`와 `exit(frame, progress)`에서 `KTransitionFrame`의 이동, 배율, 회전,
불투명도와 `dim`, `cornerRadius`, `blurRadius`를 정하거나 `clipTo`, `reveal`로 자른다. 진행률이 1일 때 들어온 화면이, 0일 때 가려질 화면이
원래 모습이어야 하고, 가로 값에는 `frame.endSign`을 곱해야 RTL에서 뒤집힌다. 디자인 언어의 모서리 크기는 `frame.shapes`에서 읽는다.

Navigation Compose의 `NavHost`나 Compose `AnimatedContent`에는 `KNavTransitions`의 묶음을 쓴다. `Push`, `Slide`, `SlideUp`, `SheetStack`,
`Zoom`, `Fade`, `FadeThrough`, `SharedAxisX`, `SharedAxisY`, `SharedAxisZ`, `None`이 있다. Compose 기본 전환으로 만들었으므로
navigation-compose의 예측 뒤로 가기 되감기를 그대로 따르지만, 화면을 어둡게 하거나 3D로 돌리거나 모양으로 드러내는 전환은 없다.

```kotlin
val nav = rememberKNavTransition(KNavTransitions.Push)   // '애니메이션 삭제'면 None
NavHost(controller, "home", enterTransition = nav.enter, exitTransition = nav.exit,
    popEnterTransition = nav.popEnter, popExitTransition = nav.popExit) { … }

AnimatedContent(step, transitionSpec = { kTransform(KNavTransitions.SharedAxisX, forward = targetState > initialState) }) { … }
```

## 오버레이

### 다이얼로그와 시트

| 컴포넌트 | 하는 일 | 주요 파라미터 |
| --- | --- | --- |
| `KDialog` | 누른 버튼에서 패널이 튀어나오듯 커지고, 배경은 서서히 흐려지며 어두워진다. 바깥을 누르거나 뒤로 가기로 닫는다. 너비는 280~420dp | `origin`(`Modifier.kOrigin { origin = it }`로 얻음), `title`(TalkBack이 읽는 창 이름), `contentWindowInsets` |
| `KAlertDialog` | 제목, 본문, 아이콘, 버튼 1~3개. 짧은 두 버튼은 나란히 놓고(취소가 시작 쪽), 셋이거나 글자가 길면 세로로 쌓아 취소를 맨 아래에 둔다 | `actions: List<KDialogAction>`(`KDialogActionStyle.Default`, `Preferred`, `Destructive`, `Cancel`), `stacked` |
| `KSheet` | iOS 시트처럼 아래에서 올라오고 뒤쪽 콘텐츠는 조금 물러나며 모서리가 둥글어진다. 아래로 끌어 닫고, 덜 끌고 놓으면 던진 속도를 이어받아 제자리로 돌아온다. 넓은 화면에서는 `KSheetDefaults.MaxWidth`(640dp)를 넘지 않고 가운데에 놓인다 | `title`(TalkBack이 읽는 창 이름), `contentWindowInsets` |
| `KActionSheet` | iOS 동작 시트. 동작 판과 떨어진 취소 판이 있고, 아래로 끌어 닫는다. `KSheetPresentation.Auto`면 600dp 이상 창에서 버튼 옆 팝오버로 바뀐다(취소는 바깥 누르기로 대신) | `actions`, `title`, `message`, `presentation` |
| `KDetentSheet` | Apple 지도처럼 여러 높이(`Detent.Peek`, `Fraction`, `Full`)에 멈추는 바텀 시트. 안의 목록 스크롤과 이어지고, 손을 떼면 던진 속도를 보고 가까운 높이로 정착한다. 폴더블을 접고 펴도 같은 높이 단계를 유지한다 | `rememberKDetentSheetState(detents, initialDetent, dismissible)`, `header` |
| `KFullScreenModal` | 아래에서 올라와 화면을 덮는 모달. 뒤 화면은 살짝 물러난다. 아래로 끌거나(안쪽 목록이 맨 위면 이어서) 던지면 닫히고, 덜 끌면 제자리로 정착한다 | `dragToDismiss`, `title` |

```kotlin
var origin by remember { mutableStateOf<Offset?>(null) }
KButton("주문", onClick = { confirm = true }, modifier = Modifier.kOrigin { origin = it })
KDialog(confirm, { confirm = false }, origin = origin) {
    Text("결제할까요?")
    KButton("결제", onClick = { confirm = false }, fullWidth = true)
}
KAlertDialog(asking, { asking = false }, "삭제할까요?", listOf(
    KDialogAction("취소", KDialogActionStyle.Cancel) { asking = false },
    KDialogAction("삭제", KDialogActionStyle.Destructive, onClick = ::delete),
))
KSheet(sharing, { sharing = false }, title = "공유") { KListItem("링크 복사", onClick = { sharing = false }) }

// 화면을 채우는 곳(스크롤 목록 밖)에 둔다
val places = rememberKDetentSheetState(listOf(Detent.Peek(120.dp), Detent.Fraction(0.5f), Detent.Full))
KDetentSheet(places, title = "주변 장소") { Text("카페 12곳") }
```

`KSheet`와 `KDialog`의 `title`은 화면에 보이는 제목이 아니라 TalkBack이 읽는 창 이름이다. 보이는 제목은 내용에 직접 넣는다.

### 메뉴, 툴팁, 안내

| 컴포넌트 | 하는 일 | 주요 파라미터 |
| --- | --- | --- |
| `KDropdownMenu` | 버튼에 붙는 메뉴. 앵커 아래 공간이 모자라거나 키보드가 올라와 있으면 위로 뒤집고, 화면 가장자리에서는 안으로 밀어 넣는다. 키보드로 열면 첫 항목에 초점이 가고 방향키로 옮기며, 끝 방향키로 하위 메뉴에 들어가고 시작 방향키나 Esc로 나온다 | `entries`: `KMenuEntry.Action`(아이콘, 끝 글자, `destructive`), `Check`(메뉴를 연 채 켜고 끔), `Submenu`(같은 판 안에서 넘어감), `Section`, `Divider` |
| `Modifier.kContextMenu` | 길게 누르면 햅틱과 함께 요소가 떠오르고 배경이 흐려지며, 요소 위나 아래에 메뉴가 뜬다. `destructive` 항목은 빨간 글자다. TalkBack에서는 사용자 정의 동작으로 제공한다 | `items: List<KMenuItem>` |
| `KSpeedDial` | 떠 있는 둥근 버튼. 누르면 +가 ×로 돌고 항목이 차례로 튀어 오르며 뒤가 흐려진다 | `items: List<KMenuItem>` |
| `Modifier.kTooltip`, `tooltipOnLongPress` | 화살표가 달린 말풍선 | `placement`(`KTooltipPlacement.Auto`, `Above`, `Below`) |
| `Modifier.kRichTooltip` | 제목, 설명, 글자 버튼이 있는 툴팁. 저절로 닫히지 않고 바깥이나 버튼을 누르면 닫힌다 | `title`, `actionLabel`, `onAction` |
| `CoachMarkHost` + `Modifier.coachMarkTarget` | 처음 쓰는 사람을 위한 기능 안내. 화면이 어두워지고 대상 둘레만 밝게 뚫린다 | `rememberCoachMarkState()`, `steps: List<CoachStep>`, `state.start()` |

```kotlin
Box {
    KIconButton(Icons.Rounded.More, "더 보기", onClick = { menu = true })
    KDropdownMenu(menu, { menu = false }, listOf(
        KMenuEntry.Check("숨긴 파일 보기", hidden) { hidden = it },
        KMenuEntry.Divider,
        KMenuEntry.Action("삭제", destructive = true) { delete() },
    ))
}
KCard(Modifier.kContextMenu(listOf(KMenuItem("공유") { share() }, KMenuItem("삭제", destructive = true) { delete() }))) { Text("사진") }
KIconButton(Icons.Rounded.Info, "도움말", onClick = {}, modifier = Modifier.tooltipOnLongPress("길게 누르면 설명이 보여요"))
```

`KDropdownMenu`, `KActionSheet`의 팝오버, `KSearchView`는 부른 자리의 부모 레이아웃을 앵커로 쓰므로 여는 버튼과 함께 `Box`로 감싼다. 보이는 동안 크기 0인 요소가 그 자리에 하나 놓인다.

### 공통 동작

**높이와 스크롤**

- 시트는 내용 높이에 맞추되 최대 높이는 위쪽 안전 영역에서 `KSheetDefaults.TopGap`(48dp) 아래까지다. 내용이 더 길면 시트 안에 `LazyColumn`이나 `verticalScroll`을 넣는다. 목록이 맨 위일 때 아래로 끌면 시트가 내려간다.
- 다이얼로그는 안전 영역 안쪽 28dp 여백까지 커진다. 안에서 `fillMaxSize`나 `weight`를 쓰면 그 최대 크기를 채운다.
- 시트, 다이얼로그, 여러 높이 시트, 서랍, 펼친 카드, 미니 플레이어 안에 `LazyColumn`을 넣어도 무한 높이 오류가 나지 않는다.
- `KDetentSheet`, `KMiniPlayer`, `KMiniPlayerFullScreen`은 부른 자리에 그리므로 화면을 채우는 `Box` 안에 둔다. `verticalScroll` 안에 넣으면 `Can't represent a width … and height` 오류가 난다.

**인셋(상태 표시줄, 내비게이션 바, 키보드, 컷아웃)**

- 시트, 다이얼로그, 여러 높이 시트는 `contentWindowInsets`로 스스로 피한다. 기본값은 시트가 `KSheetDefaults.windowInsets`(아래쪽과 양옆의 safeDrawing), 다이얼로그가 `WindowInsets.safeDrawing`이다.
  안에 입력란이 있으면 키보드가 올라올 때 함께 올라온다. 호스트가 직접 처리하려면 `contentWindowInsets = { WindowInsets(0) }`을 준다.
- 토스트와 아일랜드는 위쪽, 스낵바는 아래쪽(키보드 포함)과 양옆의 안전 영역을 피한다. 여러 높이 시트의 `Detent.Peek`, `Fraction` 높이는 내비게이션 바와 키보드 위에서 잰다.
- edge-to-edge를 쓰지 않는 화면에서는 인셋이 0이 되므로 여백이 두 번 들어가지 않는다.

**포커스와 TalkBack**

- 다이얼로그, 시트, 메뉴, 기능 안내, 서랍이 열리면 뒤 화면은 TalkBack에서 빠지고 키보드 Tab 이동도 모달 밖으로 나가지 않는다. 닫으면 연 버튼으로 포커스가 돌아온다.
- 포커스를 모달 안으로 옮기는 것은 키보드로 쓰는 중일 때뿐이다. 터치로 열었을 때는 시트 안 입력란이 저절로 키보드를 띄우지 않는다.
- 토스트, 스낵바, 툴팁은 뒤 화면을 가리지 않는다.
- 팝업 안 입력란이 포커스를 가진 채 닫히면 포커스를 거두고 키보드를 내린다.

**뒤로 가기**는 가장 안쪽이 먼저 받는다. 시트 안에 호스트가 `BackHandler`를 두면 그것이 먼저, 그다음 시트가 닫히고, 그다음 화면 순서다.

**팝업 안의 글래스.** 다이얼로그, 시트, 여러 높이 시트, 서랍, 미니 플레이어 안의 글래스(입력란, 칩, 버튼 등)는 판 뒤 화면을 다시 비추지 않고 판 위에 얹힌 한 겹으로 옅게 그린다. 다른 배경을 비추게 하려면 `backdrop`을 직접 넘긴다.

'애니메이션 삭제' 설정에서는 메뉴, 시트, 검색 화면, 모달이 바로 열리고 닫히며, 하위 메뉴와 세그먼트 체크도 움직이지 않고 바뀐다.

## 피드백과 로딩

알림 종류는 `KStatus`(`Neutral`, `Info`, `Success`, `Warning`, `Error`)로 고른다. 종류마다 `KStatusIcon`(원 안의 i, 체크, 느낌표, 둥근 삼각형 경고)과 색이 정해진다.

### 알림

| 컴포넌트 | 하는 일 | 주요 파라미터 |
| --- | --- | --- |
| `KToastHost` + `rememberKToastState()` | 위에서 미끄러져 내려오는 토스트. `show`는 suspend 함수이고, `tone`을 주면 상태 아이콘을 단다(`Positive`는 성공, `Destructive`는 오류, `Accent`는 정보) | `state.show(text, durationMs, tone)` |
| `KToaster` + `rememberKToasterState()` | 종류별 아이콘이 붙은 토스트를 위나 아래에 쌓는다. 새 토스트가 앞에 오고 뒤 토스트는 작아지며 끝만 비친다. 쌓인 토스트를 누르면 펼치고, 펼친 동안에는 사라지기 타이머가 멈춘다. 옆으로 쓸면 닫힌다 | `position`(`KToastPosition.Top`, `Bottom`), `maxVisible`(기본 3, 넘으면 앞의 것이 닫힐 때까지 기다림), `richColors`(바탕에 종류 색을 옅게) |
| `KSnackbarHost` + `rememberKSnackbarState()` | 아래에서 떠오르는 스낵바. 동작 버튼과 남은 시간을 보여 주는 링(닫기 버튼)이 있고 옆으로 쓸어 닫는다. 문구는 두 줄까지, 버튼 글자가 길거나 문구 자리가 좁으면 버튼을 아래 줄 끝으로 내린다. 결과를 `KSnackbarResult.Action`이나 `Dismissed`로 돌려준다 | `state.show(message, actionLabel, durationMs, status)`, `bottomOffset` |
| `KIslandHost` + `rememberKIslandState()` | 다이내믹 아일랜드식 알림. 작은 캡슐이 부풀어 알림이 되고, `compactText`가 있으면 좁은 캡슐(예: 타이머)로 시작해 누르면 펼쳐진다. 위로 쓸어 닫는다. 결과는 `KIslandResult.Clicked`, `Dismissed`, `TimedOut`. 통화 시간처럼 보이는 알림의 내용만 바꿀 때는 코루틴 없이 `state.update(…)`를 부른다(보여 주는 알림이 없으면 `false`) | `state.show(KIslandContent(…))`, `state.update(…)`, `alwaysVisible` |
| `KBanner` | 화면 위쪽에 머무는 배너. 저절로 사라지지 않고 레이아웃 안에 자리를 차지하며, `visible`이 바뀌면 높이가 펼쳐지고 접힌다 | `status`, `title`, `actions: List<KBannerAction>`, `onDismiss` |
| `KAlert` | 본문 안 알림 상자. 제목은 TalkBack에서 제목으로, 아이콘은 상태 이름으로 읽힌다 | `variant`(`KVariant.Tonal` 기본, `Outlined`, `Filled`, `Glass`), `action` |
| `KNoticeBar` | 한 줄 공지 막대. 글이 막대보다 길면 글의 방향으로 흘러가고 끝쪽 가장자리를 흐리게 지운다. 화면 밖에서는 멈춘다 | `scroll`(`false`거나 '애니메이션 삭제'면 줄을 바꿔 전부 보여 줌), `velocity` |

`KToasterState`의 `show`는 id를 돌려준다. `loading`은 돌아가는 아이콘이 붙고 저절로 사라지지 않으며, `update(id, …)`로 결과를 알린다.
`promise(loading, success, error) { … }`는 로딩 토스트를 띄우고 작업이 끝나면 같은 토스트를 성공이나 실패로 바꾼 뒤 `Result`를 돌려준다. `dismiss(id)`, `dismissAll()`로 닫는다.

```kotlin
val toast = rememberKToastState()
val toaster = rememberKToasterState()
val snackbar = rememberKSnackbarState()
KineticaScaffold { padding ->
    Column(Modifier.padding(padding)) {
        KBanner(offline, "인터넷에 연결되어 있지 않아요", status = KStatus.Warning, actions = listOf(KBannerAction("다시 시도", ::retry)))
        KButton("저장", onClick = { scope.launch { toast.show("저장했어요", tone = KTone.Positive) } })
        KButton("올리기", onClick = {
            scope.launch { toaster.promise("사진을 올리는 중…", success = { n: Int -> "사진 ${n}장을 올렸어요" }, error = { "올리지 못했어요" }) { upload() } }
        })
        KButton("삭제", onClick = {
            scope.launch { if (snackbar.show("메일을 삭제했어요", actionLabel = "되돌리기") == KSnackbarResult.Action) restore() }
        })
    }
    KToastHost(toast)
    KToaster(toaster)
    KSnackbarHost(snackbar)
}
```

`KineticaScaffold` 안이면 스낵바가 `bottomBar` 위에 저절로 뜬다. 밖에서 Material `NavigationBar`처럼 하단 막대가 있으면 `KSnackbarHost(state, bottomOffset = 80.dp)`로 그 높이를 준다.

TalkBack은 토스트, 스낵바, 배너, 아일랜드가 나타나거나 내용이 바뀌면 읽어 준다. `KStatus.Error`는 읽던 것을 끊고 바로 읽고(assertive), 나머지는 읽던 것이 끝난 뒤 읽는다(polite).
토스트의 표시 시간은 접근성 설정의 '조치를 취할 시간'이 더 길면 그 값을 따른다. 펼친 토스트는 실제 높이로 늘어놓으므로 글자 2배와 RTL에서도 겹치지 않는다.

`KIslandState.show`에 `key`를 주면 같은 종류의 알림이 대기열에 쌓이지 않고 마지막 내용으로 바뀝니다. `replaceShowing = true`이면 지금 보이는 알림도 바꾸고 표시 시간을 다시 잽니다. 아바타처럼 아이콘이 아닌 내용은 `KIslandContent(leading = { … })`로 넣습니다.

```kotlin
island.show(KIslandContent("민지", message, compactText = "새 메시지", leading = { KAvatar(minji) }), key = "chat", replaceShowing = true)
```

### 로딩과 진행

| 컴포넌트 | 하는 일 | 주요 파라미터 |
| --- | --- | --- |
| `KLoadingIndicator` | `KLoadingStyle.Dots`(점 튀기기), `Bars`(물결 막대), `Morph`(도형 모핑, 기본), `Orbit`(궤도), `Pulse`(퍼지는 고리). '애니메이션 삭제'면 멈춘 모래시계 아이콘과 "불러오는 중…" 글자로 바뀐다 | `style`, `contained`(표면 원판 위에 올림), `label` |
| `KProgressBar`, `KProgressRing` | 용수철로 따라가는 진행 표시. `wave`를 켜면 액체가 차오르듯 출렁이고, `progress = null`이면 진행 정도를 모르는 상태를 보여 준다 | `progress`, `wave`, `thickness` |
| `KSegmentedProgressBar`, `KBufferProgressBar`, `KStepProgressBar` | 스토리식 칸 막대, 미리 받은 구간이 보이는 막대, 단계 점이 있는 막대. 단계 막대는 TalkBack에서 "5단계 중 2단계"처럼 읽힌다. 모두 RTL에서 오른쪽부터 찬다 | `segments`, `buffer`, `steps` |
| `Modifier.borderSpinner` | 빛 한 줄기가 꼬리를 끌며 테두리를 따라 돈다. 둘레 길이를 따라 같은 속도로 움직여 긴 카드의 모서리에서 빨라지지 않는다. 꺼져 있으면 시계도 멈춘다 | `active`, `shape`, `periodMs` |
| `Modifier.skeleton`, `KSkeletonText` | 불러오는 동안 빛이 흐르는 자리 표시 | `visible`, `style`(`KSkeletonStyle.Shimmer`, `Pulse`) |
| `KSkeletonListItem`, `KSkeletonCard`, `KSkeletonAvatar` | 스켈레톤 프리셋. 목록 행 프리셋은 `KListItem`과 높이, 여백이 같아 불러온 뒤 내용이 튀지 않는다 | `leading`, `lines` |
| `KRefreshBox` | 맨 위에서 당기면 콘텐츠가 저항감 있게 내려오고 표시가 차오른다. 기준 거리를 넘으면 햅틱이 오고, 놓으면 새로 고친다. TalkBack에서는 "새로 고침" 동작으로 제공한다 | `indicator`(`KRefreshIndicator.Drop`(기본, 물방울), `Arrow`(원 화살표), `Dots`), `threshold`, `indicatorTop` |

```kotlin
KRefreshBox(refreshing, onRefresh = ::reload) {
    LazyColumn {
        if (loading) items(6) { KSkeletonListItem() } else items(mails) { KListItem(it.subject) }
    }
}
KProgressRing(0.62f, Modifier.size(64.dp), wave = true) { Text("62%") }
KCard(Modifier.borderSpinner(active = syncing, shape = RoundedCornerShape(24.dp))) { Text("동기화 중") }
```

### 결과와 빈 화면

| 컴포넌트 | 하는 일 | 주요 파라미터 |
| --- | --- | --- |
| `KResultMark`, `KResultDialog` | `KResult.Loading`이면 링이 돌고, 결과로 바뀌면 원이 닫힌 뒤 체크, ×, 느낌표가 획 순서대로 그려진다. 성공이면 파티클이 튄다 | `state`(`Loading`, `Success`, `Failure`, `Warning`) |
| `KEmptyState`, `KEmptyIcon` | 빈 목록, 오프라인, 오류 화면. 원판 위 그림이 천천히 떠 있다 | `art = { KEmptyIcon(KEmptyIconKind.Offline) }`(`NoResults`, `Offline`, `EmptyInbox`, `Error`, `NoPermission`, `Search`, `Success`, `Maintenance`), `action` |
| `KHaptics`, `rememberKHaptics()` | 키트와 같은 규칙의 햅틱. 새 상수가 없는 옛 기기에서는 느낌이 비슷한 상수로 바꿔 낸다. `LocalKineticaHaptics provides false`로 앱 전체에서 끈다 | `perform(KHapticType.Selection, Tick, Success, Warning, Error, Edge, LongPress)` |

```kotlin
KResultDialog(paid, KResult.Success, "결제했어요", onDismissRequest = { paid = false })
KEmptyState("검색 결과가 없어요", message = "다른 단어로 찾아보세요", art = { KEmptyIcon(KEmptyIconKind.Search) })
val haptics = rememberKHaptics()
KButton("완료", onClick = { haptics.perform(KHapticType.Success) })
```

'애니메이션 삭제' 설정에서는 토스트와 배너가 바로 나타나고 사라지며, 로딩 표시는 멈춘 아이콘과 글자로, 테두리 로딩은 고르게 칠한 테두리로 바뀐다.

## 데이터 표시

### 차트

| 컴포넌트 | 하는 일 | 주요 파라미터 |
| --- | --- | --- |
| `KLineChart` | 선이 그려지며 나타나고, 손가락을 대고 옆으로 움직이면 값 말풍선이 따라온다. 데이터가 바뀌면 이전 모양에서 부드럽게 바뀐다. 채우기를 주면 영역처럼 보인다 | `points` 또는 `series: List<ChartSeries>`, `labels`, `fill`, `smooth`, `valueFormatter` |
| `KBarChart` | 막대가 차례로 자라고 누르면 강조된다 | `values`, `labels`, `orientation`(`KBarOrientation.Vertical`, `Horizontal`) |
| `KAreaChart` | 여러 계열 영역 차트. `stacked = true`(기본)면 아래부터 쌓고 띠 사이에 2dp 틈을 비우며, `false`면 계열마다 선 아래를 옅게 채워 겹친다 | `series`, `stacked` |
| `KMultiBarChart` | 여러 계열 막대 | `mode`(`KBarMode.Stacked`(음수 불가), `Grouped`(음수 가능)) |
| `KDonutChart` | 조각을 누르면 튀어나오며 가운데에 값을 보여 준다 | `slices: List<ChartSlice>`, `center` |
| `KPieChart` | 조각 안에 8% 이상인 조각의 비율을 적고, 누르면 조각이 튀어나온다. 넓은 자리에서도 320dp를 넘지 않는다 | `showPercent`, `legend` |
| `Sparkline` | 줄 안에 넣는 작은 선 그래프 | `points`, `fill` |
| `ActivityRings` | Apple Watch식 활동 링. 목표를 넘으면 겹쳐 돈다 | `rings: List<ActivityRing>` |
| `KHeatmap`, `KCalendarHeatmap` | 행×열 격자 히트맵과 요일×주 달력 히트맵(잔디). 달력 히트맵은 기간이 길면 가로로 스크롤하고 처음에 최근 주를 보인다 | `colors`, `maxCell`, `legend` |
| `KGauge` | 반원(`KGaugeShape.Semicircle`)이나 270도 원호(`Arc`) 게이지. `KGaugeSegment`로 구간 색과 이름을 주면 트랙에 구간을 옅게 깔고 지금 구간 색으로 채운다. 360dp를 넘지 않는다 | `range`, `segments` |
| `KStatCard` | 제목, 큰 값, 변화율, 스파크라인을 담은 숫자 카드. 값은 자릿수가 굴러가며 바뀐다. 변화율에는 화살표와 부호를 붙이고, `increaseIsGood = false`면 오를 때 빨간색으로 칠한다 | `change`, `trend`, `caption`, `increaseIsGood` |

어느 차트를 쓸지 고를 때는 이렇게 나눈다. 계열 하나의 흐름은 `KLineChart`, 여러 계열의 합과 비중은 `KAreaChart`, 막대 하나짜리는 `KBarChart`, 여러 계열 막대는 `KMultiBarChart`다.
가운데에 합계를 크게 보일 때는 `KDonutChart`, 비율 자체가 중요하고 조각이 다섯 개 안팎이면 `KPieChart`를 쓴다. 모두 같은 `ChartSeries`와 `ChartSlice`를 받는다.

차트 색은 `KChartPalettes`를 앞에서부터 차례로 쓴다. `categorical(surface)`는 색각 이상에도 이웃 색이 구별되는 8색(밝은 표면, 어두운 표면 따로), `sequential(surface, levels)`는 크기 단계 색,
`Good`, `Warning`, `Serious`, `Critical`은 상태 색이다. 계열이 8개를 넘으면 돌려 쓰지 말고 작은 것을 '기타'로 묶는다. 계열이 둘 이상이면 범례가 붙어 색만으로 구별하지 않게 한다.
예전부터 있던 `KChartDefaults.Palette`도 그대로 쓸 수 있지만, 새 화면에는 표면 밝기에 맞춰 고르는 `KChartPalettes`를 권한다.

```kotlin
val colors = KChartPalettes.categorical(KineticaTheme.surface)
KLineChart(points = listOf(3f, 5f, 4f, 8f, 6f), Modifier.fillMaxWidth().height(160.dp), labels = listOf("월", "화", "수", "목", "금"))
KAreaChart(listOf(ChartSeries(app, colors[0], "앱"), ChartSeries(web, colors[1], "웹")), Modifier.height(200.dp), labels = days)
KMultiBarChart(listOf(ChartSeries(app, colors[0], "앱"), ChartSeries(web, colors[1], "웹")), Modifier.height(200.dp), labels = days, mode = KBarMode.Grouped)
KDonutChart(listOf(ChartSlice(42f, "식비", colors[0]), ChartSlice(18f, "교통", colors[1])), Modifier.size(200.dp))
KStatCard("이번 주 방문", "12,480", change = 0.124f, trend = app, caption = "지난주 대비")
KGauge(72f, label = "배터리", segments = listOf(KGaugeSegment(20f, KChartPalettes.Critical), KGaugeSegment(100f, KChartPalettes.Good)))
```

TalkBack은 차트를 표로 읽는다. 영역 차트와 여러 계열 막대는 가로 항목 하나를 한 행으로 두고 '1월: 앱 3, 웹 5, 합계 8'처럼 계열 값과 합계를 차례로 읽으며 행 번호를 알린다.
히트맵은 행과 열 번호가 있는 표로 읽고, 파이는 조각마다 값과 비율을 읽는다. 행에서 두 번 탭하면 그 항목의 말풍선을 연다.

아래 축 이름은 가장 긴 이름과 간격이 들어가도록 건너뛰며 놓는다. 그래서 600dp, 840dp 폭에서는 더 많은 이름이, 좁은 화면이나 글자 2배에서는 적은 이름이 보인다. 히트맵 칸은 폭에 맞춰 `maxCell`까지만 커진다.

RTL에서의 방향은 다음과 같다.

- 시간 축이 있는 차트(`KLineChart`, `KBarChart`, `KAreaChart`, `KMultiBarChart`, `KHeatmap`, `KCalendarHeatmap`)는 RTL에서도 왼쪽이 처음이고 오른쪽이 최근이다. 아랍어와 히브리어 앱의 시계열 그래프도 대부분 왼쪽에서 오른쪽으로 읽고, 숫자도 왼쪽부터 쓰기 때문이다. 범례와 말풍선 글자는 앱 방향을 따른다.
- 원 그래프(`KPieChart`, `KGauge`)는 시계와 계기판 관례대로 방향을 바꾸지 않는다.
- 단계의 흐름(`KTimeline` 가로, `KStepIndicator`, `KWizard`)과 달력(`KWeekStrip`, 기간 다이얼로그의 달력)은 읽는 방향을 따라 RTL에서 오른쪽부터 놓고, 마법사는 진행 방향 반대로 밀려 들어온다.
- 키패드 숫자 배치는 전화 키패드 관례대로 바꾸지 않고, 지우기 키 화살표만 뒤집는다.

### 그 밖의 데이터 블록

| 컴포넌트 | 하는 일 | 주요 파라미터 |
| --- | --- | --- |
| `KTimeline` | 점과 선으로 잇는 타임라인. 지금 단계 점은 숨 쉬듯 퍼진다 | `items: List<KTimelineItem>`(`KTimelineStatus.Done`, `Current`, `Upcoming`, `Error`), `orientation`(`Vertical`, `Horizontal`) |
| `FlipDigits`, `FlipClock`, `rememberCountdown` | 기계식 플립 카드 숫자. 바뀐 자리만 위 반쪽이 접혀 내려간다. 남은 시간을 글자로 바꿀 때는 `countdownText(ms)` | `showSeconds`, `is24Hour` |
| `KProfileHeader` | 커버 위에 아바타를 겹친 프로필 머리. 숫자 칸과 버튼 줄이 있고 스크롤에 따라 접힌다 | `stats: List<KProfileStat>`, `actions`, `collapse` |
| `KPriceTag`, `KPricingCard`, `KBillingPeriodToggle` | 할인율과 취소선 정가가 있는 가격표, 추천 배지가 걸리는 요금제 카드(라디오), 월간과 연간 전환. `KPriceDefaults.format`, `discountPercent` 도우미가 있다 | `originalPrice`, `recommendedLabel`, `yearlyBadge` |

```kotlin
KTimeline(listOf(
    KTimelineItem("주문 접수", time = "10:02", status = KTimelineStatus.Done),
    KTimelineItem("배송 중", status = KTimelineStatus.Current),
    KTimelineItem("도착"),
))
val left by rememberCountdown(saleEndsAt)                       // 남은 ms
FlipDigits(countdownText(left))
KPricingCard("프로", "₩9,900", selected = plan == "pro", onSelect = { plan = "pro" }, period = "월", recommendedLabel = "추천")
```

'애니메이션 삭제' 설정에서는 차트가 처음부터 최종 모양을 그리고, 게이지와 단계 표시는 바로 옮겨 가며, 타임라인의 지금 단계 점과 금액 입력 커서는 움직이지 않는다. 마법사는 옅어지기만 한다.
반복 애니메이션(타임라인 점, 커서)은 화면에 보이는 동안만 돈다.

## 미디어와 소셜

### 미디어

| 컴포넌트 | 하는 일 | 주요 파라미터 |
| --- | --- | --- |
| `KMiniPlayer` | 아래에 떠 있는 재생 막대를 누르거나 위로 끌면 전체 화면 플레이어로 커지고, 앨범 아트가 이어지며 커진다. 아래로 끌거나 뒤로 가기로 접는다 | `rememberKMiniPlayerState()`, `artwork`, `collapsed`, `expanded`(진행도 `() -> Float`를 받음), `barBottomPadding` |
| `KMiniPlayerBar`, `KMiniPlayerFullScreen` | 막대와 전체 화면을 나눈 미니 플레이어. 막대는 캡슐 없이 내용만 그리므로 `KFloatingTabBar`의 `accessory`처럼 다른 컴포넌트 안에 넣는다. 펼치면 막대가 놓인 자리에서 앨범 아트가 커지고, 접으면 그 자리로 돌아간다 | 같은 `KMiniPlayerState`를 함께 넘긴다 |
| `PlayPauseIcon`, `MenuCloseIcon`, `PlusCloseIcon` | 모양이 매끄럽게 바뀌는 아이콘 | `playing`, `open` |
| `KImageViewer` + `Modifier.imageViewerSource` | 전체 화면 사진 뷰어. 썸네일에서 커지며 열리고, 핀치로 1~5배 확대, 두 번 탭 확대, 위아래로 끌어 닫기를 지원한다. 확대한 사진이 가장자리에 닿은 뒤 바깥쪽으로 더 끌면 옆 사진으로 넘어간다 | `rememberKImageViewerState()`, `images: List<Painter>` 또는 `pageCount` + `content`, `caption` |
| `BeforeAfterSlider` | 두 이미지를 겹쳐 놓고 가운데 손잡이를 끌어 비교한다. 가로 드래그만 받아 세로 스크롤을 막지 않는다 | `initial` 또는 `position`과 `onPositionChange`(호스트가 위치를 들고 있을 때) |
| `KOnboarding` + `OnboardingPage` | 첫 실행 안내. 그림이 시차를 두고 따라오고 배경색이 섞이며, 마지막 페이지에서 버튼이 '시작하기'로 늘어난다 | `pages`, `onFinish` |
| `LikeButton`, `KRating` | 하트 좋아요는 누르면 하트가 튀며 입자가 터지고 숫자가 굴러간다 | `liked`, `count` |

`KImageViewer`의 Painter는 `intrinsicSize` 비율대로 맞춰 그리고, `BrushPainter`처럼 크기가 없으면 화면 전체를 채운다. 열린 채 목록이 비면 바로 닫히고, 상태 없는 버전(`KImageViewer(images, startIndex, onDismiss)`)은 `onDismiss`를 부른다.
사진 뷰어, 펼친 미니 플레이어, 온보딩은 떠 있는 동안 시스템 바 아이콘을 배경에 맞게 바꾸고 닫으면 되돌린다.

```kotlin
val viewer = rememberKImageViewerState()
Row { photos.forEachIndexed { i, p -> Image(p, null, Modifier.size(96.dp).imageViewerSource(viewer, i).clickable { viewer.open(i) }) } }
KImageViewer(viewer, photos)

val player = rememberKMiniPlayerState()
Box(Modifier.fillMaxSize()) {
    KMiniPlayer(
        player,
        artwork = { Box(Modifier.fillMaxSize().background(Color(0xFFFF7A45))) },
        collapsed = { Text("지금 재생 중") },
        expanded = { PlayPauseIcon(playing, Modifier.size(48.dp)) },
    )
}
```

### 채팅과 소셜

| 컴포넌트 | 하는 일 | 주요 파라미터 |
| --- | --- | --- |
| `KChatBubble` | 보낸 말풍선(`KChatSide.Sent`)은 강조색으로 채우고 끝 쪽에, 받은 말풍선은 표면 스타일로 시작 쪽에 붙는다. 묶음 안쪽 모서리를 작게 깎고 마지막 말풍선에만 꼬리를 단다(RTL에서는 반대). 폭은 받은 폭의 78%를 넘지 않아 글자를 키워도 비율이 그대로다. `time`, `status`(보내는 중, 보냄, 전달됨, 읽음, 실패)는 내용 아래에 작게 붙고 TalkBack은 상태로 읽는다 | `position = chatGroupPosition(앞과 같은 사람, 뒤와 같은 사람)`, `time`, `status`, `maxWidthFraction` |
| `KTypingIndicator` | 받은 말풍선 모양 안에서 점 세 개가 차례로 튄다. 나타나면 TalkBack이 '입력 중'을 알린다 | `bubble` |
| `KChatInput` | 첨부 버튼, `maxLines`줄까지 높아지는 입력란, 보내기 버튼. 글자가 없으면 마이크 버튼(`onVoice`)이고 글자를 치면 강조색 원이 차오르며 위 화살표로 바뀐다. 하드웨어 키보드에서는 Enter로 보내고 Shift+Enter로 줄을 바꾼다 | `onSend`, `onAttach`, `onVoice` |
| `KStoryRing` | 아바타 둘레 스토리 링. 안 본 스토리는 그라디언트, 본 스토리는 가는 회색 링이며, `loading`이면 점선으로 나뉘어 돈다 | `seen`, `loading`, `colors`(`KStoryRingDefaults.Colors`, `CloseFriends`) |
| `KStoryViewer` | 전체 화면 스토리 뷰어. 분할 진행 막대, 시작 쪽 3분의 1을 누르면 이전, 나머지는 다음(RTL은 반대), 길게 누르면 멈춤, 아래로 끌거나 예측 뒤로 가기로 닫기 | `rememberKStoryViewerState(pageCount)`, `durationMs`, `header`, `footer` |
| `KAttachmentChip` | 첨부 파일 한 개. 올리는 중이면 바탕이 진행률만큼 차고 끝 취소 버튼 둘레에 진행 링이 돈다. 실패하면 다시 시도 버튼으로 바뀐다. 긴 이름은 가운데를 줄여 확장자를 남긴다 | `status`(`KAttachmentStatus.Ready`, `Uploading`, `Failed`), `progress`, `onRemove`, `onRetry` |
| `KFileDropZone` | 점선 테두리의 파일 영역. 누르면 `onClick`(파일 선택기), `onDrop`을 주면 분할 화면이나 데스크톱 모드에서 다른 앱 파일을 끌어 놓을 수 있다. 끌어 온 파일이 위에 있으면 테두리가 강조색 실선이 된다 | `onDrop`, `mimeTypes` |
| `KMapPin` | 지도 핀. `label`이면 가격 태그, `count`가 2 이상이면 묶음 핀, 그 밖은 물방울 핀. 처음 나타날 때 떨어지며 튀고 고르면 커진다. 아래 가운데가 지도 좌표다. 지도와 같은 크기의 `Box` 안에서 `Modifier.mapPinAnchor { 좌표 }`를 붙이면 꼬리 끝이 그 좌표에 놓인다 | `label`, `count`, `selected`, `dropIn` |

```kotlin
KChatBubble("내일 몇 시에 볼까요?", KChatSide.Received, position = chatGroupPosition(sameAsPrevious = false, sameAsNext = true))
KChatBubble("2시 어때요?", KChatSide.Sent, time = "오후 2:14", status = KChatStatus.Read)
KTypingIndicator()
KChatInput(draft, { draft = it }, onSend = { draft = "" }, onAttach = ::pickFile, onVoice = ::record)
KStoryRing(seen = false, onClick = { story = true }) { KAvatar(KAvatarData("민지"), size = KAvatarSize.Large) }
KAttachmentChip("견적서_최종.pdf", detail = "2.4MB", status = KAttachmentStatus.Uploading, progress = 0.4f, onRemove = {})
KMapPin(label = "₩12,000", selected = true, modifier = Modifier.mapPinAnchor { projection.toScreen(store.latLng) })
```

구글 지도처럼 SurfaceView로 그리는 지도 위에서는 글래스가 지도를 블러로 비추지 못하므로 `KMapPin`에 단색 표면을 쓴다.
'애니메이션 삭제' 설정에서는 스토리 뷰어가 바로 바뀌고 핀은 떨어지지 않는다. 스토리 자동 넘김은 그대로이며 '조치를 취할 시간' 접근성 설정만큼 늘어난다.

`KChatInput`은 화면 키보드의 Enter 키가 기본으로 보내기 키입니다(`imeSend = true`). 줄 바꿈 키로 두려면 `imeSend = false`를 넘깁니다. 하드웨어 키보드에서는 Enter가 보내고 Shift+Enter가 줄을 바꿉니다.

글자 수는 `showCounter = true`면 늘 보이고, `counterThreshold = 50`을 주면 남은 글자가 50자 이하가 될 때부터 보입니다. TalkBack은 입력란 이름으로 `label`을 읽고, 없으면 `placeholder`를 읽습니다. 글을 쓰기 시작해 안내 글이 사라져도 이름은 남습니다.

```kotlin
KChatInput(draft, { draft = it }, onSend = { draft = "" }, placeholder = "메시지 보내기", label = "민지에게 메시지", maxLength = 500, counterThreshold = 50)
```

다이얼로그나 바텀 시트 안에서 받은 말풍선은 그림자 없이 옅게 그려집니다. 판 위에서도 떠 보이게 하려면 `variant = KVariant.Elevated`를 넘깁니다.

## 효과와 모션

여기 있는 효과는 키트 컴포넌트뿐 아니라 Material `Button`, `Card`, `Image`, `LazyColumn` 항목 같은 호스트 Composable에도 그대로 붙는다. 터치를 소비하지 않으므로 클릭, 리플, TalkBack 역할은 그대로다.
리플, 버스트, 하트비트처럼 누를 때 효과와 등장, 반복 애니메이션 프리셋은 `kinetica-render-compose`의 `Modifier.kineticaEffects`가 맡는다([33 모션 효과](33-effects.md)).

### 효과 수정자

| 효과 | 사용법 |
| --- | --- |
| 글로우 테두리 | `Modifier.glowBorder(shape, colors, width, glow)`. Siri와 Apple Intelligence처럼 색이 가장자리를 따라 돈다. 화면 전체에 붙이면 "듣는 중" 효과가 된다 |
| 젤리 누름 | `Modifier.jellyPress()`. 납작해졌다가 출렁이며 돌아온다 |
| 자석 | `Modifier.magnetic(strength)`. 끌면 손가락을 따라오다가 놓으면 튕기며 제자리로 돌아온다 |
| 기울이면 3D | `Modifier.motionTilt(maxDegrees, shine)`. 중력 센서로 기기 기울기를 읽어 요소가 따라 기울고 빛이 흐른다 |
| 누르면 기울기 | `Modifier.tiltOnPress()`. 누른 쪽으로 기운다 |
| 홀로그램 | `Modifier.holographic(shape, useSensor)`. 누른 위치나 기기 기울기에 따라 무지개빛 광택이 움직인다 |
| 흔들기 | `Modifier.shake(trigger)`. `trigger`가 바뀔 때마다 좌우로 흔든다(오류 표시) |
| 반짝이 | `Modifier.sparkle()`. 요소 주위에 작은 별이 반짝였다 사라진다 |
| 스크롤 시차 | `Modifier.parallaxScroll(scrollState 또는 listState, rate, pullState)`. 헤더가 느리게 움직이고, `rememberParallaxState()`를 넘기면 맨 위에서 당길 때 커진다 |
| 스포트라이트 | `Modifier.spotlight(shape = …)`. 손가락을 대고 움직이면 그 자리에 빛이 따라다니고 테두리가 빛난다 |
| 물결 왜곡 | `Modifier.rippleDistortion(strength, wavelength)`. 누른 자리에서 물결이 퍼지며 내용이 굴절된다. Android 13 이상은 AGSL 셰이더로, 그 아래에서는 밝은 고리로 대신한다. 물결은 최대 4개까지 겹친다 |
| 액체 모양 | `Modifier.liquidMorph(color)`. 누른 자리가 오목하게 들어갔다가 출렁이며 돌아온다. 버튼으로는 `KBlobButton` |
| 방울 묶음 | `KBlobGroup { Box(Modifier.blob()) }`. 가까운 방울끼리 메타볼처럼 녹아 붙는다. Android 12 이상에서 이어지고, 그 아래에서는 따로 그린다. 방울은 `Modifier.offset { }`처럼 배치를 바꾸는 방법으로 움직여야 한다 |
| 길게 눌러 확인 | `Modifier.holdToConfirm(onConfirm)`. 누르고 있는 동안 테두리를 따라 선이 그려지고 다 차야 실행한다. TalkBack에서는 두 번 탭으로 바로 실행한다 |
| +1 떠오르기 | `Modifier.floatUp(state)` + `rememberKFloatUpState().emit("+1")`. 요소 위로 글자가 톡 튀어나와 떠오른다 |

`glowBorder`는 부모가 clip하는 요소 안에 두면 바깥 번짐이 잘리고 안쪽에 얼룩이 남습니다. 자르지 않는 부모 안에 두거나 사방에 `glow`만큼 여백을 둡니다. 켜고 끌 때는 람다로 진하기를 넘기면 다시 구성하지 않고 서서히 나타납니다.

```kotlin
val level by animateFloatAsState(if (speaking) 1f else 0f, label = "glow")
Box(Modifier.glowBorder({ level }, RoundedCornerShape(24.dp)))
```

### 글자, 배경, 파티클

| 효과 | 사용법 |
| --- | --- |
| 숫자 롤링 | `RollingText("1,284,000원")`. 바뀐 자릿수만 위아래로 굴러가고, 굴러가는 중에 값이 또 바뀌면 지금 위치에서 이어 굴러간다 |
| 타자기 | `TypewriterText("…")`. 깜박이는 커서와 함께 한 글자씩 나타난다 |
| 글자 효과 | `ShimmerText`(빛이 흐르는 글자), `GradientText`(색이 흐르는 글자), `ScrambleText`(뒤섞였다 맞춰지는 글자) |
| 컨페티 | `ConfettiHost(state)` + `rememberConfettiState().burst(origin)` |
| 파티클 | `ParticleField(ParticlePreset.Snow)`. 프리셋은 `Snow`, `Fireworks`, `Bubbles`, `Sparks`. `rememberParticleFieldState()`의 `launch(at)`으로 원하는 자리에서 터뜨린다 |
| 메시 그라디언트 배경 | `MeshGradientBackground(palette = MeshGradients.Sunset)`. 색 덩어리가 천천히 떠다니며 섞인다(API 33 이상은 AGSL 셰이더). 프리셋은 `Sunset`, `Ocean`, `Aurora`, `Candy`, `Midnight`이고 `MeshPalette`로 직접 만들 수 있다 |
| 하늘 배경 | `AuroraBackground`(오로라 띠), `StarfieldBackground`(반짝이는 별과 가끔 별똥별) |
| 이미지 대표색 | `rememberImagePalette(image)`, `imagePaletteOf(image)`로 `dominant`, `vibrant`, `muted`, `dark` 색을 뽑고, `PaletteBackground(palette)`로 그 색에 물든 배경을 그린다 |
| 뒤집는 카드 | `FlipCard(flipped, onFlippedChange, front = { … }, back = { … })`. 끌면 손을 따라 돌고, 놓으면 속도와 각도를 보고 가까운 면에 멈춘다. `axis`로 가로축, 세로축을 고른다. 기본으로는 보이지 않는 면을 컴포지션에서 빼므로 그 면의 상태가 뒤집을 때 처음으로 돌아간다. `keepBothFaces = true`면 두 면을 모두 남겨 상태를 유지하고, 보이지 않는 면은 그리지도 터치나 TalkBack에 드러내지도 않는다 |
| 모핑 아이콘 | `MorphIcon(from, to, atEnd)`. `KMorphIcons`의 다섯 쌍(`Search`↔`Close`, `Plus`↔`Check`, `Menu`↔`ArrowBack`, `Heart`↔`HeartFilled`, `VolumeLow`↔`VolumeHigh`)과 직접 만든 `KMorphGlyph`를 쓴다. 진행을 직접 정하는 `progress` 오버로드도 있다 |
| 복사 버튼 | `KCopyButton(text)`. 클립보드에 복사하고 아이콘이 체크로 바뀌며, TalkBack이 '복사됨'을 알린다 |

### 화면 전환과 목록 모션

| API | 쓰임 |
| --- | --- |
| `KCircularReveal`, `Modifier.circularReveal` | `origin`(화면 좌표, `Modifier.kOrigin`으로 얻음)에서 원이 퍼지며 드러난다. `onBack`을 주면 뒤로 가기 제스처 동안 원이 줄어든다 |
| `KSharedTransitionHost`, `Modifier.kSharedElement`, `kSharedBounds` | Compose `SharedTransitionLayout`을 감싼 공유 요소 전환. `onBack`과 `backTarget`을 주면 뒤로 가기 제스처 동안 전환을 되감는다 |
| `Modifier.staggerIn(index, state)` | 항목이 순서대로 떠오르며 등장한다. `rememberKStaggerState()`를 넘기면 지연 목록에서 다시 들어온 항목은 등장 애니메이션을 하지 않는다 |
| `Modifier.scrollTransform(listState, index, effect)` | 지연 목록 가장자리 항목에 `KScrollEffect.ScaleFade`, `Wheel`, `TopStack` 효과를 준다 |
| `KFlyToHost`, `Modifier.flyToSource`, `flyToTarget` | `rememberKFlyToState().launch(key) { 썸네일 }`로 상품이 포물선을 그리며 장바구니로 날아가고, 도착하면 장바구니가 튄다 |

화면 전환은 모두 예측 뒤로 가기 제스처를 따라간다. `KFullScreenModal`, `PushNavigation`, `KExpandHost`는 [오버레이](#오버레이)와 [내비게이션과 앱 바](#내비게이션과-앱-바)에 있고, 전환 종류는 [화면 이동 전환](#화면-이동-전환)에 정리했다.

```kotlin
KButton("구매", onClick = ::buy, modifier = Modifier.jellyPress().glowBorder(CircleShape))
RollingText("₩$total", style = TextStyle(fontSize = 32.sp))
ShimmerText("새 기능이 생겼어요")

val confetti = rememberConfettiState()
Box(Modifier.fillMaxSize()) {
    MeshGradientBackground(Modifier.fillMaxSize(), palette = MeshGradients.Ocean)
    KButton("축하하기", onClick = { confetti.burst(Offset(540f, 1200f)) })
    ConfettiHost(confetti, Modifier.fillMaxSize())
}

val stagger = rememberKStaggerState()
LazyColumn(state = listState) {
    itemsIndexed(todos) { i, todo -> KListItem(todo, Modifier.staggerIn(i, stagger).scrollTransform(listState, i)) }
}
```

### 수정자 순서

순서를 바꿔도 클릭은 동작한다. 다만 아래 순서를 따라야 움직임이 배경과 리플까지 함께 적용되고, 겉에 그리는 효과가 잘리지 않는다.

```kotlin
val shape = RoundedCornerShape(20.dp)
Box(
    Modifier
        .jellyPress()          // 1. 움직임: jellyPress, magnetic, motionTilt, shake, kineticaEffects
        .glowBorder(shape)     // 2. 겉에 그리는 효과: glowBorder, sparkle, kBadge, skeleton. 잘리지 않게 clip보다 앞에 둔다
        .glass(shape = shape)  // 3. 배경: glass, kSurface, background
        .clip(shape)           // 4. 자르기
        .clickable { open() }  // 5. 입력: clickable, kClickable, kContextMenu, tooltipOnLongPress
        .padding(16.dp),
) { Text("항목") }
```

움직임 효과를 `background` 뒤에 두면 배경은 그대로 있고 안쪽 내용만 움직인다. `expandSource`, `imageViewerSource`, `coachMarkTarget`, `flyToSource`는 위치만 알리므로 어디에 두어도 된다.

### 애니메이션을 멈추는 때

- 시스템의 '애니메이션 삭제' 설정이 켜져 있으면 반복, 기울기, 컨페티 효과는 모두 멈춘다. 모달, 원형 드러내기, 공유 요소 전환은 바로 바뀌고, 차례 등장과 +1 떠오름, 날아가기는 생략한다.
- 반복 효과(`glowBorder`, `sparkle`, `skeleton`, 글자 효과, 움직이는 배경, 로딩 링, 입력 중 점, 스토리 링 회전)는 요소가 창 밖으로 스크롤되어 나가 있는 동안 멈춘다.
- 배경 효과는 앱이 백그라운드로 가면(Lifecycle STARTED 미만) 멈추고, `motionTilt`는 센서를 놓는다.

움직이는 배경(`MeshGradientBackground`, `AuroraBackground`)은 시트나 다이얼로그가 열려 있는 동안 멈춥니다. 뒤 화면이 블러에 가려 움직임이 보이지 않는데 블러를 매 프레임 다시 할 필요가 없기 때문입니다. 직접 만든 움직이는 배경도 같은 규칙을 따르게 하려면 `KineticaScaffold`의 `background` 안에서 `shouldPauseBackground()`를 읽어 시계를 멈춥니다.

## 넓은 화면과 폴더블

화면 폭에 따라 배치를 바꾸는 컴포넌트와 창 정보 도우미다. 두 칸으로 나누는 기준 폭은 `KAdaptiveDefaults.TwoPaneMinWidth`(600dp)이고, 컴포저블이 받은 폭으로 판단하므로 멀티 윈도우에서도 맞게 동작한다.

| 컴포넌트 | 하는 일 | 주요 파라미터 |
| --- | --- | --- |
| `KListDetail` | 좁으면 상세가 옆에서 밀려 들어오고, 넓으면 목록과 상세를 두 칸으로 놓는다. 반쯤 접은 폴더블에서는 두 칸 경계를 힌지에 맞춘다. 접고 펼쳐도 선택과 스크롤 위치가 유지된다 | `selected`, `onBack`, `listWidth`(기본 360dp), `emptyDetail`, `detailBackground` |
| `KNavigationRail` | 화면 시작 쪽에 세우는 내비게이션 레일. 인디케이터가 세로로 늘어났다 줄며 이동한다 | `items`, `header` |
| `KAdaptiveNavigation` | 좁으면 아래 탭 바, 넓으면 레일로 바꾼다. 바뀌어도 내용을 다시 만들지 않으므로 스크롤 위치가 그대로다 | `tabs`, `railHeader` |
| `KTabletopLayout` | 탁자 모드(가로로 반쯤 접음)일 때 `top`을 접힌 선 위에, `bottom`을 아래에 놓는다. 영상과 조작부처럼 보는 내용과 만지는 내용을 나눌 때 쓴다. 탁자 모드가 아니면 `top`을 내용 높이대로 위에 두고 `bottom`이 나머지를 채운다 | `minPaneHeight` |
| `rememberKWindowLayout()` | 지금 창의 폭과 높이, 폭 구간(`KWindowWidth.Compact`, `Medium`, `Expanded`), 자세(`KPosture.Flat`, `Book`, `Tabletop`), 힌지 위치를 돌려준다. `columns(minCellWidth, maxColumns)`로 격자 열 수를 구한다 | `KWindowDefaults.MediumMinWidth`(600dp), `ExpandedMinWidth`(840dp) |
| `Modifier.kReadableWidth()` | 글, 입력 양식, 설정 카드처럼 한 줄로 읽는 내용이 넓은 화면에서 끝없이 늘어나지 않게 폭을 줄여 가운데에 둔다 | `maxWidth`(기본 `KWindowDefaults.ReadingMaxWidth`, 640dp) |

```kotlin
var open by rememberSaveable { mutableStateOf<String?>(null) }
KListDetail(open, onBack = { open = null }, list = {
    LazyColumn { items(mails) { KListItem(it.subject, onClick = { open = it.id }) } }
}) { id -> Text("메일 $id", Modifier.kReadableWidth()) }

val window = rememberKWindowLayout()
LazyVerticalGrid(GridCells.Fixed(window.columns(minCellWidth = 320.dp))) { items(20) { KCard { Text("카드 $it") } } }
```

- `KListDetail`의 상세 상태는 지금 고른 항목 것만 기억하므로 예전 항목을 다시 고르면 처음 상태로 열린다. 두 칸 전환에 쓰려고 붙들어 두는 상세 내용도 최근 8개 항목까지다.
  `detailBackground`에 목록과 같은 배경을 한 번 주면 상세마다 배경을 칠하지 않아도 된다. 두 칸 배치에서는 상세가 바뀌어도 배경이 그대로 있고, 좁은 화면에서는 상세와 함께 밀려 들어온다.
- `KSheet`는 넓은 화면에서 640dp 폭으로 가운데에 놓이고, 세로로 반쯤 접은 폴더블(책 모드)에서는 접힌 선 시작 쪽 칸에 놓인다.
- 펼친 `KMiniPlayer`는 탁자 모드에서 앨범 아트를 접힌 선 위, 조작부를 아래에 둔다. 넓고 낮은 화면에서는 아트와 조작부를 옆으로 놓는다.
- `rememberKWindowLayout()`은 창 크기를 읽으므로 계측 테스트에서 넓은 화면을 흉내 낼 때는 `ForcedSize`가 아니라 `WindowSize`로 창 크기를 바꿔 시험한다.

쇼케이스의 메일 앱(앱 화면 예 → 메일)이 `KListDetail`을, 음악 앱이 `KFloatingTabBar`와 그 `accessory`에 넣은 `KMiniPlayerBar`를 쓴다.

## 호스트 앱에서 쓸 때

### Material 3와 함께

- Material 3 `Card(onClick)`, `Surface(onClick)`, `Button`, `ListItem`은 누를 때 `LocalIndication`이 아니라 컴포넌트가 직접 만든 `ripple()`을 쓴다. 그래서 `KineticaTheme` 안에서도 리플이 나온다.
  리플 없이 쓰려면 `onClick` 없는 `Card` 안에 `Modifier.kClickable(shape)`을 붙이거나 `KSurface(onClick = …)`, `KCard(onClick = …)`를 쓴다.
- `MaterialTheme`은 `LocalIndication`을 리플로 다시 넣는다. 그래서 `KineticaTheme` 안쪽에서 `MaterialTheme`을 다시 감싸면 그 아래는 리플로 돌아간다.
  `MaterialTheme`은 늘 `KineticaTheme` 바깥에 두고, 화면 일부만 Material 색을 바꿀 때는 `MaterialTheme` 바로 안에 `KineticaTheme`을 한 번 더 감싼다.
- Material `Card`에 `Modifier.glass()`를 붙이면 `Card`가 자기 배경색으로 덮어 글래스가 보이지 않는다. `CardDefaults.cardColors(containerColor = Color.Transparent)`를 주거나 `Box`에 붙인다.
- `KineticaEditor`는 편집기 색을 쓰려고 안에서 `MaterialTheme`을 다시 감싸지만 바깥의 `LocalIndication`을 다시 넣는다. 그래서 `toolbarEnd`나 슬롯에 넣은 호스트 버튼은 편집기 밖과 같은 터치 피드백을 쓴다.

### `KineticaScaffold` 밖에서 부를 때

`KineticaScaffold` 안에서 부르면 다이얼로그, 시트, 메뉴, 토스트가 스캐폴드 위층에 그려지고 뒤 화면 전체를 블러로 비춘다.
호스트의 Material `Scaffold`나 그냥 `Column` 안에서 불러도 된다. 이때 다이얼로그, 시트, 메뉴, 펼침 메뉴, 기능 안내, 펼친 카드는 전체 화면 Dialog 창으로,
토스트, 스낵바, 아일랜드는 뒤 화면 터치를 막지 않는 Popup 창으로 뜨므로 부른 자리의 배치를 깨뜨리지 않는다.

별도 창으로 뜬 다이얼로그와 시트는 호스트 화면을 기록할 수 없으므로 Android 12 이상에서 시스템 창 블러(`blurBehindRadius`)로 뒤를 흐린다.
기기 설정이나 절전 모드로 창 블러가 꺼져 있으면(일부 기기는 기본으로 꺼져 있다) 블러 없이 어둡게만 하고, 판은 뒤 아이콘이 비치지 않는 불투명한 색으로 칠한다.
뒤 화면까지 같은 글래스 느낌을 내려면 화면을 `KineticaScaffold`로 감싼다.

### 직접 만든 글래스 판 안에 글래스 놓기

호스트가 `Modifier.glass`나 `Modifier.kSurface`로 직접 만든 판 안에 글래스 카드를 놓으면, 카드는 판을 건너뛰고 판 뒤 배경을 비춰 판에 구멍이 난 것처럼 보인다.
판 안쪽 내용을 `KGlassLayer { }`로 감싸면 키트 팝업과 같이 판 위 한 겹으로 그린다. 판 자신의 수정자는 `KGlassLayer` 바깥에 둔다.

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

### 영상, 지도, 호스트 View 위의 글래스

`AndroidView`로 넣은 EditText, RecyclerView, ScrollView, WebView, TextureView는 글래스 안에서 터치, 스크롤, 입력이 그대로 동작하고, 배경에 두면 블러로 비친다.
SurfaceView(지도, 영상, 카메라)는 창과 다른 표면에 그려져 기록되지 않는다. 그 위의 글래스는 블러 없이 색만 덮이므로, 블러가 필요하면 TextureView를 쓰거나 `backdrop = null`로 fallback 색을 쓴다. 지도 위 핀처럼 블러가 필요 없으면 단색 표면이 낫다.

### 시스템 바 아이콘

- `KineticaScaffold`는 단색과 톤 표면일 때 상태 표시줄과 내비게이션 바 아이콘을 표면 밝기에 맞춘다(`systemBarIcons`).
- 바텀 시트가 열려 콘텐츠가 뒤로 물러나면 `KineticaScaffold`의 `backdropColor`(기본 검정)가 상태 표시줄 뒤에 드러난다. 콘텐츠가 절반 넘게 물러난 동안에는 `systemBarIcons` 값과 상관없이 상태 표시줄 아이콘을 `backdropColor` 밝기에 맞추고, 시트가 내려가면 되돌린다.
  내비게이션 바 아이콘은 건드리지 않는다. `backdropColor`가 투명에 가까우면(알파 0.5 미만) 무엇이 비칠지 모르므로 바꾸지 않는다.
- 사진 뷰어, 펼친 미니 플레이어, 온보딩은 떠 있는 동안 아이콘을 바꾸고 닫으면 되돌린다. 별도 창으로 뜨면 그 창에 적용한다. 배경을 호스트가 정하는 화면(`KExpandHost` 상세 등)에는 `KSystemBarIcons(light = true)`를 둔다.
- 요청이 걸려 있는 동안 호스트가 창의 아이콘 설정을 직접 바꾸면, 요청이 빠질 때 처음 기억한 값으로 덮어쓰지 않고 호스트 값을 둔다.
- 기본 `enableEdgeToEdge()`는 기기에 따라(삼성 등) 제스처 내비게이션에서도 반투명 막을 깔아 화면 아래에 흰 띠가 보인다. `enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))`로 부르고 API 29 이상에서 `window.isNavigationBarContrastEnforced = false`로 둔다.

### XML(View) 화면

Compose 코드 없이 `com.naury.kinetica.ui.view`의 진입점을 쓴다.

```kotlin
val sheet = KSheets.show(this, R.layout.sheet_share, KPopupOptions(title = "공유"))
sheet.contentView?.findViewById<Button>(R.id.close)?.setOnClickListener { sheet.dismiss() }
KDialogs.show(this, confirmView)
KToasts.show(this, "저장했습니다")
```
```java
KPopupHandle sheet = KSheets.show(this, R.layout.sheet_share);
sheet.setOnDismissListener(() -> refresh());
```
```xml
<com.naury.kinetica.ui.view.KSurfaceView
    android:layout_width="wrap_content" android:layout_height="wrap_content"
    app:kinetica_surfaceStyle="cleanLight" app:kinetica_cornerRadius="20dp"> … </com.naury.kinetica.ui.view.KSurfaceView>
```

- 액티비티는 `ComponentActivity`(AppCompatActivity, FragmentActivity 포함)여야 한다. Fragment에서는 `requireActivity()`와 `KPopupOptions(lifecycleOwner = viewLifecycleOwner)`를 넘긴다.
- `KPopupOptions`로 표면(`surface`), TalkBack 창 이름(`title`), 뒤로 가기로 닫을지(`cancelable`), lifecycle 소유자를 정한다.
- 시트와 다이얼로그는 액티비티 화면 위에 겹친 레이어에 그려지고 그 아래 View를 블러로 비춘다(API 31 이상). 키보드가 올라오면 함께 올라오고, 뒤로 가기로 닫힌다(`cancelable = false`면 닫히지 않음).
- 화면 회전 등으로 액티비티가 다시 만들어지면 열린 창은 애니메이션 없이 정리된다. 회전 뒤에도 열어 두려면 `onSaveInstanceState`에 열림 여부를 저장했다가 다시 띄운다.
- 메인 스레드에서만 부른다.
- `KSurfaceView`의 `app:kinetica_surfaceStyle`은 글래스 값(`liquid`, `acrylic` 등)과 함께 `cleanLight`, `cleanDark`, `solidOneUi`, `solidOneUiDark`, `solidMaterial`, `solidMaterialDark`, `tonal`, `tonalDark`를 받는다.
  코드에서는 `surfaceStyle = SurfaceStyles.CleanLight`처럼 넘긴다. 안쪽 글자 색은 `contentColor`와 `setOnContentColorChangeListener`로 받는다. 앱 테마에서 기본 스타일을 정하려면 `kinetica_surfaceViewStyle` 속성을 쓴다.

## 성능

실기기(Galaxy Z Fold7 메인 화면, 1968×2184, 120Hz, release 빌드)에서 쇼케이스 화면을 위아래로 스크롤하며 잰 값이다. 측정은 글래스 표면에서 했고, 단색과 톤 표면은 배경을 기록하지 않으므로 이보다 가볍다.

| 화면 | 프레임 p50 | p90 | 끊김(기한 놓침) |
| --- | --- | --- | --- |
| 첫 화면(글래스 카드 9개, 움직이는 배경) | 13 ms | 14 ms | 0.34% |
| UI 키트(바, 탭 바, 카드, 사진 위 글래스) | 13 ms | 15 ms | 0.22% |
| 모션 효과 | 12 ms | 14 ms | 0.22% |
| 컴포넌트(컨트롤 30여 개, 휠 피커, 캐러셀) | 13 ms | 17 ms | 0.39% |
| 컴포넌트 효과 탭 스크롤 | 12 ms | 14 ms | 0.09% |
| 물결 왜곡을 누른 채 끌기 | 12 ms | 12 ms | 0% |
| 파티클(눈, 불꽃놀이, 거품, 불꽃) | 13 ms | 13~14 ms | 0% |
| 방울 묶음 펼치기와 접기 | 13 ms | 13 ms | 0% |
| 홀로그램 카드를 끄는 중 | 17 ms | 18 ms | 0~0.45% |
| 앱 화면 예(음악, 건강) | 7~13 ms | 9~14 ms | 0~0.11% |
| 메일 두 칸(목록, 본문 스크롤) | 17 ms | 18~19 ms | 0~0.08% |

p50이 120Hz 한 프레임(8.3 ms)보다 큰 것은 입력부터 화면 표시까지 걸린 시간이라서다. 위 측정에서 모두 초당 118프레임 안팎으로 그렸다.
메일 두 칸과 홀로그램 카드는 GPU 시간 p50이 11~12 ms로 다른 화면보다 길지만, 앞 프레임과 겹쳐 그려지므로 끊김으로 이어지지 않았다.

- 글래스는 배경을 블러 반경에 맞춰 낮은 해상도로 기록해 흐린 뒤 늘려 그린다(블러 12px 미만은 원래 해상도, 48px 미만은 1/2, 그 이상은 1/3). 흐린 결과라 화질 차이는 거의 없고,
  목록에 글래스가 많을 때 새 항목이 들어오며 생기던 GPU 메모리 할당이 줄어 컴포넌트 화면 끊김이 5.15%에서 0.1% 안팎으로 줄었다.
- 페이저, 지연 목록, 이동 애니메이션은 글래스를 다시 그리지 않고 부모 레이어만 옮긴다. 글래스는 위치가 바뀌면 배경 레이어의 이동량만 고쳐서, 다시 그리지 않고도 놓인 자리의 배경을 비춘다.
- 그래도 글래스 하나마다 블러 층을 하나 더 그리므로, 작은 버튼마다 글래스를 쓰기보다 큰 카드나 바에 쓰는 편이 낫다. 버튼은 `Filled`나 `Tonal` 변형을 권한다.
- 효과 값은 그리기 단계에서 읽으므로 애니메이션 중에 재구성하지 않는다. `LazyColumn` 300행에 효과, 글래스, 배지, 로딩 표시, 차트, 말풍선 등을 놓아도 애니메이션 중에 행을 다시 구성하지 않는 것을
  `HostUsageTest`, `KFormsTest`, `KButtonsChipsCardsTest`, `KFeedbackTest`, `KDataChartsTest`, `KMotionExtrasTest`가 확인한다.
- 소프트웨어 렌더러(SwiftShader 에뮬레이터)에서는 블러가 매우 느리다. 에뮬레이터는 `-gpu host`로 실행한다.

### 영상 위 글래스

영상 통화처럼 `background`에 TextureView 영상을 두고 그 위에 글래스 바를 띄우면, 블러는 영상 프레임이 바뀔 때만 다시 한다(30fps 영상이면 120Hz 화면에서 네 프레임에 한 번).

- 글래스는 배경 기록과 자기 레이어 크기가 그대로면 레이어를 다시 기록하지 않는다. 바 위 숫자 굴림이나 테두리 애니메이션 때문에 글래스가 다시 그려져도 HWUI가 지난 블러 결과를 그대로 쓴다.
  배경을 기록하는 요소도 자기 레이어에 두어, 형제나 부모가 다시 그려질 때 배경 기록을 같은 내용으로 다시 하지 않는다(`GlassRedrawCostTest`).
- 바는 기본으로 배경과 콘텐츠를 함께 비춘다. 그래서 콘텐츠 안에서 매 프레임 움직이는 요소(말하는 사람 테두리 등)가 있으면 바 블러도 매 프레임 다시 한다.
  바 뒤로 보일 것이 영상뿐이면 바에 `LocalScaffoldBackground`를 비추게 한다. 스타일이 같으면 콘텐츠 글래스와 블러 기록 하나를 함께 쓴다.

```kotlin
bottomBar = {
    CompositionLocalProvider(LocalGlassBackdrop provides LocalScaffoldBackground.current) { CallDock() }
}
```

- 블러 반경은 모양을 보고 고르면 된다. Fold7 영상 통화 화면에서 바 블러를 12, 18, 24, 32dp로 바꿔 재도 프레임 p50 9~11 ms, p90 13~16 ms로 차이가 없었다.
  기록 해상도는 반경에 맞춰 알아서 낮춘다(밀도 2.6 기준 약 5dp 미만은 원래 해상도, 18dp 미만은 1/2, 그 이상은 1/3). 비용을 가르는 것은 반경이 아니라 블러를 몇 프레임마다 다시 하느냐다.
- `background` 칸 안의 무엇이든 바뀌면 배경 기록 전체가 다시 그려진 것으로 처리되어, 그 프레임에 모든 글래스 블러를 다시 한다. 말하는 사람 테두리나 말하기 고리처럼 매 프레임 움직이는 장식은 `background`가 아니라 `content` 층에 같은 자리로 올린다. `background`에는 영상과 정적인 바탕만 둔다.
- 블러를 몇 프레임에 한 번으로 솎지 않는다. 배경을 초당 30번만 바꾸게 했더니 블러가 바뀌는 프레임에 비용이 몰려 그 프레임이 마감을 놓쳤다(Fold7 스크롤 끊김 0.1%에서 8~13%로 늘었다).

## 통화

영상 통화와 음성 통화 화면을 만드는 부품과, 그 부품만으로 만든 완성 화면이다. 키트는 UI와 상호작용만 맡고 영상과 오디오는 다루지 않는다. 영상은 호스트가 슬롯에 `AndroidView`(TextureView, SurfaceView)나 Compose 렌더러로 넣는다.

부품은 호스트 상태(`StateFlow`)를 키트 데이터 클래스로 바꾸지 않고 원시 값, 람다, 슬롯으로 받는다. 완성 화면 `KCallScreen`만 상태 하나(`KCallState`)와 콜백 묶음(`KCallActions`)을 받는다.

### 부품

| 컴포넌트 | 하는 일 | 주요 파라미터 |
| --- | --- | --- |
| `KCallControlButton` | 원 안 아이콘과 아래 이름표. 켜짐은 원을 채우고, 진행 중은 둘레가 맥박처럼 뛰고, 끊기와 받기는 늘 그 색으로 채운다. TalkBack은 '마이크, 꺼짐, 새 항목 2개'처럼 한 번에 읽는다 | `checked`(null이면 상태 없는 버튼), `checkedIcon`, `tone`, `pending`, `badge`, `size`, `showLabel` |
| `KCallDock` | 버튼 막대. 폭이 모자라면 원을 48dp로 줄이고, 그래도 넘치면 필수가 아닌 버튼을 끊기 앞 '더보기'로 접는다. '더보기' 배지는 접힌 버튼 배지의 합이다 | `items: List<KCallDockItem>`, `onMore`(null이면 시트를 직접 연다), `onItemPositioned`(버튼 위치), `vertical`. 버튼을 직접 넣는 `KCallDock { }`도 있다 |
| `KCallTopBar` | 제목, 연결 품질, 통화 시간, 인원, 녹화와 암호화 배지를 담은 상태 캡슐과 앞뒤 버튼. 글자가 커져도 끝 버튼은 밀려나지 않고, 부가 칩은 다 들어갈 때만 보인다 | `startedAtMillis`, `participantCount`, `quality`, `navigation`, `chips`, `actions` |
| `KParticipantTile` | 영상 슬롯, 카메라가 꺼지면 아바타와 말하기 물결, 이름표, 마이크 꺼짐과 손 들기와 약한 연결 표시, 말하는 동안 글로우 테두리. 누르면 `onClick`, 길게 누르면 고정한다 | `video`, `speaking`, `audioLevel: () -> Float`, `mirror`, `screenShare`, `nameTag`, `layer` |
| `KParticipantGrid` | 인원과 화면 비율에 맞춘 격자, 1:1 전체 화면과 내 작은 창, 크게 보기와 한 줄 띠, 9명이 넘으면 쪽 넘김. 보이는 칸 근처만 그린다 | `participants`와 `key`(호스트 목록 그대로), `focusKey`, `selfKey`, `contentPadding`, `layer`, `tile` |
| `KSelfView` | 끌어서 놓으면 가까운 모서리에 붙는 내 영상 창 | `rememberKSelfViewState()`, `size`, `contentPadding` |
| `KCallStatusView` | 연결 중, 호출 중, 대기, 재연결, 종료, 실패 화면. 펄스 링과 아바타, 문구(바뀌면 TalkBack이 읽는다), 단계에 맞는 버튼 | `status`, `title`, `message`, `detail`, `onCancel`, `onRetry`, `onClose`, `summary` |
| `KIncomingCall`, `KIncomingCallScreen` | 걸려 온 전화. 버튼 방식과 밀어서 받기 방식, 영상 통화의 음성과 영상 받기, 빠른 답장 시트 | `onAccept`, `onDecline`, `onAcceptVideo`, `onMessage`, `quickReplies`, `style` |
| `KCallBanner`, `KCallBubble`, `KRaisedHandPill` | 화면 공유 경고와 오류 배너, 버튼 위에 꼬리를 단 말풍선, 손 든 동안의 캡슐 | `liveRegion`, `anchor`(`onItemPositioned` 값), `durationMillis` |
| `KReactionOverlay`, `KReactionPicker` | 아래에서 떠오르는 이모지 반응(최대 8개)과 반응 고르기 | `rememberKReactionState().send(emoji, from)` |
| `KParticipantRow`, `KAudioRouteList` | 참가자 목록 행(상태를 글자로도 쓴다)과 오디오 출력 고르기 | `onPin`, `onMute`, `onRemove`, `routes`, `selectedId` |
| `KAudioLevelIndicator`, `KNetworkQualityIndicator`, `KCallTimer`, `KOngoingCallPill` | 소리 크기 막대와 고리, 연결 품질 막대, 굴러가는 통화 시간, 다른 화면 위 통화 캡슐 | `level: () -> Float`, `quality`, `startedAtMillis` |
| `rememberKCallChromeState`, `Modifier.kCallChromeTouches` | 영상 통화의 바 자동 숨김. 마지막 터치 뒤 4초(접근성 권장 시간이 더 길면 그 값), TalkBack이 켜져 있으면 숨기지 않는다 | `autoHide`, `holdVisible`, `timeoutMillis` |
| `rememberKStableSpeaker` | 발표자 보기에서 1.5초 넘게 말해야 큰 화면을 바꾼다 | `speakingKey`, `delayMillis` |

### 완성 화면

```kotlin
KCallScreen(
    state = KCallState(
        phase = KCallPhase.Connected,
        participants = people.map { KCallParticipant(it.id, it.name, isLocal = it.isLocal, isMuted = it.muted, isVideoOn = it.cameraOn, isSpeaking = it.speaking) },
        startedAtMillis = call.connectedAt,
    ),
    actions = KCallActions(onEnd = vm::hangUp, onToggleMute = vm::toggleMute, onToggleCamera = vm::toggleCamera, onShareScreen = ::requestShare),
    video = { p -> VideoRenderer(p.id) },
    audioLevel = { id -> levels.value[id] ?: 0f },
)
```

넘기지 않은 콜백의 버튼은 화면에 없다. `KCallState.isVideoCall`이 false면 음성 화면을, 참가자 수에 따라 1:1과 그룹 배치를 고른다. 참가자, 오디오 출력, 반응, 더보기 시트가 들어 있고, 입퇴장과 손 들기는 `island`로 알린다. 일부만 바꾸려면 `dock`(기본 버튼 목록을 받는 슬롯), `extraDockItems`, `topBarActions`, `nameTag`를 쓴다. 화면 속 화면 창에 있으면 `pictureInPicture = true`로 한 사람만 그린다. 소리 크기를 데이터 클래스에 넣지 않고 `audioLevel` 람다로 받는 것은, 초당 수십 번 바뀌는 값 때문에 화면 전체를 다시 구성하지 않기 위해서다.

이 밖에 입장 전 화면 `KCallLobby`(미리보기 슬롯, 마이크 소리 크기, 마이크와 카메라 켜고 끄기, 이름, 참여 버튼)와 걸려 온 전화 화면 `KIncomingCallScreen`이 있다.

### 부품으로 직접 조합하기

영상은 `background`에, 이름표와 글로우 테두리는 `content`에 같은 자리로 그린다. 같은 `KParticipantGridState`를 두 층에 넘기면 타일이 같은 자리로 함께 움직인다. 바는 `LocalScaffoldBackground`를 비춰 영상만 블러한다([영상 위 글래스](#영상-위-글래스)).

```kotlin
val chrome = rememberKCallChromeState(autoHide = isVideo, holdVisible = sheetOpen || reconnecting || sharing)
val grid = rememberKParticipantGridState()
val tiles = @Composable { layer: KCallLayer ->
    KParticipantGrid(people, key = { it.id }, state = grid, focusKey = pinnedId, selfKey = me.id, contentPadding = stagePadding, layer = layer) { p, _ ->
        KParticipantTile(p.name, video = if (p.cameraOn) ({ VideoRenderer(p.id) }) else null, speaking = p.speaking,
            audioLevel = { levels.value[p.id] ?: 0f }, onClick = { if (chrome.isVisible) pin(p.id) else chrome.show() }, layer = layer)
    }
}
KineticaScaffold(
    Modifier.kCallChromeTouches(chrome),
    background = { tiles(KCallLayer.Video) },
    bottomBar = {
        CompositionLocalProvider(LocalGlassBackdrop provides LocalScaffoldBackground.current) {
            AnimatedVisibility(chrome.isVisible) { KCallDock(dockItems, onItemPositioned = { key, rect -> if (key == "audio") audioAnchor = rect }) }
        }
    },
) { tiles(KCallLayer.Overlay) }
```

영상 View는 Compose 위에 그려질 수 있으므로 상단 배너가 있으면 그 높이만큼 `contentPadding`의 위쪽을 늘려 무대를 내린다. 숨김을 막아야 하는 조건(시트, 재연결, 미디어 오류, 내 화면 공유, 다른 통화가 오디오를 가져감, 말풍선 표시, 상대 없음)은 모두 `holdVisible`에 넣는다.

### 통화 화면 성능

- 소리 크기, 글로우 테두리, 말하기 물결은 그리기 단계에서 읽으므로 매 프레임 바뀌어도 다시 구성하지 않는다. 소리 크기는 늘 람다로 넘긴다.
- 띠와 쪽 넘김은 보이는 칸 근처의 타일만 컴포지션에 남겨 영상 렌더러 수를 보이는 만큼으로 줄인다. 보이는 칸이 바뀔 때만 다시 구성한다.
- 바 자동 숨김은 터치마다 상태를 바꾸지 않고 숨김 시계만 다시 시작하므로, 만지는 동안 화면을 다시 구성하지 않는다.
- `KCallScreen`은 영상 위 표면으로 테마 표면의 어두운 짝(`surface` 기본값)을 써 글자 대비를 지킨다.

## 쇼케이스에서 확인하기

`sample-showcase` 첫 화면은 UI 키트를 가장 크게 두고, 그 아래에 고급 기능(대시보드 보드, 화면 만들기, 서버 화면, XML 화면)을 작게 둔다. 표면 종류와 프리셋, 밝기, 형태(디자인 언어)는 모든 화면 상단 바의 모양 버튼이 여는 시트 한 곳에서 고르고, 모든 데모가 그 값을 따른다. UI 키트를 누르면 분류별 허브가 열린다.

- **표면과 디자인**: 같은 컴포넌트를 글래스, 단색, 톤 프리셋마다 나란히 그려 비교하고, '형태' 줄에서는 지금 표면 그대로 디자인 언어 6종을 나란히 그린다. 프리셋이나 형태의 적용 버튼을 누르면 앱 전체 모양이 그 값으로 바뀐다. 아래에서 다이얼로그, 바텀 시트, 토스트, 탭 바, 컨트롤, 당겨서 새로 고침을 직접 눌러 본다.
- **앱 화면 예**: 컴포넌트를 조합해 만든 실제 앱 같은 화면 14개다. 형태는 늘 모양 설정을 따르고, 표면은 기본으로 설정을 따르되 목록 위의 '앱 추천'을 고르면 앱마다 추천하는 표면을 쓴다. 칸 오른쪽 위에 앱의 추천 표면을 적어 두었다. 사진이 주인공인 소셜 피드는 표면이 화면 설계의 일부라 단색으로 고정했다. 전화 앱의 걸려 온 전화와 음성 통화는 완성 컴포넌트로 만들었고, 영상 통화는 상단 버튼으로 `KCallScreen` 판과 부품을 직접 조합한 판을 바꿔 볼 수 있다.
- **컴포넌트**: 기본, 입력, 미디어, 효과, 버튼·칩·카드, 내비게이션·오버레이, 피드백·로딩, 데이터·도메인, 소셜·모션, 통화 탭에 이 문서의 컴포넌트가 모두 있다. 허브에서 종류를 누르면 그 탭으로 열린다.
- **모션과 화면 전환**: 모션 효과(효과 수정자와 [33 모션 효과](33-effects.md)의 프리셋), 화면 이동, 장면 전환.
  - 화면 이동: 전환 22종을 칩으로 골라 목록에서 상세, 더 깊은 화면까지 쌓아 본다. 쇼핑 상세(Push, Parallax), 설정 하위 화면(CardStack), 새 글(SheetStack 두 겹), 전체 화면 다이얼로그(`cover(Bottom)`), 온보딩에서 홈(Uncover), 앱 열기(Zoom), 스토리(Cube), 카드(Flip), 보정 전후(Wipe), 미디어(BlurDissolve), 누른 버튼에서 퍼지는 테마 전환(원 reveal)과 축하 화면(별 reveal) 예가 있다. 그 아래에서 `KTransitionContent` 탭 전환(TabSlide, SharedAxisX, FadeThrough)과 컨테이너 변환(Card, `listRow`, `fab`, 사진에서 `KImageViewer`)을 본다.
  - 장면 전환: 홈, 상품, 결제 세 화면 번들을 `RemoteKineticaPlayer(bundle, sceneId)`로 재생하며 전환 14종과 방향을 고른다. 뒤로 가기는 `forward = false`다. 중간 프레임 탭에서는 `SceneTransition(from, to, spec, progress)`를 슬라이더로 멈춰 본다.
