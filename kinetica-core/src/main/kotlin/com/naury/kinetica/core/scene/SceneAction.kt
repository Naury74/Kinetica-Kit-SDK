package com.naury.kinetica.core.scene

import java.net.URLDecoder

/**
 * 노드 액션([SceneNode.action]) 문자열을 이름과 파라미터로 나눈 것. 액션은 `open_order?id=42`처럼 `이름?키=값&키=값` 형식으로 쓴다.
 * `%20` 같은 URL 인코딩은 디코딩해 준다. 문서에는 여전히 문자열 하나로 저장되며 SDK가 코드를 실행하지는 않는다.
 *
 * ```kotlin
 * KineticaPlayer(document, onAction = { raw ->
 *     val action = SceneAction.parse(raw)
 *     when (action.name) { "open_order" -> openOrder(action["id"]) }
 * })
 * ```
 */
public data class SceneAction(val name: String, val params: Map<String, String> = emptyMap()) {
    public operator fun get(key: String): String? = params[key]

    public companion object {
        /**
         * [raw]를 이름과 파라미터로 나눈다. 형식이 어긋나도 예외를 던지지 않고 최대한 읽는다.
         * `?`가 없으면 파라미터 없이 이름만 쓰고, `=`가 없는 키는 빈 값이 된다. 같은 키가 여러 번 나오면 마지막 값을 쓰고,
         * 빈 문자열이면 이름도 빈 문자열이다. 문서에 저장된 액션은 [isValidAction] 검증을 통과했으므로 이름이 비어 있지 않다.
         */
        @JvmStatic
        public fun parse(raw: String): SceneAction {
            val name = raw.substringBefore('?').trim()
            val query = raw.substringAfter('?', "")
            val params = query.split('&').filter { it.isNotEmpty() }.associate { pair ->
                decode(pair.substringBefore('=')) to decode(pair.substringAfter('=', ""))
            }
            return SceneAction(name, params)
        }

        private fun decode(text: String): String = runCatching { URLDecoder.decode(text, Charsets.UTF_8.name()) }.getOrDefault(text)
    }
}
