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
- 편집기: 효과 미리보기, 밝은 테마(`StudioTheme.light`/`system`)
- `kinetica-ui`(새 모듈): 글래스 UI 키트
  - `GlassScaffold`, `GlassSurface`
  - 스타일 8종: Liquid, Acrylic, One UI, Vision, Material, Frosted, Clear와 다크 테마용 변형
  - `GlassDialog`, `GlassSheet`, `GlassToast`, `GlassTabBar`
  - 길게 눌러 여는 메뉴(`glassContextMenu`), `GlassSegmentedControl`, `GlassSwitch`, `GlassSlider`, 당겨서 새로 고침(`GlassRefreshBox`)
  - 카드가 커지며 열리는 상세 화면(`GlassExpandHost`), iOS식으로 밀어 넣는 화면 이동(`PushNavigation`, 가장자리 스와이프와 예측 뒤로 가기 지원)
  - 고급 효과: `glowBorder`, `jellyPress`, `magnetic`, `motionTilt`, `RollingText`, `TypewriterText`, `ConfettiHost`
- 샘플: 샘플 앱을 `sample-showcase` 하나로 합침(글래스 UI, 모션 효과, 대시보드 보드, 화면 이동, 장면 전환, 서버 화면, 화면 만들기, XML 화면 예). 소비자 확인 빌드 추가
