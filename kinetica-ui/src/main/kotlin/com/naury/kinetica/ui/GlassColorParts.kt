package com.naury.kinetica.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** sRGB 색을 HSV로 바꾼다. 무채색이라 색조가 정해지지 않으면 [fallbackHue]를 쓴다. */
internal fun Color.toPickerHsv(fallbackHue: Float = 0f): PickerHsv {
    val max = max(red, max(green, blue))
    val min = min(red, min(green, blue))
    val delta = max - min
    val hue = when {
        delta == 0f -> fallbackHue
        max == red -> HUE_SECTOR * (((green - blue) / delta) % SECTORS)
        max == green -> HUE_SECTOR * ((blue - red) / delta + GREEN_OFFSET)
        else -> HUE_SECTOR * ((red - green) / delta + BLUE_OFFSET)
    }
    val saturation = if (max == 0f) 0f else delta / max
    return PickerHsv((hue + FULL_TURN) % FULL_TURN, saturation, max, alpha)
}

/** `#RRGGBB` 형식의 글자. 알파는 넣지 않는다. */
internal fun hexCodeOf(color: Color): String = String.format(Locale.ROOT, "%06X", color.toArgb() and RGB_MASK)

/** `RRGGBB` 또는 `AARRGGBB` 16진수를 색으로 바꾼다. `#`은 있어도 된다. 형식이 맞지 않으면 `null`. 6자리면 [alpha]를 쓴다. */
internal fun parseHex(text: String, alpha: Float): Color? {
    val raw = text.removePrefix("#")
    val value = raw.toLongOrNull(HEX_RADIX) ?: return null
    return when (raw.length) {
        RGB_DIGITS -> Color(value.toInt() or OPAQUE).copy(alpha = alpha)
        ARGB_DIGITS -> Color(value.toInt())
        else -> null
    }
}

/** TalkBack이 읽을 대략적인 색 이름. 앞의 값은 기본 이름, 뒤의 값은 "밝은 %s"나 "어두운 %s" 같은 꾸밈 문구이고 없으면 `null`이다. */
internal fun colorNameOf(color: Color): Pair<Int, Int?> {
    val hsv = color.toPickerHsv()
    if (hsv.v < BLACK_VALUE) return R.string.kinetica_ui_color_black to null
    if (hsv.s < GRAY_SATURATION) {
        return when {
            hsv.v > WHITE_VALUE -> R.string.kinetica_ui_color_white to null
            hsv.v > LIGHT_GRAY_VALUE -> R.string.kinetica_ui_color_gray to R.string.kinetica_ui_color_light
            hsv.v < DARK_VALUE -> R.string.kinetica_ui_color_gray to R.string.kinetica_ui_color_dark
            else -> R.string.kinetica_ui_color_gray to null
        }
    }
    val base = HUE_NAMES.first { hsv.h < it.first }.second
    val tone = when {
        hsv.v < DARK_VALUE -> R.string.kinetica_ui_color_dark
        hsv.s < LIGHT_SATURATION && hsv.v > LIGHT_VALUE -> R.string.kinetica_ui_color_light
        else -> null
    }
    return base to tone
}

/** "밝은 파랑, #7FB2FF"처럼 이름과 HEX를 붙인 TalkBack 문구. */
@Composable
internal fun colorDescription(color: Color): String {
    val (base, tone) = colorNameOf(color)
    val name = stringResource(base)
    val full = if (tone == null) name else stringResource(tone, name)
    return stringResource(R.string.kinetica_ui_color_state, full, "#" + hexCodeOf(color))
}

/** 투명도를 보여 주는 체크무늬. */
internal fun DrawScope.drawAlphaChecker(cell: Float, light: Color, dark: Color) {
    drawRect(light)
    val columns = (size.width / cell).toInt() + 1
    val rows = (size.height / cell).toInt() + 1
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            if ((row + column) % 2 == 1) drawRect(dark, Offset(column * cell, row * cell), Size(cell, cell))
        }
    }
}

