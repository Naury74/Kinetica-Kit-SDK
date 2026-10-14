package com.naury.kinetica.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.naury.kinetica.core.scene.SceneData
import com.naury.kinetica.editor.KineticaEditor
import com.naury.kinetica.editor.StudioTheme
import com.naury.kinetica.player.KineticaPlayer
import com.naury.kinetica.player.SceneFit
import com.naury.kinetica.render.SceneSlots

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 편집 화면은 항상 어두운 색이라 시스템 막대 아이콘을 밝게 둔다.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                StudioScreen()
            }
        }
    }
}

@Composable
private fun StudioScreen(viewModel: StudioViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val data by viewModel.data.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val slots = remember(sales) { HostWidgets.slots(sales) }
    var previewing by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val theme = remember { StudioTheme() }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::addPhoto)
    }
    var mockup by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        uri?.let { viewModel.export(it, mockup) }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = theme.canvasBackground,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        when (val current = state) {
            StudioState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

            is StudioState.Ready -> {
                MessageEffect(current, snackbar, viewModel::messageShown)
                val modifier = Modifier.padding(padding).safeDrawingPadding()
                if (previewing) {
                    Preview(current, data, slots, theme, modifier, onAction = viewModel::onAction, onClose = { previewing = false })
                    return@Scaffold
                }
                KineticaEditor(
                    session = current.session,
                    theme = theme,
                    modifier = modifier,
                    slots = slots,
                    hostViews = HostWidgets.options,
                    data = data,
                    toolbarEnd = {
                        ToolbarButton(R.string.sample_preview, theme) { previewing = true }
                        ToolbarButton(R.string.sample_dashboard, theme, viewModel::openDashboard)
                        ToolbarButton(R.string.sample_photo, theme) {
                            photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        ToolbarButton(R.string.sample_demo, theme, viewModel::openDemo)
                        ToolbarButton(R.string.sample_save, theme, viewModel::save)
                        ExportButtons(theme) { asMockup, name ->
                            mockup = asMockup
                            exportLauncher.launch(name)
                        }
                    },
                )
            }
        }
    }
}

/** 화면 상태의 알림을 한 번 보여 주고 [onShown]으로 지운다. */
@Composable
private fun MessageEffect(state: StudioState.Ready, snackbar: SnackbarHostState, onShown: () -> Unit) {
    val message = state.message ?: return
    val text = state.messageArg?.let { stringResource(message, it) } ?: stringResource(message)
    LaunchedEffect(state) {
        snackbar.showSnackbar(text)
        onShown()
    }
}

/** PNG와 휴대폰 틀 목업 내보내기. [export]는 목업 여부와 기본 파일 이름을 받는다. */
@Composable
private fun ExportButtons(theme: StudioTheme, export: (mockup: Boolean, name: String) -> Unit) {
    ToolbarButton(R.string.sample_export, theme) { export(false, "kinetica-scene.png") }
    ToolbarButton(R.string.sample_export_mockup, theme) { export(true, "kinetica-mockup.png") }
}

/** 앱 사용자가 보게 될 화면. 호스트 뷰는 눌리고, 데이터는 실시간이며, 동작은 [onAction]으로 온다. */
@Composable
@Suppress("LongParameterList")
private fun Preview(
    state: StudioState.Ready,
    data: SceneData,
    slots: SceneSlots,
    theme: StudioTheme,
    modifier: Modifier,
    onAction: (String) -> Unit,
    onClose: () -> Unit,
) {
    val document by state.session.document.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.sample_back_to_edit), color = theme.accent) }
            Text(stringResource(R.string.sample_preview_hint), color = theme.onPanel, style = MaterialTheme.typography.bodySmall)
        }
        KineticaPlayer(
            document = document,
            modifier = Modifier.fillMaxSize(),
            data = data,
            slots = slots,
            onAction = onAction,
            // 화면 모양대로 펼쳐, 아래 붙은 버튼·좌우로 늘어나는 그래프 같은 제약을 실제 기기 크기에서 보여 준다.
            fit = SceneFit.Responsive,
        )
    }
}

@Composable
private fun ToolbarButton(@StringRes label: Int, theme: StudioTheme, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(stringResource(label), color = theme.accent) }
}
