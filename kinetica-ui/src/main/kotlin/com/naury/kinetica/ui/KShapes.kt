package com.naury.kinetica.ui

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** 모서리를 어떻게 깎을지. [KineticaShapes.corner]로 앱 전체를, [KShapeToken.corner]로 역할 하나를 고른다. */
public enum class KCornerStyle {
    /** 원호로 둥글린 모서리. 가장 흔한 모양이다. */
    Round,

    /** 곡률이 직선에서 서서히 커지는 연속 곡률 모서리(스쿼클). 같은 반지름의 원호보다 부드럽고 조금 더 각져 보인다. */
    Squircle,

    /** 모서리를 비스듬히 잘라 낸 모양. 반지름만큼 양쪽 변에서 잘라 낸다. */
    Cut,

    /** 각진 모서리. 반지름을 무시한다. */
    Square,
}

/** [KineticaShapes]의 모서리 크기 단계. 실제 반지름은 [KineticaShapes]의 같은 이름 값이다. */
public enum class KShapeSize {
    /** 모서리 없음(0dp) */
    None,
    ExtraSmall,
    Small,
    Medium,
    Large,
    ExtraLarge,

    /** 다이얼로그와 바텀 시트처럼 큰 판에 쓰는 가장 큰 단계 */
    Huge,

    /** 짧은 변의 절반. 버튼이나 칩이면 양 끝이 반원인 캡슐, 정사각형이면 원이 된다. */
    Full,
}

/**
 * 컴포넌트 역할 하나의 모양. 크기 단계와, 앱 전체 모서리 체계와 다르게 깎고 싶을 때의 모서리 모양을 담는다.
 *
 * @property size 모서리 크기 단계
 * @property corner 이 역할만 쓸 모서리 모양. null이면 [KineticaShapes.corner]를 따른다.
 */
@Immutable
public data class KShapeToken(val size: KShapeSize, val corner: KCornerStyle? = null) {
    public companion object {
        @JvmField public val None: KShapeToken = KShapeToken(KShapeSize.None)

        @JvmField public val ExtraSmall: KShapeToken = KShapeToken(KShapeSize.ExtraSmall)

        @JvmField public val Small: KShapeToken = KShapeToken(KShapeSize.Small)

        @JvmField public val Medium: KShapeToken = KShapeToken(KShapeSize.Medium)

        @JvmField public val Large: KShapeToken = KShapeToken(KShapeSize.Large)

        @JvmField public val ExtraLarge: KShapeToken = KShapeToken(KShapeSize.ExtraLarge)

        @JvmField public val Huge: KShapeToken = KShapeToken(KShapeSize.Huge)

        @JvmField public val Full: KShapeToken = KShapeToken(KShapeSize.Full)
    }
}

/**
 * kinetica-ui 컴포넌트의 모양 토큰. 모서리 체계([corner]), 크기 단계([extraSmall]..[huge]), 컴포넌트 역할마다 쓸 단계로 이루어진다.
 * [KineticaDesign.shapes]에 넣어 [KineticaTheme]으로 앱 전체에 적용하고, 컴포넌트의 `shape` 파라미터로 하나만 바꿀 수 있다.
 * 기본값은 [KineticaDesigns.Cupertino]의 모양(캡슐 버튼과 칩, 24dp 카드, 32dp 시트)이다.
 *
 * 크기 단계 값이 음수면 생성할 때 [IllegalArgumentException]을 던진다.
 *
 * ```kotlin
 * val shapes = KineticaShapes(corner = KCornerStyle.Squircle, button = KShapeToken.Medium, card = KShapeToken.ExtraLarge)
 * KineticaTheme(design = KineticaDesigns.Cupertino.copy(shapes = shapes)) { App() }
 * ```
 *
 * @property corner 앱 전체 모서리 모양
 * @property button 글자 버튼과 아이콘 버튼
 * @property chip 칩과 태그, 필터
 * @property card 카드와 [KSurface]
 * @property sheet 바텀 시트의 위 모서리
 * @property dialog 다이얼로그와 알림 대화 상자
 * @property field 입력란과 선택 상자
 * @property search 검색창
 * @property menu 드롭다운 메뉴와 팝오버
 * @property toast 토스트와 스낵바
 * @property tabBar 떠 있는 탭 바와 세그먼트 컨트롤 바깥
 * @property indicator 탭과 세그먼트의 선택 인디케이터
 * @property listSection 목록 섹션 판
 * @property avatar 아바타와 프로필 사진
 * @property checkbox 체크박스 상자. 20dp 상자에 그리므로 [KShapeSize.Full]이면 원이 된다.
 * @property radio 라디오 버튼 테두리
 * @property badge 숫자 배지와 상태 배지
 * @property image 카드나 목록 행 안의 썸네일, 아이콘 받침
 */
