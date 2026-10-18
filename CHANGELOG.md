# Changelog

장면 문서의 스키마 버전(`schemaVersion`)은 SDK 버전과 따로 올립니다. 지금 스키마 버전은 1입니다.

## 1.0.0-alpha03 (개발 중)

### 새 기능

- `LocalKineticaReducedMotion`과 `isReducedMotion()`: 시스템 '애니메이션 삭제' 대신 호스트가 움직임 줄이기를 정하고, 직접 만든 애니메이션도 키트와 같은 기준을 따릅니다.
- `LocalKineticaFillContrast`: 채운 버튼, 배지, 선택된 칩이 지킬 글자 명암비를 정합니다. 기본값 4.5에서 브랜드 색을 진하게 고치지 않으려면 3.0이나 1 이하를 줍니다.
- `SurfaceStyle.Solid`의 `contentMuted`, `raised`: 호스트 팔레트의 보조 글자색과 한 단계 올라온 면의 색을 그대로 씁니다.
- `KButton`, `KIconButton`에 `painterResource`로 읽은 drawable 같은 `Painter` 아이콘을 받는 오버로드, `KTab.painter`.
- `KIcons`에 편집용 아이콘 `Undo`, `Redo`, `Rotate`, `Flip`, `Crop`, `Compare`를 더해 69개가 되었습니다.

### 바뀐 것

- 표면에 `raised`를 주면 중립 톤(`KVariant.Tonal`, `KTone.Neutral`) 버튼 바탕도 그 색을 씁니다.
- `LocalKineticaHaptics`가 `false`면 이제 버튼, 칩, 스테퍼, 휠 피커, 메뉴, 밀어서 동작 등 이 키트의 모든 컴포넌트가 진동을 내지 않습니다. 전에는 `KHaptics`, 토스트, 당겨서 새로 고침만 따랐습니다.

## 1.0.0-alpha02

### 새 기능

- `KListLeading.PainterIcon`: `painterResource`로 읽은 drawable처럼 `Painter`를 목록 앞 아이콘으로 씁니다. 색과 크기 규칙은 `KListLeading.Icon`과 같습니다.
- `KDialogAction`의 `onClick`에 기본값이 생겼습니다. 다이얼로그는 버튼을 누르면 스스로 닫히므로, 닫기만 하는 취소 버튼은 `KDialogAction("취소", KDialogActionStyle.Cancel)`처럼 씁니다.

### 고친 것

- 여러 개를 고르는 `KChipGroup`(`KChipSelection.Multiple`)이 줄을 넘길 때, 칩을 켜고 끄면 폭이 바뀌어 뒤 칩들이 다른 줄로 옮겨 가던 문제. 연달아 누르면 손가락 아래 칩이 바뀌어 다른 칩이 눌렸습니다. 이제 체크 자리를 늘 확보하고, 꺼진 칩은 글자를 가운데에 둡니다. 하나만 고르는 묶음은 전처럼 폭이 바뀝니다.
- 밝은 사진을 `KImageViewer`로 열면 흰 상태 표시줄 아이콘이 사진에 묻히던 문제. 상태 표시줄 뒤에 위에서 옅어지는 어두운 막을 둡니다.
- 흰 사진 위에서 `KImageViewer`의 닫기 버튼과 페이지 번호가 잘 보이지 않던 문제. 글자가 밝은 표면이면 버튼 안쪽을 옅게 어둡게 칠하고 ×에 어두운 테두리를 둡니다.

### 문서

- `PushNavigation`은 빠지는 화면도 전환이 끝날 때까지 그린다는 점과, 그 화면의 데이터를 다루는 법을 적었습니다.
- Material 3 색을 `KineticaColors`로 넘길 때는 짝이 되는 글자 색(`onError` 등)도 함께 넘기도록 예를 고쳤습니다. 다크 테마의 `error`만 넘기면 채운 버튼이 탁해집니다.

## 1.0.0-alpha01

첫 공개 버전입니다. 알파 동안에는 공개 API가 바뀔 수 있습니다.
UI와 모션 키트(`kinetica-ui`)가 중심이고, 위젯 보드, 화면 편집기, 재생기, 서버 주도 화면이 함께 들어 있습니다. 방향은 [ADR-0007](docs/adr/0007-ui-motion-kit-first.md)에 적었습니다.

### 주요 기능

#### UI 키트 (`kinetica-ui`, 새 모듈)

다른 Kinetica 모듈 없이 혼자 쓰는 Compose 컴포넌트 모음입니다. 자세한 내용은 [docs/35](docs/35-ui-kit.md)에 있습니다.

