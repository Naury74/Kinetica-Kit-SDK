# 30 — UI composer 설계 (ADR-0006)

## 노드

| 종류 | 내용 | 비고 |
| --- | --- | --- |
| `Frame` | 크기, 모서리, 자르기, 자식 목록 | 배경은 `style.fill`, 테두리는 `stroke`, 그림자는 `style.shadows`(최대 8개) |
| `HostSlot` | 크기, `key` | 호스트가 `slots(key)`로 Composable을 준다. 문서에는 키·크기·배치만 저장 |
| `Text` | 기존 + `fontWeight`(100~900), `lineHeight`, `letterSpacing` | 문서 단위 |
| 기존 | Group·Rectangle·Ellipse·Polygon·Path·Image | 그대로 |

## 바인딩과 동작

```text
SceneNode.bindings: Map<BoundProperty, String>   // 속성 → 데이터 키
SceneNode.action: String?                        // 누르면 onAction(action)
```

- `BoundProperty`: `Text`, `Visible`, `ImageUri`, `Fill`, `Opacity`, `Variant`(컴포넌트 변형 이름).
- 호스트 데이터는 `SceneData`(키 → `DataValue`: 글자·숫자·참거짓·색)이며 `StateFlow`로 바뀌면 바로 다시 그린다.
- 값이 없거나 종류가 맞지 않으면 문서에 저장된 값을 쓴다. 식·코드 실행은 없다.
- 재생기(`KineticaPlayer`·`RemoteKineticaPlayer`)는 숫자 값이 바뀌면 600ms 동안 올라가거나 내려가며 바꾼다(`numberAnimationMs`, 0이면 바로).
  시스템 "애니메이션 삭제"가 켜져 있으면 바로 바꾼다. 다른 화면에서는 `animateSceneNumbers(data)`로 같은 값을 얻는다.

## 렌더링

- 노드 하나가 Compose 요소 하나. 위치는 `offset(translation)`, 회전·배율은 `graphicsLayer(transformOrigin = pivot)`로 문서 변환과 같다.
- 컨테이너(그룹·Frame)의 자식은 그 안에 중첩되어 부모 변환을 그대로 물려받는다. 불투명도는 레이어로 합성한다.
- 문서 단위 1 = 1 px(배율 1). 편집기는 viewport 배율을, 재생기는 화면 폭에 맞춘 배율을 바깥 레이어에 적용한다.
- 편집 모드: 편집 제스처 층이 맨 위에서 포인터를 가져간다. 사용 모드: 노드의 동작과 호스트 뷰가 포인터를 받는다.
- 호스트 뷰 안의 dp·sp는 기기 밀도가 아니라 `HOST_VIEW_UNITS_PER_DP`(문서 2.75 단위 = 1dp)로 계산한다. 그래서 같은 문서가 기기 밀도·편집 배율과 상관없이 상자 대비 같은 모습이다.
- 화면 읽기(TalkBack): 글자 노드는 그 글자를 읽는다. 이미지·아이콘·모양에는 `SceneNode.accessibility`(`label`·`role`(`button`·`image`·`heading`))를 붙이고,
  장식이면 `hidden = true`로 읽지 않게 한다. 동작이 있는 노드는 버튼으로 알린다. JSON은 `"accessibility": {"label": "…", "role": "image"}`이며
  모르는 역할은 무시한다. 편집기 속성 패널 **접근성**에서 정한다.
- 글자 내용이 데이터에 묶여 있으면 글자 수정 창이 그 사실을 알리고, 묶음을 풀며 저장(실행 취소 한 단계)할 수 있다.

## 자동 배치와 화면 크기 대응 (2단계)

```text
Frame.layout: AutoLayout?      // direction(Horizontal·Vertical), gap, padding, mainAlign(Start·Center·End·SpaceBetween),
                               // crossAlign(Start·Center·End·Stretch), hugWidth·hugHeight. JSON 키는 row·column·main·cross
SceneNode.layoutFill: Boolean  // 자동 배치 안에서 남는 공간을 똑같이 나눠 채움
SceneNode.constraints          // 자동 배치 밖: 부모 크기가 바뀔 때 h·v 각각 start·end·both·center·scale
```

