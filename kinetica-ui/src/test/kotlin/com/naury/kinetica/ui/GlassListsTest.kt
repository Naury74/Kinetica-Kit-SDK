package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GlassListsTest {
    @get:Rule
    val compose = createComposeRule()

    private val archived = mutableIntStateOf(0)
    private val deleted = mutableIntStateOf(0)
    private val pinned = mutableIntStateOf(0)

    private fun swipeRow(rtl: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                SwipeActionsRow(
                    startActions = listOf(SwipeAction("Pin", Color.Blue) { pinned.intValue++ }),
                    endActions = listOf(
                        SwipeAction("Delete", Color.Red, removesRow = true) { deleted.intValue++ },
                        SwipeAction("Archive", Color.Gray) { archived.intValue++ },
                    ),
                    modifier = Modifier.width(360.dp).testTag("row"),
                ) { BasicText("메일", Modifier.fillMaxWidth().height(72.dp).background(Color.White)) }
            }
        }
    }

    @Test
    fun `행을 조금 밀면 버튼이 드러나고 누르면 실행된다`() {
        swipeRow()
        compose.onNodeWithTag("row").performTouchInput { swipeLeft(startX = centerX, endX = centerX - 120f, durationMillis = 300) }
        compose.waitForIdle()
        compose.onNodeWithText("Archive").performClick()
        compose.waitForIdle()
        assertThat(archived.intValue).isEqualTo(1)
        assertThat(deleted.intValue).isEqualTo(0)
    }

    @Test
    fun `끝까지 밀면 가장 바깥 동작이 실행되고 삭제 동작은 행을 줄인 뒤 부른다`() {
        swipeRow()
        compose.onNodeWithTag("row").performTouchInput { swipeLeft(startX = right - 10f, endX = left + 10f, durationMillis = 400) }
        compose.waitForIdle()
        assertThat(deleted.intValue).isEqualTo(1)
        assertThat(archived.intValue).isEqualTo(0)
    }

    @Test
    fun `끝까지 민 뒤 애니메이션 중에 다시 끌어도 동작은 한 번 실행된다`() {
        swipeRow()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("row").performTouchInput { swipeLeft(startX = right - 10f, endX = left + 10f, durationMillis = 400) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("row").performTouchInput { swipeRight(startX = centerX - 100f, endX = centerX + 60f, durationMillis = 100) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertThat(deleted.intValue).isEqualTo(1)
        assertThat(pinned.intValue).isEqualTo(0)
    }

    @Test
    fun `RTL에서는 오른쪽으로 밀어야 끝 쪽 동작이 드러난다`() {
        swipeRow(rtl = true)
        compose.onNodeWithTag("row").performTouchInput { swipeRight(startX = left + 10f, endX = right - 10f, durationMillis = 400) }
        compose.waitForIdle()
        assertThat(deleted.intValue).isEqualTo(1)
        assertThat(pinned.intValue).isEqualTo(0)
    }

    @Test
    fun `TalkBack 사용자 지정 동작으로 모든 동작을 실행할 수 있다`() {
        swipeRow()
        compose.onNodeWithTag("row").performCustomAccessibilityActionWithLabel("Pin")
        compose.onNodeWithTag("row").performCustomAccessibilityActionWithLabel("Archive")
        compose.waitForIdle()
        assertThat(pinned.intValue).isEqualTo(1)
        assertThat(archived.intValue).isEqualTo(1)
    }

    @Test
    fun `같은 그룹에서 다른 행을 열면 열려 있던 행이 닫힌다`() {
        lateinit var first: SwipeActionsState
        lateinit var second: SwipeActionsState
        compose.setContent {
            val group = rememberSwipeActionsGroup()
            first = rememberSwipeActionsState(group)
            second = rememberSwipeActionsState(group)
            androidx.compose.foundation.layout.Column {
                listOf(first to "a", second to "b").forEach { (state, tag) ->
                    SwipeActionsRow(
                        endActions = listOf(SwipeAction("Archive", Color.Gray) {}),
                        modifier = Modifier.width(360.dp).testTag(tag),
                        fullSwipe = false,
                        state = state,
                    ) { BasicText(tag, Modifier.fillMaxWidth().height(64.dp).background(Color.White)) }
                }
            }
        }
        compose.onNodeWithTag("a").performTouchInput { swipeLeft(startX = centerX, endX = centerX - 150f, durationMillis = 300) }
        compose.waitForIdle()
        assertThat(first.isOpen).isTrue()
        compose.onNodeWithTag("b").performTouchInput { swipeLeft(startX = centerX, endX = centerX - 150f, durationMillis = 300) }
        compose.waitForIdle()
        assertThat(second.isOpen).isTrue()
        assertThat(first.isOpen).isFalse()
    }

    private val tasks = mutableStateListOf("A", "B", "C", "D", "E")

    private fun reorderList() {
        compose.setContent {
            val listState = rememberLazyListState()
            val reorder = rememberReorderableListState(listState) { from, to -> tasks.add(to, tasks.removeAt(from)) }
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(tasks, key = { it }) { task ->
                    ReorderableItem(reorder, task, Modifier.testTag(task)) {
                        BasicText(task, Modifier.fillMaxWidth().height(60.dp).background(Color.White))
                    }
                }
            }
        }
    }

    @Test
    fun `길게 눌러 끌면 순서가 바뀐다`() {
        reorderList()
        compose.onNodeWithTag("A").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            repeat(20) { moveBy(androidx.compose.ui.geometry.Offset(0f, 8.dp.toPx())) }
            up()
        }
        compose.waitForIdle()
        assertThat(tasks.indexOf("A")).isGreaterThan(1)
    }

    @Test
    fun `위로 이동과 아래로 이동 동작으로 순서를 바꾼다`() {
        reorderList()
        compose.onNodeWithTag("A").performCustomAccessibilityActionWithLabel("Move down")
        compose.waitForIdle()
        assertThat(tasks.toList()).containsExactly("B", "A", "C", "D", "E").inOrder()
        compose.onNodeWithTag("D").performCustomAccessibilityActionWithLabel("Move up")
        compose.waitForIdle()
        assertThat(tasks.toList()).containsExactly("B", "A", "D", "C", "E").inOrder()
    }

    @Test
    fun `아코디언은 머리글을 누르면 펼쳐지고 상태와 동작을 알린다`() {
        val open = mutableStateOf(false)
        compose.setContent {
            GlassExpandable(open.value, { open.value = it }, header = { BasicText("배송", Modifier.testTag("header")) }) {
                BasicText("내일 도착")
            }
        }
        compose.onNodeWithText("내일 도착").assertDoesNotExist()
        compose.onNodeWithText("배송").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        compose.onNodeWithText("배송").performClick()
        compose.waitForIdle()
        assertThat(open.value).isTrue()
        compose.onNodeWithText("내일 도착").assertExists()
        compose.onNodeWithText("배송")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
            .performSemanticsAction(SemanticsActions.Collapse)
        assertThat(open.value).isFalse()
    }

    @Test
    fun `그룹에서 한 항목만 펼치면 다른 항목은 접힌다`() {
        lateinit var state: GlassExpandableGroupState
        compose.setContent {
            state = rememberGlassExpandableGroupState(singleOpen = true, initiallyExpanded = listOf("a"))
            GlassExpandableGroup(state = state) {
                Item("a", header = { BasicText("A") }) { BasicText("내용 A") }
                Item("b", header = { BasicText("B") }) { BasicText("내용 B") }
            }
        }
        compose.onNodeWithText("내용 A").assertExists()
        compose.onNodeWithText("B").performClick()
        compose.waitForIdle()
        assertThat(state.expandedKeys).containsExactly("b")
        compose.onNodeWithText("내용 A").assertDoesNotExist()
        compose.onNodeWithText("내용 B").assertExists()
    }

    @Test
    fun `밀어서 확인은 끝까지 밀어야 확정되고 중간에 놓으면 돌아온다`() {
        val confirms = mutableIntStateOf(0)
        compose.setContent {
            Box(Modifier.width(320.dp)) {
                GlassSlideToConfirm("밀어서 결제", { confirms.intValue++ }, Modifier.testTag("slide"))
            }
        }
        compose.onNodeWithTag("slide").performTouchInput { swipeRight(startX = left + 30f, endX = centerX, durationMillis = 300) }
        compose.waitForIdle()
        assertThat(confirms.intValue).isEqualTo(0)
        compose.onNodeWithTag("slide").performTouchInput { swipeRight(startX = left + 30f, endX = right + 40f, durationMillis = 400) }
        compose.waitForIdle()
        assertThat(confirms.intValue).isEqualTo(1)
        compose.onNodeWithTag("slide").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Confirmed"))
    }

    @Test
    fun `TalkBack에서는 버튼처럼 눌러 확정하고 확정을 풀면 다시 밀 수 있다`() {
        val confirmed = mutableStateOf(false)
        compose.setContent {
            GlassSlideToConfirm("밀어서 결제", { confirmed.value = true }, Modifier.testTag("slide"), confirmed = confirmed.value)
        }
        compose.onNodeWithTag("slide")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Button))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertThat(confirmed.value).isTrue()
        compose.onNodeWithTag("slide").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
        confirmed.value = false
        compose.waitForIdle()
        compose.onNodeWithTag("slide").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Disabled))
    }

    @Test
    fun `밀어서 확인 상태는 reset으로 되돌리고 확정은 다시 만들어도 유지된다`() {
        val restore = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        val confirms = mutableIntStateOf(0)
        lateinit var state: GlassSlideToConfirmState
        restore.setContent {
            state = rememberGlassSlideToConfirmState()
            GlassSlideToConfirm("밀어서 결제", state, { confirms.intValue++ }, Modifier.testTag("slide"))
        }
        compose.onNodeWithTag("slide").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertThat(state.isConfirmed).isTrue()
        assertThat(confirms.intValue).isEqualTo(1)

        restore.emulateSavedInstanceStateRestore()
        assertThat(state.isConfirmed).isTrue()
        compose.onNodeWithTag("slide").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))

        // 확정 직후 같은 프레임 안에서 되돌려도 다시 밀 수 있다.
        compose.runOnIdle { state.reset() }
        compose.onNodeWithTag("slide").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Disabled))
        compose.onNodeWithTag("slide").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertThat(confirms.intValue).isEqualTo(2)
    }

    @Test
    fun `confirmed 값으로 확정한 밀어서 확인은 다시 만들어도 확정을 유지한다`() {
        val restore = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restore.setContent { GlassSlideToConfirm("밀어서 결제", {}, Modifier.testTag("slide")) }
        compose.onNodeWithTag("slide").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        restore.emulateSavedInstanceStateRestore()

        compose.onNodeWithTag("slide").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Confirmed"))
    }
}
