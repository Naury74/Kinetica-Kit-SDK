<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/images/logo-dark.png">
    <img src="docs/images/logo-light.png" alt="Kinetica" width="420">
  </picture>
</p>

<p align="center">
  <a href="https://central.sonatype.com/artifact/com.naurylab/kinetica-ui"><img src="https://img.shields.io/maven-central/v/com.naurylab/kinetica-ui?label=Maven%20Central&color=D6338A" alt="Maven Central"></a>
  <img src="https://img.shields.io/badge/minSdk-26-3DDC84" alt="minSdk 26">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue" alt="License"></a>
</p>

**Kinetica**는 Jetpack Compose 앱에 붙여 쓰는 UI와 모션 키트입니다.
버튼, 입력란, 다이얼로그, 바텀 시트, 토스트, 차트, 통화 화면까지 200여 개의 컴포넌트와 효과가 들어 있고, 배경이 비치는 글래스, 깔끔한 단색, 브랜드 색을 옅게 섞은 톤 가운데 앱에 맞는 표면을 고르면 모든 컴포넌트가 그 모양을 따릅니다.
사용자가 홈 화면 위젯을 직접 배치하는 보드, 앱 안에서 화면을 만드는 편집기, 서버가 내려준 화면을 그리는 재생기도 함께 제공합니다.

> **알파 버전**: 1.0.0 정식 버전 전(`-alpha`, `-beta`)에는 공개 API가 바뀔 수 있습니다. 바뀐 내용은 [CHANGELOG.md](CHANGELOG.md)에 적습니다.

<table>
  <tr>
    <td align="center" width="33%"><img src="docs/images/showcase-surface-glass.png" width="220" alt="글래스 표면의 송금 앱"><br><b>글래스</b><br>배경이 블러로 비치는 표면</td>
    <td align="center" width="33%"><img src="docs/images/showcase-solid.png" width="220" alt="단색 표면의 송금 앱"><br><b>단색</b><br>같은 화면을 깔끔한 단색으로</td>
    <td align="center" width="33%"><img src="docs/images/showcase-tonal.png" width="220" alt="톤 표면의 송금 앱"><br><b>톤</b><br>브랜드 색을 옅게 섞은 표면</td>
  </tr>
</table>

## 기능

- **컴포넌트**: 버튼, 칩, 스위치, 슬라이더, 떠오르는 라벨 입력란, 인증번호, 태그, 휠 피커, 달력, 목록과 설정 행, 카드, 캐러셀, 앱 바, 탭 바, 서랍, 다이얼로그, 여러 높이에 멈추는 시트, 드롭다운 메뉴, 토스트, 스낵바, 다이내믹 아일랜드식 알림, 스켈레톤, 차트, 사진 뷰어, 채팅 입력창
- **표면 스타일**: 글래스 15종(Liquid, Acrylic, One UI, Vision, Mica 등), 단색 6종, 브랜드 색으로 만드는 톤. 앱 전체에 하나를 정하고 컴포넌트마다 바꿀 수도 있습니다
- **디자인 언어**: Cupertino, Material, One UI, Fluent, Brutal, Soft. 모서리, 그림자, 밀도, 컨트롤 모양, 움직임 빠르기를 한 번에 바꿉니다
- **모션 효과**: 젤리 누름, 글로우 테두리, 자석, 기울이면 따라 기우는 3D 카드, 홀로그램, 물결 왜곡, 숫자 롤링, 타자기, 컨페티, 파티클, 메시 그라디언트 배경
- **화면 이동**: 가장자리에서 밀어 뒤로 가는 push 내비게이션과 예측 뒤로 가기, 카드가 커지며 열리는 상세 화면, 공유 요소 전환, 전환 22종
- **통화 화면**: 참가자 격자와 고정 띠, 조작 막대, 상단 바, 반응, 손 들기, 걸려 오는 전화와 대기실 화면. `KCallScreen` 하나로 쓰거나 부품을 직접 조합합니다
- **위젯 보드**: 앱 사용자가 홈 화면 위젯을 옮기고 크기를 바꾸고 겹쳐 스택으로 만듭니다. 배치는 저장되고 넓은 화면 배치는 따로 기억합니다
- **화면 편집기와 서버 주도 화면**: 앱 안에서 Figma처럼 화면을 만들고 키프레임 애니메이션을 붙입니다. 서버가 내려준 JSON으로 앱 업데이트 없이 화면을 바꿉니다
- **접근성**: TalkBack, 키보드 조작, 아랍어 같은 RTL 언어, 큰 글꼴을 지원하고, 시스템의 '애니메이션 삭제'가 켜져 있으면 움직임을 줄입니다
- **폴드와 태블릿**: 목록-상세 두 칸, 내비게이션 레일, 탁자 모드 배치. 접거나 펼쳐도 상태가 이어집니다
- **XML View 화면**: Compose를 쓰지 않는 화면에서도 `KSheets`, `KDialogs`, `KToasts`, `KSurfaceView`로 시트, 다이얼로그, 토스트, 글래스 표면을 씁니다

