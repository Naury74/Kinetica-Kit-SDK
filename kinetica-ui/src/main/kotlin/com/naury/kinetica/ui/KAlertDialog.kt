package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * [KDialogAction]의 역할. 버튼 색과 놓이는 순서가 여기서 정해진다.
 * 1.0.0-alpha04에서 [DestructiveTonal]과 [Text]가 더해졌으므로, 이 값을 `when`으로 빠짐없이 나누는 코드는 두 항목을 처리해야 한다.
 */
public enum class KDialogActionStyle {
    /**
     * 일반 동작. 다른 강조 동작이 없고 혼자이면 채운 버튼, 그 밖에는 옅은 강조색 버튼이다.
     * 혼자인지 셀 때 [Cancel], [DestructiveTonal], [Text]는 세지 않는다.
     */
    Default,

    /** 권장 동작. 강조색으로 채운다. */
    Preferred,

    /** 삭제처럼 되돌리기 어려운 동작. [KineticaColors.destructive]로 채운다. */
    Destructive,

    /** 취소. 옅은 무채색 버튼이고, 가로로 놓이면 시작 쪽, 세로로 쌓이면 맨 아래에 온다. */
    Cancel,

    /**
     * 옅은 위험 동작. [KineticaColors.destructive]를 옅게 깔고 글자를 그 색으로 쓴다. "저장" 옆의 "버리기"처럼 권장 동작 곁에 놓는
     * 위험 동작에 쓴다. [KActionSheet]에서는 [Destructive]처럼 빨간 글자로 쓴다.
     */
    DestructiveTonal,

    /**
     * 바탕 없이 보조 글자 색으로만 쓰는 동작. "나중에", "계속 편집"처럼 조용한 선택에 쓴다. 글자 색은 표면의
     * [SurfaceStyle.contentMuted]이고, 없으면 글자 색을 옅게 쓴다. [KActionSheet]에서도 같은 보조 글자 색으로 쓴다.
     * [Cancel]과 달리 놓이는 자리를 옮기지 않는다.
     */
    Text,
}

/** [KAlertDialog]를 어떤 모양으로 띄울지 정한다. */
public enum class KDialogPresentation {
    /**
     * 창 폭이 600dp 미만이면 [Sheet], 그 이상이면 [Card]다. 열려 있는 동안 폴더블을 펼치거나 접어 창 폭이 기준을 넘으면
     * 그 자리에서 다른 모양으로 바뀐다. 이때 [KAlertDialog]의 content 칸은 새로 구성되므로 입력값은 호출한 쪽 상태에 둔다.
     */
    Auto,

    /** 화면 가운데 카드. [KDialog]와 같이 열린다. */
    Card,

    /** 아래에서 올라오는 시트. [KSheet]와 같이 손잡이를 끌어 닫고(`dragToDismiss`로 끌 수 있다), 내비게이션 바와 키보드를 피한다. */
    Sheet,
}

/**
 * [KAlertDialog]와 [KActionSheet]의 버튼 하나. 누르면 다이얼로그나 시트를 닫고 [onClick]을 부른다.
 * 닫기는 다이얼로그가 하므로 [onClick]에서 다시 닫지 않아도 된다. 닫기만 하는 취소 버튼은 [onClick]을 생략한다.
 *
 * @property icon 글자 앞 아이콘. 동작 시트와 팝오버에서만 쓴다.
 * @property enabled false면 흐리게 그리고 누를 수 없다.
 * @property dismissOnClick false면 [KAlertDialog]가 눌러도 닫지 않고 [onClick]만 부른다. 입력 검증에 실패했거나
 *   녹음 중지처럼 결과를 같은 창에 보여 줄 때 쓰고, 닫을 때는 호출한 쪽이 visible을 false로 바꾼다.
 *   [KActionSheet]는 고르면 끝나는 메뉴이므로 이 값과 상관없이 늘 닫는다.
 */
