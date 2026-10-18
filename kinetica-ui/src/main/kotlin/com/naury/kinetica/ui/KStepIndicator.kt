// 파일 이름은 대표 컴포저블인 KStepIndicator를 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** [KStepIndicator]의 모양 */
public enum class KStepStyle {
    /** 번호 원과 이음선, 아래 단계 이름. 끝난 단계는 체크 표시로 바뀐다. 단계가 3~5개인 가입, 결제 흐름에 쓴다. */
    Numbered,

    /** 작은 점. 지금 단계 점은 캡슐로 늘어난다. 온보딩처럼 이름이 필요 없는 짧은 흐름에 쓴다. */
    Dots,

    /** 단계 수만큼 나눈 막대와 위의 'n/전체 단계 · 이름' 글자. 좁은 자리나 단계가 많은 흐름에 쓴다. */
    Bar,
}

/**
 * 단계 표시. 여러 화면에 걸친 흐름에서 지금 몇 번째 단계인지 보인다. [current]가 바뀌면 채움이 스프링으로 옮겨 가고,
 * '애니메이션 삭제' 설정에서는 바로 바뀐다. 단계는 읽는 방향을 따라 RTL에서는 오른쪽부터 놓는다.
 * [onStepClick]을 주면 끝난 단계(지금보다 앞)를 눌러 되돌아갈 수 있다.
 *
 * TalkBack은 진행 표시로 읽고, 상태로 '4단계 중 2단계, 배송 정보'를 알린다.
 *
 * ```kotlin
 * KStepIndicator(listOf("장바구니", "배송", "결제", "완료"), current = 1, Modifier.fillMaxWidth())
 * ```
 *
 * @param steps 단계 이름. 비어 있으면 [IllegalArgumentException]을 던진다.
 * @param current 지금 단계 번호(0부터). 범위 밖이면 끝 값으로 그린다.
 * @param onStepClick 끝난 단계를 눌렀을 때. `null`이면 누를 수 없다.
 */
@Composable
public fun KStepIndicator(
    steps: List<String>,
    current: Int,
    modifier: Modifier = Modifier,
    style: KStepStyle = KStepStyle.Numbered,
    onStepClick: ((Int) -> Unit)? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(steps.isNotEmpty()) { "steps가 비어 있습니다." }
    val at = current.coerceIn(steps.indices)
    val still = reducedMotion()
    val position = animateFloatAsState(
        at.toFloat(),
        if (still) snap() else spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "step-position",
    )
    val state = stringResource(R.string.kinetica_ui_step_of, steps.size, at + 1, steps[at])
    val look = TimelineLook(KineticaTheme.colors.accent, KineticaTheme.colors.destructive, surface.contentColor, surface)
    val properties: SemanticsPropertyReceiver.() -> Unit = {
        stateDescription = state
        progressBarRangeInfo = ProgressBarRangeInfo((at + 1).toFloat(), 1f..steps.size.toFloat(), (steps.size - 2).coerceAtLeast(0))
    }
    // 자식이 있는 채로 묶지 않으면 TalkBack이 이 진행 상태에 멈추지 않는다. 막대형의 글자는 상태 문구와 같은 말이라 지운다.
    val semantics = if (style == KStepStyle.Bar) {
        Modifier.clearAndSetSemantics(properties)
    } else {
        Modifier.semantics(mergeDescendants = true, properties = properties)
    }
    when (style) {
        KStepStyle.Numbered -> NumberedSteps(steps, at, modifier.then(semantics), look, onStepClick)
        KStepStyle.Dots -> DotSteps(steps.size, { position.value }, modifier.then(semantics), look)
        KStepStyle.Bar -> BarSteps(steps, at, { position.value }, modifier.then(semantics), look)
    }
}

