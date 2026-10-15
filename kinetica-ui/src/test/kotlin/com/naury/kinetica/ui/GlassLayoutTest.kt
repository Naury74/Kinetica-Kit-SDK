package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GlassLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `큰 제목은 목록을 스크롤하면 접히고 뒤로 버튼과 제목 heading이 있다`() {
        lateinit var state: GlassLargeTitleState
        var back = 0
        compose.setContent {
            state = rememberGlassLargeTitleState()
            GlassScaffold(Modifier.fillMaxSize(), topBar = { GlassLargeTitleBar(state, "설정", onBack = { back++ }) }) { padding ->
                LazyColumn(Modifier.fillMaxSize().nestedScroll(state.connection).testTag("list"), contentPadding = padding) {
                    items(60) { BasicText("항목 $it", Modifier.fillMaxWidth().height(48.dp)) }
                }
            }
        }
        assertThat(state.collapsedFraction).isEqualTo(0f)

        compose.onNodeWithTag("list").performTouchInput { swipeUp() }
        compose.waitForIdle()

        assertThat(state.collapsedFraction).isEqualTo(1f)
        compose.onNodeWithContentDescription("Back").performClick()
        assertThat(back).isEqualTo(1)
        compose.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading),
        ).assert(SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(androidx.compose.ui.text.AnnotatedString("설정"))))
    }

    @Test
    fun `페이지 인디케이터는 현재 페이지를 읽어 주고 값 조절로 페이지를 고른다`() {
        var selected = -1
        compose.setContent {
            GlassPageIndicator(5, 1, 0.3f, Modifier.testTag("dots"), onSelect = { selected = it })
        }

        compose.onNodeWithTag("dots").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Page 2 of 5"))
        compose.onNodeWithTag("dots").performSemanticsAction(SemanticsActions.SetProgress) { it(3f) }

        assertThat(selected).isEqualTo(3)
    }

    @Test
    fun `캐러셀은 밀어서 다음 페이지로 넘어간다`() {
        lateinit var pager: PagerState
        compose.setContent {
            pager = rememberPagerState { 4 }
            Box(Modifier.size(360.dp, 240.dp)) {
                GlassCarousel(4, Modifier.fillMaxSize().testTag("carousel"), state = pager) { page -> BasicText("카드 $page") }
                GlassPageIndicator(pager, Modifier.align(Alignment.BottomCenter))
            }
        }
        compose.onNodeWithText("카드 0").assertExists()

        compose.onNodeWithTag("carousel").performTouchInput { swipeLeft() }
        compose.waitForIdle()

        assertThat(pager.currentPage).isGreaterThan(0)
    }

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test
    fun `카드 더미는 끌거나 TalkBack 동작으로 넘기고 되돌린다`() {
        val swiped = mutableListOf<Pair<String, SwipeDirection>>()
        lateinit var state: SwipeCardStackState
        compose.setContent {
            state = rememberSwipeCardStackState()
            SwipeCardStack(
                listOf("가", "나", "다"),
                onSwipe = { item, direction -> swiped += item to direction },
                modifier = Modifier.size(300.dp, 400.dp).testTag("stack"),
                state = state,
            ) { item -> BasicText(item, Modifier.fillMaxSize().testTag("card-$item")) }
        }

        compose.onNodeWithTag("stack").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertThat(swiped).containsExactly("가" to SwipeDirection.Right)
        assertThat(state.swipedCount).isEqualTo(1)

        compose.onNodeWithTag("card-나", useUnmergedTree = true).assertExists()
        compose.onNode(hasCustomAction("Swipe left"))
            .performCustomAccessibilityActionWithLabel("Swipe left")
        compose.waitForIdle()
        assertThat(swiped.last()).isEqualTo("나" to SwipeDirection.Left)

        compose.onNode(hasCustomAction("Undo last swipe")).performCustomAccessibilityActionWithLabel("Undo last swipe")
        compose.waitForIdle()
        assertThat(state.swipedCount).isEqualTo(1)
        assertThat(swiped).hasSize(2)
    }

    @Test
    fun `스피드 다이얼은 눌러 펼치고 항목을 누르면 접은 뒤 동작을 실행한다`() {
        val expanded = mutableStateOf(false)
        var shared = 0
        compose.setContent {
            GlassScaffold(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    GlassSpeedDial(
                        expanded.value,
                        { expanded.value = it },
                        listOf(GlassMenuItem("공유") { shared++ }, GlassMenuItem("삭제", destructive = true) {}),
                        Modifier.align(Alignment.BottomEnd).padding(24.dp),
                    )
                }
            }
        }
        compose.onNodeWithText("공유").assertDoesNotExist()
        compose.onNodeWithContentDescription("Quick actions")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
            .performClick()
        compose.waitForIdle()
        assertThat(expanded.value).isTrue()

        compose.onNodeWithText("공유").performClick()
        compose.waitForIdle()

        assertThat(shared).isEqualTo(1)
        assertThat(expanded.value).isFalse()
        compose.onNodeWithText("공유").assertDoesNotExist()
    }

    @Test
    fun `목록 상태를 받은 큰 제목은 코드로 스크롤해도 접힘이 맞는다`() {
        lateinit var state: GlassLargeTitleState
        val list = androidx.compose.foundation.lazy.LazyListState()
        compose.setContent {
            state = rememberGlassLargeTitleState(list)
            GlassScaffold(Modifier.fillMaxSize(), topBar = { GlassLargeTitleBar(state, "설정") }) { padding ->
                LazyColumn(Modifier.fillMaxSize().nestedScroll(state.connection), state = list, contentPadding = padding) {
                    items(60) { BasicText("항목 $it", Modifier.fillMaxWidth().height(48.dp)) }
                }
            }
        }
        assertThat(state.collapsedFraction).isEqualTo(0f)

        compose.runOnIdle { kotlinx.coroutines.runBlocking { list.scrollToItem(20) } }
        compose.waitForIdle()
        assertThat(state.collapsedFraction).isEqualTo(1f)

        compose.runOnIdle { kotlinx.coroutines.runBlocking { list.scrollToItem(0) } }
        compose.waitForIdle()
        assertThat(state.collapsedFraction).isEqualTo(0f)
    }

    @Test
    fun `카드 더미에서 넘긴 카드를 지워도 다음 카드를 건너뛰지 않는다`() {
        val items = androidx.compose.runtime.mutableStateListOf("가", "나", "다")
        lateinit var state: SwipeCardStackState
        compose.setContent {
            state = rememberSwipeCardStackState()
            SwipeCardStack(
                items,
                onSwipe = { item, _ -> items.remove(item) },
                modifier = Modifier.size(300.dp, 400.dp).testTag("stack"),
                state = state,
            ) { item -> BasicText(item, Modifier.fillMaxSize().testTag("card-$item")) }
        }

        compose.onNodeWithTag("stack").performTouchInput { swipeRight() }
        compose.waitForIdle()

        assertThat(items).containsExactly("나", "다").inOrder()
        assertThat(state.swipedCount).isEqualTo(0)
        compose.onNodeWithTag("card-나", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("card-가", useUnmergedTree = true).assertDoesNotExist()

        // 지운 카드는 되돌릴 수 없으므로 기록만 지운다.
        compose.runOnIdle { assertThat(kotlinx.coroutines.runBlocking { state.undo() }).isFalse() }
        assertThat(state.canUndo).isFalse()
    }

    @Test
    fun `스피드 다이얼은 항목이 많아도 출발 지연의 합이 진행도를 넘지 않는다`() {
        assertThat(speedDialStagger(1)).isEqualTo(0f)
        assertThat(speedDialStagger(3)).isEqualTo(0.07f)
        for (count in 2..40) {
            assertThat(speedDialStagger(count) * (count - 1)).isAtMost(0.5f + 1e-6f)
        }
    }

    private fun hasCustomAction(label: String) = SemanticsMatcher("동작 $label") { node ->
        node.config.getOrElseNullable(SemanticsActions.CustomActions) { null }?.any { it.label == label } == true
    }
}
