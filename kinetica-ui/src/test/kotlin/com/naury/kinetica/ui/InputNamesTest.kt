package com.naury.kinetica.ui

import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 입력란 이름이 실제 Android 접근성 노드에 남는지. 호스트 앱 3차 피드백: 채팅 입력란에 글을 쓰면 안내 글이 사라져
 * TalkBack이 '편집 상자'라고만 읽었다. Compose는 자식이 있는 입력란의 contentDescription을 EditText 바로 아래 자식 노드로
 * 내보내므로, EditText 노드와 그 자식 노드에서 이름을 찾는다(uiautomator dump에서도 같은 구조로 보인다).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class InputNamesTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var case by mutableStateOf(0)

    private fun composeView(view: View): View? {
        if (view.javaClass.simpleName == "AndroidComposeView") return view
        if (view is ViewGroup) for (i in 0 until view.childCount) composeView(view.getChildAt(i))?.let { return it }
        return null
    }

    /** 입력란 EditText 노드와 TalkBack이 읽는 자식 노드가 내보내는 이름(contentDescription, 글자)과 EditText의 글자 */
    private class FieldNode(val className: CharSequence?, val text: CharSequence?, val names: List<String>)

    private fun fieldNodes(): List<FieldNode> {
        val provider = composeView(compose.activity.window.decorView)!!.accessibilityNodeProvider
        return compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().map { node ->
            val info = provider.createAccessibilityNodeInfo(node.id)!!
            val names = mutableListOf<String>()
            info.contentDescription?.let { names += it.toString() }
            // 접근성에서 숨긴 자식은 TalkBack이 읽지 않는다.
            val children = (0 until info.childCount).mapNotNull { provider.createAccessibilityNodeInfo(virtualChildId(info, it)) }
            for (child in children.filter { it.isVisibleToUser }) {
                child.contentDescription?.let { names += it.toString() }
                child.text?.let { names += it.toString() }
            }
            FieldNode(info.className, info.text, names)
        }
    }

    /** 숨은 API인 getChildId의 위 32비트가 가상 뷰 id다. Robolectric에서는 숨은 API 제한이 없다. */
    private fun virtualChildId(info: AccessibilityNodeInfo, index: Int): Int {
        val id = AccessibilityNodeInfo::class.java.getMethod("getChildId", Int::class.java).invoke(info, index) as Long
        return (id ushr VIRTUAL_SHIFT).toInt()
    }

    /** [cases]를 차례로 그리며 입력란 하나의 이름이 [name] 하나만 있는지 본다. 같은 말을 두 번 읽으면 안 된다. */
    private fun assertNamed(name: String, vararg cases: @Composable () -> Unit) {
        compose.setContent { cases[case]() }
        for (i in cases.indices) {
            case = i
            compose.waitForIdle()
            val field = fieldNodes().single()
            assertThat(field.className).isEqualTo("android.widget.EditText")
            assertThat(field.names.filter { it == name }).hasSize(1)
        }
    }

    @Test
    fun `채팅 입력란은 글을 쓴 뒤에도 안내 글을 이름으로 내보낸다`() {
        assertNamed(
            "Message",
            { KChatInput("", {}, onSend = {}) },
            { KChatInput("hello", {}, onSend = {}) },
            { KChatInput(TextFieldState("hello"), onSend = {}) },
        )
        compose.onNodeWithContentDescription("Message").assertExists()
    }

    @Test
    fun `채팅 입력란 label이 있으면 label을 이름으로 쓰고 안내 글은 힌트로 남긴다`() {
        compose.setContent { KChatInput("", {}, onSend = {}, placeholder = "Say hi", label = "Chat with Mina") }
        compose.waitForIdle()
        val field = fieldNodes().single()
        assertThat(field.names).containsAtLeast("Chat with Mina", "Say hi")
        compose.onNodeWithText("Say hi").assertExists()
    }

    @Test
    fun `검색 막대는 검색어가 있어도 이름이 남는다`() {
        assertNamed("Search", { KSearchBar("", {}, {}) }, { KSearchBar("kotlin", {}, {}) })
    }

    @Test
    fun `검색 화면 입력란도 검색어가 있을 때 이름이 있다`() {
        // 펼친 검색 화면은 다른 창에 그려지므로 semantics로 확인한다.
        compose.setContent { KSearchView(true, {}, "abc", {}, {}, placeholder = "Find") }
        compose.onNode(hasSetTextAction()).assert(hasContentDescription("Find"))
    }

    @Test
    fun `태그 입력란은 안내 글이 없어도 기본 이름이 있다`() {
        assertNamed("Add tag", { KTagInput(emptyList(), {}) }, { KTagInput(listOf("a"), {}) })
    }

    @Test
    fun `태그 입력란은 안내 글을 한 번만 이름으로 읽는다`() {
        assertNamed("Topics", {
            KTagInput(emptyList(), {}, placeholder = "Topics")
        }, { KTagInput(listOf("a"), {}, placeholder = "Topics") })
    }

    @Test
    fun `라벨이 떠오르는 입력란은 글이 있어도 라벨을 내보낸다`() {
        assertNamed(
            "Name",
            { KTextField("", {}, label = "Name") },
            { KTextField("Mina", {}, label = "Name") },
            { KTextField("Mina", {}, label = "Name", variant = KTextFieldVariant.Outlined) },
            { KTextField("Mina", {}, label = "Name", variant = KTextFieldVariant.Underline) },
            { KTextArea("Hello", {}, label = "Name") },
            { KAutocomplete("Se", {}, emptyList(), {}, label = "Name") },
            { KPasswordField("secret", {}, label = "Name") },
        )
    }

    @Test
    fun `인증 코드 입력란은 이름이 있다`() {
        assertNamed("Verification code", { KCodeInput("", {}) }, { KCodeInput("12", {}) })
    }

    @Test
    fun `글자 수는 counterThreshold 안쪽에 들어왔을 때만 보인다`() {
        var text by mutableStateOf("a".repeat(5))
        compose.setContent { KChatInput(text, { text = it }, onSend = {}, maxLength = 10, counterThreshold = 3) }
        compose.onNodeWithContentDescription("5 of 10 characters", useUnmergedTree = true).assertDoesNotExist()
        text = "a".repeat(7)
        compose.onNodeWithContentDescription("7 of 10 characters", useUnmergedTree = true).assertExists()
        text = "a".repeat(10)
        compose.onNodeWithContentDescription("10 of 10 characters", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `counterThreshold가 음수면 예외를 던진다`() {
        val rule = runCatching { ChatCounterRule(always = false, threshold = -1) }
        assertThat(rule.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(ChatCounterRule(always = true, threshold = null).visible(1, 10)).isTrue()
        assertThat(ChatCounterRule(always = true, threshold = 0).visible(9, 10)).isFalse()
        assertThat(ChatCounterRule(always = false, threshold = 0).visible(10, 10)).isTrue()
    }

    private companion object {
        const val VIRTUAL_SHIFT = 32
    }
}
