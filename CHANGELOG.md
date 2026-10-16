# Changelog

문서 스키마 버전(`schemaVersion`)은 SDK 버전과 따로 관리합니다. 현재 스키마 버전은 1입니다.

## 1.0.0-alpha01

첫 공개 버전입니다. docs 17 백로그의 M0~M5 대부분을 구현했습니다. 알파 기간에는 공개 API가 바뀔 수 있습니다.

- core: 장면 문서와 검증, 3x3 행렬과 viewport, 명령과 reducer, hit test, 변형·정렬·그룹 계산
- history: 트랜잭션 단위 실행 취소, `KineticaSession`
- serialization: 스키마 버전 1 JSON, migration 구조, 파일 저장소, JSON 중첩 깊이 상한
- motion: keyframe 평가, easing, 재생 제어, 장면 morph, 전환 진행률
- guides, presets: 가이드라인 스냅, 모션 프리셋 6종
- render, gestures, editor, player, export: Compose 그리기, 포인터 상태 기계, 편집 화면, 재생기와 전환, PNG 내보내기
- java: Java 호스트용 정적 진입점
- UI 조립(ADR-0006): Frame, HostSlot, 데이터 바인딩, 동작, 노드마다 Compose 요소로 그리는 `SceneLayout`, 오토 레이아웃, 제약, `SceneFit.Responsive`
- board: 호스트 위젯 보드. 자유 배치와 격자 배치, 가장자리 분할, 길게 눌러 편집, 자동 스크롤, 접근성 동작, 배치 저장과 위젯 목록 변경 대응, XML View 지원
- 디자인: 다각형, 별, 선, 화살표, 그라디언트, 여러 겹 그림자, 레이어 블러(내보내기 포함), 점선, 모서리별 반지름, 리치 텍스트, 기기 프레임 목업
- 컴포넌트와 인스턴스(덮어쓴 값 유지), 디자인 토큰, 기본 키트 `UiKit`
- motion: 너비, 높이, 모서리, 선 색, 블러, 그림자 애니메이션
- 서버 주도 화면: `RemoteSceneLoader`, `RemoteKineticaPlayer`
- 오토 레이아웃 줄 바꿈, 최소·최대 크기, 텍스트에 맞춘 자동 크기(`TextMeasurer`/`SceneTextMeasurer`), 끌어서 순서 바꾸기
- 보드 위젯 스택, 서버 화면 여러 개를 번들로 받기, XML 효과의 pressed 상태 추적과 둥근 모서리
- 모션 효과 `NodeEffects`(누름, 등장, 반복), Compose `Modifier.kineticaEffects`, XML `ViewEffects`, 보드 위젯 효과, `EffectPresets`
- 그라디언트 애니메이션, 서버 화면 etag 저장과 서명 확인, 보드에 같은 위젯 여러 개 두기, 키보드 편집
- 보드 정리: 뺀 위젯의 id를 다시 쓰지 않음(`itemIds`), XML 상태 저장과 길게 누르기, RTL 언어 지원, 위로 끌면 열리는 Windows식 스냅 레이아웃 선택(`SnapLayouts`)
- 장면: 접근성 정보(`NodeAccessibility`), 숫자 서식(`TextNumberFormat`), 동작에 값 넘기기(`SceneAction.parse("name?key=value")`), 잘못된 문서 저장 거부(`SceneCodec.validate`), 숫자 올라가는 애니메이션, pressed 상태 변형, 재생기의 "애니메이션 삭제" 대응, XML 재생기 `KineticaPlayerView`
- 편집기: 효과 미리보기, 밝은 테마(`StudioTheme.light`/`system`), 여러 노드를 한꺼번에 크기 조절·회전(`TransformOps.resizeMany`/`rotateMany`)
- 그리기: 반투명 그룹을 레이어 하나로 합성해 겹친 자식이 더 진해 보이지 않음, API 26·27에서도 사진 EXIF 방향 반영
- `kinetica-ui`(새 모듈): 글래스 UI 키트
  - `GlassScaffold`, `GlassSurface`
  - 스타일 8종: Liquid, Acrylic, One UI, Vision, Material, Frosted, Clear와 다크 테마용 변형
  - `GlassDialog`, `GlassSheet`, `GlassToast`, `GlassTabBar`
  - 길게 눌러 여는 메뉴(`glassContextMenu`), `GlassSegmentedControl`, `GlassSwitch`, `GlassSlider`, 당겨서 새로 고침(`GlassRefreshBox`)
  - 카드가 커지며 열리는 상세 화면(`GlassExpandHost`), iOS식으로 밀어 넣는 화면 이동(`PushNavigation`, 가장자리 스와이프와 예측 뒤로 가기 지원)
  - 버튼·스테퍼·칩·진행 표시·배지, 휠 피커·날짜·시간 선택기, 검색창·입력란, 접히는 큰 제목 바, 캐러셀·페이지 점, 카드 더미, 펼침 버튼 메뉴, 다이내믹 아일랜드식 알림, 되돌리기 스낵바
  - 밀어서 동작·순서 바꾸기·아코디언·밀어서 확정, 선·막대·도넛 차트·스파크라인·활동 링, 월 달력·플립 시계·범위 슬라이더, 미니 플레이어·모핑 아이콘·별점·좋아요·아바타·스켈레톤, 여러 높이 바텀 시트·상단 탭·툴팁·기능 안내·서랍
  - 메시 그라디언트 배경, 빛·색·뒤섞임 글자, 흔들기·반짝이·스크롤 시차·스포트라이트, 스타일 Mica·HarmonyOS·Obsidian·Pastel
  - 슬라이더·스위치·세그먼트 `enabled`, 슬라이더 키보드 조절, RTL 배치, TalkBack 문구(영어·한국어)
  - 고급 효과: `glowBorder`, `jellyPress`, `magnetic`, `motionTilt`, `RollingText`, `TypewriterText`, `ConfettiHost`
  - 입력: `GlassCodeInput`(SMS 자동 채우기), `GlassTagInput`, `GlassColorPicker`, `GlassSignaturePad`(이미지, SVG 내보내기), `GlassPasswordField`와 `passwordStrength`
  - 미디어와 피드백: `GlassImageViewer`, `BeforeAfterSlider`, `GlassResultMark`와 `GlassResultDialog`, `GlassOnboarding`, `GlassEmptyState`
  - 적응형: `GlassListDetail`(폴더블 힌지 맞춤), `GlassNavigationRail`, `GlassAdaptiveNavigation`, `GlassFloatingTabBar`. `androidx.window` 의존성이 추가됨
  - 효과 2차: `rippleDistortion`, `ParticleField`, `FlipCard`, `liquidMorph`, `GlassBlobButton`, `GlassBlobGroup`, `holographic`, `tiltOnPress`
