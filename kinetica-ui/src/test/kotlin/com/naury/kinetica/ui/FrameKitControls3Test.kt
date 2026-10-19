package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 편집 SDK 3차 요청 중 조작 컴포넌트: 칩 줄 그림자 여백 끄기, 슬라이더 위 줄 글자와 간격, 범위 밖 다이얼 기준값 맞추기. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class FrameKitControls3Test {
    @get:Rule
    val compose = createComposeRule()

    private fun setChipGroup(wrap: Boolean, minTouchTarget: Boolean, shadowRoom: Boolean) {
        compose.setContent {
            KChipGroup(
                listOf("전체", "사진"),
                setOf(0),
                {},
                Modifier.testTag("group"),
                wrap = wrap,
                minTouchTarget = minTouchTarget,
                shadowRoom = shadowRoom,
            )
        }
    }

    @Test
    fun `한 줄 칩 묶음은 기본으로 위아래 그림자 여백을 둔다`() {
        setChipGroup(wrap = false, minTouchTarget = true, shadowRoom = true)
        compose.onNodeWithTag("group").assertHeightIsEqualTo(60.dp)
    }

    @Test
    fun `그림자 여백을 끄면 minTouchTarget 한 줄 칩 묶음 높이가 48dp다`() {
        setChipGroup(wrap = false, minTouchTarget = true, shadowRoom = false)
        compose.onNodeWithTag("group").assertHeightIsEqualTo(48.dp)
    }

    @Test
    fun `줄을 넘기는 칩 묶음은 그림자 여백이 없어 옵션을 켜 두어도 높이가 칩 높이다`() {
        setChipGroup(wrap = true, minTouchTarget = true, shadowRoom = true)
        compose.onNodeWithTag("group").assertHeightIsEqualTo(48.dp)
    }

    @Test
    fun `KChipRow도 그림자 여백을 끄면 내용 높이와 같다`() {
        compose.setContent {
            KChipRow(Modifier.testTag("row"), shadowRoom = false) { Box(Modifier.size(80.dp, 30.dp)) }
        }
        compose.onNodeWithTag("row").assertHeightIsEqualTo(30.dp)
    }

    private class Header(val name: String, val style: TextStyle?, val spacing: Dp?)

    /**
     * Top 줄을 둔 슬라이더를 함께 그리고, 이름마다 트랙 노드 위쪽이 제 바깥 상자 위쪽에서 얼마나 내려와 있는지 돌려준다.
     * 위 줄 높이와 간격의 합이다. [Header.spacing]이 null이면 간격을 넘기지 않는다.
     */
    private fun headerOffsets(vararg headers: Header): List<Dp> {
        compose.setContent {
            Column {
                headers.forEach { h ->
                    Box(Modifier.width(300.dp).testTag(h.name)) {
                        if (h.spacing == null) {
                            KSlider(0.5f, {}, label = h.name, badgePosition = KSliderBadgePosition.Top, labelTextStyle = h.style)
                        } else {
                            KSlider(
                                0.5f,
                                {},
                                label = h.name,
                                badgePosition = KSliderBadgePosition.Top,
                                labelTextStyle = h.style,
                                labelSpacing = h.spacing,
                            )
                        }
                    }
                }
            }
        }
        return headers.map { h ->
            val track = compose.onNodeWithContentDescription(h.name).getUnclippedBoundsInRoot()
            val box = compose.onNodeWithTag(h.name).getUnclippedBoundsInRoot()
            track.top - box.top
        }
    }

    @Test
    fun `Top 줄 간격 기본값은 4dp이고 0이면 트랙이 그만큼 올라온다`() {
        val (default, explicit, none) = headerOffsets(Header("a", null, null), Header("b", null, 4.dp), Header("c", null, 0.dp))
        assertThat(explicit.value).isWithin(0.5f).of(default.value)
        assertThat((default - none).value).isWithin(0.5f).of(4f)
    }

    @Test
    fun `Top 줄 이름 스타일을 주면 기본 스타일 위에 덮어쓴다`() {
        val (base, small, large) = headerOffsets(
            Header("a", null, 0.dp),
            Header("b", TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium), 0.dp),
            Header("c", TextStyle(fontSize = 32.sp), 0.dp),
        )
        assertThat(small.value).isLessThan(base.value)
        assertThat(large.value).isGreaterThan(base.value)
    }

    private var dialValue = 0f

    private fun setDial(coerce: Boolean?) {
        dialValue = 20f
        compose.setContent {
            var current by remember { mutableFloatStateOf(20f) }
            val onChange: (Float) -> Unit = {
                current = it
                dialValue = it
            }
            if (coerce == null) {
                KDialSlider(current, onChange, "노출", resetValue = 150f)
            } else {
                KDialSlider(current, onChange, "노출", resetValue = 150f, coerceResetValue = coerce)
            }
        }
    }

    @Test
    fun `범위 밖 기준값은 기본으로 다이얼을 막는다`() {
        setDial(coerce = null)
        compose.onNodeWithContentDescription("노출").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
    }

    @Test
    fun `coerceResetValue가 false면 범위 밖 기준값으로 다이얼을 막는다`() {
        setDial(coerce = false)
        compose.onNodeWithContentDescription("노출").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
    }

    @Test
    fun `coerceResetValue면 범위 밖 기준값을 끝값으로 맞춰 조작하고 두 번 탭하면 그 끝값으로 간다`() {
        setDial(coerce = true)
        val dial = compose.onNodeWithContentDescription("노출")
        dial.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Disabled))
        dial.performTouchInput { doubleClick(Offset(centerX, height * 0.75f)) }
        compose.waitForIdle()
        assertThat(dialValue).isEqualTo(100f)
    }

    @Test
    fun `coerceResetValue여도 NaN 기준값은 막는다`() {
        compose.setContent { KDialSlider(0f, {}, "노출", resetValue = Float.NaN, coerceResetValue = true) }
        compose.onNodeWithContentDescription("노출").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
    }
}
