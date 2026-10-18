// 파일 이름은 대표 컴포저블인 KParticipantTile을 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 통화 타일을 어느 층으로 그릴지. [KineticaScaffold]의 `background`에 영상을 두고 그 위에 글래스 바를 띄울 때, 매 프레임 움직이는
 * 장식(글로우 테두리, 말하기 물결, 소리 막대)이 배경 기록에 들어가면 모든 글래스 블러를 매 프레임 다시 한다. 그래서 같은 타일을
 * `background`에는 [Video]로, `content`에는 [Overlay]로 같은 자리에 두 번 그린다(docs/35 '영상 위 글래스').
 */
public enum class KCallLayer {
    /** 모두 한 번에 그린다. 글래스 배경 기록과 상관없는 곳에서 쓴다. */
    All,

    /** 영상(또는 영상이 꺼졌을 때 아바타)과 바탕만 그린다. 누르기와 TalkBack은 받지 않는다. */
    Video,

    /** 영상을 뺀 나머지(이름표, 아이콘, 글로우 테두리, 말하기 물결)와 누르기, TalkBack을 맡는다. */
    Overlay,
}

/** 누르는 동안 줄어드는 배율. [KParticipantGrid]가 두 층이 같은 값을 읽도록 참가자마다 하나씩 들고 있는다. */
@Stable
internal class TilePress {
    val scale = Animatable(1f)
}

/**
 * 참가자 타일 하나. [video]가 있으면 영상을, 없으면(카메라 꺼짐) 마지막 프레임을 남기지 않고 아바타를 그린다. 아래쪽 이름표에는
 * 마이크 꺼짐 아이콘이나 말하는 동안 소리 막대, 약한 연결 표시가 붙고, 손을 들면 위쪽에 손 배지가 튀어나온다.
 * 말하는 동안에는 둘레에 강조색 글로우 테두리가 돈다.
 *
 * 누르면 [onClick], 길게 누르면 [onTogglePin]을 부르고, 누르는 동안 살짝 작아진다. TalkBack은 타일 하나로 '민지, 말하는 중, 마이크 꺼짐'처럼
 * 모든 상태를 한 번에 읽고, 고정과 해제는 사용자 지정 동작으로 한다. 고정된 타일은 위쪽 끝에 고정 표시가 붙는다.
 *
 * 영상은 호스트가 [video] 슬롯에 `AndroidView`(TextureView, SurfaceView)나 Compose 렌더러로 넣는다. 키트는 영상을 다루지 않는다.
 *
 * ```kotlin
 * KParticipantTile(
 *     name = p.name, muted = p.muted, speaking = p.speaking, audioLevel = { levels[p.id] ?: 0f },
 *     video = if (p.cameraOn) ({ VideoRenderer(p.track) }) else null,
 *     onClick = { spotlight(p.id) }, onTogglePin = { pin(p.id) },
 * )
 * ```
 *
 * @param video 영상 슬롯. 타일 크기를 꽉 채워 그린다. null이면 아바타를 그린다.
 * @param avatar 영상이 없을 때 그릴 아바타. null이면 [name]의 이니셜이다.
 * @param audioLevel 소리 크기(0..1). 그리기 단계에서 읽는다.
 * @param mirror 영상을 좌우로 뒤집는다. 전면 카메라의 내 영상에 쓴다. `SurfaceView`는 Compose 변환을 따르지 않으므로 렌더러에서 뒤집는다.
 * @param screenShare 화면 공유 타일. 이름표에 '(화면)'을 붙이고 글로우 테두리를 그리지 않는다.
 * @param onClickLabel TalkBack이 [onClick]을 읽을 이름(예: '크게 보기')
 * @param onTogglePin 고정과 해제. null이면 길게 누르기와 고정 동작이 없다. [onClick]이 없으면 짧게 눌러도 고정한다.
 * @param nameTag 이름표를 바꿀 슬롯. null이면 기본 이름표다. 타일 아래쪽 시작 모서리에 놓인다.
 * @param speakingBorder 말하는 동안 글로우 테두리를 그릴지. 화면을 꽉 채운 타일에서는 끄고 이름표의 소리 막대만 남긴다.
 * @param layer 그릴 층. [KCallLayer] 참고
 * @param labelPadding 이름표를 더 안쪽으로 옮길 여백. 화면을 꽉 채운 타일에서 조작 막대와 시스템 바를 피할 때 쓴다.
 */
