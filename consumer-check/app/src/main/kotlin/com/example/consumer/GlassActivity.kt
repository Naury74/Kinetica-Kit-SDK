package com.example.consumer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.naury.kinetica.ui.GlassDialog
import com.naury.kinetica.ui.GlassScaffold
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.GlassSurface
import com.naury.kinetica.ui.jellyPress

/** 배포 artifact만으로 글래스 UI를 쓰는지 확인한다. */
class GlassActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var open by remember { mutableStateOf(false) }
            GlassScaffold(style = GlassStyles.Acrylic, background = { Box(Modifier.fillMaxSize().background(Color.Magenta)) }) {
                GlassSurface(Modifier.jellyPress()) { BasicText("glass") }
                GlassDialog(open, { open = false }) { BasicText("dialog") }
            }
        }
    }
}
