package com.naury.kinetica.core.scene

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 텍스트에 바인딩된 숫자([DataValue.Number])의 표시 형식. 예를 들어 `suffix = "원"`, `grouping = true`이면 `1,284,000원`이 된다.
 * 기기 언어와 상관없이 소수점은 `.`, 천 단위 구분자는 `,`로 같게 표시한다. 숫자가 아닌 문자열 값에는 적용하지 않는다.
 *
 * @property decimals 소수 자릿수. 0..6이며 `null`이면 소수점 아래의 불필요한 0만 지운다.
 * @property grouping `true`면 천 단위마다 `,`를 넣는다.
 */
public data class TextNumberFormat(
    val prefix: String = "",
    val suffix: String = "",
    val decimals: Int? = null,
    val grouping: Boolean = true,
) {
    /** [number]를 이 형식의 문자열로 바꾼다. 유한하지 않은 값이면 빈 문자열을 돌려준다. */
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

        /** prefix와 suffix 각각의 최대 길이(UTF-16). */
        public const val MAX_AFFIX_LENGTH: Int = 16
        private const val GROUP = 3
    }
}

internal fun checkNumberFormat(node: SceneNode, sink: ViolationSink) {
    val format = (node.content as? NodeContent.Text)?.numberFormat ?: return
    val badAffix = listOf(format.prefix, format.suffix).any { it.length > TextNumberFormat.MAX_AFFIX_LENGTH || !isWellFormedUtf16(it) }
    if (badAffix || (format.decimals != null && format.decimals !in 0..TextNumberFormat.MAX_DECIMALS)) {
        sink.add(ViolationCode.InvalidMetadata, "숫자 형식의 prefix나 suffix가 너무 길거나 소수 자릿수가 0..6 범위를 벗어났습니다.", node.id)
    }
}
