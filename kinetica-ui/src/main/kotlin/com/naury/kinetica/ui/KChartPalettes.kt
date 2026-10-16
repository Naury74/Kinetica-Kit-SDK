package com.naury.kinetica.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * 차트 색 팔레트. 색각 이상(적록, 청황)이 있어도 이웃한 색끼리 구별되도록 순서까지 맞춰 고른 값이다.
 * 밝은 표면과 어두운 표면은 같은 색상을 밝기만 따로 골랐다. 어두운 표면에서 밝은 팔레트를 쓰면 일부 색이 바탕에 묻힌다.
 *
 * 계열 색은 [categorical]의 앞에서부터 차례로 쓴다. 돌려 쓰지 않는 것이 원칙이라, 계열이 8개를 넘으면 작은 것을 '기타'로 묶는다.
 * 산점도처럼 모든 계열이 서로 맞닿을 수 있는 차트는 앞의 3개까지만 쓴다. 색만으로 구별하지 않도록 범례나 이름을 함께 둔다.
 *
 * ```kotlin
 * val colors = KChartPalettes.categorical(KineticaTheme.surface)
 * KAreaChart(listOf(ChartSeries(app, colors[0], "앱"), ChartSeries(web, colors[1], "웹")))
 * ```
 */
public object KChartPalettes {
    /** 밝은 표면용 계열 색 8개. 파랑, 주황, 청록, 노랑, 자홍, 초록, 보라, 빨강 순서다. */
    public val Categorical: List<Color> = listOf(
        Color(0xFF2A78D6),
        Color(0xFFEB6834),
        Color(0xFF1BAF7A),
        Color(0xFFEDA100),
        Color(0xFFE87BA4),
        Color(0xFF008300),
        Color(0xFF6250D6),
        Color(0xFFE34948),
    )

    /** 어두운 표면용 계열 색 8개. [Categorical]과 같은 색상과 순서다. */
    public val CategoricalDark: List<Color> = listOf(
        Color(0xFF3987E5),
        Color(0xFFD95926),
        Color(0xFF199E70),
        Color(0xFFC98500),
        Color(0xFFD55181),
        Color(0xFF008300),
        Color(0xFF9085E9),
        Color(0xFFE66767),
    )

    /** 크기를 나타내는 한 색상(파랑) 단계 13개. 앞이 옅고 뒤가 짙다. */
    public val SequentialBlue: List<Color> = listOf(
        Color(0xFFCDE2FB),
        Color(0xFFB7D3F6),
        Color(0xFF9EC5F4),
        Color(0xFF86B6EF),
        Color(0xFF6DA7EC),
        Color(0xFF5598E7),
        Color(0xFF3987E5),
        Color(0xFF2A78D6),
        Color(0xFF256ABF),
        Color(0xFF1C5CAB),
        Color(0xFF184F95),
        Color(0xFF104281),
        Color(0xFF0D366B),
    )

    /** 상태 색: 좋음 */
    public val Good: Color = Color(0xFF0CA30C)

    /** 상태 색: 주의 */
    public val Warning: Color = Color(0xFFFAB219)

    /** 상태 색: 심각 */
    public val Serious: Color = Color(0xFFEC835A)

    /** 상태 색: 위험 */
    public val Critical: Color = Color(0xFFD03B3B)

    /** [surface] 밝기에 맞는 계열 색 8개 */
    @JvmStatic
    public fun categorical(surface: SurfaceStyle): List<Color> = if (surface.isDark) CategoricalDark else Categorical

