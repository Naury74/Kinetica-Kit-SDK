// 파일 이름은 대표 컴포저블인 KKeypad를 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** [KKeypad]의 아래 줄 시작 쪽 키 */
public enum class KKeypadMode {
    /** PIN 입력. 둥근 키에 숫자와 알파벳을 쓰고, 아래 줄 시작 쪽은 [KKeypad]의 `extraKey`(생체 인증 버튼 자리)다. */
    Pin,

    /** 정수 입력. 아래 줄 시작 쪽이 "00" 키다. 원화처럼 소수가 없는 금액에 쓴다. */
    Numeric,

    /** 소수 입력. 아래 줄 시작 쪽이 기기 언어의 소수점 키다. */
    Decimal,

    /**
     * 전화 다이얼. 둥근 키에 숫자와 알파벳을 쓰고, 넷째 줄은 `*`, 0, `#`이다. 0을 길게 누르면 `+`를 넘긴다.
     * 다섯째 줄 가운데는 [KKeypad]의 `extraKey`(통화 버튼 자리), 끝 쪽은 지우기 키다.
     */
    Phone,
}

/**
 * 숫자 키패드. 1~9를 세 줄로, 아래 줄에 [mode]에 맞는 키와 0, 지우기 키를 놓는다. 키를 누르면 가벼운 햅틱을 주고,
 * 지우기 키를 길게 누르면 [onClear]를 부른다. 시스템 키보드를 띄우지 않으므로 PIN, 송금, 계산 화면에서 키보드 대신 쓴다.
 * 숫자 배치는 전화 키패드 관례대로 RTL에서도 바꾸지 않고, 지우기 키만 화살표 방향을 뒤집는다.
 * 키 크기는 폭에 맞춰 커지되 최대 폭은 [maxWidth]이고, 키 높이는 최소 터치 크기 이상이다.
 *
 * TalkBack은 키마다 숫자나 '지우기'를 버튼으로 읽는다.
 *
 * ```kotlin
 * KKeypad(onDigit = { pin += it }, onBackspace = { pin = pin.dropLast(1) }, mode = KKeypadMode.Pin)
 * ```
 *
 * @param onDigit 누른 키 글자. '0'~'9', "00" 키는 '0'을 두 번, 소수점 키는 '.'을 넘긴다. [KKeypadMode.Phone]에서는 '*', '#',
 *   0을 길게 누른 '+'도 넘긴다.
 * @param extraKey [KKeypadMode.Pin]에서는 아래 줄 시작 칸(생체 인증 버튼 등), [KKeypadMode.Phone]에서는 다섯째 줄 가운데 칸(통화 버튼)에
 *   놓을 내용. `null`이면 비운다.
 * @param letters 숫자 아래 알파벳(ABC, DEF …)을 쓸지. 기본값은 PIN과 전화 모드에서만 쓴다.
 */
@Composable
public fun KKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    mode: KKeypadMode = KKeypadMode.Numeric,
    onClear: (() -> Unit)? = null,
    enabled: Boolean = true,
    letters: Boolean = mode == KKeypadMode.Pin || mode == KKeypadMode.Phone,
    maxWidth: Dp = 360.dp,
    extraKey: (@Composable () -> Unit)? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val haptic = kHapticFeedback()
    val latestDigit by rememberUpdatedState(onDigit)
    val press: (Char) -> Unit = { char ->
        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
        latestDigit(char)
    }
    val locale = LocalConfiguration.current.locales.get(0) ?: Locale.ROOT
    val decimal = remember(locale) { DecimalFormatSymbols.getInstance(locale).decimalSeparator }
    val pin = mode == KKeypadMode.Pin || mode == KKeypadMode.Phone
    // PIN과 전화 키는 버튼처럼, 숫자 입력 키는 입력란 칸처럼 깎는다.
    val shape: Shape = if (pin) shapeFor { button } else shapeFor { this.field }
    val spec = KeySpec(shape, pin, letters, enabled, surface)
    val outer = LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier.widthIn(max = maxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (pin) PIN_GAP else KEY_GAP)) {
            DIGIT_ROWS.forEach { row ->
                KeyRow(pin) { row.forEach { d -> DigitKey(d, spec, Modifier.weight(1f)) { press(d) } } }
            }
            if (mode == KKeypadMode.Phone) {
                PhoneRows(spec, press, onBackspace, onClear, extraKey, outer)
                return@Column
            }
            KeyRow(pin) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    when (mode) {
                        KKeypadMode.Pin, KKeypadMode.Phone -> extraKey?.invoke()

                        KKeypadMode.Numeric -> TextKey("00", "00", spec, Modifier) {
                            press('0')
                            press('0')
                        }

                        KKeypadMode.Decimal -> TextKey(
                            decimal.toString(),
                            stringResource(R.string.kinetica_ui_keypad_decimal),
                            spec,
                            Modifier,
                        ) {
                            press('.')
                        }
                    }
                }
                DigitKey('0', spec, Modifier.weight(1f)) { press('0') }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(LocalLayoutDirection provides outer) { BackspaceKey(spec, onBackspace, onClear) }
                }
            }
        }
    }
}

