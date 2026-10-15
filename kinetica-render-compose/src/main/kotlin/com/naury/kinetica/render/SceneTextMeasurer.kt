package com.naury.kinetica.render

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.TextMeasurer
import kotlin.math.ceil

/**
 * 화면과 같은 글꼴과 규칙으로 텍스트 노드 크기를 측정하는 [TextMeasurer]. 문서 단위 1을 1px로, 글꼴 배율은 1로 보고 측정한다.
 * 세션([com.naury.kinetica.history.KineticaSession])이나 [com.naury.kinetica.core.scene.Bindings]에 넘겨 쓴다.
 *
 * @param fonts 호스트 글꼴. 화면에 준 [LocalSceneFonts]와 같아야 크기가 맞는다.
 */
public class SceneTextMeasurer @JvmOverloads constructor(context: Context, private val fonts: SceneFonts = SceneFonts.Default) :
    TextMeasurer {
    private val measurer = androidx.compose.ui.text.TextMeasurer(
        createFontFamilyResolver(context.applicationContext),
        Density(1f),
        LayoutDirection.Ltr,
        cacheSize = CACHE_SIZE,
    )

    override fun measure(text: NodeContent.Text, maxWidth: Double?): Size {
        val units: (Double) -> androidx.compose.ui.unit.TextUnit = { it.toFloat().sp }
        val width = maxWidth?.let { ceil(it).toInt().coerceAtLeast(1) }
        val layout = measurer.measure(
            text = text.annotated(fonts, units),
            style = text.baseStyle(Color.Black, fonts, units),
            overflow = TextOverflow.Clip,
            softWrap = width != null,
            constraints = if (width == null) Constraints() else Constraints(maxWidth = width),
        )
        val measuredWidth = maxWidth ?: ceil(layout.size.width.toDouble()).coerceAtLeast(1.0)
        return Size(measuredWidth, ceil(layout.size.height.toDouble()).coerceAtLeast(1.0))
    }

    private companion object {
        const val CACHE_SIZE = 32
    }
}
