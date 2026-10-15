package com.naury.kinetica.ui

import android.view.WindowManager
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

/**
 * [visible]인 동안, 그리고 닫힘 애니메이션이 끝날 때까지 [content]를 띄운다. [content]는 열림 전환(`true`가 열린 상태)을 받아 애니메이션을 맞춘다.
 *
 * [GlassScaffold] 안에서는 그 오버레이 레이어에 그린다. 밖에서는 [kind]에 따라 창을 따로 띄운다. [OverlayKind.Modal]은 전체 화면 대화 상자 창,
 * [OverlayKind.Top]과 [OverlayKind.Bottom]은 내용 크기의 팝업 창, [OverlayKind.InPlace]는 호출한 자리다.
 * 그래서 호스트가 `Column` 안에서 불러도 주변 배치에 끼어들지 않는다(팝업 창의 자리 표시로 크기 0인 요소 하나만 남는다).
 *
 * [edgeInsets]는 [OverlayKind.Top]과 [OverlayKind.Bottom]이 피할 시스템 영역이다. [content]는 이 여백을 직접 넣지 않는다.
 */
@Composable
internal fun GlassOverlay(
    visible: Boolean,
    kind: OverlayKind = OverlayKind.Modal,
    edgeInsets: WindowInsets = NoInsets,
    content: @Composable (Transition<Boolean>) -> Unit,
) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible
    val shown = state.currentState || state.targetState
    if (!shown) return
    val latest = rememberUpdatedState(content)
    val host = LocalGlassHost.current
    val body: @Composable () -> Unit = { latest.value(rememberTransition(state, label = "glass-overlay")) }
    when {
        host != null -> HostedOverlay(host, kind, edgeInsets, body)
        kind == OverlayKind.Modal -> ModalWindow { ModalFocus(null) { body() } }
        kind == OverlayKind.InPlace -> body()
        else -> EdgeWindow(kind == OverlayKind.Top, edgeInsets, body)
    }
}

@Composable
private fun HostedOverlay(host: GlassOverlayHost, kind: OverlayKind, edgeInsets: WindowInsets, body: @Composable () -> Unit) {
    val latestBody = rememberUpdatedState(body)
    val latestInsets = rememberUpdatedState(edgeInsets)
    // content는 한 번만 정하고 최신 람다는 상태로 읽는다. 컴포지션 도중에 오버레이 상태를 다시 쓰지 않기 위해서다.
    val entry = remember {
        OverlayEntry(Any(), modal = kind == OverlayKind.Modal).also { entry ->
            entry.content = {
                CompositionLocalProvider(LocalOverlayEntry provides entry) {
                    when (kind) {
                        OverlayKind.Modal -> ModalFocus(host) { latestBody.value() }

                        OverlayKind.Top, OverlayKind.Bottom -> Box(
                            Modifier.fillMaxSize().windowInsetsPadding(latestInsets.value),
                            contentAlignment = if (kind == OverlayKind.Top) Alignment.TopCenter else Alignment.BottomCenter,
                        ) { latestBody.value() }

                        OverlayKind.InPlace -> latestBody.value()
                    }
                }
            }
        }
    }
    DisposableEffect(entry) {
        host.entries += entry
        onDispose {
            entry.pushBack = 0f
            host.entries -= entry
        }
    }
}

/**
 * 키보드로 쓰는 중이면 모달 내용으로 초점을 옮기고, 닫히면 열기 전에 초점이 있던 요소로 돌려준다.
 * 터치로 쓰는 중이거나 안쪽에 초점을 받을 요소가 없으면 뒤 화면의 초점만 걷어 낸다. 터치에서 옮기지 않는 이유는
 * 시트 안 입력란이 저절로 초점을 받아 키보드가 튀어나오지 않게 하려는 것이다.
 */
@Composable
private fun ModalFocus(host: GlassOverlayHost?, content: @Composable () -> Unit) {
    val requester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val inputMode = LocalInputModeManager.current
    Box(Modifier.focusRequester(requester).focusGroup()) { content() }
    LaunchedEffect(Unit) {
        // 첫 배치가 끝나야 초점 탐색이 요소 위치를 알 수 있다.
        withFrameNanos { }
        val saved = host?.saveBackgroundFocus() == true
        val moved = inputMode.inputMode == InputMode.Keyboard && requester.requestFocus(FocusDirection.Enter)
        if (!moved && saved) focusManager.clearFocus()
    }
    if (host != null) {
        DisposableEffect(Unit) { onDispose { host.restoreBackgroundFocus() } }
    }
}

/**
 * 모달이 열려 있는 동안([blocked]) 이 요소 아래를 TalkBack에서 숨기고 키보드 초점이 들어가지 못하게 한다. [requester]는 이 그룹에 붙어
 * 모달이 닫힐 때 초점을 되돌리는 데 쓴다. 의미 노드를 지우는 요소는 맨 끝에 두어, 열고 닫을 때 앞쪽 초점 요소가 다시 만들어지지 않게 한다.
 */
internal fun Modifier.modalBackground(requester: FocusRequester, blocked: Boolean): Modifier = this
    .focusRequester(requester)
    .focusProperties { onEnter = { if (blocked) cancelFocusChange() } }
    .focusGroup()
    .then(if (blocked) Modifier.clearAndSetSemantics { } else Modifier.semantics { })

