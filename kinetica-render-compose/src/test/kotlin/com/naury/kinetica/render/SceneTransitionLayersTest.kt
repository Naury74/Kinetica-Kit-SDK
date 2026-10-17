package com.naury.kinetica.render

import androidx.compose.ui.geometry.Size
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.scene.SceneTransitionEdge
import com.naury.kinetica.core.scene.SceneTransitionKind
import com.naury.kinetica.core.scene.SceneTransitionSpec
import org.junit.Test

class SceneTransitionLayersTest {
    private val layer = TransitionLayer()

    private fun at(
        kind: SceneTransitionKind,
        front: Boolean,
        p: Float,
        from: SceneTransitionEdge = SceneTransitionEdge.End,
        rtl: Boolean = false,
    ): TransitionLayer = layer.apply { compute(SceneTransitionSpec(kind, from), front, p, Size(W, H), rtl) }

    @Test
    fun `밀기는 들어오는 장면이 끝 쪽에서 오고 나가는 장면은 조금 물러나며 어두워진다`() {
        assertThat(at(SceneTransitionKind.Push, front = true, p = 0f).translationX).isEqualTo(W)
        assertThat(at(SceneTransitionKind.Push, front = true, p = 0.5f).translationX).isEqualTo(W / 2)
        assertThat(at(SceneTransitionKind.Push, front = true, p = 1f).translationX).isEqualTo(0f)
        val back = at(SceneTransitionKind.Push, front = false, p = 1f)
        assertThat(back.translationX).isWithin(EPS).of(-W * 0.3f)
        assertThat(back.dim).isWithin(EPS).of(0.2f)
    }

    @Test
    fun `RTL이면 Start와 End의 좌우가 바뀌고 위아래는 그대로다`() {
        assertThat(at(SceneTransitionKind.Slide, true, 0f, rtl = true).translationX).isEqualTo(-W)
        assertThat(at(SceneTransitionKind.Slide, true, 0f, SceneTransitionEdge.Start, rtl = true).translationX).isEqualTo(W)
        assertThat(at(SceneTransitionKind.Slide, true, 0f, SceneTransitionEdge.Bottom, rtl = true).translationY).isEqualTo(H)
        assertThat(at(SceneTransitionKind.Slide, false, 1f, SceneTransitionEdge.Top).translationY).isEqualTo(H)
    }

    @Test
    fun `와이프는 들어오는 쪽 가장자리부터 드러난다`() {
        val half = at(SceneTransitionKind.Wipe, true, 0.5f)
        assertThat(half.clip).isEqualTo(CLIP_RECT)
        assertThat(listOf(half.clipLeft, half.clipTop, half.clipRight, half.clipBottom)).containsExactly(W / 2, 0f, W, H).inOrder()
        val rtl = at(SceneTransitionKind.Wipe, true, 0.25f, rtl = true)
        assertThat(listOf(rtl.clipLeft, rtl.clipRight)).containsExactly(0f, W / 4).inOrder()
        assertThat(at(SceneTransitionKind.Wipe, false, 0.5f).clip).isEqualTo(CLIP_NONE)
    }

    @Test
    fun `아이리스는 중심에서 가장 먼 모서리까지 퍼진다`() {
        val spec = SceneTransitionSpec(SceneTransitionKind.Iris, originX = 0.0, originY = 0.0)
        layer.compute(spec, true, 1f, Size(W, H), false)
        assertThat(layer.clip).isEqualTo(CLIP_CIRCLE)
        assertThat(layer.clipRight).isWithin(EPS).of(kotlin.math.hypot(W, H))
        layer.compute(spec, true, 0f, Size(W, H), false)
        assertThat(layer.clipRight).isEqualTo(0f)
    }

    @Test
    fun `페이드와 줌은 진행도에 따라 투명도가 바뀌고 끝에서는 들어오는 장면만 보인다`() {
        listOf(
            SceneTransitionKind.Fade,
            SceneTransitionKind.Zoom,
            SceneTransitionKind.ZoomBlur,
            SceneTransitionKind.Rotate,
        ).forEach { kind ->
            assertThat(at(kind, true, 0f).alpha).isEqualTo(0f)
            assertThat(at(kind, true, 1f).alpha).isEqualTo(1f)
            assertThat(at(kind, true, 1f).scale).isEqualTo(1f)
            assertThat(at(kind, false, 1f).alpha).isEqualTo(0f)
            assertThat(at(kind, false, 0f).alpha).isEqualTo(1f)
        }
        assertThat(at(SceneTransitionKind.Zoom, true, 0.5f).scale).isWithin(EPS).of(0.925f)
        assertThat(at(SceneTransitionKind.ZoomBlur, true, 0.5f).blurPx).isGreaterThan(0f)
        assertThat(at(SceneTransitionKind.ZoomBlur, true, 1f).blurPx).isEqualTo(0f)
    }

    @Test
    fun `뒤집기는 절반에서 면이 바뀌고 큐브는 맞닿은 모서리를 축으로 돈다`() {
        assertThat(at(SceneTransitionKind.Flip, false, 0.4f).alpha).isEqualTo(1f)
        assertThat(at(SceneTransitionKind.Flip, true, 0.4f).alpha).isEqualTo(0f)
        assertThat(at(SceneTransitionKind.Flip, true, 0.6f).alpha).isEqualTo(1f)
        assertThat(at(SceneTransitionKind.Flip, true, 1f).rotationY).isWithin(EPS).of(0f)
        val cubeIn = at(SceneTransitionKind.Cube, true, 0.5f)
        assertThat(cubeIn.pivotX).isEqualTo(0f)
        assertThat(cubeIn.rotationY).isEqualTo(45f)
        val cubeOut = at(SceneTransitionKind.Cube, false, 0.5f)
        assertThat(cubeOut.pivotX).isEqualTo(1f)
        assertThat(cubeOut.rotationY).isEqualTo(-45f)
        assertThat(at(SceneTransitionKind.Cube, true, 0.5f, SceneTransitionEdge.Bottom).rotationX).isEqualTo(-45f)
    }

    @Test
    fun `덮기와 드러내기는 움직이는 쪽과 위에 그리는 쪽이 반대다`() {
        assertThat(at(SceneTransitionKind.Cover, false, 0.5f).translationX).isEqualTo(0f)
        assertThat(at(SceneTransitionKind.Uncover, false, 0.5f).translationX).isEqualTo(-W / 2)
        assertThat(SceneTransitionSpec(SceneTransitionKind.Cover).incomingOnTop(forward = true)).isTrue()
        assertThat(SceneTransitionSpec(SceneTransitionKind.Uncover).incomingOnTop(forward = true)).isFalse()
        assertThat(SceneTransitionSpec(SceneTransitionKind.Push).incomingOnTop(forward = false)).isFalse()
    }

    @Test
    fun `디졸브는 들어오는 장면에만 마스크를 씌운다`() {
        assertThat(at(SceneTransitionKind.Dissolve, true, 0.3f).dissolve).isEqualTo(0.3f)
        assertThat(at(SceneTransitionKind.Dissolve, false, 0.3f).dissolve).isLessThan(0f)
    }

    @Test
    fun `None은 절반에서 바로 바뀐다`() {
        assertThat(at(SceneTransitionKind.None, true, 0.49f).alpha).isEqualTo(0f)
        assertThat(at(SceneTransitionKind.None, false, 0.49f).alpha).isEqualTo(1f)
        assertThat(at(SceneTransitionKind.None, true, 0.5f).alpha).isEqualTo(1f)
    }

    private companion object {
        const val W = 200f
        const val H = 100f
        const val EPS = 1e-4f
    }
}
