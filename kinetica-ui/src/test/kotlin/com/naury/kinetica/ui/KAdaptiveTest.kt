package com.naury.kinetica.ui

import android.graphics.Rect
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.window.layout.FoldingFeature
import androidx.window.testing.layout.FoldingFeature
import androidx.window.testing.layout.TestWindowLayoutInfo
import androidx.window.testing.layout.WindowLayoutInfoPublisherRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@Config(sdk = [34], qualifiers = "w900dp-h900dp-mdpi")
class KAdaptiveTest {
    @get:Rule(order = 0)
    val publisher = WindowLayoutInfoPublisherRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val tabs = listOf(KTab("Home", dot), KTab("Search", dot), KTab("Library", dot))

    @Test
    fun `좁은 화면에서는 상세를 밀어 넣고 뒤로 가기로 목록에 돌아간다`() {
        var selected by mutableStateOf<String?>(null)
        compose.setContent {
            Box(Modifier.width(400.dp)) {
                KListDetail(selected, onBack = { selected = null }, list = { MailList { selected = it } }) { BasicText("detail $it") }
            }
        }
        compose.onNodeWithText("mail 3").performClick()
        compose.onNodeWithText("detail mail 3").assertIsDisplayed()
        compose.onNodeWithText("mail 3").assertDoesNotExist()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertThat(selected).isNull()
        compose.onNodeWithText("mail 3").assertIsDisplayed()
    }

    @Test
    fun `넓은 화면에서는 목록과 상세를 나란히 놓고 뒤로 가기를 가로채지 않는다`() {
        var selected by mutableStateOf<String?>("mail 1")
        compose.setContent {
            Box(Modifier.width(840.dp)) {
                KListDetail(
                    selected,
                    onBack = { selected = null },
                    emptyDetail = { BasicText("nothing") },
                    list = { MailList(Modifier.testTag("list")) { selected = it } },
                ) { BasicText("detail $it") }
            }
        }
        compose.onNodeWithTag("list").assertIsDisplayed()
        compose.onNodeWithText("detail mail 1").assertIsDisplayed()
        assertThat(compose.onNodeWithTag("list").getUnclippedBoundsInRoot().width).isEqualTo(KAdaptiveDefaults.ListPaneWidth)
        assertThat(compose.activity.onBackPressedDispatcher.hasEnabledCallbacks()).isFalse()

        compose.onNodeWithText("mail 4").performClick()
        compose.onNodeWithText("detail mail 4").assertIsDisplayed()
        compose.runOnIdle { selected = null }
        compose.onNodeWithText("nothing").assertIsDisplayed()
    }

    @Test
    fun `접고 펼쳐 폭이 바뀌어도 선택과 목록 스크롤 위치가 남는다`() {
        var width by mutableStateOf(840.dp)
        var selected by mutableStateOf<String?>(null)
        compose.setContent {
            Box(Modifier.width(width)) {
                KListDetail(selected, onBack = { selected = null }, list = { MailList(Modifier.testTag("list")) { selected = it } }) {
                    BasicText("detail $it")
                }
            }
        }
        compose.onNodeWithTag("list").performScrollToIndex(30)
        compose.onNodeWithText("mail 30").performClick()

        compose.runOnIdle { width = 400.dp }
        compose.onNodeWithText("detail mail 30").assertIsDisplayed()
        compose.runOnIdle { width = 840.dp }
        compose.onNodeWithText("detail mail 30").assertIsDisplayed()
        compose.onNodeWithText("mail 30").assertIsDisplayed()
    }

    @Test
    fun `힌지가 화면을 세로로 가르면 두 칸 경계를 힌지에 맞춘다`() {
        compose.setContent {
            KListDetail("mail 1", onBack = {}, Modifier.fillMaxSize(), list = { MailList(Modifier.testTag("list")) {} }) {
                BasicText("detail $it", Modifier.testTag("detail"))
            }
        }
        compose.waitForIdle()
        val hinge = FoldingFeature(Rect(0, 0, 900, 900), center = 500, size = 20, state = FoldingFeature.State.FLAT, orientation = VERTICAL)
        publisher.overrideWindowLayoutInfo(TestWindowLayoutInfo(listOf(hinge)))
        compose.waitForIdle()

        assertThat(compose.onNodeWithTag("list").getUnclippedBoundsInRoot().width).isEqualTo(490.dp)
        assertThat(compose.onNodeWithTag("detail").getUnclippedBoundsInRoot().left).isEqualTo(510.dp)
    }

