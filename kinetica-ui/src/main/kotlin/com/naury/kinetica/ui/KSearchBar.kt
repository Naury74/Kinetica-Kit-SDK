package com.naury.kinetica.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 캡슐 검색창. 누르면 캡슐이 가로로 살짝 늘어났다 돌아오고, 끝쪽에서 '취소' 버튼이 밀려 들어온다.
 * 글자가 있으면 지우기 버튼이 나타난다. 포커스를 잃으면 '취소' 버튼이 사라지고 원래 모습으로 돌아간다.
 *
 * 키보드의 검색 키를 누르면 [onSearch]를 호출하고 키보드를 내린다. '취소'를 누르면 [onQueryChange]에 빈 문자열을 넘기고 포커스를 해제한다.
 *
 * @param query 입력한 검색어
 * @param onQueryChange 바뀐 검색어를 받는다.
 * @param onSearch 키보드의 검색 키를 눌렀을 때 그때의 검색어를 받는다.
 * @param placeholder 검색어가 비어 있을 때 보이는 안내 글. 기본값은 '검색'이다. TalkBack은 검색어가 있을 때도 이 글을 입력란 이름으로 읽는다.
 * @param showCancel `false`면 포커스를 받아도 '취소' 버튼을 띄우지 않는다. 지우기 버튼만으로 충분한 좁은 자리에 쓴다.
 */
@Composable
public fun KSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.kinetica_ui_search),
    surface: SurfaceStyle = KineticaTheme.surface,
    showCancel: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val levels = remember { FieldLevels(focused = false, error = false) }
    LaunchedEffect(focused) { levels.focusTo(focused) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val stretch = remember { Animatable(0f) }
    val still = reducedMotion()
    LaunchedEffect(focused) {
        if (focused && !still) {
            stretch.animateTo(1f, tween(STRETCH_IN_MS))
            stretch.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
        }
    }
    val textStyle = themed(TextStyle(color = surface.contentColor, fontSize = SEARCH_FONT_SIZE))
    val accent = KineticaTheme.colors.accent
    Row(modifier.keepVisibleWhileFocused(focused), verticalAlignment = Alignment.CenterVertically) {
        ProvideFieldSelection(accent) {
            BasicTextField(
                query,
                onQueryChange,
                Modifier
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = 1f + STRETCH_X * stretch.value
                        scaleY = 1f - STRETCH_Y * stretch.value
                    }
                    .kSurface(surface.forField(), CircleShape)
                    .fieldFocusFrame(SEARCH_HEIGHT / 2, levels, accent, accent)
                    .onFocusChanged { focused = it.isFocused }
                    .fieldName(placeholder),
                singleLine = true,
                textStyle = textStyle,
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    onSearch(query)
                    keyboard?.hide()
                }),
                decorationBox = { field ->
                    SearchDecoration(query, placeholder, surface, { onQueryChange("") }, field)
                },
            )
        }
        CancelButton(showCancel && focused) {
            onQueryChange("")
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
}

@Composable
private fun SearchDecoration(
    query: String,
    placeholder: String,
    surface: SurfaceStyle,
    onClear: () -> Unit,
    field: @Composable () -> Unit,
) {
    Row(
        Modifier.heightIn(min = SEARCH_HEIGHT).padding(start = SEARCH_PADDING, end = CLEAR_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Magnifier(surface.contentColor.copy(alpha = HINT_ALPHA), Modifier.size(ICON_SIZE))
        Spacer(Modifier.width(ICON_GAP))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                ThemedText(
                    placeholder,
                    Modifier.fieldNameText(),
                    style = TextStyle(color = surface.contentColor.copy(alpha = HINT_ALPHA), fontSize = SEARCH_FONT_SIZE),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            field()
        }
        FieldClearButton(query.isNotEmpty(), surface.contentColor, onClear)
    }
}

@Composable
private fun CancelButton(visible: Boolean, onCancel: () -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // 끝쪽(LTR이면 오른쪽)에서 밀려 들어오도록 방향을 맞춘다.
    val sign = if (rtl) -1 else 1
    AnimatedVisibility(
        visible,
        enter = expandHorizontally(expandFrom = Alignment.Start) + slideInHorizontally { it * sign } + fadeIn(),
        exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + slideOutHorizontally { it * sign } + fadeOut(),
    ) {
        ThemedText(
            stringResource(R.string.kinetica_ui_search_cancel),
            Modifier
                .padding(start = CANCEL_GAP)
                .clickable(null, KIndication(CircleShape), role = Role.Button, onClick = onCancel)
                .padding(horizontal = CANCEL_PADDING, vertical = CANCEL_VERTICAL),
            style = TextStyle(color = KineticaTheme.colors.accent, fontSize = SEARCH_FONT_SIZE),
            maxLines = 1,
        )
    }
}

/** 돋보기 아이콘. 아이콘 라이브러리에 기대지 않고 원과 손잡이를 직접 그린다. */
@Composable
private fun Magnifier(color: Color, modifier: Modifier) {
    Box(
        modifier.drawWithCache {
            val width = MAGNIFIER_STROKE.toPx()
            val stroke = Stroke(width, cap = StrokeCap.Round)
            val radius = size.minDimension * LENS_RATIO
            val lens = Offset(radius + width / 2, radius + width / 2)
            // 렌즈 원의 오른쪽 아래 45도 지점에서 손잡이가 시작한다.
            val handle = radius * DIAGONAL
            onDrawBehind {
                drawCircle(color, radius, lens, style = stroke)
                drawLine(
                    color,
                    lens + Offset(handle, handle),
                    Offset(size.width - width / 2, size.height - width / 2),
                    width,
                    StrokeCap.Round,
                )
            }
        },
    )
}

private val SEARCH_HEIGHT = 44.dp
private val SEARCH_PADDING = 14.dp
private val CLEAR_PADDING = 4.dp
private val SEARCH_FONT_SIZE = 17.sp
private val ICON_SIZE = 18.dp
private val ICON_GAP = 8.dp
private val CANCEL_PADDING = 10.dp
private val CANCEL_GAP = 4.dp
private val CANCEL_VERTICAL = 10.dp
private val MAGNIFIER_STROKE = 2.dp
private const val HINT_ALPHA = FIELD_HINT_ALPHA
private const val LENS_RATIO = 0.36f
private const val DIAGONAL = 0.7071f
private const val STRETCH_IN_MS = 110
private const val STRETCH_X = 0.03f
private const val STRETCH_Y = 0.04f
