package com.naury.kinetica.serialization

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.EasingSpec
import com.naury.kinetica.core.animation.KeyValue
import com.naury.kinetica.core.animation.Keyframe
import com.naury.kinetica.core.animation.PropertyTrack
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.animation.TimelineMarker
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.PathCommand
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.scene.TextAlignment
import com.naury.kinetica.core.scene.ViolationCode
import com.naury.kinetica.core.testing.TestScenes
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class SceneCodecTest {
    private val codec = SceneCodec()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/fixtures/v1/$name")) { "fixture 없음: $name" }.readText().trim()

    private fun decoded(text: String): SceneDocument {
        val result = codec.decode(text)
        check(result is DecodeResult.Success) { "읽혀야 합니다: $result" }
        return result.document
    }

    private fun failure(text: String): SceneLoadError {
        val result = codec.decode(text)
        check(result is DecodeResult.Failure) { "실패해야 합니다" }
        return result.error
    }

    @Test
    fun `명세의 예시 fixture를 읽고 같은 문자열로 다시 쓴다`() {
        val text = fixture("scene-demo.json")

        val document = decoded(text)

        assertThat(document.artboard).isEqualTo(Artboard(1080.0, 1920.0))
        val node = document.node(NodeId("shape-a"))!!
        assertThat(node.transform).isEqualTo(Transform2D(translationX = 120.0, translationY = 220.0))
        assertThat(node.content).isEqualTo(NodeContent.Rectangle(Size(240.0, 120.0)))
        assertThat(node.style).isEqualTo(NodeStyle(fill = ArgbColor(0xFF4466FF.toInt())))
        assertThat(codec.encode(document)).isEqualTo(text)
    }

    @Test
    fun `모든 노드 종류와 애니메이션이 왕복한다`() {
        val document = richDocument()

        val encoded = codec.encode(document)

        assertThat(decoded(encoded)).isEqualTo(document)
        assertThat(codec.encode(decoded(encoded))).isEqualTo(encoded)
    }

    @Test
    fun `크기 상한을 넘으면 해석하지 않고 거부한다`() {
        val small = SceneCodec(maxJsonBytes = 100)
        val text = fixture("scene-demo.json")

        assertThat((small.decode(text) as DecodeResult.Failure).error).isInstanceOf(SceneLoadError.TooLarge::class.java)
        assertThat((small.decode(text.toByteArray()) as DecodeResult.Failure).error).isInstanceOf(SceneLoadError.TooLarge::class.java)
        val huge = "[" + "1,".repeat(6_000_000) + "1]"
        assertThat(failure(huge)).isInstanceOf(SceneLoadError.TooLarge::class.java)
    }

    @Test
    fun `깨진 JSON과 잘못된 최상위 값을 거부한다`() {
        val text = fixture("scene-demo.json")
        listOf(text.dropLast(10), "", "[]", "null", "{\"schemaVersion\":\"1\"}").forEach {
            assertThat(failure(it)).isInstanceOf(SceneLoadError.Malformed::class.java)
        }
    }

    @Test
    fun `지원하지 않는 형식 버전을 거부한다`() {
        val text = fixture("scene-demo.json")

        assertThat(failure(text.replace("\"schemaVersion\":1", "\"schemaVersion\":2"))).isEqualTo(SceneLoadError.UnsupportedSchema(2))
        assertThat(failure(text.replace("\"schemaVersion\":1", "\"schemaVersion\":0"))).isEqualTo(SceneLoadError.UnsupportedSchema(0))
    }

    @Test
    fun `migration으로 예전 형식을 올려 읽는다`() {
        val v0 = fixture("scene-demo.json").replace("\"schemaVersion\":1", "\"schemaVersion\":0").replace("\"canvas\"", "\"artboard\"")
        val renameArtboard = SceneMigration { json ->
            JsonObject(
                json.mapKeys { (key, _) -> if (key == "artboard") "canvas" else key } + ("schemaVersion" to JsonPrimitive(1)),
            )
        }
        val migrating = SceneCodec(migrations = SceneMigrations(mapOf(0 to renameArtboard)))

        val result = migrating.decode(v0)

        assertThat((result as DecodeResult.Success).document).isEqualTo(decoded(fixture("scene-demo.json")))
    }

    @Test
    fun `유한하지 않은 숫자와 문자열 숫자를 위치와 함께 거부한다`() {
        val text = fixture("scene-demo.json")

        assertThat(
            failure(text.replace("\"x\":120", "\"x\":1e309")),
        ).isEqualTo(SceneLoadError.Malformed("nodes[0].transform.x", "유한한 숫자여야 합니다."))
        assertThat(failure(text.replace("\"x\":120", "\"x\":NaN"))).isInstanceOf(SceneLoadError.Malformed::class.java)
        assertThat(
            failure(text.replace("\"x\":120", "\"x\":\"120\"")),
        ).isEqualTo(SceneLoadError.Malformed("nodes[0].transform.x", "숫자여야 합니다."))
    }

    @Test
    fun `중복 id·순환·잘못된 URI·알 수 없는 종류를 거부한다`() {
        val text = fixture("scene-demo.json")
        val node = "{\"id\":\"shape-a\",\"type\":\"rect\",\"bounds\":{\"width\":1,\"height\":1}}"
        val duplicate = text.replace("\"nodes\":[", "\"nodes\":[$node,")
        val cycle = """{"schemaVersion":1,"documentId":"d","canvas":{"width":10,"height":10},"nodes":[""" +
            """{"id":"p","type":"group","parentId":"q","children":["q"]},{"id":"q","type":"group","parentId":"p","children":["p"]}],""" +
            """"rootOrder":[]}"""
        val http = """{"schemaVersion":1,"documentId":"d","canvas":{"width":10,"height":10},"nodes":[""" +
            """{"id":"i","type":"image","bounds":{"width":1,"height":1},"uri":"https://example.com/x.png"}],"rootOrder":["i"]}"""

        assertThat(failure(duplicate)).isEqualTo(SceneLoadError.Malformed("nodes[1].id", "id가 겹칩니다."))
        assertThat((failure(cycle) as SceneLoadError.Invalid).violations.map { it.code }).contains(ViolationCode.Cycle)
        assertThat((failure(http) as SceneLoadError.Invalid).violations.map { it.code }).containsExactly(ViolationCode.InvalidUri)
        assertThat(failure(text.replace("\"type\":\"rect\"", "\"type\":\"video\""))).isInstanceOf(SceneLoadError.Malformed::class.java)
    }

    @Test
    fun `오류에 사용자 글자를 담지 않는다`() {
        val secret = "SECRET-CONTENT"
        val text = """{"schemaVersion":1,"documentId":"d","canvas":{"width":10,"height":10},"nodes":[""" +
            """{"id":"t","type":"text","bounds":{"width":1,"height":1},"text":"$secret\uD800","name":"$secret"}],"rootOrder":["t"]}"""

        assertThat(failure(text).toString()).doesNotContain(secret)
    }

    @Test
    fun `알 수 없는 필드는 무시하고 extensions는 보존한다`() {
        val text = fixture(
            "scene-demo.json",
        ).replace("\"animations\":[]", "\"animations\":[],\"future\":1,\"extensions\":{\"host.tag\":{\"a\":[1,2]}}")

        val document = decoded(text)

        assertThat(document.extensions).containsExactly("host.tag", "{\"a\":[1,2]}")
        assertThat(codec.encode(document)).contains("\"extensions\":{\"host.tag\":{\"a\":[1,2]}}")
        assertThat(codec.encode(document)).doesNotContain("future")
    }

    @Test
    fun `keyframe 총수 상한을 객체를 만들기 전에 확인한다`() {
        val keys = (0 until 20).joinToString(",") { "{\"timeMs\":$it,\"value\":0}" }
        val track = "{\"nodeId\":\"shape-a\",\"property\":\"opacity\",\"keyframes\":[$keys]}"
        val text = fixture("scene-demo.json").replace(
            "\"animations\":[]",
            "\"animations\":[{\"id\":\"t\",\"durationMs\":100,\"tracks\":[$track]}]",
        )
        val limited = SceneCodec(limits = com.naury.kinetica.core.scene.SceneLimits(maxTotalKeyframes = 10))

        assertThat((limited.decode(text) as DecodeResult.Failure).error).isInstanceOf(SceneLoadError.Malformed::class.java)
        assertThat(codec.decode(text)).isInstanceOf(DecodeResult.Success::class.java)
    }

    private fun richDocument(): SceneDocument {
        val base = TestScenes.sample()
        val extra = listOf(
            SceneNode(NodeId("e"), NodeContent.Ellipse(Size(80.5, 40.25)), name = "원", visible = false, locked = true),
            SceneNode(
                NodeId("p"),
                NodeContent.Path(
                    listOf(
                        PathCommand.MoveTo(Vec2(0.0, 0.0)),
                        PathCommand.LineTo(Vec2(10.0, 0.0)),
                        PathCommand.QuadTo(Vec2(15.0, 5.0), Vec2(10.0, 10.0)),
                        PathCommand.CubicTo(Vec2(5.0, 15.0), Vec2(0.0, 15.0), Vec2(0.0, 10.0)),
                        PathCommand.Close,
                    ),
                ),
                style = NodeStyle(stroke = ArgbColor(0x80FF0000.toInt()), strokeWidth = 2.5, opacity = 0.75),
            ),
            SceneNode(
                NodeId("t"),
                NodeContent.Text(Size(300.0, 80.0), "안녕 \"Kinetica\" 😀\n둘째 줄", fontSize = 24.0, alignment = TextAlignment.Center),
                metadata = mapOf("role" to "title"),
            ),
            SceneNode(NodeId("i"), NodeContent.Image(Size(64.0, 64.0), "content://media/external/images/1")),
            SceneNode(
                NodeId("r"),
                NodeContent.Rectangle(Size(10.0, 10.0), cornerRadius = 3.0),
                transform = Transform2D(rotationDegrees = 12.5, pivotX = 5.0),
            ),
        )
        return base.copy(
            artboard = Artboard(1080.0, 1920.0, background = ArgbColor(0xFF101216.toInt())),
            nodes = base.nodes + extra.associateBy { it.id },
            rootOrder = base.rootOrder + extra.map { it.id },
            timelines = listOf(richTimeline()),
            extensions = mapOf("host.meta" to "{\"v\":1}"),
        )
    }

    private fun richTimeline(): Timeline = Timeline(
        id = "intro",
        durationMs = 1_000,
        fpsHint = 30,
        markers = listOf(TimelineMarker("mid", 500)),
        tracks = listOf(
            PropertyTrack(
                NodeId("a"),
                AnimatableProperty.TranslationX,
                listOf(
                    Keyframe(0, KeyValue.Scalar(0.0), EasingSpec.EaseInOut),
                    Keyframe(300, KeyValue.Scalar(120.5), EasingSpec.Spring(0.6, 300.0)),
                    Keyframe(600, KeyValue.Scalar(80.0), EasingSpec.Steps(4)),
                    Keyframe(1_000, KeyValue.Scalar(0.0)),
                ),
            ),
            PropertyTrack(
                NodeId("e"),
                AnimatableProperty.Fill,
                listOf(
                    Keyframe(0, KeyValue.Color(ArgbColor(0xFF000000.toInt()))),
                    Keyframe(1_000, KeyValue.Color(ArgbColor(0x00FFFFFF))),
                ),
            ),
        ),
    )
}