/** 색 손잡이. 그림자 위에 흰 테두리를 두르고 가운데를 [fill]로 채운 뒤 윗부분에 유리 같은 반사광을 얹는다. */
internal fun DrawScope.drawColorHandle(center: Offset, radius: Float, fill: Color) {
    val rim = radius * HANDLE_RIM
    drawCircle(Color.Black.copy(alpha = HANDLE_SHADOW), radius + rim / 2, center + Offset(0f, rim / 2))
    drawCircle(Color.White.copy(alpha = HANDLE_GLASS), radius, center)
    drawCircle(fill.copy(alpha = 1f), radius - rim, center)
    drawCircle(Color.White.copy(alpha = HANDLE_EDGE), radius, center, style = Stroke(rim / 2))
    drawArc(
        Color.White.copy(alpha = HANDLE_SHINE),
        startAngle = SHINE_START,
        sweepAngle = SHINE_SWEEP,
        useCenter = false,
        topLeft = center - Offset(radius - rim * SHINE_INSET, radius - rim * SHINE_INSET),
        size = Size((radius - rim * SHINE_INSET) * 2, (radius - rim * SHINE_INSET) * 2),
        style = Stroke(rim * HANDLE_SHINE_WIDTH),
    )
}

/** 투명도 슬라이더. 체크무늬 위에 투명에서 [color]로 이어지는 띠를 그리고, 손잡이는 지금 색이다. RTL에서는 오른쪽이 투명이다. */
@Composable
internal fun ColorAlphaSlider(color: Color, onAlphaChange: (Float) -> Unit, onFinished: () -> Unit, modifier: Modifier) {
    val latest by rememberUpdatedState(onAlphaChange)
    val finished by rememberUpdatedState(onFinished)
    val title = stringResource(R.string.kinetica_ui_color_opacity)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var focused by remember { mutableStateOf(false) }
    val alpha by rememberUpdatedState(color.alpha)
    Box(
        modifier
            .fillMaxWidth()
            .height(ALPHA_HEIGHT)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                val step = alphaKeyStep(event.key, rtl) ?: return@onKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> latest((alpha + step).coerceIn(0f, 1f))
                    KeyEventType.KeyUp -> finished()
                }
                true
            }
            .focusTarget()
            .semantics {
                contentDescription = title
                progressBarRangeInfo = ProgressBarRangeInfo(color.alpha, 0f..1f)
                setProgress {
                    latest(it.coerceIn(0f, 1f))
                    finished()
                    true
                }
            }
            .holdViewParentDrag(Orientation.Horizontal)
            .pointerInput(rtl) {
                detectTapGestures {
                    latest(alphaAt(it.x, size.width.toFloat(), size.height.toFloat(), rtl))
                    finished()
                }
            }
            .pointerInput(rtl) {
                detectHorizontalDragGestures(onDragEnd = { finished() }) { change, _ ->
                    change.consume()
                    latest(alphaAt(change.position.x, size.width.toFloat(), size.height.toFloat(), rtl))
                }
            }
            .alphaTrack(color, rtl) { focused },
    )
}

/** 체크무늬 위에 투명에서 불투명으로 이어지는 띠와 손잡이를 그린다. */
private fun Modifier.alphaTrack(color: Color, rtl: Boolean, focused: () -> Boolean): Modifier = drawWithCache {
    val opaque = color.copy(alpha = 1f)
    val trackHeight = size.height * ALPHA_TRACK_RATIO
    val top = (size.height - trackHeight) / 2
    val radius = trackHeight / 2
    val inset = size.height / 2
    val checker = obtainGraphicsLayer()
    checker.record(size = IntSize(size.width.toInt(), trackHeight.toInt())) {
        drawAlphaChecker(CHECKER_CELL.toPx(), Color.White, Color(CHECKER_DARK))
    }
    val ramp = Brush.horizontalGradient(
        if (rtl) listOf(opaque, opaque.copy(alpha = 0f)) else listOf(opaque.copy(alpha = 0f), opaque),
        startX = inset,
        endX = size.width - inset,
    )
    val track = Path().apply {
        addRoundRect(
            RoundRect(
                0f,
                top,
                size.width,
                top + trackHeight,
                CornerRadius(radius),
            ),
        )
    }
    onDrawBehind {
        clipPath(track) {
            translate(0f, top) { drawLayer(checker) }
            drawRect(ramp)
        }
        val x = inset + (size.width - inset * 2) * (if (rtl) 1f - color.alpha else color.alpha)
        val handle = size.height / 2 * ALPHA_HANDLE_RATIO
        drawColorHandle(Offset(x, size.height / 2), handle, color.compositeOverWhite())
        if (focused()) drawFocusRing(Offset(x, size.height / 2), handle)
    }
}

