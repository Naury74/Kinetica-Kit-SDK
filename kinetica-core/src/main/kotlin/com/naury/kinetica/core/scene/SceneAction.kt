package com.naury.kinetica.core.scene

import java.net.URLDecoder

/**
 * 노드 동작([SceneNode.action]) 글자를 이름과 값으로 나눈 것. 동작은 `이름?키=값&키=값` 형식으로 쓴다(예: `open_order?id=42`).
 * 값은 URL 인코딩(`%20` 등)을 풀어 준다. 문서에 저장되는 것은 여전히 글자 하나이며 코드는 실행하지 않는다.
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
        /** [raw]를 나눈다. `?`가 없으면 값 없이 이름만이다. 같은 키가 여러 번이면 마지막 값이다. */
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
