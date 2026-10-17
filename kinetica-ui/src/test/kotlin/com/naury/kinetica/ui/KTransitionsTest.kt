package com.naury.kinetica.ui

import android.provider.Settings
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
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
class KTransitionsTest {
    @get:Rule
    val compose = createComposeRule()

    private val presets = listOf(
        KTransitions.Push, KTransitions.Parallax, KTransitions.Slide, KTransitions.Cover, KTransitions.Uncover,
        KTransitions.Zoom, KTransitions.Fade, KTransitions.FadeThrough, KTransitions.SharedAxisX, KTransitions.SharedAxisY,
        KTransitions.SharedAxisZ, KTransitions.TabSlide, KTransitions.SheetStack, KTransitions.CardStack, KTransitions.Cube,
        KTransitions.Flip, KTransitions.CircleReveal, KTransitions.reveal(KRevealShape.Star), KTransitions.BlurDissolve,
        KTransitions.Wipe, KTransitions.wipe(KTransitionEdge.Top), KTransitions.cover(KTransitionEdge.Bottom),
        KTransitions.uncover(KTransitionEdge.Start), KTransitions.push(from = KTransitionEdge.Start), KTransitions.None,
    )

    private fun frame(rtl: Boolean = false) = KTransitionFrame().apply { reset(Size(400f, 800f), 2f, if (rtl) -1f else 1f) }

    private fun KTransitionFrame.assertUntouched() {
        assertThat(translationX).isZero()
        assertThat(translationY).isZero()
        assertThat(scaleX).isEqualTo(1f)
        assertThat(scaleY).isEqualTo(1f)
        assertThat(rotationX).isZero()
        assertThat(rotationY).isZero()
        assertThat(rotationZ).isZero()
        assertThat(alpha).isEqualTo(1f)
        assertThat(dim).isZero()
        assertThat(cornerRadius).isZero()
        assertThat(blurRadius).isZero()
        assertThat(clipsRect).isFalse()
        assertThat(revealShape).isNull()
    }

    @Test
    fun `모든 전환은 끝나면 들어온 화면이, 시작할 때는 가려질 화면이 원래 모습이다`() {
        presets.forEach { transition ->
            listOf(false, true).forEach { rtl ->
                frame(rtl).apply { transition.enter(this, 1f) }.assertUntouched()
                frame(rtl).apply { transition.exit(this, 0f) }.assertUntouched()
            }
        }
    }

    @Test
    fun `밀기는 진행률만큼 들어오고 아래 화면은 물러나며 어두워지고 RTL에서는 반대쪽에서 들어온다`() {
        val half = frame().apply { KTransitions.Push.enter(this, 0.5f) }
        assertThat(half.translationX).isEqualTo(200f)
        assertThat(half.edgeShadow).isEqualTo(0.5f)
        val below = frame().apply { KTransitions.Push.exit(this, 0.5f) }
        assertThat(below.translationX).isWithin(0.01f).of(-60f)
        assertThat(below.dim).isWithin(0.001f).of(0.09f)

        assertThat(frame(rtl = true).apply { KTransitions.Push.enter(this, 0.5f) }.translationX).isEqualTo(-200f)
        assertThat(frame(rtl = true).apply { KTransitions.Push.exit(this, 0.5f) }.translationX).isWithin(0.01f).of(60f)
    }

    @Test
    fun `와이프와 드러내기는 진행률만큼 자르고 RTL에서는 반대쪽부터 드러낸다`() {
        val ltr = frame().apply { KTransitions.Wipe.enter(this, 0.25f) }
        assertThat(ltr.clipLeft).isEqualTo(300f)
        assertThat(ltr.clipRight).isEqualTo(400f)
        val rtl = frame(rtl = true).apply { KTransitions.Wipe.enter(this, 0.25f) }
        assertThat(rtl.clipLeft).isEqualTo(0f)
        assertThat(rtl.clipRight).isEqualTo(100f)

        val circle = frame().apply { KTransitions.CircleReveal.enter(this, 0.5f) }
        assertThat(circle.revealShape).isEqualTo(KRevealShape.Circle)
        // 가운데에서 가장 먼 모서리까지의 절반
        assertThat(circle.revealRadius).isWithin(0.1f).of(Math.hypot(200.0, 400.0).toFloat() / 2f)
    }

