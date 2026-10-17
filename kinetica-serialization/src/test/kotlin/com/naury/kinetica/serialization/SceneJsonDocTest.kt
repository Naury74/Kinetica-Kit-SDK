package com.naury.kinetica.serialization

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.scene.Bindings
import com.naury.kinetica.core.scene.DataValue
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneData
import com.naury.kinetica.core.scene.SceneTransitionKind
import com.naury.kinetica.core.scene.SceneTransitionSpec
import java.io.File
import org.junit.Test

/** docs/34의 짧은 예가 실제로 읽히는지 확인한다. 문서와 코드가 어긋나면 실패한다. */
class SceneJsonDocTest {
    @Test
    fun `문서의 손으로 쓴 예가 읽히고 숫자 형식·동작이 쓰인 대로다`() {
        val markdown = File("../docs/34-scene-json.md").readText()
        val example = markdown.substringAfter("```json").substringBefore("```")

        val result = SceneCodec().decode(example)

        assertThat(result).isInstanceOf(DecodeResult.Success::class.java)
        val document = (result as DecodeResult.Success).document
        val shown = Bindings.apply(document, SceneData.of("sales.today" to DataValue.Number(1_284_000.0)))
        assertThat((shown.node(NodeId("sales"))!!.content as NodeContent.Text).text).isEqualTo("1,284,000원")
        assertThat(document.node(NodeId("buy"))!!.action).isEqualTo("open_orders?filter=today")
        assertThat(document.node(NodeId("buy"))!!.transform.translationY).isGreaterThan(0.0)
        assertThat(document.transition?.kind).isEqualTo(SceneTransitionKind.Push)
        val iris = markdown.substringAfter("```json\n\"transition\": ").substringBefore("\n```")
        val withIris = example.replace("\"transition\": { \"kind\": \"push\" }", "\"transition\": $iris")
        assertThat((SceneCodec().decode(withIris) as DecodeResult.Success).document.transition)
            .isEqualTo(SceneTransitionSpec(SceneTransitionKind.Iris, durationMs = 500, originX = 0.85, originY = 0.9))
    }
}
