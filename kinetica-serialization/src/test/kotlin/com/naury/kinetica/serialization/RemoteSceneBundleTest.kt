package com.naury.kinetica.serialization

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.testing.TestScenes
import com.naury.kinetica.core.testing.TestScenes.id
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RemoteSceneBundleTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val bundles = SceneBundleCodec()
    private val home = TestScenes.sample()
    private val promo = home.copy(
        documentId = "promo",
        nodes = home.nodes + (id("a") to home.nodes.getValue(id("a")).copy(transform = Transform2D(translationX = 9.0))),
    )

    private fun body(text: String, etag: String? = null) = SceneFetch.Body(text.toByteArray(), etag)

    @Test
    fun `묶음은 화면마다 따로 읽고 깨진 화면만 실패한다`() {
        val homeJson = SceneCodec().encode(home)
        val text = "{\"bundleVersion\":1,\"scenes\":{\"home\":$homeJson,\"promo\":{\"schemaVersion\":\"x\"}}}"

        val result = bundles.decode(text.toByteArray()) as BundleDecodeResult.Success

        assertThat(result.scenes.getValue("home")).isEqualTo(DecodeResult.Success(home))
        assertThat(result.scenes.getValue("promo")).isInstanceOf(DecodeResult.Failure::class.java)
        assertThat(bundles.decode("{\"bundleVersion\":2,\"scenes\":{}}".toByteArray())).isInstanceOf(BundleDecodeResult.Failure::class.java)
    }

    @Test
    fun `받은 묶음의 화면을 보여 주고 캐시해 다시 켜면 먼저 꺼내며 버전 표시도 이어 간다`() = runTest {
        val cache = RemoteSceneCache(FileSceneRepository(folder.root, ioDispatcher = StandardTestDispatcher(testScheduler)), "shop")
        val online = RemoteSceneBundleLoader({ body(bundles.encode(mapOf("home" to home, "promo" to promo)), "v3") }, cache)

        online.refresh()
        assertThat(online.refresh()).isEqualTo(online.states.value)
        var sent: String? = null
        val restarted = RemoteSceneBundleLoader({ etag ->
            sent = etag
            SceneFetch.NotModified
        }, cache)
        restarted.refresh()

        assertThat(online.state("promo").first()).isEqualTo(RemoteSceneState.Ready(promo, SceneOrigin.Network))
        assertThat(restarted.state("home").first()).isEqualTo(RemoteSceneState.Ready(home, SceneOrigin.Cache))
        assertThat(sent).isEqualTo("v3")
    }

    @Test
    fun `깨지거나 거절된 화면은 이전 문서를 유지하고 다른 화면은 바뀐다`() = runTest {
        val responses = ArrayDeque(
            listOf(
                body(bundles.encode(mapOf("home" to home, "promo" to promo))),
                body(bundles.encode(mapOf("home" to promo, "promo" to home))),
            ),
        )
        val loader = RemoteSceneBundleLoader(
            { responses.removeFirst() },
            settings = RemoteBundleSettings(minRefreshIntervalMs = 0),
            accept = { id, document -> !(id == "promo" && document == home) },
        )

        loader.refresh()
        loader.refresh()

        assertThat(loader.state("home").first()).isEqualTo(RemoteSceneState.Ready(promo, SceneOrigin.Network))
        assertThat(loader.state("promo").first()).isEqualTo(RemoteSceneState.Ready(promo, SceneOrigin.Network, RemoteSceneError.Rejected))
    }

    @Test
    fun `동시에 새로 고치면 받기는 한 번이고 첫 실패는 없는 화면을 Failed로 알린다`() = runTest {
        var calls = 0
        val loader = RemoteSceneBundleLoader({
            calls++
            kotlinx.coroutines.delay(100)
            SceneFetch.Failed
        })

        val first = async { loader.refresh() }
        val second = async { loader.refresh() }
        first.await()
        second.await()

        assertThat(calls).isEqualTo(1)
        assertThat(loader.state("home").first()).isEqualTo(RemoteSceneState.Failed(RemoteSceneError.Network))
    }
}
