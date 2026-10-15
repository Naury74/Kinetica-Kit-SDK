# 연동 예

## 편집 세션 수명 주기

```kotlin
class SceneViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = FileSceneRepository(File(app.filesDir, "scenes"))
    val session: KineticaSession = (KineticaSession.create(document) as SessionOpenResult.Opened).session

    fun save() = viewModelScope.launch { repository.save("scene", session.document.value) }

    override fun onCleared() = session.close()
}
```

- 세션은 Activity가 아니라 ViewModel에 둡니다. 편집기는 저장소에 직접 접근하지 않으므로 자동 저장 여부와 시점은 호스트가 정합니다.
- 파일이 손상됐거나 스키마 버전이 달라 `LoadResult.Failed`가 와도 SDK는 파일을 지우지 않습니다. 새 장면으로 시작할지는 호스트가 정합니다.

## 이미지

SDK는 URI를 직접 열지 않습니다. 사용자가 고른 `content://` 이미지는 호스트가 크기를 줄여 읽은 뒤 넘겨 줍니다.

```kotlin
val cache = mutableStateMapOf<String, ImageBitmap>()
KineticaEditor(session, images = SceneImageProvider { uri -> cache[uri] })
```

## 서버 화면

서버 JSON 형식은 [docs/34](34-scene-json.md)에 정리되어 있습니다. 네트워크 요청은 호스트가 직접 구현합니다. 아래는 OkHttp를 쓰는 예입니다.

```kotlin
val fetcher = SceneFetcher { etag ->                       // etag: 지난번 응답의 ETag. 처음 받거나 캐시가 없으면 null
    withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://api.example.com/screens/promo")
            .apply { etag?.let { header("If-None-Match", it) } }.build()
        client.newCall(request).execute().use { response ->
            when {
                response.code == 304 -> SceneFetch.NotModified
                response.isSuccessful -> SceneFetch.Body(response.body!!.bytes(), etag = response.header("ETag"))
                else -> SceneFetch.Failed                   // 실패 이유는 호스트가 기록하고, SDK는 지금 화면을 유지한다
            }
        }
    }
}
val loader = RemoteSceneLoader(
    fetcher,
    cache = RemoteSceneCache(FileSceneRepository(File(filesDir, "remote"))),   // 생략 가능: 앱을 다시 켰을 때 먼저 보여 줄 마지막 문서
    fallback = bundledPromo,                                                    // 생략 가능: 받기 전이나 실패했을 때 보여 줄 문서
)
```

- `RemoteKineticaPlayer`는 처음 화면에 보일 때와 `refreshKey`가 바뀔 때 `refresh()`를 호출합니다. 당겨서 새로 고침을 붙이려면 `refreshKey`를 바꾸거나 `loader.refresh()`를 직접 호출합니다.
- `SceneFetcher`가 던진 예외는 네트워크 실패로 처리합니다. 서명 확인과 JSON 파싱은 메인 스레드가 아닌 곳에서 합니다.
- 로더는 화면보다 오래 살아야 하므로 ViewModel이나 앱 범위에 둡니다.

## View 화면

XML 화면은 `KineticaPlayerView`를 씁니다.

```xml
<com.naury.kinetica.player.KineticaPlayerView android:id="@+id/promo" android:layout_width="match_parent" android:layout_height="300dp" />
```

```kotlin
val player = findViewById<KineticaPlayerView>(R.id.promo)
player.setLoader(loader)                                   // 문서를 직접 넘기려면 player.setDocument(document)
player.setHostViews { key, context -> chartView(context) } // 호스트 뷰 노드(key)에 넣을 View를 만든다
player.setOnActionListener { raw -> handle(SceneAction.parse(raw)) }
player.data = SceneData.of("sales.today" to DataValue.Number(1_284_000.0))
swipeRefresh.setOnRefreshListener { player.refresh() }      // setLoader 때 한 번 받아 오므로, 다시 받을 때만 호출한다
```

XML 속성으로는 `app:kinetica_fit="responsive"`, `app:kinetica_autoplay="false"`를 쓸 수 있습니다. Fragment에서는 `BoardStates.obtain(this, …)`처럼
Fragment를 그대로 넘깁니다. ViewModel과 lifecycle을 가진 객체라면 무엇이든 됩니다.

XML 화면에서 보드는 `KineticaBoardView`와 `BoardStates.obtain`([docs/31](31-board.md))으로, 효과는 `ViewEffects.apply`([docs/33](33-effects.md))로 붙입니다.

## PNG 내보내기

```kotlin
val exporter = PngExporter(context)
val result = exporter.exportToUri(document, ExportRequest(widthPx = 1080, timeMs = 500.0), contentResolver, uri)
when (result) {
    is ExportResult.Success -> { /* result.widthPx × result.heightPx */ }
    is ExportResult.Failure -> { /* result.error: DimensionsExceeded, OutOfMemoryPrevented, ... */ }
}
```

`uri`는 시스템 문서 선택기(`CreateDocument("image/png")`)로 받습니다. 파일로 저장할 때 `exportToFile`은 임시 파일에 먼저 쓴 뒤 rename으로 기존 파일을 교체합니다.

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

- 두 장면에서 서로 이어질 노드에는 같은 `metadata["morphKey"]`를 넣습니다. 한 노드가 여러 노드와 짝지어지면 `AmbiguousMapping` 오류가 납니다.
- 화면 이동(navigation)은 호스트가 관리합니다. 전환이 끝났다는 `Completed` 알림은 한 번만 오므로 그때 화면을 닫으면 됩니다.
- 호스트 화면 사이에서 영역을 이어 주려면 `SharedTransitionScope.SharedScenePlayer`를 씁니다. 내부적으로 Compose `sharedBounds`를 사용합니다.

## Java

```java
SceneDocument document = Kinetica.emptyDocument("scene", 1080.0, 1920.0);
KineticaSession session = Kinetica.openSession(document);
Kinetica.add(session, Kinetica.rectangle("box", 10.0, 20.0, 100.0, 50.0, 0xFF4466FF));
Kinetica.moveBy(session, "box", 5.0, 0.0);
String json = Kinetica.encode(Kinetica.document(session));
```

`kinetica-java`는 필요할 때만 추가하는 모듈입니다. 잘못된 입력이 들어오면 `IllegalArgumentException`을 던지며, 예외 메시지에는 사용자 콘텐츠를 담지 않습니다.