    @Test
    fun `힌지 한쪽이 너무 좁으면 힌지를 무시하고 기본 폭을 쓴다`() {
        val fold = androidx.compose.ui.geometry.Rect(100f, 0f, 110f, 900f)
        val split = splitPanes(900f, 360f, 200f, fold, rtl = false)
        assertThat(split.start).isEqualTo(360f)
        assertThat(split.gap).isEqualTo(0f)
        // RTL에서는 목록이 오른쪽에 있으므로 힌지 오른쪽 폭이 목록 폭이 된다.
        val rtl = splitPanes(900f, 360f, 200f, androidx.compose.ui.geometry.Rect(500f, 0f, 520f, 900f), rtl = true)
        assertThat(rtl.start).isEqualTo(380f)
        assertThat(rtl.gap).isEqualTo(20f)
    }

    @Test
    fun `toString과 hashCode가 같은 다른 항목을 바꿔도 상세 상태가 섞이지 않는다`() {
        var selected by mutableStateOf<SameLooking?>(SameLooking(1))
        compose.setContent {
            KListDetail(selected, onBack = { selected = null }, list = {}) { item -> Counter("${item.id}") }
        }
        compose.onNodeWithText("1: 0").performClick()
        compose.onNodeWithText("1: 1").assertIsDisplayed()

        compose.runOnIdle { selected = SameLooking(2) }
        compose.onNodeWithText("2: 0").assertIsDisplayed()
    }

    @Test
    fun `다른 항목을 고르면 이전 항목의 상세 상태는 버리고 지금 항목 상태는 배치가 바뀌어도 남는다`() {
        var width by mutableStateOf(840.dp)
        var selected by mutableStateOf<String?>("a")
        compose.setContent {
            Box(Modifier.width(width)) {
                KListDetail(selected, onBack = { selected = null }, list = {}) { Counter(it) }
            }
        }
        compose.onNodeWithText("a: 0").performClick()
        compose.runOnIdle { selected = "b" }
        compose.onNodeWithText("b: 0").performClick()
        compose.onNodeWithText("b: 1").performClick()

        compose.runOnIdle { width = 400.dp }
        compose.onNodeWithText("b: 2").assertIsDisplayed()
        compose.runOnIdle { width = 840.dp }
        compose.onNodeWithText("b: 2").assertIsDisplayed()

        compose.runOnIdle { selected = "a" }
        compose.onNodeWithText("a: 0").assertIsDisplayed()
    }

    @Test
    fun `상세 상태와 목록 스크롤은 회전 뒤에도 남는다`() {
        val restore = StateRestorationTester(compose)
        var selected by mutableStateOf<String?>(null)
        restore.setContent {
            Box(Modifier.width(840.dp)) {
                KListDetail(selected, onBack = { selected = null }, list = { MailList(Modifier.testTag("list")) { selected = it } }) {
                    Counter(it)
                }
            }
        }
        compose.onNodeWithTag("list").performScrollToIndex(30)
        compose.onNodeWithText("mail 30").performClick()
        compose.onNodeWithText("mail 30: 0").performClick()

        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("mail 30: 1").assertIsDisplayed()
        compose.onNodeWithText("mail 30").assertIsDisplayed()
    }

    @Test
    fun `좁은 화면 목록에서 넓어져도 목록 스크롤이 남는다`() {
        var width by mutableStateOf(400.dp)
        compose.setContent {
            Box(Modifier.width(width)) {
                KListDetail<String>(null, onBack = {}, list = { MailList(Modifier.testTag("list")) {} }) {}
            }
        }
        compose.onNodeWithTag("list").performScrollToIndex(30)
        compose.runOnIdle { width = 840.dp }
        compose.onNodeWithText("mail 30").assertIsDisplayed()
    }

