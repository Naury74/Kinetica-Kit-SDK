# 아키텍처

## 모듈과 의존 방향

```text
sample-showcase ─┬─ kinetica-ui (UI 키트와 효과) ── Compose, coroutines, activity-compose, androidx.window만
                 ├─ kinetica-board-compose ── kinetica-render-compose, kinetica-history, kinetica-serialization
                 ├─ kinetica-player-compose ── kinetica-render-compose, kinetica-motion, kinetica-serialization(api)
                 ├─ kinetica-editor-compose ─┬─ kinetica-render-compose ── kinetica-core
                 │                            ├─ kinetica-gestures ──────── kinetica-core
                 │                            ├─ kinetica-history ───────── kinetica-core
                 │                            ├─ kinetica-guides ────────── kinetica-core
                 │                            ├─ kinetica-motion ────────── kinetica-core
                 │                            └─ kinetica-presets ───────── kinetica-core
                 ├─ kinetica-export-android
                 └─ kinetica-presets
kinetica-export-android ── kinetica-render-compose, kinetica-motion
kinetica-java ── kinetica-core, kinetica-history, kinetica-serialization
```

- `kinetica-core`는 Kotlin 표준 라이브러리만 씁니다. history와 motion은 `StateFlow` 때문에, serialization은 `StateFlow`와 파일 IO dispatcher 때문에 `kotlinx-coroutines-core`를 씁니다. serialization은 JSON을 읽고 쓰려고 `kotlinx-serialization-json`도 씁니다.
- `kinetica-gestures`는 history와 guides에 의존하지 않습니다. 문서 수정은 `GestureHost`로, 스냅은 `DragSnap`으로만 주고받습니다.
- `kinetica-render-compose`는 편집 도구나 실행 취소에 의존하지 않습니다. 렌더러는 문서와 viewport만 받습니다.
- `kinetica-player-compose`는 서버 화면을 읽으려고 serialization을 `api`로 가져옵니다. 재생기의 release 런타임에는 core, motion, render, serialization만 들어가야 하며, `verifyReleaseClasspath`가 `check`에서 이를 확인합니다.
- `kinetica-ui`는 다른 Kinetica 모듈에 의존하지 않습니다. UI 키트만 쓰는 앱에는 장면 엔진이 들어가지 않습니다.
- 모듈 사이에 의존을 새로 더할 때는 [ADR](adr)을 남깁니다.

## 데이터 흐름

```text
포인터 → CanvasGestureController → 트랜잭션 + SceneCommand → SceneReducer(전체 검증) → 새 SceneDocument → StateFlow → 그리기
재생: SceneDocument + 시각 → TimelineEvaluator → 평가한 문서(그리기 전용, 저장하지 않음)
저장: 확정된 SceneDocument → SceneCodec → FileSceneRepository(임시 파일 → fsync → rename)
```

- 문서는 불변 객체입니다. 명령이 거부되면 원래 문서가 그대로 남습니다.
- 트랜잭션 안의 명령은 바로 화면에 반영되고, 실행 취소 기록은 commit할 때 한 단계로 남습니다. 기록에는 바뀐 노드만 담은 diff가 들어갑니다.
- 모션을 편집하는 동안 화면에는 지금 재생 위치에서 평가한 문서가 보입니다. 편집한 값은 `AutoKey`가 나눠 track이 있는 속성은 keyframe에, 나머지는 원래 문서에 씁니다.

## 좌표계

| 공간 | 단위 | 설명 |
| --- | --- | --- |
| node-local | 문서 단위 | 도형을 정의한 좌표. 사각형, 타원, 텍스트, 이미지는 `(0, 0)`에서 크기만큼의 영역 |
| 부모, 문서 | 문서 단위 | artboard 왼쪽 위가 원점이고 y는 아래로 커짐 |
| viewport | 픽셀 | 화면 좌표. 문서에는 저장하지 않음 |

열 벡터 기준이며, 행렬은 오른쪽부터 적용합니다.

```text
p_parent   = T(translation) × T(pivot) × R(rotation) × S(scale) × T(-pivot) × p_local
p_document = M_parent × … × p_local
p_viewport = T(pan) × S(zoom) × p_document          zoom ∈ [0.1, 16]
```

- 포인터를 기준으로 확대할 때는 `p' = q − (z'/z)(q − p)`로 계산해 포인터 아래의 문서 위치를 고정합니다.
- 회전은 도 단위이고, 양수면 화면에서 시계 방향입니다.
- 터치 허용 거리는 dp로 정하고 `dp × density / zoom`으로 문서 단위로 바꿉니다.
- 크기 조절은 드래그를 시작한 때의 문서를 기준으로 절대값을 계산하고, 반대쪽 핸들이 문서 좌표에서 움직이지 않게 위치를 다시 맞춥니다.

## 애니메이션

- keyframe 구간 `t0 ≤ t < t1`은 `t0` keyframe의 easing으로 보간합니다. keyframe 시각에서는 입력한 값을 그대로 쓰고, 첫 keyframe 전과 마지막 keyframe 뒤에는 가장 가까운 keyframe 값을 유지합니다.
- 회전은 구간 시작 keyframe의 `rotation`(`RotationDirection`, JSON 키는 `rotationDirection`)에 따라 보간합니다. 기본은 가장 짧은 방향이고 시계 방향, 반시계 방향, 값 차이 그대로를 고를 수 있습니다.
- 색은 sRGB 채널마다 보간합니다. 불투명도는 0..1로, 선 두께는 0 이상으로 제한합니다.
- `PlaybackController`는 프레임 시간을 더해 가지 않고 기준 시계와 기준 위치로 지금 시각을 계산합니다. marker는 지나갈 때 한 번 알립니다.
- `SceneMorph`는 `morphKey` metadata나 같은 id로 노드 짝을 찾고, 계층을 펼친 world 변환을 보간합니다. pivot을 도형 가운데에 두므로 회전도 도형 가운데를 축으로 보간됩니다.
