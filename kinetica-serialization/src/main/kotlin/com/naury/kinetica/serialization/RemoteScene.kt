package com.naury.kinetica.serialization

import com.naury.kinetica.core.scene.SceneDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 서버에서 장면 JSON을 받아 오는 호스트 구현. SDK는 HTTP 클라이언트를 정하지 않으므로 호스트가 OkHttp나 Ktor 같은 클라이언트로 구현한다.
 *
 * @param etag 지난번 응답의 ETag. 내용이 그대로면 서버는 [SceneFetch.NotModified]를 돌려줄 수 있다.
 */
public fun interface SceneFetcher {
    public suspend fun fetch(etag: String?): SceneFetch
}

/** [SceneFetcher]의 결과. */
public sealed interface SceneFetch {
    /**
     * 받은 UTF-8 JSON 본문과 다음 요청에 보낼 ETag.
     *
     * @param signature 서버가 본문에 붙인 서명. 응답 헤더 등에서 꺼낸 바이트이며 [SceneVerifier]가 확인한다.
     */
    public class Body(public val bytes: ByteArray, public val etag: String? = null, public val signature: ByteArray? = null) : SceneFetch

    /** 지난번과 내용이 같다. 현재 문서를 그대로 쓴다. */
    public data object NotModified : SceneFetch

    /** 네트워크 또는 서버 오류. 원인은 호스트가 기록하고, SDK는 현재 문서를 유지한다. */
    public data object Failed : SceneFetch
}

/**
 * 받은 본문이 신뢰할 수 있는 서버에서 왔는지 확인한다. `false`를 돌려주면 본문을 파싱하지 않고 버린다.
 * 앱 저장소의 캐시는 이미 확인을 거친 본문이므로 다시 확인하지 않는다.
 */
public fun interface SceneVerifier {
    public fun verify(bytes: ByteArray, signature: ByteArray?): Boolean

    public companion object {
        /**
         * [publicKey]로 [SceneFetch.Body.signature]를 확인한다. 서명이 없거나 형식이 잘못되었으면 `false`를 돌려준다.
         *
         * @param algorithm `java.security.Signature` 알고리즘 이름. 기본값은 `SHA256withECDSA`이고, RSA 키라면 `SHA256withRSA`를 쓴다.
         */
        @JvmStatic
        @JvmOverloads
        public fun signature(publicKey: java.security.PublicKey, algorithm: String = "SHA256withECDSA"): SceneVerifier =
            SceneVerifier { bytes, signature ->
                signature != null && runCatching {
                    java.security.Signature.getInstance(algorithm).run {
                        initVerify(publicKey)
                        update(bytes)
                        verify(signature)
                    }
                }.getOrDefault(false)
            }

        /**
         * 공개 키를 교체하는 기간에 쓴다. [publicKeys] 중 하나라도 서명과 맞으면 통과시킨다.
         * 새 키로 서명하기 전에 새 키를 추가한 앱을 먼저 배포하고, 사용자가 모두 업데이트한 뒤 이전 키를 뺀다. 빈 목록이면 모든 본문을 거절한다.
         */
        @JvmStatic
        @JvmOverloads
        public fun anyOf(publicKeys: List<java.security.PublicKey>, algorithm: String = "SHA256withECDSA"): SceneVerifier {
            val verifiers = publicKeys.map { signature(it, algorithm) }
            return SceneVerifier { bytes, signature -> verifiers.any { it.verify(bytes, signature) } }
        }
    }
}

/**
 * 받은 문서를 캐시할 저장소와 문서 id. [id]는 [com.naury.kinetica.core.scene.NodeId]와 같은 규칙을 따르며 화면마다 달라야 한다.
 */
public data class RemoteSceneCache @JvmOverloads constructor(val repository: SceneRepository, val id: String = "remote-scene")

/** 현재 보여 주는 문서의 출처. */
public enum class SceneOrigin {
    /** 호스트가 앱에 넣어 둔 기본 문서. */
    Fallback,

    /** 지난번에 받아 캐시해 둔 문서. */
    Cache,

    /** 이번에 서버에서 받은 문서. */
    Network,
}

/** [RemoteSceneLoader]의 상태. */
public sealed interface RemoteSceneState {
    /** 아직 보여 줄 문서가 없다. 캐시도 기본 문서도 없이 첫 요청을 기다리는 중이다. */
    public data object Loading : RemoteSceneState

    /**
     * 보여 줄 문서가 있다. [lastError]는 마지막 새로 고침이 실패한 이유이며, 실패해도 이전 문서를 유지한다.
     */
    public data class Ready(val document: SceneDocument, val origin: SceneOrigin, val lastError: RemoteSceneError? = null) :
        RemoteSceneState

    /** 보여 줄 문서가 없고 서버에서 받는 것도 실패했다. */
    public data class Failed(val error: RemoteSceneError) : RemoteSceneState
}

