// 파일 이름은 [KCallScreen]의 영상과 음성 무대를 모아 둔 곳임을 나타낸다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/** 무대 배치에 쓰는 값 */
internal class StageSpec(val focusKey: String?, val chrome: KCallChromeState, val padding: PaddingValues)

/**
 * 바 높이만큼 무대를 비운다. 바가 숨으면 시스템 바 인셋만 남기고 넓어진다. [side]면 조작 막대가 끝 쪽 세로 막대이므로
 * 아래 대신 끝 쪽을 막대 폭만큼 비운다.
 */
@Composable
internal fun rememberStagePadding(ui: CallUi, chrome: KCallChromeState, side: Boolean = false): PaddingValues {
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
    val visible = chrome.isVisible
    val top by animateDpAsState(
        if (visible) {
            with(density) { ui.topHeight.toDp() }.coerceAtLeast(insets.calculateTopPadding())
        } else {
            insets.calculateTopPadding() +
                STAGE_GAP
        },
        label = "stage-top",
    )
    val bottom by animateDpAsState(
        if (visible && !side) {
            with(density) { ui.bottomHeight.toDp() }.coerceAtLeast(insets.calculateBottomPadding())
        } else {
            insets.calculateBottomPadding() +
                STAGE_GAP
        },
        label = "stage-bottom",
    )
    val endInset = insets.calculateEndPadding(direction)
    val end by animateDpAsState(
        if (visible && side) with(density) { ui.sideWidth.toDp() }.coerceAtLeast(endInset) else endInset + STAGE_GAP,
        label = "stage-end",
    )
    return PaddingValues(
        start = insets.calculateStartPadding(direction) + STAGE_GAP,
        top = top,
        end = end,
        bottom = bottom,
    )
}

/** 영상 무대 한 층. 두 층이 같은 [grid] 상태로 같은 자리에 그린다. */
@Composable
internal fun VideoStage(inputs: CallInputs, ui: CallUi, grid: KParticipantGridState, stage: StageSpec, layer: KCallLayer) {
    val state = inputs.state
    val local = state.local
    val ordered = listOfNotNull(local) + state.participants.filterNot { it.isLocal }
    val other = state.remotes.firstOrNull { !it.isScreenShare }
    val self = if (ui.swapped) other?.id else local?.id
    val labels = TileLabels(
        stringResource(R.string.kinetica_ui_call_show_controls),
        stringResource(R.string.kinetica_ui_call_spotlight),
        stringResource(R.string.kinetica_ui_call_swap),
        local?.let { stringResource(R.string.kinetica_ui_call_you_named, it.name) },
    )
    KParticipantGrid(
        ordered,
        key = { it.id },
        modifier = Modifier.fillMaxSize(),
        state = grid,
        focusKey = stage.focusKey,
        selfKey = self,
        contentPadding = stage.padding,
        layer = layer,
        columns = inputs.gridColumns,
    ) { p, role -> StageTile(inputs, ui, stage, p, role, layer, labels) }
}

/** 타일 동작 이름과 내 이름표 */
private class TileLabels(val showControls: String, val spotlight: String, val swap: String, val you: String?)

/**
 * 무대 타일 하나. 막대가 숨어 있으면 누르기는 막대를 꺼내기만 한다. 막대가 보이면 격자와 띠 칸은 크게 보기(고정),
 * 1:1 작은 창은 화면 바꾸기, 크게 보이는 타일은 막대 숨기기와 보이기다.
 */
@Composable
private fun StageTile(
    inputs: CallInputs,
    ui: CallUi,
    stage: StageSpec,
    p: KCallParticipant,
    role: KTileRole,
    layer: KCallLayer,
    labels: TileLabels,
) {
    val togglePin: () -> Unit = { ui.pinnedId = if (ui.pinnedId == p.id) null else p.id }
    val (click, label) = tileTap(role, stage.chrome, togglePin, { ui.swapped = !ui.swapped }, labels)
    val full = role == KTileRole.Full
    KParticipantTile(
        if (p.isLocal) labels.you ?: p.name else p.name,
        Modifier.fillMaxSize(),
        video = if (p.isVideoOn || p.isScreenShare) ({ inputs.video(this, p) }) else null,
        avatar = p.avatar,
        muted = p.isMuted,
        speaking = p.isSpeaking,
        audioLevel = { inputs.audioLevel(p.id) },
        handRaised = p.hasRaisedHand,
        quality = p.networkQuality,
        pinned = p.id == ui.pinnedId,
        screenShare = p.isScreenShare,
        mirror = p.isLocal && inputs.state.isFrontCamera,
        onClick = click,
        onClickLabel = label,
        onTogglePin = togglePin.takeIf { !p.isScreenShare && !full && role != KTileRole.Floating },
        nameTag = inputs.nameTag?.let { tag -> { tag(p) } },
        speakingBorder = !full,
        layer = layer,
        shape = tileShape(role),
        labelPadding = if (full) stage.padding.only(bottom = true) else PaddingValues(0.dp),
        surface = inputs.surface,
    )
}

/** 타일을 눌렀을 때 할 일과 그 이름 */
private fun tileTap(
    role: KTileRole,
    chrome: KCallChromeState,
    pin: () -> Unit,
    swap: () -> Unit,
    labels: TileLabels,
): Pair<() -> Unit, String> = when {
    !chrome.isVisible -> chrome::show to labels.showControls
    role == KTileRole.Grid || role == KTileRole.Strip -> pin to labels.spotlight
    role == KTileRole.Floating -> swap to labels.swap
    else -> chrome::toggle to labels.showControls
}

/** 역할별 타일 모양. 화면을 꽉 채운 타일은 각지고, 띠 칸은 작아서 반지름을 줄인다. */
@Composable
private fun tileShape(role: KTileRole): androidx.compose.ui.graphics.Shape = when (role) {
    KTileRole.Full -> RectangleShape
    KTileRole.Strip -> roundedShapeFor(STRIP_RADIUS) { card }
    else -> shapeFor { card }
}

/** [PaddingValues]에서 아래쪽만 남긴다. */
private fun PaddingValues.only(bottom: Boolean): PaddingValues = PaddingValues(bottom = if (bottom) calculateBottomPadding() else 0.dp)

/** 크게 볼 사람. 화면 공유, 고정한 사람, 발표자 보기의 말하는 사람(없으면 첫 상대) 순이다. 없으면 격자다. */
internal fun focusOf(state: KCallState, ui: CallUi, speaker: String?): String? {
    val speakerView = ui.layout == KCallLayout.Speaker && state.participants.size >= SPEAKER_MIN
    return state.remotes.firstOrNull { it.isScreenShare }?.id ?: ui.pinnedId
        ?: (speaker ?: state.remotes.firstOrNull()?.id).takeIf { speakerView }
}

private val STAGE_GAP = 8.dp
private val STRIP_RADIUS = 14.dp
