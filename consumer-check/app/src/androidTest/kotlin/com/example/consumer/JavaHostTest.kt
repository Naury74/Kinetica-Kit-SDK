package com.example.consumer

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 시나리오 5: Java 액티비티에서 XML 진입점, ViewEffects, KineticaBoardView. */
@RunWith(AndroidJUnit4::class)
class JavaHostTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun javaEntryPointsWork() {
        ActivityScenario.launch(JavaHostActivity::class.java).use {
            waitView(withText("자바 위젯 a"))
            repeat(2) { onView(withText("효과 버튼")).perform(click()) }
            onView(withText("눌림 2")).check(matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))

            onView(withText("자바 시트")).perform(click())
            waitView(withText("자바에서 연 시트"))
            TestSupport.device.pressBack()
            compose.waitForIdle()
            Thread.sleep(TestSupport.SETTLE_MS)
            onView(withText("자바에서 연 시트")).check(doesNotExist())
            onView(withText("시트 닫힘")).check(matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))

            onView(withText("자바 다이얼로그")).perform(click())
            waitView(withText("자바에서 연 다이얼로그"))
            TestSupport.device.pressBack()
            Thread.sleep(TestSupport.SETTLE_MS)

            onView(withText("자바 토스트")).perform(click())
            compose.waitText("자바 토스트")
        }
    }

    @Test
    fun javaRotationDoesNotCrash() {
        ActivityScenario.launch(JavaHostActivity::class.java).use { scenario ->
            waitView(withText("자바 위젯 a"))
            scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            Thread.sleep(1_500)
            waitView(withText("자바 위젯 a"))
            scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
            Thread.sleep(1_500)
            waitView(withText("자바 위젯 b"))
        }
    }
}