@Immutable
public data class KDialogAction(
    val label: String,
    val style: KDialogActionStyle = KDialogActionStyle.Default,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    val dismissOnClick: Boolean = true,
    val onClick: () -> Unit = {},
) {
    /** 1.0.0-alpha02까지의 주 생성자. 컴파일된 호출자를 위해 남긴다. */
    @Deprecated("dismissOnClick을 더한 주 생성자를 씁니다.", level = DeprecationLevel.HIDDEN)
    public constructor(
        label: String,
        style: KDialogActionStyle = KDialogActionStyle.Default,
        icon: ImageVector? = null,
        enabled: Boolean = true,
        onClick: () -> Unit = {},
    ) : this(label, style, icon, enabled, true, onClick)

    /** 1.0.0-alpha02까지의 copy. 컴파일된 호출자를 위해 남긴다. */
    @Deprecated("dismissOnClick을 더한 copy를 씁니다.", level = DeprecationLevel.HIDDEN)
    public fun copy(
        label: String = this.label,
        style: KDialogActionStyle = this.style,
        icon: ImageVector? = this.icon,
        enabled: Boolean = this.enabled,
        onClick: () -> Unit = this.onClick,
    ): KDialogAction = KDialogAction(label, style, icon, enabled, dismissOnClick, onClick)

    /** 1.0.0-alpha02까지 다섯 번째 구성 요소는 onClick이었다. 컴파일된 구조 분해 호출자를 위해 남긴다. */
    @Deprecated("component6을 씁니다.", level = DeprecationLevel.HIDDEN)
    @JvmName("component5")
    public fun legacyComponent5(): () -> Unit = onClick
}

/**
 * 제목, 본문, 버튼으로 된 알림 다이얼로그. [KDialog] 위에 버튼 배치를 정해 둔 프리셋이다.
 * 버튼이 둘이고 글자가 반 폭에 들어가면 나란히 놓고(취소는 시작 쪽), 셋이거나 글자가 길거나 [stacked]가 true면 세로로 쌓는다.
 * 넷 이상이면 늘 세로로 쌓는다. 세로로 쌓을 때는 권장 동작이 위, 취소가 맨 아래다([keepActionOrder]가 true면 넘긴 순서대로).
 * 큰 글자 설정에서도 버튼 글자가 잘리지 않도록 자동으로 쌓는다. 버튼이 없으면 버튼 줄 없이 그리고, 바깥을 누르거나 뒤로 가기로만 닫힌다.
 *
 * 버튼을 누르면 [onDismissRequest]를 부른 뒤 그 버튼의 동작을 실행한다. [KDialogAction.dismissOnClick]이 false인 버튼은
 * [onDismissRequest]를 부르지 않고 동작만 실행한다. 바깥을 누르거나 뒤로 가기를 해도 [onDismissRequest]를 부른다.
 * [KineticaScaffold] 밖에서는 [KDialog]처럼 대화 상자 창에 띄운다.
 *
 * ```kotlin
 * KAlertDialog(
 *     visible = asking, onDismissRequest = { asking = false }, title = "사진을 삭제할까요?", message = "휴지통에서 30일 뒤 지워집니다.",
 *     actions = listOf(
 *         KDialogAction("취소", KDialogActionStyle.Cancel),
 *         KDialogAction("삭제", KDialogActionStyle.Destructive) { delete() },
 *     ),
 * )
 * ```
 *
 * @param actions 버튼. 개수 제한은 없고 예외를 던지지 않는다. 비어 있으면 버튼 줄을 그리지 않는다.
 * @param icon 제목 위에 놓을 아이콘
 * @param stacked true면 늘 세로로 쌓고, false면 셋까지는 늘 나란히 놓는다. null이면 글자 길이를 보고 정한다. 넷 이상이면 이 값과 상관없이 쌓는다.
 * @param origin 카드가 커지기 시작하는 화면 좌표(px). [Modifier.kOrigin]으로 얻는다. 시트로 뜰 때는 쓰지 않는다.
 * @param iconTint [icon]과 그 뒤 원의 색. [Color.Unspecified]면 위험 동작이 있을 때 [KineticaColors.destructive], 없으면 강조색이다.
 * @param presentation 가운데 카드로 띄울지, 아래에서 올라오는 시트로 띄울지. 기본값은 카드다.
 * @param onDismissed [visible]이 false가 되어 닫힘 애니메이션이 끝나고 카드나 시트가 화면에서 사라진 뒤 한 번 부른다.
 *   버튼, 바깥 누름, 뒤로 가기, 끌어 내리기 어느 쪽으로 닫아도 같고, '애니메이션 삭제'가 켜져 있으면 닫은 바로 다음 프레임에 부른다.
 *   열린 채로 구성에서 빠지거나 [KDialogPresentation.Auto]가 열린 채 카드와 시트를 바꿀 때는 부르지 않는다.
 * @param keepActionOrder true면 취소를 옮기지 않고 [actions]를 넘긴 순서대로 놓는다. 세로로 쌓으면 위에서 아래로, 나란히 놓으면
 *   시작 쪽부터다. false면 취소를 나란히 놓을 때 시작 쪽, 쌓을 때 맨 아래로 옮긴다.
 * @param dragToDismiss 시트로 뜰 때 끌어 닫기를 쓸지. false면 [KSheet]처럼 끌어도 닫히지 않고 손잡이를 그리지 않는다.
 *   바깥 누름과 뒤로 가기로는 그대로 닫힌다. 카드로 뜰 때는 쓰지 않는다.
 * @param appearScale 카드로 뜰 때 열리기 시작하는 카드 크기 배율. [KDialog]의 `appearScale`과 같고 기본값은
 *   [KSheetDefaults.DialogAppearScale](0.6)이다. 1에 가까울수록 커지는 움직임이 줄고 [origin]에서 미끄러져 들어오며 나타나는 쪽에 가깝다.
 *   시트로 뜰 때는 쓰지 않는다.
 * @param content 본문과 버튼 사이에 넣는 내용(입력란, 녹음 파형 등). 이 칸이 있으면 본문 전체가 세로로 스크롤되므로,
 *   키보드가 올라와 남은 높이가 모자라도 입력란과 버튼이 가려지지 않는다. 안에 세로 스크롤 목록을 넣으려면 높이를 정해 준다.
 */
