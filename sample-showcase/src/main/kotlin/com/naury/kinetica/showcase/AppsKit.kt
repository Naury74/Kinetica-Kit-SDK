// 화면 모양 값(간격·크기·색)과 아이콘 좌표는 그 자리에서 읽는 편이 낫다.
// AppLook은 밝은 표면, 어두운 표면과 그 위 색을 한 번에 받아 인자가 많다.
@file:Suppress("MagicNumber", "LongParameterList")

package com.naury.kinetica.showcase

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KineticaColors
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.SurfaceStyle
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/**
 * 앱 화면 예 하나의 표면과 강조색. 쇼케이스 표면 종류를 따르는 다른 데모와 달리, 앱마다 어울리는 표면(글래스, 단색, 톤)을 하나로 정해 두고
 * 밝기만 쇼케이스 설정을 따른다. 그래야 여러 앱을 오가며 키트가 낼 수 있는 분위기의 폭이 보인다.
 */
internal class AppLook(
    val light: SurfaceStyle,
    val dark: SurfaceStyle,
    val defaultDark: Boolean,
    val accent: Color,
    val accentDark: Color = accent,
    val onAccent: Color = Color.White,
    val onAccentDark: Color = onAccent,
)

@Composable
internal fun AppLook.isDark(): Boolean = LocalShowcaseLook.current.isDark(defaultDark)

@Composable
internal fun AppLook.surface(): SurfaceStyle = if (isDark()) dark else light

/** [look]의 표면과 강조색을 테마로 깐다. 스캐폴드 밖에 뜨는 시트, 다이얼로그도 같은 표면을 쓰게 하려고 앱 전체를 감싼다. */
@Composable
internal fun AppTheme(look: AppLook, content: @Composable () -> Unit) {
    val dark = look.isDark()
    KineticaTheme(
        darkTheme = dark,
        surface = if (dark) look.dark else look.light,
        colors = KineticaColors(
            accent = if (dark) look.accentDark else look.accent,
            onAccent = if (dark) look.onAccentDark else look.onAccent,
        ),
        content = content,
    )
}

/** 넓은 화면에서 본문이 [maxWidth]를 넘지 않도록 양옆에 둘 여백. 좁은 화면에서는 [minimum]이다. */
@Composable
internal fun appPadding(minimum: Dp = 20.dp, maxWidth: Dp = READING_WIDTH): Dp {
    val width = LocalConfiguration.current.screenWidthDp.dp
    return maxOf(minimum, (width - maxWidth) / 2)
}

/** 원화 금액. 음수면 앞에 −를 붙인다. */
internal fun won(amount: Long): String {
    val text = NumberFormat.getNumberInstance(Locale.KOREA).format(abs(amount))
    return if (amount < 0) "−${text}원" else "${text}원"
}

internal fun won(amount: Int): String = won(amount.toLong())

/**
 * 사진 대신 쓰는 그림. 두 색 그라디언트 위에 부드러운 원 두 개를 겹쳐 상품 사진이나 음식 사진 자리를 채운다.
 * [seed]가 같으면 늘 같은 그림이다.
 */
@Composable
internal fun ArtBox(
    colors: List<Color>,
    seed: Int,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val brush = remember(colors) { Brush.linearGradient(colors) }
    Box(modifier.clip(shape)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(brush)
            val r = size.minDimension
            val fx = ((seed * 37) % 60 + 20) / 100f
            val fy = ((seed * 53) % 50 + 25) / 100f
            drawCircle(Color.White.copy(alpha = 0.22f), r * 0.42f, Offset(size.width * fx, size.height * fy))
            drawCircle(Color.White.copy(alpha = 0.14f), r * 0.26f, Offset(size.width * (1 - fx * 0.6f), size.height * (1 - fy * 0.5f)))
            drawCircle(Color.Black.copy(alpha = 0.06f), r * 0.6f, Offset(size.width * (1 - fx), size.height * 1.1f))
        }
        content()
    }
}