- 샘플: 샘플 앱을 `sample-showcase` 하나로 합침(글래스 UI, 모션 효과, 대시보드 보드, 앱 화면 예, 컴포넌트, 화면 이동, 장면 전환, 서버 화면, 화면 만들기, XML 화면 예). 소비자 확인 빌드 추가
- 샘플: 컴포넌트 화면을 기본, 입력, 미디어, 효과 탭으로 나눔. 메일 앱은 펼친 폴더블과 태블릿에서 목록과 본문을 두 칸으로 보여 주고, 음악 앱은 스크롤하면 줄어드는 탭 바 위에 미니 플레이어를 붙임
- 호스트 앱 연동: `GlassTheme`(다크 모드·호스트 강조색·글꼴을 한 번에), 화면 밖 반복 효과 정지, `GlassScaffold` 밖에서 쓰면 별도 창으로 띄우기, 시트·다이얼로그 `contentWindowInsets`, 모달 포커스 가두기, 스낵바 `bottomOffset`(스캐폴드 안이면 탭 바 위에 저절로)
- XML 진입점: `GlassSheets`·`GlassDialogs`·`GlassToasts`·`GlassSurfaceView`, RecyclerView·ViewPager2·CoordinatorLayout·Fragment 대응, View 기본 스타일 테마 속성(`kinetica_glassSurfaceViewStyle`·`kinetica_boardViewStyle`·`kinetica_playerViewStyle`), `ViewEffects.effectsOf`
- Java: `PressEffect.of`, `LoopEffect.of`, `NodeEffects`·`AppearEffect` 생성자 기본값, `NodeEffects.None` 필드
- 편집기 timeline: 두 손가락·Ctrl+휠·버튼으로 시간 축 확대, 맞춤, 배율에 맞춰 바뀌는 눈금자, 재생 위치 따라가기
- 편집기 timeline: 탭, Shift 탭, 길게 눌러 켜는 선택 모드, 사각 선택으로 여러 키 선택. 고른 키를 함께 끌기(실행 취소 한 단계), 복사·붙여넣기(재생 위치로), 삭제. 단축키 Delete, Ctrl+C/V/A, Esc
- 편집기 timeline: easing 곡선 편집기(베지어 조절점 끌기, 프리셋 6종, 곡선을 따라 움직이는 미리보기)
- 편집기 timeline: 곡선 편집기에서 스프링의 감쇠비와 강성, 계단의 칸 수(1~20)를 슬라이더로 바꿉니다. 그래프는 스프링의 넘침과 계단 모양을 그대로 그리고, 손을 뗄 때마다 실행 취소 한 단계로 남습니다.
- 편집기 timeline: 키 줄의 빈 곳을 세로로 끌면 사각 선택 대신 줄 목록이 스크롤됩니다. 사각 선택은 가로로 끌거나 길게 눌러 선택 모드를 켠 뒤 끌어서 시작합니다.
- 편집기 timeline: 키 도구 줄을 늘 같은 높이로 두어, 길게 누른 뒤 이어서 끌 때 키 줄이 밀려 손가락과 사각형이 어긋나던 문제를 고쳤습니다. 회전 방향 칩은 같은 줄 끝으로 옮겼습니다.
- 계단 easing 점프 방식: `EasingSpec.Steps.jump`(`StepJump`: `Start`, `End`, `None`, `Both`)로 CSS `steps()`의 jump-start, jump-end, jump-none, jump-both를 고릅니다. 기본값은 예전과 같은 `End`이고, `None`은 칸이 2개 이상이어야 합니다. JSON의 `steps` easing에 선택 필드 `jump`가 추가되며, 없으면 `end`로 읽고 `end`일 때는 쓰지 않습니다. 스키마 버전은 그대로 1입니다. 곡선 편집기에서도 칸 수 슬라이더 아래의 칩으로 점프 방식을 바꿀 수 있습니다.
- 회전 보간 방향: `Keyframe.rotation`(`RotationDirection`: 가장 짧게, 시계, 반시계, 값 그대로). JSON keyframe에 선택 필드 `rotationDirection`이 추가되며, 없으면 예전처럼 가장 짧은 방향으로 보간합니다. 스키마 버전은 그대로 1입니다.
- 3차 컴포넌트 호스트 연동: XML 화면의 `ComposeView` 안에서 `GlassColorPicker`를 끌면 바깥 `NestedScrollView`가 스크롤되던 문제를 고쳤습니다. 투명도 슬라이더, `BeforeAfterSlider`, `FlipCard`도 자기 방향으로 끄는 동안에는 바깥 View가 가로채지 않고, 반대 방향 끌기는 바깥 스크롤에 넘깁니다.
- `GlassPasswordField`, `GlassTagInput`, `GlassCodeInput`에 `imeAction`과 `keyboardActions`를 추가했습니다. `GlassTagInput`은 입력 중인 글자가 있으면 동작 키로 칩을 만들고, 빈 입력란에서 누르면 `keyboardActions`로 넘깁니다. 이제 빈 입력란에서 완료를 누르면 기본 동작대로 키보드가 내려갑니다.
- `GlassPasswordField`, `GlassTagInput`에 안쪽 입력란에 붙는 `fieldModifier`를 추가했습니다. `modifier`는 예전처럼 바깥 묶음에 붙습니다.
- `BeforeAfterSlider(position, onPositionChange, before, after)`: 손잡이 위치를 호스트가 들고 있는 버전을 추가했습니다.

