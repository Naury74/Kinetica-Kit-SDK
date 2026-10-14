package com.naury.kinetica.board

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView

/**
 * XML View 화면에서 놓인 자리마다 호스트 [View]를 만든다. [id]는 자리 id이며(한 번만 놓는 위젯은 위젯 키와 같음)
 * 위젯 키는 [KineticaBoardState.widgetKeyOf]로 얻는다. 같은 자리로는 보드가 살아 있는 동안 한 번만 부른다.
 */
public fun interface BoardViewFactory {
    public fun create(id: String, context: Context): View
}

/** 사용 모드에서 위젯을 길게 눌렀을 때. 보통 편집 모드로 바꾼다. */
public fun interface OnBoardLongPressListener {
    public fun onLongPress()
}

/**
 * [KineticaBoard]를 XML·View 화면에 넣는 [FrameLayout]. Compose를 쓰지 않는 앱도 기존 View를 그대로 위젯으로 쓴다.
 *
 * ```kotlin
 * val board = findViewById<KineticaBoardView>(R.id.board)
 * board.setBoard(KineticaBoardState.create(catalog, saved = prefs.getString("board", null))) { id, context ->
 *     layoutInflater.inflate(layoutFor(state.widgetKeyOf(id)), null)
 * }
 * editButton.setOnClickListener { board.isEditing = !board.isEditing }
 * ```
 *
 * XML 속성: `app:kinetica_editing`, `app:kinetica_stackAutoAdvanceMs`, `app:kinetica_accentColor`.
 * View에 id가 있으면 화면을 돌려도 편집 모드가 유지된다. 배치는 화면이 가려질 때(`onStop`) [KineticaBoardState.toJson]으로 저장하고,
 * 화면이 끝나면 [KineticaBoardState.close]한다. 보드를 그리려면 Activity·Fragment처럼 lifecycle이 있는 창에 붙어 있어야 한다(Compose 요구 사항).
 */
public class KineticaBoardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    FrameLayout(context, attrs, defStyleAttr) {
    private var board by mutableStateOf<Pair<KineticaBoardState, BoardViewFactory>?>(null)
    private var editingState by mutableStateOf(false)
    private var permissionsState by mutableStateOf(BoardPermissions())
    private var stackAdvanceState by mutableLongStateOf(0L)
    private var colorsState by mutableStateOf<BoardColors?>(null)
    private var longPressState by mutableStateOf<OnBoardLongPressListener?>(null)

    /** 편집 모드. 켜면 위젯을 옮기고 크기를 바꾸고 빼고 추가할 수 있다. */
    public var isEditing: Boolean
        get() = editingState
        set(value) {
            editingState = value
        }

    /** 0보다 크면 사용 모드에서 위젯 묶음이 이 간격(ms)마다 다음 위젯으로 넘어간다. */
    public var stackAutoAdvanceMs: Long
        get() = stackAdvanceState
        set(value) {
            stackAdvanceState = value
        }

    /** 편집 모드에서 허용할 일. */
    public var permissions: BoardPermissions
        get() = permissionsState
        set(value) {
            permissionsState = value
        }

    /** 편집 표시 색. `null`이면 창의 MaterialTheme 기본 색을 쓴다. */
    public var colors: BoardColors?
        get() = colorsState
        set(value) {
            colorsState = value
        }

    init {
        context.obtainStyledAttributes(attrs, R.styleable.KineticaBoardView, defStyleAttr, 0).apply {
            try {
                editingState = getBoolean(R.styleable.KineticaBoardView_kinetica_editing, false)
                stackAdvanceState = getInt(R.styleable.KineticaBoardView_kinetica_stackAutoAdvanceMs, 0).toLong()
                if (hasValue(R.styleable.KineticaBoardView_kinetica_accentColor)) {
                    colorsState = BoardColors.fromAccent(getColor(R.styleable.KineticaBoardView_kinetica_accentColor, 0))
                }
            } finally {
                recycle()
            }
        }
        addView(
            ComposeView(context).apply {
                setContent {
                    board?.let { (state, factory) ->
                        val listener = longPressState
                        KineticaBoard(
                            state,
                            Modifier.fillMaxSize(),
                            editing = editingState,
                            permissions = permissionsState,
                            colors = colorsState ?: BoardColors.fromMaterialTheme(),
                            onLongPress = listener?.let { { it.onLongPress() } },
                            stackAutoAdvanceMs = stackAdvanceState,
                        ) { id ->
                            AndroidView(factory = { viewContext -> factory.create(id, viewContext) }, modifier = Modifier.fillMaxSize())
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

    /** 사용 모드에서 위젯을 길게 누르면 불린다. `null`이면 길게 눌러도 아무 일도 없다. */
    public fun setOnLongPressListener(listener: OnBoardLongPressListener?) {
        longPressState = listener
    }

    override fun onSaveInstanceState(): Parcelable = SavedState(super.onSaveInstanceState(), editingState)

    override fun onRestoreInstanceState(state: Parcelable?) {
        val saved = state as? SavedState
        super.onRestoreInstanceState(saved?.superState ?: state)
        saved?.let { editingState = it.editing }
    }

    private class SavedState : BaseSavedState {
        val editing: Boolean

        constructor(superState: Parcelable?, editing: Boolean) : super(superState) {
            this.editing = editing
        }

        constructor(parcel: Parcel) : super(parcel) {
            editing = parcel.readInt() != 0
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(if (editing) 1 else 0)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(source: Parcel): SavedState = SavedState(source)

                override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
            }
        }
    }
}
