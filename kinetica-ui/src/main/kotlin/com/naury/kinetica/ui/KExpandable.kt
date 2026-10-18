package com.naury.kinetica.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 아코디언. 머리글을 누르면 화살표가 돌고 내용이 용수철처럼 펼쳐지며, 내용은 살짝 아래에서 떠오르듯 나타난다.
 * 접혀 있는 동안에는 [content]를 구성하지 않는다. TalkBack은 펼침 상태를 읽고 펼치기와 접기 동작을 제공한다.
 *
 * ```kotlin
 * var open by remember { mutableStateOf(false) }
 * KExpandable(open, { open = it }, header = { BasicText("배송 정보") }) { BasicText("내일 도착") }
 * ```
 *
 * @param header 머리글. 오른쪽 끝(RTL에서는 왼쪽 끝)에 화살표가 붙는다.
 */
@Composable
public fun KExpandable(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    header: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    surface: SurfaceStyle = KineticaTheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val progress = remember { Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded) { progress.animateTo(if (expanded) 1f else 0f, if (expanded) OPEN_SPEC else CLOSE_SPEC) }
    val showContent by remember { derivedStateOf { progress.value > 0f || progress.targetValue > 0f } }
    Column(modifier.kSurface(surface, RoundedCornerShape(CORNER))) {
        CompositionLocalProvider(LocalKineticaContentColor provides surface.contentColor) {
            ExpandableHeader(expanded, onExpandedChange, progress, surface.contentColor, header)
            if (showContent) {
                Box(
                    Modifier
                        .clipToBounds()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints.copy(minHeight = 0))
                            val height = (placeable.height * progress.value).roundToInt().coerceAtLeast(0)
                            layout(placeable.width, height) { placeable.place(0, 0) }
                        },
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                val level = progress.value.coerceIn(0f, 1f)
                                alpha = ((level - FADE_START) / (1f - FADE_START)).coerceIn(0f, 1f)
                                translationY = CONTENT_RISE.toPx() * (1f - level)
                            }
                            .padding(start = PADDING_H, end = PADDING_H, bottom = PADDING_V),
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandableHeader(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    progress: Animatable<Float, *>,
    color: Color,
    header: @Composable RowScope.() -> Unit,
) {
    val state = stringResource(if (expanded) R.string.kinetica_ui_expanded else R.string.kinetica_ui_collapsed)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = HEADER_MIN_HEIGHT)
            // 눌림 표시는 카드 모서리를 따라 둥글게 잘린다. 펼친 동안에는 아래가 본문과 이어지므로 위쪽만 둥글다.
            .clickable(null, if (expanded) ExpandedHeaderPress else CollapsedHeaderPress, role = Role.Button) {
                onExpandedChange(!expanded)
            }
            .semantics {
                expandedStateText(state)
                if (expanded) {
                    collapse {
                        onExpandedChange(false)
                        true
                    }
                } else {
                    expand {
                        onExpandedChange(true)
                        true
                    }
                }
            }
            .padding(horizontal = PADDING_H, vertical = PADDING_V),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, content = header)
        Chevron(color, Modifier.graphicsLayer { rotationZ = HALF_TURN * progress.value })
    }
}

/** 아래를 가리키는 화살표. 경로는 크기가 바뀔 때만 다시 만든다. */
@Composable
private fun Chevron(color: Color, modifier: Modifier) {
    Box(
        modifier
            .size(CHEVRON_SIZE)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(w * CHEVRON_SIDE, h * CHEVRON_TOP)
                    lineTo(w / 2f, h * (1f - CHEVRON_TOP))
                    lineTo(w * (1f - CHEVRON_SIDE), h * CHEVRON_TOP)
                }
                val stroke = Stroke(CHEVRON_STROKE.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                onDrawBehind { drawPath(path, color.copy(alpha = CHEVRON_ALPHA), style = stroke) }
            },
    )
}

/**
 * [KExpandableGroup] 안의 어떤 항목이 펼쳐져 있는지 담는 상태.
 *
 * @property singleOpen `true`면 한 항목을 펼칠 때 나머지를 접는다.
 */
@Stable
public class KExpandableGroupState internal constructor(public val singleOpen: Boolean, initial: Collection<Any>) {
    private val open = mutableStateListOf<Any>().apply { addAll(if (singleOpen) initial.take(1) else initial) }

    /** 지금 펼쳐진 항목의 key */
    public val expandedKeys: List<Any> get() = open.toList()

    /** [key] 항목이 펼쳐져 있으면 `true`다. */
    public fun isExpanded(key: Any): Boolean = key in open

