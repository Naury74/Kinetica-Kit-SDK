package com.naury.kinetica.ui

import android.content.ClipData
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 길게 누르고 있어야 실행되는 동작. 누르는 동안 [shape] 테두리를 따라 [color] 선이 [durationMs]에 걸쳐 한 바퀴 그려지고, 바탕도 시작 쪽에서 끝 쪽으로
 * 옅게 찬다(RTL이면 반대). 다 차면 햅틱과 함께 [onConfirm]이 불리고, 그 전에 손을 떼면 빠르게 되돌아간다. 삭제나 송금처럼 실수로 누르면 곤란한 동작에 쓴다.
 * TalkBack 사용자는 길게 누를 수 없으므로 두 번 탭하면 바로 실행되는 [label] 동작을 둔다. 이미 누를 수 있는 요소(버튼)에 붙이지 말고
 * 표면이나 Box에 붙인다.
 *
 * ```kotlin
 * val red = KineticaTheme.colors.destructive
 * KSurface(Modifier.holdToConfirm(::deleteAccount, CircleShape, color = red), shape = CircleShape) { … }
 * ```
 *
 * @param durationMs 눌러야 하는 시간(ms). 0 이하면 [IllegalArgumentException]을 던진다.
 * @param label TalkBack 동작 이름. null이면 '길게 눌러 확인'이다.
 */
@Composable
public fun Modifier.holdToConfirm(
    onConfirm: () -> Unit,
    shape: Shape = CircleShape,
    durationMs: Int = 1_200,
    color: Color = KineticaTheme.colors.accent,
    label: String? = null,
): Modifier {
    require(durationMs > 0) { "durationMs는 0보다 커야 합니다." }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = kHapticFeedback()
    val latest = rememberUpdatedState(onConfirm)
    val actionLabel = label ?: stringResource(R.string.kinetica_ui_hold_confirm)
    return this
        .semantics {
            onClick(actionLabel) {
                latest.value()
                true
            }
        }
        .pointerInput(durationMs) {
            awaitEachGesture {
                awaitFirstDown()
                val fill = scope.launch {
                    val left = ((1f - progress.value) * durationMs).toInt()
                    progress.animateTo(1f, tween(left, easing = LinearEasing))
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    latest.value()
                    delay(DONE_HOLD_MS)
                    progress.animateTo(0f, tween(RESET_MS))
                }
                waitForUpOrCancellation()
                if (progress.value < 1f) {
                    fill.cancel()
                    scope.launch { progress.animateTo(0f, spring(stiffness = RELEASE_STIFFNESS)) }
                }
            }
        }
        .drawWithCache {
            val outline = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
            val measure = PathMeasure().apply { setPath(outline, false) }
            val segment = Path()
            val stroke = Stroke(HOLD_STROKE.toPx(), cap = StrokeCap.Round)
            val rtl = layoutDirection == LayoutDirection.Rtl
            onDrawWithContent {
                drawContent()
                val p = progress.value
                if (p <= 0f) return@onDrawWithContent
                val w = size.width * p
                clipPath(outline) {
                    drawRect(color.copy(alpha = FILL_ALPHA), Offset(if (rtl) size.width - w else 0f, 0f), Size(w, size.height))
                }
                segment.reset()
                measure.getSegment(0f, measure.length * p, segment, true)
                drawPath(segment, color, style = stroke)
            }
        }
}

/**
 * 누르면 [text]를 클립보드에 복사하는 아이콘 버튼. 복사하면 겹친 사각형 아이콘이 줄어들며 체크 표시가 그려지고, 잠시 뒤 다시 복사 아이콘으로 돌아온다.
 * TalkBack은 복사한 뒤 '복사됨'을 알린다. '애니메이션 삭제' 설정에서는 아이콘이 바로 바뀐다.
 *
 * ```kotlin
 * Row { Text(coupon); KCopyButton(coupon) }
 * ```
 *
 * @param label 클립 설명. 시스템 클립보드 미리 보기에 쓰인다.
 * @param holdMs 체크 표시를 보여 주는 시간(ms)
 */
@Composable
public fun KCopyButton(
    text: String,
    modifier: Modifier = Modifier,
    label: String = "",
    onCopied: () -> Unit = {},
    holdMs: Long = 1_600L,
    size: Dp = KMetrics.IconButton,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(holdMs)
            copied = false
        }
    }
    val still = reducedMotion()
    val level =
        animateFloatAsState(
            if (copied) 1f else 0f,
            if (still) snap() else spring(dampingRatio = COPY_DAMPING, stiffness = COPY_STIFFNESS),
            label = "copy",
        )
    val ink = surface.contentColor
    val positive = KineticaTheme.colors.positive
    val name = stringResource(if (copied) R.string.kinetica_ui_copy_done else R.string.kinetica_ui_copy_action)
    Box(
        modifier
            .size(size)
            .kSurface(surface, CircleShape)
            .kClickable(CircleShape) {
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, text))) }
                copied = true
                onCopied()
            }
            .semantics {
                contentDescription = name
                if (copied) {
                    stateDescription = name
                    liveRegion = LiveRegionMode.Polite
                }
            }
            .copiedCheck(positive) { level.value },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            rememberVectorPainter(SocialGlyphs.Copy),
            null,
            Modifier.size(size / 2).graphicsLayer {
                val gone = (level.value / CHECK_START).coerceIn(0f, 1f)
                alpha = 1f - gone
                scaleX = 1f - gone * COPY_SHRINK
                scaleY = 1f - gone * COPY_SHRINK
            },
            colorFilter = ColorFilter.tint(ink),
        )
    }
}

/** 복사 아이콘이 사라진 자리에 체크를 [level]만큼 그린다. */
private fun Modifier.copiedCheck(color: Color, level: () -> Float): Modifier = drawWithCache {
    val box = size.minDimension * CHECK_BOX
    val stroke = size.minDimension * CHECK_STROKE
    onDrawWithContent {
        drawContent()
        val check = ((level() - CHECK_START) / (1f - CHECK_START)).coerceIn(0f, 1f)
        drawCheckStroke(color, check, Offset((size.width - box) / 2, (size.height - box) / 2), box, stroke)
    }
}

private val HOLD_STROKE = 3.dp
private const val FILL_ALPHA = 0.18f
private const val DONE_HOLD_MS = 400L
private const val RESET_MS = 260
private const val RELEASE_STIFFNESS = 900f
private const val COPY_DAMPING = 0.7f
private const val COPY_STIFFNESS = 420f
private const val CHECK_START = 0.35f
private const val CHECK_BOX = 0.46f
private const val CHECK_STROKE = 0.06f
private const val COPY_SHRINK = 0.5f
