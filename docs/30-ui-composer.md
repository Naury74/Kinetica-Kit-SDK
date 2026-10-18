# 30 — UI 조립 (장면, 호스트 뷰, 서버 화면)

UI 키트와 함께 제공하는 고급 기능의 설계다. 장면 문서(scene graph) 위에 호스트 앱의 실제 Compose 뷰를 놓는다. 노드 속성은 호스트 데이터에 연결하고, 사용자가 노드를 누르면 호스트에 동작을 알린다.
장면은 [32 편집기](32-studio.md)의 `KineticaEditor`로 만들고, 재생기 `KineticaPlayer`나 서버 화면용 `RemoteKineticaPlayer`로 보여 준다.
이 방향을 정한 배경은 [ADR-0006](adr/0006-host-ui-composer.md)에, 연동 코드는 [연동 예](integration.md)에 있다.

## 노드

| 종류 | 내용 | 비고 |
| --- | --- | --- |
| `Frame` | 크기, 모서리, 자르기, 자식 목록 | 배경은 `style.fill`, 테두리는 `stroke`, 그림자는 `style.shadows`로 최대 8개 |
| `HostSlot` | 크기, `key` | 호스트가 `slots(key)`로 Composable을 넘긴다. 문서에는 키, 크기, 배치만 저장한다 |
| `Text` | 기본 속성에 `fontWeight`, `lineHeight`, `letterSpacing`을 더한다. `fontWeight`는 100~900이다 | 단위는 문서 단위 |
| 그 밖의 노드 | Group, Rectangle, Ellipse, Polygon, Path, Image | |

## 바인딩과 동작

```text
SceneNode.bindings: Map<BoundProperty, String>   // 속성 → 데이터 키
SceneNode.action: String?                        // 누르면 onAction(action) 호출
```

- `BoundProperty`는 `Text`, `Visible`, `ImageUri`, `Fill`, `Opacity`, `Variant`다. `Variant`에는 컴포넌트 베리언트 이름을 연결한다.
- 호스트 데이터는 `SceneData`로 넘긴다. 키마다 문자열, 숫자, 불리언, 색 가운데 하나인 `DataValue`를 담는다. `StateFlow`로 넘기면 값이 바뀔 때 바로 다시 그린다.
- 값이 없거나 타입이 맞지 않으면 문서에 저장된 값을 쓴다. 식이나 코드는 실행하지 않는다.
- 동작은 `이름` 또는 `이름?키=값&키=값` 형태의 문자열 하나이며 URL에 안전한 문자만 쓴다. 호스트는 `open_order?id=42` 같은 문자열을 `SceneAction.parse(action)`으로 이름과 값으로 나눈다.
- 숫자에 바인딩한 텍스트는 `NodeContent.Text.numberFormat`으로 접두사, 접미사, 천 단위 구분, 소수 자릿수를 정해 `1,284,000원`처럼 보여 준다. 편집기에서는 텍스트 속성의 **숫자 형식**에서 정한다.
- `KineticaPlayer`와 `RemoteKineticaPlayer`는 숫자 값이 바뀌면 600ms 동안 숫자가 올라가거나 내려가며 바뀌게 한다. 길이는 `numberAnimationMs`로 정하고, 0이면 바로 바꾼다.
  시스템의 '애니메이션 삭제' 설정이 켜져 있어도 바로 바꾼다. 재생기 밖에서는 `animateSceneNumbers(data)`로 같은 결과를 얻는다.

## 렌더링

