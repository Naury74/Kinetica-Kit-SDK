# ADR-0003: 기본 파일 저장소를 kinetica-serialization에 둔다

상태: 채택

## 배경

docs 13은 호스트가 `SceneRepository`를 제공하되, 앱 전용 폴더에 저장하는 선택 기본 구현을 두라고 한다.
저장은 원자적 교체(임시 파일 → fsync → rename)와 coroutine IO를 써야 한다.

## 결정

- `SceneRepository` 계약과 `FileSceneRepository`를 `kinetica-serialization`에 둔다. 저장 형식(`SceneCodec`)과 항상 함께 쓰이고 Android API가 필요 없다(`java.io`·`java.nio`).
- 입출력은 생성자로 받은 `CoroutineDispatcher`(기본 `Dispatchers.IO`)에서 한다. 이를 위해 `kotlinx-coroutines-core`에 의존한다.
- id는 `NodeId`와 같은 문자만 허용해 경로 이동을 막는다. 읽기 전에 파일 크기를 codec 상한과 비교한다.
- 손상된 파일은 지우지 않고 `LoadResult.Failed`로 알린다. 복구 여부는 호스트가 정한다.

## 결과

- 호스트는 폴더 하나만 넘기면 저장·열기를 쓸 수 있고, 다른 저장소(Room, 클라우드)는 같은 계약으로 바꿔 끼운다.
- 이미지 자산 해석(`MediaResolver`)은 Android 디코딩이 필요해 이 모듈에 두지 않는다.
