package com.naury.kinetica.editor

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.KeyValue
import com.naury.kinetica.core.animation.Keyframe
import com.naury.kinetica.core.animation.PropertyTrack
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.testing.TestScenes.id
import org.junit.Test

class TimelineViewTest {
    // 1초 80px, 앞 여백 16px, 뒤 여백 24px
    private val view = TimelineView(defaultPxPerMs = 0.08f, startPx = 16f, endPx = 24f).apply {
        durationMs = 3_000
        viewportPx = 400f
    }

    @Test
    fun `눈금 간격은 배율에 맞춰 바뀐다`() {
        assertThat(tickSteps(0.08f, 12f, 64f)).isEqualTo(TickSteps(250, 1_000))
        assertThat(tickSteps(0.32f, 12f, 64f)).isEqualTo(TickSteps(50, 250))
        assertThat(tickSteps(1.6f, 12f, 64f)).isEqualTo(TickSteps(10, 50))
        assertThat(tickSteps(0.01f, 12f, 64f)).isEqualTo(TickSteps(2_000, 10_000))
        assertThat(tickLabel(2_000)).isEqualTo("2s")
        assertThat(tickLabel(250)).isEqualTo("0.25s")
        assertThat(tickLabel(1_500)).isEqualTo("1.5s")
    }

    @Test
    fun `확대하면 기준점의 시각이 제자리에 남고 범위를 넘지 않는다`() {
        val anchorTime = view.timeAt(100f)

        view.zoomBy(4f, anchorX = 100f)

        assertThat(view.pxPerMs).isWithin(1e-6f).of(0.32f)
        assertThat(view.timeAt(100f)).isWithin(0.01).of(anchorTime)
        view.zoomBy(1_000f)
        assertThat(view.pxPerMs).isEqualTo(view.maxPxPerMs)
        view.zoomBy(0.0001f)
        assertThat(view.pxPerMs).isEqualTo(view.minPxPerMs)
        assertThat(view.scrollPx).isEqualTo(0f)
    }

    @Test
    fun `맞춤은 전체 길이를 한 화면에 넣고 스크롤은 끝을 넘지 않는다`() {
        view.fit()

        assertThat(view.contentPx).isWithin(0.01f).of(400f)
        assertThat(view.viewX(3_000.0)).isWithin(0.01f).of(376f)
        view.zoomBy(2f, anchorX = 0f)
        view.scrollBy(10_000f)
        assertThat(view.scrollPx).isEqualTo(view.maxScrollPx)
        view.scrollBy(-10_000f)
        assertThat(view.scrollPx).isEqualTo(0f)
    }

    @Test
    fun `재생 위치가 화면 밖이면 보이도록 스크롤한다`() {
        view.reveal(2_000.0)
        assertThat(view.scrollPx).isEqualTo(0f)

        view.zoomBy(4f, anchorX = 0f)
        view.reveal(1_500.0)

        assertThat(view.viewX(1_500.0)).isWithin(0.01f).of(400f * TimelineView.REVEAL_LEAD)
        view.reveal(0.0)
        assertThat(view.scrollPx).isEqualTo(0f)
    }

    @Test
    fun `선택은 탭과 Shift 탭, 사각 선택으로 바뀌고 없어진 키는 빠진다`() {
        val selection = KeySelection()
        val a = SelectedKey(id("a"), AnimatableProperty.TranslationX, 0)
        val b = SelectedKey(id("a"), AnimatableProperty.TranslationX, 500)

        selection.tap(a, additive = false)
        selection.tap(b, additive = true)
        assertThat(selection.keys).containsExactly(a, b)
        selection.tap(a, additive = true)
        assertThat(selection.keys).containsExactly(b)
        selection.marquee(setOf(a), additive = true, base = setOf(b))
        assertThat(selection.keys).containsExactly(a, b)

        val track = PropertyTrack(id("a"), AnimatableProperty.TranslationX, listOf(Keyframe(0, KeyValue.Scalar(0.0))))
        selection.retain(Timeline("main", 1_000, listOf(track)))
        assertThat(selection.keys).containsExactly(a)
    }
}
