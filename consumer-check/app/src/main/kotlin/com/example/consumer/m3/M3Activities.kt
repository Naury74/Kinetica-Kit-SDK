package com.example.consumer.m3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/** edge-to-edge를 켠 Material3 호스트 화면. */
class M3EdgeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { M3App() }
    }
}

/**
 * enableEdgeToEdge를 부르지 않는 Material3 호스트 화면. Android 15 이상에서 targetSdk 35 이상이면 시스템이 edge-to-edge를 강제하므로
 * 화면 자체는 시스템 바 아래까지 그려지지만, 시스템 바 색과 아이콘 대비는 기본값으로 남는다. 그보다 낮은 기기에서는 예전 방식이 된다.
 */
class M3NoEdgeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { M3App() }
    }
}
