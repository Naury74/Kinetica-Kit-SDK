package com.naury.kinetica.boardsample

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.naury.kinetica.board.BoardArrangement
import com.naury.kinetica.board.KineticaBoard
import com.naury.kinetica.board.KineticaBoardState

/** 보드를 쓰는 평범한 가게 앱 홈 화면. 편집을 누르면 그 자리에서 위젯을 옮기고 크기를 바꾸고 빼고 추가한다. */
class BoardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) { StoreHome() }
        }
    }
}

@Composable
private fun StoreHome(viewModel: StoreViewModel = viewModel()) {
    val context = LocalContext.current
    val data by viewModel.data.collectAsStateWithLifecycle()
    var grid by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val state = remember(grid) { BoardStore.open(context, grid) }
    // 화면을 떠나거나 배치 방식을 바꾸면 저장한다. 편집을 마칠 때도 저장한다.
    DisposableEffect(state) {
        onDispose {
            BoardStore.save(context, grid, state)
            state.close()
        }
    }
    val history by state.history.state.collectAsState()
    Column(Modifier.fillMaxSize().background(Color(SCREEN_BACKGROUND)).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.board_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (editing) {
                TextButton(onClick = { state.history.undo() }, enabled = history.canUndo) { Text(stringResource(R.string.board_undo)) }
                TextButton(onClick = { state.history.redo() }, enabled = history.canRedo) { Text(stringResource(R.string.board_redo)) }
            }
            Button(onClick = {
                if (editing) BoardStore.save(context, grid, state)
                editing = !editing
            }) { Text(stringResource(if (editing) R.string.board_done else R.string.board_edit)) }
        }
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(selected = !grid, onClick = { grid = false }, label = { Text(stringResource(R.string.board_free)) })
            FilterChip(selected = grid, onClick = { grid = true }, label = { Text(stringResource(R.string.board_grid)) })
            TextButton(onClick = { context.startActivity(Intent(context, XmlBoardActivity::class.java)) }) {
                Text(stringResource(R.string.board_xml))
            }
            TextButton(onClick = { context.startActivity(Intent(context, EffectsActivity::class.java)) }) {
                Text(stringResource(R.string.board_effects))
            }
        }
        if (editing) {
            Text(
                stringResource(if (grid) R.string.board_hint_grid else R.string.board_hint_free),
                color = Color(HINT),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        KineticaBoard(
            state,
            Modifier.weight(1f).fillMaxWidth(),
            editing = editing,
            onLongPress = { editing = true },
            stackAutoAdvanceMs = 5_000,
        ) { id -> StoreWidget(state.widgetKeyOf(id) ?: id, id, data) }
    }
}

/** 배치 저장소. 배치 방식마다 따로 저장한다. */
object BoardStore {
    private const val PREFS = "kinetica-board"

    fun open(context: Context, grid: Boolean): KineticaBoardState = KineticaBoardState.create(
        storeCatalog,
        if (grid) BoardArrangement.Grid(columns = 2) else BoardArrangement.Free(),
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(grid), null),
    )

    fun save(context: Context, grid: Boolean, state: KineticaBoardState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key(grid), state.toJson()).apply()
    }

    private fun key(grid: Boolean) = if (grid) "grid" else "free"
}

private const val SCREEN_BACKGROUND = 0xFFF3F4F8
private const val HINT = 0xFF6B7280
