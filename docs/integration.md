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

## 서버 화면

서버 JSON 형식은 [docs/34](34-scene-json.md)입니다. 받기는 호스트가 정합니다(OkHttp 예):

```kotlin
val fetcher = SceneFetcher { etag ->                       // etag: 지난번 응답의 ETag(처음·캐시 없음이면 null)
    withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://api.example.com/screens/promo")
            .apply { etag?.let { header("If-None-Match", it) } }.build()
        client.newCall(request).execute().use { response ->
            when {
                response.code == 304 -> SceneFetch.NotModified
                response.isSuccessful -> SceneFetch.Body(response.body!!.bytes(), etag = response.header("ETag"))
                else -> SceneFetch.Failed                   // 이유는 호스트가 기록. SDK는 지금 화면을 유지
            }
        }
    }
}
val loader = RemoteSceneLoader(
    fetcher,
    cache = RemoteSceneCache(FileSceneRepository(File(filesDir, "remote"))),   // 선택: 다시 켜면 먼저 보여 줄 마지막 문서
    fallback = bundledPromo,                                                    // 선택: 받기 전·실패할 때 보여 줄 문서
)
```

- `RemoteKineticaPlayer`는 처음 보일 때와 `refreshKey`가 바뀔 때 `refresh()`를 부릅니다. 당겨서 새로 고침은 `refreshKey`를 바꾸거나 직접 `loader.refresh()`.
- 던진 예외는 네트워크 실패로 다룹니다. 서명 확인·JSON 읽기는 화면 스레드가 아닌 곳에서 합니다.
- 로더는 화면보다 오래 살도록 ViewModel이나 앱 단위로 둡니다.

## View 화면

XML 화면은 `KineticaPlayerView`를 씁니다.

```xml
<com.naury.kinetica.player.KineticaPlayerView android:id="@+id/promo" android:layout_width="match_parent" android:layout_height="300dp" />
```

```kotlin
val player = findViewById<KineticaPlayerView>(R.id.promo)
player.setLoader(loader)                                   // 또는 player.setDocument(document)
player.setHostViews { key, context -> chartView(context) } // 호스트 뷰 노드(key)에 넣을 View
player.setOnActionListener { raw -> handle(SceneAction.parse(raw)) }
player.data = SceneData.of("sales.today" to DataValue.Number(1_284_000.0))
```

보드는 `KineticaBoardView`·`BoardStates.obtain`([docs/31](31-board.md)), 효과는 `ViewEffects.apply`([docs/33](33-effects.md))입니다.

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