    /** [key] 항목을 펼치거나 접는다. [singleOpen]이면 펼칠 때 다른 항목을 접는다. */
    public fun setExpanded(key: Any, expanded: Boolean) {
        if (!expanded) {
            open.remove(key)
            return
        }
        if (singleOpen) open.retainAll { it == key }
        if (key !in open) open.add(key)
    }
}

/**
 * 화면 회전 뒤에도 펼친 항목을 기억하는 [KExpandableGroupState]를 만든다. `Bundle`에 담을 수 없는 key는 기억하지 않는다.
 *
 * @param initiallyExpanded 처음에 펼쳐 둘 항목의 key. [singleOpen]이면 첫 번째만 펼친다.
 */
@Composable
public fun rememberKExpandableGroupState(
    singleOpen: Boolean = true,
    initiallyExpanded: Collection<Any> = emptyList(),
): KExpandableGroupState = rememberSaveable(singleOpen, saver = groupSaver(singleOpen)) {
    KExpandableGroupState(singleOpen, initiallyExpanded)
}

private fun groupSaver(singleOpen: Boolean) = Saver<KExpandableGroupState, ArrayList<Any>>(
    save = { state -> ArrayList(state.expandedKeys.filter { canBeSaved(it) }) },
    restore = { keys -> KExpandableGroupState(singleOpen, keys) },
)

/** [KExpandableGroup] 안에서 항목을 만드는 범위 */
@Stable
public class KExpandableGroupScope internal constructor(private val state: KExpandableGroupState, private val surface: SurfaceStyle) {
    /**
     * 그룹 안의 아코디언 항목 하나. 펼침 상태는 그룹 상태가 관리한다.
     *
     * @param key 그룹 안에서 겹치지 않는 값. 화면 회전 뒤에도 기억하려면 `Bundle`에 담을 수 있는 값이어야 한다.
     */
    @Composable
    public fun Item(
        key: Any,
        header: @Composable RowScope.() -> Unit,
        modifier: Modifier = Modifier,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        KExpandable(state.isExpanded(key), { state.setExpanded(key, it) }, header, modifier, surface, content)
    }
}

/**
 * 아코디언 여러 개를 세로로 쌓은 그룹. [state]가 `singleOpen`이면 한 번에 한 항목만 펼쳐진다.
 *
 * ```kotlin
 * KExpandableGroup {
 *     Item("ship", header = { BasicText("배송") }) { BasicText("내일 도착") }
 *     Item("refund", header = { BasicText("환불") }) { BasicText("7일 안에 가능") }
 * }
 * ```
 *
 * @param spacing 항목 사이 간격
 */
@Composable
public fun KExpandableGroup(
    modifier: Modifier = Modifier,
    state: KExpandableGroupState = rememberKExpandableGroupState(),
    spacing: Dp = 10.dp,
    surface: SurfaceStyle = KineticaTheme.surface,
    content: @Composable KExpandableGroupScope.() -> Unit,
) {
    val scope = remember(state, surface) { KExpandableGroupScope(state, surface) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) { scope.content() }
}

private val OPEN_SPEC = spring<Float>(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
private val CLOSE_SPEC = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
private val CORNER = 20.dp
private val PADDING_H = 20.dp
private val PADDING_V = 14.dp
private val HEADER_MIN_HEIGHT = 56.dp
private val CONTENT_RISE = 10.dp
private const val FADE_START = 0.25f
private const val HALF_TURN = 180f
private val CollapsedHeaderPress = KIndication(RoundedCornerShape(CORNER))
private val ExpandedHeaderPress = KIndication(RoundedCornerShape(topStart = CORNER, topEnd = CORNER))
private val CHEVRON_SIZE = 18.dp
private val CHEVRON_STROKE = 2.dp
private const val CHEVRON_SIDE = 0.2f
private const val CHEVRON_TOP = 0.35f
private const val CHEVRON_ALPHA = 0.7f

/**
 * 펼침 상태 문구. Android 16(API 36)부터는 TalkBack이 expand, collapse 동작에서 상태를 직접 읽으므로("collapsed") 문구를 또 넣으면
 * 두 번 읽는다. 그 전 버전은 동작만으로는 상태를 읽지 않으므로 [text]를 넣는다.
 */
internal fun SemanticsPropertyReceiver.expandedStateText(text: String) {
    if (Build.VERSION.SDK_INT < EXPANDED_STATE_API) stateDescription = text
}

private const val EXPANDED_STATE_API = 36
