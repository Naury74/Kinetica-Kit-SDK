package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-mdpi")
class KOverlaysTest {
    @get:Rule
    val compose = createComposeRule()

    private val area = IntRect(10, 10, 390, 790)

    @Test
    fun `앵커 팝업은 아래에 공간이 없으면 위로 뒤집고 화면 밖으로 나가지 않는다`() {
        val panel = IntSize(200, 300)
        val top = placeAnchored(Rect(20f, 100f, 120f, 140f), area, panel, 6f, alignRight = false)
        assertThat(top.below).isTrue()
        assertThat(top.y).isEqualTo(146)
        assertThat(top.x).isEqualTo(20)

        // 키보드가 올라와 아래 영역이 줄면 위로 뒤집힌다.
        val nearBottom = placeAnchored(Rect(300f, 600f, 380f, 640f), IntRect(10, 10, 390, 700), panel, 6f, alignRight = false)
        assertThat(nearBottom.below).isFalse()
        assertThat(nearBottom.y + panel.height).isEqualTo(594)
        // 끝 가장자리에 놓인 앵커에서도 판이 화면 안으로 밀려 들어온다.
        assertThat(nearBottom.x).isEqualTo(390 - 200)

        val rtl = placeAnchored(Rect(250f, 100f, 380f, 140f), area, panel, 6f, alignRight = true)
        assertThat(rtl.x + panel.width).isEqualTo(380)
        // 위아래 모두 모자라면 넓은 쪽에 놓고 그 높이로 줄인다.
        assertThat(anchoredRoom(Rect(0f, 300f, 100f, 340f), area, 6f)).isEqualTo(790 - 346)
    }

