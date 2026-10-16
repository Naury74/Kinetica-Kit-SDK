package com.naury.kinetica.ui

import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
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
class KMotionExtrasTest {
    @get:Rule
    val compose = createComposeRule()

    private val resolver get() = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver

    private fun reduced(block: () -> Unit) {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            block()
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
    }

    @Test
    fun `전체 화면 모달은 아래로 끌어 놓으면 닫힌다`() {
        var open by mutableStateOf(true)
        compose.setContent {
            KFullScreenModal(open, { open = false }, title = "새 게시물") { Box(Modifier.fillMaxSize().testTag("modal")) }
        }
        compose.onNodeWithTag("modal").performTouchInput { swipeDown(startY = height * 0.2f, endY = height * 0.9f) }
        compose.waitForIdle()
        assertThat(open).isFalse()
    }

    @Test
    fun `원형 드러내기는 사라지는 애니메이션이 끝나면 내용을 뺀다`() {
        var visible by mutableStateOf(true)
        compose.setContent { KCircularReveal(visible, Modifier.size(100.dp)) { BasicText("검색") } }
        compose.onNodeWithText("검색").assertExists()
        visible = false
        compose.waitForIdle()
        compose.onNodeWithText("검색").assertDoesNotExist()
    }

    @Test
    fun `원형 드러내기 수정자는 진행만큼만 그린다`() {
        compose.setContent {
            Box(Modifier.size(100.dp).testTag("r").circularReveal({ 0.3f }, { Offset.Zero }).background(Color.Red))
        }
        val map = compose.onNodeWithTag("r").captureToImage().toPixelMap()
        assertThat(map[2, 2]).isEqualTo(Color.Red)
        assertThat(map[map.width - 2, map.height - 2]).isNotEqualTo(Color.Red)
    }

    @Test
    fun `공유 요소 호스트는 대상을 바꾸면 새 화면을 그리고 밖에서는 수정자가 아무 일도 하지 않는다`() {
        var target by mutableStateOf(0)
        compose.setContent {
            Column {
                Box(Modifier.size(10.dp).kSharedElement("solo").testTag("solo"))
                KSharedTransitionHost(target, onBack = if (target == 1) ({ target = 0 }) else null, backTarget = 0) { screen ->
                    if (screen == 0) {
                        BasicText("목록", Modifier.kSharedBounds("card"))
                    } else {
                        BasicText("상세", Modifier.kSharedBounds("card"))
                    }
                }
            }
        }
        compose.onNodeWithTag("solo").assertExists()
        target = 1
        compose.waitForIdle()
        compose.onNodeWithText("상세").assertExists()
        compose.onNodeWithText("목록").assertDoesNotExist()
    }

    @Test
    fun `차례 등장은 애니메이션 삭제 설정에서 바로 보인다`() = reduced {
        compose.mainClock.autoAdvance = false
        compose.setContent { Box(Modifier.size(40.dp).testTag("s").staggerIn(10).background(Color.Red)) }
        compose.mainClock.advanceTimeBy(16)
        val map = compose.onNodeWithTag("s").captureToImage().toPixelMap()
        assertThat(map[map.width / 2, map.height / 2]).isEqualTo(Color.Red)
    }

    @Test
    fun `등장한 항목은 상태에 남아 다시 등장하지 않는다`() {
        val state = KStaggerState()
        compose.setContent { Box(Modifier.size(40.dp).staggerIn(0, state)) }
        compose.waitForIdle()
        assertThat(state.shown).contains(0)
        state.reset()
        assertThat(state.shown).isEmpty()
    }

