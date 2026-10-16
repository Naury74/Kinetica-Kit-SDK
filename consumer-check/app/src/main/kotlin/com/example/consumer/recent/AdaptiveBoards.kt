package com.example.consumer.recent

import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.example.consumer.R
import com.naury.kinetica.board.BoardArrangement
import com.naury.kinetica.board.BoardStates
import com.naury.kinetica.board.BoardStorage
import com.naury.kinetica.board.BoardWidget
import com.naury.kinetica.board.GridColumns
import com.naury.kinetica.board.KineticaBoard
import com.naury.kinetica.board.KineticaBoardState
import com.naury.kinetica.board.KineticaBoardView
import com.naury.kinetica.board.rememberKineticaBoardState

/** 홈 화면 위젯 목록. 큰 위젯 하나와 작은 위젯 여럿이다. */
val adaptiveCatalog = listOf(
    BoardWidget("weather", "날씨", columnSpan = 2),
    BoardWidget("todo", "할 일"),
    BoardWidget("music", "음악"),
    BoardWidget("steps", "걸음"),
    BoardWidget("mail", "메일"),
    BoardWidget("photos", "사진"),
)

/** 시험마다 빈 배치에서 시작하도록 프로세스 안에만 두는 저장소. 실제 앱이라면 SharedPreferences나 DataStore를 쓴다. */
object MemoryBoardStorage {
    private val saved = HashMap<String, String>()

    fun of(name: String): BoardStorage = object : BoardStorage {
        override fun load(): String? = saved[name]

        override fun save(json: String) {
            saved[name] = json
        }
    }

    fun clear() = saved.clear()
}

/** 칸이 180dp보다 좁아지지 않는 만큼 열을 늘리는 Compose 보드. 폰 세로는 2열, 넓은 화면은 더 많은 열이다. */
class AdaptiveBoardActivity : ComponentActivity() {
    lateinit var board: KineticaBoardState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val state = rememberKineticaBoardState(
                    adaptiveCatalog,
                    MemoryBoardStorage.of("compose"),
                    BoardArrangement.Grid(columns = 2, columnsByWidth = GridColumns.Adaptive(minCellWidthDp = 180f, maxColumns = 8)),
                )
                board = state
                KineticaBoard(state, Modifier.fillMaxSize().safeDrawingPadding().testTag("adaptiveBoard")) { id ->
                    Box(
                        Modifier.fillMaxSize().background(Color(0xFFDDE3FF), RoundedCornerShape(16)).testTag("w_$id"),
                        contentAlignment = Alignment.Center,
                    ) { Text(id) }
                }
            }
        }
    }
}

/** 같은 보드를 XML의 KineticaBoardView(app:kinetica_minCellWidth)로 띄운다. */
class XmlAdaptiveBoardActivity : AppCompatActivity() {
    lateinit var board: KineticaBoardState
    val views = HashMap<String, TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_xml_adaptive_board)
        board = BoardStates.obtain(this, adaptiveCatalog, MemoryBoardStorage.of("xml"), BoardArrangement.Grid(columns = 2))
        findViewById<KineticaBoardView>(R.id.adaptiveBoard).setBoard(board) { id, context ->
            TextView(context).apply {
                text = id
                gravity = Gravity.CENTER
                setBackgroundColor(0xFFDDE3FF.toInt())
                views[id] = this
            }
        }
    }
}
