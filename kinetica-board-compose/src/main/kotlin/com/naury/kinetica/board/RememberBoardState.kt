package com.naury.kinetica.board

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/**
 * 보드 배치를 저장하고 읽는 저장소. 호스트가 쓰는 SharedPreferences, DataStore, 서버 등에 맞춰 구현한다. 메인 스레드에서 호출된다.
 */
public interface BoardStorage {
    /** 저장된 배치 JSON. 없으면 `null`. */
    public fun load(): String?

    /** 배치 JSON을 저장한다. */
    public fun save(json: String)

    public companion object {
        /** SharedPreferences [name] 파일의 [key]에 저장한다. */
        @JvmStatic
        public fun sharedPreferences(context: Context, name: String, key: String): BoardStorage {
            val preferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
            return object : BoardStorage {
                override fun load(): String? = preferences.getString(key, null)

                override fun save(json: String) {
                    preferences.edit().putString(key, json).apply()
                }
            }
        }
    }
}

/**
 * 화면 회전 같은 구성 변경에도 배치와 실행 취소 기록을 유지하고, 화면이 가려질 때(ON_STOP)마다 [storage]에 저장하는 보드 상태.
 * 앱이 백그라운드에서 종료되어도 마지막으로 저장한 배치로 다시 연다.
 *
 * 상태는 이 화면의 ViewModel에 둔다. 한 화면에 보드가 여러 개면 [key]를 서로 다르게 준다. [key]는 ViewModel 키로 쓰인다.
 * 화면이 완전히 끝나면 저장하고 닫는다. XML View 화면에서는 [BoardStates.obtain]을 쓴다.
 * 저장본을 읽지 못했으면([KineticaBoardState.restoreFailed]) 사용자가 배치를 바꾸기 전까지 저장본을 덮어쓰지 않는다.
 *
 * @param catalog 추가할 수 있는 위젯 목록. 처음 만들 때 받은 목록만 쓴다.
 */
@Composable
public fun rememberKineticaBoardState(
    catalog: List<BoardWidget>,
    storage: BoardStorage,
    arrangement: BoardArrangement = BoardArrangement.Free(),
    key: String = "kinetica-board",
): KineticaBoardState {
    val holder = viewModel(key = key) { BoardStateHolder(KineticaBoardState.create(catalog, arrangement, storage.load()), storage) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(holder, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) holder.save() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return holder.state
}

internal class BoardStateHolder(val state: KineticaBoardState, private val storage: BoardStorage) : ViewModel() {
    private val opened = state.document.value

    fun save() {
        // 읽지 못한 저장본도 덮어쓰면 복구할 수 없으므로 사용자가 배치를 바꾸기 전까지 남겨 둔다.
        if (state.restoreFailed && state.document.value == opened) return
        storage.save(state.toJson())
    }

    override fun onCleared() {
        save()
        state.close()
    }
}

/** Compose를 쓰지 않는 Activity나 Fragment에서 [rememberKineticaBoardState]와 같은 보드 상태를 얻는다. */
public object BoardStates {
    /**
     * [owner]의 ViewModel에 보드 상태를 두므로 화면을 회전해도 배치와 실행 취소 기록이 유지된다. [owner]가 가려질 때(ON_STOP)마다
     * [storage]에 저장하고, 읽지 못한 저장본은 사용자가 배치를 바꾸기 전까지 덮어쓰지 않는다. 화면이 완전히 끝나면 저장하고 닫으므로
     * [KineticaBoardState.close]를 직접 호출하지 않는다. `onCreate`에서 호출하며, 같은 화면에서 다시 호출하면 같은 상태를 돌려준다.
     *
     * ```kotlin
     * val state = BoardStates.obtain(this, catalog, BoardStorage.sharedPreferences(this, "home", "board"))
     * findViewById<KineticaBoardView>(R.id.board).setBoard(state) { id, context -> makeWidget(state.widgetKeyOf(id), context) }
     * ```
     */
    @JvmStatic
    @JvmOverloads
    public fun <T> obtain(
        owner: T,
        catalog: List<BoardWidget>,
        storage: BoardStorage,
        arrangement: BoardArrangement = BoardArrangement.Free(),
        key: String = "kinetica-board",
    ): KineticaBoardState where T : ViewModelStoreOwner, T : LifecycleOwner {
        val holder = ViewModelProvider(
            owner,
            viewModelFactory { initializer { BoardStateHolder(KineticaBoardState.create(catalog, arrangement, storage.load()), storage) } },
        )[key, BoardStateHolder::class.java]
        owner.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) = holder.save()

                override fun onDestroy(owner: LifecycleOwner) = owner.lifecycle.removeObserver(this)
            },
        )
        return holder.state
    }
}
