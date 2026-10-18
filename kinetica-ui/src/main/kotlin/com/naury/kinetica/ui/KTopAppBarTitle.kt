package com.naury.kinetica.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** [KTopAppBar] 제목 슬롯이 받는 값. */
@Immutable
public interface KTopAppBarTitleScope {
    /** [KTopAppBarVariant.Medium]의 아래 큰 제목 줄이면 true, 한 줄 바나 Medium의 위 작은 제목이면 false다. */
    public val isLarge: Boolean

    /** 이 자리에서 String 제목이 쓰는 글자 모양. 색은 바 표면의 글자 색이다. 직접 만든 제목도 이 모양을 쓰면 바와 어울린다. */
    public val textStyle: TextStyle
}

internal class TitleScope(override val isLarge: Boolean, override val textStyle: TextStyle) : KTopAppBarTitleScope

/**
 * 눌러서 목록을 펴는 제목. 제목 글자 끝에 아래 화살표를 놓고, [expanded]면 화살표가 위를 향하도록 돌아간다.
 * '애니메이션 삭제'가 켜져 있으면 바로 돌아간다. 한 줄 바에서는 18sp, Medium 큰 제목 줄에서는 26sp로 String 제목과 같은 크기다.
 *
 * TalkBack에서는 제목(heading)이면서 버튼으로 읽히고, 상태로 '펼쳐짐'이나 '접힘'을 읽는다. 화살표는 좌우 대칭이라 RTL에서도 그대로다.
 *
 * ```kotlin
 * KTopAppBar(title = { KTopAppBarDropdownTitle(albumName, expanded = showAlbums, onClick = { showAlbums = !showAlbums }) })
 * ```
 *
 * @param text 제목 글자. 길면 한 줄에서 말줄임한다.
 * @param expanded 목록이 펼쳐져 있는지
 * @param onClickLabel TalkBack이 '두 번 탭하여 …' 뒤에 읽을 동작 이름(예: "앨범 고르기"). null이면 기본 안내를 읽는다.
 * @param maxTextWidth 제목 글자가 차지할 수 있는 가장 큰 폭
 */
@Composable
public fun KTopAppBarTitleScope.KTopAppBarDropdownTitle(
    text: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClickLabel: String? = null,
    maxTextWidth: Dp = DROPDOWN_MAX_TEXT,
) {
    val still = reducedMotion()
    val turn by animateFloatAsState(
        if (expanded) HALF_TURN else 0f,
        if (still) snap() else tween(ARROW_MS),
        label = "top-bar-dropdown-arrow",
    )
    val state = stringResource(if (expanded) R.string.kinetica_ui_expanded else R.string.kinetica_ui_collapsed)
    val shape = shapeFor { button }
    Row(
        modifier
            .heightIn(min = KMetrics.MinTouch)
            .clip(shape)
            .kClickable(shape, onClickLabel = onClickLabel, role = Role.DropdownList, onClick = onClick)
            .semantics {
                heading()
                stateDescription = state
            }
            .padding(start = DROPDOWN_PAD_START, end = DROPDOWN_PAD_END),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemedText(
            text,
            Modifier.widthIn(max = maxTextWidth),
            style = textStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Image(
            KGlyphs.ChevronDown,
            contentDescription = null,
            modifier = Modifier
                .padding(start = 2.dp)
                .size(if (isLarge) LARGE_ARROW else ARROW)
                .graphicsLayer { rotationZ = turn },
            colorFilter = ColorFilter.tint(textStyle.color.copy(alpha = ARROW_ALPHA)),
        )
    }
}

private const val HALF_TURN = 180f
private const val ARROW_MS = 220
private const val ARROW_ALPHA = 0.7f
private val ARROW = 20.dp
private val LARGE_ARROW = 26.dp
private val DROPDOWN_PAD_START = 8.dp
private val DROPDOWN_PAD_END = 4.dp
private val DROPDOWN_MAX_TEXT = 240.dp
