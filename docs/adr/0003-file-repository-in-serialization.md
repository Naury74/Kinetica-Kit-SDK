# ADR-0003: 기본 파일 저장소를 kinetica-serialization에 둔다

상태: 채택

## 배경

docs 13에 따르면 `SceneRepository`는 호스트가 제공하되, 앱 전용 폴더에 저장하는 기본 구현도 골라 쓸 수 있게 두어야 한다.
저장할 때는 원자적 교체(임시 파일 → fsync → rename)를 하고 IO는 coroutine으로 처리해야 한다.

## 결정

- `SceneRepository` 계약과 `FileSceneRepository`를 `kinetica-serialization`에 둔다. 저장 형식(`SceneCodec`)과 항상 함께 쓰이고, `java.io`와 `java.nio`만으로 구현할 수 있어 Android API가 필요 없다.
- 입출력은 생성자로 받은 `CoroutineDispatcher`(기본값 `Dispatchers.IO`)에서 처리한다. 이 때문에 `kotlinx-coroutines-core`에 의존한다.
- id에는 `NodeId`와 같은 문자만 허용해 경로 조작(path traversal)을 막는다. 읽기 전에 파일 크기를 codec 상한과 비교한다.
- 손상된 파일은 지우지 않고 `LoadResult.Failed`로 알린다. 복구할지는 호스트가 정한다.

## 결과

- 호스트는 폴더 하나만 넘기면 저장과 열기를 쓸 수 있다. Room이나 클라우드 같은 다른 저장소는 같은 계약을 구현해 바꿔 끼우면 된다.
- 이미지 에셋을 불러오는 `MediaResolver`는 Android 디코딩이 필요하므로 이 모듈에 두지 않는다.