@Immutable
public data class KineticaShapes(
    val corner: KCornerStyle = KCornerStyle.Round,
    val extraSmall: Dp = 6.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 14.dp,
    val large: Dp = 20.dp,
    val extraLarge: Dp = 24.dp,
    val huge: Dp = 32.dp,
    val button: KShapeToken = KShapeToken.Full,
    val chip: KShapeToken = KShapeToken.Full,
    val card: KShapeToken = KShapeToken.ExtraLarge,
    val sheet: KShapeToken = KShapeToken.Huge,
    val dialog: KShapeToken = KShapeToken.Huge,
    val field: KShapeToken = KShapeToken.Medium,
    val search: KShapeToken = KShapeToken.Full,
    val menu: KShapeToken = KShapeToken.Large,
    val toast: KShapeToken = KShapeToken.ExtraLarge,
    val tabBar: KShapeToken = KShapeToken.Full,
    val indicator: KShapeToken = KShapeToken.Full,
    val listSection: KShapeToken = KShapeToken.Large,
    val avatar: KShapeToken = KShapeToken.Full,
    val checkbox: KShapeToken = KShapeToken.ExtraSmall,
    val radio: KShapeToken = KShapeToken.Full,
    val badge: KShapeToken = KShapeToken.Full,
    val image: KShapeToken = KShapeToken.Small,
) {
    init {
        require(listOf(extraSmall, small, medium, large, extraLarge, huge).all { it >= 0.dp }) { "모서리 크기 단계는 0 이상이어야 합니다." }
    }

    /**
     * [token]의 모서리 반지름. [KShapeSize.Full]이면 [Dp.Infinity](짧은 변의 절반으로 줄여 그린다)이고,
     * 모서리 모양이 [KCornerStyle.Square]면 0dp다.
     */
    public fun radius(token: KShapeToken): Dp = when {
        cornerOf(token) == KCornerStyle.Square -> 0.dp
        else -> sizeOf(token.size)
    }

    /** [token]의 모양. 같은 토큰이면 같은(equals) 모양을 돌려주므로 수정자 비교에 그대로 써도 된다. */
    public fun shape(token: KShapeToken): Shape = shapeOf(cornerOf(token), sizeOf(token.size))

    /**
     * [token] 모양의 표면 안에 [inset]만큼 들어간 요소의 모양. 반지름이 바깥 반지름에서 [inset]을 뺀 값(최소 4dp)이라
     * 안쪽 모서리가 바깥과 나란히 굽는다. 캡슐이면 안쪽도 캡슐이다. 모서리 모양은 바깥과 같다.
     */
    public fun inner(token: KShapeToken, inset: Dp): Shape {
        val size = sizeOf(token.size)
        val inner = if (size == Dp.Infinity) size else KMetrics.concentric(size, inset)
        return shapeOf(cornerOf(token), if (size == 0.dp) 0.dp else inner)
    }

    /** [token]의 모서리 모양 */
    public fun cornerOf(token: KShapeToken): KCornerStyle = token.corner ?: corner

    private fun sizeOf(size: KShapeSize): Dp = when (size) {
        KShapeSize.None -> 0.dp
        KShapeSize.ExtraSmall -> extraSmall
        KShapeSize.Small -> small
        KShapeSize.Medium -> medium
        KShapeSize.Large -> large
        KShapeSize.ExtraLarge -> extraLarge
        KShapeSize.Huge -> huge
        KShapeSize.Full -> Dp.Infinity
    }
}

/** [corner] 모양으로 반지름 [radius]를 깎은 모양. [radius]가 [Dp.Infinity]면 캡슐(짧은 변의 절반)이다. */
internal fun shapeOf(corner: KCornerStyle, radius: Dp): Shape {
    if (corner == KCornerStyle.Square || radius <= 0.dp) return RectangleShape
    val size = if (radius == Dp.Infinity) CornerSize(FULL_PERCENT) else CornerSize(radius)
    return when (corner) {
        KCornerStyle.Round -> RoundedCornerShape(size)
        KCornerStyle.Squircle -> KSquircleShape(size)
        KCornerStyle.Cut -> CutCornerShape(size)
        KCornerStyle.Square -> RectangleShape
    }
}

