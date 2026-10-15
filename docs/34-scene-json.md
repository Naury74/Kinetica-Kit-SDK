# 34 — 장면 JSON 형식 (스키마 버전 1)

서버가 내려주는 화면(`RemoteSceneLoader`)과 저장 파일(`FileSceneRepository`)이 쓰는 형식이다. 서버에서 JSON을 직접 만들 때 이 문서를 따른다.
가장 쉬운 방법은 앱의 편집기(`KineticaEditor`)로 화면을 만든 뒤 `SceneCodec().encode(document)` 결과를 서버에 올리는 것이다.
코드로 문서를 만들 때는 `encode`가 읽을 수 없는 문서를 거부하고 어떤 규칙을 어겼는지 코드로 알려 준다. `SceneCodec.validate`로 미리 검사할 수도 있다.

## 짧은 예

손으로 쓴 최소한의 예다. 기본값과 같은 항목은 빼도 된다. 이 예는 테스트(`SceneJsonDocTest`)가 실제로 읽어서 확인한다.

```json
{
  "schemaVersion": 1,
  "documentId": "promo",
  "canvas": { "width": 1080, "height": 1920, "background": "#F3F4F8" },
  "nodes": [
    {
      "id": "card", "type": "frame",
      "transform": { "x": 60, "y": 120 },
      "bounds": { "width": 960, "height": 560 },
      "cornerRadius": 48,
      "layout": { "direction": "column", "gap": 24, "padding": [48, 48, 48, 48] },
      "children": ["title", "sales", "logo", "buy"],
      "style": { "fill": "#FFFFFF", "shadows": [{ "color": "#33000000", "x": 0, "y": 12, "blur": 32 }] },
      "effects": { "appear": { "kind": "slideUp" } }
    },
    {
      "id": "title", "type": "text", "parentId": "card",
      "bounds": { "width": 864, "height": 60 },
      "text": "오늘 매출", "fontSize": 44, "fontWeight": 700,
      "style": { "fill": "#1B1E26" }
    },
    {
      "id": "sales", "type": "text", "parentId": "card",
      "bounds": { "width": 864, "height": 100 },
      "text": "-", "fontSize": 80, "fontWeight": 800,
      "bindings": { "text": "sales.today" },
      "numberFormat": { "suffix": "원" }
    },
    {
      "id": "logo", "type": "ellipse", "parentId": "card",
      "bounds": { "width": 96, "height": 96 },
      "style": { "fill": "#5B6CFF" },
      "accessibility": { "label": "가게 로고", "role": "image" }
    },
    {
      "id": "buy", "type": "rect", "parentId": "card",
      "bounds": { "width": 864, "height": 120 }, "cornerRadius": 60,
      "style": { "fill": "#5B6CFF" },
      "action": "open_orders?filter=today",
      "effects": { "press": [{ "kind": "scale" }] },
      "accessibility": { "label": "주문 보기", "role": "button" }
    }
  ],
  "rootOrder": ["card"]
}
```

호스트 앱에서는 다음처럼 쓴다.

```kotlin
RemoteKineticaPlayer(
    loader,
    data = SceneData.of("sales.today" to DataValue.Number(1_284_000.0)),   // "1,284,000원"으로 보이고, 값이 바뀌면 숫자가 올라간다
    onAction = { raw ->
        val action = SceneAction.parse(raw)                                 // name = "open_orders", action["filter"] = "today"
        if (action.name == "open_orders") openOrders(action["filter"])
    },
)
```

## 단위와 좌표

- 길이, 위치, 글자 크기는 모두 **문서 단위**다. 너비 1080인 artboard를 너비 약 393dp인 휴대폰에 맞추면 문서 단위 2.75가 약 1dp가 된다.
  예를 들어 `fontSize: 44`는 16sp쯤이다. 재생기는 `SceneFit`에 따라 artboard를 화면에 맞춰 줄이거나 키운다.
