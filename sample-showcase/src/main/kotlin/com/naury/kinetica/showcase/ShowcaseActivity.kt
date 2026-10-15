package com.naury.kinetica.showcase

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/** Kinetica 쇼케이스: SDK로 무엇을 만들 수 있는지 한 앱에서 보여 준다. */
class ShowcaseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GlassDemo(onBack = ::finish) }
    }
}
