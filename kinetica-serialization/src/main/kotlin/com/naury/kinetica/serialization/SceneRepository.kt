package com.naury.kinetica.serialization

import com.naury.kinetica.core.scene.SceneDocument

/**
 * 문서를 저장하는 곳. 호스트가 구현을 고르며 SDK 화면은 직접 저장하지 않는다.
 * [FileSceneRepository]가 앱 전용 폴더에 저장하는 기본 구현이다.
 *
 * id는 영문자·숫자와 `.` `_` `:` `-`로 된 1..128자다(경로 문자를 쓸 수 없다).
 */
public interface SceneRepository {
    public suspend fun save(id: String, document: SceneDocument): SaveResult

    public suspend fun load(id: String): LoadResult

    /** 저장된 문서 id. 순서는 정하지 않는다. */
    public suspend fun list(): List<String>

    /** 지웠거나 원래 없었으면 `true`. */
    public suspend fun delete(id: String): Boolean
}

public sealed interface SaveResult {
    public data object Saved : SaveResult

    /** 저장하지 못했다. 이전에 저장한 문서는 그대로 남는다. */
    public data class Failed(val reason: SaveFailure) : SaveResult
}

public enum class SaveFailure {
    InvalidId,

    /** 디스크 공간 부족, 권한 등 입출력 오류. */
    IoError,
}

public sealed interface LoadResult {
    public data class Loaded(val document: SceneDocument) : LoadResult

    public data object NotFound : LoadResult

    /** 파일은 있지만 읽을 수 없다(손상, 다른 형식 버전, 너무 큼). 파일은 지우지 않는다. */
    public data class Failed(val error: SceneLoadError) : LoadResult

    public data class IoFailed(val reason: SaveFailure) : LoadResult
}
