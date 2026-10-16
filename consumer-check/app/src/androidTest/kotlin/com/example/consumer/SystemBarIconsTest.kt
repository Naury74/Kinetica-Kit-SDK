package com.example.consumer

import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.consumer.TestSupport.back
import com.example.consumer.recent.BarIconsActivity
import com.example.consumer.recent.BarIconsScaffoldActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 창의 상태 표시줄 아이콘이 어두운지(밝은 배경용). 메인 스레드에서 읽는다. */
private fun Window.darkStatusIcons(): Boolean {
    var dark = false
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        dark = WindowCompat.getInsetsController(this, decorView).isAppearanceLightStatusBars
    }
    return dark
}

/** 화면에 붙은 창 가운데 Compose Dialog 창. 없으면 null. WindowInspector는 API 29부터 있다. */
@androidx.annotation.RequiresApi(29)
private fun composeDialogWindow(): Window? {
    var found: Window? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        fun search(view: View): Window? {
            if (view is DialogWindowProvider) return view.window
            if (view is ViewGroup) for (i in 0 until view.childCount) search(view.getChildAt(i))?.let { return it }
            return null
        }
        found = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull { search(it) }
    }
    return found
}

private fun <A : BarIconsActivity> AndroidComposeTestRule<ActivityScenarioRule<A>, A>.openViewer() {
    onNodeWithTag("barOpen").performClick()
    assertTrue("뷰어가 열리지 않았다", becomes { activity.viewerOpen })
    Thread.sleep(TestSupport.SETTLE_MS)
    waitForIdle()
}

private fun <A : BarIconsActivity> AndroidComposeTestRule<ActivityScenarioRule<A>, A>.closeViewer() {
    back(this)
    assertTrue("뷰어가 닫히지 않았다", becomes { !activity.viewerOpen })
    Thread.sleep(TestSupport.SETTLE_MS)
    waitForIdle()
}

/**
 * 시나리오 9: 밝은 화면(어두운 아이콘)의 호스트가 사진 뷰어를 연다. 뷰어가 떠 있는 동안 아이콘이 밝아지고 닫으면 호스트 설정으로 돌아와야 한다.
 * 스캐폴드 안에서는 뷰어가 호스트 창에 뜨므로 호스트 창이 바뀐다.
 */
@RunWith(AndroidJUnit4::class)
class SystemBarIconsScaffoldTest {
    @get:Rule
    val rule = createAndroidComposeRule<BarIconsScaffoldActivity>()

    private fun hostDark() = rule.activity.window.darkStatusIcons()

    @Test
    fun viewerLightensIconsAndRestores() {
        rule.waitTag("barOpen")
        assertTrue("호스트는 밝은 배경용 어두운 아이콘으로 시작한다", hostDark())
        rule.openViewer()
        assertTrue("뷰어가 떠 있는 동안 아이콘이 밝아져야 한다", rule.becomes { !hostDark() })
        rule.closeViewer()
        assertTrue("닫으면 호스트의 어두운 아이콘으로 돌아와야 한다", rule.becomes { hostDark() })
    }

    /** 호스트가 먼저 KSystemBarIcons(light = true)를 두고 뷰어를 열었다 닫으면 호스트 요청이 남는다. 호스트가 빼면 원래대로 돌아온다. */
    @Test
    fun hostRequestSurvivesViewer() {
        rule.waitTag("barOpen")
        rule.runOnIdle { rule.activity.hostIcons = true }
        assertTrue("호스트 요청으로 아이콘이 밝아져야 한다", rule.becomes { !hostDark() })
        rule.openViewer()
        assertFalse(hostDark())
        rule.closeViewer()
        assertFalse("뷰어를 닫아도 호스트 요청(밝은 아이콘)이 남아야 한다", hostDark())
        rule.runOnIdle { rule.activity.hostIcons = null }
        assertTrue("호스트가 요청을 빼면 처음 설정으로 돌아와야 한다", rule.becomes { hostDark() })
    }

