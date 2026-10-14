package com.naury.kinetica.player

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import com.naury.kinetica.core.scene.SceneData
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.render.SceneSlots
import com.naury.kinetica.serialization.RemoteSceneLoader

/** XML 화면에서 호스트 뷰 노드(키)마다 넣을 [View]를 만든다. 같은 키로는 재생기가 살아 있는 동안 한 번만 부른다. */
public fun interface SceneViewFactory {
    public fun create(key: String, context: Context): View
}

/** 동작이 있는 노드를 눌렀을 때. [action]은 노드의 동작 글자이며 [com.naury.kinetica.core.scene.SceneAction.parse]로 나눌 수 있다. */
public fun interface OnSceneActionListener {
    public fun onAction(action: String)
}

/**
 * [KineticaPlayer]·[RemoteKineticaPlayer]를 XML·View 화면에 넣는 [FrameLayout]. Compose를 쓰지 않는 화면도 장면·서버 화면을 재생한다.
 *
 * ```kotlin
 * val player = findViewById<KineticaPlayerView>(R.id.promo)
 * player.setLoader(loader)                       // 서버 화면. 또는 player.setDocument(document)
 * player.setHostViews { key, context -> TextView(context) }
 * player.setOnActionListener { action -> handle(SceneAction.parse(action)) }
 * player.data = SceneData.of("sales.today" to DataValue.Number(1_284_000.0))
 * ```
 *
 * 문서·로더·데이터는 메인 스레드에서 바꾼다. 처음 붙을 때와 [setLoader]로 로더를 바꿀 때 서버에서 새로 받는다.
 */
public class KineticaPlayerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    FrameLayout(context, attrs, defStyleAttr) {
    private var source by mutableStateOf<Source?>(null)
    private var dataState by mutableStateOf(SceneData.Empty)
    private var factory by mutableStateOf<SceneViewFactory?>(null)
    private var listener by mutableStateOf<OnSceneActionListener?>(null)
    private var fitState by mutableStateOf(SceneFit.Contain)
    private var autoplayState by mutableStateOf(true)

    /** 바인딩에 쓸 호스트 데이터. 바꾸면 바로 다시 그린다(숫자는 올라가며 바뀐다). */
    public var data: SceneData
        get() = dataState
        set(value) {
            dataState = value
        }

    /** 영역에 맞추는 방식. 기본은 [SceneFit.Contain]. */
    public var fit: SceneFit
        get() = fitState
        set(value) {
            fitState = value
        }

    /** 문서의 첫 timeline을 바로 재생한다. 기본 `true`. */
    public var autoplay: Boolean
        get() = autoplayState
        set(value) {
            autoplayState = value
        }

    init {
        addView(
            ComposeView(context).apply { setContent { PlayerContent() } },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    /** 앱에 든 문서를 재생한다. */
    public fun setDocument(document: SceneDocument) {
        source = Source.Local(document)
    }

    /** 서버 화면을 재생한다. 받기 전에는 비어 있고, 받지 못하면 로더의 캐시·기본 문서를 보여 준다. */
    public fun setLoader(loader: RemoteSceneLoader) {
        source = Source.Remote(loader)
    }

    /** 호스트 뷰 노드에 넣을 View. `null`이면 호스트 뷰 노드는 비어 보인다. */
    public fun setHostViews(factory: SceneViewFactory?) {
        this.factory = factory
    }

    public fun setOnActionListener(listener: OnSceneActionListener?) {
        this.listener = listener
    }

    @Composable
    private fun PlayerContent() {
        val slots = rememberViewSlots(factory)
        val onAction: (String) -> Unit = { action -> listener?.onAction(action) }
        when (val current = source) {
            is Source.Local -> {
                val timeline = current.document.timelines.firstOrNull()
                val playback = timeline?.let { rememberPlaybackController(it) }
                if (autoplayState && playback != null) LaunchedEffect(playback) { playback.play() }
                KineticaPlayer(
                    current.document,
                    Modifier.fillMaxSize(),
                    playback,
                    data = dataState,
                    slots = slots,
                    onAction = onAction,
                    fit = fitState,
                )
            }

            is Source.Remote -> RemoteKineticaPlayer(
                current.loader,
                Modifier.fillMaxSize(),
                data = dataState,
                slots = slots,
                onAction = onAction,
                fit = fitState,
                autoplay = autoplayState,
            )

            null -> Unit
        }
    }

    @Composable
    private fun rememberViewSlots(factory: SceneViewFactory?): SceneSlots = remember(factory) {
        if (factory == null) emptyMap() else ViewSlots(factory)
    }

    private sealed interface Source {
        data class Local(val document: SceneDocument) : Source

        data class Remote(val loader: RemoteSceneLoader) : Source
    }
}

/** 키를 물으면 그 키의 View를 만드는 슬롯. 문서에 어떤 키가 있는지 미리 몰라도 된다. */
private class ViewSlots(private val factory: SceneViewFactory) : AbstractMap<String, @Composable () -> Unit>() {
    override val entries: Set<Map.Entry<String, @Composable () -> Unit>> get() = emptySet()

    override fun containsKey(key: String): Boolean = true

    override fun get(key: String): @Composable () -> Unit = {
        AndroidView(factory = { context -> factory.create(key, context) }, modifier = Modifier.fillMaxSize())
    }
}
