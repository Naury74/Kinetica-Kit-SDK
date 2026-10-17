// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.naury.kinetica.editor.StudioToolbarButton
import com.naury.kinetica.editor.StudioToolbarGroup
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
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KSheet
import com.naury.kinetica.ui.KToastHost
import com.naury.kinetica.ui.SurfaceStyles
import com.naury.kinetica.ui.kClickable
import com.naury.kinetica.ui.rememberKToastState
import java.io.File
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 화면 만들기 데모. 운영자가 앱 안에서 도형, 텍스트, 자동 레이아웃, 컴포넌트, 애니메이션, 호스트 위젯, 데이터 연결로 화면을 디자인한다.
 * 도구 모음 끝에는 사용자 화면 미리보기, 예제와 대시보드 열기, 사진 추가, 저장, PNG와 목업 내보내기 버튼이 있다.
 * 만든 문서는 그대로 서버 화면 JSON으로 쓸 수 있다.
 */
@Composable
internal fun StudioDemo(onBack: () -> Unit, model: StudioDemoModel = viewModel()) {
    var previewing by rememberSaveable { mutableStateOf(false) }
    val sales = rememberLiveSales()
    val session by model.session.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val toast = rememberKToastState()
    LaunchedEffect(message) {
        message?.let {
            model.messageShown()
            toast.show(it)
        }
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(model::addPhoto) }
    var mockup by remember { mutableStateOf(false) }
    var more by rememberSaveable { mutableStateOf(false) }
    val export =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
            uri?.let { model.export(it, mockup) }
        }
    val slots = remember(sales) { HostWidgets.slots(sales) }
    val theme = StudioTheme.system()
    Column(Modifier.fillMaxSize().background(theme.canvasBackground).statusBarsPadding().navigationBarsPadding()) {
        if (previewing) {
            val document by session.document.collectAsStateWithLifecycle()
            PreviewBack(theme.accent) { previewing = false }
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
                onExit = onBack,
                toolbarEnd = {
                    StudioToolbarGroup {
                        StudioToolbarButton("미리보기", { previewing = true }, icon = ShowcaseIcons.Expand)
                        StudioToolbarButton("사진", {
                            photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }, icon = KIcons.Plus)
                        StudioToolbarButton("저장", model::save, icon = KIcons.Download)
                    }
                    StudioToolbarButton("더 보기", { more = true }, icon = KIcons.Grid)
                },
            )
        }
    }
    val moreActions = studioMoreActions(model) { asMockup ->
        mockup = asMockup
        export.launch(if (asMockup) "kinetica-mockup.png" else "kinetica-scene.png")
    }
    StudioMoreSheet(more, theme.isDark, { more = false }, moreActions)
    KToastHost(toast)
}

/** 2초마다 하루씩 밀려나는 최근 7일 매출(0..1). 매출 그래프 위젯에 넘긴다. */
@Composable
private fun rememberLiveSales(): List<Float> {
    var sales by remember { mutableStateOf(List(7) { Random.nextFloat() }) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            sales = sales.drop(1) + Random.nextFloat()
        }
    }
    return sales
}

/** 자주 쓰지 않는 예제 열기와 내보내기를 모은 바텀 시트. 줄을 누르면 시트를 닫고 동작을 실행한다. */
@Composable
private fun StudioMoreSheet(
    visible: Boolean,
    dark: Boolean,
    onDismiss: () -> Unit,
    actions: List<Triple<String, ImageVector, () -> Unit>>,
) {
    KSheet(visible, onDismiss, surface = if (dark) SurfaceStyles.LiquidDark else SurfaceStyles.Liquid, title = "더 보기") {
        Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
            actions.forEach { (label, icon, action) ->
                MoreRow(label, icon) {
                    onDismiss()
                    action()
                }
            }
        }
    }
}

/** 미리보기에서 편집기로 돌아가는 버튼. 편집기 도구 모음 버튼처럼 높이 44dp 캡슐에 18dp 아이콘을 둔다. */
@Composable
private fun PreviewBack(color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .kClickable(CircleShape, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShowcaseIcon(KIcons.ChevronBack, 18.dp, color, Modifier.padding(end = 6.dp))
        Text("편집으로 돌아가기", color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** 더 보기 시트의 한 줄. 높이 52dp, 앞 아이콘 22dp, 모서리 14dp의 눌림 표시를 쓴다. */
@Composable
private fun MoreRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .kClickable(shape, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShowcaseIcon(icon, 22.dp, modifier = Modifier.padding(end = 14.dp))
        Label(label, 16, FontWeight.Medium)
    }
}

/** 더 보기 시트에 넣을 동작. 예제를 열거나 PNG와 목업으로 내보낸다. */
private fun studioMoreActions(model: StudioDemoModel, export: (mockup: Boolean) -> Unit): List<Triple<String, ImageVector, () -> Unit>> =
    listOf(
        Triple("예제 장면 열기", ShowcaseIcons.Sparkle) { model.open(DemoScene.create(), "예제 장면을 열었어요") },
        Triple("대시보드 예제 열기", KIcons.Grid) { model.open(DashboardScene.create(), "대시보드 예제를 열었어요") },
        Triple("PNG로 내보내기", KIcons.Download) { export(false) },
        Triple("휴대폰 목업으로 내보내기", ShowcaseIcons.Phone) { export(true) },
    )

private val DATA = SceneData.of(
    DashboardScene.SALES_TODAY to DataValue.Number(1_284_000.0),
    DashboardScene.ORDER_COUNT to DataValue.Number(42.0),
    DashboardScene.STORE_OPEN to DataValue.Bool(true),
    DashboardScene.GREETING to DataValue.Text("안녕하세요, 사장님"),
)

/** 편집 세션이 화면보다 오래 살아 있어 화면을 회전해도 실행 취소 기록이 남는다. 저장한 장면이 있으면 그 장면으로 연다. */
internal class StudioDemoModel(application: Application) : AndroidViewModel(application) {
    private val repository = FileSceneRepository(File(application.filesDir, "scenes"))
    private val exporter = PngExporter(application)
    private val imageLoader = ContentImageLoader(application)
    private val resolver = application.contentResolver
    private val current = MutableStateFlow(openSession(DashboardScene.create()))
    private val notice = MutableStateFlow<String?>(null)

    val session: StateFlow<KineticaSession> = current.asStateFlow()

    /** 잠깐 띄울 토스트 문구. 보여 준 뒤 [messageShown]을 부른다. */
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
                SaveResult.Saved -> "저장했어요. 다음에 열어도 이 장면이 열려요"
                is SaveResult.Failed -> "저장하지 못했어요"
            }
        }
    }

    /** 고른 사진을 원본 비율 그대로 가로 600 단위 이미지 노드로 넣는다. 앱을 다시 실행해도 열리도록 영구 읽기 권한을 요청해 둔다. */
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

    /** 1080 픽셀 너비 PNG로 내보낸다. [mockup]이면 휴대폰 목업 프레임에 넣는다. */
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