/** 새로 고침이 실패한 이유. 사용자 입력 텍스트는 담지 않는다. */
public sealed interface RemoteSceneError {
    public data object Network : RemoteSceneError

    /** JSON이 형식, 크기, 검증 중 하나를 통과하지 못했다. */
    public data class Invalid(val error: SceneLoadError) : RemoteSceneError

    /** 문서 자체는 올바르지만, 필요한 HostSlot이나 액션이 있는지 보는 호스트의 `accept` 검사를 통과하지 못했다. */
    public data object Rejected : RemoteSceneError

    /** [SceneVerifier]가 서명을 확인하지 못했다. */
    public data object Unverified : RemoteSceneError
}

/**
 * 서버 주도 UI 로더. 서버가 내려준 장면 문서를 받아 검증한 뒤 재생기에 넘길 상태로 만든다.
 *
 * - 처음 [refresh]할 때 [cache]의 문서를 먼저 보여 주고 이어서 서버에 요청한다. 캐시가 없으면 [fallback]을 보여 준다.
 * - 받은 본문은 [verifier]가 있으면 서명부터 확인하고, [codec]의 크기 제한과 검증을 거친다. 마지막으로 [accept]가 `false`를 돌려주면 쓰지 않는다.
 *   어느 단계에서 실패해도 현재 문서는 유지한다.
 * - 받은 문서는 ETag와 함께 [cache]에 저장한다. 그래서 앱을 다시 실행해도 내용이 그대로면 서버가 본문을 다시 보낼 필요가 없다.
 * - 문서 안의 액션, 데이터 키, HostSlot 키는 이름일 뿐이며 SDK가 코드를 실행하지 않는다. 어떻게 처리할지는 호스트의 `onAction`과 `slots`가 정한다.
 *
 * [refresh]는 동시에 여러 번 호출해도 순서대로 실행한다. 어느 스레드에서 호출해도 되며, 서명 확인과 JSON 파싱은 [Dispatchers.Default]에서 한다.
 * [SceneFetcher]와 캐시 저장소가 쓰는 스레드는 각 구현이 정한다.
 */
public class RemoteSceneLoader(
    private val fetcher: SceneFetcher,
    private val cache: RemoteSceneCache? = null,
    private val fallback: SceneDocument? = null,
    private val codec: SceneCodec = SceneCodec(),
    private val verifier: SceneVerifier? = null,
    private val accept: (SceneDocument) -> Boolean = { true },
) {
    private val mutable = MutableStateFlow<RemoteSceneState>(
        fallback?.let { RemoteSceneState.Ready(it, SceneOrigin.Fallback) } ?: RemoteSceneState.Loading,
    )
    private val lock = Mutex()
    private var etag: String? = null
    private var cacheRead = false

    public val state: StateFlow<RemoteSceneState> = mutable.asStateFlow()

    /** 서버에서 문서를 다시 받고, 끝난 뒤의 상태를 돌려준다. [fetcher]가 던진 예외는 네트워크 실패로 처리한다. */
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

    // 호스트 구현이 어떤 예외를 던질지 알 수 없다. 화면을 유지하는 게 우선이라 취소를 뺀 모든 예외를 네트워크 실패로 본다.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun fetchSafely(): SceneFetch = try {
        fetcher.fetch(etag)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        SceneFetch.Failed
    }

    private suspend fun readCache() {
        val stored = (cache?.repository?.load(cache.id) as? LoadResult.Loaded)?.document ?: return
        val loaded = stored.copy(extensions = stored.extensions - ETAG_KEY)
        if (!accept(loaded)) return
        etag = stored.extensions[ETAG_KEY]
        mutable.value = RemoteSceneState.Ready(loaded, SceneOrigin.Cache)
    }

    private suspend fun receive(body: SceneFetch.Body) {
        // 재생기는 메인 스레드에서 refresh를 호출하는데, 본문이 크면 서명 확인과 파싱이 오래 걸리므로 다른 스레드로 넘긴다.
        val (verified, decodedResult) = withContext(Dispatchers.Default) {
            val ok = verifier?.verify(body.bytes, body.signature) != false
            ok to if (ok) codec.decode(body.bytes) else null
        }
        if (!verified) return fail(RemoteSceneError.Unverified)
        val document = when (val decoded = requireNotNull(decodedResult)) {
            is DecodeResult.Success -> decoded.document
            is DecodeResult.Failure -> return fail(RemoteSceneError.Invalid(decoded.error))
        }
        if (!accept(document)) return fail(RemoteSceneError.Rejected)
        etag = body.etag
        mutable.value = RemoteSceneState.Ready(document, SceneOrigin.Network)
        // ETag는 캐시하는 문서의 extensions에만 넣어 함께 저장하고, 화면에 넘기는 문서에는 넣지 않는다.
        cache?.repository?.save(cache.id, body.etag?.let { document.copy(extensions = document.extensions + (ETAG_KEY to it)) } ?: document)
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

    private companion object {
        const val ETAG_KEY = "kinetica.remote.etag"
    }
}
