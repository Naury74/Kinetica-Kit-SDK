package com.example.consumer

import android.app.Activity
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice

/** 계측 시험에서 같이 쓰는 도우미. */
object TestSupport {
    val device: UiDevice get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /** adb shell 명령을 실행하고 출력을 돌려준다. */
    fun shell(command: String): String {
        val pfd: ParcelFileDescriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes().decodeToString() }
    }

    /** 시스템 뒤로 가기(실제 키 이벤트)를 보내고 Compose가 정리될 때까지 기다린다. */
    fun back(rule: ComposeTestRule) {
        device.pressBack()
        rule.waitForIdle()
        Thread.sleep(SETTLE_MS)
        rule.waitForIdle()
    }

    /** 활동 창 기준 IME 높이(px). 키보드가 없으면 0. */
    fun imeBottom(activity: Activity): Int {
        val insets = ViewCompat.getRootWindowInsets(activity.window.decorView) ?: return 0
        return insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
    }

    /** 화면에 떠 있는 IME 창의 위 끝(화면 좌표 px). 키보드가 없으면 null. 어느 창이 키보드를 띄웠든 상관없이 잰다. */
    fun imeTopOnScreen(): Int? {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val info = automation.serviceInfo
        if (info.flags and android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS == 0) {
            info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = info
        }
        val ime = automation.windows.firstOrNull { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD } ?: return null
        val rect = android.graphics.Rect()
        ime.getBoundsInScreen(rect)
        return rect.top.takeIf { rect.height() > 0 }
    }

    fun systemBars(activity: Activity): androidx.core.graphics.Insets {
        val insets = ViewCompat.getRootWindowInsets(activity.window.decorView) ?: return androidx.core.graphics.Insets.NONE
        return insets.getInsets(WindowInsetsCompat.Type.systemBars())
    }

    const val SETTLE_MS = 600L
}

@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.waitTag(tag: String, timeoutMs: Long = 5_000) = waitUntilAtLeastOneExists(hasTestTag(tag), timeoutMs)

@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.waitText(text: String, timeoutMs: Long = 5_000) = waitUntilAtLeastOneExists(hasText(text, substring = true), timeoutMs)

@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.waitGone(tag: String, timeoutMs: Long = 5_000) = waitUntilDoesNotExist(hasTestTag(tag), timeoutMs)

/** 조건이 [timeoutMs] 안에 참이 되는지. 시험 실패 메시지를 직접 남기려고 예외 대신 Boolean을 돌려준다. */
fun ComposeTestRule.becomes(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean = try {
    waitUntil(timeoutMs, condition)
    true
} catch (_: ComposeTimeoutException) {
    false
}