- `transform`의 `x`, `y`는 부모의 왼쪽 위를 기준으로 한다. 최상위 노드의 부모는 artboard다. 자동 레이아웃(`layout`)을 쓰는 Frame의 자식은 레이아웃이 위치를 정하므로 적지 않아도 된다.
- 색은 `#RRGGBB`나 `#AARRGGBB`로 쓴다.

## 문서

| 키 | 필수 | 내용 |
| --- | --- | --- |
| `schemaVersion` | ○ | `1` |
| `documentId` | ○ | 영문자, 숫자, `._:-`로 이루어진 id |
| `canvas` | ○ | `width`, `height`(문서 단위), `background`(배경색, 생략 가능) |
| `nodes` | ○ | 노드 목록. 순서는 상관없다 |
| `rootOrder` | ○ | 최상위 노드 id 목록. 앞에 있는 노드가 아래에 그려진다 |
| `animations` | | 타임라인 목록 |
| `tokens` | | 색 토큰(이름 → 색). 노드의 `style.fillToken`이 참조한다 |
| `extensions` | | 호스트가 따로 보관할 값(이름 → JSON). SDK는 읽지 않는다 |

## 노드 공통

| 키 | 필수 | 내용 |
| --- | --- | --- |
| `id` | ○ | 문서 안에서 겹치지 않는 id. 영문자, 숫자, `._:-`로 1..128자 |
| `type` | ○ | `frame` `group` `rect` `ellipse` `polygon` `path` `text` `image` `host` |
| `parentId` | | 부모 id. 부모의 `children`에도 이 노드가 들어 있어야 한다 |
| `transform` | | `x` `y` `scaleX` `scaleY` `rotation`(도) `pivotX` `pivotY`. 기본값은 이동 없음, 배율 1 |
| `bounds` | 그룹이 아니면 ○ | `width`, `height` |
| `style` | | `fill`(채우기 색, 글자 노드는 글자색) `stroke` `strokeWidth` `opacity`(0..1) `shadows`(최대 8개: `color` `x` `y` `blur` `spread` `inner`) `blur` `gradient` `dash` |
| `visible` | | 기본값 `true` |
| `bindings` | | 속성 → 데이터 키. 바인딩할 수 있는 속성은 `text` `visible` `imageUri` `fill` `opacity` `variant` |
| `action` | | 누르면 호스트의 `onAction`에 넘길 문자열. `이름` 또는 `이름?키=값&키=값` 형태이며 URL에 안전한 문자만 쓴다 |
| `effects` | | 움직임 효과. 아래 표 참고 |
| `accessibility` | | `label`(스크린 리더가 읽을 이름) `role`(`button` `image` `heading`) `hidden`(장식용이라 읽지 않음) |
| `constraints` | | 자동 레이아웃 밖에서 부모 크기가 바뀔 때 따르는 제약. `h`, `v` 각각 `start` `end` `both` `center` `scale` 중 하나 |
| `fill` | | `true`면 자동 레이아웃 안에서 남는 공간을 채운다 |
| `sizeLimits` | | `minWidth` `maxWidth` `minHeight` `maxHeight` |

## 종류별

| type | 키 |
| --- | --- |
| `frame` | `children`, `cornerRadius`, `corners`([왼쪽 위, 오른쪽 위, 오른쪽 아래, 왼쪽 아래]), `clip`, `layout`: `direction`(`row`·`column`) `gap` `padding`([왼쪽, 위, 오른쪽, 아래]) `main`(`start` `center` `end` `between`) `cross`(`start` `center` `end` `stretch`) `hugWidth` `hugHeight` `wrap` `lineGap` |
| `rect` | `cornerRadius`, `corners` |
| `polygon` | `sides`(3..64), `innerRatio`(별 모양) |
| `text` | `text`, `fontSize`, `fontWeight`(100..900), `align`(`start` `center` `end`), `lineHeight`, `letterSpacing`, `fontFamily`, `italic`, `decoration`(`none` `underline` `strike`), `autoSize`(`fixed` `height` `auto`), `spans`, `numberFormat`: `prefix` `suffix` `decimals`(0..6) `grouping`(기본 `true`) |
| `image` | `uri`. SDK는 `content://`와 `android.resource://`만 열고, 그 밖의 이미지는 호스트가 `SceneImageProvider`로 넘긴다 |
| `host` | `key`. 호스트가 Compose에서는 `slots`로, XML에서는 `setHostViews`로 내용을 넣는다 |

