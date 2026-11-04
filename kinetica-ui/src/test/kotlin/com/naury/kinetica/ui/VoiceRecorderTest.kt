package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class VoiceRecorderTest {
    @get:Rule
    val compose = createComposeRule()

    private var now = 10_000L
    private val events = mutableListOf<String>()

    private fun setUpRecorder(state: KVoiceRecorderState, rtl: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Box(Modifier.fillMaxSize().padding(top = 200.dp), contentAlignment = Alignment.Center) {
                    KVoiceRecorderButton(
                        onStart = { events += "start" },
                        onSend = { events += "send:$it" },
                        onLock = { events += "lock" },
                        onCancel = { events += "cancel" },
                        state = state,
                        modifier = Modifier.testTag("mic"),
                    )
                }
            }
        }
    }

    @Test
    fun `TalkBack에서는 누르면 잠긴 녹음을 시작하고 다시 누르면 시간과 함께 보낸다`() {
        val state = KVoiceRecorderState { now }
        setUpRecorder(state)
        compose.onNodeWithContentDescription("Record voice message").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertThat(state.phase).isEqualTo(KVoiceRecorderPhase.Locked)
        assertThat(events).containsExactly("start", "lock").inOrder()

        now += 4_200
        compose.onNodeWithContentDescription("Send voice message")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Recording"))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertThat(events.last()).isEqualTo("send:4200")
        assertThat(state.isActive).isFalse()
    }

    @Test
    fun `취소 사용자 정의 동작은 녹음 중에만 있고 onCancel을 부른다`() {
        val state = KVoiceRecorderState { now }
        setUpRecorder(state)
        val idle = compose.onNodeWithContentDescription("Record voice message").fetchSemanticsNode()
        assertThat(idle.config.contains(SemanticsActions.CustomActions)).isFalse()
        compose.onNodeWithContentDescription("Record voice message").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        val action = compose.onNodeWithContentDescription("Send voice message").fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].single()
        assertThat(action.label).isEqualTo("Cancel recording")
        compose.runOnIdle { action.action() }
        assertThat(events).containsExactly("start", "lock", "cancel").inOrder()
    }

    @Test
    fun `길게 눌렀다 떼면 보내고 짧게 누르면 시작하지 않는다`() {
        val state = KVoiceRecorderState { now }
        setUpRecorder(state)
        compose.onNodeWithTag("mic").performTouchInput { click() }
        compose.waitForIdle()
        assertThat(events).isEmpty()
        assertThat(state.hint).isGreaterThan(0)

        compose.onNodeWithTag("mic").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 50)
            move()
        }
        compose.waitForIdle()
        assertThat(state.phase).isEqualTo(KVoiceRecorderPhase.Recording)
        now += 1_500
        compose.onNodeWithTag("mic").performTouchInput { up() }
        compose.waitForIdle()
        assertThat(events).containsExactly("start", "send:1500").inOrder()
    }

    @Test
    fun `위로 밀면 잠그고 시작 쪽으로 밀면 취소한다`() = slideAndLock(rtl = false)

    @Test
    fun `RTL에서는 오른쪽이 취소 쪽이다`() = slideAndLock(rtl = true)

    private fun slideAndLock(rtl: Boolean) {
        val state = KVoiceRecorderState { now }
        setUpRecorder(state, rtl)
        val node = compose.onNodeWithTag("mic")
        node.performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 50)
            moveBy(Offset(0f, -100.dp.toPx()))
            up()
        }
        compose.waitForIdle()
        assertThat(state.phase).isEqualTo(KVoiceRecorderPhase.Locked)
        assertThat(events).containsExactly("start", "lock").inOrder()
        compose.runOnIdle { state.cancel() }

        events.clear()
        val towardStart = if (rtl) 1f else -1f
        node.performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 50)
            // 끝 쪽으로 미는 것은 취소가 아니다.
            moveBy(Offset(-towardStart * 140.dp.toPx(), 0f))
            moveBy(Offset(towardStart * 280.dp.toPx(), 0f))
            up()
        }
        compose.waitForIdle()
        assertThat(events).containsExactly("start", "cancel").inOrder()
        assertThat(state.isActive).isFalse()
    }

    @Test
    fun `재생 파형은 값 범위를 내보내고 누른 자리로 옮긴다 RTL에서는 거꾸로 센다`() {
        var progress by mutableStateOf(0.3f)
        var seek = -1f
        var rtl by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                KWaveform(
                    FloatArray(64) { (it % 7) / 7f },
                    { progress },
                    Modifier.width(200.dp).height(32.dp).testTag("wave"),
                    onSeek = { seek = it },
                    label = "음성 메시지",
                    durationMillis = 10_000L,
                )
            }
        }
        compose.onNodeWithTag("wave")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0.3f, 0f..1f)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "00:03 / 00:10"))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.8f) }
        assertThat(seek).isEqualTo(0.8f)

        compose.onNodeWithTag("wave").performTouchInput { click(Offset(width * 0.25f, height / 2f)) }
        compose.waitForIdle()
        assertThat(seek).isWithin(0.02f).of(0.25f)

        rtl = true
        compose.waitForIdle()
        compose.onNodeWithTag("wave").performTouchInput { click(Offset(width * 0.25f, height / 2f)) }
        compose.waitForIdle()
        assertThat(seek).isWithin(0.02f).of(0.75f)
    }

    @Test
    fun `소리 막대 링버퍼는 빠진 표본을 채우고 오래 멈춰도 한 바퀴만 돈다`() {
        val ring = LevelRing()
        var level = 0.2f
        ring.advanceTo(10) { level }
        level = 0.9f
        ring.advanceTo(13) { level }
        assertThat(ring[0]).isEqualTo(0.9f)
        assertThat(ring[2]).isEqualTo(0.9f)
        assertThat(ring[3]).isEqualTo(0.2f)
        var calls = 0
        ring.advanceTo(1_000_000) {
            calls++
            0.5f
        }
        assertThat(calls).isEqualTo(1)
        assertThat(ring[255]).isEqualTo(0.5f)
    }

    @Test
    fun `카운트다운 캡슐은 이름과 남은 시간을 읽고 0이 되면 한 번 알린다`() {
        var finished = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CountdownPill(
                CountdownSpec(targetMillis = now + 2_000, totalMillis = 2_000, now = { now }),
                TimePillLook(KPillProgress.Ring, androidx.compose.ui.graphics.Color.Red, androidx.compose.ui.text.TextStyle.Default, null),
                "타이머",
                Modifier.size(80.dp, 24.dp),
            ) { finished++ }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("타이머, 00:02").assertExists()
        // 가짜 시계를 먼저 옮기고, 깨어날 시각을 조금 넘겨 다음 프레임에서 다시 구성되게 한다.
        now += 1_000
        compose.mainClock.advanceTimeBy(1_100)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("타이머, 00:01").assertExists()
        now += 1_000
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("타이머, 00:00").assertExists()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertThat(finished).isEqualTo(1)
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun `흐른 시간 글자는 초를 버린다`() {
        assertThat(elapsedText(0)).isEqualTo("00:00")
        assertThat(elapsedText(59_999)).isEqualTo("00:59")
        assertThat(elapsedText(3_600_000 + 61_000)).isEqualTo("01:01:01")
    }

    @Test
    fun `채팅 입력줄은 글자가 없을 때만 녹음 버튼 자리를 보인다`() {
        var draft by mutableStateOf("")
        compose.setContent {
            KChatInput(draft, { draft = it }, onSend = {}, voiceButton = { Box(Modifier.size(44.dp).testTag("voice")) })
        }
        compose.onNodeWithTag("voice").assertExists()
        draft = "안녕"
        compose.waitForIdle()
        compose.onNodeWithTag("voice").assertDoesNotExist()
        compose.onNodeWithContentDescription("Send").performClick()
    }
}
