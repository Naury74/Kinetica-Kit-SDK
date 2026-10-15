package com.example.consumer.xml

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.consumer.R
import com.naury.kinetica.ui.GlassColorPicker
import com.naury.kinetica.ui.GlassSignaturePad
import com.naury.kinetica.ui.SignaturePadState
import com.naury.kinetica.ui.rememberSignaturePadState

/** NestedScrollView 안 ComposeView에 GlassColorPicker와 GlassSignaturePad를 넣은 XML 호스트 화면. */
class XmlComposeActivity : AppCompatActivity() {
    /** 시험과 XML 버튼이 함께 쓰는 서명 상태. ComposeView가 처음 그려질 때 채운다. */
    var signature: SignaturePadState? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_xml_compose)
        val colorValue = findViewById<TextView>(R.id.colorValue)
        val signatureState = findViewById<TextView>(R.id.signatureState)

        findViewById<ComposeView>(R.id.colorPicker).setContent {
            var color by rememberSaveable(stateSaver = ColorSaver) { mutableStateOf(Color(0xFF0A84FF)) }
            GlassColorPicker(color, { color = it }, Modifier.testTag("xmlColorPicker"))
            LaunchedEffect(Unit) {
                snapshotFlow { color }.collect { colorValue.text = "색: #%08X".format(it.toArgb()) }
            }
        }
        findViewById<ComposeView>(R.id.signature).setContent {
            val state = rememberSignaturePadState()
            signature = state
            GlassSignaturePad(Modifier.fillMaxWidth().height(200.dp).testTag("xmlSignature"), state)
            LaunchedEffect(state) {
                snapshotFlow { state.isEmpty }.collect { signatureState.text = if (it) "서명: 없음" else "서명: 있음" }
            }
        }
        findViewById<Button>(R.id.signatureUndo).setOnClickListener { signature?.undo() }
    }

    private companion object {
        val ColorSaver = androidx.compose.runtime.saveable.Saver<Color, Int>(save = { it.toArgb() }, restore = { Color(it) })
    }
}