- 테마와 표면: 표면, 강조색, 글꼴, 터치 피드백을 한 번에 정하는 `KineticaTheme`, 그리고 `KineticaScaffold`, `KSurface`, `Modifier.kSurface`
- 표면 스타일 `SurfaceStyle`: 뒤 배경을 블러로 비추는 `Glass`, 불투명한 단색 `Solid`, 강조색을 옅게 섞은 `Tonal`이 있습니다. 프리셋 `SurfaceStyles`에는 글래스 15종(Liquid, Acrylic, One UI, Vision, Frosted, Mica, Material, HarmonyOS, Obsidian, Pastel과 그 어두운 변형), 단색 6종(Clean, One UI, Material의 밝은 판과 어두운 판), 톤(`Tonal`, `TonalDark`, `tonal(색)`)이 들어 있습니다
- 모양 베리언트 `KVariant`(`Filled`, `Tonal`, `Outlined`, `Text`, `Elevated`, `Glass`)와 색 역할 `KTone`(`Accent`, `Neutral`, `Destructive`, `Positive`)
- 컴포넌트 모양을 따라가는 터치 피드백 `KIndication`과 `kClickable`, `kCombinedClickable`, `kSelectable`, `kToggleable`
- 버튼과 선택: `KButton`(로딩, 성공 표시, 전체 폭), `KIconButton`, `KIconToggleButton`, `KFab`, `KExtendedFab`, `KSplitButton`, `KButtonGroup`, `KToggleGroup`, 칩 4종과 `KChipRow`, `KChipGroup`, 모양이 5가지인 `KSwitch`, `KCheckbox`, `KRadio`, `KSegmentedControl`, `KSlider`, `KRangeSlider`, `KStepper`, `KQuantityStepper`, `KRating`, `KSlideToConfirm`
- 입력: `KTextField`(모양 5종, 접두어와 접미어, 글자 수), `KTextArea`, `KSearchBar`, `KSelect`, `KAutocomplete`, SMS 자동 채우기를 지원하는 `KCodeInput`, `KTagInput`, `KPasswordField`와 `passwordStrength`, `KAmountInput`, `KKeypad`, `KColorPicker`, 이미지와 SVG로 내보내는 `KSignaturePad`
  - `KTextField`, `KTextArea`, `KChatInput`은 `TextFieldState` 오버로드도 받습니다
  - `KChatInput`은 `maxLength`, `showCounter`, 한도에 가까울 때만 글자 수를 보이는 `counterThreshold`, TalkBack이 읽을 이름 `label`을 받습니다
  - `KKeypadMode.Phone`은 `*`, `0`, `#` 줄과 통화 버튼 자리(`extraKey`)가 있는 전화 키패드입니다. 0을 길게 누르면 `+`를 넘깁니다
- 날짜와 시간: `KWheelPicker`, `KDatePicker`, `KTimePicker`, 하루나 기간을 고르는 `KCalendar`, `KWeekStrip`, `KDateRangeDialog`, `KTimeRangeDialog`
- 목록과 카드: `KListItem`, `KListSection`, 긴 목록용 `kListSection`, 설정 행 프리셋 5종, `KDivider`, `SwipeActionsRow`, `ReorderableItem`, `KExpandable`, `KCard`, `KSelectableCard`, `KMediaCard`, `KHorizontalCard`, `KCarousel`, `KPageIndicator`, `SwipeCardStack`, `KAvatar`, `AvatarStack`, `KBadge`, `KStatusBadge`. `KListTrailing.Value`는 글자 색 `color`를 받습니다
- 내비게이션: `KTopAppBar`, `KLargeTitleBar`, `KBottomAppBar`, `KFloatingToolbar`, `KTabBar`, `KFloatingTabBar`, 인디케이터가 4종인 `KTopTabs`, 밀기와 모달 방식의 `KDrawer`, 가장자리 스와이프와 예측형 뒤로 가기를 지원하는 `PushNavigation`, `KExpandHost`, `KSearchView`, `KStepIndicator`, `KWizard`, `KPagination`, `KBreadcrumb`, `KIndexBar`
- 오버레이: `KDialog`, `KAlertDialog`, `KSheet`, 넓은 화면에서는 팝오버로 뜨는 `KActionSheet`, `KDetentSheet`, `KFullScreenModal`, 체크와 하위 메뉴와 키보드 이동을 지원하는 `KDropdownMenu`, `kContextMenu`, `KSpeedDial`, `kTooltip`, `kRichTooltip`, `CoachMarkHost`
- 피드백과 로딩: `KToastHost`, 토스트를 쌓고 위치를 고르고 `promise`를 쓰는 `KToaster`, `KSnackbarHost`, `KIslandHost`, `KBanner`, `KAlert`, `KNoticeBar`, `KLoadingIndicator` 5종, `borderSpinner`, 진행 막대 4종과 `KProgressRing`, 스켈레톤과 그 프리셋, 인디케이터가 3종인 `KRefreshBox`, `KResultMark`, `KResultDialog`, 그림이 8종인 `KEmptyState`, `KHaptics`
  - `KIslandContent.leading`, 보이는 알림을 바꿀지 정하는 `KIslandState.show(key, replaceShowing)`, 보이는 알림의 내용만 코루틴 없이 바꾸고 바꿨는지를 돌려주는 `KIslandState.update(content)`
  - 같은 `KIslandState`를 여러 호스트가 그리면 가장 나중에 들어온 호스트 하나만 그립니다. 그 호스트가 사라지면 앞의 호스트가 이어서 그리므로, 앱 전체의 `KIslandHost`와 `KCallScreen(island)`에 같은 상태를 넘겨도 알림은 한 번만 뜹니다
