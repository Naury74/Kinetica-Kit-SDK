package com.naury.kinetica.serialization

import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.SceneDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * [RemoteSceneBundleLoader] 설정.
 *
 * @param verifier 있으면 서명이 맞는 묶음만 읽는다.
 * @param minRefreshIntervalMs 이 시간(ms) 안에 다시 부른 `refresh()`는 받지 않고 지금 상태를 돌려준다. `refresh(force = true)`는 늘 받는다.
 */
public data class RemoteBundleSettings @JvmOverloads constructor(
    val codec: SceneBundleCodec = SceneBundleCodec(),
    val verifier: SceneVerifier? = null,
    val minRefreshIntervalMs: Long = 2_000L,
)

/**
 * 서버가 여러 화면을 한 번에 내려줄 때 쓴다([SceneBundleCodec] 형식). [RemoteSceneLoader]와 같은 규칙을 화면마다 적용한다.
 *
 * - 처음 [refresh]에서 화면마다 캐시를 먼저 꺼내고, 캐시도 없으면 [fallback]의 같은 id 문서를 보여 준다.
 * - 받은 묶음에서 올바르고 [accept]를 통과한 화면만 바꾼다. 깨지거나 거절된 화면은 이전 문서를 유지하며 그 이유만 남긴다.
 *   묶음에 없는 화면은 그대로 둔다.
 * - 서명([RemoteBundleSettings.verifier])과 버전 표시는 묶음 전체에 하나다. 버전 표시는 캐시에 함께 저장한다.
 * - 동시에 여러 번 [refresh]하면(화면 여러 개가 함께 열릴 때) 진행 중인 받기 하나를 함께 기다리고,
 *   마지막 받기가 [RemoteBundleSettings.minRefreshIntervalMs] 안에 끝났으면 다시 받지 않는다.
 *
 * @param cache 화면마다 `"<prefix>-<화면 id>"` 이름으로 저장한다.
 */
