// 파일 이름은 대표 API인 KAttachmentChip을(를) 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** [KAttachmentChip]의 상태 */
public enum class KAttachmentStatus {
    /** 올라간 파일. 끝 버튼은 삭제다. */
    Ready,

    /** 올리는 중. 끝 버튼은 취소이고 둘레에 진행 링이 돈다. */
    Uploading,

    /** 올리지 못함. 끝 버튼은 다시 시도다. */
    Failed,
}

/**
 * 첨부 파일 한 개를 보여 주는 칩. 앞에 썸네일(없으면 파일 아이콘), 가운데 이름과 크기, 끝에 상태별 버튼을 놓는다.
 * 올리는 중이면 칩 바탕이 [progress]만큼 옅은 강조색으로 차고 끝 취소 버튼 둘레에 진행 링이 그려진다. [progress]가 null이면 링이 돈다.
 * 실패하면 둘째 줄이 빨간 '업로드 실패'가 되고 끝 버튼이 다시 시도로 바뀐다. 파일 이름이 길면 가운데를 줄여 확장자를 남긴다.
 * TalkBack은 이름과 둘째 줄을 한 번에 읽고, 올리는 중에는 진행률을 값으로 알린다. 끝 버튼은 따로 초점을 받는다.
 *
 * ```kotlin
 * KAttachmentChip("보고서.pdf", detail = "2.4MB", status = KAttachmentStatus.Uploading, progress = 0.4f, onRemove = ::cancel)
 * ```
 *
 * @param progress 올린 정도(0..1). 범위를 벗어나면 0..1로 맞춘다. [status]가 [KAttachmentStatus.Uploading]일 때만 쓴다.
 * @param onRemove 끝 X 버튼. 올리는 중이면 취소, 다 올라갔으면 삭제다. null이면 버튼을 그리지 않는다.
 * @param onRetry 실패했을 때 다시 시도 버튼. null이면 실패해도 [onRemove] 버튼을 그린다.
 */
