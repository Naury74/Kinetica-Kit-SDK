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

**Kinetica**는 Jetpack Compose용 UI 키트입니다.
버튼과 다이얼로그 같은 기본 컴포넌트부터 차트, 채팅, 영상 통화 화면까지 200개가 넘는 컴포넌트를 담았고, 누르고 넘기고 화면을 옮기는 순간마다 자연스럽게 움직이도록 만들었습니다.

표면 스타일 하나만 고르면 앱 전체의 모양이 바뀝니다. 뒤가 비치는 글래스, 깔끔한 단색, 브랜드 색을 옅게 입힌 톤 가운데 앱에 맞는 것을 쓰면 됩니다.

<table>
  <tr>
    <td align="center" width="25%"><img src="docs/images/showcase-videocall.png" width="200" alt="영상 통화"><br><b>영상 통화</b></td>
    <td align="center" width="25%"><img src="docs/images/showcase-bank.png" width="200" alt="송금 앱"><br><b>송금</b></td>
    <td align="center" width="25%"><img src="docs/images/showcase-music.png" width="200" alt="음악 앱"><br><b>음악</b></td>
    <td align="center" width="25%"><img src="docs/images/showcase-health.png" width="200" alt="건강 앱"><br><b>건강</b></td>
  </tr>
</table>

## 기능

- **컴포넌트**: 입력, 선택, 목록, 카드, 바텀 시트, 다이얼로그, 토스트, 알림, 차트, 사진 뷰어, 영상 통화 화면 등 200여 개
- **표면 스타일**: 글래스, 단색, 톤 중 하나를 고르면 모든 컴포넌트가 따릅니다
- **디자인 언어**: Cupertino, Material, One UI, Fluent, Brutal, Soft
- **모션과 화면 전환**: 누름 효과, 등장 애니메이션, push 내비게이션, 공유 요소 전환 등 22가지 전환
- **편집 도구 컨트롤**: 눈금 다이얼, 기준값 스냅 슬라이더, 선택 순서 표시, 블러 없는 가벼운 유리 표면
- **위젯 보드, 화면 에디터, 서버 드리븐 UI**: 필요한 모듈만 더해 씁니다
- **접근성과 다국어**: TalkBack, 키보드, RTL, 큰 글꼴, 애니메이션 삭제 설정, 13개 언어
- **폴더블, 태블릿, XML 화면** 지원

## 화면

<table>
  <tr>
    <td align="center" width="33%"><img src="docs/images/showcase-design-material.png" width="220" alt="Material 디자인 언어의 건강 앱"><br><b>Material</b></td>
    <td align="center" width="33%"><img src="docs/images/showcase-design-oneui.png" width="220" alt="One UI 디자인 언어의 설정 앱"><br><b>One UI</b></td>
    <td align="center" width="33%"><img src="docs/images/showcase-design-brutal.png" width="220" alt="Brutal 디자인 언어의 항공권 검색"><br><b>Brutal</b></td>
  </tr>
  <tr>
    <td align="center" width="33%"><img src="docs/images/showcase-surface-glass.png" width="220" alt="글래스 표면의 송금 앱"><br><b>글래스</b></td>
    <td align="center" width="33%"><img src="docs/images/showcase-solid.png" width="220" alt="단색 표면의 송금 앱"><br><b>단색</b></td>
    <td align="center" width="33%"><img src="docs/images/showcase-tonal.png" width="220" alt="톤 표면의 송금 앱"><br><b>톤</b></td>
  </tr>
  <tr>
    <td align="center" width="33%"><img src="docs/images/showcase-delivery.png" width="220" alt="음식 배달 앱"><br><b>음식 배달</b></td>
    <td align="center" width="33%"><img src="docs/images/showcase-smarthome.png" width="220" alt="스마트 홈 앱"><br><b>스마트 홈</b></td>
    <td align="center" width="33%"><img src="docs/images/showcase-tasks.png" width="220" alt="할 일 앱"><br><b>할 일</b></td>
  </tr>
  <tr>
    <td align="center" width="33%"><img src="docs/images/showcase-chat.png" width="220" alt="메신저 앱"><br><b>메신저</b></td>
    <td align="center" width="33%"><img src="docs/images/showcase-travel.png" width="220" alt="여행 예약 앱"><br><b>여행 예약</b></td>
    <td align="center" width="33%"><img src="docs/images/showcase-settings.png" width="220" alt="설정 앱"><br><b>설정</b></td>
  </tr>
