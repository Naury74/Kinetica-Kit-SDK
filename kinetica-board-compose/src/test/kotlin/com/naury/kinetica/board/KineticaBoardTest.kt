package com.naury.kinetica.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp-mdpi")
class KineticaBoardTest {
    @get:Rule
    val compose = createComposeRule()

    private val catalog =
        listOf(BoardWidget("a", "A", widthDp = 120f, heightDp = 80f), BoardWidget("b", "B", widthDp = 120f, heightDp = 80f))

    // 눌림 표시가 사라지는 애니메이션처럼 끝나지 않은 프레임 요청을 남긴 채 끝나면, 같은 JVM의 다음 View 기반 테스트에서
    // Compose 프레임이 오지 않아 재구성과 LaunchedEffect가 멈춘다. 테스트마다 애니메이션이 끝날 때까지 시계를 돌린다.
    @After
    fun settleAnimations() {
        compose.waitForIdle()
    }

    private fun show(state: KineticaBoardState, editing: Boolean, onClick: (String) -> Unit = {}) {
        val edit = mutableStateOf(editing)
        compose.setContent {
            Box(Modifier.size(400.dp, 600.dp)) {
                KineticaBoard(state, Modifier.fillMaxSize(), editing = edit.value) { key ->
                    TextButton(onClick = { onClick(key) }, modifier = Modifier.fillMaxSize()) { Text("widget $key") }
                }
            }
        }
    }

    @Test
    fun `사용 모드에서는 위젯이 눌리고 편집 모드에서는 눌리지 않는다`() {
        val clicks = mutableListOf<String>()
        val state = KineticaBoardState.create(catalog)
        show(state, editing = false) { clicks += it }

        compose.onNodeWithText("widget a").performClick()

        assertThat(clicks).containsExactly("a")
    }

    @Test
    fun `왼쪽 가장자리로 끌어 놓으면 화면 왼쪽 반을 차지하고 한 번에 되돌린다`() {
        val state = KineticaBoardState.create(catalog)
        show(state, editing = true)
        val before = state.rectOf("a")

        compose.onNodeWithTag(BOARD_WIDGET_TAG + "a").performTouchInput {
            down(center)
            moveTo(Offset(center.x + 40f, center.y))
            // 보드 왼쪽 끝(위젯 왼쪽 위 12dp)으로 끈다.
            moveTo(Offset(-8f, center.y + 100f))
            up()
        }
        compose.waitForIdle()

        val half = state.rectOf("a")!!
        val scale = 400.0 * KineticaBoardState.UNITS / KineticaBoardState.REFERENCE_WIDTH
        // 보이는 영역 600dp에서 위젯 추가 버튼 자리 88dp를 뺀 뒤 간격 12dp를 두고 나눈 왼쪽 반.
        assertThat(half.left * scale).isWithin(0.5).of(12.0 * KineticaBoardState.UNITS)
        assertThat(half.right * scale).isWithin(0.5).of(194.0 * KineticaBoardState.UNITS)
        assertThat(half.height).isWithin(0.5).of(488.0 * KineticaBoardState.UNITS)
        state.history.undo()
        assertThat(state.rectOf("a")).isEqualTo(before)
    }

    @Test
    fun `위쪽 가장자리로 끌면 배치 틀 고르기판이 뜨고 고른 칸 자리에 놓인다`() {
        val state = KineticaBoardState.create(catalog)
        show(state, editing = true)
        val unit = KineticaBoardState.UNITS
        val picker = SnapLayouts.picker(com.naury.kinetica.core.geometry.Rect(0.0, 0.0, 400 * unit, 600 * unit), 12 * unit, unit)
        // 반반 틀의 오른쪽 칸. 고르기판 좌표는 문서 단위이고, 위젯 a는 화면 (12dp, 12dp)에 있다.
        val slot = picker.slots.first { it.layout == 0 && it.zone == 1 }
        val target = Offset((slot.hit.center.x / unit - 12).toFloat(), (slot.hit.center.y / unit - 12).toFloat())

        compose.onNodeWithTag(BOARD_WIDGET_TAG + "a").performTouchInput {
            down(center)
            moveTo(Offset(center.x + 30f, center.y))
            moveTo(Offset(center.x, -10f))
            moveTo(target)
            up()
        }
        compose.waitForIdle()

        val placed = state.rectOf("a")!!
        val scale = 400.0 * unit / KineticaBoardState.REFERENCE_WIDTH
        assertThat(placed.left * scale).isWithin(0.5).of(206.0 * unit)
        assertThat(placed.right * scale).isWithin(0.5).of(388.0 * unit)
        assertThat(placed.height).isWithin(0.5).of(488.0 * unit)
    }