/** 투명도 슬라이더 방향키 한 번의 변화량. 왼쪽과 오른쪽은 RTL에서 뒤집고, 위는 올리고 아래는 내린다. 다른 키면 `null`. */
internal fun alphaKeyStep(key: Key, rtl: Boolean): Float? = when (key) {
    Key.DirectionRight -> if (rtl) -COLOR_KEY_STEP else COLOR_KEY_STEP
    Key.DirectionLeft -> if (rtl) COLOR_KEY_STEP else -COLOR_KEY_STEP
    Key.DirectionUp -> COLOR_KEY_STEP
    Key.DirectionDown -> -COLOR_KEY_STEP
    else -> null
}

/** 키보드 초점이 있는 손잡이 둘레에 그리는 고리. 밝은 배경과 어두운 배경 모두에서 보이게 흰 고리 바깥에 어두운 고리를 두른다. */
internal fun DrawScope.drawFocusRing(center: Offset, radius: Float) {
    val width = FOCUS_RING_WIDTH.toPx()
    val gap = FOCUS_RING_GAP.toPx()
    drawCircle(Color.Black.copy(alpha = FOCUS_RING_SHADE), radius + gap + width + width / 2, center, style = Stroke(width))
    drawCircle(Color.White, radius + gap + width / 2, center, style = Stroke(width))
}

/** 손잡이 중심이 양 끝에서 반지름만큼 들어가도록 [x]를 알파(0..1)로 바꾼다. */
private fun alphaAt(x: Float, width: Float, height: Float, rtl: Boolean): Float {
    val inset = height / 2f
    val raw = ((x - inset) / (width - inset * 2).coerceAtLeast(1f)).coerceIn(0f, 1f)
    return if (rtl) 1f - raw else raw
}

/** 반투명 색을 흰 바탕에 얹었을 때의 색. 손잡이 안을 칠할 때 쓴다. */
private fun Color.compositeOverWhite(): Color = Color(
    red * alpha + (1f - alpha),
    green * alpha + (1f - alpha),
    blue * alpha + (1f - alpha),
)

/** HEX 입력란. 포커스가 없을 때는 지금 색을 보여 주고, 6자리나 8자리를 다 입력하면 바로 적용한다. */
@Composable
internal fun ColorHexField(color: Color, onColor: (Color) -> Unit, style: GlassStyle, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(hexCodeOf(color)) }
    val shown = if (focused) text else hexCodeOf(color)
    val title = stringResource(R.string.kinetica_ui_color_hex)
    val accent = GlassTheme.colors.accent
    val grow = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val hexStyle =
        TextStyle(color = style.onGlass, fontSize = HEX_FONT_SIZE, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
    // HEX 코드는 RTL 언어에서도 왼쪽부터 쓴다.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier
                .heightIn(min = HEX_HEIGHT)
                .glass(style, CircleShape)
                .padding(horizontal = HEX_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(HEX_SWATCH).clip(CircleShape).checkerFill(color))
            ThemedText(
                "#",
                Modifier.padding(start = HEX_GAP),
                style = TextStyle(
                    color = style.onGlass.copy(alpha = HASH_ALPHA),
                    fontSize = HEX_FONT_SIZE,
                    fontFamily = FontFamily.Monospace,
                ),
            )
            BasicTextField(
                shown,
                { next ->
                    val cleaned = next.uppercase(Locale.ROOT).filter { it in HEX_CHARS }.take(ARGB_DIGITS)
                    text = cleaned
                    if (cleaned.length == RGB_DIGITS || cleaned.length == ARGB_DIGITS) parseHex(cleaned, color.alpha)?.let(onColor)
                },
                Modifier
                    .width(HEX_FIELD_WIDTH * grow)
                    .onFocusChanged {
                        if (it.isFocused && !focused) text = hexCodeOf(color)
                        focused = it.isFocused
                    }
                    .semantics { contentDescription = title },
                singleLine = true,
                textStyle = themed(hexStyle),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
            )
        }
    }
}

/** 체크무늬 위에 [color]를 칠한다. 반투명 색이 얼마나 비치는지 보여 준다. */
private fun Modifier.checkerFill(color: Color): Modifier = drawWithCache {
    val cell = CHECKER_CELL.toPx() / 2
    onDrawBehind {
        drawAlphaChecker(cell, Color.White, Color(CHECKER_DARK))
        drawRect(color)
    }
}

