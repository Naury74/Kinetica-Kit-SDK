package com.example.consumer.recent

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.consumer.m3.rememberSamplePhotos
import com.naury.kinetica.ui.KImageViewer
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.KSystemBarIcons
import com.naury.kinetica.ui.rememberKImageViewerState

/**
 * 밝은 배경에 어두운 상태 표시줄 아이콘을 쓰는 호스트 화면. 사진 뷰어를 열면 아이콘이 밝아졌다가 닫으면 돌아와야 한다.
 * [hostIcons]는 호스트가 직접 두는 KSystemBarIcons 요청이다(null이면 두지 않는다).
 */
open class BarIconsActivity : ComponentActivity() {
    var hostIcons by mutableStateOf<Boolean?>(null)

    /** 뷰어가 열려 있는지. 스캐폴드 안 뷰어가 떠 있으면 뒤쪽 화면의 접근성 노드가 가려지므로 시험은 이 값을 읽는다. */
    @Volatile
    var viewerOpen = false
        private set

    protected open val inScaffold: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                if (inScaffold) {
                    KineticaScaffold(background = { Box(Modifier.fillMaxSize().background(Color(0xFFF7F7F7))) }) { Body() }
                } else {
                    Box(Modifier.fillMaxSize().background(Color(0xFFF7F7F7))) { Body() }
                }
            }
        }
    }

    @Composable
    private fun Body() {
        val photos = rememberSamplePhotos(3)
        val viewer = rememberKImageViewerState()
        SideEffect { viewerOpen = viewer.isOpen }
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            Text(if (viewer.isOpen) "뷰어 열림" else "뷰어 닫힘", Modifier.testTag("barViewerState"))
            Button({ viewer.open(0) }, Modifier.testTag("barOpen")) { Text("사진 보기") }
        }
        hostIcons?.let { KSystemBarIcons(light = it) }
        KImageViewer(viewer, photos)
    }
}

/** [BarIconsActivity]와 같고 KineticaScaffold 안에 둔다. 뷰어는 별도 창이 아니라 스캐폴드 오버레이에 뜬다. */
class BarIconsScaffoldActivity : BarIconsActivity() {
    override val inScaffold: Boolean = true
}
