package com.naury.kinetica.ui

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 체크박스, 라디오, 스위치 변형, 목록 행과 섹션, 입력란 변형을 확인한다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class KFormsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun role(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun close(a: Color, b: Color): Boolean =
        abs(a.red - b.red) < 0.05f && abs(a.green - b.green) < 0.05f && abs(a.blue - b.blue) < 0.05f

    @Test
    fun `체크박스는 눌러 켜고 끄며 체크박스 역할로 읽힌다`() {
        var checked by mutableStateOf(false)
        compose.setContent { KCheckbox(checked, { checked = it }, Modifier.testTag("box")) }
        compose.onNodeWithTag("box").assert(role(Role.Checkbox)).assertIsOff().performClick()
        assertThat(checked).isTrue()
        compose.onNodeWithTag("box").assertIsOn()
    }

    @Test
    fun `세 상태 체크박스는 미정 상태를 알리고 콜백 없이 쓰면 누를 수 없다`() {
        compose.setContent {
            Column {
                KTriStateCheckbox(ToggleableState.Indeterminate, {}, Modifier.testTag("tri"))
                KCheckbox(true, null, Modifier.testTag("static"))
            }
        }
        compose.onNodeWithTag(
            "tri",
        ).assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Indeterminate))
        compose.onNodeWithTag("static").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }

    @Test
    fun `애니메이션 삭제 설정이면 체크박스가 첫 프레임에 다 채워진다`() {
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            var checked by mutableStateOf(false)
            compose.mainClock.autoAdvance = false
            compose.setContent {
                CompositionLocalProvider(LocalKineticaColors provides KineticaColors(accent = Color.Red)) {
                    Box(Modifier.background(Color.White)) { KCheckbox(checked, { checked = it }, Modifier.testTag("box")) }
                }
            }
            compose.mainClock.advanceTimeBy(32)
            checked = true
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(32)
            val map = compose.onNodeWithTag("box").captureToImage().toPixelMap()
            // 체크 획에서 조금 비켜 난 상자 안쪽 점이 강조색으로 차 있어야 한다.
            assertThat(close(map[map.width * 3 / 8, map.height * 3 / 8], Color.Red)).isTrue()
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
    }

    @Test
    fun `라디오 묶음은 누른 항목을 고르고 선택 상태를 알린다`() {
        var selected by mutableStateOf(0)
        compose.setContent { KRadioGroup(listOf("작게", "보통", "크게"), selected, { selected = it }) }
        compose.onNodeWithText("크게").performClick()
        assertThat(selected).isEqualTo(2)
        compose.onNodeWithText("크게").assertIsSelected().assert(role(Role.RadioButton))
    }

    @Test
    fun `스위치 행은 행 전체가 스위치 하나로 읽히고 제목을 눌러도 바뀐다`() {
        var on by mutableStateOf(false)
        compose.setContent { KSwitchRow("비행기 모드", on, { on = it }, Modifier.testTag("row"), subtitle = "모든 무선 연결을 끕니다") }
        compose.onNodeWithTag("row").assert(role(Role.Switch)).assertIsOff()
        // 행 안 스위치는 따로 누를 수 있는 노드가 아니다.
        compose.onAllNodesWithText("비행기 모드").fetchSemanticsNodes().let { assertThat(it).hasSize(1) }
        compose.onNodeWithText("비행기 모드", useUnmergedTree = true).performClick()
        assertThat(on).isTrue()
        compose.onNodeWithTag("row").assertIsOn()
    }

    @Test
    fun `체크박스 행과 라디오 행과 꺾쇠 행은 각자 역할을 갖는다`() {
        var clicks = 0
        compose.setContent {
            Column {
                KCheckboxRow("자동", true, {}, Modifier.testTag("check"))
                KRadioRow("밝게", true, {}, Modifier.testTag("radio"))
                KDisclosureRow("Wi-Fi", { clicks++ }, Modifier.testTag("link"), value = "Home")
                KValueRow("버전", "1.0", Modifier.testTag("value"))
            }
        }
        compose.onNodeWithTag("check").assert(role(Role.Checkbox)).assertIsOn()
        compose.onNodeWithTag("radio").assert(role(Role.RadioButton)).assertIsSelected()
        compose.onNodeWithTag("link").assert(role(Role.Button)).performClick()
        assertThat(clicks).isEqualTo(1)
        compose.onNodeWithTag("value").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }

    @Test
    fun `둥근 섹션은 첫 행 위와 끝 행 아래 모서리만 둥글고 가운데 행은 가장자리까지 찬다`() {
        compose.setContent {
            Box(Modifier.background(Color.Red).padding(8.dp)) {
                KListSection(Modifier.width(300.dp).testTag("section"), surface = SurfaceStyles.OneUi) {
                    item { KListItem("하나") }
                    item { KListItem("둘") }
                    item { KListItem("셋") }
                }
            }
        }
        val map = compose.onNodeWithTag("section").captureToImage().toPixelMap()
        val fill = Color(0xFFFCFCFC)
        assertThat(close(map[1, 1], Color.Red)).isTrue()
        assertThat(close(map[map.width - 2, map.height - 2], Color.Red)).isTrue()
        assertThat(close(map[1, map.height / 2], fill)).isTrue()
        assertThat(close(map[map.width / 2, map.height / 2 - 3], fill)).isTrue()
    }

    @Test
    fun `오른쪽에서 왼쪽 언어에서는 켜진 스위치 손잡이가 왼쪽에 간다`() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.background(Color.Black)) {
                    KSwitch(true, {}, Modifier.testTag("switch"), accent = Color.Blue, variant = KSwitchVariant.Material)
                }
            }
        }
        val map = compose.onNodeWithTag("switch").captureToImage().toPixelMap()
        val y = map.height / 2
        // 손잡이(흰색)는 시작 쪽, 즉 왼쪽에 있고 오른쪽 끝은 트랙 색이다.
        assertThat(close(map[map.width / 4, y], Color.White)).isTrue()
        assertThat(close(map[map.width * 7 / 8, y], Color.Blue)).isTrue()
    }

    @Test
    fun `스위치 변형마다 역할과 상태가 같다`() {
        compose.setContent {
            Column {
                KSwitchVariant.entries.forEach { KSwitch(true, {}, Modifier.testTag(it.name), variant = it) }
            }
        }
        KSwitchVariant.entries.forEach { compose.onNodeWithTag(it.name).assert(role(Role.Switch)).assertIsOn() }
    }

    @Test
    fun `글자 수 제한은 넘는 입력을 자르고 아래에 글자 수를 보여 준다`() {
        var text by mutableStateOf("")
        compose.setContent {
            Column {
                KTextField(text, { text = it }, "닉네임", Modifier.testTag("field"), maxLength = 5)
                KTextField(text, { text = it }, "닉네임2", variant = KTextFieldVariant.Outlined, maxLength = 5, prefix = "@")
            }
        }
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("field"))).performTextReplacement("가나다라마바사")
        assertThat(text).isEqualTo("가나다라마")
        compose.onAllNodesWithText("5/5").fetchSemanticsNodes().let { assertThat(it).hasSize(2) }
    }

    @Test
    fun `모든 입력란 변형이 같은 값을 받고 입력을 넘긴다`() {
        var text by mutableStateOf("")
        compose.setContent {
            Column {
                KTextFieldVariant.entries.forEach { KTextField(text, { text = it }, it.name, Modifier.testTag(it.name), variant = it) }
            }
        }
        KTextFieldVariant.entries.forEachIndexed { i, variant ->
            compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(variant.name))).performTextReplacement("값$i")
            assertThat(text).isEqualTo("값$i")
        }
    }

    @Test
    fun `여러 줄 입력란은 최소 줄 수만큼 높고 최대 줄 수에서 더 자라지 않는다`() {
        var text by mutableStateOf("")
        compose.setContent {
            Column(Modifier.width(320.dp)) {
                KTextField("", {}, "한 줄", Modifier.testTag("single"))
                KTextArea(text, { text = it }, "메모", Modifier.testTag("area"), minLines = 3, maxLines = 5)
            }
        }
        val single = compose.onNodeWithTag("single").getUnclippedBoundsInRoot().height
        val empty = compose.onNodeWithTag("area").getUnclippedBoundsInRoot().height
        assertThat(empty).isGreaterThan(single)
        text = (1..5).joinToString("\n") { "줄 $it" }
        compose.waitForIdle()
        val five = compose.onNodeWithTag("area").getUnclippedBoundsInRoot().height
        assertThat(five).isGreaterThan(empty)
        text = (1..12).joinToString("\n") { "줄 $it" }
        compose.waitForIdle()
        assertThat(compose.onNodeWithTag("area").getUnclippedBoundsInRoot().height).isEqualTo(five)
    }

    @Test
    fun `목록 행 300개는 가만히 있는 동안 다시 구성하지 않고 행 하나를 바꾸면 그 행만 다시 구성한다`() {
        val compositions = IntArray(300)
        val checked = mutableStateOf(setOf<Int>())
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LazyColumn(Modifier.size(360.dp, 640.dp)) {
                items(300, key = { it }) { i ->
                    SideEffect { compositions[i]++ }
                    val on = i in checked.value
                    KSwitchRow("항목 $i", on, { checked.value = if (it) checked.value + i else checked.value - i })
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        val settled = compositions.sum()
        assertThat(settled).isGreaterThan(0)
        compose.mainClock.advanceTimeBy(2_000)
        assertThat(compositions.sum()).isEqualTo(settled)

        val before = compositions.copyOf()
        compose.onNodeWithText("항목 1").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        // 상태 집합 하나를 읽으므로 보이는 행이 함께 다시 구성될 수는 있지만, 스위치 애니메이션 중에 프레임마다 다시 구성되지는 않는다.
        val changed = compositions.indices.filter { compositions[it] != before[it] }
        changed.forEach { assertThat(compositions[it] - before[it]).isAtMost(2) }
        assertThat(changed).contains(1)
    }

    @Test
    fun `섹션 구분선은 앞 아이콘이 있으면 글자 시작에서 시작한다`() {
        compose.setContent {
            Box(Modifier.background(Color.White)) {
                KListSection(Modifier.width(300.dp).testTag("section"), surface = SurfaceStyles.OneUi, style = KListSectionStyle.Plain) {
                    item { KListItem("하나", Modifier.testTag("r1"), leading = KListLeading.Avatar("A", color = Color.Gray)) }
                    item { KListItem("둘") }
                }
            }
        }
        val first = compose.onNodeWithText("하나").getUnclippedBoundsInRoot()
        val map = compose.onNodeWithTag("section").captureToImage().toPixelMap()
        val density = 2f
        val y = (compose.onNodeWithTag("r1").getUnclippedBoundsInRoot().bottom.value * density).toInt() - 1
        val before = map[(first.left.value * density).toInt() - 6, y]
        val after = map[(first.left.value * density).toInt() + 6, y]
        assertThat(close(before, Color.White)).isTrue()
        assertThat(close(after, Color.White)).isFalse()
    }

    @Test
    fun `KListItem 세 줄은 위 글자와 설명을 모두 보여 주고 행을 누르면 콜백이 온다`() {
        var opened = 0
        compose.setContent {
            KListItem(
                "주간 보고",
                Modifier.fillMaxWidth().testTag("row"),
                overline = "팀장",
                supporting = "이번 주 진행 상황",
                trailing = KListTrailing.Disclosure(),
                onClick = { opened++ },
            )
        }
        compose.onNodeWithText("팀장").assertExists()
        compose.onNodeWithText("이번 주 진행 상황").assertExists()
        compose.onNodeWithTag("row").performClick()
        assertThat(opened).isEqualTo(1)
        assertThat(compose.onNodeWithTag("row").getUnclippedBoundsInRoot().height.value).isAtLeast(84f)
    }

    @Test
    fun `지연 섹션 300행은 가만히 있는 동안 다시 구성하지 않고 머리글과 꼬리글을 둔다`() {
        val compositions = IntArray(300)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LazyColumn(Modifier.size(360.dp, 640.dp)) {
                kListSection((0 until 300).toList(), key = { it }, header = "연락처", footer = "끝", sectionKey = "contacts") { i ->
                    SideEffect { compositions[i]++ }
                    KListItem("사람 $i", leading = KListLeading.Avatar("$i"), onClick = {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        val settled = compositions.sum()
        assertThat(settled).isGreaterThan(0)
        assertThat(compositions.count { it > 0 }).isLessThan(30)
        compose.mainClock.advanceTimeBy(2_000)
        assertThat(compositions.sum()).isEqualTo(settled)
        compose.onNodeWithText("연락처").assertExists()
    }

    @Test
    fun `지연 섹션은 행 사이에 이음매 없이 표면이 이어지고 첫 행 위 모서리만 둥글다`() {
        compose.setContent {
            Box(Modifier.background(Color.Red).padding(8.dp)) {
                LazyColumn(Modifier.width(300.dp).testTag("lazy")) {
                    kListSection(listOf(1, 2, 3), surface = SurfaceStyles.CleanLight) { KListItem("행 $it", Modifier.testTag("row$it")) }
                }
            }
        }
        val map = compose.onNodeWithTag("lazy").captureToImage().toPixelMap()
        val density = 2f
        val seam = (compose.onNodeWithTag("row1").getUnclippedBoundsInRoot().bottom.value - 8f) * density
        val mid = (compose.onNodeWithTag("row2").getUnclippedBoundsInRoot().top.value - 8f) * density + 20
        // 구분선(글자 시작 16dp부터)보다 앞쪽 x에서 경계 픽셀과 행 가운데 픽셀이 같아야 가로 테두리가 없는 것이다.
        assertThat(close(map[12, seam.toInt()], map[12, mid.toInt()])).isTrue()
        assertThat(close(map[12, mid.toInt()], Color.White)).isTrue()
        assertThat(close(map[1, 1], Color.White)).isFalse()
    }
}