/** 프리셋과 최근 색 칩. 지금 색과 같은 칩에는 고리를 그린다. */
@Composable
internal fun ColorSwatch(swatch: Color, selected: Boolean, onPick: () -> Unit) {
    val description = colorDescription(swatch)
    val ring = LocalGlassStyle.current.onGlass
    Box(
        Modifier
            .size(SWATCH_TOUCH)
            .clip(CircleShape)
            .clickable(null, GlassIndication(CenteredCircleShape((SWATCH_TOUCH - SWATCH_SIZE) / 2)), onClick = onPick)
            .semantics {
                contentDescription = description
                this.selected = selected
            }
            .drawWithCache {
                val radius = SWATCH_SIZE.toPx() / 2
                val center = Offset(size.width / 2, size.height / 2)
                val ringWidth = SWATCH_RING.toPx()
                onDrawBehind {
                    drawCircle(Color.Black.copy(alpha = SWATCH_SHADOW), radius + 1f, center + Offset(0f, 1f))
                    drawCircle(swatch.copy(alpha = 1f), radius, center)
                    drawCircle(Color.White.copy(alpha = SWATCH_EDGE), radius, center, style = Stroke(1.dp.toPx()))
                    if (selected) {
                        drawCircle(
                            ring.copy(alpha = SWATCH_RING_ALPHA),
                            radius + ringWidth * RING_GAP,
                            center,
                            style = Stroke(ringWidth),
                        )
                    }
                }
            },
    )
}

private val HUE_NAMES: List<Pair<Float, Int>> = listOf(
    15f to R.string.kinetica_ui_color_red,
    40f to R.string.kinetica_ui_color_orange,
    68f to R.string.kinetica_ui_color_yellow,
    165f to R.string.kinetica_ui_color_green,
    195f to R.string.kinetica_ui_color_teal,
    255f to R.string.kinetica_ui_color_blue,
    290f to R.string.kinetica_ui_color_purple,
    340f to R.string.kinetica_ui_color_pink,
    361f to R.string.kinetica_ui_color_red,
)

private const val FULL_TURN = 360f
private const val HUE_SECTOR = 60f
private const val SECTORS = 6f
private const val GREEN_OFFSET = 2f
private const val BLUE_OFFSET = 4f
private const val RGB_MASK = 0xFFFFFF
private const val OPAQUE = -0x1000000
private const val HEX_RADIX = 16
private const val RGB_DIGITS = 6
private const val ARGB_DIGITS = 8
private const val HEX_CHARS = "0123456789ABCDEF"
private const val BLACK_VALUE = 0.16f
private const val GRAY_SATURATION = 0.14f
private const val WHITE_VALUE = 0.92f
private const val LIGHT_GRAY_VALUE = 0.7f
private const val DARK_VALUE = 0.42f
private const val LIGHT_SATURATION = 0.5f
private const val LIGHT_VALUE = 0.8f
private const val HANDLE_RIM = 0.2f
private const val HANDLE_SHADOW = 0.22f
private const val HANDLE_GLASS = 0.92f
private const val HANDLE_EDGE = 0.9f
private const val HANDLE_SHINE = 0.45f
private const val HANDLE_SHINE_WIDTH = 0.6f
private const val SHINE_START = 200f
private const val SHINE_INSET = 1.5f
private const val RING_GAP = 1.5f
private const val SHINE_SWEEP = 70f
private val ALPHA_HEIGHT = 36.dp
private const val ALPHA_TRACK_RATIO = 0.62f
private const val ALPHA_HANDLE_RATIO = 0.95f
private val CHECKER_CELL = 6.dp
private const val CHECKER_DARK = 0xFFD5D8DE
private val HEX_HEIGHT = 44.dp
private val HEX_PADDING = 12.dp
private val HEX_SWATCH = 24.dp
private val HEX_GAP = 10.dp
private val HEX_FIELD_WIDTH = 96.dp
private val HEX_FONT_SIZE = 16.sp
private const val HASH_ALPHA = 0.5f
private val SWATCH_TOUCH = 40.dp
private val SWATCH_SIZE = 26.dp
private val SWATCH_RING = 2.dp
private const val SWATCH_SHADOW = 0.12f
private const val SWATCH_EDGE = 0.6f
private const val SWATCH_RING_ALPHA = 0.8f
internal const val COLOR_KEY_STEP = 0.02f
private const val FOCUS_RING_SHADE = 0.45f
private val FOCUS_RING_WIDTH = 2.dp
private val FOCUS_RING_GAP = 2.dp
