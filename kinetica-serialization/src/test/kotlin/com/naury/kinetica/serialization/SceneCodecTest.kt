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
import com.naury.kinetica.core.scene.AutoLayout
import com.naury.kinetica.core.scene.BoundProperty
import com.naury.kinetica.core.scene.Components
import com.naury.kinetica.core.scene.CornerRadii
import com.naury.kinetica.core.scene.CrossAlign
import com.naury.kinetica.core.scene.Gradient
import com.naury.kinetica.core.scene.GradientStop
import com.naury.kinetica.core.scene.GradientType
import com.naury.kinetica.core.scene.Insets
import com.naury.kinetica.core.scene.InstanceOf
import com.naury.kinetica.core.scene.LayoutConstraints
import com.naury.kinetica.core.scene.LayoutDirection
import com.naury.kinetica.core.scene.LineCap
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.Override
import com.naury.kinetica.core.scene.PathCommand
import com.naury.kinetica.core.scene.Pin
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.scene.Shadow
import com.naury.kinetica.core.scene.SizeLimits
import com.naury.kinetica.core.scene.TextAlignment
import com.naury.kinetica.core.scene.TextDecoration
import com.naury.kinetica.core.scene.TextSpan
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
    fun `자동 배치가 덜 된 문서는 열 때 배치하고 잘못된 배치 값은 거부한다`() {
        val raw = codec.encode(richDocument()).replace("\"x\":99", "\"x\":0")
        val opened = codec.decode(raw) as DecodeResult.Success
        val bad = codec.encode(richDocument()).replace("\"direction\":\"column\"", "\"direction\":\"diagonal\"")

        assertThat(raw).isNotEqualTo(codec.encode(richDocument()))
        assertThat(opened.document.nodes.getValue(NodeId("inner")).transform.translationX).isEqualTo(99.0)
        assertThat(codec.decode(bad)).isInstanceOf(DecodeResult.Failure::class.java)
    }

    @Test
    fun `토큰·컴포넌트·인스턴스와 덮어쓴 값이 왕복하고 열 때 인스턴스를 원본에 맞춘다`() {
        val blue = ArgbColor(0xFF2255FF.toInt())
        val main = SceneNode(
            NodeId("btn"),
            NodeContent.Frame(Size(200.0, 60.0), listOf(NodeId("lbl"))),
            component = "Button",
            style = NodeStyle(fill = blue, fillToken = "brand"),
        )
        val label = SceneNode(NodeId("lbl"), NodeContent.Text(Size(180.0, 40.0), "OK"), parentId = NodeId("btn"))
        val instance = SceneNode(
            NodeId("buy"),
            NodeContent.Frame(Size(200.0, 60.0)),
            instanceOf = InstanceOf(NodeId("btn"), mapOf(NodeId("lbl") to Override(text = "Buy", visible = true))),
        )
        val raw = SceneDocument(
            documentId = "kit",
            artboard = Artboard(400.0, 400.0),
            nodes = listOf(main, label, instance).associateBy { it.id },
            rootOrder = listOf(main.id, instance.id),
            tokens = mapOf("brand" to blue),
        )

        val opened = decoded(codec.encode(raw))
        val copy = opened.node(Components.childId(NodeId("buy"), NodeId("lbl")))!!

        assertThat(opened.tokens).isEqualTo(raw.tokens)
        assertThat(opened.node(NodeId("btn"))).isEqualTo(main)
        assertThat(opened.node(NodeId("buy"))!!.instanceOf).isEqualTo(instance.instanceOf)
        assertThat((copy.content as NodeContent.Text).text).isEqualTo("Buy")
        assertThat(decoded(codec.encode(opened))).isEqualTo(opened)
    }

    @Test
    fun `예전 형식의 그림자 하나도 읽는다`() {
        val legacy = codec.encode(TestScenes.sample()).replace(
            "\"opacity\":1}",
            "\"opacity\":1,\"shadow\":{\"color\":\"#33000000\",\"x\":0,\"y\":4,\"blur\":12}}",
        )

        val document = (codec.decode(legacy) as DecodeResult.Success).document

        assertThat(document.nodes.values.first().style.shadows).containsExactly(Shadow(ArgbColor(0x33000000), 0.0, 4.0, 12.0))
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
            SceneNode(
                NodeId("i"),
                NodeContent.Image(Size(64.0, 64.0), "content://media/external/images/1"),
                bindings = mapOf(BoundProperty.ImageUri to "user.avatar", BoundProperty.Visible to "user.show"),
                action = "open_profile",
            ),
            SceneNode(
                NodeId("r"),
                NodeContent.Rectangle(Size(10.0, 10.0), cornerRadius = 3.0),
                transform = Transform2D(rotationDegrees = 12.5, pivotX = 5.0),
                constraints = LayoutConstraints(Pin.Both, Pin.End),
            ),
        )
        return base.copy(
            artboard = Artboard(1080.0, 1920.0, background = ArgbColor(0xFF101216.toInt())),
            nodes = base.nodes + (extra + uiNodes() + shapeNodes() + textNodes()).associateBy { it.id },
            rootOrder = base.rootOrder + (extra + uiNodes() + shapeNodes() + textNodes()).filter { it.parentId == null }.map { it.id },
            timelines = listOf(richTimeline()),
            extensions = mapOf("host.meta" to "{\"v\":1}"),
        )
    }

    /** Frame·자동 배치·호스트 뷰. 위치는 배치가 끝난 값이라 읽어도 그대로다. */
    private fun uiNodes(): List<SceneNode> = listOf(
        SceneNode(
            NodeId("frame"),
            NodeContent.Frame(
                Size(320.0, 200.0),
                listOf(NodeId("inner")),
                cornerRadius = 20.0,
                clip = false,
                layout = AutoLayout(
                    LayoutDirection.Vertical,
                    gap = 8.0,
                    padding = Insets(10.0, 10.0, 12.0, 14.0),
                    crossAlign = CrossAlign.Center,
                    wrap = true,
                    lineGap = 6.0,
                ),
            ),
            style = NodeStyle(
                fill = ArgbColor(0xFFFFFFFF.toInt()),
                shadows = listOf(
                    Shadow(ArgbColor(0x33000000), 0.0, 6.0, 18.0),
                    Shadow(ArgbColor(0x22000000), 1.0, 2.0, 4.0, spread = -2.0, inner = true),
                ),
                gradient = Gradient(
                    GradientType.Linear,
                    listOf(GradientStop(0.0, ArgbColor(0xFFFF0000.toInt())), GradientStop(1.0, ArgbColor(0xFF0000FF.toInt()))),
                    45.0,
                ),
                blur = 3.5,
            ),
        ),
        SceneNode(
            NodeId("inner"),
            NodeContent.HostSlot(Size(120.0, 176.0), "widget.weather"),
            parentId = NodeId("frame"),
            transform = Transform2D(translationX = 99.0, translationY = 10.0),
            layoutFill = true,
            sizeLimits = SizeLimits(minWidth = 50.0, maxHeight = 500.0),
        ),
    )

    /** 굵기·줄 높이·자간·글꼴·기울임·밑줄과 리치 텍스트 구간. */
    private fun textNodes(): List<SceneNode> = listOf(
        SceneNode(
            NodeId("styled"),
            NodeContent.Text(
                Size(200.0, 60.0),
                "굵게 기울임",
                fontSize = 20.0,
                fontWeight = 700,
                lineHeight = 28.0,
                letterSpacing = -0.5,
                fontFamily = "serif",
                italic = true,
                decoration = TextDecoration.Underline,
                spans = listOf(
                    TextSpan(0, 2, fontWeight = 900, color = ArgbColor(0xFFFF0000.toInt())),
                    TextSpan(
                        3,
                        6,
                        fontFamily = "monospace",
                        fontSize = 24.5,
                        italic = false,
                        decoration = TextDecoration.Strikethrough,
                    ),
                ),
            ),
            bindings = mapOf(BoundProperty.Text to "order.total"),
        ),
    )

    /** 다각형·모서리별 반지름·그라디언트·점선·선 끝 모양. */
    private fun shapeNodes(): List<SceneNode> = listOf(
        SceneNode(
            NodeId("star"),
            NodeContent.Polygon(Size(100.0, 90.0), sides = 6, innerRatio = 0.45),
            style = NodeStyle(stroke = ArgbColor(0xFF000000.toInt()), strokeWidth = 2.0, strokeDash = listOf(4.0, 2.0)),
        ),
        SceneNode(
            NodeId("card"),
            NodeContent.Rectangle(Size(80.0, 40.0), corners = CornerRadii(1.0, 2.0, 3.0, 4.0)),
            style = NodeStyle(
                gradient = Gradient(
                    GradientType.Radial,
                    listOf(
                        GradientStop(0.0, ArgbColor(0xFFFFFFFF.toInt())),
                        GradientStop(0.5, ArgbColor(0x80FFFFFF.toInt())),
                        GradientStop(1.0, ArgbColor(0)),
                    ),
                ),
            ),
        ),
        SceneNode(
            NodeId("arrow"),
            NodeContent.Path(listOf(PathCommand.MoveTo(Vec2(0.0, 0.0)), PathCommand.LineTo(Vec2(50.0, 10.0)))),
            style = NodeStyle(stroke = ArgbColor(0xFF000000.toInt()), strokeWidth = 3.0, startCap = LineCap.Circle, endCap = LineCap.Arrow),
        ),
    )

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
