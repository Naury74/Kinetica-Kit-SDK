package com.naury.kinetica.board

import android.os.Looper
import android.os.Parcelable
import android.util.SparseArray
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp-mdpi")
class KineticaBoardViewTest {
    @Test
    fun `XML 화면에서 호스트 View를 위젯으로 놓고 편집 모드를 바꾼다`() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val board = KineticaBoardView(activity)
                activity.setContentView(board)
                val state = KineticaBoardState.create(listOf(BoardWidget("orders", "주문", widthDp = 200f, heightDp = 100f)))
                board.setBoard(state) { key, context ->
                    TextView(context).apply {
                        text = "view $key"
                        tag = key
                    }
                }
                board.isEditing = true
                shadowOf(Looper.getMainLooper()).idle()

                val widget = board.findViewWithTag<TextView>("orders")
                assertThat(widget).isNotNull()
                assertThat(widget.text.toString()).isEqualTo("view orders")
                assertThat(widget.width).isGreaterThan(0)
                assertThat(board.isEditing).isTrue()
            }
        }
    }

    @Test
    fun `XML 속성을 읽고 화면을 돌려도 편집 모드를 되살린다`() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val attrs = Robolectric.buildAttributeSet()
                    .addAttribute(R.attr.kinetica_editing, "true")
                    .addAttribute(R.attr.kinetica_stackAutoAdvanceMs, "4000")
                    .addAttribute(R.attr.kinetica_accentColor, "#FF112233")
                    .build()
                val board = KineticaBoardView(activity, attrs).apply { id = BOARD_ID }

                assertThat(board.isEditing).isTrue()
                assertThat(board.stackAutoAdvanceMs).isEqualTo(4_000L)
                assertThat(board.colors!!.accent.toArgb()).isEqualTo(0xFF112233.toInt())

                val saved = SparseArray<Parcelable>()
                board.saveHierarchyState(saved)
                val restored = KineticaBoardView(activity).apply { id = BOARD_ID }
                restored.restoreHierarchyState(saved)

                assertThat(restored.isEditing).isTrue()
            }
        }
    }

    private companion object {
        const val BOARD_ID = 42
    }
}
