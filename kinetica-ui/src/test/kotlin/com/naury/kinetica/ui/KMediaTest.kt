package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
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
class KMediaTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `재생 아이콘은 가운데가 채워진 삼각형에서 가운데가 빈 두 막대로 바뀐다`() {
        val playing = mutableStateOf(false)
        compose.setContent {
            Box(Modifier.background(Color.White)) {
                PlayPauseIcon(playing.value, Modifier.size(48.dp).testTag("icon"), color = Color.Black)
                MenuCloseIcon(playing.value)
                PlusCloseIcon(playing.value)
            }
        }

        fun centerIsInk(): Boolean {
            val pixels = compose.onNodeWithTag("icon").captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 2].red < 0.5f
        }
        assertThat(centerIsInk()).isTrue()

        playing.value = true
        compose.waitForIdle()
        assertThat(centerIsInk()).isFalse()
    }

    @Test
    fun `별점은 누른 별까지 고르고 반 별을 허용하며 TalkBack 값 조절로도 바꾼다`() {
        val rating = mutableFloatStateOf(1f)
        compose.setContent {
            KRating(rating.floatValue, { rating.floatValue = it }, Modifier.testTag("rating"), allowHalf = true, starSize = 40.dp)
        }
        val node = compose.onNodeWithTag("rating")
        // 별 40dp, 간격 5dp. 세 번째 별의 오른쪽 절반을 누른다.
        node.performTouchInput { click(percentOffset(0f, 0.5f).copy(x = (45 * 2 + 30).dp.toPx())) }
        assertThat(rating.floatValue).isEqualTo(3f)
        node.performTouchInput { click(percentOffset(0f, 0.5f).copy(x = (45 * 3 + 10).dp.toPx())) }
        assertThat(rating.floatValue).isEqualTo(3.5f)

        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3.5 of 5 stars"))
        node.performSemanticsAction(SemanticsActions.SetProgress) { it(1.2f) }
        assertThat(rating.floatValue).isEqualTo(1f)
    }

    @Test
    fun `별점은 손을 뗄 때 고르고 세로로 끌면 부모 스크롤에 넘긴다`() {
        val rating = mutableFloatStateOf(1f)
        val scroll = androidx.compose.foundation.ScrollState(0)
        compose.setContent {
            androidx.compose.foundation.layout.Column(Modifier.size(300.dp).verticalScroll(scroll)) {
                KRating(rating.floatValue, { rating.floatValue = it }, Modifier.testTag("rating"), starSize = 40.dp)
                Box(Modifier.size(300.dp, 1000.dp))
            }
        }
        val node = compose.onNodeWithTag("rating")

        node.performTouchInput {
            down(percentOffset(0f, 0.5f).copy(x = (45 * 3 + 20).dp.toPx()))
            moveBy(androidx.compose.ui.geometry.Offset(0f, -200f))
            up()
        }
        assertThat(rating.floatValue).isEqualTo(1f)
        assertThat(scroll.value).isGreaterThan(0)

        compose.runOnIdle { kotlinx.coroutines.runBlocking { scroll.scrollTo(0) } }
        node.performTouchInput { down(percentOffset(0f, 0.5f).copy(x = (45 * 3 + 20).dp.toPx())) }
        assertThat(rating.floatValue).isEqualTo(1f)
        node.performTouchInput { up() }
        assertThat(rating.floatValue).isEqualTo(4f)
    }

    @Test
    fun `읽기 전용 별점은 입력을 받지 않는다`() {
        compose.setContent { KRating(2f, null, Modifier.testTag("rating")) }

        compose.onNodeWithTag("rating")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetProgress))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "2 of 5 stars"))
    }

    @Test
    fun `좋아요 버튼은 누르면 상태를 바꾸고 상태와 숫자를 알린다`() {
        val liked = mutableStateOf(false)
        compose.setContent {
            KineticaScaffold { LikeButton(liked.value, { liked.value = it }, Modifier.testTag("like"), count = 1234) }
        }

        compose.onNodeWithTag("like")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not liked"))
            .performClick()
        assertThat(liked.value).isTrue()
        compose.onNodeWithTag("like")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Liked"))
        compose.onNodeWithContentDescription("1,234", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `아바타 묶음은 보이는 이름과 남은 인원을 한 번에 읽는다`() {
        compose.setContent {
            AvatarStack(listOf(KAvatarData("Ana Kim"), KAvatarData("Ben"), KAvatarData("김민수"), KAvatarData("Dan")), max = 2)
        }

        compose.onNodeWithContentDescription("Ana Kim, Ben and 2 others").assertExists()
        assertThat(initialsOf("Ana Kim")).isEqualTo("AK")
        assertThat(initialsOf("김민수")).isEqualTo("김")
        assertThat(initialsOf("  ")).isEmpty()
    }

    @Test
    fun `스켈레톤이 보이는 동안 내용은 숨고 불러오는 중으로 읽힌다`() {
        val loading = mutableStateOf(true)
        compose.setContent {
            Box {
                BasicText("제목", Modifier.skeleton(loading.value))
                KSkeletonText(3)
            }
        }

        compose.onNodeWithText("제목").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Loading").assertCountEquals(2)

        loading.value = false
        compose.waitForIdle()
        compose.onNodeWithText("제목").assertExists()
    }

    @Test
    fun `미니 플레이어는 누르면 펼치고 닫기 동작으로 접으며 위로 끌어도 펼친다`() {
        val state = KMiniPlayerState(false)
        compose.setContent {
            KineticaScaffold(Modifier.fillMaxSize()) {
                KMiniPlayer(
                    state,
                    artwork = { Box(Modifier.fillMaxSize().background(Color.Red)) },
                    collapsed = { BasicText("막대", Modifier.testTag("bar")) },
                    expanded = { BasicText("전체 화면") },
                )
            }
        }

        compose.onNodeWithText("막대").performClick()
        compose.waitForIdle()
        assertThat(state.isExpanded).isTrue()
        assertThat(state.progress).isWithin(0.01f).of(1f)
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Now playing"))
            .performSemanticsAction(SemanticsActions.Dismiss)
        compose.waitForIdle()
        assertThat(state.isExpanded).isFalse()
        assertThat(state.progress).isWithin(0.01f).of(0f)

        compose.onNodeWithTag("bar", useUnmergedTree = true).performTouchInput {
            swipe(center, center.copy(y = center.y - 300f), durationMillis = 150)
        }
        compose.waitForIdle()
        assertThat(state.isExpanded).isTrue()
    }

    @Test
    fun `미니 플레이어 슬롯은 펼치는 동안 매 프레임 다시 구성되지 않는다`() {
        val state = KMiniPlayerState(false)
        var compositions = 0
        var lastProgress = 0f
        compose.setContent {
            KineticaScaffold(Modifier.fillMaxSize()) {
                KMiniPlayer(
                    state,
                    collapsed = { BasicText("막대") },
                    expanded = { progress ->
                        androidx.compose.runtime.SideEffect { compositions++ }
                        BasicText("전체 화면", Modifier.drawBehind { lastProgress = progress() })
                    },
                )
            }
        }
        compose.mainClock.autoAdvance = false

        compose.onNodeWithText("막대").performClick()
        repeat(30) { compose.mainClock.advanceTimeByFrame() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertThat(state.progress).isWithin(0.01f).of(1f)
        compose.onRoot().captureToImage()
        assertThat(lastProgress).isWithin(0.01f).of(1f)
        assertThat(compositions).isAtMost(2)
    }

    @Test
    fun `미니 플레이어 상태는 펼침 여부를 복원한다`() {
        val restore = StateRestorationTester(compose)
        var state: KMiniPlayerState? = null
        restore.setContent { state = rememberKMiniPlayerState(initiallyExpanded = true) }

        state = null
        restore.emulateSavedInstanceStateRestore()

        assertThat(state?.isExpanded).isTrue()
        assertThat(state?.progress).isEqualTo(1f)
    }
}
