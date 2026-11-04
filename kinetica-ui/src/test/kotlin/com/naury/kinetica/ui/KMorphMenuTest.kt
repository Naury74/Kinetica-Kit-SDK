package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KMorphMenuTest {
    @get:Rule
    val compose = createComposeRule()

    private fun entries(onPick: (String) -> Unit) = listOf(
        KMenuEntry.Action("최신순") { onPick("최신순") },
        KMenuEntry.Action("인기순") { onPick("인기순") },
    )

    @Test
    fun `칩을 누르면 메뉴가 펴지고 항목을 고르면 칩으로 접힌다`() {
        var open by mutableStateOf(false)
        var picked = ""
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                Box(Modifier.padding(40.dp)) {
                    KMorphMenu("정렬", open, { open = it }, entries { picked = it })
                }
            }
        }
        compose.onNodeWithText("정렬").performClick()
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("인기순").assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeBy(800)
        assertThat(picked).isEqualTo("인기순")
        assertThat(open).isFalse()
        compose.onNodeWithText("인기순").assertDoesNotExist()
        // 판이 사라진 뒤 원래 칩이 다시 보인다.
        compose.onNodeWithText("정렬").assertIsDisplayed()
    }

    @Test
    fun `스캐폴드 밖에서도 열리고 TalkBack 펼치기와 접기 동작으로 여닫는다`() {
        var open by mutableStateOf(false)
        compose.setContent {
            Box(Modifier.padding(20.dp)) { KMorphMenu("보기", open, { open = it }, entries {}) }
        }
        compose.onNodeWithText("보기").performSemanticsAction(SemanticsActions.Expand)
        compose.mainClock.advanceTimeBy(800)
        assertThat(open).isTrue()
        compose.onNodeWithText("최신순").assertIsDisplayed()
        compose.onNodeWithText("보기").performSemanticsAction(SemanticsActions.Collapse)
        compose.mainClock.advanceTimeBy(800)
        assertThat(open).isFalse()
        compose.onNodeWithText("최신순").assertDoesNotExist()
    }

    @Test
    fun `애니메이션 삭제면 모양 변화 없이 바로 열고 닫는다`() {
        var open by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalKineticaReducedMotion provides true) {
                KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                    KMorphMenu("정렬", open, { open = it }, entries {})
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        open = true
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(FEW_FRAMES)
        compose.onNodeWithText("최신순").assertIsDisplayed()
        open = false
        compose.waitForIdle()
        // 닫는 스프링이면 수백 ms 동안 남아 있는다.
        compose.mainClock.advanceTimeBy(FEW_FRAMES)
        compose.onNodeWithText("최신순").assertDoesNotExist()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `항목이 비어 있으면 거부한다`() {
        compose.setContent { KMorphMenu("정렬", false, {}, emptyList()) }
    }

    private companion object {
        const val FEW_FRAMES = 80L
    }
}
