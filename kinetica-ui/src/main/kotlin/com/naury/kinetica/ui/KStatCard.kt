package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/**
 * 대시보드와 건강 앱의 숫자 카드. 제목, 큰 값, 변화율, 흐름 그래프(스파크라인)를 한 판에 놓는다.
 * 값은 [RollingText]로 자릿수가 굴러가며 바뀐다. 변화율은 오르면 위 화살표, 내리면 아래 화살표와 함께 좋은 쪽은
 * [KineticaColors.positive], 나쁜 쪽은 [KineticaColors.destructive]로 칠하는데, 글자 대비가 모자라면 같은 색을 진하게 고쳐 쓴다.
 * 색만으로 알리지 않도록 화살표와 부호를 함께 둔다.
 *
 * TalkBack은 카드 전체를 '매출, 1,240만 원, 12.5% 증가, 지난주 대비'처럼 한 번에 읽는다. [onClick]이 있으면 버튼으로 읽는다.
 *
 * ```kotlin
 * KStatCard("이번 주 매출", "1,240만 원", Modifier.weight(1f), change = 0.125f, trend = sales, caption = "지난주 대비")
 * ```
 *
 * @param value 보여 줄 값. 형식은 호출한 쪽이 정한다.
 * @param change 변화율. 0.125는 12.5% 증가, -0.03은 3% 감소다. `null`이면 숨긴다. NaN이나 무한대면 [IllegalArgumentException]을 던진다.
 * @param trend 흐름 그래프 값. `null`이거나 두 개 미만이면 숨긴다.
 * @param caption 변화율 옆 보조 설명
 * @param icon 제목 앞 아이콘. 강조색 옅은 원 안에 그린다.
 * @param increaseIsGood 오르는 것이 좋은 지표인지. 지출이나 체중처럼 내려야 좋은 지표면 false를 넘겨 색을 뒤집는다.
 */
@Composable
public fun KStatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    change: Float? = null,
    trend: List<Float>? = null,
    caption: String? = null,
    icon: ImageVector? = null,
    increaseIsGood: Boolean = true,
    onClick: (() -> Unit)? = null,
    variant: KVariant = KVariant.Glass,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(change == null || change.isFinite()) { "change는 유한한 값이어야 합니다." }
    val locale = LocalConfiguration.current.locales.get(0) ?: Locale.ROOT
    val changeText = change?.let { formatChange(it, locale) }
    val description = joinChartParts(listOfNotNull(title, value, changeText?.let { changeWords(change, it) }, caption))
    val toneColor = changeColor(change, increaseIsGood, surface)
    KCard(modifier, onClick = onClick, variant = variant, surface = surface, contentPadding = STAT_PADDING) {
        val ink = LocalKineticaContentColor.current
        Column(Modifier.clearAndSetSemantics { contentDescription = description }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatHeader(title, icon, ink)
            RollingText(value, style = themed(TextStyle(color = ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)))
            if (changeText != null || caption != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (change != null && changeText != null) ChangePill(change, changeText, toneColor, surface)
                    caption?.let {
                        ThemedText(
                            it,
                            Modifier.align(Alignment.CenterVertically),
                            style = TextStyle(color = ink.copy(alpha = MUTED), fontSize = 13.sp),
                        )
                    }
                }
            }
            if (trend != null && trend.size >= 2) {
                val line = if (change == null || change == 0f) KineticaTheme.colors.accent else toneColor
                Sparkline(trend, Modifier.fillMaxWidth().height(SPARK_HEIGHT).padding(top = 4.dp), color = line)
            }
        }
    }
}

/** 변화율을 TalkBack에 읽을 말. '12.5% 증가'처럼 방향을 붙인다. */
@Composable
private fun changeWords(change: Float?, text: String): String = when {
    change == null || change == 0f -> stringResource(R.string.kinetica_ui_stat_flat)
    change > 0f -> stringResource(R.string.kinetica_ui_stat_up, text)
    else -> stringResource(R.string.kinetica_ui_stat_down, text)
}

/** 변화 방향과 지표 성격으로 고른 색. 변화가 없으면 글자 색이다. */
@Composable
private fun changeColor(change: Float?, increaseIsGood: Boolean, surface: SurfaceStyle): Color = when {
    change == null || change == 0f -> surface.contentColor
    (change > 0f) == increaseIsGood -> KineticaTheme.colors.positive
    else -> KineticaTheme.colors.destructive
}

@Composable
private fun StatHeader(title: String, icon: ImageVector?, ink: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (icon != null) {
            val accent = KineticaTheme.colors.accent
            Box(Modifier.size(ICON_CIRCLE).background(accent.copy(alpha = ICON_FILL), CircleShape), contentAlignment = Alignment.Center) {
                Glyph(icon, accent, size = 16.dp)
            }
        }
        val weight = KineticaTheme.design.label(FontWeight.Medium)
        ThemedText(
            title,
            style = TextStyle(color = ink.copy(alpha = MUTED), fontSize = 14.sp, fontWeight = weight),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 화살표와 변화율을 배지 모양의 옅은 색 판에 담는다. 글자는 바탕 위 대비를 맞춘 같은 색이다. */
@Composable
private fun ChangePill(change: Float, text: String, color: Color, surface: SurfaceStyle) {
    val ink = readableOn(color, surface.inkBackground())
    Row(
        Modifier
            .background(color.copy(alpha = PILL_FILL), roundedShapeFor(PILL_RADIUS) { badge })
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (change != 0f) {
            val path = remember { Path() }
            Box(
                Modifier.size(ARROW).drawBehind {
                    // 위 또는 아래를 가리키는 작은 삼각형
                    path.reset()
                    if (change > 0f) {
                        path.moveTo(size.width / 2, 0f)
                        path.lineTo(size.width, size.height)
                        path.lineTo(0f, size.height)
                    } else {
                        path.moveTo(0f, 0f)
                        path.lineTo(size.width, 0f)
                        path.lineTo(size.width / 2, size.height)
                    }
                    path.close()
                    drawPath(path, ink)
                },
            )
        }
        ThemedText(text, style = TextStyle(color = ink, fontSize = 13.sp, fontWeight = KineticaTheme.design.label(FontWeight.SemiBold)))
    }
}

/** 부호 없는 백분율. 1% 미만이나 소수가 있는 값은 소수 첫째 자리까지 쓴다. */
internal fun formatChange(change: Float, locale: Locale): String =
    NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 1 }.format(abs(change).toDouble())

private const val MUTED = 0.66f
private const val ICON_FILL = 0.14f
private const val PILL_FILL = 0.14f
private val STAT_PADDING = 16.dp
private val ICON_CIRCLE = 28.dp
private val PILL_RADIUS = 8.dp
private val ARROW = 8.dp
private val SPARK_HEIGHT = 40.dp
