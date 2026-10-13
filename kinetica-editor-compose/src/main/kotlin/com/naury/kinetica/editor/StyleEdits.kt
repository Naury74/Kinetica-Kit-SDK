package com.naury.kinetica.editor

import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Gradient
import com.naury.kinetica.core.scene.GradientStop
import com.naury.kinetica.core.scene.GradientType
import com.naury.kinetica.core.scene.LineCap
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeStyle

/** 채우기 방식. */
internal enum class FillMode { None, Solid, Linear, Radial }

/** 채우기·선·효과·모서리·다각형 입력을 명령으로 바꾼다. 입력 하나가 실행 취소 한 단계이며, 받을 수 없는 값이면 `false`다. */
internal class StyleEdits(private val state: EditorState) {
    fun fillMode(style: NodeStyle): FillMode = when (style.gradient?.type) {
        GradientType.Linear -> FillMode.Linear
        GradientType.Radial -> FillMode.Radial
        null -> if (style.fill == null) FillMode.None else FillMode.Solid
    }

    /** 채우기 방식을 바꾼다. 그라디언트로 바꾸면 지금 색에서 투명으로 가는 두 색으로 시작한다. */
    fun setFillMode(id: NodeId, mode: FillMode): Boolean = edit(id) { style ->
        val base = style.gradient?.stops?.firstOrNull()?.color ?: style.fill ?: ArgbColor(DEFAULT_COLOR)
        when (mode) {
            FillMode.None -> style.copy(fill = null, gradient = null)

            FillMode.Solid -> style.copy(fill = base, gradient = null)

            FillMode.Linear, FillMode.Radial -> style.copy(
                gradient = Gradient(
                    type = if (mode == FillMode.Linear) GradientType.Linear else GradientType.Radial,
                    stops = style.gradient?.stops ?: listOf(GradientStop(0.0, base), GradientStop(1.0, ArgbColor(base.argb and RGB_MASK))),
                    angleDegrees = style.gradient?.angleDegrees ?: DEFAULT_ANGLE,
                ),
            )
        }
    }

    /** 그라디언트 [index]번째 색(`#RRGGBB`·`#AARRGGBB`). */
    fun setStopColor(id: NodeId, index: Int, text: String): Boolean {
        val color = parseColor(text) ?: return false
        return edit(id) { style ->
            val gradient = style.gradient ?: return@edit null
            if (index !in gradient.stops.indices) return@edit null
            style.copy(
                gradient = gradient.copy(
                    stops = gradient.stops.mapIndexed { i, stop ->
                        if (i ==
                            index
                        ) {
                            stop.copy(color = color)
                        } else {
                            stop
                        }
                    },
                ),
            )
        }
    }

    fun setAngle(id: NodeId, text: String): Boolean {
        val angle = parseNumber(text) ?: return false
        return edit(id) { style -> style.gradient?.let { style.copy(gradient = it.copy(angleDegrees = angle)) } }
    }

    fun setStroke(id: NodeId, text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return edit(id) { it.copy(stroke = null) }
        val color = parseColor(trimmed) ?: return false
        return edit(id) { it.copy(stroke = color, strokeWidth = if (it.strokeWidth > 0.0) it.strokeWidth else DEFAULT_STROKE) }
    }

    fun setStrokeWidth(id: NodeId, text: String): Boolean {
        val width = parseNumber(text)?.takeIf { it >= 0.0 } ?: return false
        return edit(id) { it.copy(strokeWidth = width) }
    }

    fun toggleDash(id: NodeId): Boolean = edit(id) { it.copy(strokeDash = if (it.strokeDash.isEmpty()) DEFAULT_DASH else emptyList()) }

    fun setCap(id: NodeId, start: Boolean, cap: LineCap): Boolean = edit(id) {
        if (start) it.copy(startCap = cap) else it.copy(endCap = cap)
    }

    private fun edit(id: NodeId, change: (NodeStyle) -> NodeStyle?): Boolean = state.editStyle(id, change)

    private companion object {
        const val DEFAULT_COLOR = 0xFF7C8CFF.toInt()
        const val RGB_MASK = 0x00FFFFFF
        const val DEFAULT_ANGLE = 90.0
        const val DEFAULT_STROKE = 4.0
        val DEFAULT_DASH = listOf(12.0, 8.0)
    }
}

/** [change]가 `null`을 돌려주면 받을 수 없는 입력이며 문서는 그대로다. */
internal fun EditorState.editStyle(id: NodeId, change: (NodeStyle) -> NodeStyle?): Boolean {
    val node = document.node(id) ?: return false
    val style = change(node.style) ?: return false
    val updated = node.copy(style = style)
    if (updated == node) return true
    return execute(SceneCommand.UpdateProperties(updated))
}

internal fun parseNumber(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf(Double::isFinite)

internal fun parseColor(text: String): ArgbColor? = ArgbColor.parseHexOrNull(text.trim().let { if (it.startsWith("#")) it else "#$it" })
