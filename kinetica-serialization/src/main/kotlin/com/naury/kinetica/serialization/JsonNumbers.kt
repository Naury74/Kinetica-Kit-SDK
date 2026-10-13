package com.naury.kinetica.serialization

import kotlinx.serialization.json.JsonPrimitive

private const val MAX_EXACT_LONG = 9_007_199_254_740_992.0

/** 정수인 값은 `120.0`이 아닌 `120`으로 쓴다. 같은 문서가 항상 같은 문자열이 되게 한다. */
internal fun numberPrimitive(value: Double): JsonPrimitive {
    val isExactInteger = value == Math.rint(value) && kotlin.math.abs(value) < MAX_EXACT_LONG
    return if (isExactInteger) JsonPrimitive(value.toLong()) else JsonPrimitive(value)
}
