package com.naury.kinetica.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-mdpi")
class KAlertDialogDismissedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var open by mutableStateOf(true)
    private var dismissed = 0

    private val actions = listOf(
        KDialogAction("취소", KDialogActionStyle.Cancel),
        KDialogAction("확인", KDialogActionStyle.Preferred) {},
    )

    @Test
    fun `카드는 버튼으로 닫으면 사라진 뒤 한 번 부른다`() {
        show(KDialogPresentation.Card)
        compose.onNodeWithText("확인").performClick()
        assertCalledOnceAfterGone()
    }

    @Test
    fun `카드는 바깥을 누르면 사라진 뒤 한 번 부른다`() {
        show(KDialogPresentation.Card)
        compose.onRoot().performTouchInput { click(Offset(10f, 10f)) }
        assertCalledOnceAfterGone()
    }

    @Test
    fun `카드는 뒤로 가기로 닫아도 사라진 뒤 한 번 부른다`() {
        show(KDialogPresentation.Card)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertCalledOnceAfterGone()
    }

    @Test
    fun `시트는 끌어 내리면 사라진 뒤 한 번 부른다`() {
        show(KDialogPresentation.Sheet)
        compose.onNodeWithText("제목").performTouchInput {
            down(center)
            moveBy(Offset(0f, 150f))
            moveBy(Offset(0f, 150f))
            up()
        }
        assertCalledOnceAfterGone()
    }

    @Test
    fun `시트는 버튼으로 닫아도 사라진 뒤 한 번 부른다`() {
        show(KDialogPresentation.Sheet)
        compose.onNodeWithText("취소").performClick()
        assertCalledOnceAfterGone()
    }

    @Test
    fun `움직임 줄이기에서도 카드는 사라진 뒤 한 번 부른다`() {
        show(KDialogPresentation.Card, reduced = true)
        compose.onNodeWithText("확인").performClick()
        assertCalledOnceAfterGone(maxFrames = 6)
    }

    @Test
    fun `움직임 줄이기에서도 시트는 사라진 뒤 한 번 부른다`() {
        show(KDialogPresentation.Sheet, reduced = true)
        compose.onNodeWithText("확인").performClick()
        assertCalledOnceAfterGone(maxFrames = 6)
    }

    @Test
    fun `닫지 않으면 부르지 않고 처음부터 닫혀 있어도 부르지 않는다`() {
        open = false
        show(KDialogPresentation.Card)
        compose.mainClock.advanceTimeBy(1000)
        assertThat(dismissed).isEqualTo(0)
        open = true
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("제목").assertIsDisplayed()
        assertThat(dismissed).isEqualTo(0)
    }

    @Test
    fun `KSheet와 KDialog도 사라진 뒤 한 번 부른다`() {
        var sheetOpen by mutableStateOf(true)
        var dialogOpen by mutableStateOf(true)
        var sheetDone = 0
        var dialogDone = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                KSheet(sheetOpen, { sheetOpen = false }, onDismissed = { sheetDone++ }) { ThemedText("시트") }
                KDialog(dialogOpen, { dialogOpen = false }, onDismissed = { dialogDone++ }) { ThemedText("카드") }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(800)
        sheetOpen = false
        dialogOpen = false
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(800)
        assertThat(compose.onAllNodesWithText("시트").fetchSemanticsNodes()).isEmpty()
        assertThat(compose.onAllNodesWithText("카드").fetchSemanticsNodes()).isEmpty()
        assertThat(sheetDone).isEqualTo(1)
        assertThat(dialogDone).isEqualTo(1)
    }

    @Test
    fun `옅은 위험은 옅은 빨간 바탕에 빨간 글자다`() {
        showActions(listOf(KDialogAction("저장", KDialogActionStyle.Preferred), KDialogAction("버리기", KDialogActionStyle.DestructiveTonal)))
        val map = compose.onNodeWithText("버리기").captureToImage().toPixelMap()
        val fill = map[10, map.height / 2]
        // 흰 카드 위에 빨강을 옅게 깐 색이다.
        assertThat(fill.red).isGreaterThan(0.85f)
        assertThat(fill.red - fill.green).isGreaterThan(0.05f)
        assertThat(fill.green).isGreaterThan(0.6f)
        val ink = darkest(map)
        assertThat(ink.red - ink.green).isGreaterThan(0.3f)
        assertThat(ink.red - ink.blue).isGreaterThan(0.3f)
    }

    @Test
    fun `글자 버튼은 바탕 없이 회색 글자다`() {
        showActions(listOf(KDialogAction("저장", KDialogActionStyle.Preferred), KDialogAction("나중에", KDialogActionStyle.Text)))
        val map = compose.onNodeWithText("나중에").captureToImage().toPixelMap()
        val card = compose.onRoot().captureToImage().toPixelMap().let { root ->
            val title = compose.onNodeWithText("제목").fetchSemanticsNode().boundsInRoot
            root[(title.left - 4f).toInt(), title.center.y.toInt()]
        }
        val fill = map[10, map.height / 2]
        assertThat(near(fill, card)).isTrue()
        val ink = darkest(map)
        assertThat(ink.red).isLessThan(0.65f)
        assertThat(kotlin.math.abs(ink.red - ink.blue)).isLessThan(0.06f)
        assertThat(kotlin.math.abs(ink.red - ink.green)).isLessThan(0.06f)
    }

    @Test
    fun `일반 동작 하나와 보조 동작이면 일반 동작을 채운다`() {
        showActions(listOf(KDialogAction("확인"), KDialogAction("나중에", KDialogActionStyle.Text)))
        val map = compose.onNodeWithText("확인").captureToImage().toPixelMap()
        val fill = map[10, map.height / 2]
        // 채운 강조색(파랑)이다.
        assertThat(fill.blue - fill.red).isGreaterThan(0.5f)
    }

    @Test
    fun `버튼이 없으면 버튼 줄 없이 그리고 뒤로 가기로 닫힌다`() {
        showActions(emptyList())
        compose.onNodeWithText("제목").assertIsDisplayed()
        // 남은 버튼은 바깥 스크림의 닫기 하나뿐이다.
        val buttons = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).fetchSemanticsNodes()
        assertThat(buttons).hasSize(1)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertCalledOnceAfterGone()
    }

    @Test
    fun `버튼 다섯 개는 넘긴 순서대로 세로로 쌓는다`() {
        val labels = listOf("하나", "둘", "셋", "넷", "다섯")
        showActions(labels.map { KDialogAction(it) }, stacked = false)
        val bounds = labels.map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot }
        bounds.zipWithNext().forEach { (a, b) ->
            assertThat(b.top).isAtLeast(a.bottom)
            assertThat(b.left).isWithin(1f).of(a.left)
        }
    }

    @Test
    fun `keepActionOrder면 쌓을 때 취소를 맨 아래로 옮기지 않는다`() {
        val list = listOf(
            KDialogAction("취소", KDialogActionStyle.Cancel),
            KDialogAction("저장", KDialogActionStyle.Preferred),
            KDialogAction("나중에", KDialogActionStyle.Text),
        )
        showActions(list, stacked = true, keepOrder = true)
        assertThat(tops("취소", "저장", "나중에")).isInOrder()
    }

    @Test
    fun `기본값은 쌓을 때 취소를 맨 아래에 둔다`() {
        val list = listOf(
            KDialogAction("취소", KDialogActionStyle.Cancel),
            KDialogAction("저장", KDialogActionStyle.Preferred),
            KDialogAction("나중에", KDialogActionStyle.Text),
        )
        showActions(list, stacked = true)
        assertThat(tops("저장", "나중에", "취소")).isInOrder()
    }

    @Test
    fun `알파03 시그니처는 같은 JVM 이름으로 남아 있다`() {
        // Color 값 클래스 파라미터 때문에 이름 뒤에 해시가 붙는다. 컴파일된 호출자는 이 이름으로 찾는다.
        val alert = Class.forName("com.naury.kinetica.ui.KAlertDialogKt").methods.map { it.name }.filter { it.startsWith("KAlertDialog") }
        assertThat(alert).containsAtLeast("KAlertDialog-mf-eAJQ", "KAlertDialog-Vi8xdGg")
        assertThat(alert.distinct()).hasSize(3)
        val components = Class.forName("com.naury.kinetica.ui.KComponentsKt").methods
        assertThat(components.map { it.name }).contains("KDialog-uB6I_Z0")
        assertThat(components.count { it.name.startsWith("KDialog") }).isEqualTo(2)
        assertThat(components.count { it.name == "KSheet" }).isEqualTo(2)
    }

    private fun tops(vararg labels: String): List<Float> = labels.map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }

    private fun show(presentation: KDialogPresentation, reduced: Boolean = false) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalKineticaReducedMotion provides reduced) {
                Alert(presentation, actions, stacked = null, keepOrder = false)
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(800)
    }

    private fun showActions(list: List<KDialogAction>, stacked: Boolean? = null, keepOrder: Boolean = false) {
        compose.mainClock.autoAdvance = false
        compose.setContent { Alert(KDialogPresentation.Card, list, stacked, keepOrder) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(800)
    }

    @Composable
    private fun Alert(presentation: KDialogPresentation, list: List<KDialogAction>, stacked: Boolean?, keepOrder: Boolean) {
        KineticaScaffold(surface = SurfaceStyles.CleanLight) {
            KAlertDialog(
                open,
                { open = false },
                "제목",
                list,
                stacked = stacked,
                presentation = presentation,
                onDismissed = { dismissed++ },
                keepActionOrder = keepOrder,
            )
        }
    }

    /** 제목이 화면에 남아 있는 동안에는 부르지 않았고, 사라진 뒤 정확히 한 번 불렀는지 본다. */
    private fun assertCalledOnceAfterGone(maxFrames: Int = 60) {
        var frames = 0
        while (compose.onAllNodesWithText("제목").fetchSemanticsNodes().isNotEmpty() && frames < maxFrames) {
            assertThat(dismissed).isEqualTo(0)
            compose.mainClock.advanceTimeByFrame()
            frames++
        }
        assertThat(compose.onAllNodesWithText("제목").fetchSemanticsNodes()).isEmpty()
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        assertThat(dismissed).isEqualTo(1)
        compose.mainClock.advanceTimeBy(1000)
        assertThat(dismissed).isEqualTo(1)
    }

    private fun darkest(map: androidx.compose.ui.graphics.PixelMap): Color {
        var best = Color.White
        for (y in 0 until map.height) {
            for (x in 0 until map.width) {
                val c = map[x, y]
                if (c.red + c.green + c.blue < best.red + best.green + best.blue) best = c
            }
        }
        return best
    }

    private fun near(a: Color, b: Color): Boolean =
        kotlin.math.abs(a.red - b.red) < 0.03f && kotlin.math.abs(a.green - b.green) < 0.03f && kotlin.math.abs(a.blue - b.blue) < 0.03f
}
