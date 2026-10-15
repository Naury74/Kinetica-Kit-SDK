// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.naury.kinetica.board.BoardArrangement
import com.naury.kinetica.board.BoardStorage
import com.naury.kinetica.board.KineticaBoard
import com.naury.kinetica.board.rememberKineticaBoardState
import com.naury.kinetica.ui.GlassScaffold
import com.naury.kinetica.ui.GlassStyles

/**
 * 대시보드 보드 데모. 가게 주인이 홈 화면 위젯을 직접 배치한다. 위젯은 호스트 앱의 평범한 Compose 화면이고, 보드는 위치와 크기만 관리한다.
 * 길게 누르면 편집 모드가 된다. 위젯을 위쪽으로 끌면 스냅 레이아웃을 고를 수 있고, 왼쪽이나 오른쪽 가장자리에 놓으면 절반,
 * 모서리에 놓으면 4분의 1 크기로 붙는다. 다른 위젯 가운데에 놓으면 스택으로 묶인다.
 */
@Composable
internal fun BoardDemo(onBack: () -> Unit, viewModel: StoreViewModel = viewModel()) {
    val context = LocalContext.current
    val data by viewModel.data.collectAsStateWithLifecycle()
    var grid by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val key = if (grid) "grid" else "free"
    val state = rememberKineticaBoardState(
        storeCatalog,
        BoardStorage.sharedPreferences(context, "showcase-board", key),
        if (grid) BoardArrangement.Grid(columns = 2) else BoardArrangement.Free(),
        key = "board-$key",
    )
    val history by state.history.state.collectAsState()
    GlassScaffold(
        Modifier.fillMaxSize(),
        style = GlassStyles.OneUi,
        background = { Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFEDEBFF), Color(0xFFF6F7FB))))) },
        topBar = {
            Column {
                DemoTopBar("대시보드 보드", "길게 눌러 편집하고, 위로 끌어 스냅 레이아웃을 골라 보세요", onBack)
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(if (editing) "완료" else "편집", filled = editing) { editing = !editing }
                    if (editing) {
                        Pill("되돌리기") { if (history.canUndo) state.history.undo() }
                        Pill("다시") { if (history.canRedo) state.history.redo() }
                    }
                    Pill(if (grid) "격자" else "자유 배치") { grid = !grid }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(padding.calculateTopPadding()))
            KineticaBoard(
                state,
                Modifier.weight(1f).fillMaxWidth(),
                editing = editing,
                onLongPress = { editing = true },
                stackAutoAdvanceMs = 5_000,
            ) { id -> StoreWidget(state.widgetKeyOf(id) ?: id, id, data) }
        }
    }
}
