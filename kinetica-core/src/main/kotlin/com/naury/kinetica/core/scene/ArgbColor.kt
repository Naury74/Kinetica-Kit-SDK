package com.naury.kinetica.core.scene

/**
 * sRGB 색. 8비트 alpha, red, green, blue를 `0xAARRGGBB` 형태의 정수 하나에 담는다. alpha는 premultiplied가 아니다.
 */
@JvmInline
public value class ArgbColor(public val argb: Int) {
    public val alpha: Int get() = argb ushr ALPHA_SHIFT and CHANNEL_MASK
    public val red: Int get() = argb ushr RED_SHIFT and CHANNEL_MASK
    public val green: Int get() = argb ushr GREEN_SHIFT and CHANNEL_MASK
    public val blue: Int get() = argb and CHANNEL_MASK

    /** 불투명하면 `#RRGGBB`, 아니면 `#AARRGGBB` 형식의 대문자 문자열. */
    public fun toHex(): String = if (alpha == CHANNEL_MASK) {
        "#%06X".format(argb and RGB_MASK)
    } else {
        "#%08X".format(argb)
    }

    override fun toString(): String = toHex()

    public companion object {
        private const val ALPHA_SHIFT = 24
        private const val RED_SHIFT = 16
        private const val GREEN_SHIFT = 8
        private const val CHANNEL_MASK = 0xFF
        private const val RGB_MASK = 0xFFFFFF
        private const val RGB_HEX_DIGITS = 6
        private const val OPAQUE = 0xFF000000.toInt()
        private val hexPattern = Regex("#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})")

        public fun fromChannels(alpha: Int, red: Int, green: Int, blue: Int): ArgbColor {
            require(listOf(alpha, red, green, blue).all { it in 0..CHANNEL_MASK }) { "색 채널은 0..255 범위여야 합니다." }
            return ArgbColor((alpha shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue)
        }

        /** `#RRGGBB`(불투명) 또는 `#AARRGGBB`를 읽는다. 형식이 다르면 `null`을 돌려준다. */
        public fun parseHexOrNull(hex: String): ArgbColor? {
            if (!hexPattern.matches(hex)) return null
            val digits = hex.substring(1)
            val value = digits.toLong(radix = 16).toInt()
            return ArgbColor(if (digits.length == RGB_HEX_DIGITS) value or OPAQUE else value)
        }
    }
}
