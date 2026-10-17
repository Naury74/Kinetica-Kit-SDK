package com.naury.kinetica.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 통화 부품의 배치 계산. 화면 없이 순수 함수만 본다. */
class KCallLayoutTest {
    private val phone = Rect(0f, 0f, 400f, 800f)
    private val landscape = Rect(0f, 0f, 800f, 400f)

    private fun input(count: Int, full: Rect = phone, focus: Any? = null, self: Any? = null, corner: KCorner = KCorner.BottomEnd) =
        GridInput(
            keys = (0 until count).map { "p$it" },
            focus = focus,
            self = self,
            area = full,
            full = full,
            gap = 0f,
            strip = 100f,
            floatingWidth = 90f,
            margin = 10f,
            corner = corner,
            pageSize = 9,
            pageDots = 20f,
        )

    @Test
    fun `열 수는 칸 안 정사각형이 가장 커지는 값을 고른다`() {
        assertThat(gridColumns(1, 400f, 800f)).isEqualTo(1)
        // 세로 폰 2명은 위아래, 가로 화면 2명은 좌우
        assertThat(gridColumns(2, 400f, 800f)).isEqualTo(1)
        assertThat(gridColumns(2, 800f, 400f)).isEqualTo(2)
        assertThat(gridColumns(4, 400f, 800f)).isEqualTo(2)
        assertThat(gridColumns(6, 400f, 700f)).isEqualTo(2)
        assertThat(gridColumns(6, 800f, 360f)).isEqualTo(3)
        assertThat(gridColumns(9, 800f, 1100f)).isEqualTo(3)
    }

    @Test
    fun `1대1은 상대가 화면 전체이고 나는 고른 모서리의 작은 창이다`() {
        val plan = planGrid(input(2, self = "p0", corner = KCorner.TopStart))
        val full = plan.spots.single { it.role == KTileRole.Full }
        val floating = plan.spots.single { it.role == KTileRole.Floating }
        assertThat(full.key).isEqualTo("p1")
        assertThat(full.rect).isEqualTo(phone)
        assertThat(floating.key).isEqualTo("p0")
        assertThat(floating.rect.topLeft).isEqualTo(Offset(10f, 10f))
        // 세로 화면의 작은 창은 세로로 길다(3:4).
        assertThat(floating.rect.height).isGreaterThan(floating.rect.width)
        // 내가 누구인지 모르면 1대1이 아니라 격자다.
        assertThat(planGrid(input(2)).spots.map { it.role }.toSet()).containsExactly(KTileRole.Grid)
    }

    @Test
    fun `마지막 줄이 덜 차면 가운데로 모은다`() {
        val plan = planGrid(input(3, full = Rect(0f, 0f, 800f, 800f)))
        // 2×2에 3명이면 둘째 줄 한 칸은 가로 가운데에 놓인다.
        val last = plan.spots.last()
        assertThat(last.rect.center.x).isWithin(0.5f).of(400f)
    }

    @Test
    fun `크게 보는 사람이 있으면 세로 화면은 아래 띠, 가로 화면은 끝쪽 띠다`() {
        val portrait = planGrid(input(8, focus = "p2"))
        assertThat(portrait.spots.first().key).isEqualTo("p2")
        assertThat(portrait.spots.first().role).isEqualTo(KTileRole.Focus)
        assertThat(portrait.strip!!.horizontal).isTrue()
        assertThat(portrait.spots.drop(1).map { it.role }.toSet()).containsExactly(KTileRole.Strip)
        // 띠 칸은 7명 모두 "+N" 없이 늘어놓고 넘치면 스크롤로 본다.
        assertThat(portrait.spots.count { it.role == KTileRole.Strip }).isEqualTo(7)
        assertThat(portrait.maxScroll).isGreaterThan(0f)

        val wide = planGrid(input(5, full = landscape, focus = "p2"))
        assertThat(wide.strip!!.horizontal).isFalse()
        assertThat(wide.strip!!.area.left).isEqualTo(700f)
    }