    @Test
    fun `드롭다운 메뉴는 동작을 누르면 닫고 체크는 열어 두며 하위 메뉴로 넘어갔다 돌아온다`() {
        var open by mutableStateOf(false)
        var ran = 0
        var checked by mutableStateOf(false)
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                Box(Modifier.padding(40.dp)) {
                    KButton("메뉴", { open = true })
                    KDropdownMenu(
                        open,
                        { open = false },
                        listOf(
                            KMenuEntry.Section("보기"),
                            KMenuEntry.Check("숨긴 파일", checked) { checked = it },
                            KMenuEntry.Divider,
                            KMenuEntry.Submenu("정렬", listOf(KMenuEntry.Action("이름순") { ran++ }, KMenuEntry.Action("날짜순") {})),
                            KMenuEntry.Action("삭제", destructive = true, trailing = "Del") { ran += 10 },
                        ),
                    )
                }
            }
        }
        compose.onNodeWithText("메뉴").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("숨긴 파일").assertIsDisplayed()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off)).performClick()
        compose.waitForIdle()
        assertThat(checked).isTrue()
        assertThat(open).isTrue()

        compose.onNodeWithText("정렬").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("이름순").assertIsDisplayed()
        compose.onNodeWithText("숨긴 파일").assertDoesNotExist()
        // 맨 위 뒤로 줄을 누르면 처음 판으로 돌아온다.
        compose.onNodeWithText("정렬").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("숨긴 파일").assertIsDisplayed()

        compose.onNodeWithText("삭제").performClick()
        compose.mainClock.advanceTimeBy(500)
        assertThat(ran).isEqualTo(10)
        assertThat(open).isFalse()
        compose.onNodeWithText("삭제").assertDoesNotExist()
    }

    @Test
    fun `드롭다운 메뉴는 방향키로 하위 메뉴에 들어가고 Esc로 나온 뒤 닫힌다`() {
        var open by mutableStateOf(false)
        compose.setContent {
            val input = LocalInputModeManager.current
            remember { input.requestInputMode(InputMode.Keyboard) }
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                Box(Modifier.padding(40.dp)) {
                    BasicText("앵커")
                    KDropdownMenu(open, { open = false }, listOf(KMenuEntry.Submenu("정렬", listOf(KMenuEntry.Action("이름순") {}))))
                }
            }
        }
        compose.waitForIdle()
        open = true
        compose.mainClock.advanceTimeBy(500)
        // 키보드로 열면 첫 항목에 초점이 간다.
        val row = compose.onNodeWithText("정렬")
        row.assertIsFocused()
        row.performKeyInput { pressKey(Key.DirectionRight) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("이름순").assertIsDisplayed()
        compose.onNodeWithText("이름순").performKeyInput { pressKey(Key.Escape) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("이름순").assertDoesNotExist()
        compose.onNodeWithText("정렬").assertIsFocused()
        compose.onNodeWithText("정렬").performKeyInput { pressKey(Key.Escape) }
        compose.waitForIdle()
        assertThat(open).isFalse()
    }

    @Test
    fun `스캐폴드 밖 드롭다운 메뉴는 팝업 창에 뜨고 항목이 눌린다`() {
        var open by mutableStateOf(true)
        var picked = ""
        compose.setContent {
            Column(Modifier.padding(20.dp)) {
                BasicText("위")
                Box {
                    BasicText("앵커")
                    KDropdownMenu(open, { open = false }, listOf(KMenuEntry.Action("복사") { picked = "복사" }))
                }
            }
        }
        compose.onNodeWithText("복사").performClick()
        compose.waitForIdle()
        assertThat(picked).isEqualTo("복사")
        assertThat(open).isFalse()
    }

    @Test
    fun `선택 상자는 고른 값을 상태로 읽히고 항목을 고르면 닫힌다`() {
        var city by mutableStateOf(-1)
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                KSelect(listOf("서울", "부산", "제주"), city, { city = it }, "지역", Modifier.padding(20.dp).fillMaxWidth().testTag("select"))
            }
        }
        val box = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
        box.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not selected"))
        box.performClick()
        compose.waitForIdle()
        compose.onNodeWithText("부산").performClick()
        compose.mainClock.advanceTimeBy(500)
        assertThat(city).isEqualTo(1)
        compose.onNodeWithText("제주").assertDoesNotExist()
        box.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "부산"))
    }

    @Test
    fun `자동 완성은 초점이 있을 때 제안을 띄우고 방향키와 Enter로 고른다`() {
        var query by mutableStateOf("")
        val cities = listOf("서울", "서귀포", "부산")
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                KAutocomplete(
                    query,
                    {
                        query = it
                    },
                    cities.filter {
                        query.isNotEmpty() && it.contains(query)
                    },
                    { query = it },
                    "도시",
                    Modifier.padding(20.dp).fillMaxWidth().testTag("auto"),
                )
            }
        }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetText)).performTextInput("서")
        compose.waitForIdle()
        compose.onNodeWithText("서귀포").assertIsDisplayed()
        val field = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetText))
        field.performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
            pressKey(Key.Enter)
        }
        compose.mainClock.advanceTimeBy(500)
        assertThat(query).isEqualTo("서귀포")
        compose.onNodeWithText("서울").assertDoesNotExist()
    }

    @Test
    fun `알림 다이얼로그는 짧은 두 버튼을 나란히 놓고 셋이면 취소를 맨 아래로 쌓는다`() {
        var three by mutableStateOf(false)
        var dismissed = 0
        var deleted = 0
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                val actions = buildList {
                    add(KDialogAction("취소", KDialogActionStyle.Cancel) {})
                    add(KDialogAction("삭제", KDialogActionStyle.Destructive) { deleted++ })
                    if (three) add(KDialogAction("보관", KDialogActionStyle.Default) {})
                }
                KAlertDialog(true, { dismissed++ }, "사진을 삭제할까요?", actions, message = "30일 뒤 지워집니다.")
            }
        }
        compose.mainClock.advanceTimeBy(800)
        val cancel = compose.onNodeWithText("취소").fetchSemanticsNode().boundsInRoot
        val delete = compose.onNodeWithText("삭제").fetchSemanticsNode().boundsInRoot
        assertThat(cancel.center.y).isWithin(1f).of(delete.center.y)
        assertThat(cancel.left).isLessThan(delete.left)

        three = true
        compose.mainClock.advanceTimeBy(500)
        val cancel3 = compose.onNodeWithText("취소").fetchSemanticsNode().boundsInRoot
        val keep = compose.onNodeWithText("보관").fetchSemanticsNode().boundsInRoot
        assertThat(cancel3.top).isGreaterThan(keep.bottom)

        compose.onNodeWithText("삭제").performClick()
        compose.waitForIdle()
        assertThat(deleted).isEqualTo(1)
        assertThat(dismissed).isEqualTo(1)
    }

    @Test
    fun `동작 시트는 좁은 화면에서 취소 판을 두고 아래로 끌면 닫힌다`() {
        var open by mutableStateOf(true)
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                KActionSheet(open, { open = false }, listOf(KDialogAction("링크 복사") {}), title = "공유")
            }
        }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithText("링크 복사").performTouchInput {
            down(center)
            moveBy(Offset(0f, 150f))
            moveBy(Offset(0f, 150f))
            up()
        }
        compose.mainClock.advanceTimeBy(800)
        assertThat(open).isFalse()
    }

    @Test
    @Config(qualifiers = "w900dp-h700dp-mdpi")
    fun `넓은 화면에서 동작 시트는 팝오버로 바뀌고 취소 버튼을 그리지 않는다`() {
        var picked = 0
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                Box(Modifier.padding(40.dp)) {
                    BasicText("공유 버튼")
                    KActionSheet(true, {}, listOf(KDialogAction("링크 복사") { picked++ }))
                }
            }
        }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        // 팝오버가 열린 동안 뒤 화면은 TalkBack에서 숨으므로 합치지 않은 트리에서 찾는다.
        val anchor = compose.onNodeWithText("공유 버튼", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val item = compose.onNodeWithText("링크 복사").fetchSemanticsNode().boundsInRoot
        assertThat(item.top).isGreaterThan(anchor.bottom)
        compose.onNodeWithText("링크 복사").performClick()
        assertThat(picked).isEqualTo(1)
    }

    @Test
    fun `상단 앱 바는 맨 위에서 배경이 없다가 스크롤하면 나타나고 Medium 제목이 접힌다`() {
        lateinit var small: KTopAppBarState
        lateinit var medium: KTopAppBarState
        var compositions = 0
        compose.setContent {
            val list = rememberLazyListState()
            small = rememberKTopAppBarState(list)
            medium = rememberKTopAppBarState(list)
            Column {
                KTopAppBar("설정", state = small, actions = { SideEffect { compositions++ } })
                KTopAppBar("앨범", variant = KTopAppBarVariant.Medium, state = medium)
                LazyColumn(Modifier.fillMaxWidth().height(400.dp).testTag("list"), state = list) {
                    items(300) { BasicText("항목 $it", Modifier.height(48.dp)) }
                }
            }
        }
        compose.waitForIdle()
        assertThat(small.overlappedFraction).isEqualTo(0f)
        assertThat(medium.collapsedFraction).isEqualTo(0f)
        compose.onNodeWithTag("list").performScrollToIndex(5)
        compose.waitForIdle()
        assertThat(small.overlappedFraction).isEqualTo(1f)
        assertThat(medium.collapsedFraction).isEqualTo(1f)
        // 배경이 다 나타난 뒤에는 스크롤해도 바를 다시 구성하지 않는다.
        val settled = compositions
        compose.onNodeWithTag("list").performScrollToIndex(40)
        compose.onNodeWithTag("list").performScrollToIndex(120)
        compose.waitForIdle()
        assertThat(compositions).isEqualTo(settled)
    }

    @Test
    fun `떠 있는 툴바 상태는 아래로 스크롤하면 접히고 위로 스크롤하면 펼쳐진다`() {
        val state = KFloatingToolbarState(true)
        state.connection.onPostScroll(Offset(0f, -30f), Offset.Zero, NestedScrollSource.UserInput)
        assertThat(state.expanded).isTrue()
        state.connection.onPostScroll(Offset(0f, -30f), Offset.Zero, NestedScrollSource.UserInput)
        assertThat(state.expanded).isFalse()
        state.connection.onPostScroll(Offset(0f, 60f), Offset.Zero, NestedScrollSource.UserInput)
        assertThat(state.expanded).isTrue()
    }

    @Test
    fun `여러 칸 세그먼트는 칸마다 켜고 끄며 마지막 칸을 지킬 수 있다`() {
        var days by mutableStateOf(setOf(0))
        compose.setContent {
            KSegmentedControl(listOf(KSegment("월"), KSegment("화"), KSegment("수")), days, { days = it }, requireSelection = true)
        }
        compose.onNodeWithText("월").performClick()
        assertThat(days).containsExactly(0)
        compose.onNodeWithText("수").performClick()
        compose.onNodeWithText("월").performClick()
        compose.waitForIdle()
        assertThat(days).containsExactly(2)
        compose.onNodeWithText("수").assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
    }

    @Test
    fun `아이콘 세그먼트는 아이콘 이름을 읽고 탭 배지는 상태로 알린다`() {
        var picked by mutableStateOf(0)
        compose.setContent {
            Column {
                KSegmentedControl(
                    listOf(
                        KSegment(icon = KGlyphs.Search, contentDescription = "검색"),
                        KSegment(icon = KGlyphs.Clock, contentDescription = "최근"),
                    ),
                    picked,
                    { picked = it },
                )
                KTabBar(listOf(KTab("홈", KGlyphs.Search), KTab("메일", KGlyphs.Clock, badge = 3), KTab("알림", dot = true)), 0, {})
            }
        }
        compose.onNodeWithContentDescription("최근").performClick()
        assertThat(picked).isEqualTo(1)
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3 new items"), useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun `모달 서랍은 뒤 화면을 밀지 않고 덮는다`() {
        val state = KDrawerState(true)
        compose.setContent {
            KDrawer({ BasicText("서랍 메뉴") }, state = state, mode = KDrawerMode.Modal, surface = SurfaceStyles.CleanLight) {
                Box(Modifier.fillMaxSize().testTag("content"))
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("서랍 메뉴").assertIsDisplayed()
        val content = compose.onNodeWithTag("content", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertThat(content.left).isEqualTo(root.left)
        assertThat(content.width).isEqualTo(root.width)
    }

    @Test
    fun `리치 툴팁은 동작 버튼을 받고 바깥을 누르면 닫힌다`() {
        var visible by mutableStateOf(true)
        var acted = 0
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(40.dp).kRichTooltip(visible, {
                            visible = false
                        }, "필터를 바꿀 수 있어요", title = "새 필터", actionLabel = "알겠어요", onAction = { acted++ }),
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("새 필터").assertIsDisplayed()
        compose.onNodeWithText("알겠어요").performClick()
        compose.waitForIdle()
        assertThat(acted).isEqualTo(1)
        assertThat(visible).isFalse()

        visible = true
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().performTouchInput { click(Offset(10f, 10f)) }
        compose.waitForIdle()
        assertThat(visible).isFalse()
        assertThat(acted).isEqualTo(1)
    }

    @Test
    fun `검색 화면은 비어 있으면 최근 검색을, 글자가 있으면 제안을 보이고 누르면 검색한다`() {
        var expanded by mutableStateOf(false)
        var query by mutableStateOf("")
        val searched = mutableListOf<String>()
        val recent = mutableListOf("강아지", "바다")
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                KSearchView(
                    expanded,
                    { expanded = it },
                    query,
                    { query = it },
                    { searched += it },
                    Modifier.padding(20.dp).fillMaxWidth().testTag("bar"),
                    suggestions = listOf("바다 사진", "바다 영상").filter { it.contains(query) },
                    recent = recent,
                    onRemoveRecent = { recent.remove(it) },
                )
            }
        }
        compose.onNodeWithTag("bar").performClick()
        compose.mainClock.advanceTimeBy(800)
        assertThat(expanded).isTrue()
        compose.onNodeWithText("Recent searches").assertIsDisplayed()
        compose.onNodeWithText("강아지").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove 강아지").performClick()
        assertThat(recent).containsExactly("바다")

        query = "바다"
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("바다 영상").performClick()
        assertThat(searched).containsExactly("바다 영상")
        assertThat(query).isEqualTo("바다 영상")
        compose.onNodeWithContentDescription("Close search").performClick()
        assertThat(expanded).isFalse()
    }
}