    @Test
    fun `오른쪽 아래 손잡이로 크기를 바꾸고 빼기 표시로 뺀다`() {
        val state = KineticaBoardState.create(catalog)
        show(state, editing = true)
        val before = state.rectOf("a")!!

        compose.onNodeWithContentDescription("Resize A").performTouchInput {
            down(center)
            moveTo(Offset(center.x + 20f, center.y))
            moveTo(Offset(center.x + 60f, center.y + 30f))
            up()
        }
        compose.waitForIdle()
        val resized = state.rectOf("a")!!
        compose.onNodeWithContentDescription("Remove B").performClick()

        assertThat(resized.left).isEqualTo(before.left)
        assertThat(resized.height).isGreaterThan(before.height)
        assertThat(resized.width).isGreaterThan(before.width)
        assertThat(state.placedKeys).containsExactly("a")
    }

    @Test
    fun `편집 모드의 위젯 추가 메뉴로 뺀 위젯을 다시 넣는다`() {
        val state = KineticaBoardState.create(catalog)
        state.remove("b")
        show(state, editing = true)

        compose.onNodeWithText("Add widget").performClick()
        compose.onNodeWithText("B").performClick()

        assertThat(state.placedKeys).containsExactly("a", "b")
    }

    @Test
    fun `위젯 추가 메뉴는 뒤로 가기로 닫히고 넣을 위젯이 없으면 누를 수 없는 안내만 보인다`() {
        val state = KineticaBoardState.create(catalog)
        state.remove("b")
        show(state, editing = true)

        compose.onNodeWithText("Add widget").performClick()
        compose.onNodeWithText("B").assertIsDisplayed()
        pressBackOnTopWindow()
        compose.onNodeWithText("B").assertDoesNotExist()
        assertThat(state.placedKeys).containsExactly("a")

        state.add("b")
        compose.onNodeWithText("Add widget").performClick()
        compose.onNodeWithText("All widgets are already on the board").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "w900dp-h800dp-mdpi")
    fun `끄는 도중에 접거나 펴서 보드 폭이 바뀌면 끌기를 취소하고 배치를 그대로 둔다`() {
        val state = KineticaBoardState.create(catalog)
        val width = mutableStateOf(400.dp)
        compose.setContent {
            Box(Modifier.size(width.value, 600.dp)) {
                KineticaBoard(state, Modifier.fillMaxSize(), editing = true) { key -> Text("widget $key") }
            }
        }
        val before = state.document.value
        val widget = compose.onNodeWithTag("${BOARD_WIDGET_TAG}a")
        widget.performTouchInput {
            down(center)
            moveBy(Offset(30f, 0f))
            moveBy(Offset(30f, 20f))
        }
        width.value = 700.dp
        compose.waitForIdle()
        widget.performTouchInput {
            moveBy(Offset(30f, 40f))
            up()
        }
        compose.waitForIdle()

        assertThat(state.rectOf("a")).isEqualTo(KineticaBoardState.rectOf(before.node(before.rootOrder.first())))
        assertThat(state.document.value).isEqualTo(before)
    }

