package com.example.consumer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.consumer.m3.M3EdgeActivity
import com.example.consumer.m3.M3NoEdgeActivity
import com.example.consumer.xml.XmlHostActivity
import com.naury.kinetica.player.KineticaPlayer
import com.naury.kinetica.player.rememberPlaybackController

// docs 00: 의존성 추가 뒤 20줄 안의 연동. 아래 버튼으로 시나리오 화면을 연다.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screens = listOf(
            "Material3 (edge-to-edge)" to M3EdgeActivity::class.java,
            "Material3 (edge-to-edge 없음)" to M3NoEdgeActivity::class.java,
            "XML 호스트" to XmlHostActivity::class.java,
            "Java 호스트" to JavaHostActivity::class.java,
            "보드" to BoardActivity::class.java,
            "글래스" to GlassActivity::class.java,
            "XML 글래스" to XmlGlassActivity::class.java,
        )
        setContent {
            Column(Modifier.safeDrawingPadding()) {
                screens.forEach { (label, type) -> Button({ startActivity(Intent(this@MainActivity, type)) }) { Text(label) } }
                val playback = rememberPlaybackController(SampleScene.fade)
                LaunchedEffect(playback) { playback.play() }
                KineticaPlayer(SampleScene.document, Modifier.fillMaxWidth().height(200.dp), playback)
            }
        }
    }
}
