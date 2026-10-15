package com.example.consumer

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.consumer.m3.M3EdgeActivity
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 시나리오 3: 글래스 시트와 다이얼로그 안의 Material 입력 폼. */
@RunWith(AndroidJUnit4::class)
class FormTest {
    @get:Rule
    val rule = createAndroidComposeRule<M3EdgeActivity>()

    @Before
    fun openTab() {
        rule.onNodeWithTag("tab_form").performClick()
        rule.waitTag("openFormSheet")
    }

    @Test
    fun imeNextMovesFocusAndDoneSubmits() {
        rule.onNodeWithTag("openFormSheet").performClick()
        rule.waitTag("fieldName")
        rule.onNodeWithTag("fieldName").performClick()
        rule.onNodeWithTag("fieldName").performTextInput("홍길동")
        rule.onNodeWithTag("fieldName").performImeAction()
        rule.onNodeWithTag("fieldPhone").assertIsFocused()
        rule.onNodeWithTag("fieldPhone").performTextInput("01012345678")
        rule.onNodeWithTag("fieldPhone").performImeAction()
        rule.onNodeWithTag("fieldAddress").assertIsFocused()
        rule.onNodeWithTag("fieldAddress").performTextInput("서울")
        rule.onNodeWithTag("fieldAddress").performImeAction()
        rule.onNodeWithTag("fieldMemo").assertIsFocused()
        rule.onNodeWithTag("fieldMemo").performTextInput("문 앞")
        rule.onNodeWithTag("fieldMemo").performImeAction()
        rule.waitGone("fieldName")
        rule.onNodeWithTag("submitted").assertTextContains("홍길동/01012345678/서울/문 앞", substring = true)
        // 닫힌 뒤 키보드가 남지 않아야 한다.
        assertTrue("시트를 닫았는데 키보드가 남았다", rule.becomes(3_000) { TestSupport.imeTopOnScreen() == null })
    }

    /** 키보드가 올라와도 맨 아래 입력란과 저장 버튼이 키보드 위에 보여야 한다. */
    @Test
    fun keyboardDoesNotCoverFocusedField() {
        rule.onNodeWithTag("openFormSheet").performClick()
        rule.waitTag("fieldMemo")
        rule.onNodeWithTag("fieldMemo").performClick()
        assertTrue("키보드가 올라오지 않았다", rule.becomes(5_000) { TestSupport.imeTopOnScreen() != null })
        Thread.sleep(1_000)
        rule.waitForIdle()
        val imeTop = TestSupport.imeTopOnScreen() ?: error("키보드가 사라졌다")
        val memo = rule.onNodeWithTag("fieldMemo").fetchSemanticsNode()
        val memoBottom = memo.positionOnScreen.y + memo.size.height
        assertTrue("메모 입력란이 키보드에 가린다: bottom=$memoBottom imeTop=$imeTop", memoBottom <= imeTop)
        val submit = rule.onNodeWithTag("formSubmit").fetchSemanticsNode()
        val submitBottom = submit.positionOnScreen.y + submit.size.height
        assertTrue("저장 버튼이 키보드에 가린다: bottom=$submitBottom imeTop=$imeTop", submitBottom <= imeTop)
    }

    @Test
    fun sheetValidationKeepsSheetOpen() {
        rule.onNodeWithTag("openFormSheet").performClick()
        rule.waitTag("formSubmit")
        rule.onNodeWithTag("formSubmit").performClick()
        rule.waitTag("formError")
        rule.onNodeWithTag("formError").assertTextContains("이름을 입력하세요")
        rule.onNodeWithTag("fieldName").performTextInput("김")
        rule.onNodeWithTag("formSubmit").performClick()
        rule.onNodeWithTag("formError").assertTextContains("전화번호를 확인하세요")
        rule.onNodeWithTag("fieldName").assertExistsCompat()
    }

    @Test
    fun dialogFormValidatesAndSubmits() {
        rule.onNodeWithTag("openFormDialog").performClick()
        rule.waitTag("loginSubmit")
        rule.onNodeWithTag("loginSubmit").performClick()
        rule.waitTag("loginError")
        rule.onNodeWithTag("loginId").performTextInput("naury")
        rule.onNodeWithTag("loginId").performImeAction()
        rule.onNodeWithTag("loginPw").assertIsFocused()
        rule.onNodeWithTag("loginPw").performTextInput("secret")
        rule.onNodeWithTag("loginPw").performImeAction()
        rule.waitGone("loginId")
        rule.onNodeWithTag("submitted").assertTextContains("login:naury", substring = true)
    }

    /** 다이얼로그 안 입력란을 눌러 키보드가 올라와도 입력란이 보여야 한다. */
    @Test
    fun keyboardDoesNotCoverDialogField() {
        rule.onNodeWithTag("openFormDialog").performClick()
        rule.waitTag("loginPw")
        rule.onNodeWithTag("loginPw").performClick()
        assertTrue("키보드가 올라오지 않았다", rule.becomes(5_000) { TestSupport.imeTopOnScreen() != null })
        Thread.sleep(1_000)
        rule.waitForIdle()
        val imeTop = TestSupport.imeTopOnScreen() ?: error("키보드가 사라졌다")
        val submit = rule.onNodeWithTag("loginSubmit").fetchSemanticsNode()
        val bottom = submit.positionOnScreen.y + submit.size.height
        assertTrue("로그인 버튼이 키보드에 가린다: bottom=$bottom imeTop=$imeTop", bottom <= imeTop)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsCompat() = assertExists()
}
