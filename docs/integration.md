# 연동 예

## 편집 세션 수명

```kotlin
class SceneViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = FileSceneRepository(File(app.filesDir, "scenes"))
    val session: KineticaSession = (KineticaSession.create(document) as SessionOpenResult.Opened).session

    fun save() = viewModelScope.launch { repository.save("scene", session.document.value) }

    override fun onCleared() = session.close()
}
```

- 세션은 Activity가 아닌 ViewModel에 둡니다. 편집기는 저장소에 접근하지 않으며, 자동 저장은 호스트가 정합니다.
- `LoadResult.Failed`(손상·다른 형식 버전)는 파일을 지우지 않습니다. 새 장면으로 시작할지 호스트가 정합니다.

## 이미지

SDK는 URI를 열지 않습니다. 사용자가 고른 `content://` 이미지를 호스트가 미리 크기를 줄여 읽고 넘깁니다.

```kotlin
val cache = mutableStateMapOf<String, ImageBitmap>()
KineticaEditor(session, images = SceneImageProvider { uri -> cache[uri] })
```

## View 화면

```kotlin
val player = ComposeView(context).apply {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    setContent { KineticaPlayer(document) }
}
container.addView(player)
```

`sample-gallery`의 `ViewHostActivity`가 같은 예입니다.

## PNG 내보내기

```kotlin
val exporter = PngExporter(context)
val result = exporter.exportToUri(document, ExportRequest(widthPx = 1080, timeMs = 500.0), contentResolver, uri)
when (result) {
    is ExportResult.Success -> { /* result.widthPx × result.heightPx */ }
    is ExportResult.Failure -> { /* result.error: DimensionsExceeded, OutOfMemoryPrevented, ... */ }
}
```

`uri`는 시스템 문서 선택기(`CreateDocument("image/png")`)로 받습니다. 파일로 쓰면 `exportToFile`이 임시 파일 → rename으로 교체합니다.

## 장면 전환

```kotlin
val morph = (SceneMorph.create(cardScene, detailScene) as MorphResult.Ready).morph
val opening = rememberTransitionDriver()
val closing = rememberTransitionDriver(onEnd = { if (it == TransitionEnd.Completed) navigateBack() })
LaunchedEffect(Unit) { opening.animateTo(forward = true) }

val open by opening.state.collectAsState()
val close by closing.state.collectAsState()
Box(Modifier.fillMaxSize().dragToDismiss(closing, travelPx)) {
    SceneTransition(morph, open.progress * (1 - close.progress), Modifier.fillMaxSize())
}
```

- 두 장면에서 이을 노드에 같은 `metadata["morphKey"]`를 둡니다. 한 노드가 여러 노드와 짝지어지면 `AmbiguousMapping`입니다.
- navigation은 호스트가 소유합니다. 끝 알림(`Completed`)은 한 번만 오며, 그때 화면을 닫습니다.
- 호스트 화면 사이 범위를 잇는 것은 `SharedTransitionScope.SharedScenePlayer`(Compose `sharedBounds`)가 맡습니다.

## Java

```java
SceneDocument document = Kinetica.emptyDocument("scene", 1080.0, 1920.0);
KineticaSession session = Kinetica.openSession(document);
Kinetica.add(session, Kinetica.rectangle("box", 10.0, 20.0, 100.0, 50.0, 0xFF4466FF));
Kinetica.moveBy(session, "box", 5.0, 0.0);
String json = Kinetica.encode(Kinetica.document(session));
```

`kinetica-java`는 선택 모듈입니다. 잘못된 입력은 `IllegalArgumentException`으로 알리며 메시지에 사용자 콘텐츠를 담지 않습니다.