@Composable
public fun KParticipantTile(
    name: String,
    modifier: Modifier = Modifier,
    video: (@Composable BoxScope.() -> Unit)? = null,
    avatar: KAvatarData? = null,
    muted: Boolean = false,
    speaking: Boolean = false,
    audioLevel: () -> Float = { 0f },
    handRaised: Boolean = false,
    quality: KNetworkQuality = KNetworkQuality.Unknown,
    pinned: Boolean = false,
    screenShare: Boolean = false,
    mirror: Boolean = false,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    onTogglePin: (() -> Unit)? = null,
    nameTag: (@Composable BoxScope.() -> Unit)? = null,
    speakingBorder: Boolean = true,
    layer: KCallLayer = KCallLayer.All,
    shape: Shape = shapeFor { card },
    labelPadding: PaddingValues = PaddingValues(0.dp),
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val own = remember { TilePress() }
    val press = LocalTilePress.current ?: own
    ParticipantTileImpl(
        TileSpec(
            name,
            avatar,
            muted,
            speaking,
            audioLevel,
            handRaised,
            quality,
            pinned,
            screenShare,
            mirror,
            video != null,
            speakingBorder,
        ),
        TileActions(onClick, onClickLabel, onTogglePin),
        TileLook(layer, shape, labelPadding, surface, nameTag),
        press,
        modifier,
        video,
    )
}

/** 타일이 보여 줄 상태. [KParticipantTile]의 파라미터를 안쪽 함수에 한 덩어리로 넘기려고 묶는다. */
@Suppress("LongParameterList") // 공개 함수의 파라미터를 그대로 옮겨 담는 값 묶음이다.
internal class TileSpec(
    val name: String,
    val avatar: KAvatarData?,
    val muted: Boolean,
    val speaking: Boolean,
    val audioLevel: () -> Float,
    val handRaised: Boolean,
    val quality: KNetworkQuality,
    val pinned: Boolean,
    val screenShare: Boolean,
    val mirror: Boolean,
    val hasVideo: Boolean,
    val border: Boolean = true,
)

/** 타일 동작 */
internal class TileActions(val onClick: (() -> Unit)?, val onClickLabel: String?, val onTogglePin: (() -> Unit)?)

/** 타일 모양과 층 */
internal class TileLook(
    val layer: KCallLayer,
    val shape: Shape,
    val labelPadding: PaddingValues,
    val surface: SurfaceStyle,
    val nameTag: (@Composable BoxScope.() -> Unit)?,
)

@Composable
internal fun ParticipantTileImpl(
    spec: TileSpec,
    actions: TileActions,
    look: TileLook,
    press: TilePress,
    modifier: Modifier,
    video: (@Composable BoxScope.() -> Unit)?,
) {
    val layer = look.layer
    BoxWithConstraints(
        modifier.graphicsLayer {
            scaleX = press.scale.value
            scaleY = press.scale.value
        },
    ) {
        val compact = maxWidth < COMPACT_TILE
        val avatarSize = (minOf(maxWidth, maxHeight) * AVATAR_RATIO).coerceIn(MIN_AVATAR, MAX_AVATAR)
        if (layer != KCallLayer.Overlay) TileVideo(spec, look, avatarSize, video)
        if (layer != KCallLayer.Video) TileOverlay(spec, actions, look, press, compact, avatarSize)
    }
}

