// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.naury.kinetica.core.command.CommandResult
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.DataValue
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneData
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.editor.KineticaEditor
import com.naury.kinetica.editor.StudioTheme
import com.naury.kinetica.export.DeviceFrame
import com.naury.kinetica.export.ExportRequest
import com.naury.kinetica.export.ExportResult
import com.naury.kinetica.export.MockupFrame
import com.naury.kinetica.export.PngExporter
import com.naury.kinetica.history.KineticaSession
import com.naury.kinetica.history.SessionOpenResult
import com.naury.kinetica.player.KineticaPlayer
import com.naury.kinetica.player.SceneFit
import com.naury.kinetica.render.ContentImageLoader
import com.naury.kinetica.serialization.FileSceneRepository
import com.naury.kinetica.serialization.LoadResult
import com.naury.kinetica.serialization.SaveResult
import java.io.File
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 화면 만들기: 운영자가 앱 안에서 화면을 디자인한다(도형·글자·자동 배치·컴포넌트·움직임·호스트 위젯·데이터 연결).
 * 도구 줄 끝: 미리보기(사용자 화면), 예제·대시보드 열기, 사진 넣기, 저장, PNG·목업 내보내기. 만든 문서는 서버 화면 JSON으로 쓸 수 있다.
 */
@Composable
internal fun StudioDemo(onBack: () -> Unit, model: StudioDemoModel = viewModel()) {
    val context = LocalContext.current
    var previewing by rememberSaveable { mutableStateOf(false) }
    var sales by remember { mutableStateOf(List(7) { Random.nextFloat() }) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            sales = sales.drop(1) + Random.nextFloat()
        }
    }
    val session by model.session.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            model.messageShown()
        }
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(model::addPhoto) }
    var mockup by remember { mutableStateOf(false) }
    val export =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
            uri?.let { model.export(it, mockup) }
        }
    val slots = remember(sales) { HostWidgets.slots(sales) }
    val theme = StudioTheme.system()
    Column(Modifier.fillMaxSize().background(theme.canvasBackground).statusBarsPadding().navigationBarsPadding()) {
        if (previewing) {
            val document by session.document.collectAsStateWithLifecycle()
            TextButton(onClick = { previewing = false }) { Text("‹ 편집으로 돌아가기", color = theme.accent) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                KineticaPlayer(document, Modifier.fillMaxSize(), data = DATA, slots = slots, fit = SceneFit.Responsive)
            }
        } else {
            KineticaEditor(
                session,
                Modifier.weight(1f).fillMaxWidth(),
                theme = theme,
                slots = slots,
                hostViews = HostWidgets.options,
                data = DATA,
                toolbarEnd = {
                    studioActions(model, onBack, {
                        previewing = true
                    }, { photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { asMockup ->
                        mockup = asMockup
                        export.launch(if (asMockup) "kinetica-mockup.png" else "kinetica-scene.png")
                    }.forEach { (label, action) -> TextButton(onClick = action) { Text(label, color = theme.accent) } }
                },
            )
        }
    }
}

/** 편집기 도구 줄 끝 버튼들. */
private fun studioActions(
    model: StudioDemoModel,
    onBack: () -> Unit,
    preview: () -> Unit,
    pickPhoto: () -> Unit,
    export: (mockup: Boolean) -> Unit,
): List<Pair<String, () -> Unit>> = listOf(
    "나가기" to onBack,
    "미리보기" to preview,
    "예제" to { model.open(DemoScene.create(), "예제 장면을 열었어요") },
    "대시보드" to { model.open(DashboardScene.create(), "대시보드 예제를 열었어요") },
    "사진" to pickPhoto,
    "저장" to model::save,
    "PNG" to { export(false) },
    "목업" to { export(true) },
)

private val DATA = SceneData.of(
    DashboardScene.SALES_TODAY to DataValue.Number(1_284_000.0),
    DashboardScene.ORDER_COUNT to DataValue.Number(42.0),
    DashboardScene.STORE_OPEN to DataValue.Bool(true),
    DashboardScene.GREETING to DataValue.Text("안녕하세요, 사장님"),
)

/** 편집 세션은 화면보다 오래 살아 화면을 돌려도 실행 취소 기록이 남는다. 저장한 장면이 있으면 그것으로 연다. */
internal class StudioDemoModel(application: Application) : AndroidViewModel(application) {
    private val repository = FileSceneRepository(File(application.filesDir, "scenes"))
    private val exporter = PngExporter(application)
    private val imageLoader = ContentImageLoader(application)
    private val resolver = application.contentResolver
    private val current = MutableStateFlow(openSession(DashboardScene.create()))
    private val notice = MutableStateFlow<String?>(null)

    val session: StateFlow<KineticaSession> = current.asStateFlow()

    /** 잠깐 띄울 알림. 보여 준 뒤 [messageShown]을 부른다. */
    val message: StateFlow<String?> = notice.asStateFlow()

    init {
        viewModelScope.launch {
            (repository.load(SCENE_ID) as? LoadResult.Loaded)?.document?.let { replace(it) }
        }
    }

    fun open(document: SceneDocument, text: String) {
        replace(document)
        notice.value = text
    }

    fun save() {
        viewModelScope.launch {
            notice.value = when (repository.save(SCENE_ID, current.value.document.value)) {
                SaveResult.Saved -> "저장했어요. 다시 열어도 이 장면이에요"
                is SaveResult.Failed -> "저장하지 못했어요"
            }
        }
    }

    /** 고른 사진을 원본 비율대로 가로 600 단위 이미지 노드로 넣는다. 다시 실행해도 열리도록 지속 읽기 권한을 시도한다. */
    fun addPhoto(uri: Uri) {
        runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        viewModelScope.launch {
            val image = imageLoader.load(uri.toString()) ?: return@launch run { notice.value = "사진을 읽지 못했어요" }
            val width = PHOTO_WIDTH
            val height = width * image.height / image.width
            val session = current.value
            val artboard = session.document.value.artboard
            val node = SceneNode(
                NodeId("photo-" + UUID.randomUUID()),
                NodeContent.Image(Size(width, height), uri.toString()),
                transform = Transform2D(
                    (artboard.width - width) / 2,
                    (artboard.height - height) / 2,
                    pivotX = width / 2,
                    pivotY =
                        height / 2,
                ),
            )
            if (session.commands.execute(SceneCommand.AddNode(node)) is CommandResult.Applied) session.select(listOf(node.id))
        }
    }

    /** 1080 픽셀 너비 PNG로 내보낸다. [mockup]이면 휴대폰 틀에 넣는다. */
    fun export(uri: Uri, mockup: Boolean) {
        val request = ExportRequest(widthPx = EXPORT_WIDTH, mockup = if (mockup) MockupFrame(DeviceFrame.Phone) else null)
        viewModelScope.launch {
            val result = exporter.exportToUri(current.value.document.value, request, resolver, uri)
            notice.value = if (result is ExportResult.Success) "내보냈어요" else "내보내지 못했어요"
        }
    }

    fun messageShown() {
        notice.value = null
    }

    private fun replace(document: SceneDocument) {
        val opened = (KineticaSession.create(document) as? SessionOpenResult.Opened)?.session ?: return
        current.value.close()
        current.value = opened
    }

    private fun openSession(document: SceneDocument) = (KineticaSession.create(document) as SessionOpenResult.Opened).session

    override fun onCleared() = current.value.close()

    private companion object {
        const val SCENE_ID = "studio"
        const val PHOTO_WIDTH = 600.0
        const val EXPORT_WIDTH = 1080
    }
}
