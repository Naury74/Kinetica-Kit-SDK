package com.naury.kinetica.sample

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.scene.SceneValidator
import com.naury.kinetica.serialization.DecodeResult
import com.naury.kinetica.serialization.SceneCodec
import org.junit.Test

class DemoSceneTest {
    @Test
    fun `예제 장면은 문서 규칙을 지키고 저장 형식으로 왕복한다`() {
        val demo = DemoScene.create()
        val codec = SceneCodec()

        assertThat(SceneValidator().validate(demo)).isEmpty()
        assertThat((codec.decode(codec.encode(demo)) as DecodeResult.Success).document).isEqualTo(demo)
    }
}