@Composable
private fun TileVideo(spec: TileSpec, look: TileLook, avatarSize: Dp, video: (@Composable BoxScope.() -> Unit)?) {
    val surface = look.surface
    val back = lerp(surface.inkBackground(), surface.contentColor, TILE_TINT)
    Box(Modifier.fillMaxSize().clip(look.shape).background(back), contentAlignment = Alignment.Center) {
        if (video != null) {
            Box(Modifier.fillMaxSize().graphicsLayer { if (spec.mirror) scaleX = -1f }, content = video)
        } else {
            CallAvatar(spec.avatar ?: KAvatarData(spec.name), avatarSize)
        }
    }
}

@Composable
private fun TileOverlay(spec: TileSpec, actions: TileActions, look: TileLook, press: TilePress, compact: Boolean, avatarSize: Dp) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val still = reducedMotion()
    LaunchedEffect(pressed, still) {
        val target = if (pressed && !still) PRESSED_SCALE else 1f
        press.scale.animateTo(target, spring(dampingRatio = PRESS_DAMPING, stiffness = Spring.StiffnessMedium))
    }
    val glowOn = spec.speaking && !spec.screenShare && spec.border
    val glow by animateFloatAsState(if (glowOn) 1f else 0f, tween(GLOW_FADE_MS), label = "tile-glow")
    val accent = KineticaTheme.colors.accent
    val glowColors = remember(accent) { listOf(accent, lerp(accent, Color.White, GLOW_LIGHT), accent) }
    Box(
        Modifier
            .fillMaxSize()
            .glowBorder({ glow }, look.shape, glowColors, width = GLOW_WIDTH, glow = GLOW_BLUR, periodMs = GLOW_PERIOD_MS)
            .tileTouch(actions, look.shape, interaction)
            .tileSemantics(spec, actions),
    ) {
        if (!spec.hasVideo) {
            Box(Modifier.align(Alignment.Center).size(avatarSize).speakingRipple(spec.speaking, spec.audioLevel, accent))
        }
        val inset = if (compact) TAG_INSET_COMPACT else TAG_INSET
        Box(Modifier.align(Alignment.BottomStart).padding(look.labelPadding).padding(inset)) {
            look.nameTag?.invoke(this)
                ?: KParticipantNameTag(spec.name, Modifier, spec.muted, spec.speaking, spec.audioLevel, spec.quality, spec.screenShare)
        }
        HandBadge(spec.handRaised, Modifier.align(Alignment.TopStart).padding(inset), if (compact) HAND_COMPACT else HAND_SIZE)
        if (spec.pinned) PinMark(Modifier.align(Alignment.TopEnd).padding(inset))
    }
}

/** 누르면 [TileActions.onClick], 길게 누르면 고정. 누를 동작이 없으면 아무것도 붙이지 않는다. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.tileTouch(actions: TileActions, shape: Shape, interaction: MutableInteractionSource): Modifier {
    val haptic = kHapticFeedback()
    val tap = actions.onClick ?: actions.onTogglePin ?: return this
    val longPress = actions.onTogglePin?.takeIf { actions.onClick != null }?.let { pin ->
        {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            pin()
        }
    }
    return clip(shape).combinedClickable(
        interactionSource = interaction,
        indication = KIndication(shape, Color.White, TILE_PRESS_ALPHA),
        onClick = tap,
        onLongClick = longPress,
    )
}

/** TalkBack이 타일 하나로 상태를 모두 읽고, 고정은 사용자 지정 동작으로 하게 한다. */
@Composable
private fun Modifier.tileSemantics(spec: TileSpec, actions: TileActions): Modifier {
    val description = tileDescription(spec, actions)
    val pinLabel = stringResource(if (spec.pinned) R.string.kinetica_ui_call_unpin else R.string.kinetica_ui_call_pin)
    val tap = actions.onClick ?: actions.onTogglePin
    val pin = actions.onTogglePin?.takeIf { actions.onClick != null }
    return clearAndSetSemantics {
        contentDescription = description
        if (tap != null) {
            role = Role.Button
            onClick(actions.onClickLabel ?: pinLabel.takeIf { actions.onClick == null }) {
                tap()
                true
            }
        }
        if (pin != null) {
            customActions = listOf(
                CustomAccessibilityAction(pinLabel) {
                    pin()
                    true
                },
            )
        }
    }
}

