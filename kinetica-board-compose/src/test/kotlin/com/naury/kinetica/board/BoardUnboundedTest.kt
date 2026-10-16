package com.naury.kinetica.board

import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp-mdpi")
class BoardUnboundedTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val catalog =
        listOf(BoardWidget("a", "A", widthDp = 160f, heightDp = 100f), BoardWidget("b", "B", widthDp = 160f, heightDp = 100f))

    private fun board(activity: ComponentActivity, arrangement: BoardArrangement = BoardArrangement.Free()) =
        KineticaBoardView(activity).apply {
            isEditing = true
            setBoard(KineticaBoardState.create(catalog, arrangement)) { id, context -> TextView(context).apply { text = id } }
        }

    @Test
    fun `폭 제한 없이 재는 부모 안에서도 화면 폭으로 그리고 죽지 않는다`() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var inScroll: KineticaBoardView
            lateinit var inRow: KineticaBoardView
            lateinit var inBoth: KineticaBoardView
            scenario.onActivity { activity ->
                inScroll = board(activity)
                inRow = board(activity, BoardArrangement.Grid(columns = 2, columnsByWidth = GridColumns.Adaptive(160f)))
                inBoth = board(activity)
                val wrap = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                activity.setContentView(
                    LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(HorizontalScrollView(activity).apply { addView(inScroll, wrap) }, LinearLayout.LayoutParams(-1, 300))
                        addView(
                            LinearLayout(activity).apply {
                                orientation = LinearLayout.HORIZONTAL
                                addView(inRow, LinearLayout.LayoutParams(0, -1, 1f).apply { width = ViewGroup.LayoutParams.WRAP_CONTENT })
                            },
                            LinearLayout.LayoutParams(-2, 200),
                        )
                        addView(
                            ScrollView(activity).apply { addView(HorizontalScrollView(activity).apply { addView(inBoth, wrap) }) },
                            LinearLayout.LayoutParams(-1, 300),
                        )
                    },
                )
            }
            compose.waitForIdle()
            scenario.onActivity { activity ->
                val screen = activity.window.decorView.width
                listOf(inScroll, inRow, inBoth).forEach { view: View ->
                    assertThat(view.width).isGreaterThan(0)
                    assertThat(view.width).isAtMost(screen)
                    assertThat(view.height).isGreaterThan(0)
                }
            }
        }
    }

    @Test
    fun `Compose 가로 스크롤 안에서도 화면 폭으로 그린다`() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val state = KineticaBoardState.create(catalog, BoardArrangement.Grid(columns = 2))
                (activity as androidx.activity.ComponentActivity).setContentCompose {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        Box(Modifier.testTag("board")) {
                            KineticaBoard(state, Modifier.verticalScroll(rememberScrollState()), editing = true) { id -> Text(id) }
                        }
                    }
                }
            }
            compose.waitForIdle()
            val bounds = compose.onNodeWithTag("board").getBoundsInRoot()
            assertThat((bounds.right - bounds.left).value).isWithin(1f).of(400f)
            assertThat((bounds.bottom - bounds.top).value).isGreaterThan(0f)
        }
    }
}

private fun ComponentActivity.setContentCompose(content: @androidx.compose.runtime.Composable () -> Unit) {
    setContentView(androidx.compose.ui.platform.ComposeView(this).apply { setContent(content) })
}
