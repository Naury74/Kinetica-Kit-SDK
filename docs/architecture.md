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

- `kinetica-core`는 Kotlin 표준 라이브러리만 씁니다. history·motion·serialization은 `StateFlow`·IO dispatcher를 위해 `kotlinx-coroutines-core`를, serialization은 JSON을 위해 `kotlinx-serialization-json`을 더합니다.
- `kinetica-gestures`는 history·guides를 모릅니다. 문서 수정은 `GestureHost`, 스냅은 `DragSnap`으로만 받습니다.
- `kinetica-render-compose`는 편집 도구·실행 취소를 모릅니다. 렌더러 입력은 문서와 viewport뿐입니다.
- 모듈 사이 의존을 더할 때는 ADR을 남깁니다([adr](adr)).

## 데이터 흐름

```text
포인터 → CanvasGestureController → 트랜잭션 + SceneCommand → SceneReducer(전체 검증) → 새 SceneDocument → StateFlow → 그리기
재생: SceneDocument + 시각 → TimelineEvaluator → 평가한 문서(그리기 전용, 저장하지 않음)
저장: 확정된 SceneDocument → SceneCodec → FileSceneRepository(임시 파일 → fsync → rename)
```

- 문서는 불변입니다. 명령 하나가 거부되면 원래 문서가 그대로 남습니다.
- 트랜잭션 안의 명령은 즉시 화면에 보이지만 실행 취소 기록은 commit 때 한 단계만 남습니다. 기록은 바뀐 노드만 담는 diff입니다.
- 모션 편집 중에는 화면이 재생 위치에서 평가한 문서를 보여 주고, 편집은 `AutoKey`가 keyframe(track 있는 속성)이나 원래 문서(나머지)로 나눕니다.

## 좌표계

| 공간 | 단위 | 설명 |
| --- | --- | --- |
| node-local | 문서 단위 | 도형이 정의된 좌표. 사각형·타원·글자·이미지는 `(0, 0)`부터 크기만큼 |
| 부모·문서 | 문서 단위 | artboard 왼쪽 위가 원점, y는 아래로 |
| viewport | 픽셀 | 화면. 문서에 저장하지 않음 |

열 벡터 기준이며 행렬은 오른쪽부터 적용합니다.

```text
p_parent   = T(translation) × T(pivot) × R(rotation) × S(scale) × T(-pivot) × p_local
p_document = M_parent × … × p_local
p_viewport = T(pan) × S(zoom) × p_document          zoom ∈ [0.1, 16]
```

- 포인터 기준 확대는 `p' = q − (z'/z)(q − p)`로 포인터 아래 문서 점을 유지합니다.
- 회전 양수는 화면에서 시계 방향, 단위는 도입니다.
- 손가락 여유 거리는 dp로 정하고 `dp × density / zoom`으로 문서 단위로 바꿉니다.
- 크기 조절은 시작 시점 문서에서 절대값으로 계산하고, 반대쪽 손잡이가 문서 좌표에서 제자리에 있도록 이동을 다시 맞춥니다.

## 애니메이션

- keyframe 구간 `t0 ≤ t < t1`은 `t0` keyframe의 easing으로 보간합니다. keyframe 시각에서는 작성한 값, 처음 전·끝 뒤는 가까운 값을 유지합니다.
- 회전은 가장 짧은 방향, 색은 sRGB 채널별, 불투명도는 0..1, 선 두께는 0 이상으로 맞춥니다.
- `PlaybackController`는 프레임을 더하지 않고 "기준 시계 + 기준 위치"로 시각을 계산합니다. marker는 지날 때 한 번 알립니다.
- `SceneMorph`는 `morphKey` metadata나 같은 id로 짝짓고, 계층을 펼친 world 변환을 가운데 축으로 보간합니다.