/** 전화 모드의 넷째 줄(`*`, 0, `#`)과 다섯째 줄(통화 버튼 자리, 지우기). */
@Composable
private fun PhoneRows(
    spec: KeySpec,
    press: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: (() -> Unit)?,
    extraKey: (@Composable () -> Unit)?,
    outer: LayoutDirection,
) {
    val haptic = kHapticFeedback()
    val plusLabel = stringResource(R.string.kinetica_ui_keypad_plus)
    KeyRow(true) {
        SymbolKey('*', stringResource(R.string.kinetica_ui_keypad_star), spec, Modifier.weight(1f)) { press('*') }
        DigitKey(
            '0',
            spec,
            Modifier.weight(1f),
            longPress = KeyLongPress(plusLabel) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                press('+')
            },
        ) { press('0') }
        SymbolKey('#', stringResource(R.string.kinetica_ui_keypad_pound), spec, Modifier.weight(1f)) { press('#') }
    }
    KeyRow(true) {
        Box(Modifier.weight(1f))
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { extraKey?.invoke() }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalLayoutDirection provides outer) { BackspaceKey(spec, onBackspace, onClear) }
        }
    }
}

/** 길게 누를 때의 동작과 TalkBack이 읽을 그 동작 이름 */
private class KeyLongPress(val label: String, val action: () -> Unit)

@Composable
private fun SymbolKey(symbol: Char, label: String, spec: KeySpec, modifier: Modifier, onClick: () -> Unit) {
    val ink = spec.surface.contentColor
    Box(modifier, contentAlignment = Alignment.Center) {
        KeySurface(spec, label, onClick) {
            if (symbol == '*') {
                // 글꼴의 *는 위로 치우치고 작아 숫자와 어울리지 않으므로 키 가운데에 직접 그린다.
                Box(
                    Modifier.size(ASTERISK).drawBehind {
                        val r = size.minDimension / 2
                        val stroke = ASTERISK_STROKE.toPx()
                        repeat(ASTERISK_SPOKES) { i ->
                            val a = Math.toRadians(i * HALF_TURN / ASTERISK_SPOKES + QUARTER_TURN)
                            val dx = (cos(a) * r).toFloat()
                            val dy = (sin(a) * r).toFloat()
                            drawLine(ink, center + Offset(dx, dy), center - Offset(dx, dy), stroke, StrokeCap.Round)
                        }
                    },
                )
            } else {
                ThemedText(symbol.toString(), style = TextStyle(color = ink, fontSize = 30.sp, fontWeight = FontWeight.Normal))
            }
        }
    }
}

private class KeySpec(val shape: Shape, val pin: Boolean, val letters: Boolean, val enabled: Boolean, val surface: SurfaceStyle)

@Composable
private fun KeyRow(pin: Boolean, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (pin) PIN_GAP else KEY_GAP), content = content)
}

