package com.naury.kinetica.core.scene

/**
 * 문서 안에서 노드를 가리키는 안정적인 id. UUID나 ULID 같은 값을 권장한다.
 *
 * 영문자, 숫자, `.` `_` `:` `-`만 쓸 수 있고 길이는 1..[MAX_LENGTH]다.
 *
 * @throws IllegalArgumentException 형식에 맞지 않는 경우. 외부 입력은 [parseOrNull]로 먼저 확인한다.
 */
@JvmInline
public value class NodeId(public val value: String) {
    init {
        require(isValid(value)) { "NodeId 형식이 올바르지 않습니다." }
    }

    override fun toString(): String = value

    public companion object {
        public const val MAX_LENGTH: Int = 128

        private val pattern = Regex("[A-Za-z0-9._:-]{1,$MAX_LENGTH}")

        public fun isValid(value: String): Boolean = pattern.matches(value)

        public fun parseOrNull(value: String): NodeId? = if (isValid(value)) NodeId(value) else null
    }
}
