# 34 — 장면 JSON 형식 (스키마 버전 1)

서버가 내려주는 화면(`RemoteSceneLoader`)과 저장 파일(`FileSceneRepository`)이 쓰는 형식이다. 서버에서 JSON을 직접 만들 때 이 문서를 따른다.
가장 쉬운 방법은 앱의 편집기 `KineticaEditor`로 화면을 만들고 `SceneCodec().encode(document)` 결과를 서버에 올리는 것이다.
코드로 문서를 만들면 `encode`가 기기에서 읽지 못할 문서를 `IllegalArgumentException`으로 거부하고, 메시지에 위반 코드와 노드 id를 담는다. `SceneCodec.validate`로 미리 검사해도 된다.

## 짧은 예

손으로 쓴 작은 예다. 기본값과 같은 항목은 빼도 된다. `SceneJsonDocTest`가 이 예를 그대로 읽어 검사한다.

```json
{
  "schemaVersion": 1,
  "documentId": "promo",
  "canvas": { "width": 1080, "height": 1920, "background": "#F3F4F8" },
  "transition": { "kind": "push" },
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

호스트 앱에서는 이렇게 쓴다.

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

- 길이, 위치, 글자 크기는 모두 **문서 단위**다. 너비 1080인 artboard를 너비 약 393dp인 휴대폰에 맞추면 문서 단위 2.75가 약 1dp다.
  그래서 `fontSize: 44`는 16sp쯤으로 보인다. 재생기는 `SceneFit`에 따라 artboard를 화면 크기에 맞춰 줄이거나 키운다.
- `transform`의 `x`, `y`는 부모의 왼쪽 위가 기준이다. 최상위 노드의 부모는 artboard다. 자동 레이아웃(`layout`)을 쓰는 Frame의 자식은 레이아웃이 위치를 정하므로 적지 않아도 된다.
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
| `transition` | | 다른 화면에서 이 화면으로 넘어올 때의 전환. [장면 전환](#장면-전환-transition)을 보라 |

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
| `effects` | | 움직임 효과. [아래 표](#움직임-효과-effects)를 보라 |
| `accessibility` | | `label`(스크린 리더가 읽을 이름) `role`(`button` `image` `heading`) `hidden`(장식용이라 읽지 않음) |
| `constraints` | | 자동 레이아웃 밖에서 부모 크기가 바뀔 때 따르는 제약. `h`와 `v`에 각각 `start` `end` `both` `center` `scale` 가운데 하나를 쓴다 |
| `fill` | | `true`면 자동 레이아웃 안에서 남는 공간을 채운다 |
| `sizeLimits` | | `minWidth` `maxWidth` `minHeight` `maxHeight` |

## 종류별

| type | 키 |
| --- | --- |
| `frame` | `children`, `cornerRadius`, `corners`([왼쪽 위, 오른쪽 위, 오른쪽 아래, 왼쪽 아래]), `clip`, `layout`: `direction`(`row`, `column`) `gap` `padding`([왼쪽, 위, 오른쪽, 아래]) `main`(`start` `center` `end` `between`) `cross`(`start` `center` `end` `stretch`) `hugWidth` `hugHeight` `wrap` `lineGap` |
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

## 장면 전환 `transition`

문서 최상위의 `transition`은 다른 화면에서 이 화면으로 넘어올 때 쓰는 전환이다. 없으면 바로 바뀐다.
`AnimatedScene`과 번들을 재생하는 `RemoteKineticaPlayer(bundle, sceneId)`가 이 값을 읽고, 화면이 바뀔 때 이전 화면에서 이 화면으로 전환한다.
시스템 설정의 '애니메이션 삭제'가 켜져 있으면 전환 없이 바로 바뀐다.

| 키 | 필수 | 내용 |
| --- | --- | --- |
| `kind` | ○ | 아래 표의 전환 종류 |
| `from` | | 새 화면이 들어오는 가장자리. `end`(기본) `start` `top` `bottom`. RTL 언어에서는 `start`와 `end`의 좌우가 바뀐다. 방향이 없는 전환에서는 무시한다 |
| `durationMs` | | 전환 시간(ms). `0..10000`, 기본값 400 |
| `easing` | | keyframe의 `easing`과 같은 형식. 기본값은 `cubicBezier`(0.42, 0, 0.58, 1)이다 |
| `originX`, `originY` | | `iris`가 퍼지기 시작하는 점과 `zoom` 계열의 중심. 화면 크기에 대한 비율(0..1)이고 기본값은 0.5다 |

| `kind` | 모습 | `from` |
| --- | --- | --- |
| `none` | 바로 바뀐다 | |
| `fade` | 두 화면이 겹치며 서서히 바뀐다 | |
| `morph` | 두 화면에서 `metadata.morphKey`나 id가 같은 노드의 위치, 크기, 색을 이어 준다. 짝이 없는 노드는 페이드된다. 짝을 지을 수 없으면 `fade`로 그린다 | |
| `dissolve` | 새 화면이 노이즈 무늬로 조금씩 드러난다 | |
| `slide` | 두 화면이 함께 밀려 바뀐다 | ○ |
| `push` | 새 화면이 밀고 들어오고, 이전 화면은 조금만 물러나며 어두워진다 | ○ |
| `cover` | 새 화면이 멈춰 있는 이전 화면을 덮는다 | ○ |
| `uncover` | 이전 화면이 밀려 나가며 아래에 있던 새 화면을 드러낸다 | ○ |
| `wipe` | 경계선이 가장자리에서 반대쪽으로 쓸고 지나가며 새 화면을 드러낸다 | ○ |
| `iris` | `originX`, `originY`에서 원이 퍼지며 새 화면을 드러낸다 | |
| `zoom` | 새 화면이 조금 작은 크기에서 커지며 나타나고, 이전 화면은 커지며 사라진다 | |
| `zoomBlur` | `zoom`에 블러를 더한다. Android 12 미만에서는 `zoom`과 같다 | |
| `rotate` | 이전 화면이 돌며 사라지고 새 화면이 반대로 돌며 나타난다 | ○ |
| `flip` | 카드를 뒤집듯 3D로 돈다. 절반을 지나면 새 화면이 보인다 | ○ |
| `cube` | 정육면체의 옆면으로 넘어가듯 3D로 돈다 | ○ |

뒤로 가기로 이전 화면에 돌아갈 때는 재생기에 `forward = false`를 넘긴다. 같은 전환을 거꾸로 재생하므로, `push`라면 위에 있던 화면이 들어왔던 쪽으로 빠지고
`cover`라면 덮었던 화면이 걷힌다.

```json
"transition": { "kind": "iris", "durationMs": 500, "originX": 0.85, "originY": 0.9 }
```

동작 문자열에 `transition` 파라미터를 붙이면 문서의 전환 대신 그 전환을 쓴다. 예를 들면 `go?scene=promo&transition=wipe&from=bottom&durationMs=300`이다.
호스트는 `SceneTransitionSpec.fromAction(SceneAction.parse(raw), fallback)`으로 전환을 얻어 재생기에 넘긴다. `fallback`에 다음 화면 문서의 `transition`을 주면 파라미터로 준 값만 덮어쓴다. 알 수 없는 값이나 범위를 벗어난 값은 무시한다.

```kotlin
var current by rememberSaveable { mutableStateOf("home") }
var transition by remember { mutableStateOf<SceneTransitionSpec?>(null) }
RemoteKineticaPlayer(bundle, current, transition = transition, onAction = { raw ->
    val action = SceneAction.parse(raw)
    action["scene"]?.let { next ->
        transition = SceneTransitionSpec.fromAction(action)   // 파라미터가 없으면 null이라 다음 화면 문서의 transition을 쓴다
        current = next
    }
})
```

번들 하나에 화면 여러 개를 넣고 `sceneId`만 바꾸면 각 화면 문서의 `transition`대로 전환한다. 문서가 화면 id보다 한 프레임 늦게 도착해도 전환은 새 문서로 그린다.
`sample-showcase`의 장면 전환 데모가 이 방식으로 홈, 상품, 결제 세 화면을 재생하고, 진행률 슬라이더로 `SceneTransition(from, to, spec, progress)`의 중간 프레임을 보여 준다.
호스트 앱 예는 `consumer-check`의 `SceneBundleHostScreen`에 있다.

## 타임라인 `animations`

| 키 | 필수 | 내용 |
| --- | --- | --- |
| `id` | ○ | 타임라인 id. 편집기가 만드는 타임라인은 `main`이다 |
| `durationMs` | ○ | 재생 길이(ms) |
| `tracks` | | 목록. `nodeId`, `property`, `keyframes` |
| `markers` | | 목록. `name`, `timeMs` |
| `fpsHint` | | 미리보기와 내보내기의 권장 초당 프레임 수. 기본값 60 |

`property`는 `translationX` `translationY` `scaleX` `scaleY` `rotationDegrees` `opacity` `fill` `strokeWidth` `width` `height` `cornerRadius` `stroke` `blur` `shadowBlur` `shadowX` `shadowY` `shadowSpread` `shadowColor` `cornerTopLeft` `cornerTopRight` `cornerBottomRight` `cornerBottomLeft` `gradientStart` `gradientEnd` `gradientAngle` 중 하나다.

keyframe에 쓰는 키는 다음과 같다. 시각은 겹치지 않고 커지는 순서여야 한다.

| 키 | 필수 | 내용 |
| --- | --- | --- |
| `timeMs` | ○ | `0..durationMs` |
| `value` | ○ | 숫자, 색 속성이면 색 문자열 |
| `easing` | | 이 키에서 다음 키까지의 easing. `type`: `linear`(기본) `cubicBezier`(`x1` `y1` `x2` `y2`, x는 0..1) `steps`(`count`, 선택 `jump`) `spring`(`dampingRatio` `stiffness`) |
| `easing.jump` | | 계단 easing에서 값이 오르는 시점. CSS `steps()`의 점프 방식과 같다. `end`(기본, 각 칸이 끝날 때) `start`(각 칸이 시작할 때) `none`(`count - 1`번 올라 첫 칸은 0, 마지막 칸은 1이다. `count`는 2 이상) `both`(`count + 1`번 올라 첫 칸도 0보다 크고 마지막 칸도 1보다 작다). 구간의 양 끝은 어느 방식이든 0과 1이다. `end`이면 쓰지 않고, 모르는 값은 `Malformed`로 거부한다 |
| `rotationDirection` | | 회전 track에서 이 키부터 다음 키까지 도는 방향. `shortest`(기본, 가장 짧은 방향) `clockwise` `counterClockwise` `direct`(값 차이 그대로, 0에서 720이면 두 바퀴). 회전이 아닌 속성에서는 무시한다 |

```json
{ "nodeId": "logo", "property": "rotationDegrees", "keyframes": [
  { "timeMs": 0, "value": 0, "rotationDirection": "direct", "easing": { "type": "cubicBezier", "x1": 0.42, "y1": 0, "x2": 0.58, "y2": 1 } },
  { "timeMs": 1200, "value": 720 }
] }
```

## 읽을 때의 규칙

- 크기 상한(기본 10 MiB), JSON 문법, 스키마 버전, 필드 형식과 유한한 숫자, 문서 검증 순으로 확인한다. 하나라도 실패하면 문서 전체를 버린다.
  서버 화면은 이때 지금 보이는 문서를 그대로 두고 실패 이유를 `RemoteSceneState.Ready.lastError`에 남긴다. 실패 정보에는 위치(`nodes[3].bounds`)와 코드가 들어 있다.
- 모르는 키와 모르는 `accessibility.role`은 무시한다. 모르는 `type`, 효과 `kind`, `rotationDirection` 값은 실패로 처리한다.
- `transition.kind`와 `transition.from`만은 예외다. 전환은 화면 내용과 상관이 없으므로, 이 버전이 모르는 값이 와도 실패로 처리하지 않고 각각 `fade`와 `end`로 읽는다.
  `transition`이 없는 예전 문서도 그대로 읽힌다.
- 동작, 데이터 키, 호스트 뷰 키는 이름일 뿐이며 어떤 코드도 실행하지 않는다.
