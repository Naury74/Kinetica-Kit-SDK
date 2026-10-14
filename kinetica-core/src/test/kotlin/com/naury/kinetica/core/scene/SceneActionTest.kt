package com.naury.kinetica.core.scene

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SceneActionTest {
    @Test
    fun `동작 글자를 이름과 값으로 나누고 인코딩을 푼다`() {
        val action = SceneAction.parse("open_order?id=42&title=%EC%BF%A0%ED%82%A4%20%EC%84%B8%ED%8A%B8&flag")

        assertThat(action.name).isEqualTo("open_order")
        assertThat(action["id"]).isEqualTo("42")
        assertThat(action["title"]).isEqualTo("쿠키 세트")
        assertThat(action["flag"]).isEqualTo("")
        assertThat(SceneAction.parse("refresh")).isEqualTo(SceneAction("refresh"))
        assertThat(SceneAction.parse("bad?x=%zz")["x"]).isEqualTo("%zz")
    }
}