- 데이터 표시: `KLineChart`, `KBarChart`, `KAreaChart`, `KMultiBarChart`, `KDonutChart`, `KPieChart`, `Sparkline`, `ActivityRings`, `KHeatmap`, `KCalendarHeatmap`, `KGauge`, `KStatCard`, `KChartPalettes`, `KTimeline`, `FlipDigits`, `FlipClock`, `KProfileHeader`, `KPriceTag`, `KPricingCard`, `KBillingPeriodToggle`
- 미디어와 소셜: `KMiniPlayer`, `KMiniPlayerBar`, `KMiniPlayerFullScreen`, 모핑 아이콘, `KImageViewer`, `BeforeAfterSlider`, `KOnboarding`, `LikeButton`, `KChatBubble`, `KTypingIndicator`, `KChatInput`, `KStoryRing`, `KStoryViewer`, `KAttachmentChip`, `KFileDropZone`, `KMapPin`. 지도 핀의 꼬리 끝을 좌표에 맞추려면 `Modifier.mapPinAnchor`를 씁니다
- 효과와 모션: `glowBorder`(`intensity`는 값이나 람다로 받음), `jellyPress`, `magnetic`, `motionTilt`, `tiltOnPress`, `holographic`, `shake`, `sparkle`, `parallaxScroll`, `spotlight`, `rippleDistortion`, `liquidMorph`, `KBlobButton`, `KBlobGroup`, `holdToConfirm`, `floatUp`, `RollingText`, `TypewriterText`, `ShimmerText`, `GradientText`, `ScrambleText`, `ConfettiHost`, `ParticleField`, `MeshGradientBackground`, `AuroraBackground`, `StarfieldBackground`, 이미지 대표색 배경, `FlipCard`, `MorphIcon`, `KCopyButton`, `KCircularReveal`, `KSharedTransitionHost`, `staggerIn`, `scrollTransform`, `KFlyToHost`
- 디자인 언어 `KineticaDesign`과 프리셋 `KineticaDesigns`(`Cupertino`, `Material`, `OneUi`, `Fluent`, `Brutal`, `Soft`): 표면 스타일과 별개로 컴포넌트의 생김새를 고릅니다. `KineticaTheme(design = …)`로 적용합니다. 들어 있는 것은 다음과 같습니다
  - 모양 토큰 `KineticaShapes`: 모서리 방식 `KCornerStyle`(둥근 모서리, 스쿼클, 잘린 모서리, 각진 모서리), 크기 단계, 역할별 `KShapeToken`. 스쿼클 모양 `KSquircleShape`도 따로 씁니다
  - 깊이 표현 `KDepth`(그림자, 테두리, 평면, 오프셋 그림자), 밀도 `KDensity`, 라벨과 제목 굵기, 탭 인디케이터, 목록 구분 `KListSeparator`
  - 컨트롤 모양 `KineticaControls`: 스위치 모양 `Outline`, `Block`과 슬라이더 손잡이 `KSliderThumb` 5종
  - 모션 성격 `KineticaMotion`
  - 화면 전환도 디자인 언어를 따릅니다. `SheetStack`과 `KFullScreenModal`은 sheet 모서리를, `CardStack`과 `KExpandHost`의 카드는 card 모서리를 쓰고, 기본 스프링은 `KineticaMotion`에 맞춥니다. 직접 만든 전환은 `KTransitionFrame.shapes`로 모서리 크기를 읽습니다
