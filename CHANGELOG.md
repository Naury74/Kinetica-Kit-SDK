# Changelog

장면 문서의 스키마 버전(`schemaVersion`)은 SDK 버전과 따로 관리합니다. 현재 스키마 버전은 1입니다.

## 1.0.0-alpha01

첫 공개 버전입니다. 알파 기간에는 공개 API가 바뀔 수 있습니다.
UI·모션 키트(`kinetica-ui`)를 중심으로, 위젯 보드와 화면 편집기, 재생기, 서버 주도 화면을 함께 제공합니다([ADR-0007](docs/adr/0007-ui-motion-kit-first.md)).

### 새 기능

- 입력 컴포넌트 `KTextField`, `KTextArea`, `KChatInput`의 `TextFieldState` 오버로드와 `KChatInput`의 `maxLength`·`showCounter`, `KIslandContent.leading`과 `KIslandState.show(key, replaceShowing)`, `glowBorder`의 `intensity`(값·람다).

#### UI 키트 (`kinetica-ui`, 새 모듈)

다른 Kinetica 모듈 없이 단독으로 쓰는 Compose 컴포넌트 모음입니다. 자세한 내용은 [docs/35](docs/35-ui-kit.md)에 있습니다.

- 테마와 표면: `KineticaTheme`(표면, 강조색, 글꼴, 터치 피드백을 한 번에), `KineticaScaffold`, `KSurface`, `Modifier.kSurface`
- 표면 스타일 `SurfaceStyle`: 배경을 블러로 비추는 `Glass`, 불투명 단색 `Solid`, 강조색을 옅게 섞은 `Tonal`. 프리셋 `SurfaceStyles`는 글래스 15종(Liquid, Acrylic, One UI, Vision, Frosted, Mica, Material, HarmonyOS, Obsidian, Pastel과 어두운 변형), 단색 6종(Clean, One UI, Material의 밝은 쪽과 어두운 쪽), 톤(`Tonal`, `TonalDark`, `tonal(색)`)
- 모양 변형 `KVariant`(`Filled`, `Tonal`, `Outlined`, `Text`, `Elevated`, `Glass`)와 색 역할 `KTone`(`Accent`, `Neutral`, `Destructive`, `Positive`)
- 모양에 맞춘 터치 피드백 `KIndication`과 `kClickable`, `kCombinedClickable`, `kSelectable`, `kToggleable`
- 버튼과 선택: `KButton`(로딩, 성공 표시, 전체 폭), `KIconButton`, `KIconToggleButton`, `KFab`, `KExtendedFab`, `KSplitButton`, `KButtonGroup`, `KToggleGroup`, 칩 4종과 `KChipRow`, `KChipGroup`, `KSwitch`(모양 3종), `KCheckbox`, `KRadio`, `KSegmentedControl`, `KSlider`, `KRangeSlider`, `KStepper`, `KQuantityStepper`, `KRating`, `KSlideToConfirm`
- 입력: `KTextField`(모양 5종, 접두와 접미, 글자 수), `KTextArea`, `KSearchBar`, `KSelect`, `KAutocomplete`, `KCodeInput`(SMS 자동 채우기), `KTagInput`, `KPasswordField`와 `passwordStrength`, `KAmountInput`, `KKeypad`, `KColorPicker`, `KSignaturePad`(이미지와 SVG 내보내기)
- 날짜와 시간: `KWheelPicker`, `KDatePicker`, `KTimePicker`, `KCalendar`(하루와 기간 선택), `KWeekStrip`, `KDateRangeDialog`, `KTimeRangeDialog`
- 목록과 카드: `KListItem`, `KListSection`, 긴 목록용 `kListSection`, 설정 행 프리셋 5종, `KDivider`, `SwipeActionsRow`, `ReorderableItem`, `KExpandable`, `KCard`, `KSelectableCard`, `KMediaCard`, `KHorizontalCard`, `KCarousel`, `KPageIndicator`, `SwipeCardStack`, `KAvatar`, `AvatarStack`, `KBadge`, `KStatusBadge`
- 내비게이션: `KTopAppBar`, `KLargeTitleBar`, `KBottomAppBar`, `KFloatingToolbar`, `KTabBar`, `KFloatingTabBar`, `KTopTabs`(인디케이터 3종), `KDrawer`(밀기, 모달), `PushNavigation`(가장자리 스와이프와 예측 뒤로 가기), `KExpandHost`, `KSearchView`, `KStepIndicator`, `KWizard`, `KPagination`, `KBreadcrumb`, `KIndexBar`
- 오버레이: `KDialog`, `KAlertDialog`, `KSheet`, `KActionSheet`(넓은 화면에서는 팝오버), `KDetentSheet`, `KFullScreenModal`, `KDropdownMenu`(체크, 하위 메뉴, 키보드 이동), `kContextMenu`, `KSpeedDial`, `kTooltip`, `kRichTooltip`, `CoachMarkHost`
- 피드백과 로딩: `KToastHost`, `KToaster`(쌓임, 위치, `promise`), `KSnackbarHost`, `KIslandHost`, `KBanner`, `KAlert`, `KNoticeBar`, `KLoadingIndicator` 5종, `borderSpinner`, 진행 막대 4종과 `KProgressRing`, 스켈레톤과 프리셋, `KRefreshBox`(표시 3종), `KResultMark`, `KResultDialog`, `KEmptyState`(그림 8종), `KHaptics`
- 데이터 표시: `KLineChart`, `KBarChart`, `KAreaChart`, `KMultiBarChart`, `KDonutChart`, `KPieChart`, `Sparkline`, `ActivityRings`, `KHeatmap`, `KCalendarHeatmap`, `KGauge`, `KStatCard`, `KChartPalettes`, `KTimeline`, `FlipDigits`, `FlipClock`, `KProfileHeader`, `KPriceTag`, `KPricingCard`, `KBillingPeriodToggle`
- 미디어와 소셜: `KMiniPlayer`, `KMiniPlayerBar`, `KMiniPlayerFullScreen`, 모핑 아이콘, `KImageViewer`, `BeforeAfterSlider`, `KOnboarding`, `LikeButton`, `KChatBubble`, `KTypingIndicator`, `KChatInput`, `KStoryRing`, `KStoryViewer`, `KAttachmentChip`, `KFileDropZone`, `KMapPin`
- 효과와 모션: `glowBorder`, `jellyPress`, `magnetic`, `motionTilt`, `tiltOnPress`, `holographic`, `shake`, `sparkle`, `parallaxScroll`, `spotlight`, `rippleDistortion`, `liquidMorph`, `KBlobButton`, `KBlobGroup`, `holdToConfirm`, `floatUp`, `RollingText`, `TypewriterText`, `ShimmerText`, `GradientText`, `ScrambleText`, `ConfettiHost`, `ParticleField`, `MeshGradientBackground`, `AuroraBackground`, `StarfieldBackground`, 이미지 대표색 배경, `FlipCard`, `MorphIcon`, `KCopyButton`, `KCircularReveal`, `KSharedTransitionHost`, `staggerIn`, `scrollTransform`, `KFlyToHost`
- 넓은 화면과 폴더블: `KListDetail`(힌지 맞춤), `KNavigationRail`, `KAdaptiveNavigation`, `KTabletopLayout`, `rememberKWindowLayout`, `kReadableWidth`. `androidx.window` 의존성이 함께 들어갑니다
- 호스트 앱 연동: `KineticaScaffold` 밖에서 부르면 별도 창(Dialog, Popup)으로 띄우기, 시스템 창 블러와 불투명 판 fallback, `contentWindowInsets`, 모달 포커스 가두기, 닫을 때 키보드 내리기, 스낵바 `bottomOffset`, `KGlassLayer`, `KSystemBarIcons`, 화면 밖과 백그라운드에서 반복 효과 멈춤
- XML 진입점(`com.naury.kinetica.ui.view`): `KSheets`, `KDialogs`, `KToasts`, `KSurfaceView`. RecyclerView, ViewPager2, CoordinatorLayout, Fragment 안에서 쓸 수 있고, 테마 속성 `kinetica_surfaceViewStyle`로 기본 스타일을 정합니다
- TalkBack 문구(영어, 한국어), 키보드 조작, RTL 배치, '애니메이션 삭제' 설정 대응

