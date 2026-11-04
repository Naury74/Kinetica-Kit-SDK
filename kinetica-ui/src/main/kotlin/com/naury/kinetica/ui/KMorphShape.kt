package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.flow.collectLatest

/**
 * [KShapeMorphButton]과 [KShapeToggle]이 오가는 모양. 정사각형 안에 꽉 차는 둥근 도형으로, 두 모양 사이를 매끄럽게 이어서 바꿀 수 있다.
 * 미리 만든 값([Circle], [Square], [Cookie4] 등)을 쓰거나 [squircle], [cookie]로 만든다.
 */
@Immutable
public class KMorphShape private constructor(
    private val exponent: Float,
    private val lobes: Int,
    private val depth: Float,
    /** 모양을 돌려 둘 각도(도). 시계 방향이다. */
    public val rotation: Float,
) {
    /** 각도 [angle](라디안)에서 가운데부터 테두리까지 거리. 정사각형 반 변을 1로 본다. */
    internal fun radius(angle: Float): Float {
        val c = abs(cos(angle)).pow(exponent)
        val s = abs(sin(angle)).pow(exponent)
        val square = (c + s).pow(-1f / exponent)
        return square * (1f + depth * cos(lobes * angle)) / (1f + depth)
    }

    override fun equals(other: Any?): Boolean = other is KMorphShape &&
        exponent == other.exponent &&
        lobes == other.lobes &&
        depth == other.depth &&
        rotation == other.rotation

    override fun hashCode(): Int = ((exponent.hashCode() * HASH + lobes) * HASH + depth.hashCode()) * HASH + rotation.hashCode()

    public companion object {
        /** 원 */
        public val Circle: KMorphShape = KMorphShape(2f, 0, 0f, 0f)

        /** 모서리가 둥근 사각형(스쿼클) */
        public val Square: KMorphShape = KMorphShape(SQUARE_EXPONENT, 0, 0f, 0f)

        /** 둥근 꽃잎 네 개짜리 쿠키 */
        public val Cookie4: KMorphShape = KMorphShape(2f, 4, 0.12f, 45f)

        /** 물결 테두리 여섯 개짜리 쿠키 */
        public val Cookie6: KMorphShape = KMorphShape(2f, 6, 0.08f, 0f)

        /** 물결 테두리 아홉 개짜리 쿠키. 잔물결이라 원과 가장 가깝다. */
        public val Cookie9: KMorphShape = KMorphShape(2f, 9, 0.05f, 0f)

        /** 꽃잎이 깊은 네잎클로버 */
        public val Clover: KMorphShape = KMorphShape(2f, 4, 0.26f, 45f)

        /** 뾰족한 꽃잎 여덟 개짜리 꽃 */
        public val Flower: KMorphShape = KMorphShape(2f, 8, 0.14f, 0f)

        /**
         * 슈퍼타원(스쿼클). [exponent]가 2면 원이고 클수록 네모에 가깝다(4~5가 둥근 사각형).
         * 2보다 작거나 유한한 수가 아니면 [IllegalArgumentException]을 던진다.
         */
        public fun squircle(exponent: Float, rotation: Float = 0f): KMorphShape {
            require(exponent.isFinite() && exponent >= 2f) { "exponent는 2 이상의 유한한 수여야 합니다." }
            return KMorphShape(exponent, 0, 0f, rotation)
        }

        /**
         * 테두리가 [lobes]번 물결치는 쿠키 모양. [depth]는 물결 깊이로 0..0.4이며 0이면 원이다.
         * [lobes]가 2..24 밖이거나 [depth]가 0..0.4 밖이면 [IllegalArgumentException]을 던진다.
         */
        public fun cookie(lobes: Int, depth: Float, rotation: Float = 0f): KMorphShape {
            require(lobes in MIN_LOBES..MAX_LOBES) { "lobes는 $MIN_LOBES..$MAX_LOBES 사이여야 합니다." }
            require(depth in 0f..MAX_DEPTH) { "depth는 0..$MAX_DEPTH 사이여야 합니다." }
            return KMorphShape(2f, lobes, depth, rotation)
        }
    }
}

private const val HASH = 31
private const val SQUARE_EXPONENT = 4.6f
private const val MIN_LOBES = 2
private const val MAX_LOBES = 24
private const val MAX_DEPTH = 0.4f