@Composable
public fun KAlertDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    actions: List<KDialogAction>,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector? = null,
    stacked: Boolean? = null,
    origin: Offset? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
    iconTint: Color = Color.Unspecified,
    presentation: KDialogPresentation = KDialogPresentation.Card,
    onDismissed: (() -> Unit)? = null,
    keepActionOrder: Boolean = false,
    dragToDismiss: Boolean = true,
    appearScale: Float = KSheetDefaults.DialogAppearScale,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val sheet = when (presentation) {
        KDialogPresentation.Card -> false
        KDialogPresentation.Sheet -> true
        KDialogPresentation.Auto -> rememberKWindowLayout().isCompact
    }
    val spec = AlertSpec(title, message, icon, iconTint, AlertButtons(actions, stacked, keepActionOrder))
    if (sheet) {
        KSheet(visible, onDismissRequest, modifier, surface, title = title, onDismissed = onDismissed, dragToDismiss = dragToDismiss) {
            Spacer(Modifier.height(SHEET_TOP_GAP))
            AlertBody(spec, surface, onDismissRequest, content)
        }
    } else {
        KDialog(
            visible,
            onDismissRequest,
            modifier,
            surface,
            origin = origin,
            title = title,
            onDismissed = onDismissed,
            appearScale = appearScale,
        ) {
            AlertBody(spec, surface, onDismissRequest, content)
        }
    }
}

/** 1.0.0-alpha04의 시그니처. 컴파일된 호출자를 위해 남긴다. */
@Deprecated("바이너리 호환용", level = DeprecationLevel.HIDDEN)
@Composable
public fun KAlertDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    actions: List<KDialogAction>,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector? = null,
    stacked: Boolean? = null,
    origin: Offset? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
    iconTint: Color = Color.Unspecified,
    presentation: KDialogPresentation = KDialogPresentation.Card,
    onDismissed: (() -> Unit)? = null,
    keepActionOrder: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    KAlertDialog(
        visible, onDismissRequest, title, actions, modifier, message, icon, stacked, origin, surface, iconTint, presentation,
        onDismissed, keepActionOrder, content = content,
    )
}

