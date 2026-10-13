package com.naury.kinetica.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.history.KineticaSession
import com.naury.kinetica.history.SessionOpenResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1000dp-h900dp-mdpi")
class TimelinePanelTest {
    @get:Rule
    val compose = createComposeRule()

    private val session = (KineticaSession.create(SceneDocument.empty(Artboard(1080.0, 1920.0))) as SessionOpenResult.Opened).session

    private fun keyTimes(): List<Long> {
        val document = session.document.value
        val id = document.rootOrder.single()
        return document.timelines.singleOrNull()?.track(id, AnimatableProperty.TranslationX)?.keyframes?.map { it.timeMs }.orEmpty()
    }

    @Test
    fun `키를 넣고 눈금자로 옮긴 뒤 캔버스에서 끌면 그 시각에 키가 생긴다`() {
        compose.setContent { KineticaEditor(session) }
        compose.onNodeWithText("Rectangle").performClick()
        compose.onNodeWithText("Motion").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Add key for X").performClick()
        compose.waitForIdle()
        assertThat(keyTimes()).containsExactly(0L)
        val before = session.document.value

        // 시작 여백 16dp + 1초 80dp, mdpi라 96px
        compose.onNodeWithTag(RULER_TAG).performTouchInput { click(Offset(96f, centerY)) }
        compose.waitForIdle()
        assertThat(session.document.value).isEqualTo(before)

        compose.onNodeWithTag(CANVAS_TAG).performTouchInput { swipe(center, center + Offset(60f, 0f), durationMillis = 300) }
        compose.waitForIdle()

        assertThat(keyTimes()).containsExactly(0L, 1_000L).inOrder()
        val node = session.document.value.node(session.document.value.rootOrder.single())!!
        assertThat(node.transform.translationX).isEqualTo(before.node(node.id)!!.transform.translationX)
    }
}