    @Test
    fun `쪽 크기를 넘으면 쪽을 나눠 옆에 놓고 보이는 칸 근처만 그린다`() {
        val plan = planGrid(input(30))
        assertThat(plan.pages).isEqualTo(4)
        val second = plan.spots.filter { it.rect.left >= phone.width && it.rect.left < phone.width * 2 }
        assertThat(second.map { it.key }).containsExactlyElementsIn((9 until 18).map { "p$it" })
        val first = plan.spots.first()
        val far = plan.spots.last()
        assertThat(plan.isNearlyVisible(first, scroll = 0f)).isTrue()
        assertThat(plan.isNearlyVisible(far, scroll = 0f)).isFalse()
        assertThat(plan.isNearlyVisible(far, scroll = phone.width * 3)).isTrue()
        // 쪽 아래 점 줄 자리는 비운다.
        assertThat(plan.area.bottom).isEqualTo(780f)
    }

    @Test
    fun `가까운 모서리와 시계 방향 다음 모서리`() {
        assertThat(nearestCorner(Offset(10f, 10f), phone)).isEqualTo(KCorner.TopStart)
        assertThat(nearestCorner(Offset(390f, 790f), phone)).isEqualTo(KCorner.BottomEnd)
        assertThat(nearestCorner(Offset(10f, 790f), phone)).isEqualTo(KCorner.BottomStart)
        assertThat(KCorner.TopStart.clockwise()).isEqualTo(KCorner.TopEnd)
        assertThat(KCorner.BottomStart.clockwise()).isEqualTo(KCorner.TopStart)
    }

    @Test
    fun `통화 시간 문구`() {
        assertThat(formatCallDuration(0)).isEqualTo("0:00")
        assertThat(formatCallDuration(65_400)).isEqualTo("1:05")
        assertThat(formatCallDuration(3_725_000)).isEqualTo("1:02:05")
        assertThat(formatCallDuration(-5)).isEqualTo("0:00")
    }

    private fun item(key: String, essential: Boolean = false, tone: KTone = KTone.Neutral, badge: Int = 0) =
        KCallDockItem(key, KIcons.Mic, key, {}, essential = essential, tone = tone, badge = badge)

    @Test
    fun `도크는 넉넉하면 그대로, 좁으면 원을 줄이고, 그래도 넘치면 끊기 앞 더보기로 접는다`() {
        val items =
            listOf(
                item("mic", true),
                item("cam", true),
                item("a"),
                item("b"),
                item("c", badge = 2),
                item("d", badge = 1),
                item("end", true, KTone.Destructive),
            )
        val extent = { d: androidx.compose.ui.unit.Dp -> d + 8.dp }
        val roomy = planDock(items, 1_000.dp, 56.dp, extent)
        assertThat(roomy.overflow).isEmpty()
        assertThat(roomy.size).isEqualTo(56.dp)

        val tight = planDock(items, dockLength(7, 56.dp), 56.dp, extent)
        assertThat(tight.overflow).isEmpty()
        assertThat(tight.size).isEqualTo(KCallControlDefaults.CompactSize)

        val narrow = planDock(items, dockLength(5, 56.dp), 56.dp, extent)
        assertThat(narrow.shown.map { it.key }).containsExactly("mic", "cam", "a", "end").inOrder()
        assertThat(narrow.overflow.map { it.key }).containsExactly("b", "c", "d").inOrder()
        assertThat(narrow.overflow.sumOf { it.badge }).isEqualTo(3)
        // 더보기는 끊기 바로 앞이다.
        assertThat(narrow.moreIndex).isEqualTo(narrow.shown.indexOfFirst { it.key == "end" })
    }

    @Test
    fun `약한 연결은 약함과 끊김뿐이다`() {
        assertThat(KNetworkQuality.entries.filter { it.isWeak }).containsExactly(KNetworkQuality.Poor, KNetworkQuality.Lost)
    }
}