/** 1.0.0-alpha03까지의 시그니처. 컴파일된 호출자를 위해 남긴다. */
@Deprecated("onDismissed, keepActionOrder를 더한 KAlertDialog를 씁니다.", level = DeprecationLevel.HIDDEN)
@Composable
public fun KAlertDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    actions: List<KDialogAction>,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector? = null,
    stacked: Boolean? = null,
    origin: Offset? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
    iconTint: Color = Color.Unspecified,
    presentation: KDialogPresentation = KDialogPresentation.Card,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    KAlertDialog(
        visible, onDismissRequest, title, actions, modifier, message, icon, stacked, origin, surface, iconTint, presentation,
        content = content,
    )
}

/** 1.0.0-alpha02까지의 시그니처. 컴파일된 호출자를 위해 남긴다. */
@Deprecated("iconTint, presentation, content를 더한 KAlertDialog를 씁니다.", level = DeprecationLevel.HIDDEN)
@Composable
public fun KAlertDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    actions: List<KDialogAction>,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector? = null,
    stacked: Boolean? = null,
    origin: Offset? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    KAlertDialog(visible, onDismissRequest, title, actions, modifier, message, icon, stacked, origin, surface, content = null)
}

/** 카드와 시트가 같이 쓰는 본문 값. 파라미터 수를 줄이려고 묶었다. */
private class AlertSpec(val title: String, val message: String?, val icon: ImageVector?, val iconTint: Color, val buttons: AlertButtons) {
    val actions: List<KDialogAction> get() = buttons.actions
}

/** 버튼 줄의 값. [keepOrder]면 취소를 옮기지 않는다. */
private class AlertButtons(val actions: List<KDialogAction>, val stacked: Boolean?, val keepOrder: Boolean)