/** 위 두 모서리만 [token] 모양으로 깎은 모양. 바텀 시트처럼 화면 아래에 붙는 판에 쓴다. */
internal fun KineticaShapes.topShape(token: KShapeToken): Shape = partialShape(token, topStart = true, topEnd = true)

/** 끝 쪽 두 모서리만 [token] 모양으로 깎은 모양. 화면 시작 쪽에서 나오는 서랍에 쓴다. */
internal fun KineticaShapes.endShape(token: KShapeToken): Shape = partialShape(token, topEnd = true, bottomEnd = true)

/**
 * 모서리마다 크기를 따로 준 [token] 모양. [first]면 위 모서리를, [last]면 아래 모서리를 [token] 반지름으로, 나머지는 [rest]로 깎는다.
 * 목록 섹션 행처럼 판을 위아래로 나눈 조각에 쓴다.
 */
internal fun KineticaShapes.segmentShape(token: KShapeToken, first: Boolean, last: Boolean, rest: Dp = 0.dp): Shape =
    partialShape(token, topStart = first, topEnd = first, bottomEnd = last, bottomStart = last, rest = rest)

/** 고른 모서리만 [token] 반지름으로, 나머지는 [rest]로 깎은 모양 */
@Suppress("LongParameterList") // 네 모서리를 각각 고른다.
internal fun KineticaShapes.partialShape(
    token: KShapeToken,
    topStart: Boolean = false,
    topEnd: Boolean = false,
    bottomEnd: Boolean = false,
    bottomStart: Boolean = false,
    rest: Dp = 0.dp,
): Shape {
    val corner = cornerOf(token)
    val radius = radius(token)
    if (corner == KCornerStyle.Square || (radius <= 0.dp && rest <= 0.dp)) return RectangleShape
    val outer = if (radius == Dp.Infinity) CornerSize(FULL_PERCENT) else CornerSize(radius)
    val inner = CornerSize(rest)
    fun pick(on: Boolean) = if (on) outer else inner
    val ts = pick(topStart)
    val te = pick(topEnd)
    val be = pick(bottomEnd)
    val bs = pick(bottomStart)
    return when (corner) {
        KCornerStyle.Round -> RoundedCornerShape(ts, te, be, bs)
        KCornerStyle.Squircle -> KSquircleShape(ts, te, be, bs)
        KCornerStyle.Cut -> CutCornerShape(ts, te, be, bs)
        KCornerStyle.Square -> RectangleShape
    }
}

/**
 * 연속 곡률 모서리(스쿼클). 모서리 곡선이 직선 변에서 서서히 휘기 시작해, 같은 반지름의 원호 모서리보다 이음매가 매끄럽다.
 * 곡선은 반지름의 (1 + [smoothing])배 길이에 걸쳐 그린다. 변이 짧아 그만큼 자리가 없으면 [smoothing]을 줄여 맞춘다.
 *
 * @param smoothing 매끄러운 정도(0..1). 0이면 원호 모서리와 같고, 0.6 안팎이 흔히 쓰는 값이다. 범위를 벗어나면 0..1로 맞춘다.
 */
public class KSquircleShape(
    topStart: CornerSize,
    topEnd: CornerSize = topStart,
    bottomEnd: CornerSize = topStart,
    bottomStart: CornerSize = topStart,
    private val smoothing: Float = DEFAULT_SMOOTHING,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {
    /** 네 모서리를 같은 반지름 [radius]로 깎는다. */
    public constructor(radius: Dp, smoothing: Float = DEFAULT_SMOOTHING) :
        this(CornerSize(radius), smoothing = smoothing)

    override fun copy(topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize): CornerBasedShape =
        KSquircleShape(topStart, topEnd, bottomEnd, bottomStart, smoothing)

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        if (topStart + topEnd + bottomEnd + bottomStart == 0f) return Outline.Rectangle(size.toRect())
        val ltr = layoutDirection == LayoutDirection.Ltr
        val budget = size.minDimension / 2
        val s = smoothing.coerceIn(0f, 1f)
        val path = Path()
        // 위 오른쪽, 아래 오른쪽, 아래 왼쪽, 위 왼쪽 순서로 시계 방향을 돈다.
        val radii = if (ltr) {
            floatArrayOf(
                topEnd,
                bottomEnd,
                bottomStart,
                topStart,
            )
        } else {
            floatArrayOf(topStart, bottomStart, bottomEnd, topEnd)
        }
        val corners = arrayOf(Offset(size.width, 0f), Offset(size.width, size.height), Offset(0f, size.height), Offset.Zero)
        for (i in 0 until CORNERS) {
            path.squircleCorner(corners[i], i, radii[i], s, budget, first = i == 0)
        }
        path.close()
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KSquircleShape) return false
        return topStart == other.topStart && topEnd == other.topEnd && bottomEnd == other.bottomEnd &&
            bottomStart == other.bottomStart && smoothing == other.smoothing
    }

    override fun hashCode(): Int {
        var result = topStart.hashCode()
        result = HASH * result + topEnd.hashCode()
        result = HASH * result + bottomEnd.hashCode()
        result = HASH * result + bottomStart.hashCode()
        return HASH * result + smoothing.hashCode()
    }

    override fun toString(): String = "KSquircleShape(topStart=$topStart, topEnd=$topEnd, bottomEnd=$bottomEnd, bottomStart=$bottomStart)"

    private companion object {
        const val DEFAULT_SMOOTHING = 0.6f
        const val CORNERS = 4
        const val HASH = 31
    }
}

