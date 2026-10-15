package com.naury.kinetica.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.translate
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 컴포넌트가 함께 쓰는 치수. iOS와 One UI의 기준에 맞춰, 터치 영역과 아이콘 크기, 아이콘과 글자 간격, 안쪽 모서리를 한곳에서 정한다.
 */
internal object GlassMetrics {
    /** 누를 수 있는 요소의 최소 터치 영역 */
    val MinTouch: Dp = 44.dp

    /** 권장 터치 영역. 목록 행이나 탭처럼 자리가 넉넉한 곳에 쓴다. */
    val Touch: Dp = 48.dp

    /** 원형 아이콘 버튼 지름 */
    val IconButton: Dp = 44.dp

    /** 원형 아이콘 버튼 안 아이콘 */
    val IconButtonIcon: Dp = 22.dp

    /** 작은 원형 아이콘 버튼(카드 위 닫기 버튼 같은 곳) 지름 */
    val SmallIconButton: Dp = 36.dp

    /** 작은 원형 아이콘 버튼 안 아이콘 */
    val SmallIconButtonIcon: Dp = 18.dp

    /** 글자 버튼 안 아이콘 */
    val ButtonIcon: Dp = 20.dp

    /** 아이콘과 글자 사이 */
    val IconTextGap: Dp = 8.dp

    /** 목록 행 앞 아이콘 */
    val ListIcon: Dp = 24.dp

    /** 탭 바와 레일 아이콘 */
    val TabIcon: Dp = 24.dp

    /** 탭 아이콘과 이름 사이 */
    val TabIconGap: Dp = 2.dp

    /** 안쪽 모서리 반지름의 최솟값 */
    val MinInnerRadius: Dp = 4.dp

    /**
     * 바깥 모서리 반지름이 [outer]인 표면 안에 [inset]만큼 들어간 요소의 모서리 반지름. 둘이 같은 중심을 공유해야
     * 안쪽 모서리가 바깥과 나란히 굽는다.
     */
    fun concentric(outer: Dp, inset: Dp): Dp = (outer - inset).coerceAtLeast(MinInnerRadius)
}

/** [base] 모양을 위아래 [vertical], 양옆 [horizontal]만큼 줄여 안쪽에 그린다. 눌림 표시를 안쪽 인디케이터와 같은 크기로 맞출 때 쓴다. */
internal class InsetShape(private val base: Shape, private val horizontal: Dp = 0.dp, private val vertical: Dp = 0.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val dx = with(density) { horizontal.toPx() }.coerceAtMost(size.width / 2)
        val dy = with(density) { vertical.toPx() }.coerceAtMost(size.height / 2)
        val inner = base.createOutline(Size(size.width - dx * 2, size.height - dy * 2), layoutDirection, density)
        val offset = Offset(dx, dy)
        return when (inner) {
            is Outline.Rectangle -> Outline.Rectangle(inner.rect.translate(offset))
            is Outline.Rounded -> Outline.Rounded(inner.roundRect.translate(offset))
            is Outline.Generic -> Outline.Generic(Path().apply { addPath(inner.path, offset) })
        }
    }

    override fun equals(other: Any?): Boolean =
        other is InsetShape && base == other.base && horizontal == other.horizontal && vertical == other.vertical

    override fun hashCode(): Int = (base.hashCode() * HASH + horizontal.hashCode()) * HASH + vertical.hashCode()

    private companion object {
        const val HASH = 31
    }
}

/** 요소 가운데에 놓인 원. 지름은 짧은 변에서 [inset]의 두 배를 뺀 값이다. 날짜나 색 칩처럼 칸 가운데 원을 그리는 요소의 눌림 표시에 쓴다. */
internal class CenteredCircleShape(private val inset: Dp = 0.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radius = (size.minDimension / 2 - with(density) { inset.toPx() }).coerceAtLeast(0f)
        val center = Offset(size.width / 2, size.height / 2)
        val rect = Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
        return Outline.Rounded(RoundRect(rect, CornerRadius(radius)))
    }

    override fun equals(other: Any?): Boolean = other is CenteredCircleShape && inset == other.inset

    override fun hashCode(): Int = inset.hashCode()
}
