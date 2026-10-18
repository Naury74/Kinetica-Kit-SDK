# Changelog

장면 문서의 스키마 버전(`schemaVersion`)은 SDK 버전과 따로 관리합니다. 현재 스키마 버전은 1입니다.

## 1.0.0-alpha01

첫 공개 버전입니다. 알파 기간에는 공개 API가 바뀔 수 있습니다.
UI·모션 키트(`kinetica-ui`)를 중심으로, 위젯 보드와 화면 편집기, 재생기, 서버 주도 화면을 함께 제공합니다([ADR-0007](docs/adr/0007-ui-motion-kit-first.md)).

### 새 기능

- `KNavTransitions.push`, `slide`, `slideUp`(`KineticaMotion`): 밀기 전환을 디자인 언어의 빠르기에 맞춥니다.
- 입력 컴포넌트 `KTextField`, `KTextArea`, `KChatInput`의 `TextFieldState` 오버로드와 `KChatInput`의 `maxLength`·`showCounter`·`counterThreshold`(한도에 가까울 때만 글자 수 표시)·`label`(TalkBack 이름), `KIslandContent.leading`과 `KIslandState.show(key, replaceShowing)`, `glowBorder`의 `intensity`(값·람다).

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
- 디자인 언어 `KineticaDesign`과 프리셋 `KineticaDesigns`(`Cupertino`, `Material`, `OneUi`, `Fluent`, `Brutal`, `Soft`): 표면 스타일과 따로 컴포넌트 형태를 고릅니다. 모양 토큰 `KineticaShapes`(모서리 체계 `KCornerStyle`의 둥근, 스쿼클, 잘린, 각진 모서리와 크기 단계, 역할별 `KShapeToken`), `KSquircleShape`, 깊이 표현 `KDepth`(그림자, 테두리, 평면, 오프셋 그림자), 밀도 `KDensity`, 라벨과 제목 굵기, 탭 인디케이터, 목록 구분 `KListSeparator`, 컨트롤 모양 `KineticaControls`(스위치 `Outline`·`Block` 추가, 슬라이더 손잡이 `KSliderThumb` 5종), 모션 성격 `KineticaMotion`. `KineticaTheme(design = …)`로 적용하고 기본값은 지금까지의 모양입니다
- 화면 이동 전환: `PushNavigation(transition = …)`과 방향을 아는 내용 전환 `KTransitionContent`에 넣는 `KTransitions` 프리셋 20종(밀기, 패럴랙스, 슬라이드, 덮기, 드러내기, 확대, 페이드, fade through, shared axis X/Y/Z, 탭 슬라이드, 시트 스택, 카드 스택, 큐브, 뒤집기, 원·마름모·별 모양 드러내기, 블러 디졸브, 와이프 등)과 직접 만드는 `KScreenTransition`. 모두 예측 뒤로 가기 제스처를 따라 되감기고 RTL에서 방향이 뒤집히며 '애니메이션 삭제' 설정이면 바로 바뀝니다. `PushNavigation`의 `paneTitle`로 화면이 바뀔 때 TalkBack이 새 화면 이름을 읽습니다
- Navigation Compose `NavHost`와 `AnimatedContent`용 전환 묶음 `KNavTransitions`(`Push`, `Slide`, `SlideUp`, `SheetStack`, `Zoom`, `Fade`, `FadeThrough`, `SharedAxisX/Y/Z`), `kTransform`, `rememberKNavTransition`
- `KExpandHost`의 컨테이너 변환 모양 `KContainerTransform`(`Card`, `listRow(color)`, `fab(color)`)과 예측 뒤로 가기 제스처 대응
- 화면 전환이 디자인 언어를 따릅니다. `SheetStack`과 `KFullScreenModal`은 sheet 모서리, `CardStack`과 `KExpandHost`의 카드는 card 모서리를 쓰고 기본 스프링은 `KineticaMotion`에 맞춥니다. 기본 디자인 언어에서는 지금까지의 모양 그대로입니다. 직접 만든 전환은 `KTransitionFrame.shapes`로 모서리 크기를 읽습니다
- 선 아이콘 63종 `KIcons`(홈, 검색, 설정, 더하기, 닫기, 방향, 공유, 휴지통, 하트, 별, 메일, 전화와 통화 끊기, 마이크, 영상, 카메라, 사진, 달력, 사람, 장바구니 등). 뒤로와 앞으로, 보내기 화살표는 RTL에서 뒤집힙니다. 목록은 `KIcons.all`에 있습니다
- 통화 컴포넌트: 부품 `KCallControlButton`, `KCallDock`(좁으면 '더보기'로 접고 배지를 합침, 버튼 위치 알림), `KCallTopBar`, `KParticipantTile`(영상 슬롯, 아바타, 이름표, 글로우 테두리, 고정), `KParticipantGrid`(인원과 화면 비율별 격자, 1:1 작은 창, 크게 보기와 한 줄 띠, 쪽 넘김), `KSelfView`, `KCallStatusView`, `KIncomingCall`, `KCallBanner`, `KCallBubble`, `KRaisedHandPill`, `KReactionOverlay`, `KReactionPicker`, `KParticipantRow`, `KAudioRouteList`, `KAudioLevelIndicator`, `KNetworkQualityIndicator`, `KCallTimer`, `KOngoingCallPill`, 바 자동 숨김 `rememberKCallChromeState`, 발표자 지연 `rememberKStableSpeaker`, 통화 아이콘 `KCallIcons`. 완성 화면 `KCallScreen`(상태 `KCallState`와 콜백 `KCallActions`, 넘기지 않은 콜백의 버튼은 감춤), `KIncomingCallScreen`, `KCallLobby`. 영상과 오디오는 다루지 않고 영상은 호스트가 슬롯으로 넣습니다. 영상 위 글래스 바가 장식 애니메이션 때문에 블러를 다시 하지 않도록 타일을 영상 층과 장식 층으로 나눠 그립니다
- `KSelfView`는 던진 속도로 모서리를 고르고 그 속도를 이어받아 붙습니다('애니메이션 삭제'면 바로 붙음)
- `KCallScreen`은 가로로 낮은 화면(높이 480dp 미만)에서 조작 막대를 끝 쪽 세로 막대로 옮깁니다. `dock` 슬롯이 `vertical`을 함께 받습니다
- `KCallScreen(gridColumns)`: 완성 통화 화면에서도 영상 격자 열 수를 호스트가 정합니다
- `KReactionOverlay(confetti)`와 `KCallScreen(confettiReactions)`: 고른 반응(기본 🎉, 부품은 끔)이 오면 색종이를 함께 터뜨립니다
- `KKeypadMode.Phone`: `*`, `0`, `#` 줄과 통화 버튼 자리(`extraKey`)가 있는 전화 키패드. 0을 길게 누르면 `+`를 넘깁니다
- `KIslandState.update(content)`: 보여 주는 알림의 내용만 코루틴 없이 바꾸고, 바꿨는지를 돌려줍니다
- `KListTrailing.Value`의 글자 색 `color`, 지도 핀의 꼬리 끝을 좌표에 맞추는 `Modifier.mapPinAnchor`
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
- 편집기 문서 패널의 '장면 전환' 섹션: 이 장면이 들어올 때의 전환 종류, 방향, 시간을 고르고 실행 취소할 수 있습니다(`SceneCommand.SetTransition`)

