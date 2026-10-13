package com.naury.kinetica.sample

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.history.KineticaSession
import com.naury.kinetica.history.SessionOpenResult
import com.naury.kinetica.serialization.FileSceneRepository
import com.naury.kinetica.serialization.LoadResult
import com.naury.kinetica.serialization.SaveResult
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 장면 하나를 앱 전용 폴더에서 열고 저장한다. 세션은 화면 회전에도 유지된다. */
class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FileSceneRepository(File(application.filesDir, "scenes"))
    private val mutableState = MutableStateFlow<StudioState>(StudioState.Loading)
    val state: StateFlow<StudioState> = mutableState.asStateFlow()

    init {
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

    fun messageShown() {
        (mutableState.value as? StudioState.Ready)?.let { mutableState.value = it.copy(message = null) }
    }

    override fun onCleared() {
        (mutableState.value as? StudioState.Ready)?.session?.close()
    }

    private fun emptyScene() = SceneDocument.empty(Artboard(ARTBOARD_WIDTH, ARTBOARD_HEIGHT, ArgbColor(ARTBOARD_COLOR.toInt())), SCENE_ID)

    private companion object {
        const val SCENE_ID = "studio"
        const val ARTBOARD_WIDTH = 1080.0
        const val ARTBOARD_HEIGHT = 1920.0
        const val ARTBOARD_COLOR = 0xFF22262F
    }
}

sealed interface StudioState {
    data object Loading : StudioState

    data class Ready(val session: KineticaSession, @StringRes val message: Int?) : StudioState
}
