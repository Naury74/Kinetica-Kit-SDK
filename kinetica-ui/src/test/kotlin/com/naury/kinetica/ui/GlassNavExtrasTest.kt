package com.naury.kinetica.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-mdpi")
class GlassNavExtrasTest {
    @get:Rule
    val compose = createComposeRule()

    private val detents = listOf(Detent.Peek(120.dp), Detent.Fraction(0.5f), Detent.Full)

    @Test
    fun `시트는 손을 뗀 위치와 속도로 다음 높이를 고른다`() {
        val state = GlassDetentSheetState(detents, Detent.Peek(120.dp), dismissible = false)
        state.updateLayout(1000f, 50f, Density(1f))
        assertThat(state.offset).isEqualTo(880f)

        state.dragBy(-300f)
        assertThat(state.targetFor(0f)).isEqualTo(Detent.Fraction(0.5f))
        // 위로 빠르게 튕기면 가까운 절반 높이를 지나 맨 위까지 간다.
        assertThat(state.targetFor(-2_000f)).isEqualTo(Detent.Full)
        // 조금만 끌어 올렸어도 빠르게 튕기면 한 칸은 올라간다.
        state.dragBy(280f)
        assertThat(state.targetFor(-500f)).isEqualTo(Detent.Fraction(0.5f))
        // 위아래 끝을 넘어 끌리지 않는다.
        state.dragBy(5_000f)
        assertThat(state.offset).isEqualTo(880f)
    }

