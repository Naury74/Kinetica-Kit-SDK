package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * [KAmountInput] 위 빠른 금액 칩 하나. 누르면 지금 금액에 [amount]를 더한다.
 *
 * @property label 칩 글자. 예: "+1만"
 */
@Immutable
public data class KAmountPreset(val label: String, val amount: BigDecimal)

/** 금액 입력 값 도우미. 입력 값은 숫자와 소수점 하나로 된 문자열이다(예: "12500", "12.5"). */
public object KAmountDefaults {
    /** [value]를 숫자로 바꾼다. 비었거나 형식이 틀리면 0이다. */
    @JvmStatic
    public fun toBigDecimal(value: String): BigDecimal = value.trimEnd('.').toBigDecimalOrNull() ?: BigDecimal.ZERO

    /** [value]를 [currency] 통화 형식으로 쓴다. 입력 중인 소수점과 뒤 0("12.", "12.50")은 그대로 둔다. */
    @JvmStatic
    public fun format(value: String, currency: Currency, locale: Locale = Locale.getDefault()): String {
        val whole = value.substringBefore('.').ifEmpty { "0" }
        val fraction = if ('.' in value) value.substringAfter('.') else null
        val formatter = NumberFormat.getCurrencyInstance(locale).apply {
            this.currency = currency
            minimumFractionDigits = 0
            maximumFractionDigits = 0
        }
        val text = formatter.format(BigDecimal(whole))
        if (fraction == null) return text
        // 통화 기호가 앞에 오든 뒤에 오든 마지막 숫자 뒤에 소수 부분을 끼운다.
        val end = text.indexOfLast { it.isDigit() } + 1
        val separator = DecimalFormatSymbols.getInstance(locale).monetaryDecimalSeparator
        return text.substring(0, end) + separator + fraction + text.substring(end)
    }

    /** [current]에 [key]를 눌렀을 때의 새 값. 소수 자리와 정수 자리 제한을 넘으면 그대로 돌려준다. */
    internal fun append(current: String, key: Char, fractionDigits: Int, maxDigits: Int): String = when {
        key == '.' -> if (fractionDigits == 0 || '.' in current) current else current.ifEmpty { "0" } + "."

        '.' in current -> if (current.substringAfter('.').length >= fractionDigits) current else current + key

        // 앞자리 0은 하나만 둔다. "0" 뒤에 숫자를 누르면 0을 바꾼다.
        current == "0" || current.isEmpty() -> if (key == '0') "0" else key.toString()

        current.length >= maxDigits -> current

        else -> current + key
    }

    /** [current]에 [amount]를 더한 값. 정수 자리 제한을 넘으면 그대로 돌려준다. */
    internal fun add(current: String, amount: BigDecimal, maxDigits: Int): String {
        val sum = toBigDecimal(current) + amount
        val plain = sum.stripTrailingZeros().toPlainString()
        return if (plain.substringBefore('.').length > maxDigits) current else plain
    }
}

/**
 * 송금과 결제 화면의 금액 입력. 큰 금액 글자 아래에 빠른 금액 칩과 [KKeypad]를 놓는다. 시스템 키보드 대신 키패드로만 입력한다.
 * 금액은 기기 언어의 통화 형식(천 단위 구분, 통화 기호 위치)으로 보이고, 숫자가 바뀔 때 자릿수가 굴러간다. 비어 있으면 0을 옅게 쓰고,
 * 끝에 깜박이는 커서를 둔다('애니메이션 삭제' 설정에서는 깜박이지 않는다). [currency]의 소수 자릿수만큼만 소수를 받는다(원화는 0자리).
 *
 * TalkBack은 금액을 '보낼 금액, ₩12,500'처럼 읽고 바뀔 때마다 알린다. [error]가 있으면 오류로 알린다.
 *
 * ```kotlin
 * var amount by rememberSaveable { mutableStateOf("") }
 * KAmountInput(
 *     amount, { amount = it }, Currency.getInstance("KRW"),
 *     label = "보낼 금액", presets = listOf(KAmountPreset("+1만", BigDecimal(10_000))),
 * )
 * ```
 *
 * @param value 입력 값. 숫자와 소수점 하나로 된 문자열이다. 금액은 [KAmountDefaults.toBigDecimal]로 얻는다.
 * @param onValueChange 바뀐 입력 값
 * @param maxDigits 정수 부분 최대 자릿수. 1 미만이면 [IllegalArgumentException]을 던진다.
 * @param hint 금액 아래 보조 글자. 예: "잔액 120,000원"
 * @param error 금액 아래 오류 글자. [hint]보다 우선하고 [KineticaColors.destructive]로 쓴다.
 * @param showKeypad false면 키패드를 숨긴다. 호스트가 키패드를 다른 곳(바텀 시트 등)에 둘 때 쓴다.
 */
