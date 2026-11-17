package com.naury.kinetica.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KMentionInputTest {
    @get:Rule
    val compose = createComposeRule()

    private val people = listOf(
        KMentionCandidate("u1", "Kim", "Design", KAvatarData("Kim")),
        KMentionCandidate("u2", "Kang", "Platform", KAvatarData("Kang")),
        KMentionCandidate("u3", "Lee", "Data"),
    )
    private val suggest: (Char, String) -> List<KMentionCandidate> = { _, q -> people.filter { it.label.startsWith(q, ignoreCase = true) } }

    @Test
    fun `트리거는 글 맨 앞이나 공백 뒤에서만 찾고 공백을 넘거나 멘션 안이면 찾지 않는다`() {
        val at = setOf('@', '#')
        assertThat(findMentionQuery("hi @ki", 6, at, emptyList())).isEqualTo(MentionQuery('@', 3, "ki"))
        assertThat(findMentionQuery("#rel", 4, at, emptyList())).isEqualTo(MentionQuery('#', 0, "rel"))
        assertThat(findMentionQuery("mail@ki", 7, at, emptyList())).isNull()
        assertThat(findMentionQuery("@ki m", 5, at, emptyList())).isNull()
        assertThat(findMentionQuery("@Kim", 4, at, listOf(KMention("u1", "Kim", '@', 0, 4)))).isNull()
        assertThat(findMentionQuery("", 0, at, emptyList())).isNull()
    }

    @Test
    fun `멘션을 넣으면 뒤에 공백이 없을 때만 붙이고 뒤 멘션 위치를 옮긴다`() {
        val later = KMention("u3", "Lee", '@', 6, 10)
        val edit = insertMention(TextFieldValue("@k and@Lee", TextRange(2)), listOf(later), MentionQuery('@', 0, "k"), people[0])
        assertThat(edit.value.text).isEqualTo("@Kim and@Lee")
        assertThat(edit.value.selection).isEqualTo(TextRange(5))
        assertThat(edit.mentions.map { it.start to it.end }).containsExactly(0 to 4, 8 to 12).inOrder()
        val end = insertMention(TextFieldValue("@k", TextRange(2)), emptyList(), MentionQuery('@', 0, "k"), people[0])
        assertThat(end.value.text).isEqualTo("@Kim ")
    }

    @Test
    fun `멘션을 한 글자라도 지우면 멘션 전체를 지우고 안에 글을 넣으면 평범한 글이 된다`() {
        val kim = KMention("u1", "Kim", '@', 3, 7)
        val lee = KMention("u3", "Lee", '@', 8, 12)
        val old = TextFieldValue("hi @Kim @Lee", TextRange(7))
        val deleted = applyMentionEdit(old, listOf(kim, lee), TextFieldValue("hi @Ki @Lee", TextRange(6)))
        assertThat(deleted.value.text).isEqualTo("hi  @Lee")
        assertThat(deleted.value.selection).isEqualTo(TextRange(3))
        assertThat(deleted.mentions).containsExactly(lee.copy(start = 4, end = 8))

        val typed = applyMentionEdit(old, listOf(kim, lee), TextFieldValue("hi @Kxim @Lee", TextRange(6)))
        assertThat(typed.mentions).containsExactly(lee.copy(start = 9, end = 13))

        val before = applyMentionEdit(old, listOf(kim, lee), TextFieldValue("hi! @Kim @Lee", TextRange(3)))
        assertThat(before.mentions.map { it.start }).containsExactly(4, 9).inOrder()

        val caret = applyMentionEdit(old, listOf(kim, lee), old.copy(selection = TextRange(5)))
        assertThat(caret.value.selection).isEqualTo(TextRange(7))
    }

    @Test
    fun `골뱅이 뒤 글로 후보를 띄우고 누르면 멘션이 들어가 알린다`() {
        val state = KMentionInputState()
        var reported: List<KMention> = emptyList()
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                KMentionInput(state, suggest, Modifier.testTag("field"), onMentionsChange = { reported = it })
            }
        }
        compose.onNodeWithTag("field").performTextInput("hi @k")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Kang, Platform").performClick()
        compose.waitForIdle()
        assertThat(state.text).isEqualTo("hi @Kang ")
        assertThat(reported).containsExactly(KMention("u2", "Kang", '@', 3, 8))
        compose.onNodeWithContentDescription("Kim, Design").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `방향키와 Enter로 고르고 Esc는 팝업을 닫는다`() {
        val state = KMentionInputState()
        compose.setContent { KMentionInput(state, suggest, Modifier.testTag("field")) }
        val field = compose.onNodeWithTag("field")
        field.requestFocus()
        field.performTextInput("@k")
        compose.waitForIdle()
        field.performKeyInput { pressKey(Key.DirectionDown) }
        field.performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        assertThat(state.text).isEqualTo("@Kang ")
        field.performTextInput("@l")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Lee, Data").assertExists()
        field.performKeyInput { pressKey(Key.Escape) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Lee, Data").assertDoesNotExist()
    }

    @Test
    fun `해시태그처럼 다른 트리거도 쓰고 입력란 이름은 안내 글이다`() {
        val state = KMentionInputState()
        val tags = listOf(KMentionCandidate("t1", "release"))
        compose.setContent {
            KMentionInput(state, { trigger, _ ->
                if (trigger ==
                    '#'
                ) {
                    tags
                } else {
                    people
                }
            }, Modifier.testTag("field"), setOf('@', '#'), "Comment")
        }
        compose.onNodeWithTag("field")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.SetText))
            .performTextInput("#re")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("release").performClick()
        compose.waitForIdle()
        assertThat(state.mentions.single().trigger).isEqualTo('#')
        compose.onNodeWithTag("field").performTextReplacement("")
        compose.waitForIdle()
        assertThat(state.mentions).isEmpty()
    }

    @Test
    fun `회전 뒤에도 글과 멘션이 남는다`() {
        val restore = StateRestorationTester(compose)
        var state: KMentionInputState? = null
        restore.setContent {
            state = rememberKMentionInputState()
            KMentionInput(state!!, suggest)
        }
        compose.runOnIdle { state!!.pick(MentionQuery('@', 0, ""), people[0]) }
        state = null
        restore.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertThat(state!!.text).isEqualTo("@Kim ")
            assertThat(state!!.mentions).containsExactly(KMention("u1", "Kim", '@', 0, 4))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `트리거가 비어 있으면 거부한다`() {
        compose.setContent { KMentionInput(KMentionInputState(), suggest, triggers = emptySet()) }
    }
}
