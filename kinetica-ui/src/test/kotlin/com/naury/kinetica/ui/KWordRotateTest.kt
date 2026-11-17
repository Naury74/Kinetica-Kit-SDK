package com.naury.kinetica.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KWordRotateTest {
    @get:Rule
    val compose = createComposeRule()

    private val words = listOf("산책", "독서", "캠핑")

    @Test
    fun `TalkBack은 모든 단어를 한 번에 읽고 live region은 없다`() {
        compose.setContent {
            KWordRotate(words, Modifier.testTag("rotate"), prefix = "오늘은", suffix = "하기 좋은 날")
        }
        compose.onNodeWithTag("rotate")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("오늘은 산책, 독서, 캠핑 하기 좋은 날")))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
        // 단어 글은 겹쳐 그려도 의미 트리에는 하나로 묶인다.
        compose.onAllNodesWithText("독서").assertCountEquals(0)
    }

    @Test
    fun `시간이 지나면 단어 자리 폭이 다음 단어 쪽으로 바뀐다`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            KWordRotate(listOf("go", "a much longer word"), Modifier.testTag("rotate"), interval = 1000)
        }
        compose.mainClock.advanceTimeBy(100)
        val before = compose.onNodeWithTag("rotate").getUnclippedBoundsInRoot().width
        compose.mainClock.advanceTimeBy(1100)
        val after = compose.onNodeWithTag("rotate").getUnclippedBoundsInRoot().width
        assertThat(after).isGreaterThan(before)
    }

    @Test
    fun `애니메이션 삭제면 첫 단어에 멈춘다`() {
        compose.setContent {
            CompositionLocalProvider(LocalKineticaReducedMotion provides true) {
                KWordRotate(listOf("go", "a much longer word"), Modifier.testTag("rotate"), interval = 1000)
            }
        }
        compose.waitForIdle()
        val before = compose.onNodeWithTag("rotate").getUnclippedBoundsInRoot().width
        compose.mainClock.advanceTimeBy(3000)
        compose.waitForIdle()
        assertThat(compose.onNodeWithTag("rotate").getUnclippedBoundsInRoot().width).isEqualTo(before)
    }

    @Test
    fun `단어 하나와 빈 앞뒤 글도 그린다`() {
        compose.setContent { KWordRotate(listOf("one"), Modifier.testTag("rotate")) }
        compose.mainClock.advanceTimeBy(5000)
        compose.onNodeWithTag("rotate").assertWidthIsAtLeast(1.dp)
    }

    @Test
    fun `빈 목록은 거부한다`() {
        val empty = runCatching { compose.setContent { KWordRotate(emptyList()) } }
        assertThat(empty.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }
}
