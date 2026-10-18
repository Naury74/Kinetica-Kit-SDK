# 33 — 모션 효과 (`NodeEffects`)

호스트 앱의 뷰에 누름 효과, 등장 애니메이션, 반복 애니메이션을 붙인다. 효과는 `NodeEffects` 값 하나로 정하고, 같은 값을 아래 네 곳에서 그대로 쓴다.
글로우 테두리, 젤리 누름, 컨페티처럼 `kinetica-ui`에 들어 있는 효과 수정자는 [35 UI 키트](35-ui-kit.md#효과와-모션)에서 다룬다.

| 쓰는 곳 | 방법 |
| --- | --- |
| 호스트의 Compose 뷰 | `Modifier.kineticaEffects(EffectPresets.button, RoundedCornerShape(16.dp))` |
| 호스트의 XML View | `ViewEffects.apply(view, EffectPresets.button, cornerRadiusDp = 16f)`. 떼어 낼 때는 `ViewEffects.clear(view)` |
| 장면 노드와 호스트 뷰 슬롯 | `SceneNode.effects`, 편집기 속성 패널의 **모션 효과** |
| 보드 위젯 | `BoardWidget(…, effects = EffectPresets.card)` |

## 효과

| 분류 | 종류 |
| --- | --- |
| 누를 때 `press` | 리플은 누른 위치에서 퍼진다. 축소는 작아졌다가 튕기듯 돌아오고, 기울이기는 누른 쪽으로 3D로 기운다. 그 밖에 어둡게, 그림자가 커지며 떠오르는 떠오르기, 손을 뗄 때 조각이 사방으로 튀는 버스트가 있다. 여러 종류를 함께 쓸 수 있다 |
| 등장 `appear` | 페이드, 위, 아래, 왼쪽, 오른쪽 슬라이드, 팝, 줌, 플립, 블러. 블러는 흐린 상태에서 선명해지며 나타나고, Android 12 미만에서는 페이드로 대신한다. 재생 시간과 지연을 ms로 정한다 |
| 반복 `loop` | 펄스, 숨쉬기, 떠다니기, 바운스, 흔들기, 시머, 글로우, 회전, 하트비트, 위글, 핑. 하트비트는 두 번 뛰고 쉬며, 핑은 라이브 표시에 쓴다. 주기(ms), 세기, 색을 정한다 |

`kinetica-presets`의 `EffectPresets`에 바로 쓸 수 있는 프리셋이 있다. `button`, `card`, `listItem(index)`, `badge`, `skeleton`, `callToAction`, `attention`,
`floating`, `like`, `liftCard`, `live`, `heartbeat`다. `listItem(index)`는 항목이 차례로 등장하게 하고, `like`는 축소와 버스트를,
`liftCard`는 떠오르기와 블러 등장을 합친 것이다. `live`는 초록색 핑이다. 값을 바꾸려면 `copy`를 쓴다.

## 화면 전환과 숫자

- `ContentTransitions`는 Compose `AnimatedContent`의 `transitionSpec`에 넣는 전환 모음이다. 서로 관련 없는 화면에는 `fadeThrough`를 쓴다.
  앞뒤 순서가 있는 화면에는 `sharedAxisX`나 `sharedAxisY`를 쓰고 방향은 `forward`로 정한다. 목록에서 상세로 갈 때는 `sharedAxisZ`,
  시트에는 `slideUp`, 완료 화면에는 `pop`이 어울린다.

  ```kotlin
  AnimatedContent(tab, transitionSpec = { ContentTransitions.sharedAxisX(forward = targetState > initialState) }) { Page(it) }
  ```
- 장면 사이의 전환은 `AnimatedScene`이 맡는다. 넘긴 문서의 `documentId`가 바뀌면 이전 장면에서 새 장면으로 넘어간다. 전환 종류는
  `transition` 파라미터로 주거나, 새 문서의 `transition`에 적는다([34 장면 JSON](34-scene-json.md#장면-전환-transition)). `fade`, `morph`, `dissolve`, `slide`, `push`,
  `cover`, `uncover`, `wipe`, `iris`, `zoom`, `zoomBlur`, `rotate`, `flip`, `cube`가 있고, 뒤로 갈 때 `forward = false`를 주면 같은 전환을 거꾸로 재생한다.
  서버 번들 화면은 `RemoteKineticaPlayer(bundle, sceneId)`의 `sceneId`만 바꾸면 같은 방식으로 전환된다.

  ```kotlin
  AnimatedScene(scenes.getValue(current), forward = !goingBack) { scene -> KineticaPlayer(scene, onAction = ::handle) }
  ```

  예측형 뒤로 가기처럼 손가락을 따라가야 하면 `SceneTransition(from, to, spec, progress = { backProgress })`로 진행도를 직접 넘긴다.
  Compose 화면 두 개에 같은 전환을 쓰려면 두 화면에 `Modifier.sceneTransitionLayer(spec, role) { progress }`를 각각 붙인다. 어느 화면을 위에 그릴지는 `spec.incomingOnTop(forward)`로 정한다.
- 장면이나 서버 화면 안의 숫자는 재생기가 알아서 애니메이션한다. 길이는 `KineticaPlayer(numberAnimationMs = …)`로 정한다. 자세한 내용은 [30 UI 조립](30-ui-composer.md#바인딩과-동작)에 있다.
- `animateNumberAsState(targetValue)`는 값이 바뀌면 지금 보이는 숫자에서 새 값까지 올라가거나 내려간다. 대시보드 매출이나 카운터에 알맞다. 큰 수도 Double로 계산한다.

## 어디에 붙일까

- 보드 위젯에는 `BoardWidget(effects = …)`만 쓴다. 위젯 안 내용에도 `Modifier.kineticaEffects`로 등장 효과를 붙이면 두 번 등장한다.
  위젯 안의 버튼처럼 일부에만 누름 효과를 주려면 그 요소에만 붙인다.
- 장면 노드는 문서의 `effects`를 쓰고, 편집기에서는 **모션 효과**에서 정한다. 호스트 Compose 뷰는 `Modifier.kineticaEffects`를, XML View는 `ViewEffects.apply`를 쓴다.
- Compose에서는 `Modifier.kineticaEffects`를 수정자 체인 맨 앞에 둔다. 적어도 `background`, `clip`, `clickable`보다는 앞이어야 한다. 그래야 크기와 기울기가 바뀔 때
  배경과 리플도 함께 움직이고, 어둡게와 리플이 배경 위에 덮인다.
- `LazyColumn`은 스크롤해 새로 들어온 항목도 등장 애니메이션을 다시 재생한다. `EffectPresets.listItem(index)`의 지연은 10번째 항목(600ms)에서 멈추므로,
  목록 전체 위치 대신 처음 화면에 보이는 순서를 넘긴다.

## 규칙

- 누름 효과는 터치를 지켜보기만 하고 소비하지 않는다. 그래서 호스트 뷰의 클릭, 스크롤, 입력은 그대로 동작한다.
  XML View에서는 foreground drawable로 View의 pressed 상태를 따라가므로 터치 리스너나 클릭 리스너를 건드리지 않는다.
  pressed 상태는 클릭 리스너를 달아 클릭할 수 있게 된 View에서만 바뀐다.
- 시스템의 '애니메이션 삭제' 설정이 켜져 있으면 등장 애니메이션과 반복 애니메이션은 건너뛰고 누름 효과만 쓴다. 개발자 옵션에서 애니메이터 길이 배율을 0으로 둔 경우도 같다.
- 장면 안의 효과는 재생기, 미리보기, 보드 사용 모드에서만 움직인다. 편집 캔버스와 보드 편집 모드에서는 움직이지 않는다.
- 떠다니기, 바운스, 글로우의 거리는 Compose와 XML에서는 dp이고, 장면과 보드에서는 문서 단위다.
- 효과는 장면 JSON에 `effects`로 저장되므로 서버 화면으로도 내려보낼 수 있다. 읽을 때 값을 검증한다. 같은 종류의 누름 효과는 하나만 둘 수 있고,
  세기는 종류별 범위 안이어야 한다. 재생 시간은 0..60초, 반복 주기는 100ms..60초다.
- 버스트와 핑은 노드 바깥까지 그린다. Compose에서는 효과를 `clip`보다 앞에 붙이고, 이웃 요소 위로 보이게 하려면 `zIndex`를 준다.
  XML View에서는 부모 ViewGroup의 오버레이에 그리므로 부모에 여백을 둔다.
- 누르자마자 떼는 빠른 탭에서도 누름 상태를 곧바로 바꾸므로, 손을 뗄 때의 애니메이션이나 버스트가 빠지지 않는다.
- Compose 효과는 Path, Brush, Paint를 크기나 모양이 바뀔 때만 새로 만들고 프레임마다 만들지 않는다. 글로우의 블러 반경은 1px 단위로 바꾼다.
- XML View의 리플, 어둡게, 시머는 `ViewEffects.apply(view, effects, cornerRadiusDp)`에서 정한 둥근 사각형 안에 그린다.
  글로우는 elevation 그림자로 흉내 내며 View의 `outlineProvider` 윤곽을 따른다.

## 확인

`sample-showcase`의 **모션 효과** 화면에서 UI 키트의 효과 수정자와 프리셋 타일 9개를 눌러 볼 수 있다. 효과를 붙여도 클릭은 그대로 전달되므로 타일을 누를 때마다 누른 횟수가 올라간다.
**대시보드 보드**에서는 매출과 주문 위젯에 카드 효과가, 공지 위젯에 눈길을 끄는 흔들기 효과가 붙어 있고, 매출 숫자는 올라가며 바뀐다.
첫 화면 고급 기능의 **XML 화면**에서는 XML 버튼에 붙인 `ViewEffects`를 확인한다.