@Composable
public fun KAmountInput(
    value: String,
    onValueChange: (String) -> Unit,
    currency: Currency,
    modifier: Modifier = Modifier,
    label: String? = null,
    hint: String? = null,
    error: String? = null,
    presets: List<KAmountPreset> = emptyList(),
    maxDigits: Int = DEFAULT_MAX_DIGITS,
    showKeypad: Boolean = true,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(maxDigits >= 1) { "maxDigits는 1 이상이어야 합니다." }
    val locale = LocalConfiguration.current.locales.get(0) ?: Locale.ROOT
    val fractionDigits = currency.defaultFractionDigits.coerceAtLeast(0)
    val shown = KAmountDefaults.format(value, currency, locale)
    val latest by rememberUpdatedState(value)
    val latestChange by rememberUpdatedState(onValueChange)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AmountDisplay(AmountText(label, shown, value.isEmpty(), hint, error), surface)
        if (presets.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presets.forEach { preset ->
                    KSuggestionChip(preset.label, {
                        latestChange(KAmountDefaults.add(latest, preset.amount, maxDigits))
                    }, surface = surface)
                }
            }
        }
        if (showKeypad) {
            KKeypad(
                onDigit = { key -> latestChange(KAmountDefaults.append(latest, key, fractionDigits, maxDigits)) },
                onBackspace = { latestChange(latest.dropLast(1)) },
                mode = if (fractionDigits > 0) KKeypadMode.Decimal else KKeypadMode.Numeric,
                onClear = { latestChange("") },
                surface = surface,
            )
        }
    }
}

private class AmountText(val label: String?, val shown: String, val empty: Boolean, val hint: String?, val error: String?)

/** 금액 글자, 이름, 보조 글자. TalkBack에는 한 덩어리로 읽힌다. */
@Composable
private fun AmountDisplay(text: AmountText, surface: SurfaceStyle) {
    val ink = surface.contentColor
    val label = text.label
    val shown = text.shown
    val error = text.error
    val hint = text.hint
    val description = joinChartParts(listOfNotNull(label, shown))
    Column(
        Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = description
            stateDescription = shown
            liveRegion = LiveRegionMode.Polite
            error?.let { error(it) }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        label?.let { ThemedText(it, style = TextStyle(color = surface.muted(MUTED), fontSize = 14.sp)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RollingText(
                shown,
                style = themed(
                    TextStyle(
                        color = if (text.empty) surface.muted(PLACEHOLDER) else ink,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    ),
                ),
            )
            AmountCaret(KineticaTheme.colors.accent)
        }
        val note = error ?: hint
        if (note != null) {
            val danger = readableOn(KineticaTheme.colors.destructive, surface.inkBackground())
            val color = if (error != null) danger else surface.muted(MUTED)
            ThemedText(note, style = TextStyle(color = color, fontSize = 13.sp, textAlign = TextAlign.Center))
        }
    }
}

/** 금액 끝의 깜박이는 커서. 화면에 보이는 동안만 깜박이고, 값은 그리기 단계에서 읽는다. */
@Composable
private fun AmountCaret(color: Color) {
    val clock = rememberLoopClock(!reducedMotion())
    Box(
        Modifier
            .padding(start = 2.dp)
            .width(CARET_WIDTH)
            .height(CARET_HEIGHT)
            .loopVisibility(clock)
            .drawBehind {
                val on = (clock.seconds % CARET_PERIOD) < CARET_PERIOD / 2
                if (on) drawRect(color)
            },
    )
}

private const val DEFAULT_MAX_DIGITS = 12
private const val MUTED = 0.64f
private const val PLACEHOLDER = 0.3f
private const val CARET_PERIOD = 1.06f
private val CARET_WIDTH = 2.dp
private val CARET_HEIGHT = 40.dp