    @Test
    fun `레일은 선택한 탭을 알리고 누르면 바꾼다`() {
        var selected by mutableIntStateOf(0)
        compose.setContent { KNavigationRail(tabs, selected, onSelect = { selected = it }, header = { BasicText("logo") }) }
        compose.onNodeWithText("Home").assertIsSelected()
        compose.onNodeWithText("Library").assertIsNotSelected().performClick()
        assertThat(selected).isEqualTo(2)
        compose.onNodeWithText("Library").assertIsSelected()
        compose.onNodeWithText("logo").assertIsDisplayed()
        val tabNodes = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).fetchSemanticsNodes()
        assertThat(tabNodes).hasSize(3)
    }

    @Test
    fun `폭에 따라 탭 바와 레일을 바꾸고 내용 상태는 유지한다`() {
        var width by mutableStateOf(400.dp)
        compose.setContent {
            Box(Modifier.width(width).height(800.dp)) {
                KAdaptiveNavigation(tabs, 0, onSelect = {}) { padding ->
                    var count by remember { mutableIntStateOf(0) }
                    BasicText("count $count ${padding.calculateBottomPadding().value > 0f}", Modifier.clickable { count++ })
                }
            }
        }
        compose.onNodeWithText("count 0 true").performClick()
        assertThat(compose.onNodeWithText("Home").getUnclippedBoundsInRoot().top).isGreaterThan(600.dp)

        compose.runOnIdle { width = 840.dp }
        compose.onNodeWithText("count 1 false").assertIsDisplayed()
        val home = compose.onNodeWithText("Home").getUnclippedBoundsInRoot()
        assertThat(home.left).isLessThan(100.dp)
        assertThat(home.top).isLessThan(600.dp)
    }

    @Test
    fun `떠 있는 탭 바는 아래로 스크롤하면 접히고 위로 스크롤하거나 누르면 펼쳐진다`() {
        val state = KFloatingTabBarState(initiallyCollapsed = false)
        compose.setContent {
            Box(Modifier.width(400.dp).height(800.dp)) {
                LazyColumn(Modifier.fillMaxSize().nestedScroll(state.nestedScrollConnection).testTag("feed")) {
                    items(60) { BasicText("row $it", Modifier.height(48.dp)) }
                }
                KFloatingTabBar(
                    tabs,
                    1,
                    onSelect = {},
                    Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                    state = state,
                    accessory = { BasicText("now playing", Modifier.testTag("accessory")) },
                )
            }
        }
        val before = compose.onNodeWithTag("accessory", useUnmergedTree = true).getUnclippedBoundsInRoot()
        compose.onNodeWithTag("feed").performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertThat(state.isCollapsed).isTrue()
        val after = compose.onNodeWithTag("accessory", useUnmergedTree = true).getUnclippedBoundsInRoot()
        // 접히면 액세서리는 캡슐 옆으로 내려온다.
        assertThat(after.top).isGreaterThan(before.top)
        assertThat(after.left).isGreaterThan(before.left)

        compose.onNodeWithTag("feed").performTouchInput { swipeDown() }
        compose.waitForIdle()
        assertThat(state.isCollapsed).isFalse()

        state.collapse()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Search").performClick()
        assertThat(state.isCollapsed).isFalse()
    }

    @Test
    fun `접힌 탭 바도 화면 읽기에서 모든 탭으로 갈 수 있다`() {
        var selected by mutableIntStateOf(1)
        val state = KFloatingTabBarState(initiallyCollapsed = true)
        compose.setContent { KFloatingTabBar(tabs, selected, onSelect = { selected = it }, state = state) }
        val tabRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        assertThat(compose.onAllNodes(tabRole).fetchSemanticsNodes()).isEmpty()
        compose.onNodeWithContentDescription("Search")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Tab bar collapsed"))
            .performCustomAccessibilityActionWithLabel("Go to Library")
        assertThat(selected).isEqualTo(2)
        assertThat(state.isCollapsed).isTrue()

        state.expand()
        compose.waitForIdle()
        assertThat(compose.onAllNodes(tabRole).fetchSemanticsNodes()).hasSize(3)
    }

    private companion object {
        val VERTICAL = FoldingFeature.Orientation.VERTICAL

        val dot: ImageVector = ImageVector.Builder("dot", 24.dp, 24.dp, 24f, 24f).path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 4f)
            arcToRelative(8f, 8f, 0f, true, true, 0f, 16f)
            arcToRelative(8f, 8f, 0f, true, true, 0f, -16f)
            close()
        }.build()
    }
}

@androidx.compose.runtime.Composable
private fun MailList(modifier: Modifier = Modifier, onOpen: (String) -> Unit) {
    LazyColumn(modifier.fillMaxSize(), rememberLazyListState()) {
        items(60) { index -> BasicText("mail $index", Modifier.height(40.dp).clickable { onOpen("mail $index") }) }
    }
}

/** toString과 hashCode는 모두 같지만 equals로는 다른 항목. */
private class SameLooking(val id: Int) {
    override fun equals(other: Any?): Boolean = other is SameLooking && other.id == id

    override fun hashCode(): Int = 0

    override fun toString(): String = "item"
}

@androidx.compose.runtime.Composable
private fun Counter(name: String) {
    var count by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    BasicText("$name: $count", Modifier.clickable { count++ })
}