### 알파 동안 바뀐 이름
- `GlassSlider`·`GlassRangeSlider`의 `activeColor`, `GlassSwitch`의 `onColor`, `GlassSlideToConfirm`의 `color` → `accent`
- `GlassCalendar`의 `onSelect` → `onSelectionChange`, `animateNumberAsState`의 `target` → `targetValue`
- `MiniPlayerState`·`DetentSheetState`·`TooltipState`·`TooltipPlacement`·`SlideToConfirmState`와 `remember*` 함수, `rememberLargeTitleState` → 앞에 `Glass`를 붙인 이름
- `GlassMiniPlayer`의 `collapsed`·`expanded` 슬롯이 진행도를 `() -> Float`로 받음
- 서버 화면·재생기 안정성: 같은 묶음을 기다리던 화면이 먼저 요청한 화면을 떠나면 멈추던 문제, 캐시 일부 손상 시 화면을 다시 받지 못하던 문제, 캐시 저장 실패가 앱을 죽이던 문제, 마이그레이션 예외가 밖으로 새던 문제(`SaveFailure.InvalidDocument` 추가)
- 재생기: 화면이 가려지면 멈추고 돌아오면 이어서 재생, `RemoteKineticaPlayer` 회전 뒤 처음부터 다시 재생하지 않음, 실행 중 바뀐 '애니메이션 삭제' 설정을 따르며 재생 위치·속도를 이어받음
- 보드: 넓은 화면 열 수. `BoardArrangement.Grid(columnsByWidth = GridColumns.Adaptive(160f, maxColumns = 6))` 또는 `GridColumns.breakpoints(600f to 4, 840f to 6)`로 펼친 폴더블과 태블릿에서 열을 늘립니다. 열 수마다 배치를 따로 기억해 넓은 화면에서 옮긴 위젯은 넓은 배치에만 남고, 접었다 펴도 두 배치가 그대로입니다. 처음 넓어질 때는 좁은 배치를 읽는 순서대로 흘려 넣되, 앞쪽 빈칸에 들어가는 위젯을 먼저 채웁니다. 저장 형식은 예전과 호환되며(넓은 배치는 노드 metadata `kinetica.cells`), 기본값은 예전처럼 열 수를 바꾸지 않습니다. XML은 `app:kinetica_minCellWidth`, `app:kinetica_maxColumns`, 코드에서는 `KineticaBoardView.columnsByWidth`. 지금 열 수는 `KineticaBoardState.activeColumns`
- 보드: 가로 스크롤 안이나 가로 `wrap_content`처럼 폭 제한 없이 재면 Constraints 오류로 죽던 문제를 고쳤습니다. 이때는 창 폭으로 그립니다.
- 보드: 저장본으로 열지 못하면 `restoreFailed`로 덮어쓰기 방지, 위젯 100개 편집 끌기에서 다른 위젯 틀을 다시 그리지 않음, 긴 키 `multiple` 위젯 추가 크래시
- `SceneLayout`: 숨긴 노드(`visible = false`, 불투명도 0, 또는 그런 조상 아래) 안의 호스트 뷰가 컴포지션에서 빠져 스크롤 위치와 입력 중인 글자가 초기화되던 문제를 고쳤습니다. 이제 숨긴 동안에도 상태를 유지하고, 그리지 않으며 터치·포커스·TalkBack에서 빠집니다. 숨긴 동안 비용을 줄이려면 `hiddenSlots = HiddenSlotPolicy.Dispose`를 넘깁니다.
- 성능: 글래스 블러를 반경에 맞춘 낮은 해상도로 기록(실기기 컴포넌트 화면 스크롤 끊김 5.15% → 0.1% 안팎)
- 미니 플레이어: `GlassMiniPlayerBar`와 `GlassMiniPlayerFullScreen`을 추가했습니다. 막대를 `GlassFloatingTabBar`의 `accessory` 같은 다른 컴포넌트 안에 두고, 그 자리에서 앨범 아트가 이어지며 커지는 전체 화면 플레이어를 펼칩니다. 기존 `GlassMiniPlayer`는 그대로 씁니다.
- `GlassListDetail`: 상세 칸 배경 `detailBackground`를 추가했습니다. 두 칸 배치에서는 상세가 바뀌어도 그대로 있고, 좁은 화면에서는 상세와 함께 밀려 들어옵니다.
- `GlassImageViewer`: 크기가 없는 Painter(`BrushPainter` 등)를 화면 전체에 채워 그립니다. 전에는 세로 그라디언트처럼 한쪽 크기만 0인 Painter가 보이지 않았습니다. 열린 채 사진 목록이 비면 바로 닫히고 `onDismiss`를 부릅니다.
- 시스템 바 아이콘: `GlassImageViewer`, 펼친 미니 플레이어, `GlassOnboarding`이 떠 있는 동안 배경에 맞춰 상태 표시줄과 내비게이션 바 아이콘을 밝게 또는 어둡게 바꾸고, 닫으면 되돌립니다. 별도 창으로 뜨면 그 창에 적용합니다. 호스트가 정하는 화면(`GlassExpandHost` 상세 등)에는 `GlassSystemBarIcons(light)`를 둡니다.
- `GlassLargeTitleBar`: 접힌 바의 글래스 배경이 아래 가장자리에서 끊기지 않고 콘텐츠 쪽으로 옅어집니다. `bottom` 줄도 `contentMaxWidth` 안에 가운데 정렬해 넓은 화면에서 큰 제목, 본문과 폭이 맞습니다.
- 보드: 넓은 화면에서 열 수가 늘어날 때 앞쪽 빈칸에 들어가는 위젯을 먼저 채우는 dense 방식으로 흘려 넣어, 큰 위젯 옆과 아래에 빈칸이 남지 않습니다. 이미 편집한 열 수의 배치는 바꾸지 않습니다.
- 편집기: 상단 도구 모음을 아이콘과 이름이 있는 캡슐 묶음(추가, 실행 취소, 보기)으로 바꾸고, 폭이 모자라면 아이콘만 남기거나 가로로 스크롤합니다. `onExit`로 나가기 버튼을 오른쪽 끝에 고정할 수 있고, `StudioToolbarButton`과 `StudioToolbarGroup`을 추가했습니다. 레이어 패널의 표시와 잠금은 눈과 자물쇠 아이콘 토글로 바꿨습니다.