@Composable
private fun DigitKey(digit: Char, spec: KeySpec, modifier: Modifier, longPress: KeyLongPress? = null, onClick: () -> Unit) {
    val ink = spec.surface.contentColor
    Box(modifier, contentAlignment = Alignment.Center) {
        KeySurface(spec, digit.toString(), onClick, onLongClick = longPress?.action, onLongClickLabel = longPress?.label) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ThemedText(
                    digit.toString(),
                    style = TextStyle(color = ink, fontSize = if (spec.pin) 30.sp else 26.sp, fontWeight = FontWeight.Normal),
                )
                if (spec.letters) {
                    // 1과 0은 알파벳이 없지만 빈 줄을 두어 숫자 높이를 다른 키와 맞춘다. 길게 눌러 +를 넣는 0은 +를 적는다.
                    val letters = if (longPress != null) "+" else LETTERS[digit - '0'].ifEmpty { " " }
                    ThemedText(
                        letters,
                        style = TextStyle(
                            color = spec.surface.muted(MUTED),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.5.sp,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun TextKey(text: String, label: String, spec: KeySpec, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        KeySurface(spec, label, onClick, plain = true) {
            ThemedText(text, style = TextStyle(color = spec.surface.contentColor, fontSize = 24.sp))
        }
    }
}

@Composable
private fun BackspaceKey(spec: KeySpec, onBackspace: () -> Unit, onClear: (() -> Unit)?) {
    val haptic = kHapticFeedback()
    val label = stringResource(R.string.kinetica_ui_keypad_backspace)
    KeySurface(
        spec,
        label,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onBackspace()
        },
        plain = true,
        onLongClick = onClear?.let { clear ->
            {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                clear()
            }
        },
    ) { Glyph(KDataGlyphs.Backspace, spec.surface.contentColor, size = 26.dp) }
}

/** 키 하나. PIN 모드의 숫자 키만 표면을 깔고, 나머지는 눌렀을 때만 모양이 보인다. */
@Composable
private fun KeySurface(
    spec: KeySpec,
    label: String,
    onClick: () -> Unit,
    plain: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    content: @Composable () -> Unit,
) {
    val circle = spec.pin
    Box(
        Modifier
            .then(
                if (circle) {
                    Modifier.size(
                        PIN_KEY,
                    )
                } else {
                    Modifier.fillMaxWidth().heightIn(min = KEY_HEIGHT).aspectRatio(KEY_ASPECT, matchHeightConstraintsFirst = false)
                },
            )
            .graphicsLayer { alpha = if (spec.enabled) 1f else DISABLED }
            .then(if (circle && !plain) Modifier.kSurface(spec.surface, spec.shape) else Modifier)
            .clip(spec.shape)
            .combinedClickable(
                interactionSource = null,
                indication = KIndication(spec.shape, pressedScale = KIndication.BUTTON_PRESSED_SCALE),
                enabled = spec.enabled,
                role = Role.Button,
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick,
                onClick = onClick,
            ).clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * PIN 자리 점. [length]개 점 가운데 앞 [filled]개를 채운다. 점이 채워질 때 살짝 커졌다 돌아온다.
 * [error]가 true로 바뀌면 점이 [KineticaColors.destructive]로 바뀌며 좌우로 흔들린다(흔들기는 '애니메이션 삭제'에서 멈춘다).
 *
 * TalkBack은 '6자리 중 2자리 입력함'처럼 읽고, 바뀔 때마다 알린다.
 *
 * ```kotlin
 * KPinDots(6, pin.length, error = wrong)
 * ```
 *
 * @param length 자리 수. 1 미만이면 [IllegalArgumentException]을 던진다.
 * @param filled 입력한 자리 수. 범위 밖이면 0..[length]로 맞춘다.
 */
@Composable
public fun KPinDots(
    length: Int,
    filled: Int,
    modifier: Modifier = Modifier,
    error: Boolean = false,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(length >= 1) { "length는 1 이상이어야 합니다." }
    val count = filled.coerceIn(0, length)
    val state = stringResource(R.string.kinetica_ui_pin_state, length, count)
    val accent = if (error) KineticaTheme.colors.destructive else surface.contentColor
    val color by animateColorAsState(accent, label = "pin-color")
    val shakes = remember { mutableIntStateOf(0) }
    LaunchedEffect(error) { if (error) shakes.intValue++ }
    Row(
        modifier
            .shake(shakes.intValue)
            .semantics(mergeDescendants = true) {
                stateDescription = state
                liveRegion = LiveRegionMode.Polite
            },
        horizontalArrangement = Arrangement.spacedBy(PIN_DOT_GAP),
    ) {
        repeat(length) { i ->
            val on = i < count
            val scale by animateFloatAsState(if (on) 1f else DOT_IDLE, spring(dampingRatio = DOT_DAMPING), label = "pin-dot")
            Box(
                Modifier.size(PIN_DOT).graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.drawBehind {
                    if (on) drawCircle(color) else drawCircle(color.copy(alpha = EMPTY_ALPHA), style = Stroke(1.5.dp.toPx()))
                },
            )
        }
    }
}

private val DIGIT_ROWS = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'))
private val LETTERS = listOf("", "", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ")
private const val MUTED = 0.6f
private const val DISABLED = 0.45f
private const val KEY_ASPECT = 1.9f
private const val DOT_IDLE = 0.86f
private const val DOT_DAMPING = 0.45f
private const val EMPTY_ALPHA = 0.45f
private val KEY_HEIGHT = 52.dp
private val KEY_GAP = 6.dp
private val PIN_GAP = 16.dp
private val PIN_KEY = 76.dp
private val PIN_DOT = 14.dp
private val PIN_DOT_GAP = 16.dp
private val ASTERISK = 18.dp
private val ASTERISK_STROKE = 2.dp
private const val ASTERISK_SPOKES = 3
private const val HALF_TURN = 180.0
private const val QUARTER_TURN = 90.0