@Composable
private fun tileDescription(spec: TileSpec, actions: TileActions): String {
    val name = if (spec.screenShare) stringResource(R.string.kinetica_ui_call_screen_of, spec.name) else spec.name
    val parts = buildList {
        add(name)
        if (spec.pinned && actions.onTogglePin != null) add(stringResource(R.string.kinetica_ui_call_pinned))
        if (spec.handRaised) add(stringResource(R.string.kinetica_ui_call_hand_raised))
        if (spec.speaking) add(stringResource(R.string.kinetica_ui_call_speaking))
        if (spec.muted) add(stringResource(R.string.kinetica_ui_call_mic_off))
        if (!spec.hasVideo && !spec.screenShare) add(stringResource(R.string.kinetica_ui_call_camera_off))
        if (spec.quality.isWeak) add(spec.quality.label())
    }
    return parts.joinToString(", ")
}

/**
 * 타일 아래쪽 이름표. 어두운 반투명 캡슐에 흰 글자로 이름을 쓰고, 마이크가 꺼졌으면 마이크 꺼짐 아이콘을, 말하는 중이면 소리 막대를
 * 앞에 붙인다. 연결이 약하면 뒤에 품질 막대를 붙인다. 영상 위에서도 읽히도록 표면 스타일과 상관없이 같은 색을 쓴다.
 * TalkBack은 읽지 않는다. 타일이 전체 상태를 한 번에 읽기 때문이다.
 *
 * @param audioLevel 소리 크기(0..1). 그리기 단계에서 읽는다.
 */
