package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
class MotionKitTest {
    @get:Rule
    val compose = createComposeRule()

    private fun inkOf(tag: String): Int {
        val white = Color.White.toArgb()
        return compose.onNodeWithTag(tag).captureToImage().toPixelMap().buffer.count { it != white }
    }

    @Test
    fun `글이 앞부분을 유지한 채 늘어나면 새 단어만 이어서 나타난다`() {
        val plan = RevealSchedule()
        plan.update("안녕하세요 반가", now = 0L, delayMs = 100L)
        assertThat(plan.progress(1, now = 100L, durationMs = 400)).isEqualTo(0f)
        plan.update("안녕하세요 반가워요 오늘은", now = 1_000L, delayMs = 100L)
        // 이미 시작한 두 단어의 시각은 그대로고, 새 단어는 지금부터 차례로 시작한다.
        assertThat(plan.progress(0, now = 400L, durationMs = 400)).isEqualTo(1f)
        assertThat(plan.progress(1, now = 500L, durationMs = 400)).isEqualTo(1f)
        assertThat(plan.progress(2, now = 1_000L, durationMs = 400)).isEqualTo(0f)
        assertThat(plan.progress(2, now = 1_200L, durationMs = 400)).isEqualTo(0.5f)
        assertThat(plan.finishAt(400)).isEqualTo(1_400L)
        plan.update("다른 글", now = 2_000L, delayMs = 100L)
        assertThat(plan.progress(0, now = 2_000L, durationMs = 400)).isEqualTo(0f)
    }

