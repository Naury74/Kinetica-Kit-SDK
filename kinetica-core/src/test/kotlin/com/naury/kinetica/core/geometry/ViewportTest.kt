package com.naury.kinetica.core.geometry

import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import org.junit.Assert.assertThrows
import org.junit.Test

class ViewportTest {
    @Test
    fun `포인터 기준 확대는 1000번 반복해도 포인터 아래 문서 점을 유지한다`() {
        val random = Random(seed = 102)
        var viewport = Viewport(panX = 37.0, panY = -12.0, zoom = 1.0)
        repeat(1_000) {
            val anchor = Vec2(random.nextDouble(0.0, 1440.0), random.nextDouble(0.0, 3200.0))
            val before = viewport.toDocument(anchor)

            viewport = viewport.zoomedAround(anchor, viewport.zoom * random.nextDouble(0.5, 2.0))

            val after = viewport.toDocument(anchor)
            assertThat(after.distanceTo(before)).isLessThan(1e-6 * maxOf(1.0, before.length()))
        }
    }

    @Test
    fun `확대 배율은 0_1에서 16 사이로 맞춘다`() {
        val anchor = Vec2(100.0, 100.0)

        assertThat(Viewport().zoomedAround(anchor, 1_000.0).zoom).isEqualTo(Viewport.MAX_ZOOM)
        assertThat(Viewport().zoomedAround(anchor, 0.0).zoom).isEqualTo(Viewport.MIN_ZOOM)
        assertThat(Viewport().zoomedAround(anchor, Double.POSITIVE_INFINITY).zoom).isEqualTo(Viewport.MAX_ZOOM)
        assertThat(Viewport().zoomedAround(anchor, Double.NaN).zoom).isEqualTo(1.0)
    }

    @Test
    fun `범위를 벗어난 배율로는 만들 수 없다`() {
        assertThrows(IllegalArgumentException::class.java) { Viewport(zoom = 0.0) }
        assertThrows(IllegalArgumentException::class.java) { Viewport(zoom = 17.0) }
        assertThrows(IllegalArgumentException::class.java) { Viewport(panX = Double.NaN) }
    }

    @Test
    fun `문서와 화면 행렬은 서로 역이다`() {
        val viewport = Viewport(panX = 120.5, panY = -48.0, zoom = 2.625)
        val point = Vec2(333.3, 777.7)

        val roundTrip = viewport.viewportToDocument().map(viewport.documentToViewport().map(point))

        assertThat(roundTrip.distanceTo(point)).isLessThan(1e-9)
        assertThat(viewport.toViewport(point).distanceTo(viewport.documentToViewport().map(point))).isLessThan(1e-9)
    }

    @Test
    fun `극단 배율에서도 왕복한다`() {
        listOf(Viewport.MIN_ZOOM, 1.25, Viewport.MAX_ZOOM).forEach { zoom ->
            val viewport = Viewport(panX = -5_000.0, panY = 9_000.0, zoom = zoom)
            val point = Vec2(1_080.0, 1_920.0)

            assertThat(viewport.toDocument(viewport.toViewport(point)).distanceTo(point)).isLessThan(1e-9)
        }
    }

    @Test
    fun `fitting은 내용을 가운데에 꽉 차게 둔다`() {
        val viewport = Viewport.fitting(Rect(0.0, 0.0, 1080.0, 1920.0), viewportWidth = 540.0, viewportHeight = 1200.0)

        assertThat(viewport.zoom).isWithin(1e-12).of(0.5)
        val center = viewport.toViewport(Vec2(540.0, 960.0))
        assertThat(center.x).isWithin(1e-9).of(270.0)
        assertThat(center.y).isWithin(1e-9).of(600.0)
        val padded = Viewport.fitting(Rect(0.0, 0.0, 1080.0, 1920.0), viewportWidth = 540.0, viewportHeight = 1200.0, paddingPx = 30.0)
        assertThat(padded.zoom).isWithin(1e-12).of(480.0 / 1080.0)
    }

    @Test
    fun `크기가 0인 내용은 배율 1로 가운데에 둔다`() {
        val viewport = Viewport.fitting(Rect(10.0, 10.0, 10.0, 10.0), viewportWidth = 100.0, viewportHeight = 100.0)

        assertThat(viewport.zoom).isEqualTo(1.0)
        assertThat(viewport.toViewport(Vec2(10.0, 10.0))).isEqualTo(Vec2(50.0, 50.0))
    }

    @Test
    fun `panBy는 viewport 픽셀 단위로 움직인다`() {
        val moved = Viewport(zoom = 4.0).panBy(Vec2(8.0, -4.0))

        assertThat(moved.toDocument(Vec2(8.0, -4.0))).isEqualTo(Vec2.Zero)
    }
}
