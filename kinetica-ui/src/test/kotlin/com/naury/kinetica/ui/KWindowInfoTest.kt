package com.naury.kinetica.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.DpSize
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
@Config(sdk = [34], qualifiers = "w900dp-h900dp-mdpi")
class KWindowInfoTest {
    @get:Rule(order = 0)
    val publisher = WindowLayoutInfoPublisherRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `폭 구간과 열 수, 읽기 폭 여백을 계산한다`() {
        assertThat(KWindowLayout(400.dp, 800.dp).widthClass).isEqualTo(KWindowWidth.Compact)
        assertThat(KWindowLayout(700.dp, 800.dp).widthClass).isEqualTo(KWindowWidth.Medium)
        assertThat(KWindowLayout(1280.dp, 800.dp).widthClass).isEqualTo(KWindowWidth.Expanded)
        assertThat(KWindowLayout(800.dp, 400.dp).isShort).isTrue()

        val wide = KWindowLayout(1000.dp, 800.dp)
        assertThat(wide.columns(320.dp)).isEqualTo(3)
        assertThat(wide.columns(320.dp, maxColumns = 2)).isEqualTo(2)
        assertThat(wide.columns(320.dp, spacing = 40.dp)).isEqualTo(2)
        assertThat(KWindowLayout(300.dp, 800.dp).columns(320.dp)).isEqualTo(1)

        assertThat(KWindowDefaults.sidePadding(1000.dp)).isEqualTo(180.dp)
        assertThat(KWindowDefaults.sidePadding(400.dp)).isEqualTo(16.dp)
    }

    @Test
    fun `반쯤 접은 가로 힌지는 탁자 모드로 알리고 위아래 칸을 힌지에 맞춘다`() {
        lateinit var window: KWindowLayout
        compose.setContent {
            window = rememberKWindowLayout()
            KTabletopLayout(
                Modifier.fillMaxSize(),
                top = { Box(Modifier.fillMaxSize().testTag("top")) },
                bottom = { Box(Modifier.fillMaxSize().testTag("bottom")) },
            )
        }
        compose.waitForIdle()
        assertThat(window.posture).isEqualTo(KPosture.Flat)
        assertThat(window.hinge).isNull()

        publisher.overrideWindowLayoutInfo(TestWindowLayoutInfo(listOf(halfOpened(FoldingFeature.Orientation.HORIZONTAL))))
        compose.waitForIdle()

        assertThat(window.posture).isEqualTo(KPosture.Tabletop)
        assertThat(window.hinge).isNotNull()
        val top = compose.onNodeWithTag("top").getUnclippedBoundsInRoot()
        val bottom = compose.onNodeWithTag("bottom").getUnclippedBoundsInRoot()
        assertThat(top.bottom).isEqualTo(window.hinge!!.top.dp)
        assertThat(bottom.top).isEqualTo(window.hinge!!.bottom.dp)
    }

    @Test
    fun `탁자 모드가 아니면 위 칸은 내용 높이, 아래 칸은 남은 높이를 쓴다`() {
        compose.setContent {
            KTabletopLayout(
                Modifier.fillMaxSize(),
                top = { BasicText("video", Modifier.testTag("top")) },
                bottom = { Box(Modifier.fillMaxSize().testTag("bottom")) },
            )
        }
        val top = compose.onNodeWithTag("top").getUnclippedBoundsInRoot()
        val bottom = compose.onNodeWithTag("bottom").getUnclippedBoundsInRoot()
        assertThat(bottom.top).isEqualTo(top.bottom)
        assertThat(bottom.bottom).isEqualTo(900.dp)
    }

    @Test
    fun `책 모드에서 다이얼로그는 누른 쪽 칸 가운데에 뜬다`() {
        compose.setContent {
            KineticaScaffold(Modifier.fillMaxSize()) {
                KDialog(true, onDismissRequest = {}, origin = Offset(800f, 400f)) { BasicText("dialog", Modifier.testTag("dialog")) }
            }
        }
        compose.waitForIdle()
        publisher.overrideWindowLayoutInfo(TestWindowLayoutInfo(listOf(halfOpened(FoldingFeature.Orientation.VERTICAL))))
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        val text = compose.onNodeWithTag("dialog").getUnclippedBoundsInRoot()
        assertThat(text.left).isAtLeast(460.dp)
    }

    @Test
    fun `탁자 모드의 시트는 아래에 그대로 두고 책 모드의 시트는 시작 쪽 칸에 둔다`() {
        compose.setContent {
            KineticaScaffold(Modifier.fillMaxSize()) {
                KSheet(true, onDismissRequest = {}) { BasicText("sheet", Modifier.testTag("sheet")) }
            }
        }
        compose.waitForIdle()
        publisher.overrideWindowLayoutInfo(TestWindowLayoutInfo(listOf(halfOpened(FoldingFeature.Orientation.HORIZONTAL))))
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertThat(compose.onNodeWithTag("sheet").getUnclippedBoundsInRoot().top).isAtLeast(460.dp)

        publisher.overrideWindowLayoutInfo(TestWindowLayoutInfo(listOf(halfOpened(FoldingFeature.Orientation.VERTICAL))))
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertThat(compose.onNodeWithTag("sheet").getUnclippedBoundsInRoot().right).isAtMost(440.dp)
    }

