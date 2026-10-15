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
 * XML View 화면에서 슬롯마다 호스트 [View]를 만든다. [id]는 슬롯 id이고, 한 번만 놓는 위젯은 위젯 키와 같다.
 * 위젯 키는 [KineticaBoardState.widgetKeyOf]로 얻는다. 보드가 살아 있는 동안 슬롯 하나에 한 번만 호출된다.
 */
public fun interface BoardViewFactory {
    public fun create(id: String, context: Context): View
}

/** 사용 모드에서 위젯을 길게 눌렀을 때 호출된다. 보통 여기서 편집 모드로 바꾼다. */
public fun interface OnBoardLongPressListener {
    public fun onLongPress()
}

/**
 * [KineticaBoard]를 XML 레이아웃에 넣을 수 있게 감싼 [FrameLayout]. Compose를 쓰지 않는 앱도 기존 View를 그대로 위젯으로 쓸 수 있다.
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
 * View에 id가 있으면 화면을 회전해도 편집 모드가 유지된다. 배치는 화면이 가려질 때(`onStop`) [KineticaBoardState.toJson]으로 저장하고,
 * 화면이 끝나면 [KineticaBoardState.close]를 호출한다. Compose를 쓰므로 Activity나 Fragment처럼 lifecycle이 있는 창에 붙어 있어야 그려진다.
 */
public class KineticaBoardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    FrameLayout(context, attrs, defStyleAttr) {
    private var board by mutableStateOf<Pair<KineticaBoardState, BoardViewFactory>?>(null)
    private var editingState by mutableStateOf(false)
    private var permissionsState by mutableStateOf(BoardPermissions())
    private var stackAdvanceState by mutableLongStateOf(0L)
    private var colorsState by mutableStateOf<BoardColors?>(null)
    private var longPressState by mutableStateOf<OnBoardLongPressListener?>(null)

    /** 편집 모드. 켜면 위젯을 옮기고, 크기를 바꾸고, 삭제하거나 추가할 수 있다. */
    public var isEditing: Boolean
        get() = editingState
        set(value) {
            editingState = value
        }

    /** 0보다 크면 사용 모드에서 위젯 스택이 이 간격(ms)마다 다음 위젯으로 넘어간다. */
    public var stackAutoAdvanceMs: Long
        get() = stackAdvanceState
        set(value) {
            stackAdvanceState = value
        }

    /** 편집 모드에서 허용할 작업. */
    public var permissions: BoardPermissions
        get() = permissionsState
        set(value) {
            permissionsState = value
        }

    /** 편집 화면의 색. `null`이면 MaterialTheme 기본 색을 쓴다. */
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

    /** 보여 줄 보드와 위젯 View를 만드는 방법을 정한다. 다시 호출하면 새 값으로 바꾼다. */
    public fun setBoard(state: KineticaBoardState, factory: BoardViewFactory) {
        board = state to factory
    }

    /** 사용 모드에서 위젯을 길게 누르면 호출된다. `null`이면 길게 눌러도 아무 일도 일어나지 않는다. */
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
