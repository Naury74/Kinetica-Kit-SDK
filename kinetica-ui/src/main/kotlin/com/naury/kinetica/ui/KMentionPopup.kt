package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** 제안 팝업의 키보드 선택 위치와 닫히는 동안 보일 마지막 후보 목록. */
@Stable
internal class MentionPopupModel {
    var active by mutableIntStateOf(0)

    /** 마지막으로 받은 비어 있지 않은 후보. 팝업이 사라지는 동안 판이 빈 채로 줄어들지 않게 한다. */
    var shown: List<KMentionCandidate> = emptyList()
        private set

    @Composable
    fun sync(candidates: List<KMentionCandidate>) {
        if (candidates.isNotEmpty()) shown = candidates
        LaunchedEffect(candidates) { active = 0 }
    }
}

/** 팝업이 열렸을 때 하드웨어 키를 처리한다. 처리했으면 `true`. 떼는 사건도 같은 키면 삼켜 입력란이 줄 바꿈 같은 일을 하지 않게 한다. */
internal fun mentionKey(event: KeyEvent, popup: MentionPopupModel, count: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit): Boolean {
    val handled = event.key in PopupKeys
    if (!handled || count == 0) return false
    if (event.type != KeyEventType.KeyDown) return true
    when (event.key) {
        Key.DirectionDown -> popup.active = (popup.active + 1) % count
        Key.DirectionUp -> popup.active = (popup.active - 1 + count) % count
        Key.Escape -> onDismiss()
        else -> onPick(popup.active.coerceIn(0, count - 1))
    }
    return true
}

private val PopupKeys = setOf(Key.DirectionDown, Key.DirectionUp, Key.Enter, Key.NumPadEnter, Key.Tab, Key.Escape)

/**
 * 글 [offset] 위치의 커서 사각형에 크기와 위치를 맞춘 상자. 안에서 부른 앵커 팝업이 이 상자를 기준으로 뜬다.
 * 배치 결과는 배치 단계에서만 읽는다.
 */
@Composable
internal fun MentionPopupAnchor(layout: TextLayoutHolder, offset: Int, content: @Composable () -> Unit) {
    Box(
        Modifier.layout { measurable, _ ->
            val result = layout.result
            val rect = result?.getCursorRect(offset.coerceIn(0, result.layoutInput.text.length)) ?: Rect.Zero
            val placeable = measurable.measure(
                Constraints.fixed(rect.width.roundToInt().coerceAtLeast(1), rect.height.roundToInt().coerceAtLeast(1)),
            )
            layout(0, 0) { placeable.place(rect.left.roundToInt(), rect.top.roundToInt()) }
        },
    ) { content() }
}

internal class MentionPopupContent(val model: MentionPopupModel, val trigger: Char, val surface: SurfaceStyle)

@Composable
internal fun MentionPopup(visible: Boolean, content: MentionPopupContent, onPick: (KMentionCandidate) -> Unit, onDismiss: () -> Unit) {
    val anchor = remember { PopupAnchor() }
    AnchoredPopup(visible, anchor, AnchoredFrame(focusable = false, onDismiss = onDismiss)) { transition, geometry ->
        val items = content.model.shown
        val title = pluralStringResource(R.plurals.kinetica_ui_mention_suggestions, items.size, items.size)
        PopupPanel(
            transition,
            geometry,
            content.surface,
            Modifier.widthIn(min = PANEL_MIN_WIDTH, max = PANEL_MAX_WIDTH).semantics { paneTitle = title },
        ) {
            items.forEachIndexed { index, candidate ->
                CandidateRow(candidate, index == content.model.active, content.trigger, content.surface) { onPick(candidate) }
            }
        }
    }
}

@Composable
private fun CandidateRow(candidate: KMentionCandidate, active: Boolean, trigger: Char, surface: SurfaceStyle, onClick: () -> Unit) {
    val accent = KineticaTheme.colors.accent
    val ink = surface.contentColor
    val description = listOfNotNull(candidate.label, candidate.supporting).joinToString(", ")
    val shape = MenuItemShape
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PopupPanelPadding)
            .clip(shape)
            .background(if (active) accent.copy(alpha = ACTIVE_FILL) else Color.Transparent)
            .kClickable(shape, onClick = onClick)
            // 아바타가 이름을 따로 읽지 않게 행 하나로 묶는다.
            .clearAndSetSemantics { contentDescription = description }
            .heightIn(min = ROW_HEIGHT)
            .padding(horizontal = ROW_PADDING, vertical = ROW_PADDING_V),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ROW_GAP),
    ) {
        val avatar = candidate.avatar
        if (avatar != null) {
            KAvatar(avatar, size = KAvatarSize.Small, surface = surface)
        } else {
            Box(
                Modifier.size(KAvatarSize.Small.size).background(ink.copy(alpha = BADGE_FILL), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                ThemedText(trigger.toString(), style = TextStyle(color = ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            }
        }
        Column(Modifier.weight(1f)) {
            ThemedText(
                candidate.label,
                style = TextStyle(color = ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            candidate.supporting?.let {
                ThemedText(
                    it,
                    style = TextStyle(color = surface.muted(SUPPORT_ALPHA), fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val PANEL_MIN_WIDTH = 220.dp
private val PANEL_MAX_WIDTH = 320.dp
private val ROW_HEIGHT = 48.dp
private val ROW_PADDING = 10.dp
private val ROW_PADDING_V = 6.dp
private val ROW_GAP = 12.dp
private const val ACTIVE_FILL = 0.12f
private const val BADGE_FILL = 0.08f
private const val SUPPORT_ALPHA = 0.6f