    @Test
    fun `접힌 선 한쪽 칸을 고른다`() {
        val horizontal = Rect(0f, 440f, 900f, 460f)
        assertThat(hingeFreeArea(Size(900f, 900f), horizontal, null, 200f, rtl = false)).isEqualTo(Rect(0f, 0f, 900f, 440f))
        assertThat(hingeFreeArea(Size(900f, 900f), horizontal, Offset(10f, 700f), 200f, rtl = false)).isEqualTo(Rect(0f, 460f, 900f, 900f))

        val vertical = Rect(440f, 0f, 460f, 900f)
        assertThat(hingeFreeArea(Size(900f, 900f), vertical, null, 200f, rtl = false)).isEqualTo(Rect(0f, 0f, 440f, 900f))
        assertThat(hingeFreeArea(Size(900f, 900f), vertical, null, 200f, rtl = true)).isEqualTo(Rect(460f, 0f, 900f, 900f))

        // 고른 칸이 너무 좁으면 반대쪽, 둘 다 좁거나 접힌 선이 밖에 있으면 전체를 쓴다.
        val nearTop = Rect(0f, 100f, 900f, 110f)
        assertThat(hingeFreeArea(Size(900f, 900f), nearTop, null, 200f, rtl = false)).isEqualTo(Rect(0f, 110f, 900f, 900f))
        assertThat(hingeFreeArea(Size(300f, 300f), Rect(0f, 140f, 300f, 160f), null, 200f, rtl = false)).isEqualTo(Rect(0f, 0f, 300f, 300f))
        assertThat(hingeFreeArea(Size(900f, 900f), Rect(0f, 950f, 900f, 960f), null, 200f, rtl = false)).isEqualTo(Rect(0f, 0f, 900f, 900f))
        assertThat(tabletopSplit(900f, 100f, 110f, 200f)).isNull()
    }

    @Test
    fun `펼친 플레이어는 넓은 화면에서 나란히, 탁자 모드에서 접힌 선 위아래로 놓는다`() {
        fun layout(width: Float, height: Float, fold: PlayerFold? = null) = fullPlayerLayout(
            width,
            Rect(0f, 0f, width, height),
            hasArtwork = true,
            fold = fold,
            PlayerMetrics(
                small = 40f,
                handle = 44f,
                gap = 28f,
                side = 32f,
                artMax = 420f,
                contentMax = 560f,
                wideMin = 600f,
                minPane = 200f,
                pairGap = 48f,
            ),
        )

        val phone = layout(400f, 800f)
        assertThat(phone.art.top).isEqualTo(44f)
        assertThat(phone.content.top).isEqualTo(phone.art.bottom + 28f)
        assertThat(phone.content.width).isEqualTo(400f)

        val tabletPortrait = layout(800f, 1280f)
        assertThat(tabletPortrait.content.width).isEqualTo(560f)
        assertThat(tabletPortrait.content.left).isEqualTo(120f)

        // 접힌 선이 없으면 아트와 내용을 48 간격으로 붙인 묶음이 가운데에 온다.
        val landscape = layout(1280f, 800f)
        assertThat(landscape.content.left - landscape.art.right).isEqualTo(48f)
        assertThat(landscape.content.width).isEqualTo(560f)
        assertThat(landscape.art.left).isEqualTo(1280f - landscape.content.right)
        assertThat(landscape.content.top).isEqualTo(landscape.art.top)

        // 폴더블 안쪽 화면처럼 폭이 빠듯하면 아트와 내용이 남은 폭을 나눠 바깥 여백이 side로 같다.
        val unfolded = layout(840f, 860f)
        assertThat(unfolded.art.left).isEqualTo(32f)
        assertThat(unfolded.content.right).isEqualTo(808f)
        assertThat(unfolded.content.left - unfolded.art.right).isEqualTo(48f)

        val tabletop = layout(900f, 880f, PlayerFold(Rect(0f, 440f, 900f, 460f), KPosture.Tabletop))
        assertThat(tabletop.art.bottom).isAtMost(440f)
        assertThat(tabletop.content.top).isAtLeast(460f)

        val book = layout(900f, 880f, PlayerFold(Rect(500f, 0f, 520f, 880f), KPosture.Book))
        assertThat(book.art.right).isAtMost(500f)
        assertThat(book.content.left).isAtLeast(520f)
        assertThat(book.art.center.x).isEqualTo(250f)
    }

    @Test
    fun `좁은 폭과 넓은 폭을 오가도 저장 상태와 스크롤 위치가 남는다`() {
        var size by mutableStateOf(DpSize(400.dp, 800.dp))
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
                var count by rememberSaveable { mutableStateOf(0) }
                val list = rememberLazyListState()
                Column(Modifier.fillMaxSize()) {
                    BasicText("count $count", Modifier.kReadableWidth().testTag("count"))
                    KButton("plus", onClick = { count++ })
                    LazyColumn(Modifier.weight(1f).testTag("list"), state = list) {
                        items(50) { BasicText("row $it") }
                    }
                }
            }
        }
        compose.onNodeWithText("plus").performClick()
        compose.onNodeWithTag("list").performScrollToIndex(30)
        size = DpSize(880.dp, 880.dp)
        compose.waitForIdle()

        compose.onNodeWithText("count 1").assertIsDisplayed()
        compose.onNodeWithText("row 30").assertIsDisplayed()
        assertThat(compose.onNodeWithTag("count").getUnclippedBoundsInRoot().width).isEqualTo(KWindowDefaults.ReadingMaxWidth)
    }

    private fun halfOpened(orientation: FoldingFeature.Orientation) = FoldingFeature(
        android.graphics.Rect(0, 0, 900, 900),
        center = 450,
        size = 20,
        state = FoldingFeature.State.HALF_OPENED,
        orientation = orientation,
    )
}