    /**
     * [surface]에 맞춘 크기 단계 색 [levels]개. 첫 색이 가장 작은 값이다. 밝은 표면은 옅은 색에서 짙은 색으로,
     * 어두운 표면은 바탕에 가까운 짙은 색에서 밝은 색으로 올라가 작은 값이 바탕 쪽으로 물러난다.
     * [levels]가 2..[SequentialBlue] 크기 밖이면 [IllegalArgumentException]을 던진다.
     */
    @JvmStatic
    public fun sequential(surface: SurfaceStyle, levels: Int = DEFAULT_LEVELS): List<Color> {
        require(levels in 2..SequentialBlue.size) { "levels는 2..${SequentialBlue.size}여야 합니다." }
        val from = if (surface.isDark) DARK_LOW else LIGHT_LOW
        val to = if (surface.isDark) DARK_HIGH else LIGHT_HIGH
        return List(levels) { SequentialBlue[(from + (to - from) * it / (levels - 1f)).roundToInt()] }
    }

    private const val DEFAULT_LEVELS = 5
    private const val LIGHT_LOW = 1
    private const val LIGHT_HIGH = 11
    private const val DARK_LOW = 11
    private const val DARK_HIGH = 3
}

/** 지금 표면에 맞는 계열 색. 차트 기본값으로 쓴다. */
@Composable
@ReadOnlyComposable
internal fun defaultChartColors(): List<Color> = KChartPalettes.categorical(KineticaTheme.surface)

/** TalkBack이 표로 읽도록 행과 열 수를 알린다. */
internal fun Modifier.chartTable(rows: Int, columns: Int): Modifier = semantics { collectionInfo = CollectionInfo(rows, columns) }

/** 표의 한 칸. [chartTable] 안에서 행과 열 번호를 알린다. */
internal fun Modifier.chartCell(row: Int, column: Int): Modifier = semantics { collectionItemInfo = CollectionItemInfo(row, 1, column, 1) }

/**
 * 가로 축 이름 줄. [position]은 이름 번호와 줄 폭(px)을 받아 이름 가운데가 놓일 x(px)를 돌려준다.
 * 가장 긴 이름과 간격이 들어가도록 몇 개씩 건너뛰어 보이므로, 넓은 화면에서는 더 많은 이름이, 좁은 화면이나 큰 글자에서는 적은 이름이 보인다.
 * 첫 이름은 늘 보이고, 마지막 이름은 앞 이름과 겹치지 않을 때만 보인다. 이름은 줄 밖으로 나가지 않게 안으로 민다.
 * [sparse]면 건너뛰지 않고 빈 문자열이 아닌 이름을 모두 놓되, 앞 이름과 겹치는 것만 뺀다(달력 히트맵의 월 이름).
 */
@Composable
internal fun ChartXAxis(
    labels: List<String>,
    ink: Color,
    modifier: Modifier = Modifier,
    sparse: Boolean = false,
    position: (index: Int, width: Int) -> Float,
) {
    val style = chartAxisText(ink)
    Layout(
        content = { labels.forEach { ThemedText(it, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val placeables = measurables.map { it.measure(Constraints(maxWidth = width)) }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        val gap = AXIS_LABEL_GAP.roundToPx()
        val widest = placeables.maxOfOrNull { it.width } ?: 0
        val step = if (labels.size > 1) position(1, width) - position(0, width) else width.toFloat()
        val stride = if (step <= 0f) 1 else ceil((widest + gap) / step).toInt().coerceAtLeast(1)
        layout(width, height) {
            var lastEnd = Int.MIN_VALUE
            placeables.forEachIndexed { i, label ->
                val wanted = labels[i].isNotEmpty() && (sparse || i % stride == 0 || i == placeables.lastIndex)
                if (!wanted) return@forEachIndexed
                val x = (position(i, width) - label.width / 2f).roundToInt().coerceIn(0, (width - label.width).coerceAtLeast(0))
                if (x < lastEnd + gap) return@forEachIndexed
                label.place(x, 0)
                lastEnd = x + label.width
            }
        }
    }
}

/** 데이터 차트 공통 문구에 쓰는 값 문자열들을 [separator]로 잇는다. */
internal fun joinChartParts(parts: List<String>, separator: String = ", "): String =
    parts.filter { it.isNotEmpty() }.joinToString(separator)

private val AXIS_LABEL_GAP = 10.dp
