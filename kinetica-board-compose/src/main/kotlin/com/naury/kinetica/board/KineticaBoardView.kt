package com.naury.kinetica.board

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView

/** XML View 화면에서 위젯 키마다 호스트 [View]를 만든다. 같은 키로는 보드가 살아 있는 동안 한 번만 부른다. */
public fun interface BoardViewFactory {
    public fun create(key: String, context: Context): View
}

/**
 * [KineticaBoard]를 XML·View 화면에 넣는 [FrameLayout]. Compose를 쓰지 않는 앱도 기존 View를 그대로 위젯으로 쓴다.
 *
 * ```kotlin
 * val board = findViewById<KineticaBoardView>(R.id.board)
 * board.setBoard(KineticaBoardState.create(catalog, saved = prefs.getString("board", null))) { key, context ->
 *     layoutInflater.inflate(layoutFor(key), null)
 * }
 * editButton.setOnClickListener { board.isEditing = !board.isEditing }
 * ```
 *
 * 화면이 끝나면 저장([KineticaBoardState.toJson])하고 [KineticaBoardState.close]한다. 보드를 그리려면 창에 붙어 있어야 하며
 * Activity·Fragment처럼 lifecycle이 있는 창이어야 한다(Compose 요구 사항).
 */
public class KineticaBoardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    FrameLayout(context, attrs, defStyleAttr) {
    private var board by mutableStateOf<Pair<KineticaBoardState, BoardViewFactory>?>(null)
    private var editingState by mutableStateOf(false)
    private var permissionsState by mutableStateOf(BoardPermissions())

    /** 편집 모드. 켜면 위젯을 옮기고 크기를 바꾸고 빼고 추가할 수 있다. */
    public var isEditing: Boolean
        get() = editingState
        set(value) {
            editingState = value
        }

    /** 편집 모드에서 허용할 일. */
    public var permissions: BoardPermissions
        get() = permissionsState
        set(value) {
            permissionsState = value
        }

    init {
        addView(
            ComposeView(context).apply {
                setContent {
                    board?.let { (state, factory) ->
                        KineticaBoard(state, Modifier.fillMaxSize(), editing = editingState, permissions = permissionsState) { key ->
                            AndroidView(factory = { viewContext -> factory.create(key, viewContext) }, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    /** 보여 줄 보드와 위젯 View를 만드는 방법을 정한다. 다시 부르면 바꾼다. */
    public fun setBoard(state: KineticaBoardState, factory: BoardViewFactory) {
        board = state to factory
    }
}