#### 모션 효과 (`kinetica-render-compose`, `kinetica-presets`)

- 효과 모델 `NodeEffects`: 누름(리플, 축소, 기울이기, 어둡게, 떠오르기, 버스트), 등장 9종, 반복 11종. 값은 장면 JSON에 저장되고 읽을 때 검증합니다
- Compose `Modifier.kineticaEffects`, XML `ViewEffects.apply`와 `ViewEffects.effectsOf`, 보드 위젯 `BoardWidget.effects`, 프리셋 `EffectPresets`
- 화면 전환 `ContentTransitions`(`fadeThrough`, `sharedAxisX`, `sharedAxisY`, `sharedAxisZ`, `slideUp`, `pop`)와 숫자 애니메이션 `animateNumberAsState`
- Java에서 효과 만들기: `PressEffect.of`, `LoopEffect.of`, `NodeEffects`와 `AppearEffect` 생성자 기본값, `NodeEffects.None`

#### 위젯 보드 (`kinetica-board-compose`)

- 호스트 위젯 보드 `KineticaBoard`와 XML용 `KineticaBoardView`. 자유 배치와 격자 배치, 가장자리로 끌면 화면의 절반이나 4분의 1에 붙기
- 위로 끌면 여는 Windows식 스냅 레이아웃 선택(`SnapLayouts`, 7가지)
- 길게 눌러 편집 시작, 끄는 동안 자동 스크롤, TalkBack 동작, 키보드 편집(방향키, Shift+방향키, Delete)
- 같은 위젯 여러 개(`multiple`, `widgetKeyOf`, `addItem`, `itemIds`), 위젯 스택(`stack`, `unstack`, `stackMembers`, `stackAutoAdvanceMs`)
- 배치 저장(`rememberKineticaBoardState`, `BoardStorage`, XML용 `BoardStates.obtain`)과 위젯 목록이 바뀌었을 때 저장본 맞추기, 읽지 못한 저장본 보호(`restoreFailed`)
- 넓은 화면 열 수(`GridColumns.Adaptive`, `GridColumns.breakpoints`)와 열 수마다 배치 따로 기억, RTL 배치