    @Test
    fun `흐림 글은 처음에 보이지 않다가 나타나고 TalkBack은 처음부터 전체 글을 읽는다`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.size(240.dp, 80.dp).background(Color.White).testTag("box")) {
                KBlurTextReveal(
                    "하나 둘 셋",
                    style = androidx.compose.ui.text.TextStyle(color = Color.Black),
                    modifier = Modifier.testTag("text"),
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(
            "text",
        ).assert(SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(androidx.compose.ui.text.AnnotatedString("하나 둘 셋"))))
        assertThat(inkOf("box")).isEqualTo(0)
        compose.mainClock.advanceTimeBy(2_000)
        assertThat(inkOf("box")).isGreaterThan(0)
    }

    @Test
    fun `애니메이션 삭제면 흐림 글이 바로 다 보인다`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalKineticaReducedMotion provides true) {
                Box(Modifier.size(240.dp, 80.dp).background(Color.White).testTag("box")) {
                    KBlurTextReveal("하나 둘 셋", style = androidx.compose.ui.text.TextStyle(color = Color.Black))
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        assertThat(inkOf("box")).isGreaterThan(0)
    }

    @Test
    fun `거품을 누르면 선택이 바뀌고 TalkBack에는 체크박스로 읽힌다`() {
        var picked by mutableStateOf(setOf("재즈"))
        compose.setContent {
            KBubblePicker(listOf("팝", "재즈", "록"), picked, { picked = it }, Modifier.width(360.dp))
        }
        compose.onNodeWithText("재즈")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        compose.onNodeWithText("팝").performClick()
        assertThat(picked).containsExactly("재즈", "팝")
        compose.onNodeWithText("재즈").performClick()
        assertThat(picked).containsExactly("팝")
    }

    @Test
    fun `애니메이션 삭제면 거품이 처음부터 겹치지 않게 정착해 있다`() {
        compose.mainClock.autoAdvance = false
        val labels = listOf("팝", "재즈", "록", "클래식", "힙합", "R&B", "인디")
        compose.setContent {
            CompositionLocalProvider(LocalKineticaReducedMotion provides true) {
                KBubblePicker(labels, setOf("록"), {}, Modifier.width(360.dp), bubbleSize = 80.dp)
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val rects = labels.map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot }
        for (i in rects.indices) {
            for (j in i + 1 until rects.size) {
                val distance = (rects[i].center - rects[j].center).getDistance()
                assertThat(distance).isAtLeast((rects[i].width + rects[j].width) / 2f * 0.95f)
            }
        }
    }

    @Test
    fun `거품 물리는 정착하면 멈춘다`() {
        val physics = BubblePhysics()
        physics.resize(6, 40f)
        physics.bounds(400f, 300f)
        physics.target(2, 56f)
        physics.settle()
        assertThat(physics.step(STEP_SECONDS)).isFalse()
        assertThat(physics.radius[2]).isWithin(0.5f).of(56f)
    }

    @Test
    fun `마키는 내용을 한 번만 구성하고 흐르며 애니메이션 삭제면 멈춘다`() {
        var reduced by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalKineticaReducedMotion provides reduced) {
                KMarquee(Modifier.width(300.dp), speed = 100.dp) { BasicText("흐르는 글", Modifier.width(120.dp)) }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        assertThat(compose.onAllNodesWithText("흐르는 글").fetchSemanticsNodes()).hasSize(1)
        val start = compose.onNodeWithText("흐르는 글").fetchSemanticsNode().boundsInRoot.right
        compose.mainClock.advanceTimeBy(500)
        val moved = compose.onNodeWithText("흐르는 글").fetchSemanticsNode().boundsInRoot.right
        assertThat(moved).isLessThan(start)
        reduced = true
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)
        val stopped = compose.onNodeWithText("흐르는 글").fetchSemanticsNode().boundsInRoot.right
        compose.mainClock.advanceTimeBy(500)
        assertThat(compose.onNodeWithText("흐르는 글").fetchSemanticsNode().boundsInRoot.right).isEqualTo(stopped)
    }

    @Test
    fun `마키는 누르고 있는 동안 멈춘다`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            KMarquee(Modifier.width(300.dp).testTag("marquee"), speed = 100.dp, pauseOnTouch = true) {
                BasicText("흐르는 글", Modifier.width(120.dp))
            }
        }
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithTag("marquee").performTouchInput { down(center) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)
        val held = compose.onNodeWithText("흐르는 글").fetchSemanticsNode().boundsInRoot.right
        compose.mainClock.advanceTimeBy(500)
        assertThat(compose.onNodeWithText("흐르는 글").fetchSemanticsNode().boundsInRoot.right).isEqualTo(held)
        compose.onNodeWithTag("marquee").performTouchInput { up() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        assertThat(compose.onNodeWithText("흐르는 글").fetchSemanticsNode().boundsInRoot.right).isNotEqualTo(held)
    }

    @Test
    fun `빛줄기는 등록한 두 요소 사이에 곡선을 그린다`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalKineticaReducedMotion provides true) {
                Box(Modifier.size(300.dp, 100.dp).background(Color.White).testTag("box")) {
                    KAnimatedBeam(listOf(KBeam("a", "b")), Modifier.fillMaxWidth().height(100.dp), color = Color.Red, width = 4.dp) {
                        Box(Modifier.align(Alignment.CenterStart).size(20.dp).beamAnchor("a"))
                        Box(Modifier.align(Alignment.CenterEnd).size(20.dp).beamAnchor("b"))
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        val pixels = compose.onNodeWithTag("box").captureToImage().toPixelMap()
        assertThat(pixels[pixels.width / 2, pixels.height / 2]).isNotEqualTo(Color.White)
        assertThat(pixels[pixels.width / 2, 4]).isEqualTo(Color.White)
    }

    @Test
    fun `독 아이콘은 버튼으로 읽히고 실행 중 상태를 알린다`() {
        var opened = ""
        compose.setContent {
            KDock(
                listOf(
                    KDockItem("메일", running = true, onClick = { opened = "메일" }) { Box(Modifier.background(Color.Blue)) },
                    KDockItem("사진", onClick = { opened = "사진" }) { Box(Modifier.background(Color.Green)) },
                ),
            )
        }
        compose.onNodeWithContentDescription("메일")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher("실행 중") { it.config.getOrNull(SemanticsProperties.StateDescription) != null })
        compose.onNodeWithContentDescription("사진")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
            .performClick()
        assertThat(opened).isEqualTo("사진")
    }

    @Test
    fun `독을 훑으면 가까운 아이콘이 커지고 뗀 곳의 아이콘이 실행된다`() {
        var opened = ""
        val names = listOf("하나", "둘", "셋", "넷")
        compose.setContent {
            KDock(
                names.map { name -> KDockItem(name, onClick = { opened = name }) { Box(Modifier.background(Color.Gray)) } },
                Modifier.testTag("dock"),
                magnification = 2f,
            )
        }
        val first = compose.onNodeWithContentDescription("하나").fetchSemanticsNode().boundsInRoot
        val third = compose.onNodeWithContentDescription("셋").fetchSemanticsNode().boundsInRoot
        val dockLeft = compose.onNodeWithTag("dock").fetchSemanticsNode().boundsInRoot.left
        compose.onNodeWithTag("dock").performTouchInput {
            down(Offset(first.center.x - dockLeft, centerY))
            moveTo(Offset(third.center.x - dockLeft, centerY))
        }
        compose.waitForIdle()
        val grown = compose.onNodeWithContentDescription("셋").fetchSemanticsNode().boundsInRoot.width
        val far = compose.onNodeWithContentDescription("하나").fetchSemanticsNode().boundsInRoot.width
        assertThat(grown).isGreaterThan(far)
        compose.onNodeWithTag("dock").performTouchInput {
            val now = compose.onNodeWithContentDescription("셋").fetchSemanticsNode().boundsInRoot
            moveTo(Offset(now.center.x - dockLeft, centerY))
            up()
        }
        assertThat(opened).isEqualTo("셋")
    }

    @Test
    fun `독을 누른 채 터치가 취소되면 아무것도 실행하지 않는다`() {
        var opened = ""
        compose.setContent {
            KDock(
                listOf("하나", "둘").map { name ->
                    KDockItem(name, onClick = { opened = name }) { Box(Modifier.background(Color.Gray)) }
                },
                Modifier.testTag("dock"),
            )
        }
        compose.onNodeWithTag("dock").performTouchInput {
            down(center)
            cancel()
        }
        compose.waitForIdle()
        assertThat(opened).isEmpty()
    }

    @Test
    fun `궤도 아이콘은 TalkBack에서 숨고 돌며 애니메이션 삭제면 멈춘다`() {
        var reduced by mutableStateOf(false)
        var where = Offset.Zero
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalKineticaReducedMotion provides reduced) {
                KOrbitingIcons(
                    listOf(
                        KOrbit(
                            60.dp,
                            periodMs = 4_000,
                            icons = listOf({
                                BasicText(
                                    "별",
                                    Modifier.onGloballyPositioned {
                                        where =
                                            it.positionInRoot()
                                    },
                                )
                            }),
                        ),
                    ),
                ) { BasicText("가운데") }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("가운데").assertExists()
        assertThat(compose.onAllNodesWithText("별").fetchSemanticsNodes()).isEmpty()
        val before = where
        compose.mainClock.advanceTimeBy(1_000)
        assertThat(where).isNotEqualTo(before)
        reduced = true
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)
        val stopped = where
        compose.mainClock.advanceTimeBy(1_000)
        assertThat(where).isEqualTo(stopped)
    }
}
