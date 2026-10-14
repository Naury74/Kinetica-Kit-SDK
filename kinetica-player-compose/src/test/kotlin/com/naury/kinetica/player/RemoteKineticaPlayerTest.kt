package com.naury.kinetica.player

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.serialization.RemoteSceneLoader
import com.naury.kinetica.serialization.RemoteSceneState
import com.naury.kinetica.serialization.SceneCodec
import com.naury.kinetica.serialization.SceneFetch
import com.naury.kinetica.serialization.SceneOrigin
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RemoteKineticaPlayerTest {
    @get:Rule
    val compose = createComposeRule()

    private fun scene(slotKey: String): SceneDocument {
        val slot = SceneNode(NodeId("slot"), NodeContent.HostSlot(Size(400.0, 200.0), slotKey))
        return SceneDocument(
            documentId = "remote",
            artboard = Artboard(400.0, 400.0),
            nodes = mapOf(slot.id to slot),
            rootOrder = listOf(slot.id),
        )
    }

    @Test
    fun `서버 문서를 받기 전에는 placeholder, 받은 뒤에는 그 문서의 호스트 뷰를 그린다`() {
        val codec = SceneCodec()
        val calls = mutableIntStateOf(0)
        val loader = RemoteSceneLoader({
            calls.intValue++
            SceneFetch.Body(codec.encode(scene("promo")).toByteArray())
        })

        compose.setContent {
            RemoteKineticaPlayer(
                loader,
                Modifier.size(200.dp),
                slots = mapOf("promo" to { Text("server promo") }),
                placeholder = { Text("loading") },
            )
        }
        // 본문 읽기는 다른 스레드에서 하므로 상태가 바뀔 때까지 기다린다.
        compose.waitUntil(5_000) { loader.state.value is RemoteSceneState.Ready }
        compose.waitForIdle()

        compose.onNodeWithText("server promo").assertIsDisplayed()
        assertThat(calls.intValue).isEqualTo(1)
        assertThat((loader.state.value as RemoteSceneState.Ready).origin).isEqualTo(SceneOrigin.Network)
    }

    @Test
    fun `받기에 실패하면 기본 문서를 그대로 보여 준다`() {
        val loader = RemoteSceneLoader({ SceneFetch.Failed }, fallback = scene("local"))

        compose.setContent {
            RemoteKineticaPlayer(loader, Modifier.size(200.dp), slots = mapOf("local" to { Text("bundled") }))
        }
        compose.waitForIdle()

        compose.onNodeWithText("bundled").assertIsDisplayed()
    }

    @Test
    fun `묶음의 두 화면을 함께 열어도 받기는 한 번이고 각자 자기 화면을 그린다`() {
        val calls = mutableIntStateOf(0)
        val bundleText = com.naury.kinetica.serialization.SceneBundleCodec().encode(mapOf("top" to scene("a"), "bottom" to scene("b")))
        val bundle = com.naury.kinetica.serialization.RemoteSceneBundleLoader({
            calls.intValue++
            SceneFetch.Body(bundleText.toByteArray())
        })

        compose.setContent {
            androidx.compose.foundation.layout.Column {
                RemoteKineticaPlayer(bundle, "top", Modifier.size(100.dp), slots = mapOf("a" to { Text("slot a") }))
                RemoteKineticaPlayer(bundle, "bottom", Modifier.size(100.dp), slots = mapOf("b" to { Text("slot b") }))
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("slot a").assertIsDisplayed()
        compose.onNodeWithText("slot b").assertIsDisplayed()
        assertThat(calls.intValue).isEqualTo(1)
    }
}
