package com.naury.kinetica.serialization

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.testing.TestScenes
import com.naury.kinetica.core.testing.TestScenes.id
import java.io.IOException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RemoteSceneLoaderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val codec = SceneCodec()
    private val first = TestScenes.sample()
    private val second = first.copy(
        nodes = first.nodes + (id("a") to first.nodes.getValue(id("a")).copy(transform = Transform2D(translationX = 9.0))),
    )

    private fun body(document: SceneDocument, etag: String? = null) = SceneFetch.Body(codec.encode(document).toByteArray(), etag)

    @Test
    fun `받은 문서를 보여 주고 캐시에 저장해 다음 실행에서 먼저 꺼낸다`() = runTest {
        val cache = FileSceneRepository(folder.root, ioDispatcher = StandardTestDispatcher(testScheduler))
        val online = RemoteSceneLoader({ body(first) }, RemoteSceneCache(cache))

        assertThat(online.state.value).isEqualTo(RemoteSceneState.Loading)
        assertThat(online.refresh()).isEqualTo(RemoteSceneState.Ready(first, SceneOrigin.Network))

        val offline = RemoteSceneLoader({ SceneFetch.Failed }, RemoteSceneCache(cache))
        assertThat(offline.refresh()).isEqualTo(RemoteSceneState.Ready(first, SceneOrigin.Cache, RemoteSceneError.Network))
    }

    @Test
    fun `깨진 JSON이나 호스트가 거절한 문서는 지금 문서를 유지하고 이유만 남긴다`() = runTest {
        val responses = ArrayDeque(
            listOf<SceneFetch>(SceneFetch.Body("{\"schemaVersion\":".toByteArray()), body(second)),
        )
        val loader = RemoteSceneLoader({ responses.removeFirst() }, fallback = first, accept = { it == first })

        val broken = loader.refresh() as RemoteSceneState.Ready
        val rejected = loader.refresh()

        assertThat(broken.document).isEqualTo(first)
        assertThat(broken.origin).isEqualTo(SceneOrigin.Fallback)
        assertThat(broken.lastError).isInstanceOf(RemoteSceneError.Invalid::class.java)
        assertThat(rejected).isEqualTo(RemoteSceneState.Ready(first, SceneOrigin.Fallback, RemoteSceneError.Rejected))
    }

    @Test
    fun `etag를 다음 요청에 넘기고 같은 내용이면 오류를 지운다`() = runTest {
        val seen = mutableListOf<String?>()
        var calls = 0
        val loader = RemoteSceneLoader({ etag ->
            seen += etag
            calls++
            when (calls) {
                1 -> body(first, "v1")
                2 -> throw IOException("offline")
                else -> SceneFetch.NotModified
            }
        })

        loader.refresh()
        assertThat((loader.refresh() as RemoteSceneState.Ready).lastError).isEqualTo(RemoteSceneError.Network)
        val final = loader.refresh()

        assertThat(seen).containsExactly(null, "v1", "v1").inOrder()
        assertThat(final).isEqualTo(RemoteSceneState.Ready(first, SceneOrigin.Network))
    }

    @Test
    fun `캐시도 기본 문서도 없이 받기에 실패하면 Failed다`() = runTest {
        val loader = RemoteSceneLoader({ SceneFetch.Failed })

        assertThat(loader.refresh()).isEqualTo(RemoteSceneState.Failed(RemoteSceneError.Network))
    }

    @Test
    fun `버전 표시를 캐시에 남겨 다시 켠 뒤 첫 요청에도 보낸다`() = runTest {
        val cache = FileSceneRepository(folder.root, ioDispatcher = StandardTestDispatcher(testScheduler))
        RemoteSceneLoader({ body(first, "v7") }, RemoteSceneCache(cache)).refresh()
        var sent: String? = null

        val restarted = RemoteSceneLoader({ etag ->
            sent = etag
            SceneFetch.NotModified
        }, RemoteSceneCache(cache))

        assertThat(restarted.refresh()).isEqualTo(RemoteSceneState.Ready(first, SceneOrigin.Cache))
        assertThat(sent).isEqualTo("v7")
    }

    @Test
    fun `캐시 저장소가 실패하거나 예외를 던져도 받은 문서를 보여 준다`() = runTest {
        val strict = SceneCodec(limits = com.naury.kinetica.core.scene.SceneLimits(maxExtensionBytes = 16))
        val small = FileSceneRepository(folder.root, strict, ioDispatcher = StandardTestDispatcher(testScheduler))
        val broken = object : SceneRepository {
            override suspend fun save(id: String, document: SceneDocument): SaveResult = throw IOException("disk")

            override suspend fun load(id: String): LoadResult = throw IOException("disk")

            override suspend fun list(): List<String> = emptyList()

            override suspend fun delete(id: String): Boolean = false
        }
        // ETag를 extensions에 넣어 저장하므로, 저장소 codec 한도를 넘으면 저장만 실패한다.
        val tagged = RemoteSceneLoader({ body(first, "v".repeat(32)) }, RemoteSceneCache(small))
        val throwing = RemoteSceneLoader({ body(first) }, RemoteSceneCache(broken))

        assertThat(tagged.refresh()).isEqualTo(RemoteSceneState.Ready(first, SceneOrigin.Network))
        assertThat(throwing.refresh()).isEqualTo(RemoteSceneState.Ready(first, SceneOrigin.Network))
    }

    @Test
    fun `캐시를 읽다가 화면을 떠나 취소되면 다음 새로 고침에서 캐시를 다시 읽는다`() = runTest {
        val files = FileSceneRepository(folder.root, ioDispatcher = StandardTestDispatcher(testScheduler))
        RemoteSceneLoader({ body(first, "v1") }, RemoteSceneCache(files)).refresh()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val slow = object : SceneRepository by files {
            override suspend fun load(id: String): LoadResult {
                gate.await()
                return files.load(id)
            }
        }
        var sent: String? = null
        val loader = RemoteSceneLoader({ etag ->
            sent = etag
            SceneFetch.NotModified
        }, RemoteSceneCache(slow))

        val leaving = launch { loader.refresh() }
        testScheduler.runCurrent()
        leaving.cancel()
        gate.complete(Unit)

        assertThat(loader.refresh()).isEqualTo(RemoteSceneState.Ready(first, SceneOrigin.Cache))
        assertThat(sent).isEqualTo("v1")
    }

    @Test
    fun `서명이 맞는 본문만 쓴다`() = runTest {
        val keys = java.security.KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val bytes = codec.encode(second).toByteArray()
        val signed = java.security.Signature.getInstance("SHA256withECDSA").run {
            initSign(keys.private)
            update(bytes)
            sign()
        }
        val responses = ArrayDeque(
            listOf(
                SceneFetch.Body(bytes),
                SceneFetch.Body(bytes, signature = byteArrayOf(1, 2, 3)),
                SceneFetch.Body(bytes, signature = signed),
            ),
        )
        val loader = RemoteSceneLoader({ responses.removeFirst() }, fallback = first, verifier = SceneVerifier.signature(keys.public))

        assertThat(loader.refresh()).isEqualTo(RemoteSceneState.Ready(first, SceneOrigin.Fallback, RemoteSceneError.Unverified))
        assertThat(loader.refresh()).isEqualTo(RemoteSceneState.Ready(first, SceneOrigin.Fallback, RemoteSceneError.Unverified))
        assertThat(loader.refresh()).isEqualTo(RemoteSceneState.Ready(second, SceneOrigin.Network))
    }

    @Test
    fun `키를 바꾸는 동안에는 옛 키와 새 키 서명을 모두 받고 모르는 키는 거절한다`() {
        fun keys() = java.security.KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val old = keys()
        val new = keys()
        val stranger = keys()
        val bytes = codec.encode(first).toByteArray()
        fun sign(pair: java.security.KeyPair) = java.security.Signature.getInstance("SHA256withECDSA").run {
            initSign(pair.private)
            update(bytes)
            sign()
        }
        val verifier = SceneVerifier.anyOf(listOf(old.public, new.public))

        assertThat(verifier.verify(bytes, sign(old))).isTrue()
        assertThat(verifier.verify(bytes, sign(new))).isTrue()
        assertThat(verifier.verify(bytes, sign(stranger))).isFalse()
        assertThat(SceneVerifier.anyOf(emptyList()).verify(bytes, sign(old))).isFalse()
    }
}