private fun Size.toRect() = androidx.compose.ui.geometry.Rect(Offset.Zero, this)

/**
 * 위 오른쪽 모서리 기준으로 계산한 곡선을 [quarter]번 90도 돌려 [corner]에 붙인다. 곡선은 Figma의 모서리 매끄럽게 하기와 같은 식이다.
 * 원호 구간은 큐빅 곡선 하나로 근사한다.
 */
@Suppress("LongParameterList") // 모서리 위치, 회전, 반지름, 매끄러운 정도, 한도, 시작 여부가 모두 모서리마다 다르다.
private fun Path.squircleCorner(corner: Offset, quarter: Int, radius: Float, smoothing: Float, budget: Float, first: Boolean) {
    fun at(x: Float, y: Float): Offset {
        var px = x
        var py = y
        repeat(quarter) {
            val t = px
            px = -py
            py = t
        }
        return Offset(corner.x + px, corner.y + py)
    }
    if (radius <= 0f) {
        val p = at(0f, 0f)
        if (first) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        return
    }
    val r = radius.coerceAtMost(budget)
    val s = if (r <= 0f) 0f else smoothing.coerceAtMost((budget / r - 1f).coerceAtLeast(0f))
    val p = (1f + s) * r
    val arcMeasure = HALF_PI * (1f - s)
    val arcLength = sin(arcMeasure / 2f) * r * sqrt(2f)
    val alpha = (HALF_PI - arcMeasure) / 2f
    val p3ToP4 = r * tan(alpha / 2f)
    val beta = HALF_PI / 2f * s
    val c = p3ToP4 * cos(beta)
    val d = c * tan(beta)
    val b = (p - arcLength - c - d) / BEZIER_SPLIT
    val a = 2f * b
    val start = at(-p, 0f)
    if (first) moveTo(start.x, start.y) else lineTo(start.x, start.y)
    val p1x = -p + a + b + c
    val p1y = d
    at(-p + a, 0f).let { c1 -> at(-p + a + b, 0f).let { c2 -> at(p1x, p1y).let { e -> cubicTo(c1.x, c1.y, c2.x, c2.y, e.x, e.y) } } }
    // 원호: 중심 (-r, r), 각도 -45°±arcMeasure/2
    val from = -HALF_PI / 2f - arcMeasure / 2f
    val to = -HALF_PI / 2f + arcMeasure / 2f
    // 원호를 큐빅 곡선 하나로 근사할 때 쓰는 제어점 거리(4/3·tan(θ/4)·r)
    val k = ARC_HANDLE * tan(arcMeasure / ARC_QUARTER) * r
    val ax = -r + r * cos(to)
    val ay = r + r * sin(to)
    val c1 = at(p1x - k * sin(from), p1y + k * cos(from))
    val c2 = at(ax + k * sin(to), ay - k * cos(to))
    val e = at(ax, ay)
    cubicTo(c1.x, c1.y, c2.x, c2.y, e.x, e.y)
    val q1 = at(ax + d, ay + c)
    val q2 = at(ax + d, ay + b + c)
    val end = at(0f, p)
    cubicTo(q1.x, q1.y, q2.x, q2.y, end.x, end.y)
}

private const val HALF_PI = (PI / 2).toFloat()
private const val FULL_PERCENT = 50
private const val BEZIER_SPLIT = 3f
private const val ARC_HANDLE = 4f / 3f
private const val ARC_QUARTER = 4f
