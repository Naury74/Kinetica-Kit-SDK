# ADR-0004: 편집기가 프리셋을 직접 쓴다

상태: 채택

## 배경

docs 02의 의존 관계도에서는 `sample-studio`만 `kinetica-presets`를 쓴다. 그런데 docs 10은 Motion 화면에 프리셋을 두라고 한다.
프리셋 메뉴를 편집기 밖의 샘플에서 붙이려면 타임라인 패널 안쪽까지 닿는 확장 지점이 필요하다.

## 결정

- `kinetica-editor-compose`가 `kinetica-presets`에 `implementation`으로 의존하고, 타임라인 패널 헤더에 프리셋 메뉴를 둔다.
- 프리셋은 명령 목록만 돌려주고, 편집기가 그 명령을 현재 재생 위치부터 트랜잭션 하나로 적용한다. 프리셋은 문서를 직접 바꾸지 않는다(docs 23).
- `kinetica-presets`는 motion 평가가 필요 없으므로 `kinetica-core`에만 의존한다. 순환 의존은 생기지 않는다.

## 결과

- 편집기를 쓰는 호스트는 따로 설정하지 않아도 프리셋을 쓴다. player만 쓰는 호스트에는 프리셋이 들어가지 않는다.
- 호스트가 자기 프리셋을 메뉴에 넣는 기능은 아직 없다. 필요해지면 `KineticaEditor`에 `PresetRegistry` 파라미터를 더한다.
