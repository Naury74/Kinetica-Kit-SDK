package com.naury.kinetica.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.KeyInjectionScope
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.withKeyDown
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.EasingSpec
import com.naury.kinetica.core.animation.Keyframe
import com.naury.kinetica.core.animation.RotationDirection
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.history.KineticaSession
import com.naury.kinetica.history.SessionOpenResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 타임라인 확대, 여러 키 선택과 끌기, easing 곡선, 회전 방향을 실제 터치로 확인한다. mdpi라 1dp가 1px이고, 처음 배율은 1ms에 0.08px이다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1000dp-h900dp-mdpi")
class TimelineGesturesTest {
    @get:Rule
    val compose = createComposeRule()

    private val session = (KineticaSession.create(SceneDocument.empty(Artboard(1080.0, 1920.0))) as SessionOpenResult.Opened).session

    private fun openMotion() {
        compose.setContent { KineticaEditor(session) }
        compose.onNodeWithText("Rectangle").performClick()
        compose.onNodeWithText("Motion").performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun keys(property: AnimatableProperty): List<Keyframe> {
        val document = session.document.value
        return document.timelines.singleOrNull()?.track(document.rootOrder.single(), property)?.keyframes.orEmpty()
    }

    private fun times(property: AnimatableProperty) = keys(property).map { it.timeMs }

    private fun addKey(name: String) {
        compose.onNodeWithContentDescription("Add key for $name").performClick()
        compose.waitForIdle()
    }

    private fun seek(x: Float) {
        compose.onNodeWithTag(RULER_TAG).performTouchInput { click(Offset(x, centerY)) }
        compose.waitForIdle()
    }

    private fun lanes(): SemanticsNodeInteraction = compose.onNodeWithTag(LANES_TAG)

    private fun timelineButton(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(TIMELINE_TAG)))

    // 0ms 키가 16px, 1초가 80px. 줄 높이는 44px이고 X, Y, 회전 순서다.
    private fun keyX(timeMs: Long, pxPerMs: Float = 0.08f) = 16f + timeMs * pxPerMs

    private fun rowY(index: Int) = 22f + index * 44f

    @Test
    fun `확대 버튼과 맞춤에 따라 눈금자의 시각이 바뀐다`() {
        openMotion()

        compose.onNodeWithContentDescription("Zoom in").performClick()
        seek(96f)
        addKey("X")
        assertThat(times(AnimatableProperty.TranslationX)).containsExactly(667L)

        timelineButton("Fit").performClick()
        compose.waitForIdle()
        val width = compose.onNodeWithTag(RULER_TAG).fetchSemanticsNode().size.width
        seek(96f)
        addKey("Y")
        val fitted = (width - 40f) / 3_000f
        assertThat(times(AnimatableProperty.TranslationY).single().toDouble()).isWithin(2.0).of(80.0 / fitted)
    }

    @Test
    fun `두 손가락으로 벌리면 기준점을 두고 확대된다`() {
        openMotion()

        lanes().performTouchInput {
            pinch(Offset(30f, rowY(0)), Offset(0f, rowY(0)), Offset(50f, rowY(0)), Offset(80f, rowY(0)), durationMillis = 300)
        }
        compose.waitForIdle()
        seek(96f)
        addKey("X")

        // 배율은 4배(1ms에 0.32px)가 된다. 전체가 화면에 들어가는 동안은 스크롤할 곳이 없어 기준점이 조금 밀리므로,
        // 끝난 뒤 스크롤은 약 6px이고 x 96은 약 268ms다. 확대하지 않았다면 1000ms다.
        assertThat(times(AnimatableProperty.TranslationX).single().toDouble()).isWithin(3.0).of(268.0)
    }

    @Test
    fun `확대한 뒤 가로 휠로 시간 축을 스크롤한다`() {
        openMotion()
        repeat(4) { compose.onNodeWithContentDescription("Zoom in").performClick() }
        seek(96f)
        addKey("X")

        lanes().performMouseInput {
            moveTo(Offset(300f, rowY(0)))
            scroll(2f, ScrollWheel.Horizontal)
        }
        compose.waitForIdle()
        seek(96f)
        addKey("Y")

        // 1ms에 0.405px이고 휠 한 칸이 48px이므로 두 칸이면 약 237ms 옮겨진다.
        val moved = times(AnimatableProperty.TranslationY).single() - times(AnimatableProperty.TranslationX).single()
        assertThat(kotlin.math.abs(moved).toDouble()).isWithin(5.0).of(237.0)
    }

