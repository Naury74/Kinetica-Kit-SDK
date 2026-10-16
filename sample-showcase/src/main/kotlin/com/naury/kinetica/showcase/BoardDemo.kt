// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.naury.kinetica.board.BoardArrangement
import com.naury.kinetica.board.BoardStorage
import com.naury.kinetica.board.GridColumns
import com.naury.kinetica.board.KineticaBoard
import com.naury.kinetica.board.KineticaBoardState
import com.naury.kinetica.board.rememberKineticaBoardState
import com.naury.kinetica.render.HOST_VIEW_UNITS_PER_DP
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
        // 위젯 처음 크기를 바꾸면 저장 키 뒤 버전을 올려, 예전 배치 대신 새 기본 배치로 연다.
        BoardStorage.sharedPreferences(context, "showcase-board", "$key-v2"),
        if (grid) {
            // 넓은 화면에서는 칸 폭 160dp 이상을 유지하며 열을 늘린다(최대 6열). 열 수마다 배치를 따로 기억한다.
            BoardArrangement.Grid(columns = 2, gapDp = BOARD_GAP.value, columnsByWidth = GridColumns.Adaptive(160f, maxColumns = 6))
        } else {
            BoardArrangement.Free(BOARD_GAP.value)
        },
        key = "board-$key",
    )
    val history by state.history.state.collectAsState()
    // 넓은 창(840dp 이상)의 격자 배치에서는 열을 늘릴 수 있도록 상단 바, 칩 줄, 보드를 함께 넓힌다.
    // 자유 배치는 폭 비율대로 위젯을 늘리므로 읽기 폭으로 묶어 위젯이 지나치게 커지지 않게 한다.
    val contentWidth = if (grid) demoContentWidth() else READING_WIDTH
    GlassScaffold(
        Modifier.fillMaxSize(),
        style = GlassStyles.OneUi,
        background = { Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFEDEBFF), Color(0xFFF6F7FB))))) },
        topBar = {
            Column {
                DemoTopBar("대시보드 보드", "길게 눌러 편집하고, 위로 끌어 스냅 레이아웃을 골라 보세요", onBack, contentWidth)
                BoardChips(contentWidth) {
                    Pill(if (editing) "완료" else "편집", filled = editing) { editing = !editing }
                    if (editing) {
                        Pill("되돌리기", Modifier.alpha(if (history.canUndo) 1f else 0.4f)) { if (history.canUndo) state.history.undo() }
                        Pill("다시", Modifier.alpha(if (history.canRedo) 1f else 0.4f)) { if (history.canRedo) state.history.redo() }
                    }
                    Pill(if (grid) "격자" else "자유 배치") { grid = !grid }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(padding.calculateTopPadding() + 8.dp))
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                contentAlignment = Alignment.TopCenter,
            ) {
                KineticaBoard(
                    state,
                    Modifier.width(boardWidth(maxWidth, grid, contentWidth)).fillMaxHeight(),
                    editing = editing,
                    onLongPress = { editing = true },
                    stackAutoAdvanceMs = 5_000,
                    widgetCornerRadius = 20.dp,
                ) { id -> CappedFontScale { StoreWidget(state.widgetKeyOf(id) ?: id, id, data) } }
            }
        }
    }
}

/** 칩 줄. 상단 바 캡슐과 같은 폭, 같은 시작 위치에 둔다. */
@Composable
private fun BoardChips(contentWidth: Dp, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.TopCenter,
    ) {
        FlowRow(
            Modifier.padding(horizontal = 16.dp).widthIn(max = contentWidth).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

/**
 * 위젯 바깥쪽 가장자리가 상단 바 캡슐 가장자리와 맞도록 보드 폭을 정한다. 보드는 가장자리에 [BOARD_GAP]만큼 간격을 두는데,
 * 격자 배치는 이 간격을 그대로 쓰고 자유 배치는 기준 폭(약 393dp)에 대한 화면 폭 비율만큼 늘려 쓴다.
 */
private fun boardWidth(available: Dp, grid: Boolean, contentWidth: Dp): Dp {
    val capsule = minOf(available - 32.dp, contentWidth)
    val width = if (grid) capsule + BOARD_GAP * 2 else capsule / (1f - 2f * BOARD_GAP.value / REFERENCE_DP)
    return minOf(available, width)
}

private val BOARD_GAP = 12.dp
private val REFERENCE_DP = (KineticaBoardState.REFERENCE_WIDTH / HOST_VIEW_UNITS_PER_DP).toFloat()
