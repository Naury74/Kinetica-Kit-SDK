// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toArgb
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
import com.naury.kinetica.ui.GlassStyle
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.view.GlassDialogs
import com.naury.kinetica.ui.view.GlassPopupOptions
import com.naury.kinetica.ui.view.GlassSheets
import com.naury.kinetica.ui.view.GlassSurfaceView
import com.naury.kinetica.ui.view.GlassToasts

/** Compose 없이 만든 화면. 레이아웃 XML에 보드와 재생기, 글래스 표면을 넣고, 기존 View를 위젯으로 쓴다. 글래스 창도 코드로 띄운다. */
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
        setUpGlass()
    }

    /** XML 화면에서 글래스 시트, 다이얼로그, 토스트를 띄우고 GlassSurfaceView 글자 색을 스타일에 맞춘다. */
    private fun setUpGlass() {
        val caption = findViewById<GlassSurfaceView>(R.id.promo_caption)
        findViewById<TextView>(R.id.promo_caption_text).setTextColor(caption.contentColor)
        findViewById<Button>(R.id.glass_sheet).setOnClickListener { showSheet() }
        findViewById<Button>(R.id.glass_dialog).setOnClickListener { showDialog() }
        findViewById<Button>(R.id.glass_toast).setOnClickListener { GlassToasts.show(this, "XML 화면에서 띄운 글래스 토스트") }
    }

    private fun showSheet() {
        val memo = EditText(this).apply { hint = "메모를 입력하면 시트가 키보드 위로 올라옵니다" }
        val save = Button(this).apply { text = "저장" }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(sheetText("XML로 만든 시트 내용", 20f))
            addView(sheetText("아래 보드와 재생기가 블러 처리되어 비칩니다. 아래로 끌거나 뒤로 가기로 닫습니다.", 14f))
            addView(memo)
            addView(save)
        }
        val sheet = GlassSheets.show(this, content, GlassPopupOptions(sheetStyle, "글래스 시트"))
        save.setOnClickListener {
            sheet.dismiss()
            GlassToasts.show(this, "저장했습니다: ${memo.text}")
        }
    }

    private fun showDialog() {
        val ok = Button(this).apply { text = "확인" }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(sheetText("글래스 다이얼로그", 20f))
            addView(sheetText("바깥을 누르거나 뒤로 가기를 하면 닫힙니다.", 14f))
            addView(ok)
        }
        val dialog = GlassDialogs.show(this, content, GlassPopupOptions(sheetStyle, "글래스 다이얼로그"))
        ok.setOnClickListener { dialog.dismiss() }
    }

    /** 시스템 다크 모드에 맞춘 스타일. 글자 색도 같은 스타일에서 가져온다. */
    private val sheetStyle: GlassStyle
        get() {
            val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            return if (night) GlassStyles.LiquidDark else GlassStyles.Liquid
        }

    private fun sheetText(text: String, sizeSp: Float) = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(sheetStyle.onGlass.toArgb())
        setPadding(0, 0, 0, (resources.displayMetrics.density * 8).toInt())
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