@Composable
public fun KParticipantNameTag(
    name: String,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    speaking: Boolean = false,
    audioLevel: () -> Float = { 0f },
    quality: KNetworkQuality = KNetworkQuality.Unknown,
    screenShare: Boolean = false,
) {
    val label = if (screenShare) stringResource(R.string.kinetica_ui_call_screen_of, name) else name
    // 글자가 커지면 아이콘도 함께 키워 글자 옆에서 작아 보이지 않게 한다. 타일이 좁으므로 1.5배까지만 키운다.
    val icon = TAG_ICON * androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceIn(1f, TAG_ICON_SCALE)
    Row(
        modifier
            .clip(roundedShapeFor(TAG_RADIUS) { badge })
            .background(Color.Black.copy(alpha = TAG_SCRIM))
            .padding(horizontal = TAG_PADDING_H, vertical = TAG_PADDING_V)
            .clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(TAG_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (muted) {
            Image(rememberVectorPainter(KIcons.MicOff), null, Modifier.size(icon), colorFilter = ColorFilter.tint(Color(CALL_WARNING)))
        } else if (speaking) {
            KAudioLevelIndicator(audioLevel, color = KineticaTheme.colors.accent.let { lerp(it, Color.White, GLOW_LIGHT) }, size = icon)
        }
        ThemedText(
            label,
            Modifier.weight(1f, fill = false),
            style = TextStyle(color = Color.White, fontSize = TAG_TEXT, fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (quality.isWeak) {
            androidx.compose.runtime.CompositionLocalProvider(LocalKineticaContentColor provides Color.White) {
                KNetworkQualityIndicator(quality, size = icon, contentDescription = null)
            }
        }
    }
}

/** 손을 들면 살짝 기울어진 채 튀어나오고, 내리면 줄어들며 사라진다. */
@Composable
internal fun HandBadge(raised: Boolean, modifier: Modifier, size: Dp) {
    val still = reducedMotion()
    val level by animateFloatAsState(
        if (raised) 1f else 0f,
        if (still) tween(0) else spring(dampingRatio = HAND_DAMPING, stiffness = Spring.StiffnessMediumLow),
        label = "hand",
    )
    if (level <= HIDDEN) return
    Box(
        modifier
            .graphicsLayer {
                scaleX = level
                scaleY = level
                rotationZ = (1f - level) * HAND_TILT
            }
            .size(size)
            .clip(CircleShape)
            .background(Color(HAND_YELLOW)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            rememberVectorPainter(KCallIcons.Hand),
            null,
            Modifier.size(size * HAND_ICON),
            colorFilter = ColorFilter.tint(Color(HAND_INK)),
        )
    }
}

@Composable
private fun PinMark(modifier: Modifier) {
    Box(
        modifier.size(PIN_SIZE).clip(CircleShape).background(Color.Black.copy(alpha = TAG_SCRIM)),
        contentAlignment = Alignment.Center,
    ) {
        Image(rememberVectorPainter(KCallIcons.Pin), null, Modifier.size(PIN_ICON), colorFilter = ColorFilter.tint(Color.White))
    }
}

/**
 * [size]에 맞춘 아바타. [KAvatar]는 정해진 크기만 받으므로 타일과 상태 화면처럼 크기가 자유로운 곳은 이것으로 그린다.
 * 사진이 없으면 이름 색 원 위에 이니셜을 쓴다. 장식이라 TalkBack은 읽지 않는다.
 */
@Composable
internal fun CallAvatar(avatar: KAvatarData, size: Dp, modifier: Modifier = Modifier) {
    val shape = shapeFor { this.avatar }
    Box(
        modifier.size(size).clip(shape).background(
            if (avatar.image !=
                null
            ) {
                Color.Black.copy(alpha = PHOTO_BACK)
            } else {
                avatar.backgroundColor()
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (avatar.image != null) {
            avatar.image.invoke()
        } else {
            ThemedText(
                initialsOf(avatar.name),
                style = TextStyle(color = Color.White, fontSize = (size.value * INITIAL_RATIO).sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
        }
    }
}

private val COMPACT_TILE = 140.dp
private val MIN_AVATAR = 40.dp
private val MAX_AVATAR = 120.dp
private const val AVATAR_RATIO = 0.42f
private const val TILE_TINT = 0.1f
private const val PRESSED_SCALE = 0.97f
private const val PRESS_DAMPING = 0.7f
private const val TILE_PRESS_ALPHA = 0.08f
private const val GLOW_FADE_MS = 220
private const val GLOW_LIGHT = 0.45f
private val GLOW_WIDTH = 3.dp
private val GLOW_BLUR = 8.dp
private const val GLOW_PERIOD_MS = 2_400
private val TAG_INSET = 10.dp
private val TAG_INSET_COMPACT = 6.dp
private val TAG_RADIUS = 10.dp
private const val TAG_SCRIM = 0.45f
private val TAG_PADDING_H = 8.dp
private val TAG_PADDING_V = 4.dp
private val TAG_GAP = 5.dp
private val TAG_ICON = 14.dp
private const val TAG_ICON_SCALE = 1.5f
private val TAG_TEXT = 13.sp
private val HAND_SIZE = 32.dp
private val HAND_COMPACT = 24.dp
private const val HAND_ICON = 0.6f
private const val HAND_DAMPING = 0.45f
private const val HAND_TILT = -25f
private const val HAND_YELLOW = 0xFFFFCC00
private const val HAND_INK = 0xFF3A2A00
private const val HIDDEN = 0.01f
private val PIN_SIZE = 28.dp
private val PIN_ICON = 16.dp
private const val PHOTO_BACK = 0.2f
private const val INITIAL_RATIO = 0.36f