public class RemoteSceneBundleLoader(
    private val fetcher: SceneFetcher,
    private val cache: RemoteSceneCache? = null,
    private val fallback: Map<String, SceneDocument> = emptyMap(),
    private val settings: RemoteBundleSettings = RemoteBundleSettings(),
    private val accept: (sceneId: String, document: SceneDocument) -> Boolean = { _, _ -> true },
) {
    private var lastFetchNanos: Long? = null
    private val mutable =
        MutableStateFlow(fallback.mapValues { (_, document) -> RemoteSceneState.Ready(document, SceneOrigin.Fallback) as RemoteSceneState })
    private val failure = MutableStateFlow<RemoteSceneError?>(null)
    private val lock = Mutex()
    private var inFlight: CompletableDeferred<Map<String, RemoteSceneState>>? = null
    private var etag: String? = null
    private var cacheRead = false

    /** 화면 id마다의 상태. 아직 보여 줄 것이 없는 화면은 없다. */
    public val states: StateFlow<Map<String, RemoteSceneState>> = mutable.asStateFlow()

    /**
     * 화면 하나의 상태. 그 화면이 아직 없으면 받는 중에는 [RemoteSceneState.Loading], 마지막 받기가 실패했으면 [RemoteSceneState.Failed]다.
     */
    public fun state(sceneId: String): Flow<RemoteSceneState> = combine(states, failure) { all, error ->
        all[sceneId] ?: error?.let(RemoteSceneState::Failed) ?: RemoteSceneState.Loading
    }.distinctUntilChanged()

    /** 서버에서 다시 받는다. 다른 [refresh]가 진행 중이면 그 결과를 함께 기다린다. */
    @JvmOverloads
    public suspend fun refresh(force: Boolean = false): Map<String, RemoteSceneState> {
        val (job, owner) = lock.withLock {
            val recent = lastFetchNanos?.let { System.nanoTime() - it < settings.minRefreshIntervalMs * NANOS_PER_MS } == true
            when {
                inFlight != null -> checkNotNull(inFlight) to false
                recent && !force -> return mutable.value
                else -> CompletableDeferred<Map<String, RemoteSceneState>>().also { inFlight = it } to true
            }
        }
        if (!owner) return job.await()
        // 기다리는 다른 호출도 같은 결과(실패·취소 포함)를 받도록 결과를 그대로 넘긴다.
        val result = runCatching { fetchAndApply() }
        lock.withLock {
            inFlight = null
            lastFetchNanos = System.nanoTime()
        }
        job.completeWith(result)
        return result.getOrThrow()
    }

    private suspend fun fetchAndApply(): Map<String, RemoteSceneState> {
        if (!cacheRead) {
            cacheRead = true
            readCache()
        }
        when (val fetched = fetchSafely()) {
            is SceneFetch.Body -> receive(fetched)

            SceneFetch.NotModified -> {
                failure.value = null
                update { it.clearError() }
            }

            SceneFetch.Failed -> failAll(RemoteSceneError.Network)
        }
        return mutable.value
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
        val repository = cache?.repository ?: return
        val meta = (repository.load(metaId()) as? LoadResult.Loaded)?.document
        // 확장 값은 JSON 글자로 저장되므로 화면 id 목록도 JSON 배열로 둔다.
        val ids = meta?.extensions?.get(IDS_KEY)?.let { raw ->
            runCatching { Json.parseToJsonElement(raw).jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull } }.getOrNull()
        }.orEmpty()
        val loaded = ids.mapNotNull { id ->
            (repository.load(sceneCacheId(id)) as? LoadResult.Loaded)?.document?.takeIf { accept(id, it) }?.let { id to it }
        }
        if (loaded.isNotEmpty()) etag = meta?.extensions?.get(ETAG_KEY)
        mutable.value = mutable.value + loaded.associate { (id, document) -> id to RemoteSceneState.Ready(document, SceneOrigin.Cache) }
    }

    private suspend fun receive(body: SceneFetch.Body) {
        if (settings.verifier?.verify(body.bytes, body.signature) == false) return failAll(RemoteSceneError.Unverified)
        val decoded = when (val result = settings.codec.decode(body.bytes)) {
            is BundleDecodeResult.Success -> result.scenes
            is BundleDecodeResult.Failure -> return failAll(RemoteSceneError.Invalid(result.error))
        }
        val accepted = mutableMapOf<String, SceneDocument>()
        update { states ->
            states + decoded.mapValues { (id, result) ->
                val document = (result as? DecodeResult.Success)?.document
                val error = when {
                    result is DecodeResult.Failure -> RemoteSceneError.Invalid(result.error)
                    document != null && !accept(id, document) -> RemoteSceneError.Rejected
                    else -> null
                }
                if (error == null && document != null) {
                    accepted[id] = document
                    RemoteSceneState.Ready(document, SceneOrigin.Network)
                } else {
                    states[id].withError(error ?: RemoteSceneError.Rejected)
                }
            }
        }
        failure.value = null
        etag = body.etag
        saveCache(accepted)
    }

    private suspend fun saveCache(accepted: Map<String, SceneDocument>) {
        val repository = cache?.repository ?: return
        accepted.forEach { (id, document) -> repository.save(sceneCacheId(id), document) }
        val ids = (mutable.value.filterValues { it is RemoteSceneState.Ready && it.origin != SceneOrigin.Fallback }.keys).sorted()
        val extensions = buildMap {
            put(IDS_KEY, JsonArray(ids.map(::JsonPrimitive)).toString())
            etag?.let { put(ETAG_KEY, it) }
        }
        repository.save(metaId(), SceneDocument(documentId = metaId(), artboard = Artboard(1.0, 1.0), extensions = extensions))
    }

    private fun failAll(error: RemoteSceneError) {
        failure.value = error
        update { states -> states.mapValues { (_, state) -> state.withError(error) } }
    }

    private fun update(change: (Map<String, RemoteSceneState>) -> Map<String, RemoteSceneState>) {
        mutable.value = change(mutable.value)
    }

    private fun RemoteSceneState?.withError(error: RemoteSceneError): RemoteSceneState = when (this) {
        is RemoteSceneState.Ready -> copy(lastError = error)
        else -> RemoteSceneState.Failed(error)
    }

    private fun Map<String, RemoteSceneState>.clearError() = mapValues { (_, state) ->
        (state as? RemoteSceneState.Ready)?.copy(lastError = null)
            ?: state
    }

    private fun metaId() = "${cache?.id ?: DEFAULT_PREFIX}-bundle"

    private fun sceneCacheId(sceneId: String) = "${cache?.id ?: DEFAULT_PREFIX}-$sceneId"

    private companion object {
        const val DEFAULT_PREFIX = "remote-bundle"
        const val NANOS_PER_MS = 1_000_000L
        const val IDS_KEY = "kinetica.remote.ids"
        const val ETAG_KEY = "kinetica.remote.etag"
    }
}
