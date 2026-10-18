package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/** 갤러리 화면을 만드는 데 쓰는 제목 슬롯, 뷰어 슬롯, 순서 선택 표시. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class KGalleryChromeTest {
    @get:Rule
    val compose = createComposeRule()

    private class Photo(private val color: Color) : Painter() {
        override val intrinsicSize: Size get() = Size(400f, 300f)

        override fun DrawScope.onDraw() {
            drawRect(color)
        }
    }

    private val photos = listOf(Photo(Color.White), Photo(Color.Black), Photo(Color.Gray))

    private fun hasState(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    private val isDropdown = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)

    @Test
    fun `드롭다운 제목은 제목으로 읽히고 누르면 펼침 상태가 바뀐다`() {
        var expanded by mutableStateOf(false)
        compose.setContent {
            KTopAppBar(
                title = { KTopAppBarDropdownTitle("Recent", expanded = expanded, onClick = { expanded = !expanded }) },
                onBack = {},
            )
        }
        compose.onNode(isDropdown).assert(isHeading()).assert(hasState("Collapsed")).performClick()
        compose.waitForIdle()
        assertThat(expanded).isTrue()
        compose.onNode(isDropdown).assert(hasState("Expanded"))
    }

    @Test
    fun `글자만 넣은 제목 슬롯도 제목으로 읽힌다`() {
        compose.setContent { KTopAppBar(title = { BasicText("Albums") }) }
        compose.onNodeWithText("Albums").assert(isHeading())
    }

    @Test
    fun `Medium 바의 제목 슬롯은 접힘에 따라 한쪽에만 놓인다`() {
        var clicks by mutableIntStateOf(0)
        compose.setContent {
            val list = rememberLazyListState()
            val bar = rememberKTopAppBarState(list)
            Column {
                KTopAppBar(
                    title = { KTopAppBarDropdownTitle("Camera", expanded = false, onClick = { clicks++ }) },
                    variant = KTopAppBarVariant.Medium,
                    state = bar,
                )
                LazyColumn(Modifier.fillMaxWidth().height(400.dp).testTag("list"), state = list) {
                    items(200) { BasicText("Row $it", Modifier.height(48.dp)) }
                }
            }
        }
        compose.onAllNodesWithText("Camera").assertCountEquals(1)
        compose.onNode(isDropdown).performClick()
        compose.onNodeWithTag("list").performScrollToIndex(20)
        compose.waitForIdle()
        compose.onAllNodesWithText("Camera").assertCountEquals(1)
        compose.onNode(isDropdown).assert(isHeading()).performClick()
        compose.waitForIdle()
        assertThat(clicks).isEqualTo(2)
    }

    @Test
    fun `제목 슬롯은 RTL에서 시작 쪽인 오른쪽에 놓인다`() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.size(400.dp, 100.dp)) { KTopAppBar(title = { BasicText("Title", Modifier.testTag("t")) }, onBack = {}) }
            }
        }
        val title = compose.onNodeWithTag("t", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertThat(title.center.x).isGreaterThan(root.center.x)
    }

    private fun setViewer(
        closeIcon: KImageViewerCloseIcon = KImageViewerCloseIcon.Close,
        onAction: (Int) -> Unit = {},
        withBottom: Boolean = true,
    ): KImageViewerState {
        lateinit var state: KImageViewerState
        compose.setContent {
            state = rememberKImageViewerState()
            KineticaScaffold {
                KImageViewer(
                    state,
                    photos,
                    closeIcon = closeIcon,
                    topActions = { page ->
                        Box(Modifier.size(44.dp).viewerChrome(CircleShape)) {
                            KSelectionBadge(order = page + 1, onClick = { onAction(page) })
                        }
                    },
                    bottomBar = if (withBottom) ({ page -> BasicText("Strip $page", Modifier.testTag("strip")) }) else null,
                )
            }
        }
        compose.runOnIdle { state.open(1) }
        compose.waitForIdle()
        return state
    }

    @Test
    fun `뷰어 위 바 슬롯은 지금 페이지를 받고 아래 슬롯은 인디케이터 대신 놓인다`() {
        val pages = mutableListOf<Int>()
        setViewer(onAction = { pages += it })
        compose.onNode(hasState("Selected, number 2")).performClick()
        compose.waitForIdle()
        assertThat(pages).containsExactly(1)
        compose.onNodeWithText("Strip 1").assertExists()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertDoesNotExist()
        compose.onNodeWithContentDescription("Close").assertExists()
    }

    /** 한 번 탭은 두 번 탭 대기 시간이 지나야 알려진다. */
    private fun tapOnce() {
        compose.onRoot().performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(DOUBLE_TAP_WAIT_MS)
        ShadowLooper.idleMainLooper(DOUBLE_TAP_WAIT_MS, TimeUnit.MILLISECONDS)
        compose.waitForIdle()
    }

    @Test
    fun `한 번 탭으로 바를 숨기면 슬롯도 TalkBack과 터치에서 빠지고 다시 탭하면 돌아온다`() {
        setViewer()
        tapOnce()
        compose.onNodeWithContentDescription("Close").assertDoesNotExist()
        compose.onNodeWithTag("strip").assertDoesNotExist()
        compose.onNode(hasState("Selected, number 2")).assertDoesNotExist()
        tapOnce()
        compose.onNodeWithTag("strip").assertExists()
        compose.onNode(hasState("Selected, number 2")).assertExists()
    }

    @Test
    fun `끌어 내려 닫으면 슬롯도 함께 사라진다`() {
        val state = setViewer()
        compose.onRoot().performTouchInput { swipe(center, center.copy(y = center.y + height * 0.45f), 120) }
        compose.waitForIdle()
        assertThat(state.isVisible).isFalse()
        compose.onNodeWithTag("strip").assertDoesNotExist()
    }

    @Test
    fun `뒤로 모양 닫기 버튼은 뒤로라고 읽히고 누르면 닫힌다`() {
        val state = setViewer(closeIcon = KImageViewerCloseIcon.Back, withBottom = false)
        compose.onNodeWithContentDescription("Close").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        assertThat(state.isVisible).isFalse()
    }

    @Test
    fun `기본 뷰어는 슬롯 없이 예전처럼 닫기와 인디케이터를 놓는다`() {
        lateinit var state: KImageViewerState
        compose.setContent {
            state = rememberKImageViewerState()
            KineticaScaffold { KImageViewer(state, photos) }
        }
        compose.runOnIdle { state.open(0) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Close").assertExists()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun `순서 선택 표시는 순서와 선택 여부를 상태로 읽는다`() {
        var order by mutableStateOf<Int?>(null)
        compose.setContent {
            Column {
                KSelectionBadge(order = order, Modifier.testTag("order"))
                KSelectionBadge(selected = true, Modifier.testTag("check"))
                KSelectionBadge(order = 0, Modifier.testTag("zero"))
            }
        }
        compose.onNodeWithTag("order").assert(hasState("Not selected"))
        compose.onNodeWithTag("check").assert(hasState("Selected"))
        compose.onNodeWithTag("zero").assert(hasState("Not selected"))
        order = 3
        compose.waitForIdle()
        compose.onNodeWithTag("order").assert(hasState("Selected, number 3"))
        // 번호 글자는 상태에 이미 들어 있으므로 따로 읽히지 않는다.
        compose.onNodeWithText("3").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "ko")
    fun `한국어로는 선택됨, 3번째로 읽는다`() {
        compose.setContent { KSelectionBadge(order = 3, Modifier.testTag("b")) }
        compose.onNodeWithTag("b").assert(hasState("선택됨, 3번째"))
    }

    @Test
    fun `누를 수 있는 선택 표시는 48dp 체크박스로 읽히고 움직임 줄이기에서도 바로 바뀐다`() {
        var order by mutableStateOf<Int?>(null)
        compose.setContent {
            CompositionLocalProvider(LocalKineticaReducedMotion provides true) {
                KSelectionBadge(order = order, Modifier.testTag("b"), onClick = { order = if (order == null) 1 else null })
            }
        }
        val node = compose.onNodeWithTag("b")
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        val bounds = node.fetchSemanticsNode().size
        assertThat(bounds.width).isEqualTo(bounds.height)
        assertThat(bounds.width.toFloat()).isAtLeast(with(compose.density) { 48.dp.toPx() } - 1f)
        node.performClick()
        compose.waitForIdle()
        node.assert(hasState("Selected, number 1"))
    }

    @Test
    fun `순서 선택 표시는 밝은 사진과 어두운 사진 위 모두에서 가장자리가 보인다`() {
        compose.setContent {
            Column {
                Box(Modifier.size(40.dp).testTag("light").background(Color.White)) { KSelectionBadge(order = null) }
                Box(Modifier.size(40.dp).testTag("dark").background(Color.Black)) { KSelectionBadge(order = null) }
            }
        }
        val light = compose.onNodeWithTag("light").captureToImage().toPixelMap()
        val dark = compose.onNodeWithTag("dark").captureToImage().toPixelMap()
        // 원 테두리를 지나는 가로줄에서 바탕과 다른 픽셀이 있어야 한다.
        val y = light.height / 2
        assertThat((0 until light.width).any { light[it, y].red < 0.85f }).isTrue()
        assertThat((0 until dark.width).any { dark[it, y].red > 0.5f }).isTrue()
    }

    private companion object {
        const val DOUBLE_TAP_WAIT_MS = 500L
    }
}
