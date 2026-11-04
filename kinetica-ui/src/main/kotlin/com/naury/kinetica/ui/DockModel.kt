package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerEventType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * [KDock]의 아이콘 하나.
 *
 * @param label 이름. 손가락이나 마우스가 가리키면 아이콘 위에 뜨고, TalkBack이 버튼 이름으로 읽는다.
 * @param running 실행 중인지. true면 아이콘 아래에 점을 찍는다.
 * @param onClick 눌렀을 때 부른다.
 * @param icon 아이콘 내용. 받은 크기를 채워 그린다. 독이 이름을 붙이므로 따로 contentDescription을 주지 않는다.
 */
@Stable
public class KDockItem(
    public val label: String,
    public val running: Boolean = false,
    public val onClick: () -> Unit,
    public val icon: @Composable () -> Unit,
)

/**
 * [KDock]의 손가락 위치와 아이콘 크기 계산. 위치는 상태라 배치 단계에서 읽으면 손가락이 움직일 때 다시 구성하지 않고 다시 배치만 한다.
 * 아이콘이 커지는 정도는 커진 뒤의 자리가 아니라 쉬고 있을 때의 자리에서 잰다. 커진 자리로 재면 아이콘이 커지며 손가락에서 멀어지고,
 * 그래서 다시 작아지는 일이 되풀이되어 떨린다.
 */
@Stable
internal class DockModel {
    /** 손가락이나 마우스 x 위치(독 안 좌표, 픽셀). 독 위에 없으면 NaN이다. */
    var pointerX by mutableFloatStateOf(Float.NaN)

    /** 손가락이 닿아 있거나 마우스가 올라와 있는지 */
    var active by mutableStateOf(false)

    /** 손가락이나 마우스가 가리키는 아이콘. 없으면 -1이다. 이름표와 햅틱에 쓰므로 바뀔 때만 다시 구성한다. */
    var hovered by mutableIntStateOf(-1)

    /** 커지는 효과가 나타난 정도(0..1). 손가락이 닿고 뗄 때 부드럽게 오간다. */
    val presence = Animatable(0f)

    /** 마지막 배치의 아이콘 가운데와 크기. 손가락 아래 아이콘을 찾을 때 쓴다. */
    var centers: FloatArray = FloatArray(0)
        private set
    var sizes: FloatArray = FloatArray(0)
        private set

    /** 아이콘 사이 간격, 가장 큰 배율, 커지는 범위(픽셀). 잴 때마다 독이 넣는다. */
    var gap = 0f
    var magnification = 1f
    var reach = 0f

    /** 손가락이 닿은 채 눌려 있는지 */
    var pressed = false
        private set

    /** 쉬고 있을 때의 아이콘 가운데에서 잰 크기를 [sizes]에 담는다. */
    fun measure(count: Int, restLeft: Float, base: Float) {
        if (sizes.size != count) {
            sizes = FloatArray(count)
            centers = FloatArray(count)
        }
        val x = pointerX
        val amount = presence.value
        for (i in 0 until count) {
            val rest = restLeft + base / 2f + i * (base + gap)
            val near = if (x.isNaN() || amount <= 0f) 0f else falloff(abs(x - rest), reach)
            sizes[i] = base * (1f + (magnification - 1f) * near * amount)
        }
    }

    /** 커진 크기로 놓은 아이콘 가운데를 [centers]에 담고 줄 전체 폭을 돌려준다. */
    fun arrange(left: Float): Float {
        var x = left
        for (i in sizes.indices) {
            centers[i] = x + sizes[i] / 2f
            x += sizes[i] + gap
        }
        return (x - gap - left).coerceAtLeast(0f)
    }

    fun pressing(down: Boolean) {
        pressed = down
    }

    /** [x] 아래 아이콘. 아이콘 사이 틈은 가까운 쪽으로 친다. 줄 밖이면 -1이다. */
    fun indexAt(x: Float): Int {
        if (centers.isEmpty() || x.isNaN()) return -1
        val first = centers.first() - sizes.first() / 2f - gap / 2f
        val last = centers.last() + sizes.last() / 2f + gap / 2f
        if (x < first || x > last) return -1
        return centers.indices.minBy { abs(centers[it] - x) }
    }
}

/**
 * 포인터 사건 하나를 반영한다. 손가락을 떼거나 마우스 단추를 놓은 곳이 독 안([inside])의 아이콘이면 그 번호를, 아니면 -1을 돌려준다.
 * 마우스는 올라와 있기만 해도 커지고, 손가락은 닿아 있는 동안만 커진다.
 */
internal fun DockModel.follow(type: PointerEventType, x: Float, inside: Boolean, mouse: Boolean): Int {
    var launch = -1
    when (type) {
        PointerEventType.Press -> pressing(true)

        PointerEventType.Release -> if (pressed) {
            pressing(false)
            if (inside) launch = indexAt(x)
        }

        PointerEventType.Exit -> if (!pressed) pointerX = Float.NaN

        else -> Unit
    }
    active = (pressed && inside) || (mouse && type != PointerEventType.Exit)
    if (active) pointerX = x
    hovered = if (active) indexAt(x) else -1
    return launch
}

/** 가리키는 곳에서 [distance]만큼 떨어진 아이콘이 커지는 정도(0..1). 가운데가 가장 크고 [reach]에서 0이 되는 반 코사인 곡선이다. */
internal fun falloff(distance: Float, reach: Float): Float =
    if (reach <= 0f || distance >= reach) 0f else ((cos(PI * distance / reach) + 1.0) / 2.0).toFloat()

/** 어디를 가리키든 커진 아이콘들이 더 차지하는 폭의 최댓값. 아이콘 가운데와 그 사이를 가리킬 때를 재 보고 가장 큰 값을 쓴다. */
internal fun dockGrowth(count: Int, base: Float, gap: Float, magnification: Float, reach: Float): Float {
    var most = 0f
    val step = base + gap
    for (probe in 0 until count * 2) {
        val x = base / 2f + probe * step / 2f
        var extra = 0f
        for (i in 0 until count) extra += base * (magnification - 1f) * falloff(abs(x - (base / 2f + i * step)), reach)
        most = maxOf(most, extra)
    }
    return most
}
