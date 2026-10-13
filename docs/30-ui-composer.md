# 30 — UI composer 설계 (ADR-0006)

## 노드

| 종류 | 내용 | 비고 |
| --- | --- | --- |
| `Frame` | 크기, 모서리, 자르기, 자식 목록 | 배경은 `style.fill`, 테두리는 `stroke`, 그림자는 `style.shadow` |
| `HostSlot` | 크기, `key` | 호스트가 `slots(key)`로 Composable을 준다. 문서에는 키·크기·배치만 저장 |
| `Text` | 기존 + `fontWeight`(100~900), `lineHeight`, `letterSpacing` | 문서 단위 |
| 기존 | Group·Rectangle·Ellipse·Path·Image | 그대로 |

## 바인딩과 동작

```text
SceneNode.bindings: Map<BoundProperty, String>   // 속성 → 데이터 키
SceneNode.action: String?                        // 누르면 onAction(action)
```

- `BoundProperty`: `Text`, `Visible`, `ImageUri`, `Fill`, `Opacity`.
- 호스트 데이터는 `SceneData`(키 → `DataValue`: 글자·숫자·참거짓·색)이며 `StateFlow`로 바뀌면 바로 다시 그린다.
- 값이 없거나 종류가 맞지 않으면 문서에 저장된 값을 쓴다. 식·코드 실행은 없다.

## 렌더링

- 노드 하나가 Compose 요소 하나. 위치는 `offset(translation)`, 회전·배율은 `graphicsLayer(transformOrigin = pivot)`로 문서 변환과 같다.
- 컨테이너(그룹·Frame)의 자식은 그 안에 중첩되어 부모 변환을 그대로 물려받는다. 불투명도는 레이어로 합성한다.
- 문서 단위 1 = 1 px(배율 1). 편집기는 viewport 배율을, 재생기는 화면 폭에 맞춘 배율을 바깥 레이어에 적용한다.
- 편집 모드: 편집 제스처 층이 맨 위에서 포인터를 가져간다. 사용 모드: 노드의 동작과 호스트 뷰가 포인터를 받는다.
