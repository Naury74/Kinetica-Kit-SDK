package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko")
class KNotificationStackTest {
    @get:Rule
    val compose = createComposeRule()

    private fun sample() = mutableStateListOf(
        KNotification("a", "지수", "오늘 저녁 7시 어때?", "지금"),
        KNotification("b", "민호", "사진 보냈어", "2분 전"),
        KNotification("c", "엄마", "밥은 먹었니", "10분 전"),
    )

    private fun setStack(
        items: MutableList<KNotification>,
        state: KNotificationStackState? = null,
        reduced: Boolean = true,
        onClick: ((String) -> Unit)? = null,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalKineticaReducedMotion provides reduced) {
                KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                    Box(Modifier.width(360.dp)) {
                        KNotificationStack(
                            "메시지",
                            items,
                            onDismiss = { id -> items.removeAll { it.id == id } },
                            modifier = Modifier.testTag("stack"),
                            state = state ?: rememberKNotificationStackState(),
                            onDismissAll = { items.clear() },
                            onClick = onClick,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `접힌 묶음은 요약 하나로 읽히고 누르면 펼쳐 모든 알림을 보인다`() {
        val items = sample()
        setStack(items)
        compose.onNodeWithContentDescription("메시지 알림 3개", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("민호", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("메시지 알림 3개", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("민호", substring = true).assertIsDisplayed()
        compose.onNodeWithText("간단히").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("민호", substring = true).assertDoesNotExist()
    }

    @Test
    fun `접힌 카드의 누르기 이름은 펼치기이고 모두 지우기 동작이 있다`() {
        val items = sample()
        setStack(items)
        val front = compose.onNodeWithContentDescription("메시지 알림 3개", substring = true)
        front.assert(SemanticsMatcher("펼치기 이름") { it.config[SemanticsActions.OnClick].label == "펼치기" })
        val actions = front.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        actions.first { it.label == "모두 지우기" }.action()
        compose.waitForIdle()
        assertThat(items).isEmpty()
        compose.onNodeWithTag("stack").assertDoesNotExist()
    }

    @Test
    fun `펼친 목록은 목록 구조이고 카드마다 지우기 동작으로 그 알림만 지운다`() {
        val items = sample()
        setStack(items, state = KNotificationStackState(true))
        compose.onNodeWithTag("stack")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo))
        val card = compose.onNodeWithContentDescription("민호", substring = true)
        val actions = card.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        actions.first { it.label == "지우기" }.action()
        compose.waitForIdle()
        assertThat(items.map { it.id }).containsExactly("a", "c").inOrder()
    }

    @Test
    fun `펼친 카드를 끝까지 밀면 지우고 취소된 끌기는 지우지 않는다`() {
        val items = sample()
        setStack(items, state = KNotificationStackState(true), reduced = false)
        val card = compose.onNodeWithContentDescription("엄마", substring = true)
        card.performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(-width * 0.8f, 0f))
            cancel()
        }
        compose.mainClock.advanceTimeBy(1000)
        assertThat(items).hasSize(3)
        compose.onNodeWithContentDescription("엄마", substring = true).performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        assertThat(items.map { it.id }).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `알림이 하나로 줄면 저절로 접히고 누르면 onClick을 부른다`() {
        val items = mutableStateListOf(KNotification("z", "배달의 민족", "라이더가 출발했어요", "지금"))
        val state = KNotificationStackState(true)
        var clicked = ""
        setStack(items, state = state, onClick = { clicked = it })
        compose.waitForIdle()
        assertThat(state.expanded).isFalse()
        compose.onNodeWithText("간단히").assertDoesNotExist()
        compose.onNodeWithContentDescription("라이더가 출발", substring = true).performClick()
        assertThat(clicked).isEqualTo("z")
    }

    @Test
    fun `onDismissAll이 없으면 같은 목록에서 하나씩 지워도 크래시 없이 모두 지운다`() {
        val items = sample()
        compose.setContent {
            CompositionLocalProvider(LocalKineticaReducedMotion provides true) {
                KNotificationStack("메시지", items, onDismiss = { id -> items.removeAll { it.id == id } })
            }
        }
        val front = compose.onNodeWithContentDescription("메시지 알림 3개", substring = true)
        front.fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == "모두 지우기" }.action()
        compose.waitForIdle()
        assertThat(items).isEmpty()
    }

    @Test
    fun `빈 목록이면 아무것도 그리지 않는다`() {
        setStack(mutableStateListOf())
        compose.onNodeWithTag("stack").assertDoesNotExist()
    }

    @Test
    fun `id가 겹치면 거부한다`() {
        val duplicate = runCatching {
            compose.setContent {
                KNotificationStack(
                    "메시지",
                    listOf(KNotification("a", "1", "1", ""), KNotification("a", "2", "2", "")),
                    onDismiss = {},
                )
            }
        }
        assertThat(duplicate.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `TalkBack 펼치기 누르기로 펼친다`() {
        val items = sample()
        val state = KNotificationStackState(false)
        setStack(items, state = state)
        compose.onNodeWithContentDescription("메시지 알림 3개", substring = true).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertThat(state.expanded).isTrue()
    }

    @Test
    fun `넓은 카드에서도 시각은 카드 끝에 붙는다`() {
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                Box(Modifier.width(700.dp)) {
                    KNotificationStack("메시지", listOf(KNotification("a", "지수", "안녕", "지금")), onDismiss = {
                    }, modifier = Modifier.testTag("stack"))
                }
            }
        }
        val card = compose.onNodeWithTag("stack").fetchSemanticsNode().boundsInRoot
        val time = compose.onNodeWithText("지금", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        // 카드 안쪽 여백만큼만 떨어져 있어야 한다. 이름과 빈칸에 폭을 나눠 주면 시각이 카드 가운데쯤에 놓였다.
        assertThat(card.right - time.right).isLessThan(card.width * 0.15f)
    }
}
