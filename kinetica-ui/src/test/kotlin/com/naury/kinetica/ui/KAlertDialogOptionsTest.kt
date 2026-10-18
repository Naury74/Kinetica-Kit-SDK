package com.naury.kinetica.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-mdpi")
class KAlertDialogOptionsTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = listOf(
        KDialogAction("취소", KDialogActionStyle.Cancel),
        KDialogAction("확인", KDialogActionStyle.Preferred) {},
    )

    @Test
    fun `content 칸은 본문과 버튼 사이에 보인다`() {
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                KAlertDialog(true, {}, "비율 직접 입력", actions, message = "가로와 세로를 넣습니다.") {
                    BasicText("입력 칸", Modifier.testTag("field"))
                }
            }
        }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("field").assertIsDisplayed()
        val message = compose.onNodeWithText("가로와 세로를 넣습니다.").fetchSemanticsNode().boundsInRoot
        val field = compose.onNodeWithTag("field").fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithText("확인").fetchSemanticsNode().boundsInRoot
        assertThat(field.top).isAtLeast(message.bottom)
        assertThat(button.top).isAtLeast(field.bottom)
    }

    @Test
    fun `dismissOnClick이 false인 버튼은 창을 남기고 onClick만 부른다`() {
        var open by mutableStateOf(true)
        var dismissed = 0
        var stopped = 0
        var saved = 0
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                KAlertDialog(
                    open,
                    {
                        dismissed++
                        open = false
                    },
                    "녹음",
                    listOf(
                        KDialogAction("중지", dismissOnClick = false) { stopped++ },
                        KDialogAction("저장", KDialogActionStyle.Preferred) { saved++ },
                    ),
                )
            }
        }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("중지").performClick()
        compose.mainClock.advanceTimeBy(800)
        assertThat(stopped).isEqualTo(1)
        assertThat(dismissed).isEqualTo(0)
        compose.onNodeWithText("녹음").assertIsDisplayed()

        compose.onNodeWithText("저장").performClick()
        compose.mainClock.advanceTimeBy(800)
        assertThat(saved).isEqualTo(1)
        assertThat(dismissed).isEqualTo(1)
        compose.onNodeWithText("녹음").assertDoesNotExist()
    }

    @Test
    fun `iconTint를 주면 위험 동작이 있어도 그 색으로 칠한다`() {
        val green = Color(0xFF00B050)
        var tint by mutableStateOf(green)
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                KAlertDialog(
                    true,
                    {},
                    "지울까요?",
                    listOf(KDialogAction("취소", KDialogActionStyle.Cancel), KDialogAction("삭제", KDialogActionStyle.Destructive)),
                    icon = KIcons.Heart,
                    iconTint = tint,
                )
            }
        }
        compose.mainClock.advanceTimeBy(800)
        assertThat(countNear(green)).isGreaterThan(10)

        tint = Color.Unspecified
        compose.mainClock.advanceTimeBy(500)
        assertThat(countNear(green)).isEqualTo(0)
    }

    @Test
    fun `기본값은 좁은 창에서도 가운데 카드다`() {
        showDefault()
        assertCard()
    }

    @Test
    fun `Sheet는 넓은 창이 아니어도 아래에 붙는다`() {
        showAlert(KDialogPresentation.Sheet)
        assertSheet()
    }

    @Test
    fun `Auto는 좁은 창에서 손잡이를 끌어 닫는 시트다`() {
        var open by mutableStateOf(true)
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                KAlertDialog(open, { open = false }, "제목", actions, presentation = KDialogPresentation.Auto)
            }
        }
        compose.mainClock.advanceTimeBy(800)
        assertSheet()
        // TalkBack 창 이름은 카드와 같이 제목이다.
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "제목")).assertExists()
        compose.onNodeWithText("제목").performTouchInput {
            down(center)
            moveBy(Offset(0f, 150f))
            moveBy(Offset(0f, 150f))
            up()
        }
        compose.mainClock.advanceTimeBy(800)
        assertThat(open).isFalse()
    }

    @Test
    @Config(qualifiers = "w900dp-h700dp-mdpi")
    fun `Auto는 넓은 창에서 가운데 카드다`() {
        showAlert(KDialogPresentation.Auto)
        assertCard()
    }

    @Test
    fun `움직임 줄이기면 시트가 바로 열린다`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalKineticaReducedMotion provides true) {
                KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                    KAlertDialog(true, {}, "제목", actions, presentation = KDialogPresentation.Sheet)
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(48)
        assertSheet()
    }

    @Test
    fun `옛 생성자, copy, component5는 바이너리 호환을 위해 남아 있다`() {
        val type = KDialogAction::class.java
        val onClick: () -> Unit = {}
        val old = type.getConstructor(
            String::class.java,
            KDialogActionStyle::class.java,
            ImageVector::class.java,
            Boolean::class.javaPrimitiveType,
            Function0::class.java,
        ).newInstance("삭제", KDialogActionStyle.Destructive, null, true, onClick)
        assertThat(old.dismissOnClick).isTrue()
        assertThat(old.onClick).isSameInstanceAs(onClick)
        val copied = type.getMethod(
            "copy",
            String::class.java,
            KDialogActionStyle::class.java,
            ImageVector::class.java,
            Boolean::class.javaPrimitiveType,
            Function0::class.java,
        ).invoke(old.copy(dismissOnClick = false), "지우기", KDialogActionStyle.Default, null, false, onClick) as KDialogAction
        assertThat(copied.label).isEqualTo("지우기")
        assertThat(copied.dismissOnClick).isFalse()
        val component5 = type.methods.filter { it.name == "component5" }.map { it.returnType }
        assertThat(component5).containsExactly(Boolean::class.javaPrimitiveType, Function0::class.java)
    }

    private fun showAlert(presentation: KDialogPresentation) {
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                KAlertDialog(true, {}, "제목", actions, presentation = presentation)
            }
        }
        compose.mainClock.advanceTimeBy(800)
    }

    private fun showDefault() {
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                KAlertDialog(true, {}, "제목", actions)
            }
        }
        compose.mainClock.advanceTimeBy(800)
    }

    /** 카드는 화면 가운데에 있어 버튼 아래가 화면 아래에서 멀다. */
    private fun assertCard() {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithText("확인").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText("제목").fetchSemanticsNode().boundsInRoot
        assertThat(root.bottom - button.bottom).isGreaterThan(root.height / 4)
        assertThat(title.top).isGreaterThan(root.height / 4)
    }

    /** 시트는 화면 아래에 붙어 버튼 아래가 화면 아래와 가깝다. */
    private fun assertSheet() {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithText("확인").fetchSemanticsNode().boundsInRoot
        assertThat(root.bottom - button.bottom).isLessThan(root.height / 8)
    }

    private fun countNear(color: Color): Int {
        val map = compose.onRoot().captureToImage().toPixelMap()
        var count = 0
        for (y in 0 until map.height) {
            for (x in 0 until map.width) {
                val c = map[x, y]
                if (abs(c.red - color.red) < 0.04f && abs(c.green - color.green) < 0.04f && abs(c.blue - color.blue) < 0.04f) count++
            }
        }
        return count
    }
}