- 화면 이동 전환: `PushNavigation(transition = …)`과, 이동 방향을 알고 내용을 바꾸는 `KTransitionContent`에 `KTransitions` 프리셋 20종을 넣습니다. 밀기, 패럴랙스, 슬라이드, 덮기, 드러내기, 확대, 페이드, fade through, shared axis X/Y/Z, 탭 슬라이드, 시트 스택, 카드 스택, 큐브, 뒤집기, 원과 마름모와 별 모양으로 드러내기, 블러 디졸브, 와이프 등이 있고, `KScreenTransition`으로 직접 만들 수도 있습니다. 모든 전환은 예측형 뒤로 가기 제스처를 따라 되감기고, RTL에서는 방향이 뒤집히고, '애니메이션 삭제'를 켜면 애니메이션 없이 바로 바뀝니다. `PushNavigation`에 `paneTitle`을 주면 화면이 바뀔 때 TalkBack이 새 화면 이름을 읽습니다
- Navigation Compose의 `NavHost`와 `AnimatedContent`에 쓰는 전환 모음 `KNavTransitions`(`Push`, `Slide`, `SlideUp`, `SheetStack`, `Zoom`, `Fade`, `FadeThrough`, `SharedAxisX/Y/Z`), `kTransform`, `rememberKNavTransition`. `KNavTransitions.push`, `slide`, `slideUp`에 `KineticaMotion`을 넘기면 디자인 언어의 빠르기로 밉니다
- `KExpandHost`의 컨테이너 변환 모양 `KContainerTransform`(`Card`, `listRow(color)`, `fab(color)`)과 예측형 뒤로 가기 지원
- 선 아이콘 63종 `KIcons`: 홈, 검색, 설정, 더하기, 닫기, 방향, 공유, 휴지통, 하트, 별, 메일, 전화와 통화 끊기, 마이크, 영상, 카메라, 사진, 달력, 사람, 장바구니 등. 뒤로, 앞으로, 보내기 화살표는 RTL에서 뒤집힙니다. 전체 목록은 `KIcons.all`입니다
- 통화 컴포넌트: 영상과 오디오는 다루지 않고, 영상은 호스트가 슬롯으로 넣습니다
  - 부품: `KCallControlButton`, `KCallDock`(좁으면 버튼을 '더보기'로 접고 배지를 합치며, 버튼 위치를 알려 줌), `KCallTopBar`, `KParticipantTile`(영상 슬롯, 아바타, 이름표, 글로우 테두리, 고정), `KParticipantGrid`, `KSelfView`, `KCallStatusView`, `KIncomingCall`, `KCallBanner`, `KCallBubble`, `KRaisedHandPill`, `KReactionOverlay`, `KReactionPicker`, `KParticipantRow`, `KAudioRouteList`, `KAudioLevelIndicator`, `KNetworkQualityIndicator`, `KCallTimer`, `KOngoingCallPill`, 바를 자동으로 숨기는 `rememberKCallChromeState`, 발표자가 너무 자주 바뀌지 않게 늦추는 `rememberKStableSpeaker`, 통화 아이콘 `KCallIcons`
  - `KParticipantGrid`는 인원과 화면 비율에 맞춰 격자를 짜고, 1:1 작은 창, 크게 보기와 한 줄 띠, 쪽 넘김을 지원합니다. 모든 쪽은 꽉 찬 쪽 기준 칸 크기로 짜서 가운데에 놓으며, 쪽이 여럿이면 `columns`는 한 쪽에 들어가는 최대 인원을 받습니다
  - 완성 화면: `KCallScreen`(상태는 `KCallState`, 콜백은 `KCallActions`이고 넘기지 않은 콜백의 버튼은 숨김), `KIncomingCallScreen`, `KCallLobby`
  - `KCallScreen`은 높이가 480dp보다 낮은 가로 화면에서 조작 막대를 끝 쪽 세로 막대로 옮깁니다. 이때 `dock` 슬롯은 `vertical`도 함께 받습니다. 영상 격자 열 수는 `gridColumns`로 정합니다
  - `KReactionOverlay(confetti)`와 `KCallScreen(confettiReactions)`: 고른 반응이 오면 색종이를 함께 터뜨립니다. 기본 반응은 🎉이고, 부품에서는 기본으로 꺼져 있습니다
  - `KSelfView`는 던진 속도로 붙을 모서리를 고르고 그 속도를 이어받아 붙습니다. '애니메이션 삭제'를 켜면 바로 붙습니다
  - 영상 위 글래스 바가 장식 애니메이션 때문에 블러를 다시 계산하지 않도록, 타일을 영상 층과 장식 층으로 나눠 그립니다
- 넓은 화면과 폴더블: 힌지에 맞추는 `KListDetail`, `KNavigationRail`, `KAdaptiveNavigation`, `KTabletopLayout`, `rememberKWindowLayout`, `kReadableWidth`. `androidx.window` 의존성이 함께 들어갑니다
- 호스트 앱 연동
  - `KineticaScaffold` 밖에서 부르면 Dialog나 Popup 창으로 띄웁니다. 시스템 창 블러를 쓰고, 기기에서 창 블러가 꺼져 있으면 불투명한 판으로 대신합니다
  - `contentWindowInsets`, 모달 안 포커스 가두기, 닫을 때 키보드 내리기, 스낵바 `bottomOffset`, `KGlassLayer`
  - `KSystemBarIcons`: 요청이 걸려 있는 동안 호스트가 창의 아이콘 설정을 직접 바꾸면 그 값을 존중합니다. 시트가 콘텐츠를 절반 넘게 밀어내는 동안에는 상태 표시줄 아이콘을 `backdropColor` 밝기에 맞춥니다
  - 화면 밖에 있거나 앱이 백그라운드에 있으면 반복 효과를 멈춥니다
