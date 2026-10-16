package com.example.consumer.recent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KTabletopLayout
import com.naury.kinetica.ui.kReadableWidth
import com.naury.kinetica.ui.rememberKWindowLayout

/**
 * 영상 앱 화면. 위는 영상, 아래는 설명 글과 조작부다. 탁자 모드면 접힌 선 위아래로 나뉘고, 넓은 창에서는 글이 읽기 좋은 폭으로 줄어든다.
 * 목록 열 수는 창 폭으로 정한다.
 */
@Composable
fun WideHostScreen() {
    val window = rememberKWindowLayout()
    Column(Modifier.fillMaxSize()) {
        Text(
            "구간 ${window.widthClass} 열 ${window.columns(minCellWidth = 280.dp)} 자세 ${window.posture} 짧음 ${window.isShort}",
            Modifier.testTag("windowInfo"),
        )
        KTabletopLayout(
            Modifier.fillMaxWidth().weight(1f).testTag("tabletop"),
            top = { Box(Modifier.fillMaxWidth().height(180.dp).background(Color.Black).testTag("video")) },
            bottom = {
                Column(Modifier.fillMaxSize().background(Color(0xFFEFEFEF)).testTag("controls")) {
                    Text(
                        "영상 설명 ".repeat(40),
                        Modifier.kReadableWidth().padding(vertical = 8.dp).testTag("readable"),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            },
        )
    }
}

/** [WideHostScreen]만 띄우는 화면. 탁자 모드 시험에서 접힌 선 정보를 이 Activity에 흘려 넣는다. */
class WideHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { WideHostScreen() } }
    }
}
