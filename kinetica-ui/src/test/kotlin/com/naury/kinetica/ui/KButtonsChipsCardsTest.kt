package com.naury.kinetica.ui

import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class KButtonsChipsCardsTest {
    @get:Rule
    val compose = createComposeRule()

    private val plus: ImageVector = ImageVector.Builder("plus", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            PathBuilder().apply {
                moveTo(4f, 11f)
                lineTo(20f, 11f)
                lineTo(20f, 13f)
                lineTo(4f, 13f)
                close()
            }.nodes,
            fill = SolidColor(Color.Black),
        )
        .build()

    private fun role(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    @Test
    fun `성공 상태 버튼은 누를 수 없고 완료를 알리며 폭은 그대로다`() {
        var clicks by mutableIntStateOf(0)
        var success by mutableStateOf(false)
        compose.setContent {
            KineticaTheme(surface = SurfaceStyles.CleanLight) {
                Column {
                    KButton("결제하기", { clicks++ }, Modifier.testTag("pay"), trailingIcon = plus, success = success)
                    Box(Modifier.width(300.dp)) { KButton("구매", {}, Modifier.testTag("wide"), fullWidth = true) }
                }
            }
        }
        val width = compose.onNodeWithTag("pay").getUnclippedBoundsInRoot().width
        compose.onNodeWithTag("pay").performClick()
        assertThat(clicks).isEqualTo(1)
        compose.onNodeWithTag("wide").assertWidthIsEqualTo(300.dp)

        success = true
        compose.waitForIdle()
        compose.onNodeWithTag("pay")
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Done"))
        compose.onNodeWithTag("pay").assertWidthIsEqualTo(width)
        compose.onNodeWithTag("pay").performClick()
        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `아이콘 토글 버튼은 체크박스로 켜고 끈다`() {
        var on by mutableStateOf(false)
        compose.setContent { KIconToggleButton(on, { on = it }, plus, "저장", Modifier.testTag("t"), size = KIconButtonSizes.Large) }
        compose.onNodeWithTag("t").assertIsOff().assert(role(Role.Checkbox)).assertWidthIsEqualTo(56.dp).performClick()
        assertThat(on).isTrue()
        compose.onNodeWithContentDescription("저장").assertIsOn()
    }

    @Test
    fun `확장 버튼은 접히면 아이콘 크기로 줄고 이름은 계속 읽힌다`() {
        var expanded by mutableStateOf(true)
        compose.setContent { KExtendedFab("새 메일", plus, {}, Modifier.testTag("fab"), expanded = expanded) }
        val full = compose.onNodeWithTag("fab").getUnclippedBoundsInRoot().width
        assertThat(full).isGreaterThan(100.dp)
        expanded = false
        compose.waitForIdle()
        compose.onNodeWithTag("fab").assertWidthIsEqualTo(56.dp)
        compose.onNodeWithText("새 메일", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `목록을 내리면 확장 버튼이 접히고 올리면 펼쳐진다`() {
        var expanded = true
        compose.setContent {
            val list = rememberLazyListState()
            expanded = rememberFabExpanded(list)
            LazyColumn(Modifier.size(300.dp, 400.dp).testTag("list"), state = list) {
                items(100) { Box(Modifier.fillMaxWidth().height(60.dp)) }
            }
        }
        compose.onNodeWithTag("list").performScrollToIndex(20)
        compose.waitForIdle()
        assertThat(expanded).isFalse()
        compose.onNodeWithTag("list").performScrollToIndex(5)
        compose.waitForIdle()
        assertThat(expanded).isTrue()
    }

    @Test
    fun `분할 버튼은 두 동작을 따로 받고 꺾쇠는 펼침 상태를 알린다`() {
        var main by mutableIntStateOf(0)
        var menu by mutableStateOf(false)
        compose.setContent { KSplitButton("보내기", { main++ }, { menu = !menu }, menuExpanded = menu) }
        compose.onNodeWithText("보내기").performClick()
        assertThat(main).isEqualTo(1)
        compose.onNodeWithContentDescription("More options")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
            .performClick()
        assertThat(menu).isTrue()
        compose.onNodeWithContentDescription("More options")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        assertThat(main).isEqualTo(1)
    }

    @Test
    fun `버튼 묶음은 누른 칸이 넓어져도 전체 폭이 그대로고 RTL에서는 첫 칸이 오른쪽에 놓인다`() {
        var clicked = -1
        var rtl by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                KButtonGroup(listOf(KGroupItem("하나"), KGroupItem("둘"), KGroupItem("셋")), { clicked = it }, Modifier.testTag("g"))
            }
        }
        compose.mainClock.advanceTimeBy(100)
        val total = compose.onNodeWithTag("g").getUnclippedBoundsInRoot().width
        val before = compose.onNodeWithText("둘").getUnclippedBoundsInRoot().width
        compose.onNodeWithText("둘").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(500)
        assertThat(compose.onNodeWithText("둘").getUnclippedBoundsInRoot().width).isGreaterThan(before)
        assertThat(compose.onNodeWithTag("g").getUnclippedBoundsInRoot().width).isEqualTo(total)
        compose.onNodeWithText("둘").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(500)
        assertThat(clicked).isEqualTo(1)

        rtl = true
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)
        val first = compose.onNodeWithText("하나").getUnclippedBoundsInRoot().left
        val last = compose.onNodeWithText("셋").getUnclippedBoundsInRoot().left
        assertThat(first).isGreaterThan(last)
    }

    @Test
    fun `고르는 묶음은 하나만 고르거나 여러 칸을 켜고 끈다`() {
        var single by mutableStateOf(setOf(0))
        var multi by mutableStateOf(setOf(0))
        compose.setContent {
            Column {
                KToggleGroup(listOf(KGroupItem("일"), KGroupItem("주")), single, { single = it })
                KToggleGroup(
                    listOf(KGroupItem("굵게"), KGroupItem(icon = plus, contentDescription = "기울임")),
                    multi,
                    { multi = it },
                    selection = KGroupSelection.Multiple,
                    style = KGroupStyle.Separated,
                )
            }
        }
        compose.onNodeWithText("주").assert(role(Role.RadioButton)).performClick()
        assertThat(single).containsExactly(1)
        compose.onNodeWithText("주").assertIsSelected()
        compose.onNodeWithContentDescription("기울임").assert(role(Role.Checkbox)).performClick()
        assertThat(multi).containsExactly(0, 1)
        compose.onNodeWithText("굵게").performClick()
        assertThat(multi).containsExactly(1)
        assertThat(nextGroupSelection(KGroupSelection.Single, setOf(5), 0, 2)).containsExactly(0)
    }

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test
    fun `칩 종류마다 동작하고 입력 칩은 지우기 버튼과 사용자 지정 동작이 있다`() {
        var filter by mutableStateOf(false)
        var removed = 0
        var assisted = 0
        compose.setContent {
            Column {
                KFilterChip("무료 배송", filter, { filter = it }, Modifier.testTag("filter"))
                KInputChip("민지", { removed++ }, Modifier.testTag("input"))
                KAssistChip("길 찾기", { assisted++ }, leadingIcon = plus)
                KSuggestionChip("좋아요", {})
            }
        }
        compose.onNodeWithTag("filter").assertIsOff().assert(role(Role.Checkbox)).performClick()
        assertThat(filter).isTrue()
        compose.onNodeWithContentDescription("Remove 민지").performClick()
        assertThat(removed).isEqualTo(1)
        compose.onNodeWithTag("input").performCustomAccessibilityActionWithLabel("Remove 민지")
        assertThat(removed).isEqualTo(2)
        compose.onNodeWithText("길 찾기").performClick()
        assertThat(assisted).isEqualTo(1)
    }

    @Test
    fun `칩 묶음은 wrap을 끄면 한 줄로 두고 가로로 스크롤한다`() {
        val names = List(12) { "카테고리 $it" }
        compose.setContent {
            Box(Modifier.width(300.dp)) { KChipGroup(names, setOf(0), {}, Modifier.testTag("row"), wrap = false) }
        }
        val top = compose.onNodeWithText(names.first()).getUnclippedBoundsInRoot().top
        assertThat(compose.onNodeWithText(names.last()).getUnclippedBoundsInRoot().top).isEqualTo(top)
        compose.onNodeWithTag("row").assert(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy))
    }

    @Test
    fun `카드는 누를 수 있고 고르는 카드는 상태를 알린다`() {
        var opened = 0
        var picked by mutableStateOf(false)
        compose.setContent {
            Column {
                KCard(onClick = { opened++ }, variant = KVariant.Outlined) { KBadge("NEW") }
                KSelectableCard(picked, {
                    picked = it
                }, Modifier.testTag("plan"), role = Role.RadioButton) { KStatusBadge("추천", KStatus.Success) }
                KHorizontalCard("이어폰", subtitle = "₩129,000", onClick = { opened++ })
            }
        }
        compose.onNodeWithText("NEW").performClick()
        compose.onNodeWithText("이어폰").performClick()
        assertThat(opened).isEqualTo(2)
        compose.onNodeWithText("추천").assert(role(Role.RadioButton)).performClick()
        assertThat(picked).isTrue()
        compose.onNodeWithText("추천").assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `미디어 카드 비율이 0 이하면 거부한다`() {
        compose.setContent { KMediaCard({}, "제목", aspectRatio = 0f) }
    }

    @Test
    fun `숫자 배지는 개수를 읽고 숨으면 자리를 비운다`() {
        var count by mutableStateOf<Int?>(3)
        compose.setContent { Row { KBadge(count, Modifier.testTag("badge")) } }
        compose.onNodeWithTag("badge").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("3 new items")))
        count = 0
        compose.waitForIdle()
        compose.onNodeWithTag("badge").assertWidthIsEqualTo(0.dp)
    }

    @Test
    fun `아바타는 이름과 상태를 함께 읽는다`() {
        compose.setContent { KAvatar(KAvatarData("민지"), Modifier.testTag("a"), status = KAvatarStatus.Online, size = KAvatarSize.Large) }
        compose.onNodeWithContentDescription("민지, Online").assertWidthIsEqualTo(56.dp).assert(role(Role.Image))
    }

    @Test
    fun `표면 위 글자 색은 대비 3 대 1을 넘도록 고친다`() {
        val green = KineticaColors().positive
        assertThat(contrast(green, Color.White)).isLessThan(3f)
        assertThat(contrast(readableOn(green, Color.White), Color.White)).isAtLeast(3f)
        assertThat(readableOn(Color.Black, Color.White)).isEqualTo(Color.Black)
        assertThat(contrast(readableOn(Color(0xFF202020), Color.Black), Color.Black)).isAtLeast(3f)
    }

    @Test
    fun `애니메이션 삭제 설정에서는 성공 모핑이 바로 끝난다`() {
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            var success by mutableStateOf(false)
            compose.mainClock.autoAdvance = false
            compose.setContent { KButton("저장", {}, Modifier.testTag("b"), success = success) }
            compose.mainClock.advanceTimeBy(100)
            success = true
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(32)
            compose.onNodeWithTag("b").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Done"))
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
    }

    @Test
    fun `버튼과 칩과 배지를 붙인 300개 목록은 애니메이션 중에 항목을 다시 구성하지 않는다`() {
        var compositions = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            KineticaTheme(surface = SurfaceStyles.CleanLight) {
                LazyColumn(Modifier.size(360.dp, 640.dp)) {
                    items(300) { i ->
                        SideEffect { compositions++ }
                        Row {
                            KButton("결제", {}, loading = i % 3 == 0)
                            KFilterChip("필터", i % 2 == 0, {})
                            KBadge(i % 5)
                            KAvatar(KAvatarData("사람 $i"), status = KAvatarStatus.Online)
                            KIconToggleButton(i % 2 == 1, {}, plus, "좋아요")
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(800)
        val settled = compositions
        assertThat(settled).isGreaterThan(0)
        compose.mainClock.advanceTimeBy(2_000)
        assertThat(compositions).isEqualTo(settled)
    }

    @Test
    fun `단색 표면에서는 배경을 기록하지 않는다`() {
        var backdrop: GlassBackdrop? = null
        var seen = false
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                backdrop = LocalGlassBackdrop.current
                seen = true
                KCard(variant = KVariant.Glass) { KButton("확인", {}, variant = KVariant.Glass) }
                KFab(plus, "추가", {}, variant = KVariant.Glass)
            }
        }
        compose.waitForIdle()
        assertThat(seen).isTrue()
        assertThat(backdrop).isNull()
    }
}
