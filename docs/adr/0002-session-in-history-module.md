# ADR-0002: 편집 세션을 kinetica-history에 둔다

상태: 채택

## 배경

공개 API 명세(docs 03)의 `SceneSession`은 `StateFlow` 문서·선택, `CommandDispatcher`, `HistoryController`를 가진다.
의존 지도(docs 21)에는 세션을 맡는 모듈이 없고, `kinetica-core`는 coroutines를 쓸 수 없다.

## 결정

- `KineticaSession`·`CommandDispatcher`·`HistoryController`를 `kinetica-history`에 둔다. 트랜잭션과 실행 취소가 세션의 핵심이라 함께 있는 편이 자연스럽다.
- `kinetica-history`는 `kinetica-core`와 `kotlinx-coroutines-core`에만 의존한다. `StateFlow`만 쓰고 dispatcher·scope는 만들지 않는다.
- 실행 취소는 문서 전체 snapshot이 아니라 바뀐 노드만 담은 diff로 쌓는다(docs 12). 단계 수와 바뀐 노드 수 합에 상한이 있다.
- 명세의 `SceneSession.playback`은 아직 두지 않는다. 재생 상태는 문서와 별개이고(docs 02), 재생 제어(KIN-303)가 생기면 player 쪽에서 묶는다.
- 세션 생성은 예외 대신 `SessionOpenResult`로 거부 이유를 돌려준다.

## 결과

- player만 쓰는 호스트도 세션 없이 `SceneDocument`를 그릴 수 있다.
- editor는 `kinetica-history`를 통해 세션을 받는다.

## 버린 대안

- 별도 `kinetica-session` 모듈: 모듈 수만 늘고 history와 항상 함께 쓰인다.
- core에 세션: core의 coroutines 금지 규칙(docs 02)을 어긴다.
