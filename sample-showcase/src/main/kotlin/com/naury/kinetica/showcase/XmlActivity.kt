// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.naury.kinetica.board.BoardStates
import com.naury.kinetica.board.BoardStorage
import com.naury.kinetica.board.BoardWidget
import com.naury.kinetica.board.KineticaBoardView
import com.naury.kinetica.core.scene.DataValue
import com.naury.kinetica.core.scene.SceneAction
import com.naury.kinetica.core.scene.SceneData
import com.naury.kinetica.player.KineticaPlayerView
import com.naury.kinetica.presets.EffectPresets
import com.naury.kinetica.render.ViewEffects
import com.naury.kinetica.serialization.RemoteSceneLoader
import com.naury.kinetica.serialization.SceneFetch

/** Compose 없이 만든 화면. 레이아웃 XML에 보드와 재생기를 넣고, 기존 View를 위젯으로 쓴다. */
class XmlActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_xml)
        val board = findViewById<KineticaBoardView>(R.id.board)
        val edit = findViewById<Button>(R.id.edit)
        // 화면을 회전해도 배치와 실행 취소 기록이 유지되고, 화면이 가려질 때마다 저장된다.
        val state = BoardStates.obtain(this, catalog, BoardStorage.sharedPreferences(this, "showcase-xml", "board"))
        board.setBoard(state) { id, context ->
            val key = state.widgetKeyOf(id) ?: id
            TextView(context).apply {
                text = catalog.first { it.key == key }.label
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                background = GradientDrawable().apply {
                    cornerRadius = resources.displayMetrics.density * 16f
                    setColor(COLORS.getValue(key))
                }
            }
        }
        fun toggle() {
            board.isEditing = !board.isEditing
            edit.text = if (board.isEditing) "완료" else "편집"
        }
        edit.setOnClickListener { toggle() }
        ViewEffects.apply(edit, EffectPresets.button, cornerRadiusDp = 8f)
        board.setOnLongPressListener { if (!board.isEditing) toggle() }

        val promo = findViewById<KineticaPlayerView>(R.id.promo)
        promo.setLoader(RemoteSceneLoader({ SceneFetch.Body(promoJson(0).toByteArray()) }))
        promo.data = SceneData.of("sales.today" to DataValue.Number(1_284_000.0))
        promo.setOnActionListener { raw -> Toast.makeText(this, "받은 동작: ${SceneAction.parse(raw).name}", Toast.LENGTH_SHORT).show() }
    }

    private companion object {
        val catalog = listOf(
            BoardWidget("inbox", "받은 메시지", widthDp = 176f, heightDp = 100f),
            BoardWidget("tasks", "할 일", widthDp = 176f, heightDp = 100f),
            BoardWidget("calendar", "일정", widthDp = 360f, heightDp = 120f),
        )
        val COLORS = mapOf("inbox" to 0xFF5B6CFF.toInt(), "tasks" to 0xFF0E9F6E.toInt(), "calendar" to 0xFFE0740B.toInt())
    }
}
