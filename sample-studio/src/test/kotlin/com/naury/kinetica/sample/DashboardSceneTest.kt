package com.naury.kinetica.sample

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.scene.SceneValidator
import com.naury.kinetica.serialization.DecodeResult
import com.naury.kinetica.serialization.SceneCodec
import org.junit.Test

class DashboardSceneTest {
    @Test
    fun `대시보드 예제는 문서 규칙을 지키고 저장 형식으로 왕복한다`() {
        val dashboard = DashboardScene.create()
        val codec = SceneCodec()

        assertThat(SceneValidator().validate(dashboard)).isEmpty()
        assertThat((codec.decode(codec.encode(dashboard)) as DecodeResult.Success).document).isEqualTo(dashboard)
    }
}
