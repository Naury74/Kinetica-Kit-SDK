package com.naury.kinetica.core.scene

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 글자에 묶인 숫자([DataValue.Number])를 보여 주는 형식. 예: `prefix = "₩"`, `suffix = "원"`, `grouping = true` → `1,284,000원`.
 * 기기 언어와 상관없이 같은 모양이다(소수점 `.`, 천 단위 `,`). 숫자가 아닌 값(글자)에는 쓰지 않는다.
 *
 * @property decimals 소수 자릿수(0..6). `null`이면 소수점 아래 0을 지운 그대로다.
 * @property grouping 천 단위마다 `,`를 넣는다.
 */
public data class TextNumberFormat(
    val prefix: String = "",
    val suffix: String = "",
    val decimals: Int? = null,
    val grouping: Boolean = true,
) {
    /** [number]를 이 형식의 글자로 바꾼다. 유한하지 않으면 빈 글자다. */
    public fun format(number: Double): String {
        if (!number.isFinite()) return ""
        val rounded = decimals?.let { BigDecimal(number).setScale(it, RoundingMode.HALF_UP) }
            ?: BigDecimal(number.toString()).stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }
        val plain = rounded.abs().toPlainString()
        val whole = plain.substringBefore('.')
        val fraction = plain.substringAfter('.', "")
        val grouped = if (grouping) whole.reversed().chunked(GROUP).joinToString(",").reversed() else whole
        val sign = if (rounded.signum() < 0) "-" else ""
        return prefix + sign + grouped + (if (fraction.isEmpty()) "" else ".$fraction") + suffix
    }

    public companion object {
        public const val MAX_DECIMALS: Int = 6

        /** 앞뒤 글자 하나의 최대 길이(UTF-16). */
        public const val MAX_AFFIX_LENGTH: Int = 16
        private const val GROUP = 3
    }
}

internal fun checkNumberFormat(node: SceneNode, sink: ViolationSink) {
    val format = (node.content as? NodeContent.Text)?.numberFormat ?: return
    val badAffix = listOf(format.prefix, format.suffix).any { it.length > TextNumberFormat.MAX_AFFIX_LENGTH || !isWellFormedUtf16(it) }
    if (badAffix || (format.decimals != null && format.decimals !in 0..TextNumberFormat.MAX_DECIMALS)) {
        sink.add(ViolationCode.InvalidMetadata, "숫자 형식의 앞뒤 글자가 너무 길거나 소수 자릿수가 0..6 밖입니다.", node.id)
    }
}
