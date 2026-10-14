package com.naury.kinetica.sample

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naury.kinetica.core.command.CommandResult
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.DataValue
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneData
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.export.DeviceFrame
import com.naury.kinetica.export.ExportRequest
import com.naury.kinetica.export.ExportResult
import com.naury.kinetica.export.MockupFrame
import com.naury.kinetica.export.PngExporter
import com.naury.kinetica.history.KineticaSession
import com.naury.kinetica.history.SessionOpenResult
import com.naury.kinetica.render.ContentImageLoader
import com.naury.kinetica.serialization.FileSceneRepository
import com.naury.kinetica.serialization.LoadResult
import com.naury.kinetica.serialization.SaveResult
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 장면 하나를 앱 전용 폴더에서 열고 저장한다. 세션은 화면 회전에도 유지된다. */
class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FileSceneRepository(File(application.filesDir, "scenes"))
    private val exporter = PngExporter(application)
    private val resolver = application.contentResolver
    private val imageLoader = ContentImageLoader(application)
    private val mutableState = MutableStateFlow<StudioState>(StudioState.Loading)
    val state: StateFlow<StudioState> = mutableState.asStateFlow()

    private val mutableData = MutableStateFlow(liveData(Random(SEED)))
    private val mutableSales = MutableStateFlow(List(SALES_DAYS) { Random(it).nextFloat() })

    /** 호스트 데이터. 실제 앱이라면 서버·DB에서 온다. 여기서는 2초마다 바뀌어 바인딩이 실시간으로 반영되는 것을 보여 준다. */
    val data: StateFlow<SceneData> = mutableData.asStateFlow()

    /** 매출 그래프 위젯(호스트 뷰)에 넘기는 막대 높이 0..1. */
    val sales: StateFlow<List<Float>> = mutableSales.asStateFlow()

    init {
        viewModelScope.launch {
            val random = Random(System.currentTimeMillis())
            while (true) {
                delay(LIVE_INTERVAL_MS)
                mutableData.value = liveData(random)
                mutableSales.value = mutableSales.value.drop(1) + random.nextFloat()
            }
        }
        viewModelScope.launch {
            val loaded = repository.load(SCENE_ID)
            val document = (loaded as? LoadResult.Loaded)?.document ?: emptyScene()
            val message = if (loaded is LoadResult.Failed || loaded is LoadResult.IoFailed) R.string.sample_load_failed else null
            val session = when (val opened = KineticaSession.create(document)) {
                is SessionOpenResult.Opened -> opened.session
                is SessionOpenResult.Rejected -> (KineticaSession.create(emptyScene()) as SessionOpenResult.Opened).session
            }
            mutableState.value = StudioState.Ready(session, message)
        }
    }

    fun save() {
        val ready = mutableState.value as? StudioState.Ready ?: return
        viewModelScope.launch {
            val message = when (repository.save(SCENE_ID, ready.session.document.value)) {
                SaveResult.Saved -> R.string.sample_saved
                is SaveResult.Failed -> R.string.sample_save_failed
            }
            mutableState.value = ready.copy(message = message)
        }
    }

    /** 호스트 위젯·데이터 바인딩·동작이 있는 대시보드 예제를 연다. */
    fun openDashboard() = openScene(DashboardScene.create(), R.string.sample_dashboard_opened)

    /** 장면의 동작을 받는다. 예제는 "refresh"만 쓴다. */
    fun onAction(action: String) {
        val ready = mutableState.value as? StudioState.Ready ?: return
        if (action == "refresh") {
            mutableData.value = liveData(Random(System.nanoTime()))
            mutableSales.value = List(SALES_DAYS) { Random(System.nanoTime() + it).nextFloat() }
        }
        mutableState.value = ready.copy(message = R.string.sample_action_received, messageArg = action)
    }

    /** 지금 장면을 예제 장면으로 바꾼다. 실행 취소 기록은 새로 시작한다. 저장 전까지 파일은 그대로다. */
    fun openDemo() = openScene(DemoScene.create(), R.string.sample_demo_opened)

    private fun openScene(document: SceneDocument, message: Int) {
        val ready = mutableState.value as? StudioState.Ready ?: return
        val opened = KineticaSession.create(document) as? SessionOpenResult.Opened ?: return
        ready.session.close()
        mutableState.value = StudioState.Ready(opened.session, message)
    }

    /**
     * 사용자가 고른 사진을 이미지 노드로 넣는다. 다시 실행해도 열리도록 지속 읽기 권한을 시도하고, 원본 비율대로 가로 600 단위로 놓는다.
     */
    fun addPhoto(uri: Uri) {
        val ready = mutableState.value as? StudioState.Ready ?: return
        runCatchingPermission { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        viewModelScope.launch {
            val image = imageLoader.load(uri.toString())
            if (image == null) {
                mutableState.value = ready.copy(message = R.string.sample_photo_failed)
                return@launch
            }
            val width = PHOTO_WIDTH
            val height = width * image.height / image.width
            val artboard = ready.session.document.value.artboard
            val node = SceneNode(
                id = NodeId("photo-" + UUID.randomUUID()),
                content = NodeContent.Image(Size(width, height), uri.toString()),
                transform = Transform2D(
                    translationX = (artboard.width - width) / 2.0,
                    translationY = (artboard.height - height) / 2.0,
                    pivotX = width / 2.0,
                    pivotY = height / 2.0,
                ),
            )
            if (ready.session.commands.execute(SceneCommand.AddNode(node)) is CommandResult.Applied) ready.session.select(listOf(node.id))
        }
    }

    private inline fun runCatchingPermission(block: () -> Unit) {
        try {
            block()
        } catch (_: SecurityException) {
            // 지속 권한을 주지 않는 제공자도 있다. 이번 실행 동안은 받은 임시 권한으로 보인다.
        }
    }

    /** 1080 픽셀 너비 PNG로 사용자가 고른 [uri]에 내보낸다. [mockup]이면 휴대폰 틀에 넣은 목업으로 내보낸다. */
    fun export(uri: Uri, mockup: Boolean = false) {
        val ready = mutableState.value as? StudioState.Ready ?: return
        val request = ExportRequest(widthPx = EXPORT_WIDTH, mockup = if (mockup) MockupFrame(DeviceFrame.Phone) else null)
        viewModelScope.launch {
            val result = exporter.exportToUri(ready.session.document.value, request, resolver, uri)
            val message = if (result is ExportResult.Success) R.string.sample_exported else R.string.sample_export_failed
            mutableState.value = (mutableState.value as? StudioState.Ready ?: ready).copy(message = message)
        }
    }

    fun messageShown() {
        (mutableState.value as? StudioState.Ready)?.let { mutableState.value = it.copy(message = null) }
    }

    override fun onCleared() {
        (mutableState.value as? StudioState.Ready)?.session?.close()
    }

    private fun liveData(random: Random): SceneData = SceneData.of(
        DashboardScene.SALES_TODAY to DataValue.Text(String.format(Locale.KOREA, "%,d원", BASE_SALES + random.nextInt(SALES_RANGE))),
        DashboardScene.ORDER_COUNT to DataValue.Number((BASE_ORDERS + random.nextInt(ORDER_RANGE)).toDouble()),
        DashboardScene.STORE_OPEN to DataValue.Bool(random.nextInt(OPEN_ODDS) != 0),
        DashboardScene.GREETING to DataValue.Text("안녕하세요, 사장님"),
    )

    private fun emptyScene() = SceneDocument.empty(Artboard(ARTBOARD_WIDTH, ARTBOARD_HEIGHT, ArgbColor(ARTBOARD_COLOR.toInt())), SCENE_ID)

    private companion object {
        const val SCENE_ID = "studio"
        const val ARTBOARD_WIDTH = 1080.0
        const val ARTBOARD_HEIGHT = 1920.0
        const val ARTBOARD_COLOR = 0xFF22262F
        const val EXPORT_WIDTH = 1080
        const val PHOTO_WIDTH = 600.0
        const val SEED = 7
        const val SALES_DAYS = 7
        const val LIVE_INTERVAL_MS = 2_000L
        const val BASE_SALES = 1_200_000
        const val SALES_RANGE = 400_000
        const val BASE_ORDERS = 80
        const val ORDER_RANGE = 40
        const val OPEN_ODDS = 4
    }
}

sealed interface StudioState {
    data object Loading : StudioState

    data class Ready(val session: KineticaSession, @StringRes val message: Int?, val messageArg: String? = null) : StudioState
}