- 노드 하나가 Compose 요소 하나가 된다. 위치는 `offset(translation)`으로, 회전과 배율은 `graphicsLayer(transformOrigin = pivot)`로 적용해 문서 변환과 똑같이 맞춘다.
- 그룹이나 Frame 같은 컨테이너의 자식은 그 안에 중첩되므로 부모 변환을 그대로 물려받는다. 불투명도는 레이어로 합성하므로 반투명 그룹 안에서 자식끼리 겹친 부분이 더 진해지지 않는다.
- 배율이 1일 때 문서 단위 1은 1px이다. 편집기는 viewport 배율을, 재생기는 화면 폭에 맞춘 배율을 바깥 레이어에 적용한다.
- 편집 모드에서는 맨 위의 편집 제스처 레이어가 포인터를 가져간다. 사용 모드에서는 노드의 동작과 호스트 뷰가 포인터를 받는다.
- 호스트 뷰 안의 dp와 sp는 기기 밀도가 아니라 `HOST_VIEW_UNITS_PER_DP`로 계산한다. 문서 2.75 단위가 1dp다. 그래서 같은 문서는 기기 밀도나 편집 배율과 상관없이 상자 크기에 비례해 같은 모습으로 보인다.
- `visible = false`이거나 불투명도가 0인 노드 안의 호스트 뷰는, 그런 노드가 조상에 있는 경우까지 포함해 기본값 `hiddenSlots = HiddenSlotPolicy.Keep`에서 컴포지션에 남는다. 측정은 계속하지만 배치하지 않으므로 그려지지 않고, 터치와 포커스를 받지 않으며, TalkBack에도 나오지 않는다.
  숨길 때 안쪽 입력란에 포커스가 있으면 포커스를 해제한다. 다시 보이면 스크롤 위치, 입력 중인 글자, `remember` 상태가 그대로 남는다. 데이터 바인딩으로 `visible`이 자주 바뀌어도 마찬가지다. 불투명도가 0에서 올라가는 페이드인 중에는 0보다 커진 순간부터 누를 수 있다.
- 숨긴 동안의 측정 비용까지 아끼려면 `HiddenSlotPolicy.Dispose`를 넘긴다. 이때는 숨기는 순간 호스트 뷰가 컴포지션에서 빠지므로, 다시 보일 때 상태가 처음부터 시작된다. 호스트 뷰가 아닌 노드는 정책과 상관없이 숨기면 만들지 않는다.
- TalkBack은 텍스트 노드의 텍스트를 읽는다. 이미지, 아이콘, 도형에는 `SceneNode.accessibility`로 `label`과 `role`을 붙인다. `role`은 `button`, `image`, `heading` 가운데 하나다. 장식용이면 `hidden = true`로 읽지 않게 한다.
  동작이 있는 노드는 버튼으로 알린다. JSON에서는 `"accessibility": {"label": "…", "role": "image"}`처럼 쓰며, 모르는 역할은 무시한다. 편집기에서는 속성 패널의 **접근성**에서 정한다.
- 텍스트 내용이 데이터에 바인딩되어 있으면 텍스트 편집 창에 그 사실을 표시하고, 바인딩을 풀면서 저장할 수 있게 한다. 이 작업은 실행 취소 한 단계로 기록된다.
- RTL 화면에서도 장면 좌표는 디자인한 그대로 두고 뒤집지 않는다.

## 오토 레이아웃과 화면 크기 대응

```text
Frame.layout: AutoLayout?      // direction(Horizontal, Vertical), gap, padding, mainAlign(Start, Center, End, SpaceBetween),
                               // crossAlign(Start, Center, End, Stretch), hugWidth, hugHeight, wrap, lineGap.
                               // JSON에서 direction 값은 row와 column, 정렬 키는 main과 cross
SceneNode.layoutFill: Boolean  // 오토 레이아웃 안에서 남는 공간을 똑같이 나눠 채운다
SceneNode.constraints          // 오토 레이아웃 밖에서 부모 크기가 바뀔 때의 규칙. h와 v에 각각 start, end, both, center, scale
```

- 배치는 `SceneReducer`가 명령마다 반영한다. 직접 다시 배치하려면 `Layouts.apply(document)`를 부른다. 문서를 열 때도 반영하므로 저장된 문서는 늘 배치가 끝난 상태이며, 배치로 정해진 값도 검증을 거친다.
- 안쪽 Frame부터 계산하고, 늘이기나 채우기로 크기가 바뀌면 최대 4번까지 다시 계산한다. 어떤 축을 내용에 맞추는 자식 Frame은 그 축으로 늘이지 않는다. 두 규칙이 번갈아 가며 크기를 바꾸지 않게 하려는 것이다.
- 데이터 때문에 노드의 표시 여부가 바뀌면 그 부분만 다시 배치한다. 애니메이션으로 정한 위치는 덮어쓰지 않는다.
- 재생기의 `SceneFit.Responsive`는 artboard를 화면 비율에 맞게 바꾸고 최상위 노드를 제약에 따라 옮긴다. 이때 문서 단위는 dp에 `HOST_VIEW_UNITS_PER_DP`를 곱한 값이다.
- `wrap`으로 줄 바꿈을 켜면 안쪽 길이를 넘는 자식은 다음 줄로 넘어가고, 줄 사이 간격은 `lineGap`이다. 각 줄 안에서 교차 축 정렬을 하고, 크기가 고정된 Frame이면 줄 전체도 정렬한다. 줄 바꿈을 켠 동안에는 채우기와 늘이기를 쓰지 않는다.
- `SceneNode.sizeLimits`로 최소 크기와 최대 크기를 정하면 오토 레이아웃, 제약, 채우기, 늘이기, 직접 바꾼 크기가 모두 이 범위 안으로 제한된다.
- 텍스트 자동 크기 `Text.autoSize`는 고정, 높이 자동, 너비와 높이 자동 가운데 하나다. core의 `TextMeasurer`로 크기를 재며, 렌더러의 `SceneTextMeasurer`는 화면과 같은 글꼴로 잰다.
  편집기는 세션에 측정기가 없으면 넣어 주고, 재생기는 데이터로 텍스트가 바뀌면 그 기기의 글꼴로 다시 잰다. 측정기가 없으면 저장된 크기를 그대로 쓴다.