@Composable
private fun AlertBody(
    spec: AlertSpec,
    surface: SurfaceStyle,
    onDismissRequest: () -> Unit,
    content: (@Composable ColumnScope.() -> Unit)?,
) {
    // content 칸이 없으면 스크롤을 붙이지 않아 예전과 같은 의미 트리를 둔다.
    val scroll = if (content != null) Modifier.verticalScroll(rememberScrollState()) else Modifier
    Column(Modifier.fillMaxWidth().then(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
        spec.icon?.let {
            val tint = when {
                spec.iconTint.isSpecified -> spec.iconTint
                spec.actions.any { action -> action.style.destructive } -> KineticaTheme.colors.destructive
                else -> KineticaTheme.colors.accent
            }
            Box(Modifier.size(ICON_CIRCLE).background(tint.copy(alpha = ICON_FILL), CircleShape), contentAlignment = Alignment.Center) {
                Glyph(it, tint, size = 26.dp)
            }
            Spacer(Modifier.height(14.dp))
        }
        ThemedText(
            spec.title,
            Modifier.semantics { heading() },
            style = TextStyle(
                color = surface.contentColor,
                fontSize = 20.sp,
                fontWeight = KineticaTheme.design.title(FontWeight.SemiBold),
                textAlign = TextAlign.Center,
            ),
        )
        spec.message?.let {
            Spacer(Modifier.height(8.dp))
            ThemedText(
                it,
                style = TextStyle(
                    color = surface.muted(MESSAGE_ALPHA),
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }
        content?.let {
            Spacer(Modifier.height(CONTENT_GAP))
            it()
        }
        if (spec.actions.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            DialogButtons(spec.buttons, surface, onDismissRequest)
        }
    }
}

/** 버튼을 나란히 또는 세로로 놓는다. 글자가 반 폭을 넘거나 셋이면 쌓고, 넷 이상이면 늘 쌓는다. */
@Composable
private fun DialogButtons(spec: AlertButtons, surface: SurfaceStyle, onDismiss: () -> Unit) {
    val actions = spec.actions
    val stacked = spec.stacked
    // 취소를 앞에 두고 한 번만 내보낸다. 세로로 쌓을 때는 취소를 맨 아래에 놓는다. TalkBack 순서는 놓인 위치를 따른다.
    val ordered = if (spec.keepOrder) actions else actions.sortedBy { if (it.style == KDialogActionStyle.Cancel) 0 else 1 }
    val cancels = if (spec.keepOrder) 0 else actions.count { it.style == KDialogActionStyle.Cancel }
    // 옅은 위험과 글자 버튼은 곁에 놓이는 보조 동작이라, 일반 동작이 하나뿐이면 그 일반 동작을 채운다.
    val primaryCount = actions.count { it.style in PrimaryStyles }
    val emphasized = actions.any { it.style == KDialogActionStyle.Preferred || it.style == KDialogActionStyle.Destructive }
    val alone = primaryCount == 1 && !emphasized
    Layout(
        content = { ordered.forEach { DialogButton(it, alone, surface, onDismiss) } },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val count = actions.size
        val gap = BUTTON_GAP.roundToPx()
        val width = constraints.maxWidth
        val half = (width - gap * (count - 1)) / count
        val fits = count <= 2 && measurables.all { it.maxIntrinsicWidth(constraints.maxHeight) <= half }
        val vertical = count > MAX_ROW_ACTIONS || (stacked ?: !fits)
        if (!vertical) {
            val placeables = measurables.map { it.measure(Constraints.fixedWidth(half)) }
            val height = placeables.maxOf { it.height }
            layout(width, height) {
                var x = 0
                placeables.forEach {
                    it.placeRelative(x, (height - it.height) / 2)
                    x += half + gap
                }
            }
        } else {
            val measured = measurables.map { it.measure(Constraints.fixedWidth(width)) }
            val placeables = measured.drop(cancels) + measured.take(cancels)
            val height = placeables.sumOf { it.height } + gap * (count - 1)
            layout(width, height) {
                var y = 0
                placeables.forEach {
                    it.placeRelative(0, y)
                    y += it.height + gap
                }
            }
        }
    }
}

@Composable
private fun DialogButton(action: KDialogAction, alone: Boolean, surface: SurfaceStyle, onDismiss: () -> Unit) {
    val (variant, tone) = action.look(alone)
    KButton(
        action.label,
        onClick = {
            if (action.dismissOnClick) onDismiss()
            action.onClick()
        },
        variant = variant,
        tone = tone,
        icon = action.icon,
        enabled = action.enabled,
        surface = surface,
        contentColor = if (action.style == KDialogActionStyle.Text) surface.muted(MESSAGE_ALPHA) else Color.Unspecified,
    )
}

/** 동작 역할에 맞는 버튼 모양과 색. [alone]이면 강조할 다른 동작이 없는 하나뿐인 일반 동작이다. */
internal fun KDialogAction.look(alone: Boolean): Pair<KVariant, KTone> = when (style) {
    KDialogActionStyle.Preferred -> KVariant.Filled to KTone.Accent
    KDialogActionStyle.Destructive -> KVariant.Filled to KTone.Destructive
    KDialogActionStyle.Cancel -> KVariant.Tonal to KTone.Neutral
    KDialogActionStyle.Default -> (if (alone) KVariant.Filled else KVariant.Tonal) to KTone.Accent
    KDialogActionStyle.DestructiveTonal -> KVariant.Tonal to KTone.Destructive
    KDialogActionStyle.Text -> KVariant.Text to KTone.Neutral
}

/** 위험 동작인지. 아이콘 색과 동작 시트의 빨간 글자를 정한다. */
internal val KDialogActionStyle.destructive: Boolean
    get() = this == KDialogActionStyle.Destructive || this == KDialogActionStyle.DestructiveTonal

private val PrimaryStyles = setOf(KDialogActionStyle.Default, KDialogActionStyle.Preferred, KDialogActionStyle.Destructive)

// 이보다 많으면 stacked가 false여도 한 줄에 다 놓을 폭이 없으므로 쌓는다.
private const val MAX_ROW_ACTIONS = 3
private const val MESSAGE_ALPHA = 0.72f
private const val ICON_FILL = 0.14f
private val ICON_CIRCLE = 52.dp
private val BUTTON_GAP = 10.dp
private val CONTENT_GAP = 18.dp

// 시트 손잡이 아래 여백. 손잡이와 아이콘(또는 제목)이 붙어 보이지 않게 한다.
private val SHEET_TOP_GAP = 8.dp
