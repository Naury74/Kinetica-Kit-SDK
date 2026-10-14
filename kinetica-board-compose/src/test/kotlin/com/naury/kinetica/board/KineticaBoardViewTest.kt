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

    @Test
    fun `XML 화면 보드 상태는 화면을 다시 만들어도 실행 취소까지 남고 가려질 때 저장된다`() {
        val saves = mutableListOf<String>()
        val storage = object : BoardStorage {
            override fun load(): String? = null

            override fun save(json: String) {
                saves += json
            }
        }
        val catalog = listOf(BoardWidget("a", "A"), BoardWidget("memo", "메모", multiple = true, placedByDefault = false))
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            var first: KineticaBoardState? = null
            scenario.onActivity { activity ->
                first = BoardStates.obtain(activity, catalog, storage).also { it.add("memo") }
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val again = BoardStates.obtain(activity, catalog, storage)
                assertThat(again).isSameInstanceAs(first)
                assertThat(again.history.state.value.canUndo).isTrue()
            }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)

            assertThat(saves).isNotEmpty()
            assertThat(KineticaBoardState.create(catalog, saved = saves.last()).itemIds).contains("memo")
        }
    }

    private companion object {
        const val BOARD_ID = 42
    }
}
