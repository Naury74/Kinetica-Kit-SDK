# 33 — 움직임 효과 (NodeEffects)

호스트 앱이 자기 뷰에 리플, pressed 상태, 등장, 반복 애니메이션 같은 효과를 붙이는 기능이다. 같은 값(`NodeEffects`)을
다음 네 곳에서 쓴다.

| 쓰는 곳 | 방법 |
| --- | --- |
| 호스트의 Compose 뷰 | `Modifier.kineticaEffects(EffectPresets.button, RoundedCornerShape(16.dp))` |
| 호스트의 XML View | `ViewEffects.apply(view, EffectPresets.button, cornerRadiusDp = 16f)`, 제거는 `ViewEffects.clear(view)` |
| 장면 노드(호스트 뷰 슬롯 포함) | `SceneNode.effects`, 편집기 속성 패널의 **모션 효과** |
| 보드 위젯 | `BoardWidget(…, effects = EffectPresets.card)` |

## 효과

| 분류 | 종류 |
| --- | --- |
| 누를 때 `press` | 리플(누른 위치에서 퍼짐), 축소(작아졌다가 튕겨 돌아옴), 기울이기(누른 쪽으로 3D로 기울어짐), 어둡게, 떠오르기(그림자가 커지며 떠오름), 버스트(뗄 때 조각이 사방으로 튐). 여러 개를 함께 쓸 수 있다 |
| 등장 `appear` | 페이드, 위로·아래로·왼쪽으로·오른쪽으로 슬라이드, 팝, 줌, 플립, 블러(블러가 걷히며 등장, Android 12 미만은 페이드로 대신함). 시간과 지연(ms)을 정한다 |
| 반복 `loop` | 펄스, 숨쉬기, 떠다니기, 바운스, 흔들기, 시머, 글로우, 회전, 하트비트(두 번 뛰고 쉼), 위글, 핑(라이브 표시용). 주기(ms), 세기, 색을 정한다 |

`kinetica-presets`의 `EffectPresets`에는 바로 쓸 수 있는 프리셋이 있다. `button`, `card`, `listItem(index)`(차례로 등장), `badge`, `skeleton`,
`callToAction`, `attention`, `floating`, `like`(축소 + 버스트), `liftCard`(떠오르기 + 블러 등장), `live`(초록 핑), `heartbeat`.
값을 바꾸려면 `copy`를 쓴다.

## 화면 전환과 숫자

- `ContentTransitions`는 Compose `AnimatedContent`의 `transitionSpec`에 넣는 전환 모음이다. 서로 관련 없는 화면에는 `fadeThrough`,
  앞뒤 순서가 있는 화면에는 `sharedAxisX`나 `sharedAxisY`(방향은 `forward`로 정함), 목록에서 상세로 갈 때는 `sharedAxisZ`, 시트에는 `slideUp`,
  완료 화면에는 `pop`을 쓴다.

  ```kotlin
  AnimatedContent(tab, transitionSpec = { ContentTransitions.sharedAxisX(forward = targetState > initialState) }) { Page(it) }
  ```
- 장면이나 서버 화면 안의 숫자는 재생기가 알아서 올라가게 애니메이션한다(`KineticaPlayer(numberAnimationMs = …)`, [docs/30](30-ui-composer.md)).
- `animateNumberAsState(targetValue)`는 값이 바뀌면 지금 보이는 숫자에서 새 값까지 올라가거나 내려간다. 대시보드 매출이나 카운터에 쓰면 된다. 큰 수도 Double로 계산한다.

## 어디에 붙일까

- 보드 위젯에는 `BoardWidget(effects = …)`만 쓴다. 위젯 안 내용에도 `Modifier.kineticaEffects`로 등장 효과를 붙이면 두 번 등장한다.
  위젯 안의 버튼처럼 일부에만 누름 효과를 주려면 그 내용에 붙인다.
- 장면 노드는 문서의 `effects`(편집기의 **모션 효과**)를, 호스트 Compose 뷰는 `Modifier.kineticaEffects`를, XML View는 `ViewEffects.apply`를 쓴다.
- Compose에서는 `Modifier.kineticaEffects`를 수정자 체인 맨 앞, 적어도 `background`, `clip`, `clickable`보다 앞에 둔다. 그래야 크기와 기울기 변화가
  배경과 리플까지 함께 움직이고, 어둡게나 리플이 배경 위에 덮인다.
- `LazyColumn`은 스크롤해 새로 들어온 항목도 등장 애니메이션을 다시 재생한다. `EffectPresets.listItem(index)`의 지연은 10번째 항목(600ms)에서 멈추므로,
  목록 전체 위치 대신 처음 화면에 보이는 순서를 넘긴다.

## 규칙

- 누름 효과는 터치를 지켜보기만 하고 소비하지 않는다. 그래서 호스트 뷰의 클릭, 스크롤, 입력은 그대로 동작한다.
  XML View에서는 View의 pressed 상태를 foreground drawable로 따라가므로 터치 리스너나 클릭 리스너를 건드리지 않는다.
  pressed 상태는 클릭할 수 있는 View, 즉 클릭 리스너를 단 View에서만 바뀐다.
- 시스템의 '애니메이션 삭제' 설정(애니메이터 배율 0)이 켜져 있으면 등장·반복 애니메이션은 하지 않고 누름 효과만 쓴다.
- 장면에서는 재생기, 미리보기, 보드 사용 모드에서만 움직인다. 편집 캔버스나 보드 편집 중에는 움직이지 않는다.
- 둥실, 통통, 글로우의 거리는 Compose와 XML에서는 dp이고, 장면과 보드에서는 문서 단위다.
- 효과는 장면 JSON에 `effects`로 저장되므로 서버 화면으로도 내려보낼 수 있다. 읽을 때 값을 검증한다. 같은 누름 종류는 한 번만 쓸 수 있고,
  세기는 정해진 범위 안이어야 하며, 시간은 0..60초, 반복 주기는 100ms 이상이어야 한다.
- 버스트와 핑은 노드 바깥까지 그린다. Compose에서는 효과를 `clip`보다 앞에 붙이고, 이웃 요소보다 위에 보이게 하려면 `zIndex`를 준다.
  XML View에서는 부모 ViewGroup의 overlay에 그리므로 부모에 여백을 둔다.
- 누르자마자 떼는 빠른 탭에서도 누름 상태를 바로 바꾸므로 떼는 애니메이션이나 버스트를 놓치지 않는다.
- Compose 효과는 경로, 붓, Paint를 크기나 모양이 바뀔 때만 만들고 프레임마다 새로 만들지 않는다. 글로우의 블러 반경은 1px 단위로 바꾼다.
- XML View의 리플, 어둡게, 시머는 `ViewEffects.apply(view, effects, cornerRadiusDp)`로 정한 둥근 사각형 안에 그린다.
  글로우는 그림자 높이로 흉내 내며 View 윤곽(`outlineProvider`)을 따른다.

## 확인

`sample-showcase`의 **모션 효과** 화면에서 누름·등장·반복 타일 9종과 고급 효과([docs/35](35-glass-ui.md))를 볼 수 있다. **대시보드 보드**에서는 매출 숫자가 올라가는 모습을,
첫 화면 맨 아래 **XML 레이아웃으로 만든 화면 보기**에서는 XML 버튼에 붙인 ViewEffects를 확인한다. 효과가 있어도 누르면 클릭 수가 올라간다. 가게 보드의 매출·주문 위젯에는 카드 효과가 있고(매출 숫자는 올라가며 바뀐다),
공지 위젯에는 주의를 끄는 흔들기 효과가 있다.