    @Test
    fun `뒤집기는 반이 지나야 새 화면이 보이고 정육면체는 이웃한 면의 경첩을 축으로 돈다`() {
        assertThat(frame().apply { KTransitions.Flip.enter(this, 0.4f) }.alpha).isEqualTo(0f)
        assertThat(frame().apply { KTransitions.Flip.exit(this, 0.4f) }.alpha).isEqualTo(1f)
        assertThat(frame().apply { KTransitions.Flip.enter(this, 0.6f) }.alpha).isEqualTo(1f)

        val enter = frame().apply { KTransitions.Cube.enter(this, 0.5f) }
        val exit = frame().apply { KTransitions.Cube.exit(this, 0.5f) }
        assertThat(enter.pivotX).isEqualTo(0f)
        assertThat(exit.pivotX).isEqualTo(1f)
        assertThat(enter.rotationY).isEqualTo(45f)
        assertThat(exit.rotationY).isEqualTo(-45f)
        assertThat(frame(rtl = true).apply { KTransitions.Cube.enter(this, 0.5f) }.pivotX).isEqualTo(1f)
    }

    private fun pushScreens(
        stack: androidx.compose.runtime.MutableState<List<String>>,
        transition: KScreenTransition,
        rtl: Boolean = false,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
                PushNavigation(
                    stack.value,
                    { stack.value = stack.value.dropLast(1) },
                    Modifier.fillMaxSize().testTag("nav"),
                    transition = transition,
                    paneTitle = { "$it 화면" },
                ) { screen -> Box(Modifier.fillMaxSize().testTag(screen)) { BasicText(screen) } }
            }
        }
    }

    private lateinit var dispatcher: OnBackPressedDispatcher

    // boundsInRoot는 부모 영역으로 잘리므로 화면 밖으로 밀린 위치는 positionInRoot로 읽는다.
    private fun left(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.x

    private fun assertPushedFromEnd(rtl: Boolean) {
        val stack = mutableStateOf(listOf("list"))
        pushScreens(stack, KTransitions.Slide, rtl)
        compose.mainClock.autoAdvance = false
        stack.value = listOf("list", "detail")
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(FRAME_MS * 4)
        val x = left("detail")
        val width = compose.onNodeWithTag("nav").fetchSemanticsNode().size.width
        if (rtl) assertThat(x).isLessThan(0f) else assertThat(x).isGreaterThan(0f)
        assertThat(kotlin.math.abs(x)).isLessThan(width.toFloat())
        // 가려지는 화면은 전환하는 동안 TalkBack에 드러나지 않는다.
        compose.onNodeWithText("list").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertThat(left("detail")).isEqualTo(0f)
    }

    @Test
    fun `화면을 넣으면 중간 프레임에서 새 화면이 끝 쪽에서 들어온다`() = assertPushedFromEnd(rtl = false)

    @Test
    fun `RTL에서는 새 화면이 왼쪽에서 들어온다`() = assertPushedFromEnd(rtl = true)

    @Test
    fun `예측형 뒤로 가기는 제스처를 따라 전환을 되감고 취소하면 돌아오며 끝내면 화면을 뺀다`() {
        val stack = mutableStateOf(listOf("list", "detail"))
        pushScreens(stack, KTransitions.Push)
        compose.waitForIdle()
        val width = compose.onNodeWithTag("nav").fetchSemanticsNode().size.width

        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(10f, 0f, 0.5f, BackEventCompat.EDGE_LEFT))
        }
        compose.waitForIdle()
        // 진행률 1 - 0.5 * 0.6 = 0.7이므로 너비의 30%만큼 빠져 있다.
        assertThat(left("detail")).isWithin(2f).of(width * 0.3f)

        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()

        assertThat(left("detail")).isEqualTo(0f)
        assertThat(stack.value).containsExactly("list", "detail").inOrder()

        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(10f, 0f, 0.8f, BackEventCompat.EDGE_LEFT))
            dispatcher.onBackPressed()
        }
        compose.waitForIdle()
        assertThat(stack.value).containsExactly("list")
        compose.onNodeWithText("list").assertExists()
    }

    @Test
    fun `애니메이션 삭제 설정이면 화면을 넣자마자 전환 없이 바뀐다`() {
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            val stack = mutableStateOf(listOf("list"))
            pushScreens(stack, KTransitions.Cube)
            compose.mainClock.autoAdvance = false
            stack.value = listOf("list", "detail")
            compose.waitForIdle()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeByFrame()
            assertThat(left("detail")).isEqualTo(0f)
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
    }

    @Test
    fun `전환하는 동안 가려지는 화면은 TalkBack에 드러나지 않고 새 화면은 창 제목을 알린다`() {
        val stack = mutableStateOf(listOf("list"))
        pushScreens(stack, KTransitions.SheetStack)
        compose.mainClock.autoAdvance = false
        stack.value = listOf("list", "detail")
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(FRAME_MS * 3)
        compose.onNodeWithText("list").assertDoesNotExist()
        val title = compose.onNodeWithTag("detail").fetchSemanticsNode().let { node ->
            generateSequence(node) {
                it.parent
            }.firstNotNullOfOrNull { it.config.getOrElseNullable(SemanticsProperties.PaneTitle) { null } }
        }
        assertThat(title).isEqualTo("detail 화면")
    }

    @Test
    fun `탭 전환은 앞으로 가면 끝 쪽에서, 뒤로 가면 시작 쪽에서 새 내용이 들어온다`() {
        val tab = mutableIntStateOf(1)
        compose.setContent {
            KTransitionContent(tab.intValue, Modifier.fillMaxSize(), KTransitions.TabSlide, isForward = { from, to -> to > from }) { page ->
                Box(Modifier.fillMaxSize().testTag("page$page"))
            }
        }
        compose.mainClock.autoAdvance = false
        tab.intValue = 2
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(FRAME_MS * 5)
        assertThat(left("page2")).isGreaterThan(0f)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("page1").assertDoesNotExist()

        compose.mainClock.autoAdvance = false
        tab.intValue = 1
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(FRAME_MS * 5)
        // 뒤로 가면 같은 전환을 되감으므로 나가는 내용이 끝 쪽으로 빠지고 새 내용은 시작 쪽에서 들어온다.
        assertThat(left("page2")).isGreaterThan(0f)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("page2").assertDoesNotExist()
        assertThat(left("page1")).isEqualTo(0f)
    }

    @Test
    fun `NavHost용 밀기 묶음은 AnimatedContent에서 끝 쪽에서 들어오고 뒤로 가면 반대로 움직인다`() {
        val step = mutableIntStateOf(0)
        val rtl = mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                AnimatedContent(
                    step.intValue,
                    Modifier.fillMaxSize(),
                    transitionSpec = { kTransform(KNavTransitions.Push, forward = targetState > initialState) },
                ) { page -> Box(Modifier.fillMaxSize().testTag("step$page")) }
            }
        }
        compose.mainClock.autoAdvance = false
        step.intValue = 1
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(FRAME_MS * 5)
        assertThat(left("step1")).isGreaterThan(0f)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        compose.mainClock.autoAdvance = false
        step.intValue = 0
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(FRAME_MS * 5)
        assertThat(left("step0")).isLessThan(0f)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        rtl.value = true
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        step.intValue = 1
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(FRAME_MS * 5)
        assertThat(left("step1")).isLessThan(0f)
    }

    private fun expandHost(state: KExpandState, transform: KContainerTransform = KContainerTransform.Card) {
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            KineticaScaffold(Modifier.fillMaxSize()) {
                Box(Modifier.size(100.dp).expandSource(state, 1))
                KExpandHost(state, transform = transform) { Box(Modifier.fillMaxSize().testTag("detail")) }
            }
        }
    }

    private fun detailWidth() = compose.onNodeWithTag("detail").fetchSemanticsNode().boundsInRoot.width

    @Test
    fun `커진 상세 화면은 예측형 뒤로 가기를 따라 작아지고 취소하면 돌아오며 끝내면 닫힌다`() {
        val state = KExpandState()
        expandHost(state)
        state.expand(1)
        compose.waitForIdle()
        val full = detailWidth()

        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(10f, 0f, 1f, BackEventCompat.EDGE_LEFT))
        }
        compose.waitForIdle()
        assertThat(detailWidth()).isLessThan(full * 0.95f)

        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        assertThat(detailWidth()).isWithin(1f).of(full)

        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.onBackPressed()
        }
        compose.waitForIdle()
        assertThat(state.expandedKey).isNull()
        compose.onNodeWithTag("detail").assertDoesNotExist()
    }

    @Test
    fun `플로팅 버튼 모양은 상세 내용을 줄이지 않고 뒤늦게 보이며 애니메이션 삭제면 바로 열린다`() {
        val state = KExpandState()
        expandHost(state, KContainerTransform.fab(androidx.compose.ui.graphics.Color.Blue))
        compose.mainClock.autoAdvance = false
        state.expand(1)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(FRAME_MS * 2)
        // 원래 크기로 그리므로 레이아웃 너비는 처음부터 화면 너비다.
        val node = compose.onNodeWithTag("detail").fetchSemanticsNode()
        assertThat(node.size.width).isEqualTo(compose.onRoot().fetchSemanticsNode().size.width)
    }

    @Test
    fun `애니메이션 삭제 설정이면 상세 화면이 바로 열린다`() {
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            val state = KExpandState()
            expandHost(state)
            compose.mainClock.autoAdvance = false
            state.expand(1)
            compose.waitForIdle()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeByFrame()
            assertThat(detailWidth()).isWithin(1f).of(compose.onRoot().fetchSemanticsNode().size.width.toFloat())
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
    }

    private companion object {
        const val FRAME_MS = 16L
    }
}
