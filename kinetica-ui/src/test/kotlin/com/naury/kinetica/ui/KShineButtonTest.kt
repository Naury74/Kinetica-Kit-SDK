package com.naury.kinetica.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KShineButtonTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `호스트 testTag가 누르기 노드에 붙고 누르면 onClick을 부른다`() {
        var clicks = 0
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                KShineButton("시작하기", onClick = { clicks++ }, modifier = Modifier.testTag("go"))
            }
        }
        compose.onNodeWithTag("go")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `불러오는 중이면 누를 수 없고 상태를 알린다`() {
        compose.setContent { KShineButton("결제하기", onClick = {}, modifier = Modifier.testTag("pay"), loading = true) }
        compose.onNodeWithTag("pay")
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))
    }

    @Test
    fun `애니메이션 삭제에서도 그리고 눌렀다 취소해도 누르기가 불리지 않는다`() {
        var clicks = 0
        compose.setContent {
            CompositionLocalProvider(LocalKineticaReducedMotion provides true) {
                KShineButton("시작하기", onClick = { clicks++ }, modifier = Modifier.testTag("go"))
            }
        }
        compose.onNodeWithTag("go").performTouchInput {
            down(center)
            cancel()
        }
        compose.mainClock.advanceTimeBy(500)
        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun `주기가 너무 짧으면 거부한다`() {
        val result = runCatching { compose.setContent { KShineButton("x", onClick = {}, period = 100) } }
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `빛이 도는 동안 시간을 흘려도 크래시 없이 그린다`() {
        compose.setContent { KShineButton("시작하기", onClick = {}, modifier = Modifier.testTag("go")) }
        compose.mainClock.advanceTimeBy(5000)
        compose.onNodeWithTag("go").assertHasClickAction()
    }
}
