package com.example.consumer

import android.content.pm.ActivityInfo
import android.view.View
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.swipeLeft
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.consumer.xml.XmlHostActivity
import com.naury.kinetica.board.KineticaBoardView
import com.naury.kinetica.player.KineticaPlayerView
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 시나리오 4: AppCompat + Fragment + RecyclerView + ViewPager2 + CoordinatorLayout XML 화면. */
@RunWith(AndroidJUnit4::class)
class XmlHostTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<XmlHostActivity>

    @Before
    fun launch() {
        scenario = ActivityScenario.launch(XmlHostActivity::class.java)
    }

    @After
    fun close() {
        scenario.close()
    }

    private fun settle() {
        compose.waitForIdle()
        Thread.sleep(TestSupport.SETTLE_MS)
        compose.waitForIdle()
    }

    private fun openDetail(position: Int) {
        onView(withId(R.id.recycler)).perform(
            RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(position, clickChild(R.id.open)),
        )
        settle()
        onView(withId(R.id.detailTitle)).check(matches(withText("상세 $position")))
    }

    @Test
    fun recyclerRowsWithEffectsReceiveClicks() {
        onView(withId(R.id.recycler)).perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(2, click()))
        onView(withId(R.id.lastClicked)).check(matches(withText("누른 항목: 2")))
        onView(withId(R.id.recycler)).perform(RecyclerViewActions.scrollToPosition<RecyclerView.ViewHolder>(45))
        // 화면 아래 끝에서 시작하는 swipeUp()은 시스템 홈 제스처가 되므로 가운데에서 위로 민다.
        repeat(3) { onView(withId(R.id.recycler)).perform(swipeFromCenterUp()) }
        onView(withId(R.id.recycler)).perform(RecyclerViewActions.scrollToPosition<RecyclerView.ViewHolder>(5))
        onView(withId(R.id.recycler)).perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(5, click()))
        onView(withId(R.id.lastClicked)).check(matches(withText("누른 항목: 5")))
    }

    @Test
    fun fragmentBackStackAndPager() {
        openDetail(40)
        onView(allOf(isInstance(KineticaBoardView::class.java), isDisplayed())).check(matches(isDisplayed()))
        waitView(withText("위젯 hello"))
        onView(withId(R.id.pager)).perform(swipeLeft())
        settle()
        onView(allOf(isInstance(KineticaPlayerView::class.java), isDisplayed())).check(matches(isDisplayed()))
        TestSupport.device.pressBack()
        settle()
        onView(withId(R.id.recycler)).check(matches(isDisplayed()))
    }

    @Test
    fun boardWidgetClickInsidePager() {
        openDetail(1)
        waitView(withText("위젯 hello"))
        onView(withText("위젯 hello")).perform(click())
        onView(withId(R.id.detailEvent)).check(matches(withText("이벤트: 위젯 hello")))
    }

    @Test
    fun sheetFromFragmentClosesBeforeFragmentPops() {
        openDetail(3)
        onView(withId(R.id.openSheet)).perform(click())
        settle()
        onView(withId(R.id.sheetInput)).check(matches(isDisplayed()))
        TestSupport.device.pressBack()
        settle()
        onView(withId(R.id.sheetInput)).check(doesNotExist())
        onView(withId(R.id.detailTitle)).check(matches(isDisplayed()))
        TestSupport.device.pressBack()
        settle()
        onView(withId(R.id.recycler)).check(matches(isDisplayed()))
    }

    @Test
    fun sheetFormTypingAndSave() {
        openDetail(3)
        onView(withId(R.id.openSheet)).perform(click())
        settle()
        onView(withId(R.id.sheetInput)).perform(click(), typeText("hello"))
        val imeTop = TestSupport.imeTopOnScreen()
        if (imeTop != null) {
            val location = IntArray(2)
            var bottom = 0
            scenario.onActivity { activity ->
                val close = activity.findViewById<View>(R.id.sheetClose)
                close.getLocationOnScreen(location)
                bottom = location[1] + close.height
            }
            assertTrue("XML 시트의 버튼이 키보드에 가린다: bottom=$bottom imeTop=$imeTop", bottom <= imeTop)
        }
        onView(withId(R.id.sheetClose)).perform(closeSoftKeyboard(), click())
        settle()
        onView(withId(R.id.detailEvent)).check(matches(withText("이벤트: 시트 저장 hello")))
        assertTrue("시트를 닫았는데 키보드가 남았다", compose.becomes(3_000) { TestSupport.imeTopOnScreen() == null })
    }

    @Test
    fun dialogAndToastFromFragment() {
        openDetail(3)
        onView(withId(R.id.openDialog)).perform(click())
        settle()
        onView(withText("XML 다이얼로그 내용")).check(matches(isDisplayed()))
        TestSupport.device.pressBack()
        settle()
        onView(withText("XML 다이얼로그 내용")).check(doesNotExist())
        onView(withId(R.id.showToast)).perform(click())
        compose.waitText("XML 토스트")
    }

    /** Fragment를 떠나면 그 Fragment에서 연 시트도 함께 닫혀야 한다(lifecycleOwner = viewLifecycleOwner). */
    @Test
    fun sheetClosedWhenFragmentReplaced() {
        openDetail(3)
        onView(withId(R.id.openSheet)).perform(click())
        settle()
        scenario.onActivity { it.supportFragmentManager.popBackStack() }
        settle()
        onView(withId(R.id.sheetInput)).check(doesNotExist())
        onView(withId(R.id.recycler)).check(matches(isDisplayed()))
    }

    @Test
    fun rotationWithOpenSheetDoesNotCrash() {
        openDetail(3)
        onView(withId(R.id.openSheet)).perform(click())
        settle()
        try {
            // 기기마다 처음 방향이 다르므로(태블릿은 가로) 지금과 반대 방향으로 돌려야 실제로 다시 만들어진다.
            scenario.onActivity {
                val landscape = it.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                it.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            Thread.sleep(1_500)
            settle()
            onView(withId(R.id.detailTitle)).check(matches(withText("상세 3")))
        } finally {
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
            Thread.sleep(1_500)
        }
        settle()
        onView(withId(R.id.detailTitle)).check(matches(withText("상세 3")))
        // 문서: 회전하면 열린 창은 애니메이션 없이 정리된다.
        onView(withId(R.id.sheetInput)).check(doesNotExist())
        TestSupport.device.pressBack()
        settle()
        onView(withId(R.id.recycler)).check(matches(isDisplayed()))
    }

    @Test
    fun darkModeToggleDoesNotCrash() {
        openDetail(3)
        try {
            TestSupport.shell("cmd uimode night yes")
            Thread.sleep(2_500)
            settle()
            onView(withId(R.id.detailTitle)).check(matches(withText("상세 3")))
            onView(withId(R.id.openSheet)).perform(click())
            settle()
            onView(withId(R.id.sheetInput)).check(matches(isDisplayed()))
        } finally {
            TestSupport.shell("cmd uimode night no")
            Thread.sleep(2_500)
        }
        settle()
        onView(withId(R.id.detailTitle)).check(matches(withText("상세 3")))
    }

    /** 실제 손가락 탭으로 ViewEffects를 붙인 항목을 눌러도 클릭이 한 번씩만 간다. */
    @Test
    fun realTapsOnViewEffectsRow() {
        var x = 0
        var y = 0
        scenario.onActivity { activity ->
            val recycler = activity.findViewById<RecyclerView>(R.id.recycler)
            val row = recycler.findViewHolderForAdapterPosition(1)!!.itemView.findViewById<View>(R.id.title)
            val location = IntArray(2)
            row.getLocationOnScreen(location)
            x = location[0] + row.width / 2
            y = location[1] + row.height / 2
        }
        TestSupport.device.click(x, y)
        settle()
        var text = ""
        scenario.onActivity { text = it.findViewById<android.widget.TextView>(R.id.lastClicked).text.toString() }
        assertEquals("누른 항목: 1", text)
    }
}