- XML 진입점 `com.naury.kinetica.ui.view`: `KSheets`, `KDialogs`, `KToasts`, `KSurfaceView`. RecyclerView, ViewPager2, CoordinatorLayout, Fragment 안에서 쓸 수 있고, 기본 스타일은 테마 속성 `kinetica_surfaceViewStyle`로 정합니다
- 접근성과 RTL: TalkBack 문구(영어, 한국어), 키보드 조작, RTL 배치, '애니메이션 삭제' 설정을 지원합니다
  - 차트는 요약과 축 이름을 먼저 읽고 막대, 점, 조각으로 넘어갑니다. `KAlert`, `KBanner`, `BeforeAfterSlider`는 한 번에 읽고, `KParticipantGrid`의 띠와 쪽 격자는 TalkBack 스크롤로 화면 밖 참가자까지 닿습니다
  - Android는 노드 하나에 사용자 정의 동작을 32개까지만 받습니다. 글자가 32개를 넘는 `KIndexBar`(예: `KIndexBarDefaults.HangulAndLatin`)는 위아래로 쓸어 글자를 옮기고, 32개 이하일 때만 글자마다 이동 동작을 줍니다. 메뉴, 떠 있는 탭 바, 밀어서 하는 동작도 32개를 넘으면 자릅니다
  - 토스트, 스낵바, 툴팁, 입력란처럼 `kinetica-ui`가 그리는 문구는 내용을 보고 문단 방향을 정하므로, RTL 화면에서도 한국어 문장의 마침표가 앞으로 가지 않습니다

#### 모션 효과 (`kinetica-render-compose`, `kinetica-presets`)

- 효과 모델 `NodeEffects`: 누름 효과(리플, 축소, 기울이기, 어둡게, 떠오르기, 버스트), 등장 효과 9종, 반복 효과 11종. 값은 장면 JSON에 저장되고 읽을 때 검증합니다
- Compose `Modifier.kineticaEffects`, XML `ViewEffects.apply`와 `ViewEffects.effectsOf`, 보드 위젯 `BoardWidget.effects`, 프리셋 `EffectPresets`. `EffectPresets.listItem(index)`의 등장 지연은 10번째 항목(600ms)부터 더 늘지 않습니다
- 화면 전환 `ContentTransitions`(`fadeThrough`, `sharedAxisX`, `sharedAxisY`, `sharedAxisZ`, `slideUp`, `pop`)와 숫자 애니메이션 `animateNumberAsState`
- Java에서 효과를 만들 때는 `PressEffect.of`, `LoopEffect.of`, `NodeEffects`와 `AppearEffect` 생성자의 기본값, `NodeEffects.None`을 씁니다

#### 위젯 보드 (`kinetica-board-compose`)

- 호스트 위젯을 올리는 보드 `KineticaBoard`와 XML용 `KineticaBoardView`. 자유 배치와 격자 배치를 지원하고, 가장자리로 끌면 화면의 절반이나 4분의 1에 붙습니다
- 위젯을 위로 끌면 Windows식 스냅 레이아웃 선택판이 열리고 7가지 배치 중 하나를 고릅니다
- 길게 눌러 편집 시작, 끄는 동안 자동 스크롤, TalkBack 동작, 키보드 편집(방향키, Shift+방향키, Delete)
- 같은 위젯 여러 개 두기(`multiple`, `widgetKeyOf`, `addItem`, `itemIds`)와 위젯 스택. 편집 중 위젯을 다른 위젯 가운데에 놓으면 스택으로 묶이고, `stackAutoAdvanceMs`로 자동 넘김 간격을 정합니다
- 배치 저장(`rememberKineticaBoardState`, `BoardStorage`, XML용 `BoardStates.obtain`). 위젯 목록이 바뀌면 저장본을 맞추고, 읽지 못한 저장본은 덮어쓰지 않습니다(`restoreFailed`)
- 넓은 화면의 열 수(`GridColumns.Adaptive`, `GridColumns.breakpoints`)와 열 수마다 따로 기억하는 배치, RTL 배치

#### 장면 엔진과 편집기