    // 맨 위 창(메뉴 팝업)에 뒤로 가기 키를 보낸다.
    private fun pressBackOnTopWindow() {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        val top = (global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<*>).last() as android.view.View
        compose.runOnUiThread {
            top.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK))
            top.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK))
        }
        compose.waitForIdle()
    }

    @Test
    fun `사용 모드에서 길게 누르면 편집 요청이 오고 그 누름은 위젯에 가지 않는다`() {
        val clicks = mutableListOf<String>()
        var requested = 0
        val state = KineticaBoardState.create(catalog)
        compose.setContent {
            Box(Modifier.size(400.dp, 600.dp)) {
                KineticaBoard(state, Modifier.fillMaxSize(), onLongPress = { requested++ }) { key ->
                    TextButton(onClick = { clicks += key }, modifier = Modifier.fillMaxSize()) { Text("widget $key") }
                }
            }
        }

        compose.onNodeWithText("widget a").performTouchInput { longClick() }
        compose.waitForIdle()

        assertThat(requested).isEqualTo(1)
        assertThat(clicks).isEmpty()
    }

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test
    fun `접근성 동작으로 위젯을 옮기고 크게 만든다`() {
        val state = KineticaBoardState.create(catalog)
        show(state, editing = true)
        val before = state.rectOf("a")!!

        // TalkBack이 읽을 이름(위젯 이름)으로 찾는다.
        compose.onNodeWithContentDescription("A").performCustomAccessibilityActionWithLabel("Move down")
        compose.onNodeWithContentDescription("A").performCustomAccessibilityActionWithLabel("Make larger")

        val after = state.rectOf("a")!!
        assertThat(after.top).isGreaterThan(before.top)
        assertThat(after.width).isGreaterThan(before.width)
    }

    @Test
    fun `아래쪽 띠에 머물면 보드가 스크롤되며 위젯을 더 아래로 놓는다`() {
        val tall = (0 until 8).map { BoardWidget("w$it", "W$it", widthDp = 360f, heightDp = 120f) }
        val state = KineticaBoardState.create(tall)
        compose.setContent {
            Box(Modifier.size(400.dp, 400.dp)) { KineticaBoard(state, Modifier.fillMaxSize(), editing = true) { BasicBox() } }
        }
        val before = state.rectOf("w0")!!

        compose.onNodeWithTag(BOARD_WIDGET_TAG + "w0").performTouchInput {
            down(center)
            moveTo(Offset(center.x, center.y + 40f))
            // 보이는 영역 아래 끝에서 70dp 위(분할 자리 40dp 바로 안쪽의 자동 스크롤 띠)에 머문다.
            moveTo(Offset(center.x, 400f - 70f - 12f))
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag(BOARD_WIDGET_TAG + "w0").performTouchInput { up() }
        compose.waitForIdle()

        // 스크롤 없이 놓았다면 약 300dp 아래가 최대지만, 스크롤된 만큼 더 내려간다.
        assertThat((state.rectOf("w0")!!.top - before.top) / KineticaBoardState.UNITS).isGreaterThan(330.0)
    }

    @Test
    fun `손가락을 보드 아래 끝 너머로 빠르게 끌어 머물러도 스크롤된다`() {
        val tall = (0 until 3).map { BoardWidget("w$it", "W$it", widthDp = 360f, heightDp = 100f) }
        val state = KineticaBoardState.create(tall)
        compose.setContent {
            Box(Modifier.size(400.dp, 400.dp)) { KineticaBoard(state, Modifier.fillMaxSize(), editing = true) { BasicBox() } }
        }
        val before = state.rectOf("w0")!!

        compose.onNodeWithTag(BOARD_WIDGET_TAG + "w0").performTouchInput {
            down(center)
            moveTo(Offset(center.x, center.y + 40f))
            moveTo(Offset(center.x, 420f))
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag(BOARD_WIDGET_TAG + "w0").performTouchInput { up() }
        compose.waitForIdle()

        assertThat((state.rectOf("w0")!!.top - before.top) / KineticaBoardState.UNITS).isGreaterThan(420.0)
    }

    @Test
    fun `키보드로 고른 위젯을 방향키로 옮기고 Shift로 크기를 바꾸고 Delete로 뺀다`() {
        val state = KineticaBoardState.create(catalog)
        show(state, editing = true)
        val before = state.rectOf("a")!!
        val node = compose.onNodeWithTag(BOARD_WIDGET_TAG + "a")

        node.requestFocus()
        node.performKeyInput { pressKey(Key.DirectionRight) }
        node.performKeyInput {
            withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionDown) }
        }
        compose.waitForIdle()
        val moved = state.rectOf("a")!!
        node.performKeyInput { pressKey(Key.Delete) }
        compose.waitForIdle()

        assertThat(moved.left).isGreaterThan(before.left)
        assertThat(moved.height).isGreaterThan(before.height)
        assertThat(state.placedKeys).doesNotContain("a")
    }

    @Test
    fun `위젯을 다른 위젯 가운데에 놓으면 묶음이 되고 배지를 누르면 풀린다`() {
        val state = KineticaBoardState.create(catalog)
        show(state, editing = true)
        val a = compose.onNodeWithTag(BOARD_WIDGET_TAG + "a").fetchSemanticsNode().boundsInRoot
        val b = compose.onNodeWithTag(BOARD_WIDGET_TAG + "b").fetchSemanticsNode().boundsInRoot

        compose.onNodeWithTag(BOARD_WIDGET_TAG + "a").performTouchInput {
            down(center)
            moveTo(Offset(center.x + 20f, center.y))
            moveTo(Offset(b.center.x - a.left, b.center.y - a.top))
            up()
        }
        compose.waitForIdle()

        val stackId = state.placedKeys.single()
        assertThat(state.stackMembers(stackId)).containsExactly("b", "a").inOrder()

        compose.onNodeWithContentDescription("Split stack").performClick()
        compose.waitForIdle()
        assertThat(state.placedKeys).containsExactly("a", "b")
    }

    @Test
    fun `사용 모드의 묶음은 첫 위젯을 보이고 옆으로 넘기면 다음 위젯이 보인다`() {
        val state = KineticaBoardState.create(catalog)
        state.stack("a", "b")
        show(state, editing = false)

        compose.onNodeWithText("widget a").assertExists()
        compose.onNodeWithText("widget a").performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithText("widget b").assertIsDisplayed()
    }

    @Test
    fun `묶음 자동 넘김을 켜면 시간마다 다음 위젯이 보인다`() {
        val state = KineticaBoardState.create(catalog)
        state.stack("a", "b")
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.size(400.dp, 600.dp)) {
                KineticaBoard(state, Modifier.fillMaxSize(), stackAutoAdvanceMs = 1_000) { key -> Text("widget $key") }
            }
        }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("widget a").assertIsDisplayed()

        compose.mainClock.advanceTimeBy(2_000)

        compose.onNodeWithText("widget b").assertIsDisplayed()
    }

    @Test
    fun `오른쪽에서 왼쪽으로 쓰는 언어에서는 첫 위젯이 오른쪽에 놓이고 방향키 왼쪽은 화면 왼쪽으로 옮긴다`() {
        val state = KineticaBoardState.create(catalog)
        val saved = state.rectOf("a")
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.size(400.dp, 600.dp)) {
                    KineticaBoard(state, Modifier.fillMaxSize(), editing = true) { key -> Text("widget $key", Modifier.testTag("w-$key")) }
                }
            }
        }
        val first = compose.onNodeWithTag("w-a").fetchSemanticsNode().boundsInRoot

        assertThat(first.left).isGreaterThan(200f)
        assertThat(state.rectOf("a")).isEqualTo(saved)

        compose.onNodeWithContentDescription("A").requestFocus().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.waitForIdle()

        assertThat(state.rectOf("a")!!.left).isGreaterThan(saved!!.left)
        assertThat(compose.onNodeWithTag("w-a").fetchSemanticsNode().boundsInRoot.left).isLessThan(first.left)
    }

    @Test
    fun `오른쪽에서 왼쪽으로 쓰면 크기 손잡이가 왼쪽 아래에 있고 왼쪽으로 끌면 넓어진다`() {
        val state = KineticaBoardState.create(catalog)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.size(400.dp, 600.dp)) {
                    KineticaBoard(state, Modifier.fillMaxSize(), editing = true) { key -> Text("widget $key", Modifier.testTag("w-$key")) }
                }
            }
        }
        val widget = compose.onNodeWithTag(BOARD_WIDGET_TAG + "a").fetchSemanticsNode().boundsInRoot
        val handle = compose.onNodeWithContentDescription("Resize A").fetchSemanticsNode().boundsInRoot
        assertThat(handle.center.x).isLessThan(widget.center.x)

        compose.onNodeWithContentDescription("Resize A").performTouchInput {
            down(center)
            moveTo(Offset(center.x - 20f, center.y))
            moveTo(Offset(center.x - 60f, center.y))
            up()
        }
        compose.waitForIdle()

        val resized = compose.onNodeWithTag(BOARD_WIDGET_TAG + "a").fetchSemanticsNode().boundsInRoot
        assertThat(resized.width).isGreaterThan(widget.width + 30f)
        assertThat(resized.right).isWithin(2f).of(widget.right)
    }

    @Test
    fun `위젯 100개를 편집할 때 드래그하는 동안 움직이지 않는 위젯 틀은 다시 그리지 않는다`() {
        val many = (0 until 100).map { BoardWidget("w$it", "W$it", widthDp = 40f, heightDp = 40f) }
        val state = KineticaBoardState.create(many)
        val composed = mutableMapOf<String, Int>()
        compose.setContent {
            CompositionLocalProvider(LocalEditFrameComposed provides { key -> composed[key] = (composed[key] ?: 0) + 1 }) {
                Box(Modifier.size(400.dp, 800.dp)) {
                    KineticaBoard(state, Modifier.fillMaxSize(), editing = true) { Box(Modifier.fillMaxSize()) }
                }
            }
        }
        val target = compose.onNodeWithTag(BOARD_WIDGET_TAG + "w50")
        val before = state.rectOf("w50")
        target.performTouchInput {
            // 왼쪽 위 삭제 버튼과 오른쪽 아래 크기 손잡이를 피해 틀 위쪽을 잡고 옮긴다.
            down(Offset(30f, 5f))
            moveBy(Offset(30f, 0f))
        }
        compose.waitForIdle()
        val settled = composed.toMap()

        repeat(5) {
            target.performTouchInput { moveBy(Offset(4f, 2f)) }
            compose.waitForIdle()
        }
        val redrawn = composed.filter { (key, count) -> key != "w50" && count != settled[key] }.keys
        target.performTouchInput { up() }
        compose.waitForIdle()

        assertThat(settled).hasSize(100)
        assertThat(redrawn).isEmpty()
        assertThat(state.rectOf("w50")).isNotEqualTo(before)
    }
}

@androidx.compose.runtime.Composable
private fun BasicBox() {
    Box(Modifier.fillMaxSize())
}
