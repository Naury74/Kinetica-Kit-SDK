package com.naury.kinetica.ui

import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/** 호스트 앱이 자기 화면과 뷰를 넣어 부를 때의 높이, 스크롤, 배치, 상태 처리를 확인한다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class HostIntegrationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private enum class Body { Lazy, Scroll, Long, Short, Fill, Weight }

    @Composable
    private fun ColumnScope.HostBody(body: Body) {
        when (body) {
            Body.Lazy -> LazyColumn(Modifier.testTag("body")) { items(100) { Row(it) } }

            Body.Scroll -> Column(Modifier.testTag("body").verticalScroll(rememberScrollState())) { repeat(100) { Row(it) } }

            Body.Long -> Column(Modifier.testTag("body")) { repeat(100) { Row(it) } }

            Body.Short -> BasicText("짧은 내용", Modifier.testTag("body"))

            Body.Fill -> Box(Modifier.testTag("body").fillMaxSize())

            Body.Weight -> {
                Box(Modifier.testTag("body").weight(1f).fillMaxWidth())
                BasicText("아래 버튼")
            }
        }
    }

    @Composable
    private fun Row(index: Int) = BasicText("항목 $index", Modifier.height(40.dp))

    private fun heightOf(tag: String): Dp = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot().let { it.bottom - it.top }

    private fun screenHeight(): Dp = heightOf("screen")

    /** 시트 최대 높이: 화면 높이에서 위쪽 간격을 뺀 값(Robolectric은 상태 표시줄 인셋이 0이다). */
    private fun sheetMax(): Dp = screenHeight() - GlassSheetDefaults.TopGap

    private fun checkSheetBodies(hosted: Boolean) {
        var body by mutableStateOf(Body.Lazy)
        compose.setContent {
            Screen(hosted) { GlassSheet(true, {}, Modifier.testTag("panel")) { HostBody(body) } }
        }
        Body.entries.forEach { next ->
            body = next
            compose.waitForIdle()
            val height = heightOf("panel")
            // 대화 상자 창은 dp 반올림 때문에 1dp까지 차이가 날 수 있다.
            assertThat(height.value).isAtMost(sheetMax().value + 1.5f)
            when (next) {
                Body.Short -> assertThat(height.value).isLessThan(TALL_SHORT_SHEET)
                else -> assertThat(height.value).isAtLeast(sheetMax().value - 1.5f)
            }
        }
        body = Body.Lazy
        compose.waitForIdle()
        compose.onNodeWithTag("body").performScrollToIndex(99)
        compose.onNodeWithText("항목 99").assertIsDisplayed()
        body = Body.Scroll
        compose.waitForIdle()
        compose.onNodeWithText("항목 99").performScrollTo().assertIsDisplayed()
    }

    @Composable
    private fun Screen(hosted: Boolean, content: @Composable () -> Unit) {
        if (hosted) {
            GlassScaffold(Modifier.fillMaxSize().testTag("screen")) { content() }
        } else {
            Box(Modifier.fillMaxSize().testTag("screen")) { content() }
        }
    }

    @Test
    fun `시트는 스크롤 목록, 긴 내용, 꽉 채우기, weight를 넣어도 최대 높이 안에 두고 짧으면 내용에 맞춘다`() {
        checkSheetBodies(hosted = true)
    }

    @Test
    fun `GlassScaffold 밖에서 연 시트도 같은 높이 정책을 따른다`() {
        checkSheetBodies(hosted = false)
        compose.onAllNodes(isDialog()).assertCountEquals(1)
    }

    @Test
    fun `다이얼로그는 긴 내용에서도 화면을 넘지 않고 안쪽 목록이 스크롤된다`() {
        var body by mutableStateOf(Body.Lazy)
        compose.setContent {
            Screen(hosted = true) { GlassDialog(true, {}, Modifier.testTag("panel")) { HostBody(body) } }
        }
        Body.entries.forEach { next ->
            body = next
            compose.waitForIdle()
            val height = heightOf("panel")
            assertThat(height.value).isAtMost(screenHeight().value - DIALOG_MARGIN * 2 + 0.5f)
            if (next == Body.Short) assertThat(height.value).isLessThan(TALL_SHORT_SHEET)
        }
        body = Body.Lazy
        compose.waitForIdle()
        compose.onNodeWithTag("body").performScrollToIndex(99)
        compose.onNodeWithText("항목 99").assertIsDisplayed()
    }

    @Test
    fun `여러 높이 시트, 서랍, 펼친 카드, 미니 플레이어 슬롯에 목록과 꽉 채우기를 넣어도 측정이 깨지지 않는다`() {
        compose.setContent {
            Box(Modifier.fillMaxSize().testTag("root")) { Nested() }
        }
        compose.waitForIdle()
        listOf("drawer", "detent", "player", "expand").forEach { tag ->
            assertThat(heightOf(tag).value).isAtMost(heightOf("root").value + 0.5f)
            assertThat(heightOf(tag).value).isGreaterThan(0f)
        }
        // 서랍과 펼친 카드가 모달로 열려 있어 그 뒤는 TalkBack 트리에서 빠지므로 병합하지 않은 트리에서 찾는다.
        compose.onNodeWithTag("detent", useUnmergedTree = true).performScrollToIndex(99)
        compose.onNodeWithTag("player", useUnmergedTree = true).performScrollToIndex(99)
        compose.onNodeWithTag("expand", useUnmergedTree = true).performScrollToIndex(99)
    }

    @Composable
    private fun Nested() {
        val expand = rememberGlassExpandState()
        val drawer = rememberGlassDrawerState(initiallyOpen = true)
        GlassDrawer(drawerContent = { LazyColumn(Modifier.testTag("drawer")) { items(100) { Row(it) } } }, state = drawer) {
            GlassScaffold(Modifier.fillMaxSize().testTag("screen")) {
                GlassDetentSheet(rememberGlassDetentSheetState(initialDetent = Detent.Full)) {
                    LazyColumn(Modifier.fillMaxSize().testTag("detent")) { items(100) { Row(it) } }
                }
                GlassMiniPlayer(
                    rememberGlassMiniPlayerState(initiallyExpanded = true),
                    collapsed = { BasicText("막대") },
                    expanded = {
                        Column(Modifier.fillMaxSize()) {
                            LazyColumn(Modifier.weight(1f).testTag("player")) { items(100) { Row(it) } }
                            BasicText("재생")
                        }
                    },
                )
                GlassExpandHost(expand) { LazyColumn(Modifier.fillMaxSize().testTag("expand")) { items(100) { Row(it) } } }
                androidx.compose.runtime.LaunchedEffect(Unit) { expand.expand("card") }
            }
        }
    }

    @Test
    fun `GlassScaffold 밖 Column 안에서 불러도 주변 배치가 그대로이고 알림은 내용 크기 창에 뜬다`() {
        val toast = GlassToastState()
        val snackbar = GlassSnackbarState()
        val island = GlassIslandState()
        var open by mutableStateOf(false)
        lateinit var scope: CoroutineScope
        compose.setContent {
            scope = rememberCoroutineScope()
            Column(Modifier.fillMaxSize().testTag("screen"), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                BasicText("위", Modifier.testTag("top"))
                GlassSheet(open, { open = false }) { BasicText("시트 안") }
                GlassDialog(open, { open = false }) { BasicText("다이얼로그 안") }
                GlassToastHost(toast)
                GlassSnackbarHost(snackbar)
                GlassIslandHost(island)
                BasicText("아래", Modifier.testTag("bottom").glassContextMenu(listOf(GlassMenuItem("복사") {})))
            }
        }
        val before = compose.onNodeWithTag("bottom").getBoundsInRoot()
        open = true
        compose.mainClock.autoAdvance = false
        scope.launch { toast.show("토스트", durationMs = 5_000) }
        scope.launch { snackbar.show("스낵바", durationMs = 5_000) }
        scope.launch { island.show(GlassIslandContent("아일랜드", "내용"), durationMs = 5_000) }
        compose.mainClock.advanceTimeBy(1_000)
        assertThat(compose.onNodeWithTag("bottom").getBoundsInRoot()).isEqualTo(before)
        compose.onNodeWithText("시트 안").assertExists()
        compose.onNodeWithText("스낵바").assertExists()
        compose.onNodeWithText("토스트").assertExists()
        // 시트와 다이얼로그는 대화 상자 창, 알림 셋은 내용 크기의 팝업 창에 뜬다.
        compose.onAllNodes(isDialog()).assertCountEquals(2)
        val popups = compose.onAllNodes(isPopup()).fetchSemanticsNodes()
        assertThat(popups).hasSize(3)
        popups.forEach {
            assertThat(it.boundsInRoot.height).isLessThan(
                compose.onNodeWithTag("screen").getBoundsInRoot().let { b ->
                    (b.bottom - b.top).value
                } * compose.density.density / 2,
            )
        }

        open = false
        compose.mainClock.advanceTimeBy(6_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.onAllNodes(isPopup()).assertCountEquals(0)
    }

    @Test
    fun `호스트 modifier의 testTag와 padding이 패널에 그대로 들어간다`() {
        compose.setContent {
            Screen(hosted = true) {
                GlassSheet(true, {}, Modifier.padding(horizontal = 20.dp).testTag("sheet")) { BasicText("시트") }
            }
        }
        val sheet = compose.onNodeWithTag("sheet").getBoundsInRoot()
        assertThat((sheet.left).value).isWithin(0.5f).of(20f)
        assertThat((compose.onNodeWithTag("screen").getBoundsInRoot().right - sheet.right).value).isWithin(0.5f).of(20f)
    }

    @Test
    fun `빠르게 열고 닫거나 컴포지션에서 빠져도 오버레이가 남지 않는다`() {
        var open by mutableStateOf(false)
        var present by mutableStateOf(true)
        compose.setContent {
            Screen(hosted = true) {
                if (present) {
                    GlassSheet(open, { open = false }) { BasicText("시트 안") }
                    GlassDialog(open, { open = false }) { BasicText("다이얼로그 안") }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        repeat(TOGGLES) {
            open = !open
            compose.mainClock.advanceTimeBy(16)
        }
        open = true
        compose.mainClock.advanceTimeBy(100)
        present = false
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText("시트 안").assertDoesNotExist()
        compose.onNodeWithText("다이얼로그 안").assertDoesNotExist()

        present = true
        open = false
        compose.waitForIdle()
        compose.onNodeWithText("시트 안").assertDoesNotExist()
    }

    @Test
    fun `밖에서 빠르게 열고 닫아도 대화 상자 창이 남지 않는다`() {
        var open by mutableStateOf(false)
        compose.setContent {
            Screen(hosted = false) { GlassSheet(open, { open = false }) { BasicText("시트 안") } }
        }
        compose.mainClock.autoAdvance = false
        repeat(TOGGLES) {
            open = !open
            compose.mainClock.advanceTimeBy(16)
        }
        open = false
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
    }

    @Test
    fun `화면을 다시 만든 뒤에도 열린 시트가 하나만 보인다`() {
        val restore = StateRestorationTester(compose)
        restore.setContent {
            var open by rememberSaveable { mutableStateOf(true) }
            GlassScaffold(Modifier.fillMaxSize()) { GlassSheet(open, { open = false }) { BasicText("시트 안") } }
        }
        compose.onAllNodesWithText("시트 안").assertCountEquals(1)
        restore.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.onAllNodesWithText("시트 안").assertCountEquals(1)
    }

    @Test
    fun `뒤로 가기는 가장 안쪽 BackHandler부터 받는다`() {
        checkBackOrder(hosted = true)
    }

    @Test
    fun `밖에서 연 시트도 뒤로 가기를 안쪽부터 받는다`() {
        checkBackOrder(hosted = false)
    }

    private fun checkBackOrder(hosted: Boolean) {
        val calls = mutableListOf<String>()
        var open by mutableStateOf(true)
        var inner by mutableStateOf(true)
        compose.setContent {
            BackHandler { calls += "화면" }
            Screen(hosted) {
                GlassSheet(open, {
                    calls += "시트"
                    open = false
                }) {
                    BackHandler(enabled = inner) {
                        calls += "시트 안"
                        inner = false
                    }
                    BasicText("시트 안")
                }
            }
        }
        compose.waitForIdle()
        repeat(3) {
            pressBack(hosted)
            compose.waitForIdle()
        }
        assertThat(calls).containsExactly("시트 안", "시트", "화면").inOrder()
    }

    /** 대화 상자 창이 열려 있으면 그 창이 뒤로 가기를 받는다. 실제 기기처럼 맨 위 창에 보낸다. */
    private fun pressBack(hosted: Boolean) {
        val dialog = ShadowDialog.getLatestDialog() as? ComponentDialog
        if (!hosted && dialog?.isShowing == true) {
            dialog.onBackPressedDispatcher.onBackPressed()
        } else {
            compose.activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    @Test
    fun `밖에서 띄운 알림 창은 호스트 화면에서 인셋을 뺀 영역의 위아래 가운데에 놓인다`() {
        val area = EdgeArea().apply {
            anchor = Offset(0f, 100f)
            root = IntRect(0, 0, 360, 640)
            insets = IntRect(0, 24, 0, 48)
        }
        val anchor = IntRect(0, 100, 360, 100)
        val size = IntSize(200, 80)
        assertThat(EdgePosition(area, top = true, room = 16).calculatePosition(anchor, IntSize(360, 640), LayoutDirection.Ltr, size))
            .isEqualTo(IntOffset(80, 8))
        assertThat(EdgePosition(area, top = false, room = 16).calculatePosition(anchor, IntSize(360, 640), LayoutDirection.Ltr, size))
            .isEqualTo(IntOffset(80, 528))
        // 대화 상자 안에서 띄우면 팝업은 화면 좌표를 받으므로 그 차이만큼 옮긴다.
        assertThat(
            EdgePosition(
                area,
                top = true,
                room = 16,
            ).calculatePosition(anchor.translate(10, 50), IntSize(360, 640), LayoutDirection.Ltr, size),
        )
            .isEqualTo(IntOffset(90, 58))
        // edge-to-edge를 쓰지 않으면 인셋은 0이고 화면이 상태 표시줄 아래에서 시작한다. 결과는 같다.
        area.root = IntRect(0, 24, 360, 592)
        area.insets = IntRect.Zero
        assertThat(EdgePosition(area, top = true, room = 16).calculatePosition(anchor, IntSize(360, 640), LayoutDirection.Ltr, size))
            .isEqualTo(IntOffset(80, 8))
        assertThat(EdgePosition(area, top = false, room = 16).calculatePosition(anchor, IntSize(360, 640), LayoutDirection.Ltr, size))
            .isEqualTo(IntOffset(80, 528))
    }

    private companion object {
        const val TALL_SHORT_SHEET = 200f
        const val DIALOG_MARGIN = 28f
        const val TOGGLES = 9
    }
}
