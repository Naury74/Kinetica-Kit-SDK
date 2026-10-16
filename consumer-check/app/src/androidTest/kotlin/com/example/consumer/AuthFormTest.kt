package com.example.consumer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.consumer.m3.M3EdgeActivity
import com.example.consumer.m3.Routes
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 시나리오 5: Material3 Scaffold 안 가입 폼에서 KPasswordField, KTagInput, KCodeInput을 쓴다. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AuthFormTest {
    @get:Rule
    val rule = createAndroidComposeRule<M3EdgeActivity>()

    @Before
    fun open() {
        rule.openExtra(Routes.AUTH)
        rule.waitTag("suEmail")
    }

    /** KPasswordField와 KTagInput의 안쪽 입력란은 fieldModifier로 붙인 태그로 찾는다. */
    private fun fieldIn(tag: String): SemanticsNodeInteraction = rule.onNodeWithTag("${tag}Field")

    @Test
    fun imeMovesThroughFieldsAndCodeOpensNextScreen() {
        rule.onNodeWithTag("suEmail").performClick()
        rule.onNodeWithTag("suEmail").performTextInput("a@b.co")
        rule.onNodeWithTag("suEmail").performImeAction()
        fieldIn("suPassword").assertIsFocused()
        fieldIn("suPassword").performTextInput("Kinetica#2026x")
        rule.onNodeWithTag("suStrength").assertTextContains("Strong", substring = true)
        // imeAction = Next이고 콜백이 없으면 기본 동작으로 다음 입력란에 간다.
        fieldIn("suPassword").performImeAction()
        fieldIn("suTags").assertIsFocused()

        fieldIn("suTags").performTextInput("디자인, 개발,")
        rule.onNodeWithTag("suTagsValue").assertTextContains("관심사: 디자인,개발")
        fieldIn("suTags").performTextInput("사")
        rule.waitUntilAtLeastOneExists(hasText("사진") and !hasSetTextAction(), 3_000)
        rule.onNode(hasText("사진") and !hasSetTextAction()).performClick()
        rule.onNodeWithTag("suTagsValue").assertTextContains("관심사: 디자인,개발,사진")
        // 중복은 더하지 않는다.
        fieldIn("suTags").performTextInput("디자인")
        fieldIn("suTags").performImeAction()
        rule.onNodeWithTag("suTagsValue").assertTextContains("관심사: 디자인,개발,사진")
        // 빈 입력란에서 완료를 누르면 칩을 만들지 않고 기본 동작대로 키보드를 내린다.
        assertTrue("관심사 입력 중 키보드가 보이지 않는다", rule.becomes(5_000) { TestSupport.imeTopOnScreen() != null })
        fieldIn("suTags").performImeAction()
        assertTrue("빈 관심사 칸에서 완료를 눌렀는데 키보드가 남았다", rule.becomes(3_000) { TestSupport.imeTopOnScreen() == null })
        rule.onNodeWithTag("suTagsValue").assertTextContains("관심사: 디자인,개발,사진")

        rule.onNodeWithTag("suSendCode").performScrollTo().performClick()
        rule.waitTag("suCode")
        rule.onNodeWithTag("suCode").performScrollTo().performClick()
        rule.onNodeWithTag("suCode").performTextInput("111111")
        rule.waitText("인증번호가 틀렸어요")
        // 틀린 뒤 호스트가 값을 비웠으니 다시 6자리를 넣으면 onComplete가 다시 와야 한다.
        rule.onNodeWithTag("suCode").performTextInput(com.example.consumer.m3.TEST_CODE)
        rule.waitTag("welcome")
        rule.onNodeWithTag("welcome").assertTextContains("a@b.co", substring = true)
        assertTrue("다음 화면으로 넘어간 뒤에도 키보드가 남았다", rule.becomes(3_000) { TestSupport.imeTopOnScreen() == null })
    }

    /** 문자 메시지 전체를 붙여 넣으면 숫자만 골라 채우고 바로 완료된다. 몇 자리 친 뒤에 붙여 넣어도 새 코드로 바뀐다. */
    @Test
    fun pastingSmsTextFillsCode() {
        rule.onNodeWithTag("suEmail").performTextInput("paste@b.co")
        rule.onNodeWithTag("suSendCode").performScrollTo().performClick()
        rule.waitTag("suCode")
        rule.onNodeWithTag("suCode").performScrollTo().performClick()
        rule.onNodeWithTag("suCode").performTextInput("98")
        rule.runOnUiThread {
            val clipboard = rule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("sms", "[Kinetica] 인증번호 123-456 을 입력하세요"))
        }
        rule.waitForIdle()
        rule.onNodeWithTag("suCode").performSemanticsAction(SemanticsActions.PasteText)
        rule.waitTag("welcome")
        rule.onNodeWithTag("welcome").assertTextContains("paste@b.co", substring = true)
    }

    /** KSheet 안에서도 비밀번호 '완료'에서 PIN 칸으로 가고, PIN을 다 채우면 시트가 닫히며 키보드도 내려간다. */
    @Test
    fun pinSheetMovesFocusAndCloses() {
        rule.onNodeWithTag("suOpenPinSheet").performScrollTo().performClick()
        rule.waitTag("pinCode")
        fieldIn("pinCurrent").performClick()
        fieldIn("pinCurrent").performTextInput("old-pass")
        fieldIn("pinCurrent").performImeAction()
        rule.onNodeWithTag("pinCode").assertIsFocused()
        assertTrue("PIN 칸으로 옮긴 뒤 키보드가 보이지 않는다", rule.becomes(5_000) { TestSupport.imeTopOnScreen() != null })
        Thread.sleep(800)
        rule.waitForIdle()
        val imeTop = TestSupport.imeTopOnScreen() ?: error("키보드가 사라졌다")
        val pin = rule.onNodeWithTag("pinCode").fetchSemanticsNode()
        val pinBottom = pin.positionOnScreen.y + pin.size.height
        assertTrue("시트 안 PIN 칸이 키보드에 가린다: bottom=$pinBottom imeTop=$imeTop", pinBottom <= imeTop)

        rule.onNodeWithTag("pinCode").performTextInput("4321")
        rule.waitGone("pinCode")
        rule.onNodeWithTag("pinResult").assertTextContains("변경됨:8:4", substring = true)
        assertTrue("시트를 닫았는데 키보드가 남았다", rule.becomes(3_000) { TestSupport.imeTopOnScreen() == null })
    }
}
