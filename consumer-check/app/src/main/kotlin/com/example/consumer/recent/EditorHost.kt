package com.example.consumer.recent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.consumer.SampleScene
import com.naury.kinetica.editor.KineticaEditor
import com.naury.kinetica.editor.StudioToolbarButton
import com.naury.kinetica.editor.StudioToolbarGroup
import com.naury.kinetica.history.KineticaSession
import com.naury.kinetica.history.SessionOpenResult

/** 호스트가 편집기를 띄우고 도구 모음 끝에 자기 버튼(내보내기, 공유)을 넣는 화면. 나가기는 호스트가 받는다. */
@Composable
fun EditorHostScreen(onExit: () -> Unit) {
    val session = remember { (KineticaSession.create(SampleScene.document) as SessionOpenResult.Opened).session }
    var exported by remember { mutableIntStateOf(0) }
    var shared by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Text("내보내기 $exported 공유 $shared", Modifier.testTag("editorHostState"))
        KineticaEditor(
            session,
            Modifier.weight(1f),
            onExit = onExit,
            toolbarEnd = {
                StudioToolbarGroup {
                    StudioToolbarButton("내보내기", { exported++ }, Modifier.testTag("hostExport"), icon = Icons.Filled.Star)
                    StudioToolbarButton("공유", { shared++ }, Modifier.testTag("hostShare"), icon = Icons.Filled.Share)
                }
            },
        )
    }
}

/** 편집기를 전체 화면으로 띄운다. 나가기를 누르면 [exits]가 늘어난다(실제 앱이라면 저장하고 finish한다). */
class EditorHostActivity : ComponentActivity() {
    var exits by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Text("나가기 $exits", Modifier.testTag("exitCount"))
                    EditorHostScreen(onExit = { exits++ })
                }
            }
        }
    }
}