    @Test
    fun `닫을 수 있는 시트만 아래로 숨길 수 있다`() {
        val fixed = GlassDetentSheetState(detents, Detent.Peek(120.dp), dismissible = false)
        val closable = GlassDetentSheetState(detents, Detent.Peek(120.dp), dismissible = true)
        fixed.updateLayout(1000f, 50f, Density(1f))
        closable.updateLayout(1000f, 50f, Density(1f))

        assertThat(fixed.dragBy(100f)).isEqualTo(0f)
        assertThat(closable.dragBy(100f)).isEqualTo(100f)
        assertThat(closable.targetFor(2_000f)).isEqualTo(Detent.Hidden)
        val error = runCatching { runBlocking { fixed.animateTo(Detent.Hidden) } }.exceptionOrNull()
        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `시트 안 목록을 위로 밀면 목록보다 시트가 먼저 펼쳐지고 TalkBack 동작으로 접힌다`() {
        lateinit var state: GlassDetentSheetState
        lateinit var list: LazyListState
        compose.setContent {
            state = rememberGlassDetentSheetState(detents)
            list = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                GlassDetentSheet(state, Modifier.testTag("sheet"), title = "장소") {
                    LazyColumn(Modifier.fillMaxSize().testTag("list"), list) {
                        items(50) { BasicText("장소 $it", Modifier.padding(16.dp)) }
                    }
                }
            }
        }

        compose.onNodeWithTag("list").performTouchInput { swipeUp(startY = bottom - 10f, endY = bottom - 200f) }
        compose.waitForIdle()

        assertThat(state.currentDetent).isNotEqualTo(Detent.Peek(120.dp))
        assertThat(list.firstVisibleItemIndex).isEqualTo(0)
        val sheet = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "장소")
        compose.onNode(sheet).performCustomAccessibilityActionWithLabel("Collapse")
        compose.waitForIdle()
        val collapsedOnce = state.currentDetent
        assertThat(collapsedOnce).isNotEqualTo(Detent.Full)
        compose.onNode(sheet).performCustomAccessibilityActionWithLabel("Expand")
        compose.waitForIdle()
        assertThat(state.currentDetent).isNotEqualTo(collapsedOnce)
    }

    @Test
    fun `상단 탭을 누르면 그 페이지로 가고 선택 상태가 따라온다`() {
        lateinit var pager: PagerState
        val tabs = listOf("채팅", "통화", "스토리").map { GlassTab(it) }
        compose.setContent {
            pager = rememberPagerState { tabs.size }
            androidx.compose.foundation.layout.Column {
                GlassTopTabs(tabs, pager)
                HorizontalPager(pager, Modifier.size(300.dp)) { BasicText("페이지 $it") }
            }
        }

        compose.onNodeWithText("스토리").performClick()
        compose.waitForIdle()

        assertThat(pager.currentPage).isEqualTo(2)
        compose.onNodeWithText("스토리").assertIsSelected()
        assertThat(compose.onNodeWithText("스토리").fetchSemanticsNode().config[SemanticsProperties.Role])
            .isEqualTo(androidx.compose.ui.semantics.Role.Tab)
    }

    @Test
    fun `툴팁은 위에 자리가 없으면 아래로 가고 화살표는 계속 요소를 가리킨다`() {
        val spacing = TooltipSpacing(margin = 12f, gap = 4f, arrowInset = 20f)
        val container = IntSize(400, 800)
        val bubble = IntSize(200, 60)

        val nearTop = placeTooltip(Rect(10f, 20f, 50f, 60f), container, bubble, GlassTooltipPlacement.Auto, spacing)
        assertThat(nearTop.above).isFalse()
        assertThat(nearTop.y).isEqualTo(64)
        assertThat(nearTop.x).isEqualTo(12)
        assertThat(nearTop.arrowX).isEqualTo(20f)

        val nearRight = placeTooltip(Rect(360f, 400f, 396f, 436f), container, bubble, GlassTooltipPlacement.Below, spacing)
        assertThat(nearRight.above).isFalse()
        assertThat(nearRight.x).isEqualTo(188)
        // 요소 가운데(378)는 말풍선 오른쪽 끝에 너무 가까워 모서리를 피할 만큼만 안쪽에 둔다.
        assertThat(nearRight.arrowX).isEqualTo(200f - 20f)

        val nearBottom = placeTooltip(Rect(100f, 760f, 140f, 790f), container, bubble, GlassTooltipPlacement.Below, spacing)
        assertThat(nearBottom.above).isTrue()
    }

    @Test
    fun `툴팁은 보이면 문구를 띄우고 바깥을 누르면 닫기를 요청한다`() {
        val visible = mutableStateOf(false)
        var dismissed = 0
        compose.setContent {
            GlassScaffold(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.align(Alignment.Center).size(48.dp).testTag("anchor")
                            .glassTooltip("여기서 필터를 바꿔요", visible.value, { dismissed++ }),
                    )
                }
            }
        }
        compose.onNodeWithText("여기서 필터를 바꿔요").assertDoesNotExist()

        visible.value = true
        compose.waitForIdle()
        compose.onNodeWithText("여기서 필터를 바꿔요").assertExists()
        val tip = compose.onNodeWithText("여기서 필터를 바꿔요").fetchSemanticsNode().boundsInRoot
        val anchor = compose.onNodeWithTag("anchor").fetchSemanticsNode().boundsInRoot
        assertThat(tip.bottom).isAtMost(anchor.top)

        compose.onNodeWithTag("anchor").performTouchInput { click(androidx.compose.ui.geometry.Offset(-150f, 300f)) }
        compose.waitForIdle()
        assertThat(dismissed).isEqualTo(1)
    }

    @Test
    fun `길게 누르면 툴팁이 뜨고 탭은 그대로 동작한다`() {
        var clicks = 0
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.align(Alignment.Center).size(48.dp).testTag("button")
                        .tooltipOnLongPress("보관함")
                        .clickable { clicks++ },
                )
            }
        }

        compose.onNodeWithTag("button").performClick()
        assertThat(clicks).isEqualTo(1)
        compose.onNodeWithText("보관함").assertDoesNotExist()

        compose.onNodeWithTag("button").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("보관함").assertExists()
        assertThat(clicks).isEqualTo(1)

        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        compose.onNodeWithText("보관함").assertDoesNotExist()
        // TalkBack은 길게 누르기 동작으로 띄운다.
        compose.onNodeWithTag("button").performSemanticsAction(SemanticsActions.OnLongClick)
        compose.waitForIdle()
        compose.onNodeWithText("보관함").assertExists()
    }

    @Test
    fun `기능 안내는 다음과 완료로 끝나고 건너뛰면 끝까지 보지 않은 것으로 알린다`() {
        val state = CoachMarkState()
        val results = mutableListOf<Boolean>()
        val steps = listOf(CoachStep("a", "검색", "여기서 찾아요"), CoachStep("b", "작성", "새 글을 써요"))
        compose.setContent {
            GlassScaffold(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.size(40.dp).coachMarkTarget(state, "a"))
                    Box(Modifier.align(Alignment.BottomCenter).size(80.dp).coachMarkTarget(state, "b"))
                }
            }
            CoachMarkHost(state, steps, onFinish = { results += it })
        }

        state.start()
        compose.waitForIdle()
        compose.onNodeWithText("검색").assertExists()
        compose.onNodeWithText("Next").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("작성").assertExists()
        compose.onNodeWithText("Skip").assertDoesNotExist()
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        assertThat(results).containsExactly(true)
        assertThat(state.isShowing).isFalse()

        state.start()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("검색. 여기서 찾아요. Step 1 of 2").performCustomAccessibilityActionWithLabel("Skip")
        compose.waitForIdle()
        assertThat(results).containsExactly(true, false).inOrder()
        compose.onNodeWithText("검색").assertDoesNotExist()
    }

    @Test
    fun `서랍은 가장자리에서 끌면 열리고 밀려난 화면을 누르면 닫힌다`() {
        lateinit var state: GlassDrawerState
        var contentClicks = 0
        compose.setContent {
            state = rememberGlassDrawerState()
            GlassDrawer(drawerContent = { BasicText("받은편지함") }, Modifier.testTag("root"), state) {
                Box(Modifier.fillMaxSize().clickable { contentClicks++ })
            }
        }
        compose.onNodeWithText("받은편지함").assertDoesNotExist()

        compose.onNodeWithTag("root").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(4f, centerY), androidx.compose.ui.geometry.Offset(300f, centerY), 200)
        }
        compose.waitForIdle()
        assertThat(state.isOpen).isTrue()
        assertThat(state.progress).isEqualTo(1f)
        compose.onNodeWithText("받은편지함").assertExists()

        compose.onNodeWithContentDescription("Close menu").performClick()
        compose.waitForIdle()
        assertThat(state.isOpen).isFalse()
        assertThat(contentClicks).isEqualTo(0)

        // 가장자리가 아닌 곳에서 끌면 열리지 않고, 화면 탭은 그대로 전달된다.
        compose.onNodeWithTag("root").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(150f, centerY), androidx.compose.ui.geometry.Offset(390f, centerY), 200)
        }
        compose.waitForIdle()
        assertThat(state.progress).isEqualTo(0f)
        contentClicks = 0
        compose.onNodeWithTag("root").performClick()
        assertThat(contentClicks).isEqualTo(1)
    }

    @Test
    fun `기능 안내 단계가 보이는 중에 비어도 닫히는 동안 그리다가 끝난다`() {
        val state = CoachMarkState()
        val steps = mutableStateOf(listOf(CoachStep("a", "검색", "여기서 찾아요")))
        compose.setContent {
            GlassScaffold(Modifier.fillMaxSize()) {
                Box(Modifier.size(40.dp).coachMarkTarget(state, "a"))
            }
            CoachMarkHost(state, steps.value)
        }
        state.start()
        compose.waitForIdle()
        compose.onNodeWithText("검색").assertExists()

        compose.mainClock.autoAdvance = false
        steps.value = emptyList()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("검색").assertExists()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText("검색").assertDoesNotExist()
    }

    @Test
    fun `스크롤 상단 탭은 처음부터 고른 페이지의 탭을 화면 안에 보인다`() {
        val tabs = (0 until 12).map { GlassTab("탭 $it") }
        compose.setContent {
            val pager = rememberPagerState(initialPage = 10) { tabs.size }
            Box(Modifier.width(300.dp)) { GlassTopTabs(tabs, pager) }
        }
        compose.waitForIdle()

        compose.onNodeWithText("탭 10").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `서랍은 시작 가장자리를 시스템 뒤로 가기 제스처에서 뺀다`() {
        lateinit var view: android.view.View
        compose.setContent {
            view = androidx.compose.ui.platform.LocalView.current
            GlassDrawer(drawerContent = { BasicText("받은편지함") }) { Box(Modifier.fillMaxSize()) }
        }
        compose.waitForIdle()

        val rects = view.systemGestureExclusionRects
        assertThat(rects).isNotEmpty()
        assertThat(rects.first().left).isEqualTo(0)
        assertThat(rects.first().height()).isAtMost(200)
    }
}