    @Test
    fun `길게 누르면 다 찬 뒤에야 확인하고 TalkBack은 바로 실행한다`() {
        var confirmed = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { Box(Modifier.size(80.dp).testTag("h").holdToConfirm({ confirmed++ }, durationMs = 1_000)) }
        compose.onNodeWithTag("h").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("h").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(500)
        assertThat(confirmed).isEqualTo(0)
        compose.onNodeWithTag("h").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_100)
        assertThat(confirmed).isEqualTo(1)
        compose.onNodeWithTag("h").performTouchInput { up() }
        compose.onNodeWithTag("h").performSemanticsAction(SemanticsActions.OnClick)
        assertThat(confirmed).isEqualTo(2)
    }

    @Test
    fun `복사 버튼은 클립보드에 넣고 복사됨을 알린다`() {
        var copied = 0
        compose.setContent { KCopyButton("KINETICA-20", onCopied = { copied++ }) }
        compose.onNodeWithContentDescription("Copy").performClick()
        compose.waitForIdle()
        assertThat(copied).isEqualTo(1)
        val clip = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSystemService(android.content.ClipboardManager::class.java).primaryClip
        assertThat(clip?.getItemAt(0)?.text.toString()).isEqualTo("KINETICA-20")
        compose.onNodeWithContentDescription("Copied").assertExists()
    }

    @Test
    fun `떠오르는 글자는 끝나면 사라진다`() {
        val state = KFloatUpState()
        compose.setContent { Box(Modifier.size(40.dp).floatUp(state)) }
        state.emit("+1")
        compose.waitForIdle()
        assertThat(state.items).isEmpty()
    }

    @Test
    fun `날아가기는 위치를 모르면 바로 도착하고 알면 날아간 뒤 도착한다`() {
        val state = KFlyToState()
        var arrived = 0
        state.launch("none", onArrive = { arrived++ }) {}
        assertThat(arrived).isEqualTo(1)
        compose.setContent {
            KFlyToHost(state, Modifier.fillMaxSize()) {
                Column {
                    Box(Modifier.size(40.dp).flyToTarget(state))
                    Box(Modifier.size(80.dp).flyToSource(state, "shoe"))
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { state.launch("shoe", onArrive = { arrived++ }) { Box(Modifier.fillMaxSize().background(Color.Red)) } }
        compose.waitForIdle()
        assertThat(arrived).isEqualTo(2)
    }

    @Test
    fun `모핑 아이콘은 끝에서 대상 아이콘과 같게 그린다`() {
        compose.setContent {
            Row {
                MorphIcon(KMorphIcons.Menu, KMorphIcons.ArrowBack, { 1f }, Modifier.testTag("a"), Color.Black)
                MorphIcon(KMorphIcons.ArrowBack, KMorphIcons.ArrowBack, { 0f }, Modifier.testTag("b"), Color.Black)
            }
        }
        val a = compose.onNodeWithTag("a").captureToImage().toPixelMap()
        val b = compose.onNodeWithTag("b").captureToImage().toPixelMap()
        assertThat(a.buffer.toList()).isEqualTo(b.buffer.toList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `점이 하나뿐인 획은 받지 않는다`() {
        KMorphGlyph(listOf(listOf(Offset.Zero)))
    }

    @Test
    fun `대표색은 가장 넓은 색과 가장 선명한 색을 고른다`() {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(40, 40, 48))
        for (x in 0 until 3) for (y in 0 until 3) bitmap.setPixel(x, y, android.graphics.Color.rgb(255, 60, 30))
        val palette = imagePaletteOf(bitmap.asImageBitmap())
        assertThat(palette.dominant.red).isLessThan(0.3f)
        assertThat(palette.vibrant.red).isGreaterThan(0.8f)
        assertThat(palette.onDominant).isEqualTo(Color.White)
    }

    @Test
    fun `배경 효과는 앱이 백그라운드에 있으면 멈춰 있다`() {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.CREATED }
            override val lifecycle: Lifecycle get() = registry
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                StarfieldBackground(Modifier.size(120.dp).testTag("sky"))
            }
        }
        compose.mainClock.advanceTimeBy(100)
        val first = compose.onNodeWithTag("sky").captureToImage().toPixelMap().buffer.toList()
        compose.mainClock.advanceTimeBy(2_000)
        val later = compose.onNodeWithTag("sky").captureToImage().toPixelMap().buffer.toList()
        assertThat(later).isEqualTo(first)
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.advanceTimeBy(2_000)
        val moving = compose.onNodeWithTag("sky").captureToImage().toPixelMap().buffer.toList()
        assertThat(moving).isNotEqualTo(first)
    }

    @Test
    fun `소셜과 모션 컴포넌트를 붙인 300개 목록은 애니메이션 중에 항목을 다시 구성하지 않는다`() {
        var compositions = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            KineticaTheme(surface = SurfaceStyles.CleanLight) {
                val list = rememberLazyListState()
                val stagger = rememberKStaggerState()
                LazyColumn(Modifier.size(360.dp, 640.dp), state = list) {
                    items(300) { i ->
                        SideEffect { compositions++ }
                        Column(Modifier.fillMaxWidth().staggerIn(i, stagger).scrollTransform(list, i)) {
                            KChatBubble("메시지 $i", if (i % 2 == 0) KChatSide.Sent else KChatSide.Received, status = KChatStatus.Read)
                            if (i % 3 == 0) KTypingIndicator()
                            KStoryRing(seen = i % 2 == 0, loading = i % 4 == 0) { Box(Modifier.size(40.dp)) }
                            KAttachmentChip("파일 $i.pdf", status = KAttachmentStatus.Uploading)
                            Box(Modifier.height(1.dp))
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        val settled = compositions
        assertThat(settled).isGreaterThan(0)
        compose.mainClock.advanceTimeBy(2_000)
        assertThat(compositions).isEqualTo(settled)
    }

    @Test
    fun `전체 화면 모달은 애니메이션 삭제 설정에서 바로 열린다`() = reduced {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            KFullScreenModal(true, {}) { BasicText("모달", Modifier.testTag("m")) }
        }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("m").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        val bounds = compose.onNodeWithTag("m").fetchSemanticsNode().boundsInRoot
        assertThat(bounds.top).isLessThan(50f)
    }
}
