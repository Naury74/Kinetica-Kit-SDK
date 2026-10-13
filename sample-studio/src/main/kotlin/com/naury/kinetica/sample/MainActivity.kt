package com.naury.kinetica.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.naury.kinetica.editor.KineticaEditor
import com.naury.kinetica.editor.StudioTheme

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
    val snackbar = remember { SnackbarHostState() }
    val theme = remember { StudioTheme() }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::addPhoto)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        uri?.let(viewModel::export)
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = theme.canvasBackground,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        when (val current = state) {
            StudioState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

            is StudioState.Ready -> {
                current.message?.let { message ->
                    val text = stringResource(message)
                    LaunchedEffect(current) {
                        snackbar.showSnackbar(text)
                        viewModel.messageShown()
                    }
                }
                KineticaEditor(
                    session = current.session,
                    theme = theme,
                    modifier = Modifier.padding(padding).safeDrawingPadding(),
                    toolbarEnd = {
                        TextButton(onClick = {
                            photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) { Text(stringResource(R.string.sample_photo), color = theme.accent) }
                        TextButton(onClick = viewModel::openDemo) { Text(stringResource(R.string.sample_demo), color = theme.accent) }
                        TextButton(onClick = viewModel::save) { Text(stringResource(R.string.sample_save), color = theme.accent) }
                        TextButton(onClick = { exportLauncher.launch("kinetica-scene.png") }) {
                            Text(stringResource(R.string.sample_export), color = theme.accent)
                        }
                    },
                )
            }
        }
    }
}