- core, history, motion, guides, serialization: 장면 문서와 검증, 행렬과 viewport, 명령과 reducer, hit test, 트랜잭션 단위 실행 취소(`KineticaSession`), keyframe 평가와 easing, 가이드라인 스냅, 스키마 버전 1 JSON과 migration 구조, 원자적으로 교체하는 파일 저장소
- UI 조립([ADR-0006](docs/adr/0006-host-ui-composer.md)): `Frame`, `HostSlot`, 데이터 바인딩, 동작(`SceneAction.parse("name?key=value")`), 노드마다 Compose 요소로 그리는 `SceneLayout`, 숫자 서식 `TextNumberFormat`, 접근성 정보 `NodeAccessibility`. 노드 순서가 바뀌어도 호스트 뷰 상태는 유지됩니다
- 오토 레이아웃(줄 바꿈, 채우기, 최소와 최대 크기), 제약, `SceneFit.Responsive`, 텍스트 자동 크기(`TextMeasurer`, `SceneTextMeasurer`), 끌어서 순서 바꾸기(`Layouts.dropIndex`)
- 컴포넌트와 인스턴스(덮어쓴 값 유지), 베리언트(`세트/변형` 이름 규칙, `ComponentVariants`, 데이터로 고르기, 누르는 동안 보여 주는 `pressed` 또는 `눌림` 베리언트), 디자인 토큰, 기본 키트 `UiKit`
- 디자인: 다각형, 별, 선, 화살표, 선형과 원형 그라디언트, 여러 겹의 바깥 그림자와 안쪽 그림자, 레이어 블러(내보내기 포함), 점선, 모서리별 반지름, 리치 텍스트와 호스트 글꼴, 기기 프레임 목업 PNG. 반투명 그룹은 레이어 하나로 합성하므로 겹친 자식이 더 진해 보이지 않습니다
- 애니메이션 속성: 이동, 배율, 회전, 불투명도, 색, 선 두께와 색, 너비, 높이, 모서리 반지름(모서리별 포함), 블러, 그림자(블러, X, Y, 퍼짐, 색), 그라디언트 색과 각도
- 계단 easing의 점프 방식(`EasingSpec.Steps.jump`: `Start`, `End`, `None`, `Both`)과 키마다 정하는 회전 방향(`Keyframe.rotation`). JSON에는 선택 필드 `easing.jump`, `rotationDirection`이 생기고, 스키마 버전은 1 그대로입니다
- 편집기 `KineticaEditor`: 선택, 이동, 크기 조절, 회전, 여러 노드 함께 크기 조절과 회전(`TransformOps.resizeMany`, `rotateMany`), 정렬과 분배, 속성 패널과 레이어 패널, 효과 미리보기, 밝은 테마(`StudioTheme.light`, `system`), 도구 모음 `StudioToolbarButton`, `StudioToolbarGroup`, `onExit`
- 타임라인: 두 손가락, Ctrl+휠, 버튼으로 시간 축 확대와 맞춤, 재생 위치 따라가기, 여러 키를 골라 함께 끌기, 복사, 붙여넣기, 삭제, 단축키(Delete, Ctrl+C/V/A, Esc), easing 곡선 편집기(베지어 조절점, 스프링 감쇠와 강성, 계단 수와 점프 방식). 키 줄의 빈 곳을 세로로 끌면 줄 목록이 스크롤되고, 사각 선택은 가로로 끌거나 길게 눌러 선택 모드를 켠 뒤 끌어서 시작합니다
- 편집기 문서 패널의 '장면 전환' 섹션에서 이 장면이 들어올 때의 전환 종류, 방향, 시간을 고르고, 실행 취소할 수 있습니다(`SceneCommand.SetTransition`)

#### 재생기와 서버 주도 화면 (`kinetica-player-compose`, `kinetica-serialization`)

- 편집기 없이 재생하는 `KineticaPlayer`, 장면 morph 전환, 끌어서 닫기, Compose `sharedBounds` 연결. 재생기는 화면이 가려지면 멈추고 돌아오면 이어서 재생합니다
- XML 재생기 `KineticaPlayerView`(`setLoader`, `setHostViews`, `refresh`, `kinetica_fit`, `kinetica_autoplay`)
- 서버 화면 `RemoteSceneLoader`, `RemoteKineticaPlayer`: 캐시(`RemoteSceneCache`), 앱에 넣어 둔 기본 문서, 앱을 다시 켜도 남는 etag, 서명 확인(`SceneVerifier.signature`, 키 교체용 `anyOf`), 받은 문서를 거절하는 `accept`. 서명 확인과 읽기는 메인 스레드 밖에서 합니다. `RemoteKineticaPlayer`는 회전한 뒤에도 처음부터 다시 재생하지 않고, 실행 중에 바뀐 '애니메이션 삭제' 설정을 따릅니다
- 여러 화면을 담은 번들(`SceneBundleCodec`, `RemoteSceneBundleLoader`, `RemoteKineticaPlayer(bundle, id)`)
- 숫자 데이터가 바뀌면 숫자가 올라가며 바뀌는 애니메이션(`numberAnimationMs`, `animateSceneNumbers`)과 재생기의 '애니메이션 삭제' 지원
- 장면 사이 전환 14종(`fade`, `morph`, `dissolve`, `slide`, `push`, `cover`, `uncover`, `wipe`, `iris`, `zoom`, `zoomBlur`, `rotate`, `flip`, `cube`). 장면 JSON 최상위의 `transition`에 종류, 방향, 시간, easing, 중심을 적고, 동작 문자열의 `transition=` 파라미터로 덮어쓸 수 있습니다. 스키마 버전은 1 그대로이고 예전 문서도 그대로 읽힙니다
- 문서가 바뀌면 전환하는 `AnimatedScene`(`forward = false`면 되감기, RTL과 '애니메이션 삭제' 지원), 진행도를 직접 넘기는 `SceneTransition(from, to, spec, progress)`, Compose 화면에 붙이는 `Modifier.sceneTransitionLayer`. `RemoteKineticaPlayer(bundle, sceneId)`는 `sceneId`가 바뀌면 화면 문서에 적힌 전환을 재생합니다(`transition`, `forward` 파라미터)
- `encode`는 잘못된 문서를 쓰지 않고 위반 코드를 알려 줍니다. 저장소는 이때 `SaveFailure.InvalidDocument`를 돌려줍니다. `SceneCodec.validate`로 미리 검사할 수도 있습니다

#### 그 밖