/** 이름으로 고르는 두 색 그라디언트 묶음. 아바타, 상품, 가게 그림에 돌려 쓴다. */
internal val ART_PALETTES = listOf(
    listOf(Color(0xFFFFB199), Color(0xFFFF0844)),
    listOf(Color(0xFF84FAB0), Color(0xFF8FD3F4)),
    listOf(Color(0xFFA18CD1), Color(0xFFFBC2EB)),
    listOf(Color(0xFFFAD961), Color(0xFFF76B1C)),
    listOf(Color(0xFF43E97B), Color(0xFF38F9D7)),
    listOf(Color(0xFF4FACFE), Color(0xFF00F2FE)),
    listOf(Color(0xFFFA709A), Color(0xFFFEE140)),
    listOf(Color(0xFF30CFD0), Color(0xFF330867)),
    listOf(Color(0xFF667EEA), Color(0xFF764BA2)),
    listOf(Color(0xFFF6D365), Color(0xFFFDA085)),
)

internal fun artPalette(index: Int): List<Color> = ART_PALETTES[Math.floorMod(index, ART_PALETTES.size)]

/** 앱 화면 예에서 쓰는 선 아이콘(24 격자). [ShowcaseIcons]에 없는 것만 둔다. */
internal object AppIcons {
    val Search = line {
        circle(10.5f, 10.5f, 6.5f)
        moveTo(15.5f, 15.5f)
        lineTo(20f, 20f)
    }
    val Bag = line {
        moveTo(5f, 8f)
        lineTo(19f, 8f)
        lineTo(18f, 20f)
        lineTo(6f, 20f)
        close()
        moveTo(9f, 10f)
        lineTo(9f, 7f)
        arcTo(3f, 3f, 0f, false, true, 15f, 7f)
        lineTo(15f, 10f)
    }
    val Heart = line {
        moveTo(12f, 20f)
        curveTo(5f, 15.5f, 3f, 12f, 3f, 9f)
        curveTo(3f, 6.2f, 5.2f, 4.5f, 7.5f, 4.5f)
        curveTo(9.4f, 4.5f, 11f, 5.6f, 12f, 7.2f)
        curveTo(13f, 5.6f, 14.6f, 4.5f, 16.5f, 4.5f)
        curveTo(18.8f, 4.5f, 21f, 6.2f, 21f, 9f)
        curveTo(21f, 12f, 19f, 15.5f, 12f, 20f)
        close()
    }
    val Comment = line {
        moveTo(4f, 12f)
        arcTo(8f, 7.5f, 0f, true, true, 8.5f, 18.6f)
        lineTo(4f, 20f)
        lineTo(5.2f, 16f)
        close()
    }
    val Bookmark = line {
        moveTo(6f, 4f)
        lineTo(18f, 4f)
        lineTo(18f, 20f)
        lineTo(12f, 15.5f)
        lineTo(6f, 20f)
        close()
    }
    val Card = line {
        roundRect(3f, 5.5f, 21f, 18.5f, 2.5f)
        moveTo(3f, 9.5f)
        lineTo(21f, 9.5f)
        moveTo(6.5f, 14.5f)
        lineTo(10f, 14.5f)
    }
    val ArrowUp = line {
        moveTo(12f, 19f)
        lineTo(12f, 5f)
        moveTo(6f, 11f)
        lineTo(12f, 5f)
        lineTo(18f, 11f)
    }
    val ArrowDown = line {
        moveTo(12f, 5f)
        lineTo(12f, 19f)
        moveTo(6f, 13f)
        lineTo(12f, 19f)
        lineTo(18f, 13f)
    }
    val Swap = line {
        moveTo(7f, 4f)
        lineTo(7f, 19f)
        moveTo(3.5f, 7.5f)
        lineTo(7f, 4f)
        lineTo(10.5f, 7.5f)
        moveTo(17f, 20f)
        lineTo(17f, 5f)
        moveTo(13.5f, 16.5f)
        lineTo(17f, 20f)
        lineTo(20.5f, 16.5f)
    }
    val Receipt = line {
        moveTo(6f, 3.5f)
        lineTo(18f, 3.5f)
        lineTo(18f, 20.5f)
        lineTo(15f, 18.5f)
        lineTo(12f, 20.5f)
        lineTo(9f, 18.5f)
        lineTo(6f, 20.5f)
        close()
        moveTo(9f, 8.5f)
        lineTo(15f, 8.5f)
        moveTo(9f, 12.5f)
        lineTo(15f, 12.5f)
    }
    val Camera = line {
        moveTo(3.5f, 8.5f)
        lineTo(7.5f, 8.5f)
        lineTo(9f, 6f)
        lineTo(15f, 6f)
        lineTo(16.5f, 8.5f)
        lineTo(20.5f, 8.5f)
        lineTo(20.5f, 19f)
        lineTo(3.5f, 19f)
        close()
        circle(12f, 13.5f, 3.5f)
    }
    val Image = line {
        roundRect(3.5f, 4.5f, 20.5f, 19.5f, 2.5f)
        moveTo(3.5f, 16f)
        lineTo(8.5f, 11f)
        lineTo(13f, 15.5f)
        lineTo(15.5f, 13f)
        lineTo(20.5f, 18f)
        circle(15.5f, 8.8f, 1.5f)
    }
    val File = line {
        moveTo(6f, 3.5f)
        lineTo(14f, 3.5f)
        lineTo(18.5f, 8f)
        lineTo(18.5f, 20.5f)
        lineTo(6f, 20.5f)
        close()
        moveTo(14f, 3.5f)
        lineTo(14f, 8f)
        lineTo(18.5f, 8f)
    }
    val Mic = line {
        roundRect(9f, 3.5f, 15f, 14f, 3f)
        moveTo(5.5f, 11f)
        arcTo(6.5f, 6.5f, 0f, false, false, 18.5f, 11f)
        moveTo(12f, 17.5f)
        lineTo(12f, 20.5f)
    }
    val MicOff = line {
        roundRect(9f, 3.5f, 15f, 14f, 3f)
        moveTo(5.5f, 11f)
        arcTo(6.5f, 6.5f, 0f, false, false, 18.5f, 11f)
        moveTo(12f, 17.5f)
        lineTo(12f, 20.5f)
        moveTo(4f, 4f)
        lineTo(20f, 20f)
    }
    val Video = line {
        roundRect(3f, 6.5f, 15.5f, 17.5f, 2.5f)
        moveTo(15.5f, 10.5f)
        lineTo(21f, 7.5f)
        lineTo(21f, 16.5f)
        lineTo(15.5f, 13.5f)
    }
    val VideoOff = line {
        roundRect(3f, 6.5f, 15.5f, 17.5f, 2.5f)
        moveTo(15.5f, 10.5f)
        lineTo(21f, 7.5f)
        lineTo(21f, 16.5f)
        lineTo(15.5f, 13.5f)
        moveTo(3f, 4f)
        lineTo(20f, 21f)
    }
    val Speaker = line {
        moveTo(4f, 9.5f)
        lineTo(7.5f, 9.5f)
        lineTo(12f, 5.5f)
        lineTo(12f, 18.5f)
        lineTo(7.5f, 14.5f)
        lineTo(4f, 14.5f)
        close()
        moveTo(15.5f, 9f)
        arcTo(4f, 4f, 0f, false, true, 15.5f, 15f)
        moveTo(17.8f, 6.5f)
        arcTo(7.5f, 7.5f, 0f, false, true, 17.8f, 17.5f)
    }
    val Dialpad = line {
        for (row in 0..2) {
            for (col in 0..2) circle(6.5f + col * 5.5f, 5f + row * 5.5f, 1.1f)
        }
        circle(12f, 21f, 1.1f)
    }
    val PhoneDown = line {
        moveTo(3f, 14.5f)
        curveTo(7.5f, 9.5f, 16.5f, 9.5f, 21f, 14.5f)
        lineTo(18.5f, 17f)
        lineTo(15.5f, 15f)
        lineTo(15.5f, 12.8f)
        curveTo(13.2f, 12.1f, 10.8f, 12.1f, 8.5f, 12.8f)
        lineTo(8.5f, 15f)
        lineTo(5.5f, 17f)
        close()
    }
    val Hand = line {
        moveTo(8f, 12f)
        lineTo(8f, 5.5f)
        arcTo(1.5f, 1.5f, 0f, false, true, 11f, 5.5f)
        lineTo(11f, 11f)
        moveTo(11f, 4.5f)
        arcTo(1.5f, 1.5f, 0f, false, true, 14f, 4.5f)
        lineTo(14f, 11f)
        moveTo(14f, 6f)
        arcTo(1.5f, 1.5f, 0f, false, true, 17f, 6f)
        lineTo(17f, 14f)
        curveTo(17f, 18f, 15f, 20.5f, 11.5f, 20.5f)
        curveTo(8.5f, 20.5f, 7f, 18.5f, 5f, 15f)
        lineTo(4.2f, 13.5f)
        arcTo(1.4f, 1.4f, 0f, false, true, 6.6f, 12.1f)
        lineTo(8f, 14f)
    }
    val Users = line {
        circle(9f, 8.5f, 3.5f)
        moveTo(2.5f, 19.5f)
        curveTo(3f, 15.5f, 5.5f, 14f, 9f, 14f)
        curveTo(12.5f, 14f, 15f, 15.5f, 15.5f, 19.5f)
        moveTo(15.5f, 5.2f)
        arcTo(3.3f, 3.3f, 0f, false, true, 15.5f, 11.8f)
        moveTo(18f, 14.4f)
        curveTo(20f, 15.2f, 21.2f, 17f, 21.5f, 19.5f)
    }
    val Person = line {
        circle(12f, 8f, 4f)
        moveTo(4.5f, 20.5f)
        curveTo(5f, 16f, 8f, 14f, 12f, 14f)
        curveTo(16f, 14f, 19f, 16f, 19.5f, 20.5f)
    }
    val Clock = line {
        circle(12f, 12f, 8.5f)
        moveTo(12f, 7.5f)
        lineTo(12f, 12f)
        lineTo(15f, 14f)
    }
    val Star = line {
        moveTo(12f, 3.5f)
        lineTo(14.6f, 9f)
        lineTo(20.5f, 9.6f)
        lineTo(16f, 13.6f)
        lineTo(17.3f, 19.5f)
        lineTo(12f, 16.5f)
        lineTo(6.7f, 19.5f)
        lineTo(8f, 13.6f)
        lineTo(3.5f, 9.6f)
        lineTo(9.4f, 9f)
        close()
    }
    val Bike = line {
        circle(6f, 16.5f, 3.5f)
        circle(18f, 16.5f, 3.5f)
        moveTo(6f, 16.5f)
        lineTo(10f, 9f)
        lineTo(15f, 9f)
        lineTo(18f, 16.5f)
        moveTo(10f, 9f)
        lineTo(12.5f, 16.5f)
        moveTo(14f, 5.5f)
        lineTo(16.5f, 5.5f)
        lineTo(15f, 9f)
    }
    val Plane = line {
        moveTo(21f, 4f)
        lineTo(3.5f, 11f)
        lineTo(10f, 13.5f)
        lineTo(12.5f, 20f)
        close()
        moveTo(10f, 13.5f)
        lineTo(21f, 4f)
    }
    val Calendar = line {
        roundRect(3.5f, 5.5f, 20.5f, 20f, 2.5f)
        moveTo(3.5f, 10f)
        lineTo(20.5f, 10f)
        moveTo(8f, 3.5f)
        lineTo(8f, 7f)
        moveTo(16f, 3.5f)
        lineTo(16f, 7f)
    }
    val Seat = line {
        moveTo(7f, 4f)
        lineTo(7f, 14f)
        lineTo(17f, 14f)
        moveTo(5f, 14f)
        lineTo(5f, 18f)
        lineTo(19f, 18f)
        lineTo(19f, 14f)
        moveTo(7f, 18f)
        lineTo(7f, 21f)
        moveTo(17f, 18f)
        lineTo(17f, 21f)
    }
    val Bulb = line {
        moveTo(9f, 17f)
        lineTo(15f, 17f)
        moveTo(10f, 20.5f)
        lineTo(14f, 20.5f)
        moveTo(9f, 17f)
        curveTo(9f, 14.5f, 6f, 13f, 6f, 9.5f)
        arcTo(6f, 6f, 0f, false, true, 18f, 9.5f)
        curveTo(18f, 13f, 15f, 14.5f, 15f, 17f)
    }
    val Thermo = line {
        moveTo(10f, 14f)
        lineTo(10f, 5.5f)
        arcTo(2f, 2f, 0f, false, true, 14f, 5.5f)
        lineTo(14f, 14f)
        arcTo(4f, 4f, 0f, true, true, 10f, 14f)
        close()
        moveTo(12f, 9f)
        lineTo(12f, 17f)
    }
    val Fan = line {
        circle(12f, 12f, 1.6f)
        moveTo(12f, 10.4f)
        curveTo(9f, 6f, 11f, 3f, 13.5f, 4f)
        curveTo(15.5f, 5f, 14.5f, 8.5f, 12f, 10.4f)
        moveTo(13.5f, 12.8f)
        curveTo(18.5f, 12.5f, 20f, 15.5f, 18.3f, 17.4f)
        curveTo(16.8f, 19f, 14f, 16.5f, 13.5f, 12.8f)
        moveTo(10.6f, 12.8f)
        curveTo(8.2f, 17.2f, 4.8f, 17.4f, 4.2f, 15f)
        curveTo(3.6f, 12.8f, 7f, 11.6f, 10.6f, 12.8f)
    }
    val Lock = line {
        roundRect(5f, 10.5f, 19f, 20.5f, 2.5f)
        moveTo(8f, 10.5f)
        lineTo(8f, 7.5f)
        arcTo(4f, 4f, 0f, false, true, 16f, 7.5f)
        lineTo(16f, 10.5f)
    }
    val Tv = line {
        roundRect(3f, 5f, 21f, 17f, 2.5f)
        moveTo(8f, 20.5f)
        lineTo(16f, 20.5f)
    }
    val Plug = line {
        moveTo(9f, 3.5f)
        lineTo(9f, 8f)
        moveTo(15f, 3.5f)
        lineTo(15f, 8f)
        moveTo(6f, 8f)
        lineTo(18f, 8f)
        lineTo(18f, 11f)
        arcTo(6f, 6f, 0f, false, true, 6f, 11f)
        close()
        moveTo(12f, 17f)
        lineTo(12f, 21f)
    }
    val Moon = line {
        moveTo(19.5f, 14.5f)
        arcTo(8f, 8f, 0f, true, true, 9.5f, 4.5f)
        arcTo(6.5f, 6.5f, 0f, false, false, 19.5f, 14.5f)
        close()
    }
    val Sun = line {
        circle(12f, 12f, 4f)
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            moveTo(12f + 6.5f * kotlin.math.cos(a).toFloat(), 12f + 6.5f * kotlin.math.sin(a).toFloat())
            lineTo(12f + 8.5f * kotlin.math.cos(a).toFloat(), 12f + 8.5f * kotlin.math.sin(a).toFloat())
        }
    }
    val Gear = line {
        circle(12f, 12f, 3f)
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            moveTo(12f + 6f * kotlin.math.cos(a).toFloat(), 12f + 6f * kotlin.math.sin(a).toFloat())
            lineTo(12f + 8.5f * kotlin.math.cos(a).toFloat(), 12f + 8.5f * kotlin.math.sin(a).toFloat())
        }
        circle(12f, 12f, 6f)
    }
    val Wifi = line {
        moveTo(3f, 9.5f)
        arcTo(13f, 13f, 0f, false, true, 21f, 9.5f)
        moveTo(6f, 13f)
        arcTo(8.5f, 8.5f, 0f, false, true, 18f, 13f)
        moveTo(9f, 16.3f)
        arcTo(4.2f, 4.2f, 0f, false, true, 15f, 16.3f)
        circle(12f, 19.5f, 0.6f)
    }
    val Bluetooth = line {
        moveTo(7f, 7.5f)
        lineTo(17f, 16.5f)
        lineTo(12f, 21f)
        lineTo(12f, 3f)
        lineTo(17f, 7.5f)
        lineTo(7f, 16.5f)
    }
    val Battery = line {
        roundRect(3f, 7.5f, 19f, 16.5f, 2.5f)
        moveTo(21f, 10.5f)
        lineTo(21f, 13.5f)
        moveTo(6.5f, 10.5f)
        lineTo(6.5f, 13.5f)
        moveTo(9.5f, 10.5f)
        lineTo(9.5f, 13.5f)
    }
    val Display = line {
        roundRect(7f, 3f, 17f, 21f, 2.5f)
        moveTo(10.5f, 18f)
        lineTo(13.5f, 18f)
    }
    val TextSize = line {
        moveTo(3f, 19f)
        lineTo(8f, 6f)
        lineTo(13f, 19f)
        moveTo(4.8f, 14.5f)
        lineTo(11.2f, 14.5f)
        moveTo(14f, 19f)
        lineTo(17.5f, 10.5f)
        lineTo(21f, 19f)
        moveTo(15.2f, 16f)
        lineTo(19.8f, 16f)
    }
    val Globe = line {
        circle(12f, 12f, 8.5f)
        moveTo(3.5f, 12f)
        lineTo(20.5f, 12f)
        moveTo(12f, 3.5f)
        curveTo(8.5f, 8f, 8.5f, 16f, 12f, 20.5f)
        curveTo(15.5f, 16f, 15.5f, 8f, 12f, 3.5f)
    }
    val Shield = line {
        moveTo(12f, 3f)
        lineTo(19.5f, 6f)
        curveTo(19.5f, 13f, 16.5f, 18f, 12f, 21f)
        curveTo(7.5f, 18f, 4.5f, 13f, 4.5f, 6f)
        close()
        moveTo(9f, 12f)
        lineTo(11.2f, 14.2f)
        lineTo(15.2f, 10f)
    }
    val Check = line {
        moveTo(5f, 12.5f)
        lineTo(10f, 17.5f)
        lineTo(19f, 7f)
    }
    val Gift = line {
        roundRect(4f, 9f, 20f, 20.5f, 2f)
        moveTo(3f, 9f)
        lineTo(21f, 9f)
        moveTo(12f, 9f)
        lineTo(12f, 20.5f)
        moveTo(12f, 9f)
        curveTo(9f, 3f, 5.5f, 5.5f, 8f, 9f)
        moveTo(12f, 9f)
        curveTo(15f, 3f, 18.5f, 5.5f, 16f, 9f)
    }
    val Truck = line {
        moveTo(2.5f, 6f)
        lineTo(14f, 6f)
        lineTo(14f, 16.5f)
        lineTo(2.5f, 16.5f)
        close()
        moveTo(14f, 9.5f)
        lineTo(18.5f, 9.5f)
        lineTo(21.5f, 13f)
        lineTo(21.5f, 16.5f)
        lineTo(14f, 16.5f)
        circle(6.5f, 18f, 1.8f)
        circle(17.5f, 18f, 1.8f)
    }
    val More = line {
        circle(5.5f, 12f, 0.9f)
        circle(12f, 12f, 0.9f)
        circle(18.5f, 12f, 0.9f)
    }
    val Edit = line {
        moveTo(4f, 20f)
        lineTo(4.8f, 16f)
        lineTo(15.5f, 5.3f)
        arcTo(2f, 2f, 0f, false, true, 18.7f, 8.5f)
        lineTo(8f, 19.2f)
        close()
    }
    val Grid = line {
        roundRect(4f, 4f, 10.5f, 10.5f, 2f)
        roundRect(13.5f, 4f, 20f, 10.5f, 2f)
        roundRect(4f, 13.5f, 10.5f, 20f, 2f)
        roundRect(13.5f, 13.5f, 20f, 20f, 2f)
    }
    val Chart = line {
        moveTo(4f, 20f)
        lineTo(20f, 20f)
        moveTo(7f, 16f)
        lineTo(7f, 11f)
        moveTo(12f, 16f)
        lineTo(12f, 6f)
        moveTo(17f, 16f)
        lineTo(17f, 9f)
    }
    val Info = line {
        circle(12f, 12f, 8.5f)
        moveTo(12f, 11f)
        lineTo(12f, 16.5f)
        circle(12f, 7.8f, 0.4f)
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, true, true, cx + r, cy)
        arcTo(r, r, 0f, true, true, cx - r, cy)
        close()
    }

    private fun PathBuilder.roundRect(left: Float, top: Float, right: Float, bottom: Float, r: Float) {
        moveTo(left + r, top)
        lineTo(right - r, top)
        arcTo(r, r, 0f, false, true, right, top + r)
        lineTo(right, bottom - r)
        arcTo(r, r, 0f, false, true, right - r, bottom)
        lineTo(left + r, bottom)
        arcTo(r, r, 0f, false, true, left, bottom - r)
        lineTo(left, top + r)
        arcTo(r, r, 0f, false, true, left + r, top)
        close()
    }

    private fun line(mirrored: Boolean = false, block: PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = mirrored,
    ).path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    ).build()
}