#### 장면 엔진과 편집기

- core, history, motion, guides, serialization: 장면 문서와 검증, 행렬과 viewport, 명령과 reducer, hit test, 트랜잭션 단위 실행 취소(`KineticaSession`), keyframe 평가와 easing, 가이드라인 스냅, 스키마 버전 1 JSON과 migration 구조, 원자적으로 교체하는 파일 저장소
- UI 조립([ADR-0006](docs/adr/0006-host-ui-composer.md)): `Frame`, `HostSlot`, 데이터 바인딩, 동작(`SceneAction.parse("name?key=value")`), 노드마다 Compose 요소로 그리는 `SceneLayout`, 숫자 서식(`TextNumberFormat`), 접근성 정보(`NodeAccessibility`)
- 오토 레이아웃(줄 바꿈, 채우기, 최소와 최대 크기), 제약, `SceneFit.Responsive`, 텍스트 자동 크기(`TextMeasurer`, `SceneTextMeasurer`), 끌어서 순서 바꾸기(`Layouts.dropIndex`)
- 컴포넌트와 인스턴스(덮어쓴 값 유지), 변형(`세트/변형` 이름, `ComponentVariants`, 데이터로 고르기, 누르는 동안의 `Pressed` 변형), 디자인 토큰, 기본 키트 `UiKit`
- 디자인: 다각형, 별, 선, 화살표, 선형과 원형 그라디언트, 여러 겹의 바깥과 안쪽 그림자, 레이어 블러(내보내기 포함), 점선, 모서리별 반지름, 리치 텍스트와 호스트 글꼴, 기기 프레임 목업 PNG
- 애니메이션 속성: 이동, 배율, 회전, 불투명도, 색, 선 두께와 색, 너비, 높이, 모서리 반지름(모서리별 포함), 블러, 그림자(블러, X, Y, 퍼짐, 색), 그라디언트 색과 각도
- 계단 easing 점프 방식(`EasingSpec.Steps.jump`: `Start`, `End`, `None`, `Both`)과 키마다 정하는 회전 방향(`Keyframe.rotation`). JSON에는 선택 필드 `easing.jump`, `rotationDirection`이 추가되며 스키마 버전은 그대로 1입니다
- 편집기 `KineticaEditor`: 선택, 이동, 크기 조절, 회전, 여러 노드를 함께 크기 조절하고 회전(`TransformOps.resizeMany`, `rotateMany`), 정렬과 분배, 속성과 레이어 패널, 효과 미리보기, 밝은 테마(`StudioTheme.light`, `system`), 도구 모음 `StudioToolbarButton`, `StudioToolbarGroup`과 `onExit`
- 타임라인: 두 손가락, Ctrl+휠, 버튼으로 시간 축 확대와 맞춤, 재생 위치 따라가기, 여러 키 선택과 함께 끌기, 복사, 붙여넣기, 삭제, 단축키(Delete, Ctrl+C/V/A, Esc), easing 곡선 편집기(베지어 조절점, 스프링 감쇠와 강성, 계단 칸 수와 점프 방식)

