package com.naury.kinetica.render

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.geometry.Viewport
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.CornerRadii
import com.naury.kinetica.core.scene.Gradient
import com.naury.kinetica.core.scene.GradientStop
import com.naury.kinetica.core.scene.GradientType
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.scene.Shadow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SceneLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val red = ArgbColor(0xFFFF0000.toInt())
    private val blue = ArgbColor(0xFF0000FF.toInt())

    private fun scene(
        vararg nodes: SceneNode,
        roots: List<String> = nodes.filter {
            it.parentId == null
        }.map { it.id.value },
    ) = SceneDocument(
        documentId = "layout",
        artboard = Artboard(200.0, 200.0, ArgbColor(0xFFFFFFFF.toInt())),
        nodes = nodes.associateBy { it.id },
        rootOrder = roots.map(::NodeId),
    )

    // 회전·배율·pivot이 있는 Frame 안에, 또 변환된 사각형이 들어 있다.
    private val nested = scene(
        SceneNode(
            NodeId("frame"),
            NodeContent.Frame(Size(120.0, 80.0), listOf(NodeId("inner")), clip = false),
            transform = Transform2D(
                translationX = 40.0,
                translationY = 30.0,
                rotationDegrees = 30.0,
                scaleX = 1.2,
                scaleY = 0.8,
                pivotX = 60.0,
                pivotY = 40.0,
            ),
            style = NodeStyle(fill = blue),
        ),
        SceneNode(
            NodeId("inner"),
            NodeContent.Rectangle(Size(30.0, 20.0)),
            parentId = NodeId("frame"),
            transform = Transform2D(translationX = 70.0, translationY = 10.0, rotationDegrees = -45.0, pivotX = 15.0, pivotY = 10.0),
            style = NodeStyle(fill = red),
        ),
    )

    private fun capture(content: @androidx.compose.runtime.Composable () -> Unit) = run {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(200.dp).testTag("root")) { content() }
            }
        }
        compose.onNodeWithTag("root").captureToImage().toPixelMap()
    }

    @Test
    fun `영역보다 큰 artboard도 왼쪽 위부터 viewport대로 그린다`() {
        val wide = scene().copy(artboard = Artboard(400.0, 400.0, red))

        val pixels = capture { SceneLayout(wide, Viewport(panX = 0.0, panY = 0.0, zoom = 0.5), Modifier.fillMaxSize()) }

        assertThat(pixels[2, 2]).isEqualTo(Color.Red)
        assertThat(pixels[197, 197]).isEqualTo(Color.Red)
    }

    @Test
    fun `순서·위치가 바뀌어도 호스트 뷰를 새로 만들지 않아 상태가 유지된다`() {
        fun slot(id: String, x: Double) =
            SceneNode(NodeId(id), NodeContent.HostSlot(Size(50.0, 50.0), id), transform = Transform2D(translationX = x))
        val document = mutableStateOf(scene(slot("a", 0.0), slot("b", 100.0)))
        // 키마다 처음 기억한 객체를 남긴다. 위치로 구분하면 순서가 바뀔 때 다른 키의 상태를 이어받는다.
        val seen = mutableMapOf<String, Any>()
        val slots: SceneSlots = listOf("a", "b").associateWith { key ->
            {
                val state = remember { Any() }
                seen[key] = state
                Box(Modifier.fillMaxSize())
            }
        }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(200.dp)) { SceneLayout(document.value, Viewport(0.0, 0.0, 1.0), Modifier.fillMaxSize(), slots = slots) }
            }
        }
        compose.waitForIdle()
        val before = seen.toMap()

        document.value = scene(slot("b", 0.0), slot("a", 120.0), roots = listOf("b", "a"))
        compose.waitForIdle()

        assertThat(seen).isEqualTo(before)
    }

    @Test
    fun `호스트가 재구성될 때마다 slots 맵을 새로 넘겨도 다른 노드는 다시 그리지 않고 호스트 뷰 상태도 유지한다`() {
        val document = scene(
            SceneNode(NodeId("photo"), NodeContent.Image(Size(50.0, 50.0), "content://photo")),
            SceneNode(NodeId("slot"), NodeContent.HostSlot(Size(50.0, 50.0), "chart"), transform = Transform2D(translationX = 100.0)),
        )
        var imageReads = 0
        val images = SceneImageProvider {
            imageReads++
            null
        }
        val tick = mutableStateOf(0)
        val states = mutableSetOf<Any>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(200.dp)) {
                    // 호스트 뷰가 쓰는 값이 바뀔 때마다 호스트 화면이 다시 그려지며 slots를 새 맵으로 만든다.
                    val value = tick.value
                    val slots: SceneSlots = mapOf(
                        "chart" to {
                            states += remember { Any() }
                            BasicText("$value")
                        },
                    )
                    SceneLayout(document, Viewport(0.0, 0.0, 1.0), Modifier.fillMaxSize(), images, slots)
                }
            }
        }
        compose.waitForIdle()
        val first = imageReads

        repeat(5) {
            tick.value++
            compose.waitForIdle()
        }

        assertThat(imageReads).isEqualTo(first)
        assertThat(states).hasSize(1)
        compose.onNodeWithText("5").assertExists()
    }

    @Test
    fun `그라디언트·별·모서리별 반지름·안쪽 그림자를 그린다`() {
        val gradient = Gradient(GradientType.Linear, listOf(GradientStop(0.0, red), GradientStop(1.0, blue)), angleDegrees = 0.0)
        val card = SceneNode(
            NodeId("card"),
            NodeContent.Rectangle(Size(100.0, 60.0), corners = CornerRadii(30.0, 0.0, 0.0, 0.0)),
            style = NodeStyle(
                gradient = gradient,
                shadows = listOf(Shadow(ArgbColor(0xFF000000.toInt()), blur = 0.0, offsetY = 0.0, spread = 6.0, inner = true)),
            ),
        )
        val star = SceneNode(
            NodeId("star"),
            NodeContent.Polygon(Size(80.0, 80.0), sides = 5, innerRatio = 0.4),
            transform = Transform2D(translationX = 110.0, translationY = 110.0),
            style = NodeStyle(fill = red),
        )

        val pixels = capture { SceneLayout(scene(card, star), Viewport(0.0, 0.0, 1.0), Modifier.fillMaxSize()) }

        // 안쪽 그림자(띠 6) 안쪽에서 왼쪽은 빨강에 가깝고 오른쪽은 파랑에 가깝다.
        assertThat(pixels[12, 30].red).isGreaterThan(pixels[12, 30].blue)
        assertThat(pixels[88, 30].blue).isGreaterThan(pixels[88, 30].red)
        // 안쪽 그림자는 가장자리를 검게 칠한다.
        assertThat(pixels[98, 30].red + pixels[98, 30].blue).isLessThan(0.2f)
        // 왼쪽 위만 둥글어 그 모서리는 배경(흰색)이고, 오른쪽 위 모서리는 칠해진다.
        assertThat(pixels[1, 1]).isEqualTo(Color.White)
        assertThat(pixels[97, 1]).isNotEqualTo(Color.White)
        // 별: 가운데는 칠하고, 꼭짓점 사이 바깥(정다각형이면 칠해질 자리)은 비운다.
        assertThat(pixels[150, 150]).isEqualTo(Color.Red)
        assertThat(pixels[150, 186]).isEqualTo(Color.White)
    }

    @Test
    fun `새 모양 효과도 Canvas 렌더러와 같은 자리에 그린다`() {
        val shapes = scene(
            SceneNode(
                NodeId("poly"),
                NodeContent.Polygon(Size(120.0, 100.0), sides = 6),
                transform = Transform2D(translationX = 20.0, translationY = 30.0, rotationDegrees = 15.0, pivotX = 60.0, pivotY = 50.0),
                style = NodeStyle(fill = blue, stroke = red, strokeWidth = 6.0, strokeDash = listOf(10.0, 6.0)),
            ),
        )
        val viewport = Viewport(panX = 4.0, panY = -2.0, zoom = 0.95)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Column {
                    Box(Modifier.size(200.dp).testTag("canvas")) { SceneCanvas(shapes, viewport, Modifier.fillMaxSize()) }
                    Box(Modifier.size(200.dp).testTag("layout")) { SceneLayout(shapes, viewport, Modifier.fillMaxSize()) }
                }
            }
        }
        val canvas = compose.onNodeWithTag("canvas").captureToImage().toPixelMap()
        val layout = compose.onNodeWithTag("layout").captureToImage().toPixelMap()

        val pure = listOf(Color.Red, Color.Blue, Color.White)
        val samples = (2 until 198 step 3).flatMap { x -> (2 until 198 step 3).map { y -> canvas[x, y] to layout[x, y] } }
            .filter { (a, b) -> a in pure && b in pure }
        assertThat(samples.size).isGreaterThan(3_000)
        assertThat(samples.count { (a, b) -> a != b }.toDouble() / samples.size).isLessThan(0.003)
    }

    @Test
    fun `레이어 렌더러는 Canvas 렌더러와 같은 자리에 그린다`() {
        val viewport = Viewport(panX = 5.0, panY = -3.0, zoom = 0.9)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Column {
                    Box(Modifier.size(200.dp).testTag("canvas")) { SceneCanvas(nested, viewport, Modifier.fillMaxSize()) }
                    Box(Modifier.size(200.dp).testTag("layout")) { SceneLayout(nested, viewport, Modifier.fillMaxSize()) }
                }
            }
        }
        val canvas = compose.onNodeWithTag("canvas").captureToImage().toPixelMap()
        val layout = compose.onNodeWithTag("layout").captureToImage().toPixelMap()

        // 가장자리 안티앨리어싱 차이는 빼고, 두 결과가 모두 확실히 한 색인 점만 비교한다.
        val pure = listOf(Color.Red, Color.Blue, Color.White)
        val samples = (2 until 198 step 3).flatMap { x -> (2 until 198 step 3).map { y -> canvas[x, y] to layout[x, y] } }
            .filter { (a, b) -> a in pure && b in pure }
        val compared = samples.size
        val mismatched = samples.count { (a, b) -> a != b }
        assertThat(compared).isGreaterThan(3_000)
        assertThat(mismatched.toDouble() / compared).isLessThan(0.002)
        assertThat(layout.buffer.count { it == Color.Red.toArgbInt() }).isGreaterThan(200)
    }

    @Test
    fun `호스트 뷰를 변환된 자리에 그리고 없는 키는 빈 틀로 그린다`() {
        val doc = scene(
            SceneNode(
                NodeId("host"),
                NodeContent.HostSlot(Size(50.0, 50.0), "widget.green"),
                transform = Transform2D(translationX = 100.0, translationY = 20.0),
            ),
            SceneNode(
                NodeId("missing"),
                NodeContent.HostSlot(Size(50.0, 50.0), "widget.none"),
                transform = Transform2D(translationX = 10.0, translationY = 120.0),
            ),
        )
        val pixels = capture {
            SceneLayout(
                doc,
                Viewport(),
                Modifier.fillMaxSize(),
                slots = mapOf(
                    "widget.green" to {
                        Box(Modifier.fillMaxSize().background(Color.Green))
                    },
                ),
            )
        }

        assertThat(pixels[125, 45]).isEqualTo(Color.Green)
        assertThat(pixels[95, 45]).isEqualTo(Color.White)
        assertThat(pixels[35, 145]).isNotEqualTo(Color.Green)
    }

    @Test
    fun `Frame은 자식을 상자 밖으로 그리지 않는다`() {
        val doc = scene(
            SceneNode(
                NodeId("frame"),
                NodeContent.Frame(Size(60.0, 60.0), listOf(NodeId("big"))),
                transform = Transform2D(translationX = 20.0, translationY = 20.0),
            ),
            SceneNode(NodeId("big"), NodeContent.Rectangle(Size(150.0, 150.0)), parentId = NodeId("frame"), style = NodeStyle(fill = red)),
        )
        val pixels = capture { SceneLayout(doc, Viewport(), Modifier.fillMaxSize()) }

        assertThat(pixels[50, 50]).isEqualTo(Color.Red)
        assertThat(pixels[120, 120]).isEqualTo(Color.White)
    }

    @Test
    fun `동작은 사용 모드에서만 호스트에 알린다`() {
        val actions = mutableListOf<String>()
        val doc = scene(
            SceneNode(
                NodeId("button"),
                NodeContent.Rectangle(Size(80.0, 40.0)),
                transform = Transform2D(translationX = 60.0, translationY = 80.0),
                style = NodeStyle(fill = blue),
                action = "open_order",
            ),
        )
        val interactive = mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                SceneLayout(doc, Viewport(), Modifier.size(200.dp).testTag("root"), interactive = interactive.value, onAction = {
                    actions +=
                        it
                })
            }
        }
        compose.onNodeWithTag("root").performTouchInput { click(Offset(100f, 100f)) }
        compose.waitForIdle()
        assertThat(actions).isEmpty()

        interactive.value = true
        compose.waitForIdle()
        compose.onNodeWithTag("root").performTouchInput { click(Offset(100f, 100f)) }
        compose.onNodeWithTag("root").performTouchInput { click(Offset(20f, 20f)) }
        compose.waitForIdle()
        assertThat(actions).containsExactly("open_order")
    }

    private fun Color.toArgbInt(): Int =
        (alpha * 255).toInt() shl 24 or ((red * 255).toInt() shl 16) or ((green * 255).toInt() shl 8) or (blue * 255).toInt()

    @Test
    fun `화면 읽기에 이름·역할을 알리고 장식은 숨기며 동작 노드는 버튼으로 알린다`() {
        val logo = SceneNode(
            NodeId("logo"),
            NodeContent.Rectangle(Size(40.0, 40.0)),
            accessibility = com.naury.kinetica.core.scene.NodeAccessibility("회사 로고", com.naury.kinetica.core.scene.NodeRole.Image),
        )
        val deco = SceneNode(
            NodeId("deco"),
            NodeContent.Text(Size(80.0, 20.0), "장식 글자"),
            transform = Transform2D(translationY = 50.0),
            accessibility = com.naury.kinetica.core.scene.NodeAccessibility(hidden = true),
        )
        val buy = SceneNode(
            NodeId("buy"),
            NodeContent.Text(Size(80.0, 20.0), "구매"),
            transform = Transform2D(translationY = 100.0),
            action = "buy",
        )
        compose.setContent {
            SceneLayout(scene(logo, deco, buy), Viewport(0.0, 0.0, 1.0), Modifier.size(200.dp), interactive = true)
        }

        compose.onNodeWithContentDescription("회사 로고").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image))
        compose.onNodeWithText("장식 글자").assertDoesNotExist()
        compose.onNodeWithText("구매").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    /** 누를 때마다 수가 오르는 글자와 50줄 목록이 있는 호스트 뷰. 숨겼다 보여도 상태가 남는지 볼 때 쓴다. */
    private class FormSlot {
        val created = mutableSetOf<Any>()
        var list: LazyListState? = null

        val slots: SceneSlots = mapOf(
            "form" to {
                var count by rememberSaveable { mutableIntStateOf(0) }
                created += remember { Any() }
                val state = rememberLazyListState()
                list = state
                Column {
                    BasicText("count $count", Modifier.clickable { count++ })
                    LazyColumn(Modifier.size(40.dp).testTag("list"), state) { items(50) { BasicText("row $it") } }
                }
            },
        )
    }

    private fun formScene(visible: Boolean) = scene(
        SceneNode(NodeId("group"), NodeContent.Group(listOf(NodeId("form"))), visible = visible),
        SceneNode(NodeId("form"), NodeContent.HostSlot(Size(150.0, 180.0), "form"), parentId = NodeId("group")),
    )

    private fun showForm(form: FormSlot, document: androidx.compose.runtime.State<SceneDocument>, policy: HiddenSlotPolicy) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                SceneLayout(document.value, Viewport(), Modifier.size(200.dp), slots = form.slots, interactive = true, hiddenSlots = policy)
            }
        }
        compose.onNodeWithText("count 0").performClick()
        compose.onNodeWithTag("list").performScrollToIndex(20)
        compose.waitForIdle()
    }

    @Test
    fun `조상을 숨겼다 다시 보여도 호스트 뷰의 상태와 스크롤 위치가 남고 숨긴 동안은 누르거나 읽을 수 없다`() {
        val form = FormSlot()
        val document = mutableStateOf(formScene(visible = true))
        showForm(form, document, HiddenSlotPolicy.Keep)

        document.value = formScene(visible = false)
        compose.waitForIdle()
        compose.onNodeWithText("count 1").assertDoesNotExist()
        compose.onNodeWithTag("list").assertDoesNotExist()
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)

        document.value = formScene(visible = true)
        compose.waitForIdle()
        compose.onNodeWithText("count 1").assertExists()
        assertThat(form.list?.firstVisibleItemIndex).isEqualTo(20)
        assertThat(form.created).hasSize(1)
    }

    @Test
    fun `Dispose면 숨길 때 호스트 뷰를 빼서 다시 보이면 처음 상태다`() {
        val form = FormSlot()
        val document = mutableStateOf(formScene(visible = true))
        showForm(form, document, HiddenSlotPolicy.Dispose)

        document.value = formScene(visible = false)
        compose.waitForIdle()
        document.value = formScene(visible = true)
        compose.waitForIdle()

        compose.onNodeWithText("count 0").assertExists()
        assertThat(form.list?.firstVisibleItemIndex).isEqualTo(0)
        assertThat(form.created).hasSize(2)
    }

    @Test
    fun `불투명도 0에서는 누를 수 없고 나타나는 중에는 누를 수 있다`() {
        val taps = mutableListOf<String>()
        fun doc(opacity: Double) = scene(
            SceneNode(
                NodeId("button"),
                NodeContent.Rectangle(Size(80.0, 40.0)),
                style = NodeStyle(fill = blue, opacity = opacity),
                action = "open",
            ),
            SceneNode(
                NodeId("host"),
                NodeContent.HostSlot(Size(80.0, 40.0), "tap"),
                transform = Transform2D(translationY = 100.0),
                style = NodeStyle(opacity = opacity),
            ),
        )
        val document = mutableStateOf(doc(0.0))
        val slots: SceneSlots = mapOf("tap" to { Box(Modifier.fillMaxSize().clickable { taps += "host" }) })
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                SceneLayout(
                    document.value,
                    Viewport(),
                    Modifier.size(
                        200.dp,
                    ).testTag("root"),
                    slots = slots,
                    interactive = true,
                    onAction = {
                        taps += it
                    },
                )
            }
        }
        fun tapBoth() {
            compose.onNodeWithTag("root").performTouchInput { click(Offset(40f, 20f)) }
            compose.onNodeWithTag("root").performTouchInput { click(Offset(40f, 120f)) }
            compose.waitForIdle()
        }

        tapBoth()
        assertThat(taps).isEmpty()
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)

        // 0에서 1로 페이드인하는 중간 값
        document.value = doc(0.3)
        compose.waitForIdle()
        tapBoth()
        assertThat(taps).containsExactly("open", "host").inOrder()
    }
}
