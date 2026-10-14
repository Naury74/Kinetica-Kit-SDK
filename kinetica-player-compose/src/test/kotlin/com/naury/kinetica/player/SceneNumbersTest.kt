package com.naury.kinetica.player

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.BoundProperty
import com.naury.kinetica.core.scene.DataValue
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneData
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SceneNumbersTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `정수끼리는 중간값도 정수이고 소수는 많은 쪽 자릿수에 맞추며 숫자가 아닌 값은 바로 바뀐다`() {
        val from = SceneData.of("sales" to DataValue.Number(100.0), "rate" to DataValue.Number(1.5), "name" to DataValue.Text("A"))
        val to = SceneData.of("sales" to DataValue.Number(200.0), "rate" to DataValue.Number(2.25), "name" to DataValue.Text("B"))

        val half = blendNumbers(from, to, 0.333f)

        assertThat(half["sales"]).isEqualTo(DataValue.Number(133.0))
        assertThat(half["rate"]).isEqualTo(DataValue.Number(1.75))
        assertThat(half["name"]).isEqualTo(DataValue.Text("B"))
        assertThat(blendNumbers(from, to, 1f)).isEqualTo(to)
    }

    @Test
    fun `재생기는 묶인 숫자 글자를 올라가며 바꾸고 끝에는 새 값이다`() {
        val label = SceneNode(
            NodeId("sales"),
            NodeContent.Text(Size(400.0, 80.0), "-", fontSize = 40.0),
            bindings = mapOf(BoundProperty.Text to "sales"),
        )
        val document = SceneDocument("n", Artboard(400.0, 100.0), mapOf(label.id to label), listOf(label.id))
        val data = mutableStateOf(SceneData.of("sales" to DataValue.Number(0.0)))
        compose.setContent { KineticaPlayer(document, Modifier.size(400.dp, 100.dp), data = data.value, numberAnimationMs = 1_000) }
        compose.onNodeWithText("0").assertExists()
        compose.mainClock.autoAdvance = false

        data.value = SceneData.of("sales" to DataValue.Number(1_000.0))
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("0").assertDoesNotExist()
        compose.onNodeWithText("1000").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(1_000)

        compose.onNodeWithText("1000").assertExists()
    }
}
