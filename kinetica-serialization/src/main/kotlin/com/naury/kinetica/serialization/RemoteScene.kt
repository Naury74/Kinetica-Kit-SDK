package com.naury.kinetica.serialization

import com.naury.kinetica.core.scene.SceneDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 서버에서 장면 JSON을 받아 오는 호스트 구현. SDK는 HTTP 클라이언트를 고르지 않으므로 호스트가 OkHttp·Ktor 등으로 만든다.
 *
 * @param etag 지난번 받은 응답의 버전 표시. 서버가 같은 내용이면 [SceneFetch.NotModified]를 돌려줄 수 있다.
 */
public fun interface SceneFetcher {
    public suspend fun fetch(etag: String?): SceneFetch
}

/** [SceneFetcher]의 결과. */
public sealed interface SceneFetch {
    /** 받은 JSON 본문(UTF-8)과 다음 요청에 쓸 버전 표시. */
    public class Body(public val bytes: ByteArray, public val etag: String? = null) : SceneFetch

    /** 지난번과 같은 내용. 지금 문서를 그대로 쓴다. */
    public data object NotModified : SceneFetch

    /** 네트워크·서버 오류. 이유는 호스트가 기록하고 SDK는 지금 문서를 유지한다. */
    public data object Failed : SceneFetch
}

/** 지금 보여 줄 문서가 어디서 왔는지. */
public enum class SceneOrigin {
    /** 호스트가 앱에 넣어 둔 기본 문서. */
    Fallback,

    /** 지난번 받아 저장해 둔 문서. */
    Cache,

    /** 이번에 서버에서 받은 문서. */
    Network,
}

/** [RemoteSceneLoader]의 상태. */
public sealed interface RemoteSceneState {
    /** 아직 보여 줄 문서가 없다(캐시·기본 문서 없이 첫 요청 중). */
    public data object Loading : RemoteSceneState

    /**
     * 보여 줄 문서가 있다. [lastError]는 마지막 새로 고침이 실패한 이유이며, 실패해도 문서는 이전 것을 유지한다.
     */
    public data class Ready(val document: SceneDocument, val origin: SceneOrigin, val lastError: RemoteSceneError? = null) :
        RemoteSceneState

    /** 보여 줄 문서가 하나도 없고 받기도 실패했다. */
    public data class Failed(val error: RemoteSceneError) : RemoteSceneState
}

/** 새로 고침이 실패한 이유. 사용자 글자는 담지 않는다. */
public sealed interface RemoteSceneError {
    public data object Network : RemoteSceneError

    /** JSON이 형식·크기·검증을 통과하지 못했다. */
    public data class Invalid(val error: SceneLoadError) : RemoteSceneError

    /** 문서는 올바르지만 호스트의 `accept` 검사(필요한 호스트 뷰·동작 등)를 통과하지 못했다. */
    public data object Rejected : RemoteSceneError
}

/**
 * 서버 주도 화면: 서버가 내려준 장면 문서를 받아 검증한 뒤 재생기에 넘길 상태로 만든다.
 *
 * - 처음 [refresh]에서 [cache]의 문서를 먼저 꺼내 보여 주고, 이어 서버에서 받는다. 캐시도 없으면 [fallback]을 보여 준다.
 * - 받은 JSON은 [codec]의 크기 상한·검증을 거치고, [accept]가 `false`면 쓰지 않는다. 어떤 실패에도 지금 문서를 유지한다.
 * - 받은 문서는 [cache]에 [cacheId]로 저장한다. 버전 표시(etag)는 메모리에만 두므로 앱을 다시 켜면 처음 한 번은 본문을 받는다.
 * - 문서 안의 동작·데이터 키·호스트 뷰 키는 이름일 뿐이며 코드를 실행하지 않는다. 처리 여부는 호스트의 `onAction`·`slots`가 정한다.
 *
 * [refresh]는 동시에 여러 번 불러도 차례로 실행한다.
 *
 * @param cacheId [cache]에 저장할 이름. [com.naury.kinetica.core.scene.NodeId] 규칙을 따른다.
 */
public class RemoteSceneLoader(
    private val fetcher: SceneFetcher,
    private val cache: SceneRepository? = null,
    private val cacheId: String = "remote-scene",
    private val fallback: SceneDocument? = null,
    private val codec: SceneCodec = SceneCodec(),
    private val accept: (SceneDocument) -> Boolean = { true },
) {
    private val mutable = MutableStateFlow<RemoteSceneState>(
        fallback?.let { RemoteSceneState.Ready(it, SceneOrigin.Fallback) } ?: RemoteSceneState.Loading,
    )
    private val lock = Mutex()
    private var etag: String? = null
    private var cacheRead = false

    public val state: StateFlow<RemoteSceneState> = mutable.asStateFlow()

    /** 서버에서 다시 받는다. 끝난 뒤의 상태를 돌려준다. [fetcher]가 던진 예외는 네트워크 실패로 다룬다. */
    public suspend fun refresh(): RemoteSceneState = lock.withLock {
        if (!cacheRead) {
            cacheRead = true
            readCache()
        }
        when (val fetched = fetchSafely()) {
            is SceneFetch.Body -> receive(fetched)
            SceneFetch.NotModified -> clearError()
            SceneFetch.Failed -> fail(RemoteSceneError.Network)
        }
        mutable.value
    }

    // 호스트 구현이 던질 예외 종류를 알 수 없다. 화면을 지키는 것이 우선이라 취소 외에는 모두 네트워크 실패로 다룬다.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun fetchSafely(): SceneFetch = try {
        fetcher.fetch(etag)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        SceneFetch.Failed
    }

    private suspend fun readCache() {
        val loaded = (cache?.load(cacheId) as? LoadResult.Loaded)?.document ?: return
        if (accept(loaded)) mutable.value = RemoteSceneState.Ready(loaded, SceneOrigin.Cache)
    }

    private suspend fun receive(body: SceneFetch.Body) {
        val document = when (val decoded = codec.decode(body.bytes)) {
            is DecodeResult.Success -> decoded.document
            is DecodeResult.Failure -> return fail(RemoteSceneError.Invalid(decoded.error))
        }
        if (!accept(document)) return fail(RemoteSceneError.Rejected)
        etag = body.etag
        mutable.value = RemoteSceneState.Ready(document, SceneOrigin.Network)
        cache?.save(cacheId, document)
    }

    private fun clearError() {
        val current = mutable.value
        if (current is RemoteSceneState.Ready) mutable.value = current.copy(lastError = null)
    }

    private fun fail(error: RemoteSceneError) {
        mutable.value = when (val current = mutable.value) {
            is RemoteSceneState.Ready -> current.copy(lastError = error)
            else -> RemoteSceneState.Failed(error)
        }
    }
}