- 편집기에서 레이어 패널 등으로 고른 오토 레이아웃 안의 노드를 끌면, 놓은 위치에 맞게 순서가 바뀐다. 계산은 `Layouts.dropIndex`가 하고, 실행 취소 한 단계로 기록된다.

## 컴포넌트와 디자인 토큰

```text
SceneNode.component: String?       // 컴포넌트 원본 이름
SceneNode.instanceOf: InstanceOf?  // 원본 id와 덮어쓴 값(자식 id 기준)
SceneDocument.tokens: Map<String, ArgbColor>
NodeStyle.fillToken, strokeToken   // 토큰에 연결한 색
```

- 명령마다 다음 순서로 문서를 정리한다. 덮어쓴 값을 기록하고, 직접 고친 색은 토큰 연결을 끊고, 토큰 색을 반영하고, 인스턴스를 원본에 다시 맞춘 다음, 오토 레이아웃을 계산한다.
- 인스턴스 자식의 id는 인스턴스 id와 원본 자식 id로 만든 고정 값이다. 그래서 인스턴스를 다시 맞춰도 Compose 상태가 유지된다.
- 편집기에서 쓰는 법은 [32 편집기](32-studio.md#컴포넌트와-디자인-토큰)에 있다.

## 서버 주도 재생

- `RemoteSceneLoader(fetcher, cache, fallback, verifier = …, accept = …)`는 호스트가 받아 온 JSON을 `SceneCodec`으로 검증한다. 처음에는 캐시된 문서를 먼저 보여 준다.
  받기에 실패하거나, 형식이 잘못되었거나, `accept`가 거절하면 지금 문서를 그대로 두고 이유만 `RemoteSceneState.Ready.lastError`에 남긴다.
  ETag는 캐시한 문서의 `extensions`에 함께 저장하므로 앱을 다시 켜도 쓸 수 있다. `verifier = SceneVerifier.signature(publicKey)`를 주면 서명이 맞는 본문만 읽는다.
- `RemoteKineticaPlayer(loader, modifier, refreshKey = …, data = …, slots = …, onAction = …)`는 보여 줄 문서가 없을 때만 `placeholder`를 표시한다. 서버 문서로 배치가 바뀌어도 호스트 뷰의 상태, 데이터, 동작은 그대로 이어진다.
- 여러 화면을 번들 하나로 받을 수도 있다. 형식은 `{"bundleVersion":1,"scenes":{"home":{…},"promo":{…}}}`이고 `SceneBundleCodec`이 읽는다. 기본 상한은 20 MiB, 64화면이다.
  `RemoteSceneBundleLoader`는 화면마다 따로 검증하고 캐시하며, 깨졌거나 거절된 화면만 이전 문서를 유지한다. 여러 화면이 동시에 열려도 네트워크 요청은 한 번만 보낸다.
  2초 안에 다시 새로 고치면 새로 받지 않으며, `refresh(force = true)`는 이 간격과 상관없이 받는다. 재생은 `RemoteKineticaPlayer(bundle, "home")`로 한다.
- 서명 키를 바꿀 때는 `SceneVerifier.anyOf(listOf(oldKey, newKey))`로 옛 키와 새 키의 서명을 모두 받는다. 새 키를 넣은 앱을 먼저 배포하고, 서버 서명을 새 키로 바꾼 뒤, 다음 앱 버전에서 옛 키를 뺀다.
- JSON 형식은 [34 장면 JSON](34-scene-json.md)에, OkHttp로 받는 예는 [연동 예](integration.md#서버-화면)에 있다.
