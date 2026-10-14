package com.naury.kinetica.serialization

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.testing.TestScenes
import com.naury.kinetica.core.testing.TestScenes.id
import java.io.IOException
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
}
