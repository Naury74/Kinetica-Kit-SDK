package com.naury.kinetica.boardsample

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.naury.kinetica.board.BoardStates
import com.naury.kinetica.board.BoardStorage
import com.naury.kinetica.board.BoardWidget
import com.naury.kinetica.board.KineticaBoardState
import com.naury.kinetica.board.KineticaBoardView

/** Compose를 쓰지 않는 XML 화면. 기존 View를 그대로 보드 위젯으로 쓴다. */
class XmlBoardActivity : ComponentActivity() {
    private lateinit var state: KineticaBoardState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_xml_board)
        val board = findViewById<KineticaBoardView>(R.id.board)
        val edit = findViewById<Button>(R.id.edit)
        // 화면을 돌려도 배치·실행 취소가 남고 화면이 가려질 때마다 저장된다. 닫기도 알아서 한다.
        val storage = BoardStorage.sharedPreferences(this, PREFS, KEY)
        state = BoardStates.obtain(this, catalog, storage)
        board.setBoard(state) { id, context ->
            // 자리 id로 위젯 키를 찾는다(여러 개 놓는 위젯은 자리마다 id가 다르다).
            val key = state.widgetKeyOf(id) ?: id
            TextView(context).apply {
                text = catalog.first { it.key == key }.label
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP)
                background = GradientDrawable().apply {
                    cornerRadius = resources.displayMetrics.density * RADIUS_DP
                    setColor(COLORS.getValue(key))
                }
                setOnClickListener { text = "${catalog.first { it.key == key }.label} ✓" }
            }
        }
        fun toggle() {
            board.isEditing = !board.isEditing
            edit.setText(if (board.isEditing) R.string.board_done else R.string.board_edit)
            if (!board.isEditing) storage.save(state.toJson())
        }
        edit.setOnClickListener { toggle() }
        board.setOnLongPressListener { if (!board.isEditing) toggle() }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // 보드 View가 편집 모드를 되살린 뒤 버튼 글자를 맞춘다.
        val board = findViewById<KineticaBoardView>(R.id.board)
        findViewById<Button>(R.id.edit).setText(if (board.isEditing) R.string.board_done else R.string.board_edit)
    }

    private companion object {
        const val PREFS = "kinetica-board-xml"
        const val KEY = "free"
        const val TEXT_SP = 18f
        const val RADIUS_DP = 16f
        val catalog = listOf(
            BoardWidget("inbox", "받은 메시지", widthDp = 176f, heightDp = 100f),
            BoardWidget("tasks", "할 일", widthDp = 176f, heightDp = 100f),
            BoardWidget("calendar", "일정", widthDp = 360f, heightDp = 140f),
        )
        val COLORS = mapOf("inbox" to 0xFF5B6CFF.toInt(), "tasks" to 0xFF0E9F6E.toInt(), "calendar" to 0xFFE0740B.toInt())
    }
}