#### 재생기와 서버 주도 화면 (`kinetica-player-compose`, `kinetica-serialization`)

- 편집기 없이 재생하는 `KineticaPlayer`, 장면 morph 전환, 끌어서 닫기, Compose `sharedBounds` 연결
- XML 재생기 `KineticaPlayerView`(`setLoader`, `setHostViews`, `refresh`, `kinetica_fit`, `kinetica_autoplay`)
- 서버 화면 `RemoteSceneLoader`, `RemoteKineticaPlayer`: 캐시(`RemoteSceneCache`), 앱에 넣어 둔 기본 문서, 앱을 다시 켜도 유지되는 etag, 서명 확인(`SceneVerifier.signature`, 키 교체용 `anyOf`), 거절 검사(`accept`)
- 여러 화면 번들(`SceneBundleCodec`, `RemoteSceneBundleLoader`, `RemoteKineticaPlayer(bundle, id)`)
- 숫자 데이터가 바뀌면 올라가며 바뀜(`numberAnimationMs`, `animateSceneNumbers`), 재생기의 '애니메이션 삭제' 대응
- 잘못된 문서는 `encode`가 쓰지 않고 위반 코드를 알림, 미리 검사하는 `SceneCodec.validate`

#### 그 밖

- `kinetica-java`: Java 호스트용 정적 진입점
- `kinetica-export-android`: 화면과 같은 그리기 코드로 만드는 PNG 내보내기
- 샘플 앱은 `sample-showcase` 하나입니다. 첫 화면에서 표면 스타일과 밝기를 고르고, UI 키트, 모션 효과, 대시보드 보드, 앱 화면 예, 컴포넌트, 화면 이동, 장면 전환, 서버 화면, 화면 만들기, XML 화면 예를 엽니다
- 배포 artifact만으로 호스트 시나리오를 빌드하고 계측 테스트를 돌리는 `consumer-check`

### 바뀐 것

- `KChatInput`의 화면 키보드 Enter 키가 기본으로 보내기 키가 됩니다(`imeSend = false`로 되돌림). 다이얼로그·시트 안의 받은 말풍선은 그림자 없이 옅게 그립니다.

알파 개발 중 동작이 바뀐 부분입니다. 공개 전 스냅샷을 쓰던 호스트만 해당합니다.

- `KSegmentedControl`(한 칸 고르기)의 TalkBack 역할 기본값이 `Role.Tab`에서 `Role.RadioButton`으로 바뀌었습니다. 아래 내용을 통째로 바꾸는 탭처럼 쓰면 `role = Role.Tab`을 넘깁니다. 이름은 기본 2줄까지 줄을 바꾸므로 한 줄로 두려면 `maxLines = 1`을 넘깁니다
- `KSwitch`의 `accent` 기본값이 `Color.Unspecified`가 되었습니다. `Capsule` 모양에서는 전처럼 `positive` 색을 쓰고, `onCheckedChange`는 `null`을 받아 표시만 할 수 있습니다
- `KChipGroup`의 칩을 `KFilterChip`과 같은 모양으로 그립니다
- `KMiniPlayer`의 `collapsed`, `expanded` 슬롯이 진행도를 `() -> Float`로 받습니다
- `KineticaScaffold`의 `indication` 기본값이 `KineticaTheme`을 따릅니다. 테마에서 `indication = null`로 끄면 스캐폴드도 호스트 값을 그대로 둡니다
- `KineticaBoardState.activeColumns`가 Compose 상태가 되어, 컴포지션에서 읽으면 열 수가 바뀔 때 다시 그려집니다
- `SceneLayout`은 숨긴 노드(`visible = false`, 불투명도 0, 또는 그런 조상 아래) 안의 호스트 뷰를 컴포지션에 남겨 상태를 지킵니다(`HiddenSlotPolicy.Keep`). 숨긴 동안 비용을 줄이려면 `hiddenSlots = HiddenSlotPolicy.Dispose`를 넘깁니다
- 재생기는 화면이 가려지면 멈추고 돌아오면 이어서 재생합니다. `RemoteKineticaPlayer`는 회전 뒤 처음부터 다시 재생하지 않고, 실행 중 바뀐 '애니메이션 삭제' 설정을 따릅니다
- `EffectPresets.listItem(index)`의 등장 지연은 10번째 항목(600ms)에서 멈춥니다
- 편집기 상단 도구 모음을 캡슐 묶음(추가, 실행 취소, 보기)으로 바꾸고, 레이어 패널의 표시와 잠금을 눈과 자물쇠 아이콘 토글로 바꿨습니다
- 타임라인 키 줄의 빈 곳을 세로로 끌면 사각 선택 대신 줄 목록이 스크롤됩니다. 사각 선택은 가로로 끌거나 길게 눌러 선택 모드를 켠 뒤 끌어서 시작합니다