#### 재생기와 서버 주도 화면 (`kinetica-player-compose`, `kinetica-serialization`)

- 편집기 없이 재생하는 `KineticaPlayer`, 장면 morph 전환, 끌어서 닫기, Compose `sharedBounds` 연결
- XML 재생기 `KineticaPlayerView`(`setLoader`, `setHostViews`, `refresh`, `kinetica_fit`, `kinetica_autoplay`)
- 서버 화면 `RemoteSceneLoader`, `RemoteKineticaPlayer`: 캐시(`RemoteSceneCache`), 앱에 넣어 둔 기본 문서, 앱을 다시 켜도 유지되는 etag, 서명 확인(`SceneVerifier.signature`, 키 교체용 `anyOf`), 거절 검사(`accept`)
- 여러 화면 번들(`SceneBundleCodec`, `RemoteSceneBundleLoader`, `RemoteKineticaPlayer(bundle, id)`)
- 숫자 데이터가 바뀌면 올라가며 바뀜(`numberAnimationMs`, `animateSceneNumbers`), 재생기의 '애니메이션 삭제' 대응
- 장면 사이 전환 14종(`fade`, `morph`, `dissolve`, `slide`, `push`, `cover`, `uncover`, `wipe`, `iris`, `zoom`, `zoomBlur`, `rotate`, `flip`, `cube`). 장면 JSON 최상위 `transition`(종류, 방향, 시간, easing, 중심)으로 정하고 동작 문자열의 `transition=` 파라미터로 덮어쓸 수 있습니다. 스키마 버전은 1 그대로이고 옛 문서는 그대로 읽힙니다
- 문서가 바뀌면 전환하는 `AnimatedScene`(`forward = false`면 되감기, RTL과 '애니메이션 삭제' 대응), 진행도를 직접 주는 `SceneTransition(from, to, spec, progress)`, Compose 화면에 붙이는 `Modifier.sceneTransitionLayer`. `RemoteKineticaPlayer(bundle, sceneId)`는 `sceneId`가 바뀌면 화면 문서의 전환을 재생합니다(`transition`, `forward` 파라미터)
- 잘못된 문서는 `encode`가 쓰지 않고 위반 코드를 알림, 미리 검사하는 `SceneCodec.validate`

