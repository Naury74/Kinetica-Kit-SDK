package com.naury.kinetica.serialization

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.RotationDirection
import com.naury.kinetica.core.scene.NodeId
import org.junit.Test

class RotationDirectionJsonTest {
    private val codec = SceneCodec()

    private fun scene(firstKey: String): String {
        val fixture = checkNotNull(javaClass.getResource("/fixtures/v1/scene-demo.json")).readText().trim()
        val track = "{\"nodeId\":\"shape-a\",\"property\":\"rotationDegrees\",\"keyframes\":[$firstKey,{\"timeMs\":100,\"value\":720}]}"
        return fixture.replace("\"animations\":[]", "\"animations\":[{\"id\":\"t\",\"durationMs\":100,\"tracks\":[$track]}]")
    }

    private fun firstRotation(text: String): RotationDirection {
        val document = (codec.decode(text) as DecodeResult.Success).document
        return document.timelines.single().track(NodeId("shape-a"), AnimatableProperty.RotationDegrees)!!.keyframes.first().rotation
    }

    @Test
    fun `회전 방향이 없으면 가장 짧은 방향이고 쓸 때도 키를 넣지 않는다`() {
        val text = scene("{\"timeMs\":0,\"value\":0}")

        assertThat(firstRotation(text)).isEqualTo(RotationDirection.Shortest)
        assertThat(codec.encode((codec.decode(text) as DecodeResult.Success).document)).doesNotContain("rotationDirection")
    }

    @Test
    fun `회전 방향 네 가지가 왕복한다`() {
        RotationDirection.entries.forEach { direction ->
            val text = scene("{\"timeMs\":0,\"value\":0,\"rotationDirection\":\"${direction.wireName}\"}")
            val encoded = codec.encode((codec.decode(text) as DecodeResult.Success).document)

            assertThat(firstRotation(text)).isEqualTo(direction)
            assertThat(firstRotation(encoded)).isEqualTo(direction)
        }
        assertThat(RotationDirection.entries.map { it.wireName }).containsExactly("shortest", "clockwise", "counterClockwise", "direct")
    }

    @Test
    fun `모르는 회전 방향은 위치와 함께 거부한다`() {
        val result = codec.decode(scene("{\"timeMs\":0,\"value\":0,\"rotationDirection\":\"sideways\"}"))

        assertThat(result).isInstanceOf(DecodeResult.Failure::class.java)
        assertThat((result as DecodeResult.Failure).error.toString()).contains("rotationDirection")
    }
}
