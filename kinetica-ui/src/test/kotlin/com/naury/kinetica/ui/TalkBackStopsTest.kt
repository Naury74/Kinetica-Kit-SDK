package com.naury.kinetica.ui

import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertWithMessage
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TalkBack이 쓸어 넘길 때 말할 것이 있는 노드를 건너뛰지 않는지. Compose는 자식을 묶은 노드나 말할 것이 있는 잎 노드만
 * 화면 낭독기 초점 대상으로 내보내므로, 묶지 않고 이름만 붙인 노드에 자식이 있으면 클릭할 수 없는 한 건너뛴다.
 * 키보드 포커스(focusable)만으로는 TalkBack이 멈추지 않는다(에뮬레이터 TalkBack 16에서 확인).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko")
class TalkBackStopsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun SemanticsNode.speaks(): Boolean {
        val c = config
        if (c.contains(SemanticsProperties.HideFromAccessibility)) return false
        return !c.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() ||
            !c.getOrNull(SemanticsProperties.StateDescription).isNullOrEmpty() ||
            c.contains(SemanticsProperties.ProgressBarRangeInfo) ||
            c.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.isNotBlank() }
    }

    private fun AccessibilityNodeInfo.stops() = isScreenReaderFocusable || isClickable || isLongClickable

    private fun SemanticsNode.label(): String {
        val c = config
        return listOfNotNull(
            c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(),
            c.getOrNull(SemanticsProperties.StateDescription),
            c.getOrNull(SemanticsProperties.Text)?.joinToString { it.text },
            c.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.let { "진행 ${it.current}" },
        ).joinToString(" / ")
    }

    /** [content]를 그리고, TalkBack이 멈추지도 않고 읽어 주는 조상도 없는 노드를 모아 비어 있는지 본다. */
    private fun assertNoSkipped(component: String, content: @Composable () -> Unit) {
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            content()
        }
        compose.waitForIdle()
        val provider = view.accessibilityNodeProvider
        val nodes = compose.onAllNodes(SemanticsMatcher("모든 노드") { true }, useUnmergedTree = true).fetchSemanticsNodes()
        val skipped = nodes.filter { node ->
            if (node.parent == null || !node.speaks()) return@filter false
            // 지운 semantics 아래 노드처럼 접근성 트리에 없는 노드는 TalkBack이 볼 수 없으니 뺀다.
            val info = provider.createAccessibilityNodeInfo(node.id) ?: return@filter false
            if (!info.isVisibleToUser || info.stops()) return@filter false
            // 루트는 호스트 뷰라 조상에서 뺀다.
            val readers = generateSequence(node.parent) { it.parent }.filter { it.parent != null }
            readers.none { provider.createAccessibilityNodeInfo(it.id)?.stops() == true }
        }
        assertWithMessage("$component: TalkBack이 건너뛰는 노드").that(skipped.map { "$component[${it.label()}]" }).isEmpty()
    }

    private val series = listOf(
        ChartSeries(listOf(3f, 5f, 4f), label = "매출"),
        ChartSeries(listOf(1f, 2f, 2f), Color.Green, label = "비용"),
    )
    private val slices = listOf(ChartSlice(3f, "식비", Color.Red), ChartSlice(2f, "교통", Color.Blue))
    private val labels = listOf("1월", "2월", "3월")
    private val chart = Modifier.size(300.dp, 200.dp)

    @Test
    fun `영역 차트`() = assertNoSkipped("KAreaChart") { KAreaChart(series, chart, labels) }

    @Test
    fun `막대 차트`() = assertNoSkipped("KBarChart") { KBarChart(listOf(3f, 5f, 4f), chart, labels) }

    @Test
    fun `가로 막대 차트`() = assertNoSkipped("KBarChart(가로)") {
        KBarChart(listOf(3f, 5f, 4f), chart, labels, orientation = KBarOrientation.Horizontal)
    }

    @Test
    fun `도넛 차트`() = assertNoSkipped("KDonutChart") { KDonutChart(slices, Modifier.size(200.dp)) }

    @Test
    fun `선 차트`() = assertNoSkipped("KLineChart") { KLineChart(listOf(3f, 5f, 4f), chart, labels) }

    @Test
    fun `여러 계열 선 차트`() = assertNoSkipped("KLineChart(계열)") { KLineChart(series, chart, labels) }

    @Test
    fun `여러 계열 막대 차트`() = assertNoSkipped("KMultiBarChart") { KMultiBarChart(series, chart, labels) }

    @Test
    fun `파이 차트`() = assertNoSkipped("KPieChart") { KPieChart(slices, Modifier.size(300.dp)) }

    @Test
    fun `히트맵`() = assertNoSkipped("KHeatmap") {
        KHeatmap(listOf(listOf(1f, 2f), listOf(3f, null)), rowLabels = listOf("월", "화"), columnLabels = listOf("오전", "오후"))
    }

    @Test
    fun `달력 히트맵`() = assertNoSkipped("KCalendarHeatmap") {
        val start = LocalDate.of(2026, 1, 1)
        KCalendarHeatmap(mapOf(start to 2f, start.plusDays(1) to 5f), start, start.plusDays(13))
    }

    @Test
    fun `색 선택`() = assertNoSkipped("KColorPicker") { KColorPicker(Color.Blue, {}, Modifier.size(320.dp, 600.dp)) }

    @Test
    fun `코드 입력`() = assertNoSkipped("KCodeInput") { KCodeInput("12", {}) }

    @Test
    fun `키패드와 PIN 점`() = assertNoSkipped("KKeypad") {
        androidx.compose.foundation.layout.Column {
            KKeypad({}, {}, mode = KKeypadMode.Pin)
            KPinDots(4, 2)
        }
    }

    @Test
    fun `쪽 표시와 캐러셀`() = assertNoSkipped("KPager") {
        androidx.compose.foundation.layout.Column {
            KPageIndicator(5, 1, 0f)
            KPageIndicator(5, 1, 0f, onSelect = {})
            KCarousel(3, Modifier.size(300.dp, 120.dp)) { Text("카드 $it") }
        }
    }

    @Test
    fun `지도 핀`() = assertNoSkipped("KMapPin") {
        androidx.compose.foundation.layout.Column {
            KMapPin(label = "카페")
            KMapPin(count = 3)
            KMapPin(label = "식당", onClick = {})
        }
    }

    @Test
    fun `진행 표시 변형`() = assertNoSkipped("KProgressVariants") {
        androidx.compose.foundation.layout.Column {
            KSegmentedProgressBar(0.5f, 4)
            KBufferProgressBar(0.3f, 0.6f)
            KStepProgressBar(2, 5)
        }
    }

    @Test
    fun `범위 슬라이더`() = assertNoSkipped("KRangeSlider") { KRangeSlider(0.2f..0.8f, {}) }

    @Test
    fun `슬라이더`() = assertNoSkipped("KSlider") { KSlider(0.4f, {}) }

    @Test
    fun `결과 표시`() = assertNoSkipped("KResultMark") {
        androidx.compose.foundation.layout.Column { KResult.entries.forEach { KResultMark(it) } }
    }

    @Test
    fun `쪽 넘김과 경로`() = assertNoSkipped("KPagination") {
        androidx.compose.foundation.layout.Column {
            KPaginationStyle.entries.forEach { KPagination(3, 10, {}, style = it) }
            KBreadcrumb(listOf("홈", "문서", "설정"), {})
        }
    }

    @Test
    fun `나뉜 버튼`() = assertNoSkipped("KSplitButton") { KSplitButton("저장", {}, {}) }

    @Test
    fun `서명 패드`() = assertNoSkipped("KSignaturePad") { KSignaturePad(Modifier.size(300.dp, 200.dp)) }

    @Test
    fun `단계 표시`() = assertNoSkipped("KStepIndicator") {
        androidx.compose.foundation.layout.Column {
            KStepStyle.entries.forEach { KStepIndicator(listOf("정보", "결제", "완료"), 1, style = it) }
        }
    }

    @Test
    fun `좋아요 버튼`() = assertNoSkipped("LikeButton") { LikeButton(false, {}, count = 12) }

    @Test
    fun `길게 눌러 확인과 복사 버튼`() = assertNoSkipped("MicroInteractions") {
        androidx.compose.foundation.layout.Column {
            Box(Modifier.size(120.dp, 48.dp).holdToConfirm({}, label = "길게 눌러 삭제")) { Text("삭제") }
            KCopyButton("abc", label = "주소 복사")
        }
    }

    @Test
    fun `스피드 다이얼`() = assertNoSkipped("KSpeedDial") {
        KineticaScaffold(Modifier.fillMaxSize()) {
            KSpeedDial(true, {}, listOf(KMenuItem("공유") {}, KMenuItem("삭제", destructive = true) {}), label = "빠른 동작")
        }
    }
}
