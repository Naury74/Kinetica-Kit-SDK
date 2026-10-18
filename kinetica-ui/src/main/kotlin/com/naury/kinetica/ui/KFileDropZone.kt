// 파일 이름은 대표 API인 KFileDropZone을(를) 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import android.app.Activity
import android.content.ClipDescription
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 끌어 온 파일이 영역 위에 있는지와, 영역에 붙일 끌어 놓기 수정자. [onDrop]이 없으면 수정자는 비어 있다. */
internal class FileDropState {
    var hovering by mutableStateOf(false)
    var modifier: Modifier = Modifier
}

@Composable
internal fun rememberFileDropState(onDrop: ((List<Uri>) -> Unit)?, mimeTypes: List<String>): FileDropState {
    val state = remember { FileDropState() }
    val latest = rememberUpdatedState(onDrop)
    val context = LocalContext.current
    val target = remember(state, context) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                state.hovering = true
            }

            override fun onExited(event: DragAndDropEvent) {
                state.hovering = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                state.hovering = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                state.hovering = false
                val uris = droppedUris(event, context)
                if (uris.isEmpty()) return false
                latest.value?.invoke(uris)
                return true
            }
        }
    }
    state.modifier = if (onDrop == null) {
        Modifier
    } else {
        Modifier.dragAndDropTarget(shouldStartDragAndDrop = { event -> event.mimeTypes().any { accepts(it, mimeTypes) } }, target = target)
    }
    return state
}

/** 끌어 온 항목의 URI. 다른 앱 파일을 읽을 수 있도록 액티비티에 끌어 놓기 권한을 요청한다. */
private fun droppedUris(event: DragAndDropEvent, context: Context): List<Uri> {
    val drag = event.toAndroidDragEvent()
    context.findActivity()?.requestDragAndDropPermissions(drag)
    val clip = drag.clipData ?: return emptyList()
    return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
}

private fun accepts(type: String, wanted: List<String>): Boolean = wanted.any { ClipDescription.compareMimeTypes(type, it) }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 점선 테두리 영역. [hover]가 1에 가까울수록 테두리가 강조색 실선에 가까워지고 바탕이 옅게 물든다. */
@Composable
internal fun DropZoneBody(
    title: String,
    subtitle: String?,
    hover: State<Float>,
    dropModifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
    surface: SurfaceStyle,
    modifier: Modifier,
) {
    val accent = KineticaTheme.colors.accent
    val ink = surface.contentColor
    val shape = shapeFor { card }
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = MIN_HEIGHT)
            .then(dropModifier)
            .kSurface(surface, shape)
            .drawWithCache {
                val width = BORDER.toPx()
                val dash = DASH.toPx()
                val dashed = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * DASH_GAP)))
                val solid = Stroke(width * 2)
                onDrawBehind {
                    val h = hover.value
                    if (h > 0f) drawShape(shape, accent.copy(alpha = HOVER_FILL * h), Offset.Zero, size)
                    val color = lerp(ink.copy(alpha = BORDER_ALPHA), accent, h)
                    val half = width / 2
                    val box = Size(size.width - width, size.height - width)
                    drawShape(shape, color, Offset(half, half), box, if (h > HALF) solid else dashed)
                }
            }
            .clip(shape)
            .kClickable(shape, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DropIcon(hover, accent)
        val weight = KineticaTheme.design.label(FontWeight.SemiBold)
        ThemedText(title, style = TextStyle(color = ink, fontSize = 15.sp, fontWeight = weight, textAlign = TextAlign.Center))
        subtitle?.let {
            ThemedText(it, style = TextStyle(color = surface.muted(SUBTITLE_ALPHA), fontSize = 13.sp, textAlign = TextAlign.Center))
        }
    }
}

@Composable
private fun DropIcon(hover: State<Float>, accent: Color) {
    Box(
        Modifier
            .size(ICON_TILE)
            .graphicsLayer {
                val scale = 1f + hover.value * HOVER_GROW
                scaleX = scale
                scaleY = scale
                translationY = -hover.value * size.height * HOVER_RISE
            }
            .clip(CircleShape)
            .drawWithCache { onDrawBehind { drawCircle(accent.copy(alpha = TILE_ALPHA)) } },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            rememberVectorPainter(SocialGlyphs.Upload),
            null,
            Modifier.size(KMetrics.IconButtonIcon),
            colorFilter = ColorFilter.tint(accent),
        )
    }
}

private val MIN_HEIGHT = 140.dp
private val BORDER = 1.5.dp
private val DASH = 6.dp
private val ICON_TILE = 48.dp
private const val DASH_GAP = 0.8f
private const val BORDER_ALPHA = 0.32f
private const val HOVER_FILL = 0.10f
private const val TILE_ALPHA = 0.14f
private const val SUBTITLE_ALPHA = 0.6f
private const val HOVER_GROW = 0.14f
private const val HOVER_RISE = 0.1f
private const val HALF = 0.5f