#### 그 밖

- `kinetica-java`: Java 호스트용 정적 진입점
- `kinetica-export-android`: 화면과 같은 그리기 코드로 만드는 PNG 내보내기
- 샘플 앱은 `sample-showcase` 하나입니다. 모양 시트 한 곳에서 표면 종류와 프리셋, 밝기, 형태(디자인 언어)를 고르고, UI 키트 허브(표면과 디자인, 앱 화면 예 14개, 컴포넌트, 모션 효과, 화면 이동 전환 22종, 장면 전환 14종)와 고급 기능(대시보드 보드, 화면 만들기, 서버 화면, XML 화면)을 엽니다
- 배포 artifact만으로 호스트 시나리오를 빌드하고 계측 테스트를 돌리는 `consumer-check`. Material 3 앱 안의 디자인 언어, `PushNavigation` 전환과 예측 뒤로 가기 취소, Navigation Compose `NavHost`와 `KNavTransitions`, 서버 번들 장면 전환, `KIcons`, 전화 키패드, 아일랜드 갱신 시나리오가 들어 있습니다

### 바뀐 것

- 밝은 표면의 `Tonal`, `Outlined`, `Text` 버튼(배너 안 글자 버튼 포함) 글자가 대비 4.5:1을 넘도록, 모자랄 때만 강조색을 필요한 만큼 어둡게 씁니다. 전에는 3:1 기준이라 기본 강조색이 3.6:1이었습니다.

- 글래스 `intensity`는 블러 반경을 바꾸지 않고 흐린 배경을 덮는 정도(알파)로 냅니다. 움직이는 배경은 모달이 열린 동안 멈춥니다(`shouldPauseBackground()`).

- `KChatInput`의 화면 키보드 Enter 키가 기본으로 보내기 키가 됩니다(`imeSend = false`로 되돌림). 다이얼로그·시트 안의 받은 말풍선은 그림자 없이 옅게 그립니다.

알파 개발 중 동작이 바뀐 부분입니다. 공개 전 스냅샷을 쓰던 호스트만 해당합니다.

- `KSegmentedControl`(한 칸 고르기)의 TalkBack 역할 기본값이 `Role.Tab`에서 `Role.RadioButton`으로 바뀌었습니다. 아래 내용을 통째로 바꾸는 탭처럼 쓰면 `role = Role.Tab`을 넘깁니다. 이름은 기본 2줄까지 줄을 바꾸므로 한 줄로 두려면 `maxLines = 1`을 넘깁니다
- `KSwitch`의 `accent` 기본값이 `Color.Unspecified`가 되었습니다. `Capsule` 모양에서는 전처럼 `positive` 색을 쓰고, `onCheckedChange`는 `null`을 받아 표시만 할 수 있습니다
- `KChipGroup`의 칩을 `KFilterChip`과 같은 모양으로 그립니다
- `KChipGroup`의 기본값(글래스 표면, `Accent` 톤)에서 고른 칩을 강조색으로 채우고 글자를 대비에 맞춰 흰색이나 검은색으로 씁니다. 전에는 글래스 위에서 고른 칩 글자가 잘 보이지 않았습니다
- `KToggleGroup`의 이름이 큰 글자에서 말줄임 대신 두 줄까지 넘어가고, 모든 칸이 가장 높은 칸에 맞춰 높아집니다
- `KExpandHost`의 `cornerRadius` 기본값이 `24.dp`에서 `Dp.Unspecified`(디자인 언어의 card 모서리)가 되었습니다. 기본 디자인 언어에서는 그대로 24dp입니다
- `KIslandState.update`가 `Unit` 대신 `Boolean`을 돌려주고, `compactText`가 없는 내용으로 바꾸면 알림을 펼칩니다
- `KMiniPlayer`의 `collapsed`, `expanded` 슬롯이 진행도를 `() -> Float`로 받습니다
- `KineticaScaffold`의 `indication` 기본값이 `KineticaTheme`을 따릅니다. 테마에서 `indication = null`로 끄면 스캐폴드도 호스트 값을 그대로 둡니다
- `KineticaBoardState.activeColumns`가 Compose 상태가 되어, 컴포지션에서 읽으면 열 수가 바뀔 때 다시 그려집니다
- `SceneLayout`은 숨긴 노드(`visible = false`, 불투명도 0, 또는 그런 조상 아래) 안의 호스트 뷰를 컴포지션에 남겨 상태를 지킵니다(`HiddenSlotPolicy.Keep`). 숨긴 동안 비용을 줄이려면 `hiddenSlots = HiddenSlotPolicy.Dispose`를 넘깁니다
- 재생기는 화면이 가려지면 멈추고 돌아오면 이어서 재생합니다. `RemoteKineticaPlayer`는 회전 뒤 처음부터 다시 재생하지 않고, 실행 중 바뀐 '애니메이션 삭제' 설정을 따릅니다
- `EffectPresets.listItem(index)`의 등장 지연은 10번째 항목(600ms)에서 멈춥니다
- 편집기 상단 도구 모음을 캡슐 묶음(추가, 실행 취소, 보기)으로 바꾸고, 레이어 패널의 표시와 잠금을 눈과 자물쇠 아이콘 토글로 바꿨습니다
- 타임라인 키 줄의 빈 곳을 세로로 끌면 사각 선택 대신 줄 목록이 스크롤됩니다. 사각 선택은 가로로 끌거나 길게 눌러 선택 모드를 켠 뒤 끌어서 시작합니다

