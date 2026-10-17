package com.naury.kinetica.serialization

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.animation.EasingSpec
import com.naury.kinetica.core.scene.SceneAction
import com.naury.kinetica.core.scene.SceneTransitionEdge
import com.naury.kinetica.core.scene.SceneTransitionKind
import com.naury.kinetica.core.scene.SceneTransitionSpec
import org.junit.Test

class TransitionJsonTest {
    private val codec = SceneCodec()
    private val fixture = checkNotNull(javaClass.getResource("/fixtures/v1/scene-demo.json")).readText().trim()

    private fun withTransition(json: String): String = fixture.removeSuffix("}") + ",\"transition\":$json}"

    private fun decode(text: String) = (codec.decode(text) as DecodeResult.Success).document

    @Test
    fun `transition이 없는 옛 문서는 그대로 읽고 쓸 때도 키를 넣지 않는다`() {
        val document = decode(fixture)

        assertThat(document.transition).isNull()
        assertThat(codec.encode(document)).doesNotContain("transition")
    }

    @Test
    fun `모든 종류와 방향이 왕복한다`() {
        SceneTransitionKind.entries.forEach { kind ->
            SceneTransitionEdge.entries.forEach { edge ->
                val spec =
                    SceneTransitionSpec(kind, edge, durationMs = 250, easing = EasingSpec.Spring(0.8, 300.0), originX = 0.2, originY = 1.0)
                val document = decode(fixture).copy(transition = spec)

                assertThat(decode(codec.encode(document)).transition).isEqualTo(spec)
            }
        }
    }

    @Test
    fun `기본값만 쓰면 kind만 남는다`() {
        val document = decode(fixture).copy(transition = SceneTransitionSpec(SceneTransitionKind.Wipe))

        assertThat(codec.encode(document)).contains("\"transition\":{\"kind\":\"wipe\"}")
    }

    @Test
    fun `모르는 종류와 방향은 페이드와 기본 방향으로 읽는다`() {
        val document = decode(withTransition("{\"kind\":\"portal\",\"from\":\"behind\"}"))

        assertThat(document.transition).isEqualTo(SceneTransitionSpec(SceneTransitionKind.Fade))
    }

    @Test
    fun `범위를 벗어난 시간과 중심, 잘못된 easing은 거부한다`() {
        listOf(
            "{\"kind\":\"iris\",\"durationMs\":-1}",
            "{\"kind\":\"iris\",\"durationMs\":20000}",
            "{\"kind\":\"iris\",\"originX\":1.5}",
            "{\"kind\":\"iris\",\"easing\":{\"type\":\"spring\",\"dampingRatio\":0,\"stiffness\":100}}",
            "{\"durationMs\":300}",
            "\"wipe\"",
        ).forEach { json ->
            assertThat(codec.decode(withTransition(json))).isInstanceOf(DecodeResult.Failure::class.java)
        }
    }

    @Test
    fun `액션 파라미터가 문서의 전환을 덮어쓴다`() {
        val base = SceneTransitionSpec(SceneTransitionKind.Push)
        val action = SceneAction.parse("go?scene=promo&transition=iris&from=top&durationMs=300&originX=0.25&originY=x")

        val spec = SceneTransitionSpec.fromAction(action, base)

        assertThat(spec).isEqualTo(SceneTransitionSpec(SceneTransitionKind.Iris, SceneTransitionEdge.Top, 300, originX = 0.25))
        assertThat(SceneTransitionSpec.fromAction(SceneAction.parse("go"), null)).isNull()
        assertThat(SceneTransitionSpec.fromAction(SceneAction.parse("go?transition=nope"), base)).isEqualTo(base)
        assertThat(SceneTransitionSpec.fromAction(SceneAction.parse("go?transition=cube"))?.kind).isEqualTo(SceneTransitionKind.Cube)
    }
}
