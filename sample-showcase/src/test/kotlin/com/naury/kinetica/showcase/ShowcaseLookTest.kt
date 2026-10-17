package com.naury.kinetica.showcase

import androidx.compose.runtime.saveable.SaverScope
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.ui.KineticaDesigns
import com.naury.kinetica.ui.SurfaceStyle
import org.junit.Test

/** 모양 설정의 형태 저장과, 앱 화면 예가 설정과 추천 표면 중 무엇을 쓰는지 본다. */
class ShowcaseLookTest {
    @Test
    fun `형태와 앱 표면 방식도 저장했다가 되살린다`() {
        val look = ShowcaseLook(SurfaceFamily.Solid, Brightness.Dark)
        look.selectDesign(3)
        look.appSurface = AppSurface.Recommended
        val saved = with(ShowcaseLook.Saver) { SaverScope { true }.save(look) }!!
        val restored = ShowcaseLook.Saver.restore(saved)!!
        assertThat(restored.design).isEqualTo(3)
        assertThat(restored.selectedDesign).isEqualTo(KineticaDesigns.Fluent)
        assertThat(restored.appSurface).isEqualTo(AppSurface.Recommended)
        assertThat(restored.summary()).isEqualTo("단색 · Fluent · 어둡게")
    }

    @Test
    fun `예전 저장 값에는 형태가 없어도 기본 형태로 되살린다`() {
        val restored = ShowcaseLook.Saver.restore(intArrayOf(0, 0, ShowcaseLook.DEFAULT_PRESET))!!
        assertThat(restored.selectedDesign).isEqualTo(KineticaDesigns.Cupertino)
        assertThat(restored.appSurface).isEqualTo(AppSurface.FollowLook)
    }

    @Test
    fun `형태 범위를 넘으면 끝 값으로 맞춘다`() {
        val look = ShowcaseLook()
        look.selectDesign(99)
        assertThat(look.design).isEqualTo(KineticaDesigns.all.lastIndex)
        look.selectDesign(-1)
        assertThat(look.design).isEqualTo(0)
    }

    @Test
    fun `앱은 기본으로 설정의 표면 종류를 따르고 종류가 같으면 앱 추천 스타일을 쓴다`() {
        // 메신저는 글래스를 추천하므로 글래스 설정에서는 앱이 고른 Acrylic을 그대로 쓴다.
        assertThat(ShowcaseLook(SurfaceFamily.Glass).surfaceFor(CHAT_LOOK)).isEqualTo(CHAT_LOOK.light)
        // 단색 설정이면 글래스 앱도 단색이 된다.
        assertThat(ShowcaseLook(SurfaceFamily.Solid).surfaceFor(CHAT_LOOK)).isInstanceOf(SurfaceStyle.Solid::class.java)
        // 톤 설정이면 앱 강조색으로 섞는다.
        val tonal = ShowcaseLook(SurfaceFamily.Tonal).surfaceFor(SHOP_LOOK)
        assertThat(tonal).isInstanceOf(SurfaceStyle.Tonal::class.java)
        assertThat((tonal as SurfaceStyle.Tonal).accent).isEqualTo(SHOP_LOOK.accent)
    }

    @Test
    fun `프리셋을 고르면 앱도 그 프리셋을 쓰고 앱 추천을 고르면 추천 표면으로 돌아간다`() {
        val look = ShowcaseLook()
        look.select(SurfaceFamily.Glass, 1)
        assertThat(look.surfaceFor(BANK_LOOK)).isEqualTo(look.selectedPreset!!.light)
        look.appSurface = AppSurface.Recommended
        assertThat(look.surfaceFor(BANK_LOOK)).isEqualTo(BANK_LOOK.light)
    }

    @Test
    fun `표면을 고정한 앱은 설정과 상관없이 추천 표면을 쓴다`() {
        assertThat(FEED_LOOK.fixed).isTrue()
        SurfaceFamily.entries.forEach { family ->
            assertThat(ShowcaseLook(family, Brightness.Dark).surfaceFor(FEED_LOOK)).isEqualTo(FEED_LOOK.dark)
        }
    }
}