    /** 뷰어가 떠 있는 동안 호스트가 어두운 아이콘을 요청하면 나중 요청이 이긴다. 호스트가 빼면 뷰어 요청으로, 뷰어를 닫으면 처음으로. */
    @Test
    fun laterRequestWinsAndUnwindsInOrder() {
        rule.waitTag("barOpen")
        rule.openViewer()
        assertTrue(rule.becomes { !hostDark() })
        rule.runOnIdle { rule.activity.hostIcons = false }
        assertTrue("나중에 들어온 호스트 요청(어두운 아이콘)이 이겨야 한다", rule.becomes { hostDark() })
        rule.runOnIdle { rule.activity.hostIcons = null }
        assertTrue("호스트 요청이 빠지면 뷰어 요청(밝은 아이콘)으로 돌아와야 한다", rule.becomes { !hostDark() })
        rule.closeViewer()
        assertTrue(rule.becomes { hostDark() })
    }

    /**
     * 어두운 화면(밝은 아이콘)인 호스트가 뷰어를 연 동안 KSystemBarIcons 없이 창 설정을 직접 바꾸면(예: 앱 안에서 밝은 테마로 전환),
     * 뷰어를 닫을 때 뷰어를 열 때 기억한 값이 아니라 호스트가 바꾼 값이 남아야 한다.
     * 뷰어가 쓴 값과 같은 값을 다시 쓰는 것은 창 상태가 바뀌지 않아 SDK가 알 수 없으므로, 뷰어와 다른 값으로 바꾸는 경우를 본다.
     */
    @Test
    fun directHostChangeWhileOpenIsKept() {
        rule.waitTag("barOpen")
        rule.runOnIdle {
            WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView).isAppearanceLightStatusBars = false
        }
        assertFalse(hostDark())
        rule.openViewer()
        assertFalse(hostDark())
        rule.runOnIdle {
            WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView).isAppearanceLightStatusBars = true
        }
        rule.closeViewer()
        assertTrue("뷰어를 연 동안 호스트가 바꾼 설정(어두운 아이콘)이 남아야 한다", hostDark())
    }
}

/** 시나리오 9(이어서): KineticaScaffold 밖에서는 뷰어가 별도 Dialog 창에 뜬다. 그 창의 아이콘이 밝아지고 호스트 창은 건드리지 않아야 한다. */
@RunWith(AndroidJUnit4::class)
@androidx.test.filters.SdkSuppress(minSdkVersion = 29)
class SystemBarIconsWindowTest {
    @get:Rule
    val rule = createAndroidComposeRule<BarIconsActivity>()

    private fun hostDark() = rule.activity.window.darkStatusIcons()

    @Test
    fun viewerWindowHasLightIconsAndHostUntouched() {
        rule.waitTag("barOpen")
        assertTrue(hostDark())
        rule.openViewer()
        val dialog = composeDialogWindow()
        assertNotNull("뷰어가 Dialog 창으로 뜨지 않았다", dialog)
        assertTrue("뷰어 창의 아이콘이 밝아야 한다", rule.becomes { !dialog!!.darkStatusIcons() })
        assertTrue("뷰어 창 아래 호스트 창은 그대로 어두운 아이콘이어야 한다", hostDark())
        rule.closeViewer()
        assertTrue("닫은 뒤 뷰어 창이 남았다", rule.becomes { composeDialogWindow() == null })
        assertTrue(hostDark())
    }

    /** 호스트 창에 둔 KSystemBarIcons는 뷰어 창과 따로 논다. 열고 닫아도 호스트 요청이 그대로다. */
    @Test
    fun hostRequestOnActivityWindowIsIndependent() {
        rule.waitTag("barOpen")
        rule.runOnIdle { rule.activity.hostIcons = true }
        assertTrue(rule.becomes { !hostDark() })
        rule.openViewer()
        val dialog = composeDialogWindow()
        assertNotNull(dialog)
        assertTrue(rule.becomes { !dialog!!.darkStatusIcons() })
        rule.closeViewer()
        assertFalse("뷰어를 닫아도 호스트 요청이 남아야 한다", hostDark())
        rule.runOnIdle { rule.activity.hostIcons = null }
        assertTrue(rule.becomes { hostDark() })
        assertEquals(true, hostDark())
    }
}