/** 회전이나 폴더블 접기·펴기로 액티비티가 다시 만들어져도 남는 정수 맵. 장바구니 수량, 좋아요처럼 id별 값을 담는다. */
@Composable
internal fun rememberIntMap(vararg initial: Pair<Int, Int>): SnapshotStateMap<Int, Int> = rememberIntMap(initial.asList())

@Composable
internal fun rememberIntMap(initial: List<Pair<Int, Int>>): SnapshotStateMap<Int, Int> =
    rememberSaveable(saver = IntMapSaver) { mutableStateMapOf<Int, Int>().apply { initial.forEach { (k, v) -> put(k, v) } } }

private val IntMapSaver = listSaver<SnapshotStateMap<Int, Int>, Int>(
    save = { map -> map.flatMap { listOf(it.key, it.value) } },
    restore = { saved -> mutableStateMapOf<Int, Int>().apply { saved.chunked(2).forEach { put(it[0], it[1]) } } },
)

/** 다시 만들어져도 남는 문자열 목록. 보낸 메시지, 할 일처럼 사용자가 더한 항목을 담는다. */
@Composable
internal fun rememberStringList(initial: List<String> = emptyList()): SnapshotStateList<String> =
    rememberSaveable(saver = StringListSaver) { initial.toMutableStateList() }

private val StringListSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)

/** 앱 화면 예 id에 맞는 [AppLook]. 이 파일 묶음에서 만든 앱만 돌려주고, 다른 앱은 null이다. 상태 표시줄 아이콘 밝기를 정할 때 쓴다. */
internal fun appLookOf(id: String): AppLook? = when (id) {
    "shop" -> SHOP_LOOK
    "bank" -> BANK_LOOK
    "chat" -> CHAT_LOOK
    "feed" -> FEED_LOOK
    "delivery" -> DELIVERY_LOOK
    "travel" -> TRAVEL_LOOK
    "smarthome" -> SMART_HOME_LOOK
    "tasks" -> TASKS_LOOK
    "settings" -> SETTINGS_LOOK
    "call" -> CALL_LOOK
    else -> null
}
