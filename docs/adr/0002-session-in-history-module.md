# ADR-0002: 편집 세션을 kinetica-history에 둔다

상태: 채택

## 배경

공개 API 명세(docs 03)의 `SceneSession`은 문서와 선택 상태를 담은 `StateFlow`, `CommandDispatcher`, `HistoryController`를 가진다.
그런데 의존 관계도(docs 21)에는 세션을 둘 모듈이 없고, `kinetica-core`는 coroutines를 쓸 수 없다.

## 결정

- `KineticaSession`, `CommandDispatcher`, `HistoryController`를 `kinetica-history`에 둔다. 트랜잭션과 실행 취소가 세션의 핵심이므로 한 모듈에 두는 편이 자연스럽다.
- `kinetica-history`는 `kinetica-core`와 `kotlinx-coroutines-core`에만 의존한다. `StateFlow`만 쓰고 dispatcher나 scope는 만들지 않는다.
- 실행 취소 기록은 문서 전체 snapshot이 아니라 바뀐 노드만 담은 diff로 쌓는다(docs 12). 단계 수와 바뀐 노드 수 합계에는 상한을 둔다.
- 명세의 `SceneSession.playback`은 아직 두지 않는다. 재생 상태는 문서와 별개이므로(docs 02) 재생 제어(KIN-303)를 구현할 때 player 쪽에서 잇는다.
- 세션을 만들 수 없을 때는 예외를 던지지 않고 `SessionOpenResult`로 거부 이유를 돌려준다.

## 결과

- player만 쓰는 호스트도 세션 없이 `SceneDocument`를 그릴 수 있다.
- editor는 `kinetica-history`에서 세션을 받는다.

## 버린 대안

- 별도 `kinetica-session` 모듈: history와 항상 함께 쓰이므로 모듈 수만 늘어난다.
- core에 세션 두기: core에서 coroutines를 쓰지 않는다는 규칙(docs 02)을 어긴다.