internal fun swipeFromCenterUp() = androidx.test.espresso.action.GeneralSwipeAction(
    androidx.test.espresso.action.Swipe.FAST,
    androidx.test.espresso.action.GeneralLocation.CENTER,
    androidx.test.espresso.action.GeneralLocation.TOP_CENTER,
    androidx.test.espresso.action.Press.FINGER,
)

/** 항목 안의 자식 View를 누른다. 항목이 화면 끝에 걸쳐 있어도 performClick으로 누른다. */
internal fun clickChild(id: Int) = object : androidx.test.espresso.ViewAction {
    override fun getConstraints() = isDisplayed()
    override fun getDescription() = "항목 안 자식 누르기"
    override fun perform(uiController: androidx.test.espresso.UiController, view: View) {
        view.findViewById<View>(id).performClick()
    }
}

/** Espresso에서 View가 나타날 때까지 기다린다. Compose 안의 AndroidView는 붙는 데 프레임이 걸린다. */
internal fun waitView(matcher: org.hamcrest.Matcher<View>, timeoutMs: Long = 5_000) {
    val end = System.currentTimeMillis() + timeoutMs
    while (true) {
        try {
            onView(matcher).check(matches(isDisplayed()))
            return
        } catch (e: Throwable) {
            if (System.currentTimeMillis() > end) throw e
            Thread.sleep(200)
        }
    }
}

private fun withContentDescriptionOfRow(position: Int) = androidx.test.espresso.matcher.ViewMatchers.withContentDescription("행 $position")

private fun isInstance(type: Class<*>) = org.hamcrest.Matchers.instanceOf<View>(type)