@Composable
private fun NumberedSteps(steps: List<String>, current: Int, modifier: Modifier, look: TimelineLook, onStepClick: ((Int) -> Unit)?) {
    val press = shapeFor { button }
    Row(modifier.fillMaxWidth()) {
        steps.forEachIndexed { i, name ->
            val status = when {
                i < current -> KTimelineStatus.Done
                i == current -> KTimelineStatus.Current
                else -> KTimelineStatus.Upcoming
            }
            val clickable = onStepClick != null && i < current
            val label = stringResource(R.string.kinetica_ui_step_name, i + 1, name)
            Column(
                Modifier
                    .weight(1f)
                    .then(if (clickable) Modifier.kClickable(press, onClickLabel = name) { onStepClick?.invoke(i) } else Modifier)
                    .clearAndSetSemantics { contentDescription = label },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth().height(STEP_DOT), verticalAlignment = Alignment.CenterVertically) {
                    StepLine(i > 0, i <= current, look, Modifier.weight(1f))
                    TimelineDot(status, null, look, size = STEP_DOT, number = (i + 1).toString())
                    StepLine(i < steps.lastIndex, i < current, look, Modifier.weight(1f))
                }
                ThemedText(
                    name,
                    Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
                    style = TextStyle(
                        color = if (i <= current) look.ink else look.ink.copy(alpha = MUTED),
                        fontSize = 12.sp,
                        fontWeight = if (i == current) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StepLine(visible: Boolean, solid: Boolean, look: TimelineLook, modifier: Modifier) {
    Box(
        modifier.height(LINE).drawBehind {
            if (!visible) return@drawBehind
            val color = if (solid) look.accent else look.ink.copy(alpha = TRACK_ALPHA)
            drawRoundRect(color, cornerRadius = CornerRadius(size.height / 2))
        },
    )
}

/** 점 표시. 지금 단계 점이 캡슐로 늘어나며 옮겨 간다. 위치는 그리기 단계에서 읽는다. */
@Composable
private fun DotSteps(count: Int, position: () -> Float, modifier: Modifier, look: TimelineLook) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val width = DOT * count + DOT_GAP * (count - 1) + (PILL - DOT)
    Box(
        modifier.size(width, DOT).drawBehind { drawDots(count, position(), look, rtl) },
    )
}

private fun DrawScope.drawDots(count: Int, position: Float, look: TimelineLook, rtl: Boolean) {
    val dot = DOT.toPx()
    val gap = DOT_GAP.toPx()
    val extra = PILL.toPx() - dot
    var x = 0f
    for (i in 0 until count) {
        // 지금 단계에 가까울수록 넓어진다. 두 점 사이를 옮겨 가는 동안 폭이 나뉘어 늘었다 줄어든다.
        val grow = (1f - abs(position - i)).coerceIn(0f, 1f)
        val w = dot + extra * grow
        val color = lerp(look.ink.copy(alpha = TRACK_ALPHA), look.accent, grow.coerceAtLeast(if (i < position) 1f else 0f))
        val left = if (rtl) size.width - x - w else x
        drawRoundRect(color, Offset(left, 0f), Size(w, dot), CornerRadius(dot / 2))
        x += w + gap
    }
}

@Composable
private fun BarSteps(steps: List<String>, current: Int, position: () -> Float, modifier: Modifier, look: TimelineLook) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            ThemedText(
                stringResource(R.string.kinetica_ui_step_count, current + 1, steps.size),
                style = TextStyle(color = look.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            )
            ThemedText(
                steps[current],
                Modifier.padding(start = 8.dp).weight(1f),
                style = TextStyle(color = look.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier.fillMaxWidth().height(BAR).drawBehind {
                val gap = BAR_GAP.toPx()
                val count = steps.size
                val w = (size.width - gap * (count - 1)) / count
                val radius = CornerRadius(size.height / 2)
                val filled = position() + 1f
                for (i in 0 until count) {
                    val left = if (rtl) size.width - (w + gap) * i - w else (w + gap) * i
                    drawRoundRect(look.ink.copy(alpha = TRACK_ALPHA), Offset(left, 0f), Size(w, size.height), radius)
                    val part = (filled - i).coerceIn(0f, 1f)
                    if (part > 0f) {
                        val fillLeft = if (rtl) left + w * (1f - part) else left
                        drawRoundRect(look.accent, Offset(fillLeft, 0f), Size(w * part, size.height), radius)
                    }
                }
            },
        )
    }
}

private const val MUTED = 0.6f
private const val TRACK_ALPHA = 0.16f
private val STEP_DOT = 28.dp
private val LINE = 2.dp
private val DOT = 8.dp
private val PILL = 24.dp
private val DOT_GAP = 6.dp
private val BAR = 6.dp
private val BAR_GAP = 4.dp