</table>

<table>
  <tr>
    <td align="center" width="50%"><img src="docs/images/showcase-fold-mail.png" width="360" alt="펼친 폴더블의 메일 앱"><br><b>폴더블: 메일</b></td>
    <td align="center" width="50%"><img src="docs/images/showcase-fold-player.png" width="360" alt="펼친 폴더블의 음악 플레이어"><br><b>폴더블: 음악 플레이어</b></td>
  </tr>
</table>

## 샘플 앱

[샘플 앱 내려받기 (kinetica-showcase.apk)](https://github.com/Naury74/Kinetica-Kit-SDK/releases/latest/download/kinetica-showcase.apk) · Android 8.0 이상

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
    implementation("com.naurylab:kinetica-ui:1.0.0-alpha05")
}
```

UI 키트는 `kinetica-ui` 하나면 됩니다. 다른 기능은 필요한 모듈만 더합니다.

| 모듈 | 용도 |
| --- | --- |
| `kinetica-ui` | 컴포넌트, 표면 스타일, 디자인 언어, 모션 |
| `kinetica-render-compose`, `kinetica-presets` | 효과 수정자 `Modifier.kineticaEffects`와 프리셋 |
| `kinetica-board-compose` | 위젯 보드 |
| `kinetica-player-compose`, `kinetica-serialization` | 애니메이션 플레이어, 서버 드리븐 UI |
| `kinetica-editor-compose` | 화면 에디터 |
| `kinetica-export-android`, `kinetica-java` | PNG 저장, Java 진입점 |

minSdk 26, Kotlin 2.2 이상. Material 3 없이도, 함께여도 씁니다.

## 사용법

화면 맨 바깥을 `KineticaTheme`으로 감싸고 표면 스타일을 정하면, 안의 컴포넌트가 모두 그 모양을 따릅니다.

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

| 더 알아보기 | 문서 |
| --- | --- |
| Material 3 테마와 맞추기, 자주 쓰는 컴포넌트 예 | [빠른 시작](docs/36-quick-start.md) |
| 표면 스타일과 전체 컴포넌트, 옵션 | [UI 키트](docs/35-ui-kit.md) |
| 다른 SDK 안에 넣기(진동, 움직임, 색, 언어 설정) | [UI 키트: 다른 SDK 안에서 쓸 때](docs/35-ui-kit.md#다른-sdk-안에서-쓸-때) |
| XML 화면에서 쓰기 | [UI 키트: XML(View) 화면](docs/35-ui-kit.md#xmlview-화면) |
| 효과 수정자와 화면 전환 | [모션 효과](docs/33-effects.md) |
| 위젯 보드 | [위젯 보드](docs/31-board.md) |
| 화면 에디터, 애니메이션 플레이어, 서버 드리븐 UI | [UI 조립](docs/30-ui-composer.md), [화면 에디터](docs/32-studio.md), [화면 JSON](docs/34-scene-json.md), [연동 예](docs/integration.md) |

## 주의사항

- 글래스 블러는 Android 12 이상에서 보이고, 그 아래에서는 반투명한 색으로 그립니다.
- 컴포넌트 문구는 13개 언어로 나옵니다. 앱 언어만 남기려면 `androidResources.localeFilters`를 쓰세요.
- 네트워크 권한을 쓰지 않고 아무것도 밖으로 보내지 않습니다. [SECURITY.md](SECURITY.md)

## 문서

[아키텍처](docs/architecture.md) · [성능](docs/performance.md) · [검증 가이드](docs/evaluation.md) · [변경 이력](CHANGELOG.md) · [기여](CONTRIBUTING.md) · [배포](docs/publishing.md)

## License

Apache License 2.0. [LICENSE](LICENSE)
