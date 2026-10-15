package com.naury.kinetica.player

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

/** XML 화면에서 호스트 뷰 노드의 키마다 넣을 [View]를 만든다. 재생기가 살아 있는 동안 키 하나에 한 번만 호출된다. */
public fun interface SceneViewFactory {
    public fun create(key: String, context: Context): View
}

/** 동작이 있는 노드를 눌렀을 때 호출된다. [action]은 노드의 동작 문자열이며 [com.naury.kinetica.core.scene.SceneAction.parse]로 파싱할 수 있다. */
public fun interface OnSceneActionListener {
    public fun onAction(action: String)
}

/**
 * [KineticaPlayer]와 [RemoteKineticaPlayer]를 XML 레이아웃에 넣을 수 있게 감싼 [FrameLayout]. Compose를 쓰지 않는 화면에서도 장면과 서버 화면을 재생할 수 있다.
 *
 * ```kotlin
 * val player = findViewById<KineticaPlayerView>(R.id.promo)
 * player.setLoader(loader)                       // 서버 화면. 또는 player.setDocument(document)
 * player.setHostViews { key, context -> TextView(context) }
 * player.setOnActionListener { action -> handle(SceneAction.parse(action)) }
 * player.data = SceneData.of("sales.today" to DataValue.Number(1_284_000.0))
 * ```
 *
 * 문서, 로더, 데이터는 메인 스레드에서 바꾼다. [setLoader]로 로더를 지정하면 바로 서버에서 받아 오고, 다시 받으려면 [refresh]를 호출한다.
 * XML 속성: `app:kinetica_fit`(`contain`, `responsive`), `app:kinetica_autoplay`(boolean). 앱 테마의 `kinetica_playerViewStyle`에
 * 스타일을 지정하면 모든 재생기의 기본값이 된다. 코드로 만들면 `contain`, 자동 재생이다.
 * Fragment 안에서는 `onViewCreated`마다 문서와 리스너를 다시 지정한다. View를 새로 만들면 호스트 View도 [SceneViewFactory]로 다시 만든다.
 */
public class KineticaPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.kinetica_playerViewStyle,
    defStyleRes: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {
    private var source by mutableStateOf<Source?>(null)
    private var dataState by mutableStateOf(SceneData.Empty)
    private var factory by mutableStateOf<SceneViewFactory?>(null)
    private var listener by mutableStateOf<OnSceneActionListener?>(null)
    private var fitState by mutableStateOf(SceneFit.Contain)
    private var autoplayState by mutableStateOf(true)
    private var refreshKey by mutableIntStateOf(0)

    /** 바인딩에 쓸 호스트 데이터. 바꾸면 바로 다시 그리며, 숫자 값은 애니메이션으로 올라가거나 내려간다. */
    public var data: SceneData
        get() = dataState
        set(value) {
            dataState = value
        }

    /** 장면을 영역에 맞추는 방식. 기본값은 [SceneFit.Contain]이다. */
    public var fit: SceneFit
        get() = fitState
        set(value) {
            fitState = value
        }

    /** 문서의 첫 timeline을 바로 재생할지. 기본값은 `true`다. */
    public var autoplay: Boolean
        get() = autoplayState
        set(value) {
            autoplayState = value
        }

    init {
        context.obtainStyledAttributes(attrs, R.styleable.KineticaPlayerView, defStyleAttr, defStyleRes).apply {
            try {
                if (getInt(R.styleable.KineticaPlayerView_kinetica_fit, 0) == 1) fitState = SceneFit.Responsive
                autoplayState = getBoolean(R.styleable.KineticaPlayerView_kinetica_autoplay, true)
            } finally {
                recycle()
            }
        }
        addView(
            ComposeView(context).apply { setContent { PlayerContent() } },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    /** 앱에 포함된 문서를 재생한다. */
    public fun setDocument(document: SceneDocument) {
        source = Source.Local(document)
    }

    /** 서버 화면을 재생한다. 받아 오기 전에는 비어 있고, 받지 못하면 로더의 캐시나 기본 문서를 보여 준다. */
    public fun setLoader(loader: RemoteSceneLoader) {
        source = Source.Remote(loader)
    }

    /** 당겨서 새로 고침처럼 서버 화면을 다시 받아 온다. [setDocument]로 넣은 문서를 재생 중이면 아무 일도 하지 않는다. */
    public fun refresh() {
        refreshKey++
    }

    /** 호스트 뷰 노드에 넣을 View를 만든다. `null`이면 호스트 뷰 노드는 비어 보인다. */
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
                refreshKey = refreshKey,
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

/** 요청받은 키의 View를 그때 만드는 슬롯 맵. 문서에 어떤 키가 있는지 미리 알 필요가 없다. */
private class ViewSlots(private val factory: SceneViewFactory) : AbstractMap<String, @Composable () -> Unit>() {
    override val entries: Set<Map.Entry<String, @Composable () -> Unit>> get() = emptySet()

    override fun containsKey(key: String): Boolean = true

    override fun get(key: String): @Composable () -> Unit = {
        AndroidView(factory = { context -> factory.create(key, context) }, modifier = Modifier.fillMaxSize())
    }
}