    @Test
    fun `사각 선택한 키를 함께 끌면 같은 양만큼 옮겨지고 실행 취소는 한 번이다`() {
        openMotion()
        addKey("X")
        addKey("Y")
        seek(keyX(1_000))
        addKey("X")
        addKey("Y")
        val before = session.document.value

        lanes().performTouchInput {
            down(Offset(150f, 5f))
            moveTo(Offset(100f, 40f))
            moveTo(Offset(5f, rowY(1) + 14f))
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithText("4 selected").assertExists()

        lanes().performTouchInput {
            down(Offset(keyX(0), rowY(0)))
            moveBy(Offset(20f, 0f))
            moveBy(Offset(20f, 0f))
            up()
        }
        compose.waitForIdle()

        assertThat(times(AnimatableProperty.TranslationX)).containsExactly(500L, 1_500L).inOrder()
        assertThat(times(AnimatableProperty.TranslationY)).containsExactly(500L, 1_500L).inOrder()
        session.history.undo()
        assertThat(session.document.value).isEqualTo(before)
    }

    @Test
    fun `길게 누르면 선택 모드가 되어 탭으로 키를 더하고 한꺼번에 지운다`() {
        openMotion()
        addKey("X")
        seek(keyX(1_000))
        addKey("X")
        addKey("Y")

        lanes().performTouchInput { longClick(Offset(keyX(0), rowY(0))) }
        compose.waitForIdle()
        lanes().performTouchInput { click(Offset(keyX(1_000), rowY(0))) }
        compose.waitForIdle()
        compose.onNodeWithText("2 selected").assertExists()

        timelineButton("Delete").performClick()
        compose.waitForIdle()

        assertThat(times(AnimatableProperty.TranslationX)).isEmpty()
        assertThat(times(AnimatableProperty.TranslationY)).containsExactly(1_000L)
    }

    @Test
    fun `고른 키를 복사해 재생 위치에 붙여넣는다`() {
        openMotion()
        addKey("X")

        lanes().performTouchInput { click(Offset(keyX(0), rowY(0))) }
        compose.waitForIdle()
        timelineButton("Copy").performClick()
        seek(keyX(2_000))
        timelineButton("Paste").performClick()
        compose.waitForIdle()

        assertThat(times(AnimatableProperty.TranslationX)).containsExactly(0L, 2_000L).inOrder()
        compose.onNodeWithText("1 selected").assertExists()
    }

    @Test
    fun `곡선 편집기에서 프리셋을 고르고 조절점을 끌어 베지어를 만든다`() {
        openMotion()
        addKey("X")
        seek(keyX(1_000))
        addKey("X")
        lanes().performTouchInput { click(Offset(keyX(0), rowY(0))) }
        compose.waitForIdle()

        timelineButton("Curve").performClick()
        timelineButton("Ease in").performClick()
        compose.waitForIdle()
        assertThat(keys(AnimatableProperty.TranslationX).first().easing).isEqualTo(EasingSpec.EaseIn)

        val graph = compose.onNodeWithTag(EASING_GRAPH_TAG).performScrollTo()
        val size = graph.fetchSemanticsNode().size
        val pad = minOf(size.width, size.height) * 0.08f
        fun point(x: Float, y: Float) =
            Offset(pad + x * (size.width - pad * 2), size.height - pad - (y + 0.5f) / 2f * (size.height - pad * 2))
        graph.performTouchInput { swipe(point(0.42f, 0f), point(0.1f, 1f), durationMillis = 300) }
        compose.waitForIdle()

        val easing = keys(AnimatableProperty.TranslationX).first().easing as EasingSpec.CubicBezier
        assertThat(easing.x1).isWithin(0.02).of(0.1)
        assertThat(easing.y1).isWithin(0.02).of(1.0)
        assertThat(easing.x2).isEqualTo(1.0)
        assertThat(easing.y2).isEqualTo(1.0)
    }

    @Test
    fun `회전 키를 고르면 회전 방향을 정할 수 있다`() {
        openMotion()
        addKey("Rotation")
        seek(keyX(1_000))
        addKey("Rotation")

        lanes().performTouchInput { click(Offset(keyX(0), rowY(2))) }
        compose.waitForIdle()
        timelineButton("Clockwise").performClick()
        compose.waitForIdle()

        assertThat(keys(AnimatableProperty.RotationDegrees).map { it.rotation })
            .containsExactly(RotationDirection.Clockwise, RotationDirection.Shortest).inOrder()
    }

    private fun keys(block: KeyInjectionScope.() -> Unit) {
        compose.onNodeWithTag(TIMELINE_TAG).performKeyInput(block)
        compose.waitForIdle()
    }

    @Test
    fun `타임라인 단축키로 모두 선택, 복사, 붙여넣기, 삭제, 선택 해제를 한다`() {
        openMotion()
        addKey("X")
        seek(keyX(1_000))
        addKey("X")
        addKey("Y")
        lanes().performTouchInput { click(Offset(keyX(0), rowY(0))) }
        compose.waitForIdle()

        keys { withKeyDown(Key.CtrlLeft) { pressKey(Key.A) } }
        compose.onNodeWithText("3 selected").assertExists()
        keys { withKeyDown(Key.CtrlLeft) { pressKey(Key.C) } }
        seek(keyX(2_000))
        keys { withKeyDown(Key.CtrlLeft) { pressKey(Key.V) } }
        assertThat(times(AnimatableProperty.TranslationX)).containsExactly(0L, 1_000L, 2_000L, 3_000L).inOrder()
        assertThat(times(AnimatableProperty.TranslationY)).containsExactly(1_000L, 3_000L).inOrder()
        compose.onNodeWithText("3 selected").assertExists()

        keys { pressKey(Key.Delete) }
        assertThat(times(AnimatableProperty.TranslationX)).containsExactly(0L, 1_000L).inOrder()
        assertThat(times(AnimatableProperty.TranslationY)).containsExactly(1_000L)

        lanes().performTouchInput { click(Offset(keyX(0), rowY(0))) }
        compose.waitForIdle()
        keys { pressKey(Key.Escape) }
        compose.onNodeWithText("0 selected").assertExists()
    }

    @Test
    fun `캔버스의 Delete는 고른 키가 있으면 키를 먼저 지우고 없으면 노드를 지운다`() {
        openMotion()
        addKey("X")
        seek(keyX(1_000))
        addKey("X")
        lanes().performTouchInput { click(Offset(keyX(0), rowY(0))) }
        compose.waitForIdle()

        compose.onNodeWithTag(CANVAS_TAG).requestFocus().performKeyInput { pressKey(Key.Delete) }
        compose.waitForIdle()
        assertThat(times(AnimatableProperty.TranslationX)).containsExactly(1_000L)
        assertThat(session.document.value.rootOrder).hasSize(1)

        compose.onNodeWithTag(CANVAS_TAG).performKeyInput { pressKey(Key.Delete) }
        compose.waitForIdle()
        assertThat(session.document.value.rootOrder).isEmpty()
    }

    @Test
    fun `Shift를 누른 채 클릭하면 키를 선택에 더한다`() {
        openMotion()
        addKey("X")
        seek(keyX(1_000))
        addKey("X")
        lanes().performTouchInput { click(Offset(keyX(0), rowY(0))) }
        compose.waitForIdle()

        // 터치 이벤트에는 테스트가 수정 키 상태를 싣지 않으므로 마우스 클릭으로 확인한다.
        keys { keyDown(Key.ShiftLeft) }
        lanes().performMouseInput { click(Offset(keyX(1_000), rowY(0))) }
        keys { keyUp(Key.ShiftLeft) }

        compose.onNodeWithText("2 selected").assertExists()
    }

    @Test
    fun `Ctrl과 휠로 시간 축을 확대한다`() {
        openMotion()
        lanes().performTouchInput { click(Offset(300f, rowY(0))) }
        compose.waitForIdle()

        keys { keyDown(Key.CtrlLeft) }
        lanes().performMouseInput {
            moveTo(Offset(300f, rowY(0)))
            scroll(-1f)
        }
        keys { keyUp(Key.CtrlLeft) }
        seek(96f)
        addKey("X")

        // 한 칸에 1.2배라 1ms에 0.096px이 되고, 전체가 화면에 들어가므로 스크롤은 0이다.
        assertThat(times(AnimatableProperty.TranslationX).single().toDouble()).isWithin(2.0).of(80.0 / 0.096)
    }
}
