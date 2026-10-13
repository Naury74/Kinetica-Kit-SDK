package com.naury.kinetica.boardsample

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
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
        state = KineticaBoardState.create(catalog, saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY, null))
        board.setBoard(state) { key, context ->
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
        edit.setOnClickListener {
            board.isEditing = !board.isEditing
            edit.setText(if (board.isEditing) R.string.board_done else R.string.board_edit)
            if (!board.isEditing) save()
        }
    }

    override fun onStop() {
        super.onStop()
        save()
    }

    override fun onDestroy() {
        super.onDestroy()
        state.close()
    }

    private fun save() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY, state.toJson()).apply()
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