@Composable
public fun KAttachmentChip(
    name: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    status: KAttachmentStatus = KAttachmentStatus.Ready,
    progress: Float? = null,
    thumbnail: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val uploading = status == KAttachmentStatus.Uploading
    val still = reducedMotion()
    val level = animateFloatAsState(
        if (uploading) {
            (progress ?: 0f).coerceIn(0f, 1f)
        } else if (status == KAttachmentStatus.Ready) {
            1f
        } else {
            0f
        },
        if (still) snap() else spring(stiffness = PROGRESS_STIFFNESS),
        label = "attachment-progress",
    )
    val colors = KineticaTheme.colors
    val ink = surface.contentColor
    val line = attachmentLine(status, progress, detail)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val shape = roundedShapeFor(CHIP_RADIUS) { chip }
    Row(
        modifier
            .widthIn(min = MIN_WIDTH)
            .heightIn(min = HEIGHT)
            .kSurface(surface, shape)
            .clip(shape)
            .drawBehind {
                if (!uploading) return@drawBehind
                val w = size.width * level.value
                drawRect(colors.accent.copy(alpha = FILL_ALPHA), Offset(if (rtl) size.width - w else 0f, 0f), Size(w, size.height))
            }
            .then(if (onClick != null) Modifier.kClickable(shape, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {
                stateDescription = line
                if (uploading) {
                    progressBarRangeInfo =
                        if (progress ==
                            null
                        ) {
                            ProgressBarRangeInfo.Indeterminate
                        } else {
                            ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
                        }
                }
            }
            .padding(INSET),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AttachmentThumb(thumbnail, colors.accent)
        AttachmentTexts(name, line, status == KAttachmentStatus.Failed, ink, Modifier.weight(1f))
        AttachmentAction(status, progress, level, onRemove, onRetry, ink)
    }
}

@Composable
private fun AttachmentTexts(name: String, line: String, failed: Boolean, ink: Color, modifier: Modifier) {
    Column(modifier) {
        ThemedText(
            name,
            style = TextStyle(color = ink, fontSize = 14.sp, fontWeight = KineticaTheme.design.label(FontWeight.SemiBold)),
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )
        ThemedText(
            line,
            style = TextStyle(color = if (failed) KineticaTheme.colors.destructive else ink.copy(alpha = DETAIL_ALPHA), fontSize = 12.sp),
            maxLines = 2,
        )
    }
}

/** 둘째 줄 글자. 올리는 중이면 진행률, 실패면 실패 문구를 크기 앞에 붙인다. */
@Composable
private fun attachmentLine(status: KAttachmentStatus, progress: Float?, detail: String?): String {
    val state = when (status) {
        KAttachmentStatus.Ready -> null

        KAttachmentStatus.Uploading -> if (progress == null) {
            stringResource(R.string.kinetica_ui_attach_uploading_plain)
        } else {
            stringResource(R.string.kinetica_ui_attach_uploading, (progress.coerceIn(0f, 1f) * PERCENT).roundToInt())
        }

        KAttachmentStatus.Failed -> stringResource(R.string.kinetica_ui_attach_failed)
    }
    return listOfNotNull(state, detail).joinToString("  ·  ")
}

@Composable
private fun AttachmentThumb(thumbnail: (@Composable () -> Unit)?, accent: Color) {
    // 칩 모서리와 같은 중심으로 굽게 한다.
    val shapes = currentShapes
    val outer = shapes.radiusOr(shapes.chip, CHIP_RADIUS)
    val shape = shapeOf(shapes.cornerOf(shapes.chip), if (outer > 0.dp) KMetrics.concentric(outer, INSET) else outer)
    Box(
        Modifier.size(THUMB).clip(shape).then(if (thumbnail == null) Modifier.background(accent.copy(alpha = TILE_ALPHA)) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnail != null) {
            thumbnail()
        } else {
            Image(
                rememberVectorPainter(SocialGlyphs.File),
                null,
                Modifier.size(KMetrics.ButtonIcon),
                colorFilter = ColorFilter.tint(accent),
            )
        }
    }
}

@Composable
private fun AttachmentAction(
    status: KAttachmentStatus,
    progress: Float?,
    level: State<Float>,
    onRemove: (() -> Unit)?,
    onRetry: (() -> Unit)?,
    ink: Color,
) {
    val retry = status == KAttachmentStatus.Failed && onRetry != null
    val action = if (retry) onRetry else onRemove
    if (action == null) return
    val label = stringResource(
        when {
            retry -> R.string.kinetica_ui_attach_retry
            status == KAttachmentStatus.Uploading -> R.string.kinetica_ui_attach_cancel
            else -> R.string.kinetica_ui_attach_remove
        },
    )
    val accent = KineticaTheme.colors.accent
    val spinning = status == KAttachmentStatus.Uploading && progress == null
    val clock = rememberLoopClock(running = spinning && !reducedMotion())
    Box(
        Modifier
            .size(KMetrics.SmallIconButton)
            .loopVisibility(clock)
            .drawBehind {
                if (status != KAttachmentStatus.Uploading) return@drawBehind
                val stroke = RING_STROKE.toPx()
                val inset = stroke / 2
                val arc = Size(size.width - stroke, size.height - stroke)
                drawCircle(ink.copy(alpha = TRACK_ALPHA), size.minDimension / 2 - inset, style = Stroke(stroke))
                rotate(if (spinning) clock.seconds * SPIN else 0f) {
                    val sweep = if (spinning) SPIN_SWEEP else FULL_TURN * level.value
                    drawArc(accent, -QUARTER_TURN, sweep, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            .clip(CircleShape)
            .kClickable(CircleShape, onClick = action)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        val icon: ImageVector = if (retry) SocialGlyphs.Retry else KGlyphs.Close
        Image(rememberVectorPainter(icon), null, Modifier.size(SMALL_ICON), colorFilter = ColorFilter.tint(ink.copy(alpha = ACTION_ALPHA)))
    }
}

/**
 * 파일을 끌어다 놓거나 눌러서 고르는 영역. 점선 테두리 안에 업로드 아이콘과 [title], [subtitle]을 가운데 놓는다.
 * 누르면 [onClick]이 불리므로 호출한 쪽이 파일 선택기를 연다. [onDrop]을 주면 다른 앱(분할 화면, 데스크톱 모드)에서 파일을 끌어 올 수 있고,
 * 끌어 온 파일이 영역 위에 있는 동안 테두리가 강조색 실선으로 바뀌며 아이콘이 커진다. 놓은 파일의 읽기 권한은 이 영역이 요청한다.
 *
 * ```kotlin
 * KFileDropZone(onClick = { picker.launch(arrayOf("application/pdf", "image/png")) }, subtitle = "PDF, 이미지 최대 20MB", onDrop = ::upload)
 * ```
 *
 * @param title 큰 글자. null이면 '파일 추가'다.
 * @param mimeTypes 받을 MIME 유형. 뒷부분을 별표로 둔 와일드카드도 쓸 수 있다. 끌어 온 항목이 하나도 맞지 않으면 반응하지 않는다.
 */
@Composable
public fun KFileDropZone(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    onDrop: ((List<android.net.Uri>) -> Unit)? = null,
    mimeTypes: List<String> = listOf("*/*"),
    enabled: Boolean = true,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val drop = rememberFileDropState(onDrop, mimeTypes)
    val still = reducedMotion()
    val hover = animateFloatAsState(if (drop.hovering) 1f else 0f, if (still) snap() else tween(HOVER_MS), label = "drop-hover")
    val heading = if (drop.hovering) {
        stringResource(R.string.kinetica_ui_drop_release)
    } else {
        title
            ?: stringResource(R.string.kinetica_ui_drop_title)
    }
    DropZoneBody(heading, subtitle, hover, drop.modifier, enabled, onClick, surface, modifier)
}

private val CHIP_RADIUS = 16.dp
private val INSET = 8.dp
private val THUMB = 40.dp
private val HEIGHT = 56.dp
private val MIN_WIDTH = 180.dp
private val SMALL_ICON = 16.dp
private val RING_STROKE = 2.dp
private const val FILL_ALPHA = 0.10f
private const val TILE_ALPHA = 0.14f
private const val DETAIL_ALPHA = 0.62f
private const val TRACK_ALPHA = 0.14f
private const val ACTION_ALPHA = 0.8f
private const val PERCENT = 100f
private const val PROGRESS_STIFFNESS = 200f
private const val SPIN = 360f
private const val SPIN_SWEEP = 100f
private const val FULL_TURN = 360f
private const val QUARTER_TURN = 90f
private const val HOVER_MS = 160