## 화면

같은 컴포넌트를 형태(디자인 언어)만 바꿔 찍은 화면입니다.

<table>
  <tr>
    <td align="center" width="33%"><img src="docs/images/showcase-design-material.png" width="220" alt="Material 형태의 할 일 앱"><br><b>Material</b><br>행마다 떨어진 목록, 채운 인디케이터</td>
    <td align="center" width="33%"><img src="docs/images/showcase-design-fluent.png" width="220" alt="Fluent 형태의 할 일 앱"><br><b>Fluent</b><br>작은 반지름, 그림자 대신 테두리</td>
    <td align="center" width="33%"><img src="docs/images/showcase-design-brutal.png" width="220" alt="Brutal 형태의 할 일 앱"><br><b>Brutal</b><br>각진 모서리와 오프셋 그림자</td>
  </tr>
</table>

샘플 앱의 데모 화면입니다.

<table>
  <tr>
    <td align="center" width="33%"><img src="docs/images/showcase-kit.png" width="220" alt="UI 키트 허브"><br><b>UI 키트</b><br>분류별로 데모에 들어가는 허브</td>
    <td align="center" width="33%"><img src="docs/images/showcase-components.png" width="220" alt="컴포넌트"><br><b>컴포넌트</b><br>버튼부터 차트, 통화까지 열 개 탭</td>
    <td align="center" width="33%"><img src="docs/images/showcase-apps.png" width="220" alt="앱 화면 예"><br><b>앱 화면 예</b><br>전화, 메신저, 쇼핑, 송금 등 14개 앱</td>
  </tr>
  <tr>
    <td align="center" width="33%"><img src="docs/images/showcase-call.png" width="220" alt="전화 앱"><br><b>전화 앱</b><br>통화 기록, 키패드, 아일랜드, 영상 통화</td>
    <td align="center" width="33%"><img src="docs/images/showcase-navigation.png" width="220" alt="CardStack 화면 이동"><br><b>화면 이동</b><br>전환 22종, 뒤로 가기는 되감기</td>
    <td align="center" width="33%"><img src="docs/images/showcase-motion.png" width="220" alt="모션 효과"><br><b>모션 효과</b><br>글로우 테두리, 젤리, 자석, 숫자 롤링</td>
  </tr>
  <tr>
    <td align="center" width="33%"><img src="docs/images/showcase-board.png" width="220" alt="위젯 보드"><br><b>위젯 보드</b><br>사용자가 직접 배치하는 홈 화면</td>
    <td align="center" width="33%"><img src="docs/images/showcase-studio.png" width="220" alt="편집기"><br><b>화면 만들기</b><br>앱 안에서 쓰는 화면 편집기</td>
    <td align="center" width="33%"><img src="docs/images/showcase-server.png" width="220" alt="서버 화면"><br><b>서버 화면</b><br>JSON만 바꿔 화면 교체</td>
  </tr>
</table>

### 폴드와 태블릿

폭이 넓은 화면에서는 메일 예가 목록과 본문을 나란히 보여 주고, 전체 화면 플레이어는 앨범 아트와 조작부를 옆으로 놓습니다. 보드의 격자는 칸 폭을 160dp 이상으로 유지하며 열을 최대 6개까지 늘립니다. 아래는 Pixel 9 Pro Fold 에뮬레이터를 펼친 상태로 찍었습니다.

<table>
  <tr>
    <td align="center" width="50%"><img src="docs/images/showcase-fold-mail.png" width="360" alt="펼친 폴더블에서 메일 예"><br><b>메일</b><br>왼쪽에 목록, 오른쪽에 본문</td>
    <td align="center" width="50%"><img src="docs/images/showcase-fold-player.png" width="360" alt="펼친 폴더블에서 음악 플레이어"><br><b>음악 플레이어</b><br>앨범 아트와 조작부를 나란히</td>
  </tr>
  <tr>
    <td align="center" width="50%"><img src="docs/images/showcase-fold-home.png" width="360" alt="펼친 폴더블에서 쇼케이스 첫 화면"><br><b>쇼케이스</b><br>UI 키트 카드와 고급 기능 세 칸</td>
    <td align="center" width="50%"><img src="docs/images/showcase-fold-board.png" width="360" alt="펼친 폴더블에서 4열 보드"><br><b>위젯 보드</b><br>폭에 맞춰 4열로 늘어난 격자</td>
  </tr>
