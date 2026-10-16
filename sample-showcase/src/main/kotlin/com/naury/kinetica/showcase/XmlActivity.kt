// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.naury.kinetica.board.BoardStates
import com.naury.kinetica.board.BoardStorage
import com.naury.kinetica.board.BoardWidget
import com.naury.kinetica.board.KineticaBoardView
import com.naury.kinetica.core.scene.DataValue
import com.naury.kinetica.core.scene.NodeEffects
import com.naury.kinetica.core.scene.PressEffect
import com.naury.kinetica.core.scene.PressKind
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
import com.naury.kinetica.ui.view.GlassPopupHandle
import com.naury.kinetica.ui.view.GlassPopupOptions
import com.naury.kinetica.ui.view.GlassSheets
import com.naury.kinetica.ui.view.GlassSurfaceView
import com.naury.kinetica.ui.view.GlassToasts

/** Compose 없이 만든 화면. 레이아웃 XML에 보드와 재생기, 글래스 표면을 넣고, 기존 View를 위젯으로 쓴다. 글래스 창도 코드로 띄운다. */
class XmlActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(showcaseContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        showcaseEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_xml)
        // 배경은 화면 끝까지 칠하고, 내용만 상태 표시줄, 내비게이션 바, 가로 화면 컷아웃, 키보드를 피한다.
        val content = findViewById<View>(R.id.content)
        val base = content.paddingLeft
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime(),
            )
            view.setPadding(base + bars.left, base + bars.top, base + bars.right, base + bars.bottom)
            insets
        }
        val board = findViewById<KineticaBoardView>(R.id.board)
        val edit = findViewById<Button>(R.id.edit)
        // 화면을 회전해도 배치와 실행 취소 기록이 유지되고, 화면이 가려질 때마다 저장된다.
        val state = BoardStates.obtain(this, catalog, BoardStorage.sharedPreferences(this, "showcase-xml", "board-v2"))
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
        stylePill(edit, filled = true)
        board.setOnLongPressListener { if (!board.isEditing) toggle() }

        val promo = findViewById<KineticaPlayerView>(R.id.promo)
        promo.setLoader(RemoteSceneLoader({ SceneFetch.Body(promoJson(0, heroOnly = true).toByteArray()) }))
        promo.data = SceneData.of("sales.today" to DataValue.Number(1_284_000.0))
        promo.setOnActionListener { raw -> GlassToasts.show(this, "받은 동작: ${SceneAction.parse(raw).name}") }
        setUpGlass()
        // 화면을 돌리거나 폴더블을 접고 펴서 액티비티가 다시 만들어지면 열려 있던 창은 닫히므로, 저장해 둔 상태로 다시 띄운다.
        when (savedInstanceState?.getString(KEY_POPUP)) {
            POPUP_SHEET -> showSheet(savedInstanceState.getString(KEY_MEMO).orEmpty())
            POPUP_DIALOG -> showDialog()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val sheetOpen = sheet?.isShowing == true
        outState.putString(
            KEY_POPUP,
            if (sheetOpen) {
                POPUP_SHEET
            } else if (dialog?.isShowing == true) {
                POPUP_DIALOG
            } else {
                null
            },
        )
        if (sheetOpen) outState.putString(KEY_MEMO, memoField?.text?.toString())
    }

    private var sheet: GlassPopupHandle? = null
    private var dialog: GlassPopupHandle? = null
    private var memoField: EditText? = null

    /** XML 화면에서 글래스 시트, 다이얼로그, 토스트를 띄우고 GlassSurfaceView 글자 색을 스타일에 맞춘다. */
    private fun setUpGlass() {
        val caption = findViewById<GlassSurfaceView>(R.id.promo_caption)
        val captionText = findViewById<TextView>(R.id.promo_caption_text)
        captionText.setTextColor(caption.contentColor)
        // auto 스타일은 다크 모드를 따라 바뀌므로 글자 색도 함께 바꾼다.
        caption.setOnContentColorChangeListener { captionText.setTextColor(it) }
        findViewById<Button>(R.id.glass_sheet).apply { stylePill(this) }.setOnClickListener { showSheet() }
        findViewById<Button>(R.id.glass_dialog).apply { stylePill(this) }.setOnClickListener { showDialog() }
        findViewById<Button>(R.id.glass_toast).apply {
            stylePill(this)
        }.setOnClickListener { GlassToasts.show(this, "XML 화면에서 띄운 글래스 토스트") }
    }

    private fun showSheet(memoText: String = "") {
        val memo = EditText(this).apply {
            hint = "메모를 입력하면 시트가 키보드 위로 올라옵니다"
            styleField(this)
            setText(memoText)
        }
        memoField = memo
        val save = Button(this).apply {
            text = "저장"
            stylePill(this, filled = true, ink = sheetStyle.onGlass.toArgb())
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(sheetText("XML로 만든 시트 내용", 20f))
            addView(sheetText("아래 보드와 재생기가 블러 처리되어 비칩니다. 아래로 끌거나 뒤로 가기로 닫습니다.", 14f))
            addView(memo, blockParams(top = 4))
            addView(save, blockParams(top = 12))
        }
        val sheet = GlassSheets.show(this, content, GlassPopupOptions(sheetStyle, "글래스 시트"))
        this.sheet = sheet
        save.setOnClickListener {
            sheet.dismiss()
            GlassToasts.show(this, "저장했습니다: ${memo.text}")
        }
    }

    private fun showDialog() {
        val ok = Button(this).apply {
            text = "확인"
            stylePill(this, filled = true, ink = sheetStyle.onGlass.toArgb())
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(sheetText("글래스 다이얼로그", 20f))
            addView(sheetText("바깥을 누르거나 뒤로 가기를 하면 닫힙니다.", 14f))
            addView(ok, blockParams(top = 8))
        }
        val dialog = GlassDialogs.show(this, content, GlassPopupOptions(sheetStyle, "글래스 다이얼로그"))
        this.dialog = dialog
        ok.setOnClickListener { dialog.dismiss() }
    }

    /** 시스템 다크 모드에 맞춘 스타일. 글자 색도 같은 스타일에서 가져온다. */
    private val sheetStyle: GlassStyle
        get() {
            val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            return if (night) GlassStyles.LiquidDark else GlassStyles.Liquid
        }

    /**
     * 버튼을 높이 44dp 캡슐로 칠한다. [filled]면 [ink] 바탕에 대비되는 글자, 아니면 [ink]를 옅게 깐 바탕에 [ink] 글자.
     * 누르면 리플 대신 살짝 줄고 어두워진다.
     */
    private fun stylePill(button: Button, filled: Boolean = false, ink: Int = getColor(R.color.xml_ink)) {
        val light = Color.luminance(ink) > HALF
        button.background = GradientDrawable().apply {
            cornerRadius = dp(22f)
            setColor(if (filled) ink else ColorUtils.setAlphaComponent(ink, TINT_ALPHA))
        }
        button.stateListAnimator = null
        button.isAllCaps = false
        button.minHeight = dp(44f).toInt()
        button.minimumHeight = dp(44f).toInt()
        button.setTextColor(
            if (!filled) {
                ink
            } else if (light) {
                Color.BLACK
            } else {
                Color.WHITE
            },
        )
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        button.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        ViewEffects.apply(button, PRESS_EFFECTS, cornerRadiusDp = 22f)
    }

    /** 입력란을 12dp 둥근 테두리 상자로 칠한다. 밑줄은 없앤다. */
    private fun styleField(field: EditText) {
        field.background = GradientDrawable().apply {
            cornerRadius = dp(12f)
            val ink = sheetStyle.onGlass.toArgb()
            setColor(ColorUtils.setAlphaComponent(ink, FIELD_ALPHA))
            setStroke(dp(1f).toInt(), ColorUtils.setAlphaComponent(ink, STROKE_ALPHA))
        }
        val horizontal = dp(14f).toInt()
        field.setPadding(horizontal, 0, horizontal, 0)
        field.minHeight = dp(48f).toInt()
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        field.setTextColor(sheetStyle.onGlass.toArgb())
    }

    private fun blockParams(top: Int) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top.toFloat()).toInt()
        }

    private fun dp(value: Float) = resources.displayMetrics.density * value

    private fun sheetText(text: String, sizeSp: Float) = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (sizeSp >= TITLE_SP) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(sheetStyle.onGlass.toArgb())
        setPadding(0, 0, 0, (resources.displayMetrics.density * 8).toInt())
    }

    private companion object {
        const val KEY_POPUP = "popup"
        const val KEY_MEMO = "memo"
        const val POPUP_SHEET = "sheet"
        const val POPUP_DIALOG = "dialog"
        const val TITLE_SP = 18f
        const val HALF = 0.5f
        const val TINT_ALPHA = 0x14
        const val FIELD_ALPHA = 0x0F
        const val STROKE_ALPHA = 0x24

        /** XML 버튼 눌림: 리플 대신 살짝 줄고 어두워진다. */
        val PRESS_EFFECTS = NodeEffects(press = listOf(PressEffect(PressKind.Scale, amount = 0.96), PressEffect(PressKind.Dim)))
        val catalog = listOf(
            BoardWidget("inbox", "받은 메시지", widthDp = 176f, heightDp = 116f),
            BoardWidget("tasks", "할 일", widthDp = 176f, heightDp = 116f),
            BoardWidget("calendar", "일정", widthDp = 368f, heightDp = 128f),
            BoardWidget("notes", "메모", widthDp = 176f, heightDp = 104f),
            BoardWidget("weather", "날씨", widthDp = 176f, heightDp = 104f),
        )
        val COLORS = mapOf(
            "inbox" to 0xFF5B6CFF.toInt(),
            "tasks" to 0xFF0E9F6E.toInt(),
            "calendar" to 0xFFE0740B.toInt(),
            "notes" to 0xFF8B5CF6.toInt(),
            "weather" to 0xFF0284C7.toInt(),
        )
    }
}