## 움직임 효과 `effects`

| 키 | 값 |
| --- | --- |
| `press` | 목록. `kind`: `ripple` `scale` `tilt` `dim` `lift` `burst`, `amount`, `color` |
| `appear` | `kind`: `fade` `slideUp` `slideDown` `slideLeft` `slideRight` `pop` `zoom` `flip` `blur`, `durationMs`, `delayMs` |
| `loop` | `kind`: `pulse` `breathe` `float` `bounce` `shake` `shimmer` `glow` `spin` `heartbeat` `wiggle` `ping`, `periodMs`(100 이상), `amount`, `color` |

## 타임라인 `animations`

| 키 | 필수 | 내용 |
| --- | --- | --- |
| `id` | ○ | 타임라인 id. 편집기가 만드는 타임라인은 `main`이다 |
| `durationMs` | ○ | 재생 길이(ms) |
| `tracks` | | 목록. `nodeId`, `property`, `keyframes` |
| `markers` | | 목록. `name`, `timeMs` |
| `fpsHint` | | 미리보기와 내보내기의 권장 초당 프레임 수. 기본값 60 |

`property`는 `translationX` `translationY` `scaleX` `scaleY` `rotationDegrees` `opacity` `fill` `strokeWidth` `width` `height` `cornerRadius` `stroke` `blur` `shadowBlur` `shadowX` `shadowY` `shadowSpread` `shadowColor` `cornerTopLeft` `cornerTopRight` `cornerBottomRight` `cornerBottomLeft` `gradientStart` `gradientEnd` `gradientAngle` 중 하나다.

keyframe의 키는 다음과 같다. 시각은 겹치지 않고 커지는 순서여야 한다.

| 키 | 필수 | 내용 |
| --- | --- | --- |
| `timeMs` | ○ | `0..durationMs` |
| `value` | ○ | 숫자, 색 속성이면 색 문자열 |
| `easing` | | 이 키에서 다음 키까지의 easing. `type`: `linear`(기본) `cubicBezier`(`x1` `y1` `x2` `y2`, x는 0..1) `steps`(`count`) `spring`(`dampingRatio` `stiffness`) |
| `rotationDirection` | | 회전 track에서 이 키부터 다음 키까지 도는 방향. `shortest`(기본, 가장 짧은 방향) `clockwise` `counterClockwise` `direct`(값 차이 그대로, 0에서 720이면 두 바퀴). 회전이 아닌 속성에서는 무시한다 |

```json
{ "nodeId": "logo", "property": "rotationDegrees", "keyframes": [
  { "timeMs": 0, "value": 0, "rotationDirection": "direct", "easing": { "type": "cubicBezier", "x1": 0.42, "y1": 0, "x2": 0.58, "y2": 1 } },
  { "timeMs": 1200, "value": 720 }
] }
```

## 읽을 때의 규칙

- 크기 상한(기본 10 MiB), JSON 문법, 스키마 버전, 필드 형식과 유한한 숫자, 문서 검증 순서로 확인한다. 하나라도 실패하면 문서 전체를 쓰지 않는다.
  서버 화면은 이때 지금 보이는 문서를 그대로 두고, 실패 이유를 `RemoteSceneState.Ready.lastError`에 남긴다. 실패 정보에는 위치(`nodes[3].bounds`)와 코드가 들어 있다.
- 모르는 키와 모르는 `accessibility.role`은 무시한다. 하지만 모르는 `type`, 효과 `kind`, `rotationDirection` 값이 있으면 실패로 처리한다.
- 동작, 데이터 키, 호스트 뷰 키는 이름일 뿐이고 어떤 코드도 실행하지 않는다.