### 고친 것

- 글래스 블러를 반경에 맞춘 낮은 해상도로 기록해 실기기 컴포넌트 화면 스크롤 끊김을 5.15%에서 0.1% 안팎으로, 그림자와 테두리 붓을 다시 써서 모션 화면 끊김을 2.8%에서 0.3%로 줄였습니다
- 페이저, 지연 목록, 이동 애니메이션에서 다시 그리지 않고 옮겨진 글래스가 옛 위치의 배경을 비추던 문제
- 시스템 바 아이콘: 요청이 걸려 있는 동안 호스트가 창의 아이콘 설정을 직접 바꾸면, 요청이 빠질 때 처음 기억한 값으로 덮어쓰던 문제. 시트가 콘텐츠를 절반 넘게 밀어내는 동안에는 상태 표시줄 아이콘을 `backdropColor` 밝기에 맞춥니다
- `KColorPicker`의 색 바퀴가 태블릿 가로 화면에서 화면 폭만큼 커지던 문제(이제 최대 320dp)
- 팝업 안의 글래스가 판을 뚫고 뒤 화면을 비추던 문제, 팝업을 닫아도 키보드가 남던 문제
- XML 화면의 `NestedScrollView`, `ViewPager2`, `RecyclerView` 안에서 `KColorPicker`, 투명도 슬라이더, `BeforeAfterSlider`, `FlipCard`를 끌면 바깥 View가 터치를 가로채던 문제
- `KImageViewer`가 크기가 없는 Painter(`BrushPainter` 등)를 그리지 못하던 문제. 열린 채 사진 목록이 비면 바로 닫고 `onDismiss`를 부릅니다
- `KLargeTitleBar`의 접힌 바 배경이 아래 가장자리에서 끊기던 문제, `bottom` 줄이 넓은 화면에서 본문 폭과 어긋나던 문제
- `KineticaEditor`가 안에서 `MaterialTheme`을 다시 감싸면서 호스트의 `LocalIndication`을 잃던 문제
- `kinetica-ui`가 그리는 문구(토스트, 스낵바, 툴팁, 입력란 등)의 문단 방향을 내용으로 정해, RTL 화면에서 한국어 문장 끝의 마침표가 앞으로 가던 문제
- RTL 화면에서 `SceneLayout`이 artboard 배경이 없거나 영역보다 작은 장면을 오른쪽 밖으로 밀어 도형이 보이지 않던 문제
- 보드: 가로 스크롤 안이나 가로 `wrap_content`처럼 폭 제한 없이 재면 Constraints 오류로 죽던 문제, 제자리에 놓아도 위젯이 밀리던 문제, 위젯 100개를 편집하며 끌 때 다른 위젯 틀을 다시 그리던 문제, 긴 키의 `multiple` 위젯을 추가하면 죽던 문제
- 보드 성능: 바뀌지 않은 노드를 다시 구성하지 않게 해 실기기 편집 끌기에서 놓친 프레임을 18.8%에서 6~9%로 줄였습니다. 편집 중 위젯 틀에 이름이 없어 TalkBack이 읽지 못하던 문제도 고쳤습니다
- 서버 화면: 같은 번들을 기다리던 화면이 먼저 요청한 화면을 떠나면 멈추던 문제, 캐시 일부가 손상되면 화면을 다시 받지 못하던 문제, 캐시 저장 실패가 앱을 죽이던 문제, migration 예외가 밖으로 새던 문제(`SaveFailure.InvalidDocument` 추가). 서명 확인과 읽기는 메인 스레드가 아닌 곳에서 합니다
- 반투명 그룹 안에서 겹친 자식이 더 진해 보이던 문제(레이어 하나로 합성), API 26·27에서 사진 EXIF 방향이 반영되지 않던 문제
- 노드 순서가 바뀌면 호스트 뷰 상태가 초기화되던 문제, 아주 깊은 JSON 중첩이 스택을 넘기던 문제
- 타임라인에서 길게 누른 뒤 이어서 끌면 키 줄이 밀려 손가락과 사각형이 어긋나던 문제

