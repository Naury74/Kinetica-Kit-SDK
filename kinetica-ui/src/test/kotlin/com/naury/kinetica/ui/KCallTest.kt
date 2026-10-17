package com.naury.kinetica.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ko-w400dp-h860dp")
class KCallTest {
    @get:Rule
    val compose = createComposeRule()

    private fun call(count: Int, video: Boolean = true, phase: KCallPhase = KCallPhase.Connected) = KCallState(
        phase = phase,
        participants = (0 until count).map {
            KCallParticipant("p$it", "참가자$it", isLocal = it == 0, isMuted = it == 2, isSpeaking = it == 1)
        },
        isVideoCall = video,
        startedAtMillis = SystemClock.elapsedRealtime(),
    )

    private fun hasState(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    @Test
    fun `조작 버튼은 이름과 상태를 하나로 읽고 누르면 콜백을 부른다`() {
        var muted by mutableStateOf(false)
        compose.setContent {
            KCallControlButton(KIcons.Mic, "마이크", { muted = !muted }, checked = muted, checkedIcon = KIcons.MicOff, badge = 2)
        }
        compose.onNodeWithContentDescription("마이크").assert(hasState("꺼짐, 새 항목 2개")).performClick()
        assertThat(muted).isTrue()
        compose.onNodeWithContentDescription("마이크").assert(hasState("켜짐, 새 항목 2개"))
    }

    @Test
    fun `좁은 도크는 더보기로 접고 배지를 합치며 기본 시트에서 접힌 버튼을 누를 수 있다`() {
        var shared = false
        compose.setContent {
            KCallDock(
                listOf(
                    KCallDockItem("mic", KIcons.Mic, "마이크", {}, essential = true),
                    KCallDockItem("chat", KIcons.Message, "채팅", {}, badge = 4),
                    KCallDockItem("share", KCallIcons.ScreenShare, "화면 공유", { shared = true }, badge = 1),
                    KCallDockItem("end", KIcons.CallEnd, "종료", {}, tone = KTone.Destructive, essential = true),
                ),
                Modifier.width(200.dp),
            )
        }
        compose.onAllNodesWithContentDescription("화면 공유").assertCountEquals(0)
        compose.onNodeWithContentDescription("더보기").assert(hasState("새 항목 5개")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("화면 공유").performClick()
        assertThat(shared).isTrue()
    }

    @Test
    fun `도크는 버튼 위치를 키로 알리고 접힌 버튼은 더보기 위치로 알린다`() {
        val bounds = HashMap<String, androidx.compose.ui.geometry.Rect>()
        compose.setContent {
            KCallDock(
                listOf(
                    KCallDockItem("mic", KIcons.Mic, "마이크", {}, essential = true),
                    KCallDockItem("a", KIcons.Speaker, "오디오", {}),
                    KCallDockItem("b", KIcons.Message, "채팅", {}),
                    KCallDockItem("end", KIcons.CallEnd, "종료", {}, tone = KTone.Destructive, essential = true),
                ),
                Modifier.width(200.dp),
                onItemPositioned = { key, rect -> bounds[key] = rect },
            )
        }
        compose.waitForIdle()
        assertThat(bounds.keys).containsAtLeast("mic", "a", "b", "end")
        assertThat(bounds["a"]).isEqualTo(bounds["b"])
        assertThat(bounds["a"]).isNotEqualTo(bounds["mic"])
    }

    @Test
    fun `타일은 상태를 한 번에 읽고 고정은 사용자 지정 동작이다`() {
        var pinned = false
        var tapped = false
        compose.setContent {
            Box(Modifier.size(200.dp)) {
                KParticipantTile(
                    "민지",
                    muted = true,
                    speaking = true,
                    handRaised = true,
                    quality = KNetworkQuality.Poor,
                    onClick = { tapped = true },
                    onClickLabel = "크게 보기",
                    onTogglePin = { pinned = true },
                )
            }
        }
        val tile = compose.onNodeWithContentDescription("민지, 손 듦, 말하는 중, 마이크 꺼짐, 카메라 꺼짐, 연결 약함")
        tile.assert(SemanticsMatcher("클릭 이름") { it.config[SemanticsActions.OnClick].label == "크게 보기" })
        tile.performCustomAccessibilityActionWithLabel("고정")
        assertThat(pinned).isTrue()
        tile.performClick()
        assertThat(tapped).isTrue()
    }

    @Test
    fun `완성 화면은 넘기지 않은 콜백의 버튼을 감추고 끊기를 부른다`() {
        var ended = false
        compose.setContent {
            KCallScreen(call(3), KCallActions(onEnd = { ended = true }, onToggleMute = {}), autoHideControls = false, island = null)
        }
        compose.onNodeWithContentDescription("마이크").assertExists()
        compose.onAllNodesWithContentDescription("화면 공유").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("카메라").assertCountEquals(0)
        compose.onNodeWithContentDescription("종료").performClick()
        assertThat(ended).isTrue()
    }

    @Test
    fun `영상 통화는 4초 동안 만지지 않으면 막대를 숨기고 숨은 동안 타일을 누르면 막대가 먼저 나온다`() {
        compose.mainClock.autoAdvance = false
        compose.setContent { KCallScreen(call(3), KCallActions(onEnd = {}), island = null) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription("종료").assertExists()
        compose.mainClock.advanceTimeBy(5_000)
        compose.onAllNodesWithContentDescription("종료").assertCountEquals(0)
        // 숨은 동안 타일의 동작 이름은 '조작 버튼 보이기'다.
        val tile = compose.onNodeWithContentDescription("참가자1, 말하는 중")
        tile.assert(SemanticsMatcher("동작 이름") { it.config[SemanticsActions.OnClick].label == "조작 버튼 보이기" })
        tile.performClick()
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithContentDescription("종료").assertExists()
        // 막대를 꺼내기만 하고 고정하지는 않는다.
        compose.onAllNodesWithContentDescription("고정됨", substring = true).assertCountEquals(0)
    }

    @Test
    fun `음성 통화와 화면 공유 중에는 막대를 숨기지 않는다`() {
        compose.mainClock.autoAdvance = false
        var state by mutableStateOf(call(3, video = false))
        compose.setContent { KCallScreen(state, KCallActions(onEnd = {}), island = null) }
        compose.mainClock.advanceTimeBy(6_000)
        compose.onNodeWithContentDescription("종료").assertExists()
        state = call(3).copy(isSharingScreen = true)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(6_000)
        compose.onNodeWithContentDescription("종료").assertExists()
    }

    @Test
    fun `상태 화면은 단계에 맞는 버튼을 두고 단계 이름을 라이브 영역으로 알린다`() {
        var retried = false
        var state by mutableStateOf(call(2, phase = KCallPhase.Connecting))
        compose.setContent { KCallScreen(state, KCallActions(onEnd = {}, onRetry = { retried = true }, onClose = {}), island = null) }
        compose.onNodeWithContentDescription("취소").assertExists()
        compose.onNodeWithContentDescription("연결 중…").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        state = state.copy(phase = KCallPhase.Failed, errorCode = "E-1")
        compose.waitForIdle()
        compose.onNodeWithText("오류 코드 E-1").assertExists()
        compose.onNodeWithText("다시 시도").performClick()
        assertThat(retried).isTrue()
        state = state.copy(phase = KCallPhase.Connected)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("연결됨").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
    }

    @Test
    fun `수신 화면은 받기와 거절, 메시지 답장을 부른다`() {
        var accepted = ""
        var reply = ""
        compose.setContent {
            KIncomingCallScreen(
                "김민지",
                onAccept = { accepted = "voice" },
                onDecline = { accepted = "decline" },
                isVideo = true,
                onAcceptVideo = { accepted = "video" },
                onMessage = { reply = it },
                quickReplies = listOf("곧 다시 걸게요"),
            )
        }
        compose.onNodeWithContentDescription("영상").performClick()
        assertThat(accepted).isEqualTo("video")
        compose.onNodeWithContentDescription("거절").performClick()
        assertThat(accepted).isEqualTo("decline")
        compose.onNodeWithContentDescription("메시지").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("곧 다시 걸게요").performClick()
        assertThat(reply).isEqualTo("곧 다시 걸게요")
    }

    @Test
    fun `글자가 두 배여도 상단 바 끝 버튼은 화면 안에 남는다`() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Box(Modifier.width(360.dp)) {
                    KCallTopBar(
                        title = "아주 긴 회의 이름이 들어가는 주간 디자인 싱크 회의",
                        startedAtMillis = SystemClock.elapsedRealtime(),
                        participantCount = 12,
                        actions = { KIconButton(KIcons.Users, "참가자", {}, Modifier.testTag("people")) },
                    )
                }
            }
        }
        assertThat(compose.onNodeWithTag("people").getUnclippedBoundsInRoot().right).isAtMost(360.dp)
        compose.onNodeWithContentDescription("12명 참가 중").assertExists()
    }

