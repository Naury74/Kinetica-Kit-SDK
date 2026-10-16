package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 손을 뗀 뒤 끌어 옮기는 컴포넌트가 중간에 멈추지 않고 정지 위치로 돌아가는지 본다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DragSettleTest {
    @get:Rule
    val compose = createComposeRule()

    private fun top(tag: String): Dp = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().top

    /** 프레임마다 조금씩 움직이는 느린 끌기. 마지막에 잠시 멈췄다 놓아 속도가 거의 0이 되게 한다. */
    private fun slowDrag(tag: String, start: Offset? = null, dy: Float, dx: Float = 0f, steps: Int = 12) =
        slowDragNode(compose.onNodeWithTag(tag), start, dy, dx, steps)

    private fun slowDragNode(node: SemanticsNodeInteraction, start: Offset? = null, dy: Float = 0f, dx: Float = 0f, steps: Int = 12) {
        // 시계를 멈추면 마지막 움직임과 손 떼기가 한 번에 처리된다. 실제 기기에서 두 이벤트가 같은 프레임에 오는 경우다.
        val auto = compose.mainClock.autoAdvance
        compose.mainClock.autoAdvance = false
        node.performTouchInput {
            down(start ?: center)
            repeat(steps) { moveBy(Offset(dx / steps, dy / steps), delayMillis = 32) }
            moveBy(Offset.Zero, delayMillis = 300)
            // 실제 기기처럼 마지막 움직임과 손 떼기가 같은 프레임에 들어온다.
            moveBy(Offset(dx.coerceIn(-2f, 2f), dy.coerceIn(-2f, 2f)), delayMillis = 8)
            up()
        }
        compose.mainClock.autoAdvance = auto
    }

    @Test
    fun `시트를 반쯤 끌다 천천히 놓으면 제자리로 돌아간다`() {
        compose.setContent {
            KineticaScaffold(Modifier.fillMaxSize()) {
                KSheet(true, {}) { Box(Modifier.fillMaxWidth().height(300.dp).testTag("sheet")) }
            }
        }
        compose.waitForIdle()
        val rest = top("sheet")
        slowDrag("sheet", dy = 60f)
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertThat(top("sheet").value).isWithin(1f).of(rest.value)
    }

    @Test
    fun `시트 안 스크롤 내용으로 끌다 놓아도 제자리로 돌아간다`() {
        compose.setContent {
            KineticaScaffold(Modifier.fillMaxSize()) {
                KSheet(true, {}) {
                    Column(Modifier.fillMaxWidth().height(300.dp).testTag("sheet").verticalScroll(rememberScrollState())) {
                        repeat(30) { BasicText("줄 $it", Modifier.height(40.dp)) }
                    }
                }
            }
        }
        compose.waitForIdle()
        val rest = top("sheet")
        slowDrag("sheet", dy = 60f)
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertThat(top("sheet").value).isWithin(1f).of(rest.value)
    }

    @Test
    fun `여러 높이 시트는 느리게 놓아도 가장 가까운 높이에 멈춘다`() {
        lateinit var state: KDetentSheetState
        compose.setContent {
            state = rememberKDetentSheetState(initialDetent = Detent.Fraction(0.5f))
            Box(Modifier.fillMaxSize()) {
                KDetentSheet(state, Modifier.testTag("sheet")) { Box(Modifier.fillMaxSize().testTag("body")) }
            }
        }
        compose.waitForIdle()
        slowDrag("body", dy = -40f)
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertThat(state.isSettled).isTrue()
        assertThat(state.currentDetent).isEqualTo(Detent.Fraction(0.5f))
    }

    @Test
    fun `여러 높이 시트를 목록으로 끌다 천천히 놓아도 높이에 멈춘다`() {
        lateinit var state: KDetentSheetState
        compose.setContent {
            state = rememberKDetentSheetState(initialDetent = Detent.Full)
            Box(Modifier.fillMaxSize()) {
                KDetentSheet(state) {
                    LazyColumn(Modifier.fillMaxSize().testTag("list")) {
                        items(50) { BasicText("항목 $it", Modifier.fillMaxWidth().height(48.dp)) }
                    }
                }
            }
        }
        compose.waitForIdle()
        slowDrag("list", dy = 150f)
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertThat(state.isSettled).isTrue()
    }

    @Test
    fun `여러 높이 시트는 두 손가락으로 끌다 하나씩 떼도 높이에 멈춘다`() {
        lateinit var state: KDetentSheetState
        compose.setContent {
            state = rememberKDetentSheetState(initialDetent = Detent.Fraction(0.5f))
            Box(Modifier.fillMaxSize()) {
                KDetentSheet(state) { Box(Modifier.fillMaxSize().testTag("body")) }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("body").performTouchInput {
            down(0, center)
            repeat(6) { moveBy(0, Offset(0f, -8f), delayMillis = 32) }
            down(1, center + Offset(40f, 0f))
            repeat(4) { moveBy(1, Offset(0f, -8f), delayMillis = 32) }
            up(0)
            repeat(4) { moveBy(1, Offset(0f, -6f), delayMillis = 32) }
            moveBy(1, Offset.Zero, delayMillis = 300)
            up(1)
        }
        compose.mainClock.autoAdvance = true
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertThat(state.isSettled).isTrue()
    }

    @Test
    fun `여러 높이 시트가 움직이는 중에 눌렀다 떼도 높이에 멈춘다`() {
        lateinit var state: KDetentSheetState
        compose.setContent {
            state = rememberKDetentSheetState(initialDetent = Detent.Fraction(0.5f))
            Box(Modifier.fillMaxSize()) {
                KDetentSheet(state) {
                    LazyColumn(Modifier.fillMaxSize().testTag("list")) {
                        items(50) { BasicText("항목 $it", Modifier.fillMaxWidth().height(48.dp)) }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("list").performTouchInput { swipeUp(durationMillis = 120) }
        compose.mainClock.advanceTimeBy(48)
        // 시트가 펼쳐지는 도중에 목록을 눌러 플링을 멈춘다.
        compose.onNodeWithTag("list").performTouchInput {
            down(center)
            up()
        }
        compose.mainClock.autoAdvance = true
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertThat(state.isSettled).isTrue()
    }

    @Test
    fun `서랍을 반쯤 끌다 천천히 놓으면 열리거나 닫힌다`() {
        lateinit var state: KDrawerState
        compose.setContent {
            state = rememberKDrawerState()
            KDrawer(drawerContent = { BasicText("메뉴") }, Modifier.testTag("root"), state) { Box(Modifier.fillMaxSize()) }
        }
        compose.waitForIdle()
        slowDrag("root", start = Offset(4f, 600f), dx = 300f, dy = 0f)
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertThat(state.progress).isAnyOf(0f, 1f)
    }

    @Test
    fun `스와이프 행을 버튼 사이에서 천천히 놓으면 열리거나 닫힌 자리에 멈춘다`() {
        lateinit var state: SwipeActionsState
        compose.setContent {
            state = rememberSwipeActionsState()
            SwipeActionsRow(
                endActions = listOf(SwipeAction("Archive", Color.Gray) {}, SwipeAction("Flag", Color.Yellow) {}),
                modifier = Modifier.width(360.dp).testTag("row"),
                fullSwipe = false,
                state = state,
            ) { BasicText("메일", Modifier.fillMaxWidth().height(72.dp).background(Color.White)) }
        }
        compose.waitForIdle()
        slowDrag("row", dx = -90f, dy = 0f)
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        val reveal = with(compose.density) { 2 * 76.dp.toPx() }
        assertThat(state.offset.value).isAnyOf(0f, -reveal)
    }

    @Test
    fun `카드를 조금 끌다 천천히 놓으면 제자리로 돌아온다`() {
        lateinit var state: SwipeCardStackState
        compose.setContent {
            state = rememberSwipeCardStackState()
            SwipeCardStack(listOf("가", "나"), { _, _ -> }, Modifier.size(300.dp, 400.dp).testTag("stack"), state) {
                BasicText(it, Modifier.fillMaxSize())
            }
        }
        compose.waitForIdle()
        slowDrag("stack", dx = 50f, dy = 20f)
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertThat(state.offset.value).isEqualTo(Offset.Zero)
    }

    @Test
    fun `당겨서 새로 고침을 조금 당기다 천천히 놓으면 제자리로 돌아온다`() {
        compose.setContent {
            KRefreshBox(false, {}, Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize().testTag("list")) {
                    items(30) { BasicText("항목 $it", Modifier.fillMaxWidth().height(48.dp).testTag("row$it")) }
                }
            }
        }
        compose.waitForIdle()
        val rest = top("row0")
        slowDrag("list", dy = 80f)
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertThat(top("row0").value).isWithin(1f).of(rest.value)
    }

    @Test
    fun `스낵바를 옆으로 조금 끌다 천천히 놓으면 제자리로 돌아온다`() {
        val snackbar = KSnackbarState()
        // 시계를 자동으로 흘리면 표시 시간까지 건너뛰어 스낵바가 닫힌다.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            KineticaScaffold(Modifier.fillMaxSize()) { KSnackbarHost(snackbar) }
            LaunchedEffect(Unit) { snackbar.show("저장했어요", durationMs = 60_000) }
        }
        compose.mainClock.advanceTimeBy(1_000)
        val rest = compose.onNodeWithText("저장했어요", useUnmergedTree = true).getUnclippedBoundsInRoot().left
        compose.onNodeWithText("저장했어요", useUnmergedTree = true).assertExists()
        slowDragNode(compose.onNodeWithText("저장했어요", useUnmergedTree = true), dx = 40f)
        compose.mainClock.advanceTimeBy(2_000)
        assertThat(
            compose.onNodeWithText("저장했어요", useUnmergedTree = true).getUnclippedBoundsInRoot().left.value,
        ).isWithin(1f).of(rest.value)
    }
}