- 배치는 `SceneReducer`가 명령마다 반영한다(내부 단계. 직접 다시 배치하려면 `Layouts.apply(document)`). 저장된 문서는 늘 배치가 끝난 상태이고, 배치가 만든 값도 검사한다. 열 때도 반영한다.
- 안쪽 Frame부터 계산하며 늘이기·채움으로 크기가 바뀌면 최대 4번까지 다시 계산한다. 그 축을 내용에 맞추는 자식 Frame은 늘이지 않는다(두 규칙이 번갈아 바뀌지 않게).
- 데이터가 보임을 바꾸면 그 자리만 다시 배치한다. 애니메이션 위치는 덮지 않는다.
- 재생기 `SceneFit.Responsive`: artboard를 화면 모양(문서 단위 = `HOST_VIEW_UNITS_PER_DP` × dp)으로 바꾸고 최상위 노드를 제약대로 옮긴다.
- 줄 바꿈(`wrap`·`lineGap`): 안쪽 길이를 넘는 자식은 다음 줄로. 줄 안에서 교차 정렬, 고정 크기 Frame이면 줄 묶음도 정렬한다. 줄 바꿈 중에는 채움·늘이기를 쓰지 않는다.
- 크기 최소·최대(`SceneNode.sizeLimits`): 자동 배치·제약·채움·늘이기·직접 바꾼 크기 모두 이 범위로 자른다.
- 글자 자동 크기(`Text.autoSize`: 고정·높이·너비와 높이): core의 `TextMeasurer`로 재며 렌더러의 `SceneTextMeasurer`가 화면과 같은 글꼴로 잰다.
  편집기는 세션에 측정기가 없으면 넣고, 재생기는 데이터로 글자가 바뀌면 그 기기 글꼴로 다시 잰다. 측정기가 없으면 저장된 크기를 쓴다.
- 편집기에서 자동 배치 안 노드(레이어 패널 등으로 고른 것)를 끌면 놓인 자리 순서로 바뀐다(`Layouts.dropIndex`, 실행 취소 한 단계).

## 컴포넌트·토큰 (3단계)

```text
SceneNode.component: String?     // 컴포넌트 원본 이름
SceneNode.instanceOf: InstanceOf? // 원본 id + 덮어쓴 값(자식 id 기준)
SceneDocument.tokens: Map<String, ArgbColor>
NodeStyle.fillToken·strokeToken  // 토큰에 이은 색
```

- 명령마다 덮어쓴 값 기록 → 직접 고친 색의 토큰 연결 끊기 → 토큰 색 반영 → 인스턴스 다시 맞추기 → 자동 배치 순으로 정리한다.
- 인스턴스 자식 id는 인스턴스 id와 원본 자식 id로 만든 고정 값이라 다시 맞춰도 Compose 상태가 유지된다.
- 편집기 사용법은 [docs/32](32-studio.md#컴포넌트디자인-토큰).

## 서버 주도 재생 (4단계)

- `RemoteSceneLoader(fetcher, cache, fallback, verifier = …, accept = …)`: 호스트가 받은 JSON을 `SceneCodec`으로 검증한다. 처음에는 캐시를 먼저 보여 주고,
  받기 실패·형식 오류·`accept` 거절이면 지금 문서를 유지하며 이유만 `RemoteSceneState.Ready.lastError`에 남긴다. etag는 캐시한 문서의 확장 값으로 함께 저장해 다시 켜도 쓴다. `verifier = SceneVerifier.signature(publicKey)`를 주면 서명이 맞는 본문만 읽는다.
- `RemoteKineticaPlayer(loader, modifier, refreshKey = …, data = …, slots = …, onAction = …)`: 보여 줄 문서가 없을 때만 `placeholder`.
- 여러 화면 묶음: `{"bundleVersion":1,"scenes":{"home":{…},"promo":{…}}}`(`SceneBundleCodec`, 기본 20 MiB·64화면).
  `RemoteSceneBundleLoader`가 화면마다 따로 검증·캐시하고, 깨지거나 거절된 화면만 이전 문서를 유지한다. 화면 여러 개가 함께 열려도
  받기는 한 번이며 2초 안의 새로 고침은 다시 받지 않는다(`refresh(force = true)`는 받음). 재생은 `RemoteKineticaPlayer(bundle, "home")`.
- 키 교체: `SceneVerifier.anyOf(listOf(oldKey, newKey))`로 옛 키·새 키 서명을 모두 받는다. 새 키를 넣은 앱을 먼저 배포하고, 서버가 새 키로 서명을 바꾼 뒤, 다음 앱에서 옛 키를 뺀다.

