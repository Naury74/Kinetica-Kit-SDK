package com.naury.kinetica.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 물결 진행 표시, 원형 슬라이더, 모양 바뀜 버튼, 쌓인 카드, 긁는 카드. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KControlsMotionTest {
    @get:Rule
    val compose = createComposeRule()

    private class Recording : HapticFeedback {
        val types = mutableListOf<HapticFeedbackType>()

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            types += hapticFeedbackType
        }
    }

    private fun rangeInfo(tag: String): ProgressBarRangeInfo? =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)

    // 물결 진행 표시

    @Test
    fun `물결 막대는 람다로 받은 진행 비율을 TalkBack 범위로 알리고 값이 바뀌면 따라간다`() {
        var progress by mutableFloatStateOf(0.3f)
        compose.setContent {
            KWavyProgressBar({ progress }, Modifier.testTag("bar"))
            KWavyProgressRing({ progress }, Modifier.testTag("ring"))
        }
        assertThat(rangeInfo("bar")!!.current).isWithin(0.001f).of(0.3f)
        progress = 1.7f
        compose.waitForIdle()
        assertThat(rangeInfo("bar")!!.current).isEqualTo(1f)
        assertThat(rangeInfo("ring")!!.current).isEqualTo(1f)
    }

    @Test
    fun `진행 정도를 모르는 물결 표시는 Indeterminate로 읽힌다`() {
        compose.setContent {
            KWavyProgressBar(Modifier.testTag("bar"))
            KWavyProgressRing(Modifier.testTag("ring"))
        }
        assertThat(rangeInfo("bar")).isEqualTo(ProgressBarRangeInfo.Indeterminate)
        assertThat(rangeInfo("ring")).isEqualTo(ProgressBarRangeInfo.Indeterminate)
    }

    @Test
    fun `진행이 바뀌면 물결이 살아나고 멈추면 잦아들어 평평해진다`() {
        compose.mainClock.autoAdvance = false
        var progress by mutableFloatStateOf(0.4f)
        lateinit var motion: WavyMotion
        compose.setContent { motion = rememberWavyMotion({ progress }, running = true) }
        compose.mainClock.advanceTimeBy(500)
        assertThat(motion.amplitude).isGreaterThan(0.5f)

        compose.mainClock.advanceTimeBy(4_000)
        assertThat(motion.amplitude).isEqualTo(0f)

        progress = 0.5f
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        assertThat(motion.amplitude).isGreaterThan(0.5f)
    }

    @Test
    fun `애니메이션 삭제처럼 멈춘 상태면 진행이 바뀌어도 물결 없이 평평하다`() {
        compose.mainClock.autoAdvance = false
        var progress by mutableFloatStateOf(0.4f)
        lateinit var motion: WavyMotion
        compose.setContent { motion = rememberWavyMotion({ progress }, running = false) }
        progress = 0.6f
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        assertThat(motion.amplitude).isEqualTo(0f)
        assertThat(motion.phase).isEqualTo(0f)
    }

    @Test
    fun `물결 모양 값이 잘못되면 예외를 던진다`() {
        assertThrows(IllegalArgumentException::class.java) { KWavyProgressLook(thickness = 0.dp) }
        assertThrows(IllegalArgumentException::class.java) { KWavyProgressLook(wavelength = (-1).dp) }
    }

    // 원형 슬라이더

    @Test
    fun `원형 슬라이더는 TalkBack 값 설정을 간격에 맞춰 넘기고 범위 정보를 알린다`() {
        var value by mutableFloatStateOf(10f)
        compose.setContent { KCircularSlider(value, { value = it }, "타이머", valueRange = 0f..60f, step = 5f) }
        val node = compose.onNodeWithContentDescription("타이머")
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(10f, 0f..60f, 11)))
        node.performSemanticsAction(SemanticsActions.SetProgress) { it(37f) }
        assertThat(value).isEqualTo(35f)
        node.performSemanticsAction(SemanticsActions.SetProgress) { it(500f) }
        assertThat(value).isEqualTo(60f)
    }

    @Test
    fun `원형 슬라이더는 방향키로 한 칸씩 움직인다`() {
        var value by mutableFloatStateOf(30f)
        compose.setContent { KCircularSlider(value, { value = it }, "온도", valueRange = 0f..60f, ticks = 12) }
        val node = compose.onNodeWithContentDescription("온도")
        node.requestFocus()
        node.performKeyInput { pressKey(Key.DirectionRight) }
        assertThat(value).isEqualTo(35f)
        node.performKeyInput { pressKey(Key.DirectionDown) }
        node.performKeyInput { pressKey(Key.DirectionDown) }
        assertThat(value).isEqualTo(25f)
    }

    @Test
    fun `원형 슬라이더는 트랙을 따라 끌면 손가락 각도의 값이 되고 눈금을 넘을 때 햅틱을 낸다`() {
        val haptics = Recording()
        var value by mutableFloatStateOf(0f)
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                KCircularSlider(value, { value = it }, "볼륨", Modifier.size(200.dp).testTag("dial"), valueRange = 0f..100f, ticks = 10)
            }
        }
        compose.onNodeWithTag("dial").performTouchInput {
            // 12시에서 시작해 3시 방향까지 시계 방향으로 돈다.
            val c = center
            val r = width * 0.44f
            down(Offset(c.x, c.y - r))
            for (i in 1..9) {
                val a = Math.toRadians(-90.0 + i * 10.0)
                moveTo(Offset(c.x + r * Math.cos(a).toFloat(), c.y + r * Math.sin(a).toFloat()))
            }
            up()
        }
        assertThat(value).isWithin(1f).of(25f)
        assertThat(haptics.types).contains(HapticFeedbackType.SegmentFrequentTick)
    }

    @Test
    fun `원형 슬라이더는 끝을 지나도 반대쪽 끝으로 넘어가지 않는다`() {
        val dial = CircleDial(0f..100f, -90f, 360f, 0f, 0, periodic = false)
        assertThat(dial.follow(5f, 355f)).isEqualTo(360f)
        assertThat(dial.follow(30f, 360f)).isEqualTo(360f)
        assertThat(dial.follow(350f, 360f)).isEqualTo(350f)
        val arc = CircleDial(0f..100f, 135f, 270f, 0f, 0, periodic = false)
        assertThat(arc.follow(300f, 260f)).isEqualTo(270f)
        assertThat(arc.follow(350f, 20f)).isEqualTo(0f)
    }

    @Test
    fun `원형 범위 슬라이더는 손잡이마다 TalkBack 노드가 있고 한 바퀴를 넘어 이어진다`() {
        var start by mutableFloatStateOf(23f)
        var end by mutableFloatStateOf(7f)
        compose.setContent {
            KCircularRangeSlider(start, end, { s, e ->
                start = s
                end = e
            }, "취침", "기상", step = 0.5f, formatValue = { "${it}시" })
        }
        compose.onNodeWithContentDescription("취침")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "23.0시"))
        compose.onNodeWithContentDescription("기상").performSemanticsAction(SemanticsActions.SetProgress) { it(25.2f) }
        assertThat(start).isEqualTo(23f)
        assertThat(end).isEqualTo(1f)
        compose.onNodeWithContentDescription("기상").requestFocus()
        compose.onNodeWithContentDescription("기상").performKeyInput { pressKey(Key.DirectionLeft) }
        assertThat(end).isEqualTo(0.5f)
        // 기본 가운데 내용은 손잡이 노드와 겹치므로 TalkBack에서 숨긴다.
        compose.onAllNodesWithText("23.0시").assertCountEquals(0)
    }

    @Test
    fun `끝값에 있는 원형 슬라이더 손잡이를 잡아도 반대쪽 끝으로 튀지 않는다`() {
        var value by mutableFloatStateOf(100f)
        compose.setContent { KCircularSlider(value, { value = it }, "볼륨", Modifier.size(200.dp).testTag("dial")) }
        compose.onNodeWithTag("dial").performTouchInput {
            // 12시 손잡이의 시계 방향 쪽 절반을 누른다.
            val r = width * 0.44f
            val a = Math.toRadians(-90.0 + 2.0)
            down(Offset(center.x + r * Math.cos(a).toFloat(), center.y + r * Math.sin(a).toFloat()))
            up()
        }
        assertThat(value).isEqualTo(100f)
    }

    @Test
    fun `원형 범위 슬라이더는 TalkBack 동작으로 범위 처음을 넘어 내려간다`() {
        var end by mutableFloatStateOf(0f)
        compose.setContent { KCircularRangeSlider(23f, end, { _, e -> end = e }, "취침", "기상", step = 0.25f) }
        val actions = compose.onNodeWithContentDescription("기상").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        actions.last().action()
        assertThat(end).isEqualTo(23.75f)
    }

    @Test
    fun `원형 범위 슬라이더의 값은 한 바퀴 돌려 범위 안으로 맞춘다`() {
        val dial = CircleDial(0f..24f, -90f, 360f, 0.25f, 24, periodic = true)
        assertThat(dial.wrap(25f)).isEqualTo(1f)
        assertThat(dial.wrap(-1f)).isEqualTo(23f)
        assertThat(dial.snap(23.9f)).isEqualTo(0f)
        assertThat(dial.tickOf(-0.5f)).isEqualTo(23)
    }

    // 모양 바뀜 버튼

    @Test
    fun `모양 바뀜 버튼은 버튼 역할로 읽히고 누르면 동작한다`() {
        var clicks = 0
        compose.setContent { KShapeMorphButton(KIcons.Send, "재생", { clicks++ }) }
        compose.onNodeWithContentDescription("재생")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `모양 토글은 체크박스로 읽히고 상태가 바뀐다`() {
        var liked by mutableStateOf(false)
        compose.setContent { KShapeToggle(liked, { liked = it }, KIcons.Heart, "좋아요") }
        val node = compose.onNodeWithContentDescription("좋아요")
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).assertIsOff()
        node.performClick()
        assertThat(liked).isTrue()
        node.assertIsOn()
    }

    @Test
    fun `모양 값은 원은 늘 1이고 사각형은 모서리 쪽이 더 멀다`() {
        val angles = listOf(0f, 0.4f, 0.785f, 2f, 4f)
        angles.forEach { assertThat(KMorphShape.Circle.radius(it)).isWithin(0.0001f).of(1f) }
        assertThat(KMorphShape.Square.radius(0.785f)).isGreaterThan(1.1f)
        assertThat(KMorphShape.Square.radius(0f)).isWithin(0.0001f).of(1f)
        assertThrows(IllegalArgumentException::class.java) { KMorphShape.cookie(1, 0.1f) }
        assertThrows(IllegalArgumentException::class.java) { KMorphShape.squircle(1f) }
    }

    // 쌓인 카드

    @Test
    fun `쌓인 카드는 누르면 펼쳐지고 고르면 앞으로 나오며 뒤로 가기로 선택이 풀린다`() {
        lateinit var dispatcher: OnBackPressedDispatcher
        lateinit var state: KStackedCardsState
        compose.setContent {
            CompositionLocalProvider(LocalKineticaReducedMotion provides true) {
                dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
                state = rememberKStackedCardsState()
                KStackedCards(4, Modifier.width(300.dp).testTag("stack"), state = state, cardHeight = 180.dp) { i ->
                    BasicText("카드 $i", Modifier.fillMaxSize())
                }
            }
        }
        compose.onNodeWithText("카드 2").performClick()
        assertThat(state.expanded).isTrue()
        assertThat(state.selected).isNull()

        // 펼치면 카드 2의 가운데는 카드 3에 덮이므로 보이는 위쪽 띠를 누른다.
        compose.onNodeWithText("카드 2").performTouchInput { click(Offset(centerX, 10f)) }
        assertThat(state.selected).isEqualTo(2)
        val top = compose.onNodeWithTag("stack").getUnclippedBoundsInRoot().top
        assertThat(compose.onNodeWithText("카드 2").getUnclippedBoundsInRoot().top).isEqualTo(top)

        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.waitForIdle()
        assertThat(state.selected).isNull()
        assertThat(state.expanded).isTrue()
    }

    @Test
    fun `쌓인 카드는 목록으로 읽히고 고른 카드를 먼저 읽는다`() {
        lateinit var state: KStackedCardsState
        compose.setContent {
            state = rememberKStackedCardsState(initiallyExpanded = true)
            KStackedCards(3, Modifier.width(300.dp).testTag("stack"), state = state) { i -> BasicText("카드 $i") }
        }
        compose.onNodeWithTag("stack").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo))
        compose.runOnIdle { state.select(1) }
        compose.onNodeWithText("카드 1")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, -1f))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        compose.onNodeWithText("카드 0").assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 0f))
    }

    // 긁는 카드

    @Test
    fun `긁는 카드는 열리기 전 내용을 숨기고 TalkBack 동작 하나로 바로 연다`() {
        var revealed = 0
        val state = KScratchCardState(false)
        compose.setContent {
            KScratchCard(Modifier.size(200.dp, 120.dp), state = state, onRevealed = { revealed++ }) { BasicText("커피 쿠폰") }
        }
        compose.onNodeWithText("커피 쿠폰").assertDoesNotExist()
        compose.onNodeWithContentDescription("Scratch to reveal")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertThat(state.isRevealed).isTrue()
        assertThat(revealed).isEqualTo(1)
        compose.onNodeWithText("커피 쿠폰").assertExists()
        compose.onNodeWithContentDescription("Scratch to reveal").assertDoesNotExist()
    }

    @Test
    fun `손가락으로 문턱만큼 긁으면 다 열리고 onRevealed를 한 번 부른다`() {
        var revealed = 0
        val state = KScratchCardState(false)
        compose.setContent {
            KScratchCard(Modifier.size(200.dp, 100.dp).testTag("card"), state = state, threshold = 0.3f, onRevealed = { revealed++ }) {
                BasicText("당첨")
            }
        }
        compose.onNodeWithTag("card").performTouchInput {
            down(Offset(0f, height * 0.25f))
            moveTo(Offset(width.toFloat(), height * 0.25f))
            moveTo(Offset(width.toFloat(), height * 0.75f))
            moveTo(Offset(0f, height * 0.75f))
            up()
        }
        compose.waitForIdle()
        assertThat(state.isRevealed).isTrue()
        assertThat(revealed).isEqualTo(1)
    }

    @Test
    fun `긁은 비율은 새로 긁힌 칸만 세고 reset으로 처음으로 돌아간다`() {
        val state = KScratchCardState(false)
        val size = Size(240f, 240f)
        state.scratch(null, Offset(0f, 120f), 20f, size, 0.9f)
        state.scratch(Offset(0f, 120f), Offset(240f, 120f), 20f, size, 0.9f)
        val once = state.scratchedFraction
        assertThat(once).isGreaterThan(0.1f)
        // 같은 자리를 다시 긁어도 비율은 늘지 않는다.
        state.scratch(Offset(240f, 120f), Offset(0f, 120f), 20f, size, 0.9f)
        assertThat(state.scratchedFraction).isEqualTo(once)
        assertThat(state.isRevealed).isFalse()

        state.reset()
        assertThat(state.scratchedFraction).isEqualTo(0f)
        assertThat(state.path.isEmpty).isTrue()
    }

    @Test
    fun `긁는 카드 문턱이 범위 밖이면 예외를 던진다`() {
        assertThrows(IllegalArgumentException::class.java) {
            compose.setContent { KScratchCard(threshold = 1.5f) { Box(Modifier) } }
        }
    }
}
