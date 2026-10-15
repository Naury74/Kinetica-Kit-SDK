package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BrushPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w900dp-h900dp-mdpi")
class GlassMiniPlayerBarTest {
    @get:Rule
    val compose = createComposeRule()

    private val tabs = listOf(GlassTab("Home"), GlassTab("Library"))

    private fun setTabBarPlayer(state: GlassMiniPlayerState) {
        compose.setContent {
            GlassScaffold(
                Modifier.fillMaxSize(),
                bottomBar = {
                    GlassFloatingTabBar(tabs, 0, {}, accessory = {
                        GlassMiniPlayerBar(state, artwork = { Box(Modifier.fillMaxSize().testTag("smallArt").background(Color.Red)) }) {
                            BasicText("막대")
                        }
                    })
                },
            ) {
                GlassMiniPlayerFullScreen(state, artwork = { Box(Modifier.fillMaxSize().testTag("bigArt").background(Color.Red)) }) {
                    BasicText("전체 화면")
                }
            }
        }
    }

    @Test
    fun `탭 바 액세서리의 막대를 누르면 그 자리에서 아트가 커지며 펼치고 닫으면 막대로 돌아간다`() {
        val state = GlassMiniPlayerState(false)
        setTabBarPlayer(state)
        compose.onNodeWithTag("bigArt").assertDoesNotExist()
        compose.onNodeWithText("전체 화면").assertDoesNotExist()
        val small = compose.onNodeWithTag("smallArt", useUnmergedTree = true).getUnclippedBoundsInRoot()

        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("막대").performClick()
        compose.mainClock.advanceTimeByFrame()
        val start = compose.onNodeWithTag("bigArt").getUnclippedBoundsInRoot()
        assertThat(start.top.value).isWithin(2f).of(small.top.value)
        assertThat(start.left.value).isWithin(2f).of(small.left.value)
        assertThat(start.width.value).isWithin(2f).of(small.width.value)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertThat(state.isExpanded).isTrue()
        assertThat(state.progress).isWithin(0.01f).of(1f)
        compose.onNodeWithText("전체 화면").assertIsDisplayed()
        val big = compose.onNodeWithTag("bigArt").getUnclippedBoundsInRoot()
        assertThat(big.width.value).isGreaterThan(small.width.value * 3)
        assertThat(big.top.value).isLessThan(small.top.value)

        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Now playing"))
            .performSemanticsAction(SemanticsActions.Dismiss)
        compose.waitForIdle()
        assertThat(state.isExpanded).isFalse()
        compose.onNodeWithTag("bigArt").assertDoesNotExist()
        compose.onNodeWithText("막대").assertIsDisplayed()
    }

    @Test
    fun `액세서리 막대를 위로 끌어도 펼친다`() {
        val state = GlassMiniPlayerState(false)
        setTabBarPlayer(state)
        compose.onNodeWithText("막대").performTouchInput {
            swipe(center, center.copy(y = center.y - 400f), durationMillis = 150)
        }
        compose.waitForIdle()
        assertThat(state.isExpanded).isTrue()
    }

    @Test
    fun `크기가 없는 Painter는 뷰어 영역을 꽉 채운다`() {
        val state = GlassImageViewerState(openAt = 0, restored = true)
        compose.setContent {
            GlassScaffold(Modifier.fillMaxSize()) {
                GlassImageViewer(state, listOf(BrushPainter(Brush.verticalGradient(listOf(Color.Green, Color.Green)))))
            }
        }
        compose.waitForIdle()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        // 위아래 바가 없는 왼쪽 가장자리 가운데와 화면 가운데 모두 사진 색이다.
        assertThat(pixels[pixels.width / 2, pixels.height / 2]).isEqualTo(Color.Green)
        assertThat(pixels[2, pixels.height / 2]).isEqualTo(Color.Green)
    }

    @Test
    fun `열린 채 사진 목록이 비면 닫히고 onDismiss를 부른다`() {
        var photos by mutableStateOf(listOf(ColorPainter(Color.Red), ColorPainter(Color.Blue)))
        var dismissed = false
        compose.setContent {
            GlassScaffold(Modifier.fillMaxSize()) {
                GlassImageViewer(photos, startIndex = 1, onDismiss = { dismissed = true })
            }
        }
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Photo viewer")).assertExists()

        compose.runOnIdle { photos = emptyList() }
        compose.waitForIdle()
        assertThat(dismissed).isTrue()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Photo viewer")).assertDoesNotExist()
    }

    @Test
    fun `상세 칸 배경은 좁은 화면에서는 상세와 함께, 넓은 화면에서는 빈 상세 뒤에도 깔린다`() {
        var selected by mutableStateOf<String?>(null)
        var width by mutableStateOf(400.dp)
        compose.setContent {
            Box(Modifier.width(width)) {
                GlassListDetail(
                    selected,
                    onBack = { selected = null },
                    emptyDetail = { BasicText("nothing") },
                    detailBackground = { Box(Modifier.fillMaxSize().testTag("detailBg")) },
                    list = { BasicText("list") },
                ) { BasicText("detail $it") }
            }
        }
        compose.onNodeWithTag("detailBg").assertDoesNotExist()
        compose.runOnIdle { selected = "mail" }
        compose.onNodeWithText("detail mail").assertIsDisplayed()
        assertThat(compose.onNodeWithTag("detailBg").getUnclippedBoundsInRoot().width).isEqualTo(400.dp)

        compose.runOnIdle {
            selected = null
            width = 840.dp
        }
        compose.onNodeWithText("nothing").assertIsDisplayed()
        val background = compose.onNodeWithTag("detailBg").getUnclippedBoundsInRoot()
        assertThat(background.width).isEqualTo(840.dp - GlassAdaptiveDefaults.ListPaneWidth)
    }
}