### 알파 동안 바뀐 이름

UI 키트를 글래스 전용에서 표면을 고르는 키트로 바꾸면서 이름을 정리했습니다. 호환용 옛 이름은 두지 않습니다.
블러 효과 자체의 이름(`Modifier.glass`, `GlassStyle`, `GlassStyles`, `GlassBackdrop`, `glassBackdrop`, `rememberGlassBackdrop`, `LocalGlassBackdrop`, `LocalGlassStyle`)은 그대로입니다.

| 예전 이름 | 새 이름 |
| --- | --- |
| `GlassTheme`, `GlassScaffold`, `GlassColors` | `KineticaTheme`, `KineticaScaffold`, `KineticaColors` |
| `LocalGlassColors`, `LocalGlassContentColor`, `LocalGlassTextStyle` | `LocalKineticaColors`, `LocalKineticaContentColor`, `LocalKineticaTextStyle` |
| `GlassTheme.style` | `KineticaTheme.surface`(표면), `KineticaTheme.glassStyle`(블러 기본 스타일) |
| 컴포넌트 `Glass*`(`GlassButton`, `GlassSheet`, `GlassDialog`, `GlassTabBar`, `GlassTextField`, `GlassSurface` 등) | `K*`(`KButton`, `KSheet`, `KDialog`, `KTabBar`, `KTextField`, `KSurface` 등) |
| 상태, 기본값, enum(`GlassSheetDefaults`, `GlassPosture`, `GlassWindowLayout` 등) | `KSheetDefaults`, `KPosture`, `KWindowLayout` 등 |
| `rememberGlass*State`, `rememberGlassWindowLayout` | `rememberK*State`, `rememberKWindowLayout` |
| `GlassIndication`, `Modifier.glassClickable`, `glassContextMenu` 등 | `KIndication`, `Modifier.kClickable`, `kCombinedClickable`, `kSelectable`, `kToggleable`, `kBadge`, `kContextMenu`, `kTooltip`, `kOrigin`, `kReadableWidth` |
| `GlassButtonKind.Filled`, `Tinted`, `Glass`, `Plain`과 버튼의 `kind`, `accent` | `KVariant.Filled`, `Tonal`, `Glass`, `Text`와 `variant`, `tone` |
| 컴포넌트의 `style: GlassStyle` 파라미터 | `surface: SurfaceStyle`(`SurfaceStyle.Glass(GlassStyles.X)` 또는 `SurfaceStyles.X`) |
| XML `GlassSheets`, `GlassDialogs`, `GlassToasts`, `GlassSurfaceView`, `GlassPopupOptions(style)`, `OnGlassDismissListener` | `KSheets`, `KDialogs`, `KToasts`, `KSurfaceView`, `KPopupOptions(surface)`, `OnPopupDismissListener` |
| XML `app:kinetica_glassStyle`, 테마 속성 `kinetica_glassSurfaceViewStyle`, `GlassSurfaceView.glassStyle` | `app:kinetica_surfaceStyle`(단색 `cleanLight`, `cleanDark`, `solidOneUi`, `solidMaterial`, 톤 `tonal` 등 추가), `kinetica_surfaceViewStyle`, `KSurfaceView.surfaceStyle` |
| `KSwitchVariant.Ios`, `Material`, `OneUi` | `KSwitchVariant.Capsule`, `Check`, `Slim` |
| `KSlider`, `KRangeSlider`의 `activeColor`, `KSwitch`의 `onColor`, `KSlideToConfirm`의 `color` | `accent` |
| `KCalendar`의 `onSelect` | `onSelectionChange` |
| `animateNumberAsState`의 `target` | `targetValue` |
| `MiniPlayerState`, `DetentSheetState`, `TooltipState`, `TooltipPlacement`, `SlideToConfirmState`, `rememberLargeTitleState`와 그 `remember*` 함수 | 앞에 `K`를 붙인 이름(`KMiniPlayerState`, `KDetentSheetState`, `KTooltipState`, `KTooltipPlacement`, `KSlideToConfirmState`, `rememberKLargeTitleState` 등) |