- `kinetica-java`: Java 호스트용 정적 진입점
- `kinetica-export-android`: 화면과 같은 그리기 코드로 만드는 PNG 내보내기
- 샘플 앱은 `sample-showcase` 하나입니다. 모양 시트 한 곳에서 표면 종류와 프리셋, 밝기, 디자인 언어를 고르고, UI 키트 허브(표면과 디자인, 앱 화면 예 14개, 컴포넌트, 모션 효과, 화면 이동 전환 22종, 장면 전환 14종)와 고급 기능(대시보드 보드, 화면 만들기, 서버 화면, XML 화면)을 엽니다
- `consumer-check`: 배포 artifact만으로 호스트 시나리오를 빌드하고 계측 테스트를 돌립니다. Material 3 앱 안의 디자인 언어, `PushNavigation` 전환과 예측형 뒤로 가기 취소, Navigation Compose `NavHost`와 `KNavTransitions`, 서버 번들 장면 전환, `KIcons`, 전화 키패드, 아일랜드 갱신 시나리오가 들어 있습니다

### 공개 전 스냅샷을 쓰던 호스트

mavenLocal 등으로 공개 전 빌드를 미리 쓰던 호스트만 보면 됩니다. 이번 버전에서 처음 쓰는 경우에는 건너뛰어도 됩니다.

#### 동작이 바뀐 것

- 대비: 밝은 표면의 `Tonal`, `Outlined`, `Text` 버튼 글자(배너 안 글자 버튼 포함)는 대비 4.5:1을 넘도록, 모자랄 때만 강조색을 필요한 만큼 어둡게 씁니다. 전에는 3:1 기준이라 기본 강조색이 3.6:1이었습니다
- 대비: 채운 버튼, 배지, 칩, 세그먼트 선택, 지도 묶음 핀처럼 흰 글자가 올라가는 채운 면은 4.5:1을 넘을 만큼만 진하게 칠합니다. 기본 강조색 #0A84FF는 #0975E2로, 빨강은 #DF342A, 초록은 #24883D가 됩니다. 글자, 테두리, 옅은 칠에 쓰는 강조색은 그대로입니다
- 글래스 `intensity`는 블러 반경을 바꾸지 않고, 흐린 배경을 덮는 정도(알파)만 바꿉니다. 움직이는 배경은 모달이 열려 있는 동안 멈춥니다(`shouldPauseBackground()`)
- `KChatInput`의 화면 키보드 Enter 키가 기본으로 보내기 키가 됩니다. 예전처럼 쓰려면 `imeSend = false`를 넘깁니다. 다이얼로그나 시트 안의 받은 말풍선은 그림자 없이 옅게 그립니다
- `KSearchBar`, `KSearchView`, `KTagInput`, `KChatInput`의 이름은 편집 노드의 contentDescription이 아니라 입력란 안 글자로 내보냅니다. 글을 쓰는 동안에도 투명하게 남겨 TalkBack이 계속 읽습니다. 테스트에서 이 입력란을 contentDescription으로 찾았다면 `hasSetTextAction()`이나 글자로 찾도록 바꿉니다
- `KSegmentedControl`(하나만 고르는 컨트롤)의 TalkBack 역할 기본값이 `Role.Tab`에서 `Role.RadioButton`으로 바뀌었습니다. 아래 내용을 통째로 바꾸는 탭처럼 쓴다면 `role = Role.Tab`을 넘깁니다. 이름은 기본으로 2줄까지 줄을 바꾸므로, 한 줄로 두려면 `maxLines = 1`을 넘깁니다
- `KSwitch`의 `accent` 기본값이 `Color.Unspecified`가 되었습니다. `Capsule` 모양에서는 전처럼 `positive` 색을 씁니다. `onCheckedChange`에 `null`을 넘기면 표시만 하는 스위치가 됩니다
- `KChipGroup`의 칩은 `KFilterChip`과 같은 모양으로 그립니다. 기본값(글래스 표면, `Accent` 톤)에서 고른 칩은 강조색으로 채우고, 글자는 대비에 맞춰 흰색이나 검은색으로 씁니다
- `KToggleGroup`의 이름은 큰 글자에서 말줄임 대신 두 줄까지 넘어가고, 모든 칸이 가장 높은 칸에 맞춰 높아집니다
- `KExpandHost`의 `cornerRadius` 기본값이 `24.dp`에서 `Dp.Unspecified`(디자인 언어의 card 모서리)가 되었습니다. 기본 디자인 언어에서는 그대로 24dp입니다
- `KIslandState.update`가 `Unit` 대신 `Boolean`을 돌려줍니다. `compactText`가 없는 내용으로 바꾸면 알림을 펼칩니다
- `KMiniPlayer`의 `collapsed`, `expanded` 슬롯이 진행도를 `() -> Float`로 받습니다
- `KImageViewer`가 열려 있는 동안 사진 목록이 비면 바로 닫고 `onDismiss`를 부릅니다
- `KineticaScaffold`의 `indication` 기본값은 `KineticaTheme`을 따릅니다. 테마에서 `indication = null`로 끄면 스캐폴드도 호스트 값을 건드리지 않습니다
- `KineticaBoardState.activeColumns`가 Compose 상태가 되었습니다. 컴포지션에서 읽으면 열 수가 바뀔 때 다시 그려집니다
- `SceneLayout`은 숨긴 노드(`visible = false`이거나 불투명도가 0인 노드, 또는 그런 노드의 자손) 안의 호스트 뷰를 컴포지션에 남겨 상태를 지킵니다(`HiddenSlotPolicy.Keep`). 숨긴 동안 비용을 줄이려면 `hiddenSlots = HiddenSlotPolicy.Dispose`를 넘깁니다
- 편집기 상단 도구 모음은 추가, 실행 취소, 보기 세 묶음의 캡슐로 바뀌었고, 레이어 패널의 표시와 잠금은 눈과 자물쇠 아이콘 토글로 바뀌었습니다