/**
 * [open]이 true가 되면 [background]에 있던 초점을 기억하고, 키보드로 쓰는 중이면 [panel] 안으로 옮긴다. 옮기지 못하면 뒤쪽 초점만 걷는다.
 * false가 되면 기억해 둔 요소로 초점을 돌려준다.
 */
@Composable
internal fun ModalFocusEffect(open: Boolean, background: FocusRequester, panel: FocusRequester) {
    val focusManager = LocalFocusManager.current
    val inputMode = LocalInputModeManager.current
    val saved = remember { booleanArrayOf(false) }
    LaunchedEffect(open) {
        if (open) {
            withFrameNanos { }
            saved[0] = background.saveFocusedChild()
            val moved = inputMode.inputMode == InputMode.Keyboard && panel.requestFocus(FocusDirection.Enter)
            if (!moved && saved[0]) focusManager.clearFocus()
        } else if (saved[0]) {
            saved[0] = false
            background.restoreFocusedChild()
        }
    }
}

/**
 * 전체 화면 대화 상자 창. 창의 어둡게 하기와 창 애니메이션은 끄고 내용이 직접 그린다. 창은 시스템 바 뒤까지 그리므로
 * 상태 표시줄, 내비게이션 바, 키보드 여백은 안쪽 내용이 [WindowInsets]로 처리한다. 뒤로 가기는 내용의 BackHandler가 받는다.
 */
@Composable
private fun ModalWindow(content: @Composable () -> Unit) {
    Dialog(onDismissRequest = {}, properties = ModalWindowProperties) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            window?.apply {
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(0f)
                setWindowAnimations(0)
            }
            onDispose { }
        }
        Box(Modifier.fillMaxSize()) { content() }
    }
}

/**
 * 창 위쪽이나 아래쪽 가장자리에 붙는 내용 크기의 팝업 창. 호스트 화면의 터치를 막지 않는다.
 * 위치는 호스트 Compose 화면의 경계에서 [insets]를 뺀 영역을 기준으로 정한다. 그래서 edge-to-edge를 쓰는 화면(인셋이 Compose에 온다)과
 * 쓰지 않는 화면(화면 자체가 시스템 바 안쪽에 있다) 모두 여백이 한 번만 들어간다.
 */
@Composable
private fun EdgeWindow(top: Boolean, insets: WindowInsets, content: @Composable () -> Unit) {
    val area = remember { EdgeArea() }
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    area.insets = IntRect(
        insets.getLeft(density, direction),
        insets.getTop(density),
        insets.getRight(density, direction),
        insets.getBottom(density),
    )
    val room = with(density) { SHADOW_ROOM.roundToPx() }
    val provider = remember(area, top, room) { EdgePosition(area, top, room) }
    Box(
        Modifier.onGloballyPositioned {
            val root = it.findRootCoordinates()
            area.anchor = it.positionInWindow()
            area.root = IntRect(root.positionInWindow().round(), root.size)
        },
    ) {
        Popup(popupPositionProvider = provider, properties = EdgeWindowProperties) {
            // 그림자가 창 가장자리에서 잘리지 않게 둘레에 여유를 두고, 위치를 그만큼 바깥으로 옮긴다.
            Box(Modifier.padding(SHADOW_ROOM)) { content() }
        }
    }
}

/** [EdgeWindow]의 위치 계산 재료. [anchor]는 호출한 자리, [root]는 호스트 Compose 화면 경계로 둘 다 창 좌표(px)이고, [insets]는 네 변의 인셋(px)이다. */
@Stable
internal class EdgeArea {
    var anchor by mutableStateOf(Offset.Zero)
    var root by mutableStateOf(IntRect.Zero)
    var insets by mutableStateOf(IntRect.Zero)
}

/** [root]에서 [insets]를 뺀 영역의 위나 아래 가운데에 팝업을 둔다. 둘레 여유 [room](px)만큼 바깥으로 옮긴다. */
internal class EdgePosition(private val area: EdgeArea, private val top: Boolean, private val room: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        // anchorBounds는 팝업이 기대하는 좌표계(보통 부모 창, 대화 상자 안에 겹치면 화면)이므로 창 좌표와의 차이만큼 옮긴다.
        val shiftX = anchorBounds.left - area.anchor.x.roundToInt()
        val shiftY = anchorBounds.top - area.anchor.y.roundToInt()
        val root = area.root
        val insets = area.insets
        val left = root.left + insets.left
        val right = root.right - insets.right
        val x = (left + right - popupContentSize.width) / 2
        val y = if (top) root.top + insets.top - room else root.bottom - insets.bottom - popupContentSize.height + room
        return IntOffset(x + shiftX, y + shiftY)
    }
}

private fun Offset.round(): IntOffset = IntOffset(x.roundToInt(), y.roundToInt())

/** 열린 시트가 뒤쪽 콘텐츠를 얼마나 밀어낼지(0..1) 알린다. [GlassScaffold] 밖에서는 아무 일도 하지 않는다. */
@Composable
internal fun ReportPushBack(amount: Float) {
    LocalOverlayEntry.current?.pushBack = amount
}

private val LocalOverlayEntry = staticCompositionLocalOf<OverlayEntry?> { null }

private val NoInsets = WindowInsets(0, 0, 0, 0)
private val SHADOW_ROOM = 16.dp

private val ModalWindowProperties = DialogProperties(
    dismissOnBackPress = false,
    dismissOnClickOutside = false,
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = false,
)

private val EdgeWindowProperties = PopupProperties(
    focusable = false,
    dismissOnBackPress = false,
    dismissOnClickOutside = false,
    clippingEnabled = false,
)
