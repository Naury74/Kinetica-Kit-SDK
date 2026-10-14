# 34 — 장면 JSON 형식 (형식 버전 1)

서버가 내려주는 화면(`RemoteSceneLoader`)과 저장 파일(`FileSceneRepository`)의 형식이다. 서버에서 직접 만들 때 이 문서를 따른다.
가장 쉬운 방법은 앱의 편집기(`KineticaEditor`)로 만든 뒤 `SceneCodec().encode(document)`의 결과를 서버에 두는 것이다.
코드로 문서를 만들면 `encode`가 읽을 수 없는 문서를 거부하고 위반 코드를 알려 준다(`SceneCodec.validate`로 미리 볼 수 있다).

## 짧은 예

아래는 손으로 쓴 최소 형태다(기본값인 항목은 빼도 된다). 이 예는 테스트(`SceneJsonDocTest`)가 실제로 읽어 확인한다.

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

호스트 쪽:

```kotlin
RemoteKineticaPlayer(
    loader,
    data = SceneData.of("sales.today" to DataValue.Number(1_284_000.0)),   // "1,284,000원"으로 보이고 바뀌면 올라간다
    onAction = { raw ->
        val action = SceneAction.parse(raw)                                 // name = "open_orders", action["filter"] = "today"
        if (action.name == "open_orders") openOrders(action["filter"])
    },
)
```

## 단위와 좌표

- 길이·위치·글자 크기는 모두 **문서 단위**다. 폭 1080 artboard가 폭 약 393dp 휴대폰에 맞춰지므로 문서 단위 2.75 ≈ 1dp다
  (`fontSize: 44` ≈ 16sp). 재생기는 artboard를 화면에 맞춰 줄이거나 키운다(`SceneFit`).
- `transform`의 `x`·`y`는 부모(최상위는 artboard) 왼쪽 위 기준이다. 자동 배치(`layout`) Frame의 자식 위치는 배치가 정하므로 쓰지 않아도 된다.
- 색은 `#RRGGBB` 또는 `#AARRGGBB`.

## 문서

| 키 | 필수 | 내용 |
| --- | --- | --- |
| `schemaVersion` | ○ | `1` |
| `documentId` | ○ | 영문자·숫자·`._:-` |
| `canvas` | ○ | `width`·`height`(문서 단위), `background`(색, 선택) |
| `nodes` | ○ | 노드 목록(순서 무관) |
| `rootOrder` | ○ | 최상위 노드 id. 앞의 것이 아래에 그려진다 |
| `animations` | | timeline 목록 |
| `tokens` | | 색 토큰(이름 → 색). 노드 `style.fillToken`이 참조 |
| `extensions` | | 호스트가 보존할 값(이름 → JSON). SDK는 읽지 않는다 |

## 노드 공통

| 키 | 필수 | 내용 |
| --- | --- | --- |
| `id` | ○ | 문서 안에서 유일. 영문자·숫자·`._:-` 1..128자 |
| `type` | ○ | `frame` `group` `rect` `ellipse` `polygon` `path` `text` `image` `host` |
| `parentId` | | 부모 id. 부모의 `children`에도 있어야 한다 |
| `transform` | | `x` `y` `scaleX` `scaleY` `rotation`(도) `pivotX` `pivotY`. 기본은 이동 없음·배율 1 |
| `bounds` | 그룹 외 ○ | `width`·`height` |
| `style` | | `fill`(색, 글자는 글자색) `stroke` `strokeWidth` `opacity`(0..1) `shadows`(최대 8개: `color` `x` `y` `blur` `spread` `inner`) `blur` `gradient` `dash` |
| `visible` | | 기본 `true` |
| `bindings` | | 속성 → 데이터 키. 속성은 `text` `visible` `imageUri` `fill` `opacity` `variant` |
| `action` | | 누르면 호스트 `onAction`에 넘길 글자. `이름` 또는 `이름?키=값&키=값`(URL 안전 글자) |
| `effects` | | 움직임 효과(아래) |
| `accessibility` | | `label`(읽을 이름) `role`(`button` `image` `heading`) `hidden`(장식) |
| `constraints` | | 자동 배치 밖에서 부모 크기가 바뀔 때: `h`·`v` 각각 `start` `end` `both` `center` `scale` |
| `fill` | | 자동 배치 안에서 남는 공간을 채움(`true`) |
| `sizeLimits` | | `minWidth` `maxWidth` `minHeight` `maxHeight` |

## 종류별

| type | 키 |
| --- | --- |
| `frame` | `children`, `cornerRadius`, `corners`([왼위, 오위, 오아, 왼아]), `clip`, `layout`: `direction`(`row`·`column`) `gap` `padding`([왼, 위, 오, 아래]) `main`(`start` `center` `end` `between`) `cross`(`start` `center` `end` `stretch`) `hugWidth` `hugHeight` `wrap` `lineGap` |
| `rect` | `cornerRadius`, `corners` |
| `polygon` | `sides`(3..64), `innerRatio`(별 모양) |
| `text` | `text`, `fontSize`, `fontWeight`(100..900), `align`(`start` `center` `end`), `lineHeight`, `letterSpacing`, `fontFamily`, `italic`, `decoration`(`none` `underline` `strike`), `autoSize`(`fixed` `height` `auto`), `spans`, `numberFormat`: `prefix` `suffix` `decimals`(0..6) `grouping`(기본 `true`) |
| `image` | `uri`. `content://`·`android.resource://`만 열며, 그 밖의 이미지는 호스트가 `SceneImageProvider`로 준다 |
| `host` | `key`. 호스트가 `slots`(Compose)·`setHostViews`(XML)로 내용을 넣는다 |

## 움직임 효과 `effects`

| 키 | 값 |
| --- | --- |
| `press` | 목록. `kind`: `ripple` `scale` `tilt` `dim` `lift` `burst`, `amount`, `color` |
| `appear` | `kind`: `fade` `slideUp` `slideDown` `slideLeft` `slideRight` `pop` `zoom` `flip` `blur`, `durationMs`, `delayMs` |
| `loop` | `kind`: `pulse` `breathe` `float` `bounce` `shake` `shimmer` `glow` `spin` `heartbeat` `wiggle` `ping`, `periodMs`(100 이상), `amount`, `color` |

## 읽을 때의 규칙

- 크기 상한(기본 10 MiB) → JSON 문법 → 형식 버전 → 필드 형식·유한한 숫자 → 문서 검증 순서로 확인하고, 하나라도 실패하면 문서 전체를 쓰지 않는다
  (서버 화면은 지금 문서를 유지하고 이유를 `RemoteSceneState.Ready.lastError`에 남긴다). 실패에는 위치(`nodes[3].bounds`)와 코드가 있다.
- 모르는 키는 무시한다. 모르는 `accessibility.role`도 무시한다. 모르는 `type`·효과 `kind`는 실패다.
- 동작·데이터 키·호스트 뷰 키는 이름일 뿐이며 코드를 실행하지 않는다.