### 고친 것

- 채운 버튼과 배지, 칩, 세그먼트 선택, 지도 묶음 핀의 흰 글자가 기본 강조색(#0A84FF), 빨강, 초록 위에서 4.5:1이 안 되던 문제. 채우는 색만 기준을 막 넘는 만큼 진하게 합니다(#0975E2, #DF342A, #24883D). 글자, 테두리, 옅은 칠의 강조색은 그대로입니다
- `KParticipantGrid` 여러 쪽 격자에서 덜 찬 마지막 쪽만 칸이 커지던 문제(모든 쪽을 꽉 찬 쪽 기준 칸으로 짜고 가운데에 놓습니다. `columns`는 쪽이 여럿이면 한 쪽 최대 인원을 받습니다)
- `KAlert`와 `KBanner`에서 TalkBack이 상태 아이콘, 제목, 본문에 따로따로 멈추던 문제(한 번에 읽습니다)
- `KChatInput`에 같은 말을 `label`과 `placeholder`로 함께 넘기면 TalkBack이 두 번 읽던 문제
- `KParticipantGrid` 띠와 여러 쪽 격자에서 TalkBack이 화면 밖 참가자에게 닿지 못하던 문제(스크롤 범위와 scrollBy를 알려, TalkBack이 끝에서 넘기고 초점 받은 칸을 화면에 보이게 합니다). 띠 칸은 띠만 담은 스크롤 상자로 옮겨, 왼쪽으로 넘길 때 큰 화면으로 건너뛰거나 오른쪽으로 넘길 때 큰 화면을 중간에 읽지 않습니다
- `KIslandHost` 알림과 쌓인 `KToaster` 토스트가 나타날 때 TalkBack이 알림 문장 앞에 "버튼"을 먼저 읽던 문제(live region 노드의 역할을 뺐습니다)
- TalkBack이 이름 뒤에 안쪽 글자를 또 읽던 문제: `RollingText`(통화 시간 "1:47" 뒤 콜론), `TypewriterText`(치는 중인 글자와 커서), 비밀번호 강도 글자, 태그 칩 이름
- 묶음 재생기(`RemoteKineticaPlayer(bundle, sceneId)`)에서 화면 id를 바꾼 첫 프레임에 이전 화면 문서가 새 화면으로 넘어가던 문제(새 화면 문서에 적힌 전환 대신 이전 문서의 전환을 읽을 수 있었습니다)
- 예측 뒤로 가기 제스처를 취소하면 `PushNavigation`, `KCircularReveal`, `KFullScreenModal`, `KSharedTransitionHost`, `KDrawer`, `KStoryViewer`가 반쯤 되감긴 채 멈추던 문제(취소와 함께 되돌리는 애니메이션도 취소되었습니다)
- 디자인 토큰을 바꾸는 `SceneCommand.SetToken`이 실행 취소되지 않던 문제(`SceneDiff`가 `tokens`를 비교하지 않았습니다)
- `RemoteKineticaPlayer(bundle, sceneId)`에서 화면 id가 바뀐 뒤 문서가 한 프레임 늦게 오면 `AnimatedScene`이 옛 문서를 기억해, 다음 전환(뒤로 가기)에서 나가는 화면을 잘못 그리던 문제
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
- `KChatInput`, `KSearchBar`, `KSearchView`, `KTagInput`에 글을 쓰면 안내 글이 사라지면서 입력란 이름도 없어져 TalkBack이 '편집 상자'라고만 읽던 문제. 이제 안내 글(또는 `label`)을 입력란 접근성 이름으로 내보냅니다
- `RollingText` 값이 짧은 간격으로 연달아 바뀌면 마지막 숫자가 빈칸으로 남을 수 있던 문제. 굴러가는 중에 새 값이 오면 지금 위치에서 이어 굴립니다

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
| 아바타 데이터 클래스 `KAvatar` | `KAvatarData`. 컴포저블과 이름이 겹쳐 바꿨고, 컴포저블은 `KAvatar(KAvatarData("민지"))`처럼 그대로 씁니다 |
