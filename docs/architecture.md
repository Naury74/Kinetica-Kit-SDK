# Architecture

## 모듈과 의존 방향

```text
sample-showcase ─┬─ kinetica-ui (글래스 UI·고급 효과) ── Compose만
                 ├─ kinetica-board-compose ── kinetica-render-compose, kinetica-history, kinetica-serialization
                 ├─ kinetica-player-compose ── kinetica-render-compose, kinetica-motion, kinetica-serialization(api)
                 ├─ kinetica-editor-compose ─┬─ kinetica-render-compose ── kinetica-core
                 │                            ├─ kinetica-gestures ──────── kinetica-core
                 │                            ├─ kinetica-history ───────── kinetica-core
                 │                            ├─ kinetica-guides ────────── kinetica-core
                 │                            ├─ kinetica-motion ────────── kinetica-core
                 │                            └─ kinetica-presets ───────── kinetica-core
                 └─ kinetica-presets
kinetica-export-android ── kinetica-render-compose, kinetica-motion
kinetica-java ── kinetica-core, kinetica-history, kinetica-serialization
```

- `kinetica-core`는 Kotlin 표준 라이브러리만 씁니다. history, motion, serialization은 `StateFlow`와 IO dispatcher 때문에 `kotlinx-coroutines-core`를 추가로 쓰고, serialization은 JSON 처리를 위해 `kotlinx-serialization-json`도 씁니다.
- `kinetica-gestures`는 history와 guides에 의존하지 않습니다. 문서 수정은 `GestureHost`로, 스냅은 `DragSnap`으로만 주고받습니다.
- `kinetica-render-compose`는 편집 도구나 실행 취소 기능에 의존하지 않습니다. 렌더러가 받는 입력은 문서와 viewport뿐입니다.
- 모듈 사이에 의존을 새로 추가할 때는 [ADR](adr)을 남깁니다.

## 데이터 흐름

```text
포인터 → CanvasGestureController → 트랜잭션 + SceneCommand → SceneReducer(전체 검증) → 새 SceneDocument → StateFlow → 그리기
재생: SceneDocument + 시각 → TimelineEvaluator → 평가한 문서(그리기 전용, 저장하지 않음)
저장: 확정된 SceneDocument → SceneCodec → FileSceneRepository(임시 파일 → fsync → rename)
```

- 문서는 불변 객체입니다. 명령이 거부되면 원래 문서가 그대로 남습니다.
- 트랜잭션 안의 명령은 바로 화면에 반영되지만, 실행 취소 기록은 commit할 때 한 단계로만 남습니다. 기록에는 바뀐 노드만 담은 diff가 들어갑니다.
- 모션을 편집하는 동안 화면에는 현재 재생 위치에서 평가한 문서가 보입니다. 편집 내용은 `AutoKey`가 나눠서, track이 있는 속성은 keyframe에, 나머지는 원래 문서에 반영합니다.

## 좌표계

| 공간 | 단위 | 설명 |
| --- | --- | --- |
| node-local | 문서 단위 | 도형이 정의된 좌표. 사각형, 타원, 텍스트, 이미지는 `(0, 0)`부터 크기만큼의 영역 |
| 부모·문서 | 문서 단위 | artboard 왼쪽 위가 원점, y는 아래로 |
| viewport | 픽셀 | 화면 좌표. 문서에는 저장하지 않음 |

열 벡터를 기준으로 하며, 행렬은 오른쪽부터 적용합니다.

```text
p_parent   = T(translation) × T(pivot) × R(rotation) × S(scale) × T(-pivot) × p_local
p_document = M_parent × … × p_local
p_viewport = T(pan) × S(zoom) × p_document          zoom ∈ [0.1, 16]
```

- 포인터를 기준으로 확대할 때는 `p' = q − (z'/z)(q − p)`로 계산해 포인터 아래의 문서 위치가 그대로 있게 합니다.
- 회전 값이 양수이면 화면에서 시계 방향이고, 단위는 도(degree)입니다.
- 터치 허용 거리는 dp로 정하고, `dp × density / zoom`으로 문서 단위로 바꿉니다.
- 크기 조절은 드래그를 시작한 시점의 문서를 기준으로 절대값으로 계산하고, 반대쪽 핸들이 문서 좌표에서 움직이지 않도록 위치를 다시 맞춥니다.

## 애니메이션

- keyframe 구간 `t0 ≤ t < t1`은 `t0` keyframe의 easing으로 보간합니다. keyframe 시각에서는 입력한 값을 그대로 쓰고, 첫 keyframe 전과 마지막 keyframe 뒤에는 가장 가까운 keyframe 값을 유지합니다.
- 회전은 구간 시작 keyframe의 `rotation`(기본은 가장 짧은 방향, 그 밖에 시계, 반시계, 값 그대로)에 따라, 색은 sRGB 채널별로 보간합니다. 불투명도는 0..1, 선 두께는 0 이상으로 제한합니다.
- `PlaybackController`는 프레임 시간을 누적하지 않고 "기준 시계 + 기준 위치"로 현재 시각을 계산합니다. marker는 지나갈 때 한 번 알립니다.
- `SceneMorph`는 `morphKey` metadata나 같은 id로 노드 짝을 찾고, 계층을 펼친 world 변환을 보간합니다. pivot을 도형 가운데에 두므로 회전은 도형 가운데를 축으로 보간됩니다.
