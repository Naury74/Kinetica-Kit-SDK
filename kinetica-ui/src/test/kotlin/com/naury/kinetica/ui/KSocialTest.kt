package com.naury.kinetica.ui

import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class KSocialTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `묶음 위치는 앞뒤 메시지가 같은 사람인지로 정한다`() {
        assertThat(chatGroupPosition(sameAsPrevious = false, sameAsNext = false)).isEqualTo(KChatGroupPosition.Single)
        assertThat(chatGroupPosition(sameAsPrevious = false, sameAsNext = true)).isEqualTo(KChatGroupPosition.First)
        assertThat(chatGroupPosition(sameAsPrevious = true, sameAsNext = true)).isEqualTo(KChatGroupPosition.Middle)
        assertThat(chatGroupPosition(sameAsPrevious = true, sameAsNext = false)).isEqualTo(KChatGroupPosition.Last)
    }

    @Test
    fun `큰 글자에서도 말풍선은 최대 폭 비율을 넘지 않고 RTL에서는 보낸 말풍선이 왼쪽에 붙는다`() {
        var rtl by mutableStateOf(false)
        val long = "아주 긴 메시지입니다. ".repeat(12)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, 2f),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                Box(Modifier.width(300.dp)) {
                    KChatBubble(long, KChatSide.Sent, time = "1:02", status = KChatStatus.Read, surface = SurfaceStyles.CleanLight)
                    Box(Modifier.testTag("probe"))
                }
            }
        }
        // 말풍선 폭이 300dp의 78%를 넘지 않으므로 글자는 끝 쪽 234dp 안에 놓인다.
        val bubble = compose.onNodeWithText(long, substring = true, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertThat(bubble.left).isAtLeast(300.dp - 300.dp * KChatBubbleDefaults.MaxWidthFraction)
        rtl = true
        compose.waitForIdle()
        assertThat(compose.onNodeWithText(long, substring = true, useUnmergedTree = true).getUnclippedBoundsInRoot().right)
            .isAtMost(300.dp * KChatBubbleDefaults.MaxWidthFraction)
    }

    @Test
    fun `말풍선은 전송 상태를 읽고 길게 누르기를 동작으로 제공한다`() {
        var menu = 0
        compose.setContent {
            KChatBubble("안녕", KChatSide.Sent, Modifier.testTag("b"), status = KChatStatus.Read, onLongClick = { menu++ })
        }
        compose.onNodeWithText("안녕").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Read"))
        compose.onNodeWithText("안녕").performCustomAccessibilityActionWithLabel("More options")
        assertThat(menu).isEqualTo(1)
    }

    @Test
    fun `입력 중 표시는 이름으로 읽힌다`() {
        compose.setContent { KTypingIndicator() }
        compose.onNodeWithContentDescription("Typing").assertExists()
    }

    @Test
    fun `채팅 입력줄은 글자가 있을 때만 보내고 없으면 음성 버튼이다`() {
        var text by mutableStateOf("")
        var sent = 0
        var voice = 0
        compose.setContent { KChatInput(text, { text = it }, { sent++ }, onAttach = {}, onVoice = { voice++ }) }
        compose.onNodeWithContentDescription("Voice message").performClick()
        assertThat(voice).isEqualTo(1)
        text = "안녕"
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Send").performClick()
        assertThat(sent).isEqualTo(1)
        compose.onNodeWithText("안녕").performClick().performKeyInput { pressKey(Key.Enter) }
        assertThat(sent).isEqualTo(2)
    }

    @Test
    fun `음성 버튼이 없으면 빈 입력줄의 보내기는 눌리지 않는다`() {
        compose.setContent { KChatInput("", {}, {}) }
        compose.onNodeWithContentDescription("Send").assertIsNotEnabled()
    }

    @Test
    fun `스토리 뷰어는 시간이 다 되면 넘기고 마지막 페이지가 끝나면 닫는다`() {
        var open by mutableStateOf(true)
        lateinit var state: KStoryViewerState
        compose.mainClock.autoAdvance = false
        compose.setContent {
            state = rememberKStoryViewerState(2)
            KStoryViewer(open, { open = false }, state, Modifier.testTag("story"), durationMs = 1_000) { Box(Modifier.fillMaxSize()) }
        }
        compose.mainClock.advanceTimeBy(1_300)
        assertThat(state.page).isEqualTo(1)
        compose.mainClock.advanceTimeBy(1_300)
        assertThat(open).isFalse()
    }

    @Test
    fun `스토리 뷰어는 끝 쪽을 누르면 다음, 시작 쪽을 누르면 이전으로 가고 동작을 제공한다`() {
        lateinit var state: KStoryViewerState
        // 진행 막대가 프레임마다 시간을 더하므로 시계를 직접 돌린다. 그러지 않으면 기다리는 동안 페이지가 넘어간다.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            state = rememberKStoryViewerState(3)
            KStoryViewer(true, {}, state, Modifier.testTag("story"), durationMs = 60_000) { Box(Modifier.fillMaxSize()) }
        }
        compose.onNodeWithTag("story").performTouchInput { click(Offset(width * 0.8f, height / 2f)) }
        compose.mainClock.advanceTimeBy(100)
        assertThat(state.page).isEqualTo(1)
        compose.onNodeWithTag("story").performTouchInput { click(Offset(width * 0.1f, height / 2f)) }
        compose.mainClock.advanceTimeBy(100)
        assertThat(state.page).isEqualTo(0)
        compose.onNodeWithTag("story")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Story 1 of 3"))
            .performCustomAccessibilityActionWithLabel("Next story")
        assertThat(state.page).isEqualTo(1)
        compose.onNodeWithTag("story").performCustomAccessibilityActionWithLabel("Pause")
        assertThat(state.paused).isTrue()
    }

    @Test
    fun `스토리 링은 본 상태를 읽고 누를 수 있다`() {
        var opened = 0
        compose.setContent { KStoryRing(seen = false, Modifier.testTag("ring"), onClick = { opened++ }) { KAvatar(KAvatar("민지")) } }
        compose.onNodeWithTag("ring").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "New story")).performClick()
        assertThat(opened).isEqualTo(1)
    }

    @Test
    fun `첨부 칩은 진행률을 알리고 상태마다 끝 버튼이 바뀐다`() {
        var status by mutableStateOf(KAttachmentStatus.Uploading)
        var removed = 0
        var retried = 0
        compose.setContent {
            KAttachmentChip("보고서.pdf", Modifier.testTag("chip"), detail = "2MB", status = status, progress = 0.4f, onRemove = {
                removed++
            }, onRetry = { retried++ })
        }
        compose.onNodeWithTag("chip").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(
                    0.4f,
                    0f..1f,
                ),
            ),
        )
        compose.onNodeWithContentDescription("Cancel upload").performClick()
        assertThat(removed).isEqualTo(1)
        status = KAttachmentStatus.Failed
        compose.waitForIdle()
        compose.onNodeWithText("Upload failed", substring = true).assertExists()
        compose.onNodeWithContentDescription("Retry").performClick()
        assertThat(retried).isEqualTo(1)
    }

    @Test
    fun `파일 영역은 누르면 고르기를 연다`() {
        var picks = 0
        compose.setContent { KFileDropZone({ picks++ }, subtitle = "PDF") }
        compose.onNodeWithText("Add files").performClick()
        assertThat(picks).isEqualTo(1)
    }

    @Test
    fun `색인 글자는 한글 초성과 로마자 대문자로 정한다`() {
        assertThat(KIndexBarDefaults.keyOf("김민지")).isEqualTo("ㄱ")
        assertThat(KIndexBarDefaults.keyOf("쌍둥이")).isEqualTo("ㅅ")
        assertThat(KIndexBarDefaults.keyOf("하늘")).isEqualTo("ㅎ")
        assertThat(KIndexBarDefaults.keyOf("anna")).isEqualTo("A")
        assertThat(KIndexBarDefaults.keyOf("7번")).isEqualTo("#")
        assertThat(KIndexBarDefaults.keyOf("")).isEqualTo("#")
    }

    @Test
    fun `자리가 모자라면 첫 글자와 끝 글자를 남기고 사이에 점을 찍는다`() {
        val slots = indexSlots(27, 9)
        assertThat(slots).hasSize(9)
        assertThat(slots.first()).isEqualTo(0)
        assertThat(slots.last()).isEqualTo(26)
        assertThat(slots.filter { it == null }).hasSize(4)
        assertThat(indexSlots(5, 9)).containsExactly(0, 1, 2, 3, 4).inOrder()
    }

    @Test
    fun `색인 막대를 끌면 손가락 아래 글자를 고르고 동작으로도 고를 수 있다`() {
        val picked = mutableListOf<Int>()
        compose.setContent { Box(Modifier.fillMaxSize()) { KIndexBar(KIndexBarDefaults.Latin, { picked += it }, Modifier.testTag("bar")) } }
        compose.onNodeWithContentDescription("Index").performTouchInput {
            down(Offset(centerX, 2f))
            moveTo(Offset(centerX, height - 2f))
            up()
        }
        assertThat(picked.first()).isEqualTo(0)
        assertThat(picked.last()).isEqualTo(26)
        compose.onNodeWithContentDescription("Index").performCustomAccessibilityActionWithLabel("Jump to M")
        assertThat(picked.last()).isEqualTo(12)
    }

    @Test
    fun `페이지 칸 수는 페이지를 넘겨도 같다`() {
        val sizes = (0 until 30).map { pageSlots(it, 30, 1).size }.toSet()
        assertThat(sizes).containsExactly(7)
        assertThat(pageSlots(15, 30, 1)).containsExactly(0, null, 14, 15, 16, null, 29).inOrder()
        assertThat(pageSlots(2, 4, 1)).containsExactly(0, 1, 2, 3).inOrder()
    }

    @Test
    fun `페이지 이동은 다음 화살표와 숫자로 바꾸고 고른 칸을 선택 상태로 둔다`() {
        var page by mutableIntStateOf(0)
        compose.setContent { KPagination(page, 10, { page = it }) }
        compose.onNodeWithContentDescription("Previous page").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Next page").performClick()
        assertThat(page).isEqualTo(1)
        compose.onNodeWithContentDescription("Page 2").assertIsSelected()
        compose.onNodeWithContentDescription("Page 3").performClick()
        assertThat(page).isEqualTo(2)
    }

    @Test
    fun `이동 경로는 사이를 접었다가 펼치고 지금 위치는 누를 수 없다`() {
        val items = listOf("홈", "문서", "디자인", "2026", "아이콘", "최종")
        var clicked = -1
        compose.setContent { KBreadcrumb(items, { clicked = it }) }
        compose.onNodeWithText("디자인").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show full path").performClick()
        compose.onNodeWithText("디자인").performClick()
        assertThat(clicked).isEqualTo(2)
        compose.onNodeWithText("최종")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Current location"))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }

    @Test
    fun `묶음 핀은 장소 수를 읽고 고른 핀은 선택 상태다`() {
        var picked = 0
        compose.setContent {
            Column {
                KMapPin(count = 12, onClick = { picked++ })
                KMapPin(label = "₩82,000", selected = true)
            }
        }
        compose.onNodeWithContentDescription("12 places").performClick()
        assertThat(picked).isEqualTo(1)
        compose.onNodeWithContentDescription("₩82,000").assertIsSelected()
    }

    @Test
    fun `애니메이션 삭제 설정에서는 핀이 떨어지지 않고 바로 놓인다`() {
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            compose.mainClock.autoAdvance = false
            compose.setContent { Box(Modifier.testTag("box")) { KMapPin(Modifier.testTag("pin")) } }
            compose.mainClock.advanceTimeBy(16)
            val map = compose.onNodeWithTag("box").captureToImage().toPixelMap()
            assertThat(map[map.width / 2, map.height / 3].alpha).isGreaterThan(0.9f)
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
    }

    @Test
    fun `단색 표면에서는 소셜 컴포넌트가 배경을 기록하지 않는다`() {
        var backdrop: GlassBackdrop? = null
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                backdrop = LocalGlassBackdrop.current
                Column {
                    KChatBubble("안녕", KChatSide.Received)
                    KTypingIndicator()
                    KChatInput("", {}, {}, onVoice = {})
                    KAttachmentChip("a.pdf")
                    KMapPin(label = "1")
                }
            }
        }
        compose.waitForIdle()
        assertThat(backdrop).isNull()
    }
}
