package com.naury.kinetica.ui

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KTreeViewTest {
    @get:Rule
    val compose = createComposeRule()

    private data class Node(val id: String, val kids: List<Node> = emptyList())

    private val tree = listOf(
        Node("src", listOf(Node("main", listOf(Node("App.kt"), Node("Theme.kt"))), Node("README.md"))),
        Node("build.gradle"),
    )
    private val access = TreeAccess<Node>({ it.id }, { it.kids })

    @Test
    fun `펼친 노드의 자식만 깊이 우선으로 늘어놓고 수준과 순번을 매긴다`() {
        val rows = flattenTree(tree, access, setOf("src"))
        assertThat(rows.map { it.key }).containsExactly("src", "main", "README.md", "build.gradle").inOrder()
        val main = rows[1]
        assertThat(main.depth).isEqualTo(1)
        assertThat(main.position).isEqualTo(1)
        assertThat(main.siblings).isEqualTo(2)
        assertThat(main.parent).isEqualTo("src")
        assertThat(flattenTree(tree, access, setOf("src", "main")).map { it.key }).contains("Theme.kt")
    }

    @Test
    fun `순환이 있어도 끝없이 돌지 않는다`() {
        lateinit var loop: Node
        val cyclic = TreeAccess<Node>({ it.id }, { if (it.id == "a") listOf(loop) else emptyList() })
        loop = Node("a")
        assertThat(flattenTree(listOf(loop), cyclic, setOf("a")).map { it.key }).containsExactly("a")
        assertThat(allTreeKeys(listOf(loop), cyclic)).containsExactly("a")
    }

    @Test
    fun `부모 체크 상태는 잎에서 계산하고 부모를 누르면 잎을 모두 켜고 끈다`() {
        val partial = treeCheckStates(tree, access, setOf("App.kt"))
        assertThat(partial["main"]).isEqualTo(ToggleableState.Indeterminate)
        assertThat(partial["src"]).isEqualTo(ToggleableState.Indeterminate)
        assertThat(partial["build.gradle"]).isEqualTo(ToggleableState.Off)
        val all = toggledChecks(tree[0], access, setOf("App.kt"))
        assertThat(all).containsExactly("App.kt", "Theme.kt", "README.md")
        assertThat(treeCheckStates(tree, access, all)["src"]).isEqualTo(ToggleableState.On)
        assertThat(toggledChecks(tree[0], access, all)).isEmpty()
    }

    @Test
    fun `폴더를 누르면 펼쳐지고 TalkBack은 수준과 펼침 상태를 읽는다`() {
        var picked: String? = null
        compose.setContent {
            KTreeView(tree, { it.id }, { it.kids }, { it.id }, Modifier.height(400.dp), onSelect = { picked = it.id })
        }
        compose.onNodeWithText("main").assertDoesNotExist()
        compose.onNodeWithText("src").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed, Level 1, 1 of 2"),
        ).performClick()
        compose.waitForIdle()
        assertThat(picked).isEqualTo("src")
        compose.onNodeWithText("src").assertIsSelected()
        compose.onNodeWithText("main").assertIsDisplayed().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed, Level 2, 1 of 2"),
        )
        compose.onNodeWithText("main").performSemanticsAction(SemanticsActions.Expand)
        compose.waitForIdle()
        compose.onNodeWithText("App.kt").assertIsDisplayed()
        compose.onNodeWithText("main").performSemanticsAction(SemanticsActions.Collapse)
        compose.waitForIdle()
        compose.onNodeWithText("App.kt").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `방향키로 옮기고 오른쪽은 펼치고 왼쪽은 부모로 가며 Enter로 고른다`() {
        val state = KTreeState()
        var picked: String? = null
        compose.setContent {
            KTreeView(tree, {
                it.id
            }, { it.kids }, { it.id }, Modifier.height(400.dp).testTag("tree"), state, onSelect = { picked = it.id })
        }
        val list = compose.onNodeWithTag("tree")
        list.requestFocus()
        list.performKeyInput { pressKey(Key.DirectionDown) }
        assertThat(state.active).isEqualTo("src")
        list.performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForIdle()
        assertThat(state.isExpanded("src")).isTrue()
        list.performKeyInput { pressKey(Key.DirectionRight) }
        assertThat(state.active).isEqualTo("main")
        list.performKeyInput { pressKey(Key.DirectionLeft) }
        assertThat(state.active).isEqualTo("src")
        list.performKeyInput { pressKey(Key.DirectionDown) }
        list.performKeyInput { pressKey(Key.Enter) }
        assertThat(picked).isEqualTo("main")
        assertThat(state.selected).isEqualTo("main")
    }

    @Test
    fun `체크박스를 켜면 부모는 일부 선택으로 보인다`() {
        val state = KTreeState(initialExpanded = setOf("src", "main"))
        compose.setContent {
            KTreeView(tree, { it.id }, { it.kids }, { it.id }, Modifier.height(600.dp), state, checkable = true)
        }
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("App.kt"))).performClick()
        compose.waitForIdle()
        assertThat(state.checked).containsExactly("App.kt")
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("src")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Indeterminate))
    }

    @Test
    fun `트리에서 사라진 노드의 선택과 펼침은 지운다`() {
        val state = KTreeState(initialExpanded = setOf("src", "main"), initialSelected = "App.kt", initialChecked = setOf("Theme.kt"))
        var roots by mutableStateOf(tree)
        compose.setContent { KTreeView(roots, { it.id }, { it.kids }, { it.id }, Modifier.height(400.dp), state) }
        roots = listOf(Node("build.gradle"))
        compose.waitForIdle()
        assertThat(state.selected).isNull()
        assertThat(state.expanded).isEmpty()
        assertThat(state.checked).isEmpty()
    }

    @Test
    fun `빈 트리도 그린다`() {
        compose.setContent { KTreeView(emptyList<Node>(), { it.id }, { it.kids }, { it.id }, Modifier.height(200.dp).testTag("tree")) }
        compose.onNodeWithTag("tree").assertExists()
    }

    @Test
    fun `애니메이션 삭제면 바로 펼친다`() {
        compose.setContent {
            CompositionLocalProvider(LocalKineticaReducedMotion provides true) {
                KTreeView(tree, { it.id }, { it.kids }, { it.id }, Modifier.height(400.dp))
            }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("src").performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(FRAME)
        compose.onNodeWithText("main").assertIsDisplayed()
    }

    @Test
    fun `회전 뒤에도 펼침과 선택이 남는다`() {
        val restore = StateRestorationTester(compose)
        var state: KTreeState? = null
        restore.setContent {
            state = rememberKTreeState()
            KTreeView(tree, { it.id }, { it.kids }, { it.id }, Modifier.height(400.dp), state!!)
        }
        compose.runOnIdle {
            state!!.expand("src")
            state!!.selected = "main"
        }
        state = null
        restore.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertThat(state!!.isExpanded("src")).isTrue()
            assertThat(state!!.selected).isEqualTo("main")
        }
    }

    private companion object {
        const val FRAME = 32L
    }
}
