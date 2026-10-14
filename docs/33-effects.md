# 33 — 움직임 효과 (NodeEffects)

호스트 앱이 자기 뷰에 리플·눌림·등장·반복 같은 움직임을 한 줄로 붙이는 기능이다. 같은 값(`NodeEffects`)을
네 곳에서 쓴다.

| 쓰는 곳 | 방법 |
| --- | --- |
| 호스트의 Compose 뷰 | `Modifier.kineticaEffects(EffectPresets.button, RoundedCornerShape(16.dp))` |
| 호스트의 XML View | `ViewEffects.apply(view, EffectPresets.button, cornerRadiusDp = 16f)` / 떼기 `ViewEffects.clear(view)` |
| 장면 노드(호스트 뷰 자리 포함) | `SceneNode.effects`, 편집기 속성 패널 **움직임 효과** |
| 보드 위젯 | `BoardWidget(…, effects = EffectPresets.card)` |

## 효과

| 묶음 | 종류 |
| --- | --- |
| 누를 때 `press` | 리플(누른 자리에서 퍼짐), 눌림(작아졌다 튕김), 기울기(누른 쪽으로 3D), 어둡게. 여러 개를 겹쳐 쓴다 |
| 나타날 때 `appear` | 페이드, 아래·위·오른쪽·왼쪽에서, 튕기며, 줌, 뒤집기. 시간·지연(ms) |
| 계속 반복 `loop` | 두근, 숨쉬기, 둥실, 통통, 흔들기, 반짝임, 빛 번짐, 회전. 주기(ms)·세기·색 |

바로 쓰는 묶음 `EffectPresets`(`kinetica-presets`): `button`, `card`, `listItem(index)`(차례로 등장), `badge`, `skeleton`,
`callToAction`, `attention`, `floating`. `copy`로 고쳐 쓴다.

## 규칙

- 누름 효과는 손가락을 관찰만 하고 소비하지 않는다. 호스트 뷰의 클릭·스크롤·입력은 그대로 동작한다.
  XML View에서는 View의 눌림 상태를 foreground drawable로 따라가므로 터치·클릭 리스너를 건드리지 않는다.
  눌림 상태는 클릭할 수 있는 View에서만 바뀐다(클릭 리스너를 단 View).
- 시스템 "애니메이션 줄이기"(애니메이터 배율 0)가 켜져 있으면 나타나기·반복은 하지 않고 누름 효과만 쓴다.
- 장면에서는 재생기·미리보기·보드 사용 모드에서만 움직이고, 편집 캔버스·보드 편집 중에는 움직이지 않는다.
- 거리(둥실·통통·빛 번짐)는 Compose·XML에서는 dp, 장면·보드에서는 문서 단위다.
- 효과는 장면 JSON에 `effects`로 저장되어 서버 화면으로도 내려보낼 수 있다. 값은 검증한다(같은 누름 종류 한 번,
  세기 범위, 시간 0..60초, 반복 주기 100ms 이상).
- XML View의 리플·어둡게·반짝임은 `ViewEffects.apply(view, effects, cornerRadiusDp)`의 둥근 사각형 안에 그린다.
  빛 번짐은 그림자 높이로 흉내 내며 View 윤곽(`outlineProvider`)을 따른다.

## 확인

`sample-board` → **효과 예**: Compose 타일 7종·차례로 나타나는 목록·XML View 두 개. 눌러도 클릭 수가 올라간다.
가게 보드의 매출·주문 위젯은 카드 효과, 공지는 주의(흔들기) 효과다.
