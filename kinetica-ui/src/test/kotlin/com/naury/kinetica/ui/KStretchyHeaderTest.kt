package com.naury.kinetica.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KStretchyHeaderTest {
    @get:Rule
    val compose = createComposeRule()

    private val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    private fun setList(list: LazyListState): KStretchyHeaderState {
        lateinit var state: KStretchyHeaderState
        compose.setContent {
            state = rememberKStretchyHeaderState(list)
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                Box(Modifier.height(500.dp).fillMaxWidth()) {
                    LazyColumn(Modifier.nestedScroll(state.connection).testTag("list"), state = list) {
                        item {
                            KStretchyHeader(state, "제주 한 달 살기", Modifier.testTag("header"), subtitle = "서귀포, 4월", height = 240.dp) {
                                Box(Modifier.fillMaxSize().background(Color(0xFF2E7D6B)))
                            }
                        }
                        items(30) { Box(Modifier.fillMaxWidth().height(64.dp)) }
                    }
                    KStretchyHeaderBar(state, "제주 한 달 살기", onBack = {}, windowInsets = WindowInsets(0))
                }
            }
        }
        compose.waitForIdle()
        return state
    }

    @Test
    fun `처음에는 헤더 제목만 제목으로 읽히고 스크롤하면 바 제목으로 넘어간다`() {
        val list = LazyListState()
        val state = setList(list)
        assertThat(state.collapsedFraction).isEqualTo(0f)
        assertThat(state.isTitleInBar).isFalse()
        compose.onAllNodesWithText("제주 한 달 살기").filter(heading).assertCountEquals(1)
        compose.runOnIdle { list.dispatchRawDelta(1000f) }
        compose.waitForIdle()
        assertThat(state.collapsedFraction).isEqualTo(1f)
        assertThat(state.isTitleInBar).isTrue()
        compose.onAllNodesWithText("제주 한 달 살기").filter(heading).assertCountEquals(1)
    }

    @Test
    fun `맨 위에서 아래로 당기면 헤더가 늘어나고 손을 떼면 돌아온다`() {
        val list = LazyListState()
        val state = setList(list)
        compose.onNodeWithTag("header").assertHeightIsEqualTo(240.dp)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("list").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 300f))
            moveBy(androidx.compose.ui.geometry.Offset(0f, 200f))
        }
        compose.mainClock.advanceTimeBy(100)
        assertThat(state.stretchFraction).isGreaterThan(0f)
        compose.onNodeWithTag("list").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(2000)
        assertThat(state.stretchFraction).isEqualTo(0f)
    }

    @Test
    fun `ScrollState로도 접힘을 정한다`() {
        val scroll = ScrollState(0)
        lateinit var state: KStretchyHeaderState
        compose.setContent {
            state = rememberKStretchyHeaderState(scroll)
            Box(Modifier.height(400.dp)) {
                Column(Modifier.nestedScroll(state.connection).verticalScroll(scroll)) {
                    KStretchyHeader(state, "서울 야경", height = 200.dp) { Box(Modifier.fillMaxSize()) }
                    Box(Modifier.height(1200.dp))
                }
                KStretchyHeaderBar(state, "서울 야경", windowInsets = WindowInsets(0))
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { scroll.dispatchRawDelta(60f) }
        compose.waitForIdle()
        assertThat(state.collapsedFraction).isGreaterThan(0f)
        assertThat(state.collapsedFraction).isLessThan(1f)
    }

    @Test
    fun `높이와 패럴랙스 비율이 잘못되면 거부한다`() {
        val result = runCatching {
            compose.setContent {
                KStretchyHeader(rememberKStretchyHeaderState(rememberScrollState()), "x", parallax = 1.5f) {}
            }
        }
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `아래로 쓸어 당겼다 놓으면 헤더가 원래 높이로 돌아온다`() {
        val list = LazyListState()
        setList(list)
        compose.onNodeWithTag("list").performTouchInput { swipeDown() }
        compose.waitForIdle()
        compose.onNodeWithTag("header").assertHeightIsEqualTo(240.dp)
    }
}