#### 바뀐 이름

UI 키트를 글래스 전용에서 표면을 고르는 키트로 바꾸면서 이름을 정리했습니다. 호환용 옛 이름은 남기지 않았습니다.
블러 효과 자체의 이름(`Modifier.glass`, `GlassStyle`, `GlassStyles`, `GlassBackdrop`, `glassBackdrop`, `rememberGlassBackdrop`, `LocalGlassBackdrop`, `LocalGlassStyle`)은 그대로입니다.

| 예전 이름 | 새 이름 |
| --- | --- |
| `GlassTheme`, `GlassScaffold`, `GlassColors` | `KineticaTheme`, `KineticaScaffold`, `KineticaColors` |
| `LocalGlassColors`, `LocalGlassContentColor`, `LocalGlassTextStyle` | `LocalKineticaColors`, `LocalKineticaContentColor`, `LocalKineticaTextStyle` |
| `GlassTheme.style` | 표면은 `KineticaTheme.surface`, 블러 기본 스타일은 `KineticaTheme.glassStyle` |
| 컴포넌트 `Glass*`(`GlassButton`, `GlassSheet`, `GlassDialog`, `GlassTabBar`, `GlassTextField`, `GlassSurface` 등) | `K*`(`KButton`, `KSheet`, `KDialog`, `KTabBar`, `KTextField`, `KSurface` 등) |
| 상태, 기본값, enum(`GlassSheetDefaults`, `GlassPosture`, `GlassWindowLayout` 등) | `KSheetDefaults`, `KPosture`, `KWindowLayout` 등 |
| `rememberGlass*State`, `rememberGlassWindowLayout` | `rememberK*State`, `rememberKWindowLayout` |
| `GlassIndication`, `Modifier.glassClickable`, `glassContextMenu` 등 | `KIndication`, `Modifier.kClickable`, `kCombinedClickable`, `kSelectable`, `kToggleable`, `kBadge`, `kContextMenu`, `kTooltip`, `kOrigin`, `kReadableWidth` |
| `GlassButtonKind.Filled`, `Tinted`, `Glass`, `Plain`과 버튼의 `kind`, `accent` | `KVariant.Filled`, `Tonal`, `Glass`, `Text`와 `variant`, `tone` |
| 컴포넌트의 `style: GlassStyle` 파라미터 | `surface: SurfaceStyle`(`SurfaceStyle.Glass(GlassStyles.X)` 또는 `SurfaceStyles.X`) |
| XML `GlassSheets`, `GlassDialogs`, `GlassToasts`, `GlassSurfaceView`, `GlassPopupOptions(style)`, `OnGlassDismissListener` | `KSheets`, `KDialogs`, `KToasts`, `KSurfaceView`, `KPopupOptions(surface)`, `OnPopupDismissListener` |
| XML `app:kinetica_glassStyle`, 테마 속성 `kinetica_glassSurfaceViewStyle`, `GlassSurfaceView.glassStyle` | `app:kinetica_surfaceStyle`(단색 `cleanLight`, `cleanDark`, `solidOneUi`, `solidMaterial`과 톤 `tonal` 등이 추가됨), `kinetica_surfaceViewStyle`, `KSurfaceView.surfaceStyle` |
| `KSwitchVariant.Ios`, `Material`, `OneUi` | `KSwitchVariant.Capsule`, `Check`, `Slim` |
| `KSlider`, `KRangeSlider`의 `activeColor`, `KSwitch`의 `onColor`, `KSlideToConfirm`의 `color` | `accent` |
| `KCalendar`의 `onSelect` | `onSelectionChange` |
| `animateNumberAsState`의 `target` | `targetValue` |
| `MiniPlayerState`, `DetentSheetState`, `TooltipState`, `TooltipPlacement`, `SlideToConfirmState`, `rememberLargeTitleState`와 각각의 `remember*` 함수 | 앞에 `K`를 붙인 이름(`KMiniPlayerState`, `KDetentSheetState`, `KTooltipState`, `KTooltipPlacement`, `KSlideToConfirmState`, `rememberKLargeTitleState` 등) |
| 아바타 데이터 클래스 `KAvatar` | `KAvatarData`. 컴포저블과 이름이 겹쳐서 바꿨고, 컴포저블은 `KAvatar(KAvatarData("민지"))`처럼 그대로 씁니다 |