</table>

모두 `sample-showcase`를 Android 에뮬레이터에서 실행해 찍었습니다.

## 샘플 앱

소스를 받지 않고도 써 볼 수 있도록 샘플 앱(APK)을 [Releases](https://github.com/Naury74/Kinetica-Kit-SDK/releases/latest)에 올려 둡니다.

- [샘플 앱 내려받기 (kinetica-showcase.apk)](https://github.com/Naury74/Kinetica-Kit-SDK/releases/latest/download/kinetica-showcase.apk)
- Android 8.0(API 26) 이상에서 설치할 수 있습니다. 처음 설치할 때 "출처를 알 수 없는 앱" 설치를 허용해야 합니다.
- 체험용 키로 서명한 앱입니다. 새 버전이 덮어 설치되지 않으면 기존 샘플 앱을 지운 뒤 다시 설치하세요.

첫 화면의 **UI 키트**를 누르면 분류별 허브가 열립니다. 표면과 디자인, 앱 화면 예, 컴포넌트, 모션 효과, 화면 이동, 장면 전환이 있습니다. 첫 화면 아래 **고급 기능**에서는 대시보드 보드, 화면 만들기 편집기, 서버 화면, Compose 없이 만든 XML 화면을 엽니다.
표면 종류와 프리셋, 밝기, 형태는 상단 바의 모양 버튼에서 고르고, 고른 값을 모든 데모가 따릅니다.

## 설치

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.naurylab:kinetica-ui:1.0.0-alpha01")
}
```

UI 키트는 `kinetica-ui` 하나로 씁니다. 다른 기능은 필요한 모듈만 더합니다. 모든 모듈의 버전은 같습니다.

| 모듈 | 내용 |
| --- | --- |
| `kinetica-ui` | UI 키트, 표면 스타일, 디자인 언어, 효과, 통화 화면. 다른 Kinetica 모듈 없이 단독으로 씁니다 |
| `kinetica-render-compose` | 수정자 하나로 붙이는 효과 `Modifier.kineticaEffects`, XML용 `ViewEffects`, 화면 전환 `ContentTransitions`, `animateNumberAsState` |
| `kinetica-presets` | 효과 프리셋 `EffectPresets`, 편집기 기본 키트 `UiKit`, 모션 프리셋 |
| `kinetica-board-compose` | 위젯 보드 `KineticaBoard`, XML용 `KineticaBoardView` |
| `kinetica-player-compose` | 장면 재생 `KineticaPlayer`, 서버 화면 `RemoteKineticaPlayer`, XML용 `KineticaPlayerView` |
| `kinetica-editor-compose` | 화면 편집기 `KineticaEditor` |
| `kinetica-serialization` | 장면 JSON, 파일 저장소, 서버 문서를 받는 `RemoteSceneLoader` |
| `kinetica-export-android` | 장면을 PNG로 내보내기 |
| `kinetica-java` | Java 호스트용 정적 진입점 |

`kinetica-core`, `kinetica-history`, `kinetica-motion`, `kinetica-guides`, `kinetica-gestures`는 위 모듈이 함께 가져오는 기반 모듈입니다. gestures를 뺀 넷은 Android 없이 도는 Kotlin JVM 모듈이라 서버나 테스트에서도 쓸 수 있습니다. 의존 방향은 [아키텍처](docs/architecture.md)에 있습니다.

- minSdk 26(Android 8.0), Java 17, Kotlin 2.2 이상이 필요합니다. Compose BOM은 2026.09 이상을 권장합니다.
- `kinetica-ui`는 Material 3에 의존하지 않습니다. Material 3 앱과 함께 써도 됩니다.
- R8 규칙은 따로 넣지 않아도 됩니다. 배포한 artifact를 R8을 켠 release 앱으로 빌드해 확인합니다.

## 사용법

### 테마와 표면

화면 루트를 `KineticaTheme`으로 감싸고 표면 스타일을 고른 뒤 컴포넌트를 씁니다.

```kotlin
setContent {
    val dark = isSystemInDarkTheme()
    KineticaTheme(
        // 단색 표면. 글래스는 SurfaceStyles.Liquid, 톤은 SurfaceStyles.tonal(brandColor)
        surface = if (dark) SurfaceStyles.CleanDark else SurfaceStyles.CleanLight,
        colors = KineticaColors(accent = Color(0xFF3B5BDB)),
        design = KineticaDesigns.Material,
    ) {
        var sharing by remember { mutableStateOf(false) }
        KineticaScaffold { padding ->
            Column(Modifier.padding(padding).padding(24.dp)) {
                KButton("공유하기", onClick = { sharing = true }, fullWidth = true)
            }
            KSheet(sharing, onDismissRequest = { sharing = false }) {
                KListItem("링크 복사", onClick = { sharing = false })
                KListItem("메시지로 보내기", onClick = { sharing = false })
            }
        }
    }
}
```

| 원하는 모양 | `surface` 값 | 블러 |
| --- | --- | --- |
| 배경이 비치는 글래스 | `SurfaceStyles.Liquid`, `LiquidDark`, `Acrylic`, `OneUiGlass`, `Vision`, `Frosted`, `Mica` 등 15종 | Android 12 이상에서 그립니다. 그 아래에서는 반투명 색입니다 |
| 깔끔한 단색 | `SurfaceStyles.CleanLight`, `CleanDark`, `OneUi`, `OneUiDark`, `Material`, `MaterialDark` 또는 `SurfaceStyle.Solid(…)` | 없음. 모든 버전에서 같게 보입니다 |
| 브랜드 색을 옅게 섞은 톤 | `SurfaceStyles.tonal(brandColor, dark)`, `Tonal`, `TonalDark` | 없음 |

버튼과 칩, 카드는 표면과 따로 모양(`KVariant`: `Filled`, `Tonal`, `Outlined`, `Text`, `Elevated`, `Glass`)과 색 역할(`KTone`: `Accent`, `Neutral`, `Destructive`, `Positive`)을 고릅니다.
Material 3 앱이라면 `MaterialTheme` 안쪽에 `KineticaTheme`을 두고 강조색과 글꼴을 넘기면 됩니다. 자세한 순서는 [빠른 시작](docs/36-quick-start.md)에 있습니다.

### 다이얼로그와 토스트

```kotlin
val toast = rememberKToastState()
val scope = rememberCoroutineScope()
var confirm by remember { mutableStateOf(false) }

KAlertDialog(
    visible = confirm,
    onDismissRequest = { confirm = false },
    title = "사진을 삭제할까요?",
    message = "삭제한 사진은 되돌릴 수 없습니다.",
    actions = listOf(
        KDialogAction("취소") { confirm = false },
        KDialogAction("삭제", KDialogActionStyle.Destructive) {
            confirm = false
            scope.launch { toast.show("삭제했어요") }
        },
    ),
)
KToastHost(toast)
```

다이얼로그, 시트, 토스트는 `KineticaScaffold` 안에서는 오버레이 층에 그리고, 밖에서는 각자 창을 띄우므로 Material `Scaffold` 안에서 불러도 됩니다.

### XML 화면

```kotlin
val handle = KSheets.show(activity, R.layout.share_sheet)
KToasts.show(activity, "저장했어요")
```

글래스나 단색 표면이 필요한 자리에는 `KSurfaceView`를 레이아웃에 넣고 `app:kinetica_surfaceStyle`로 표면을 고릅니다. 누를 때 효과는 `kinetica-render-compose`의 `ViewEffects.apply`로 붙입니다. 자세한 내용은 [UI 키트 문서](docs/35-ui-kit.md)와 [모션 효과 문서](docs/33-effects.md)에 있습니다.

### 위젯 보드

```kotlin
val catalog = listOf(BoardWidget("sales", "오늘 매출", widthDp = 176f, heightDp = 120f), BoardWidget("orders", "최근 주문"))

setContent {
    // 화면을 회전해도 배치와 실행 취소 기록이 남고, 화면이 가려질 때마다 저장된다.
    val board = rememberKineticaBoardState(catalog, BoardStorage.sharedPreferences(this, "home", "board"))
    var editing by rememberSaveable { mutableStateOf(false) }
    KineticaBoard(board, Modifier.fillMaxSize(), editing = editing, onLongPress = { editing = true }) { id ->
        when (board.widgetKeyOf(id)) {
            "sales" -> SalesCard()     // 호스트의 Composable을 그대로 쓴다
            "orders" -> OrdersCard()
        }
    }
}
```

위로 끌면 Windows 스냅 레이아웃처럼 배치를 고르고, 위젯을 겹쳐 스택으로 만듭니다. [위젯 보드 문서](docs/31-board.md)

### 장면 재생과 서버 화면

```kotlin
val box = SceneNode(NodeId("box"), NodeContent.Rectangle(Size(400.0, 400.0)), style = NodeStyle(fill = ArgbColor(0xFF7C8CFF.toInt())))
val fade = Timeline("main", 1_000, listOf(PropertyTrack(box.id, AnimatableProperty.Opacity,
    listOf(Keyframe(0, KeyValue.Scalar(0.0)), Keyframe(1_000, KeyValue.Scalar(1.0))))))
val scene = SceneDocument("hello", Artboard(1080.0, 1920.0), mapOf(box.id to box), listOf(box.id), listOf(fade))

setContent {
    val playback = rememberPlaybackController(fade)
    LaunchedEffect(playback) { playback.play() }   // 재구성할 때마다 호출되지 않도록 effect 안에서 시작한다
    KineticaPlayer(scene, Modifier.fillMaxSize(), playback)
}
```

서버가 내려준 JSON은 `RemoteKineticaPlayer`로 그립니다. 캐시, 서명 확인, 여러 화면 번들을 지원하고, 받기에 실패하면 지금 화면을 그대로 둡니다. 편집기, 호스트 뷰와 실시간 데이터, XML 재생기, PNG 내보내기, Java 예는 [연동 예](docs/integration.md)에 있습니다.

## 주의사항

- 글래스 표면의 블러는 Android 12(API 31) 이상에서 보입니다. 그보다 낮은 기기에서는 반투명 색으로 그립니다. 단색과 톤 표면은 모든 버전에서 같습니다.
- 글래스 블러는 단색보다 GPU를 더 씁니다. 펼친 폴더블이나 태블릿처럼 큰 화면에서 시트를 자주 여는 화면이라면 단색이나 톤 표면도 함께 검토하세요. 측정값은 [성능](docs/performance.md)에 있습니다.
- 물결 왜곡과 메시 그라디언트의 셰이더는 Android 13(API 33) 이상에서 쓰고, 그 아래에서는 비슷한 모양으로 대신 그립니다.
- XML View 화면에서도 쓸 수 있지만 앱에 Compose 런타임이 포함됩니다.
- SDK는 네트워크 권한이 없고 아무것도 외부로 보내지 않습니다. 서버 화면도 호스트가 받아 온 바이트를 검증해 읽기만 합니다. 자세한 내용은 [SECURITY.md](SECURITY.md)에 있습니다.
- 알파 기간에는 공개 API가 바뀔 수 있습니다. 올릴 때마다 [CHANGELOG.md](CHANGELOG.md)를 확인하세요.

## 문서

| 문서 | 내용 |
| --- | --- |
| [빠른 시작](docs/36-quick-start.md) | 의존성, 테마 맞추기, 자주 쓰는 예, 흔한 실수, 테스트하는 법 |
| [UI 키트](docs/35-ui-kit.md) | 표면 스타일과 모든 컴포넌트, 호스트 앱에서 쓸 때 알아 둘 점, 성능 |
| [모션 효과](docs/33-effects.md) | `kineticaEffects`, `ViewEffects`, 프리셋, 화면 전환 |
| [위젯 보드](docs/31-board.md) | 보드 연동, 동작, 저장, 넓은 화면 |
| [UI 조립](docs/30-ui-composer.md) | 호스트 뷰 슬롯, 데이터 바인딩, 오토 레이아웃, 컴포넌트, 서버 화면 |
| [편집기](docs/32-studio.md) | 편집 기능, 도형과 스타일, 리치 텍스트, 타임라인 |
| [장면 JSON](docs/34-scene-json.md) | 서버 화면과 저장 파일의 형식 |
| [연동 예](docs/integration.md) | 편집 세션, 이미지, 서버 화면, XML 재생기, PNG 내보내기, 장면 전환, Java |
| [아키텍처](docs/architecture.md), [성능](docs/performance.md), [검증 가이드](docs/evaluation.md) | 모듈 구조와 좌표계, 측정값, 기능을 하나씩 확인하는 순서와 알려진 제한 |
| [변경 이력](CHANGELOG.md) | 버전별로 바뀐 내용 |

빌드와 기여 방법은 [CONTRIBUTING](CONTRIBUTING.md), 배포 절차는 [docs/publishing.md](docs/publishing.md), 설계 결정은 [docs/adr](docs/adr)에 있습니다.

## License

Apache License 2.0으로 배포됩니다. 자세한 내용은 [LICENSE](LICENSE)를 참고하세요.
