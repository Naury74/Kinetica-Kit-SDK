package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** [KAvatar] 지름. ExtraSmall 24dp, Small 32dp, Medium 40dp, Large 56dp, ExtraLarge 80dp다. */
public enum class KAvatarSize(internal val size: Dp) {
    ExtraSmall(24.dp),
    Small(32.dp),
    Medium(40.dp),
    Large(56.dp),
    ExtraLarge(80.dp),
}

/** [KAvatar] 오른쪽 아래(RTL이면 왼쪽 아래)에 찍는 상태 점. */
public enum class KAvatarStatus {
    /** 초록 점 */
    Online,

    /** 빨간 점 */
    Busy,

    /** 주황 점 */
    Away,

    /** 회색 점 */
    Offline,
}

/**
 * 사람 한 명의 아바타. [avatar]의 `image`가 있으면 그것을, 없으면 이름 앞 글자로 만든 이니셜을 색 원 안에 그린다.
 * [AvatarStack]과 같은 색과 이니셜 규칙을 쓰므로 목록 행의 아바타와 겹친 묶음이 같은 사람을 같은 색으로 보여 준다.
 * [status]를 주면 바깥 테두리를 [ringColor]로 두른 상태 점을 모서리에 찍는다. TalkBack은 '민지, 온라인'처럼 이름과 상태를 읽는다.
 *
 * ```kotlin
 * KAvatar(KAvatarData("김민지"), size = KAvatarSize.Large, status = KAvatarStatus.Online)
 * KAvatar(KAvatarData("Ana", image = { Image(photo, null, contentScale = ContentScale.Crop) }), shape = RoundedCornerShape(12.dp))
 * ```
 *
 * @param avatar 그릴 사람. [AvatarStack]에 넣는 값과 같다.
 * @param shape 아바타 모양. 기본값은 원이다. 둥근 사각형이면 상태 점은 모서리 안쪽에 놓인다.
 * @param ringColor 상태 점 둘레 테두리 색. 아바타가 놓인 바탕색과 맞춘다. 기본값은 표면 색이다.
 */
@Composable
public fun KAvatar(
    avatar: KAvatarData,
    modifier: Modifier = Modifier,
    size: KAvatarSize = KAvatarSize.Medium,
    shape: Shape = shapeFor { this.avatar },
    status: KAvatarStatus? = null,
    ringColor: Color = KineticaTheme.surface.ringColor(),
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val name = avatar.name
    val description = status?.let { stringResource(R.string.kinetica_ui_avatar_status, name, it.label()) } ?: name
    val dotColor = status?.color()
    val round = shape == CircleShape || shape == RoundedCornerShape(CIRCLE_PERCENT)
    Box(
        modifier
            .size(size.size)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Image
            }
            .drawWithContent {
                drawContent()
                if (dotColor == null) return@drawWithContent
                val d = this.size.minDimension
                val r = d * DOT_RATIO / 2
                // 원이면 45도 방향 테두리 위에, 사각형이면 모서리 안쪽에 점의 중심을 둔다.
                val inset = if (round) d / 2 * (1 - DIAGONAL) else r * SQUARE_INSET
                val x = if (layoutDirection == LayoutDirection.Rtl) inset else this.size.width - inset
                val center = Offset(x, this.size.height - inset)
                drawCircle(ringColor, r + RING.toPx(), center)
                drawCircle(dotColor, r, center)
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(size.size).clip(shape).background(
                if (avatar.image !=
                    null
                ) {
                    surface.raisedFill(PLACEHOLDER)
                } else {
                    avatar.backgroundColor()
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            if (avatar.image != null) avatar.image.invoke() else Initials(initialsOf(name), size.size, Color.White)
        }
    }
}

@Composable
private fun KAvatarStatus.label(): String = stringResource(
    when (this) {
        KAvatarStatus.Online -> R.string.kinetica_ui_status_online
        KAvatarStatus.Busy -> R.string.kinetica_ui_status_busy
        KAvatarStatus.Away -> R.string.kinetica_ui_status_away
        KAvatarStatus.Offline -> R.string.kinetica_ui_status_offline
    },
)

@Composable
private fun KAvatarStatus.color(): Color = when (this) {
    KAvatarStatus.Online -> KineticaTheme.colors.positive
    KAvatarStatus.Busy -> KineticaTheme.colors.destructive
    KAvatarStatus.Away -> Color(AWAY)
    KAvatarStatus.Offline -> Color(OFFLINE)
}

/** 아바타가 놓일 바탕에 가까운 불투명 색. 글래스는 비치는 색을 알 수 없으므로 밝기에 맞춘 흰색이나 짙은 회색이다. */
internal fun SurfaceStyle.ringColor(): Color = inkBackground()

private val RING = 2.dp
private const val DOT_RATIO = 0.28f
private const val DIAGONAL = 0.7071f
private const val SQUARE_INSET = 0.9f
private const val PLACEHOLDER = 0.1f
private const val AWAY = 0xFFFF9F0A
private const val OFFLINE = 0xFF8E8E93

private const val CIRCLE_PERCENT = 50
