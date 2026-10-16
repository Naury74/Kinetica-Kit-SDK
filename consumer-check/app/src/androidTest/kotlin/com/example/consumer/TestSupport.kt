package com.example.consumer

import android.app.Activity
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

/** 홈 화면 아래 버튼으로 [route] 화면을 연다. */
fun ComposeTestRule.openExtra(route: String) {
    onNode(hasTestTag("go_$route")).performScrollTo().performClick()
    waitForIdle()
}

/** 실제 입력 장치처럼 화면 좌표(px)의 [points]를 차례로 잇는 터치를 넣는다. View 계층의 가로채기(onInterceptTouchEvent)까지 거친다. */
fun injectDrag(points: List<android.graphics.PointF>, substeps: Int = 6, stepMs: Long = 12) {
    val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
    val downTime = android.os.SystemClock.uptimeMillis()
    fun send(action: Int, x: Float, y: Float) {
        val event = android.view.MotionEvent.obtain(downTime, android.os.SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        automation.injectInputEvent(event, true)
        event.recycle()
    }
    val first = points.first()
    send(android.view.MotionEvent.ACTION_DOWN, first.x, first.y)
    points.zipWithNext().forEach { (a, b) ->
        for (i in 1..substeps) {
            Thread.sleep(stepMs)
            val f = i / substeps.toFloat()
            send(android.view.MotionEvent.ACTION_MOVE, a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
        }
    }
    val last = points.last()
    Thread.sleep(stepMs)
    send(android.view.MotionEvent.ACTION_UP, last.x, last.y)
}

/** 센서 서비스에 지금 열려 있는 이벤트 연결 가운데 이 앱(uid)의 것이 있는지. dumpsys sensorservice의 연결 목록만 본다. */
fun appHasActiveSensorConnection(): Boolean {
    val dump = TestSupport.shell("dumpsys sensorservice")
    // 기기 이미지마다 머리말이 다르다. "N open event connections ... open direct connections" 또는 "N active connections ... direct connections".
    val start = Regex("open event connections|\\d+ active connections").find(dump)?.range?.first ?: -1
    val end = Regex("direct connections").find(dump, start.coerceAtLeast(0))?.range?.first ?: -1
    if (start < 0 || end < 0) return false
    return dump.substring(start, end).contains("uid ${android.os.Process.myUid()} |")
}

/**
 * 활동 방향을 바꾸고 다시 만들어질 때까지 기다린다. 다시 만드는 중에 바로 Compose waitForIdle을 부르면 가끔 끝나지 않아,
 * 먼저 새 활동이 RESUMED가 되고 화면 방향이 바뀔 때까지 메인 스레드 밖에서 기다린 뒤 Compose를 기다린다.
 */
fun <A : android.app.Activity> rotateAndSettle(rule: ComposeTestRule, scenario: androidx.test.core.app.ActivityScenario<A>, orientation: Int) {
    var before: android.app.Activity? = null
    scenario.onActivity { before = it; it.requestedOrientation = orientation }
    val landscape = orientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    val wanted = if (landscape) android.content.res.Configuration.ORIENTATION_LANDSCAPE else android.content.res.Configuration.ORIENTATION_PORTRAIT
    val deadline = android.os.SystemClock.uptimeMillis() + 8_000
    val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
    while (android.os.SystemClock.uptimeMillis() < deadline) {
        var done = false
        instrumentation.runOnMainSync {
            val resumed = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).firstOrNull()
            done = resumed != null && resumed.resources.configuration.orientation == wanted &&
                (resumed !== before || before?.resources?.configuration?.orientation == wanted)
        }
        if (done) break
        Thread.sleep(100)
    }
    Thread.sleep(TestSupport.SETTLE_MS)
    rule.waitForIdle()
}
