# ADR-0004: 편집기가 프리셋을 직접 쓴다

상태: 채택

## 배경

docs 02의 의존 그림에서 `kinetica-presets`는 `sample-studio`만 쓴다. docs 10은 Motion 화면에 프리셋을 두라고 한다.
편집기 밖(샘플)에서 프리셋 메뉴를 붙이려면 timeline 패널 안쪽까지 들어가는 확장 지점이 필요하다.

## 결정

- `kinetica-editor-compose`가 `kinetica-presets`에 `implementation`으로 의존하고, timeline 패널 머리에 프리셋 메뉴를 둔다.
- 프리셋은 명령 목록만 돌려주고, 편집기가 재생 위치부터 트랜잭션 하나로 적용한다. 프리셋은 문서를 직접 바꾸지 않는다(docs 23).
- `kinetica-presets`는 `kinetica-core`에만 의존한다(motion 평가가 필요 없다). 순환은 없다.

## 결과

- 편집기를 쓰는 호스트는 별도 연결 없이 프리셋을 쓴다. player만 쓰는 호스트에는 들어가지 않는다.
- 호스트가 자기 프리셋을 메뉴에 넣는 기능은 아직 없다. 필요해지면 `KineticaEditor`에 `PresetRegistry` 인자를 연다.
