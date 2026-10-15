package com.naury.kinetica.ui

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** XML 호스트 안에 넣었을 때 필요한 동작(View 부모 가로채기 막기)과 호스트용으로 더한 API를 확인한다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HostIntegration3Test {
    @get:Rule
    val compose = createComposeRule()

    /** requestDisallowInterceptTouchEvent 요청을 기록하는 View 부모. */
    private class RecordingParent(context: Context) : FrameLayout(context) {
        var held = false
        val calls = mutableListOf<Boolean>()

        override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            held = disallowIntercept
            calls += disallowIntercept
        }
    }

    private val parent = RecordingParent(ApplicationProvider.getApplicationContext())
    private val child = View(parent.context).also { parent.addView(it) }

    /** 컴포넌트가 보는 LocalView를 [RecordingParent] 안의 View로 바꿔 부모에게 간 요청을 기록한다. */
    @Composable
    private fun InRecordingParent(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalView provides child, content = content)
    }

    @Test
    fun `색상 바퀴를 끄는 동안 View 부모가 가로채지 못하고 손을 떼면 풀린다`() {
        compose.setContent {
            InRecordingParent { GlassColorPicker(androidx.compose.ui.graphics.Color.Blue, {}, Modifier.size(320.dp, 600.dp)) }
        }
        val wheel = compose.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription) and
                SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.CustomActions),
        )
        wheel.performTouchInput {
            down(center)
            moveBy(Offset(0f, 40f))
        }
        assertThat(parent.held).isTrue()
        wheel.performTouchInput { up() }
        assertThat(parent.held).isFalse()
    }

    @Test
    fun `비교 슬라이더는 가로 끌기만 붙잡고 세로 끌기는 바깥 스크롤에 넘긴다`() {
        compose.setContent {
            InRecordingParent {
                BeforeAfterSlider(before = {
                    Box(Modifier.size(300.dp))
                }, after = { Box(Modifier.size(300.dp)) }, modifier = Modifier.testTag("compare"))
            }
        }
        val slider = compose.onNodeWithTag("compare")
        slider.performTouchInput {
            down(center)
            moveBy(Offset(60f, 0f))
        }
        assertThat(parent.held).isTrue()
        slider.performTouchInput { up() }
        assertThat(parent.held).isFalse()

        slider.performTouchInput {
            down(center)
            moveBy(Offset(0f, 60f))
        }
        assertThat(parent.held).isFalse()
        slider.performTouchInput { up() }
    }

    @Test
    fun `뒤집기 카드를 끄는 동안 View 부모가 가로채지 못한다`() {
        compose.setContent {
            InRecordingParent {
                FlipCard(false, {}, Modifier.size(200.dp, 120.dp).testTag("card"), front = { Box(Modifier.size(200.dp)) }, back = {})
            }
        }
        compose.onNodeWithTag("card").performTouchInput {
            down(center)
            moveBy(Offset(-50f, 0f))
        }
        assertThat(parent.held).isTrue()
        compose.onNodeWithTag("card").performTouchInput { up() }
        assertThat(parent.held).isFalse()
    }

    @Test
    fun `위치를 호스트가 들고 있는 비교 슬라이더는 바뀐 위치를 알리고 호스트 값을 따른다`() {
        var position by mutableFloatStateOf(0.5f)
        val reported = mutableListOf<Float>()
        compose.setContent {
            BeforeAfterSlider(
                position,
                {
                    reported += it
                    position = it
                },
                before = { Box(Modifier.size(300.dp)) },
                after = { Box(Modifier.size(300.dp)) },
                modifier = Modifier.testTag("compare"),
            )
        }
        val slider = compose.onNodeWithTag("compare")
        slider.performTouchInput {
            down(Offset(width * 0.5f, centerY))
            moveTo(Offset(width * 0.6f, centerY))
            moveTo(Offset(width * 0.9f, centerY))
            up()
        }
        compose.waitForIdle()
        assertThat(reported).isNotEmpty()
        assertThat(position).isWithin(0.02f).of(0.9f)

        compose.runOnIdle { position = 0.2f }
        slider.assert(SemanticsMatcher("위치 0.2") { it.config[SemanticsProperties.ProgressBarRangeInfo].current == 0.2f })
    }

    @Test
    fun `태그 입력은 글자가 있으면 칩을 만들고 빈 입력에서 동작 키를 누르면 호스트 콜백으로 넘긴다`() {
        var tags by mutableStateOf(listOf<String>())
        var nexts = 0
        compose.setContent {
            GlassTagInput(
                tags,
                { tags = it },
                imeAction = ImeAction.Next,
                keyboardActions = KeyboardActions(onNext = { nexts++ }),
                fieldModifier = Modifier.testTag("tagField"),
            )
        }
        val field = compose.onNodeWithTag("tagField")
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.ImeAction, ImeAction.Next))
        field.performClick()
        field.performTextInput("kotlin")
        field.performImeAction()
        assertThat(tags).containsExactly("kotlin")
        assertThat(nexts).isEqualTo(0)
        field.assertIsFocused()
        field.performImeAction()
        assertThat(nexts).isEqualTo(1)
    }

    @Test
    fun `비밀번호와 코드 입력란은 IME 동작을 바꾸고 안쪽 입력란에 Modifier를 붙일 수 있다`() {
        var nexts = 0
        var codeNexts = 0
        compose.setContent {
            Column(Modifier.fillMaxWidth()) {
                GlassPasswordField(
                    "",
                    {},
                    label = "비밀번호",
                    modifier = Modifier.testTag("outer"),
                    imeAction = ImeAction.Next,
                    keyboardActions = KeyboardActions(onNext = { nexts++ }),
                    fieldModifier = Modifier.testTag("pwField"),
                )
                GlassCodeInput(
                    "",
                    {},
                    Modifier.testTag("code"),
                    imeAction = ImeAction.Next,
                    keyboardActions = KeyboardActions(onNext = { codeNexts++ }),
                )
            }
        }
        val pw = compose.onNodeWithTag("pwField")
        pw.assert(hasSetTextAction())
        compose.onNodeWithTag("outer").assert(SemanticsMatcher("바깥 묶음은 입력란이 아니다") { !hasSetTextAction().matches(it) })
        pw.assert(SemanticsMatcher.expectValue(SemanticsProperties.ImeAction, ImeAction.Next))
        pw.performClick()
        pw.performImeAction()
        assertThat(nexts).isEqualTo(1)

        val code = compose.onNodeWithTag("code")
        code.assert(SemanticsMatcher.expectValue(SemanticsProperties.ImeAction, ImeAction.Next))
        code.performClick()
        code.performImeAction()
        assertThat(codeNexts).isEqualTo(1)
        compose.onNode(hasTestTag("pwField")).assert(hasSetTextAction())
    }
}