    @Test
    fun `말하는 사람은 정해진 시간 넘게 말해야 바뀐다`() {
        compose.mainClock.autoAdvance = false
        var talking by mutableStateOf<String?>("a")
        var stable: String? = null
        compose.setContent { stable = rememberKStableSpeaker(talking, delayMillis = 1_500) }
        compose.mainClock.advanceTimeBy(100)
        assertThat(stable).isEqualTo("a")
        talking = "b"
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        assertThat(stable).isEqualTo("a")
        talking = "a"
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        assertThat(stable).isEqualTo("a")
        talking = "b"
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_700)
        assertThat(stable).isEqualTo("b")
    }

    @Test
    fun `반응은 한 번에 여덟 개까지 띄우고 시간이 지나면 사라지며 마지막 반응을 읽어 준다`() {
        compose.mainClock.autoAdvance = false
        val reactions = KReactionState(kotlin.random.Random(1))
        compose.setContent { KReactionOverlay(reactions) }
        repeat(10) { reactions.send("👍", "민지") }
        compose.waitForIdle()
        assertThat(reactions.items).hasSize(KReactionState.MAX_FLOATING)
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithContentDescription("민지 님의 반응 👍").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        compose.mainClock.advanceTimeBy(3_000)
        assertThat(reactions.items).isEmpty()
    }

    @Test
    fun `내 작은 창은 사용자 지정 동작으로 다른 모서리로 옮긴다`() {
        val state = KSelfViewState(KCorner.BottomEnd)
        compose.setContent { Box(Modifier.size(300.dp)) { KSelfView(state = state) {} } }
        compose.onNodeWithContentDescription("내 화면").performCustomAccessibilityActionWithLabel("다른 모서리로 옮기기")
        assertThat(state.corner).isEqualTo(KCorner.BottomStart)
    }

    @Test
    fun `격자의 1대1 작은 창을 끌어 놓으면 가까운 모서리에 붙는다`() {
        val grid = KParticipantGridState(KCorner.BottomEnd)
        compose.setContent {
            Box(Modifier.size(400.dp, 800.dp)) {
                KParticipantGrid(listOf("me", "you"), key = { it }, state = grid, selfKey = "me") { p, role ->
                    Box(Modifier.fillMaxSize().testTag("$p-$role"))
                }
            }
        }
        compose.onNodeWithTag("me-Floating").performTouchInput {
            down(center)
            repeat(20) { moveBy(androidx.compose.ui.geometry.Offset(-15f * density, -30f * density), delayMillis = 16) }
            up()
        }
        compose.waitForIdle()
        assertThat(grid.selfCorner).isEqualTo(KCorner.TopStart)
    }

    @Test
    fun `오디오 출력 목록은 고른 경로를 라디오로 표시하고 누르면 알린다`() {
        var picked = ""
        compose.setContent {
            KAudioRouteList(
                listOf(
                    KAudioRoute("s", "스피커", KAudioRouteType.Speaker),
                    KAudioRoute("b", "Buds", KAudioRouteType.Bluetooth, enabled = false, disabledReason = "연결 안 됨"),
                ),
                selectedId = "s",
                onSelect = { picked = it },
            )
        }
        compose.onNodeWithText("연결 안 됨").assertExists()
        compose.onNodeWithText("스피커", useUnmergedTree = true).assertExists()
        compose.onRoot().assertExists()
        compose.onNodeWithText("Buds").performClick()
        assertThat(picked).isEmpty()
    }
}
