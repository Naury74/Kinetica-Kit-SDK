package com.naury.kinetica.serialization

import com.naury.kinetica.core.scene.SceneDocument

/**
 * 문서 저장소. 어떤 구현을 쓸지는 호스트가 정하며, SDK 화면은 직접 저장하지 않는다.
 * 기본 구현은 앱 전용 폴더에 저장하는 [FileSceneRepository]다.
 *
 * id는 영문자, 숫자, `.` `_` `:` `-`로 이루어진 1..128자이며 경로 구분자는 쓸 수 없다.
 */
public interface SceneRepository {
    public suspend fun save(id: String, document: SceneDocument): SaveResult

    public suspend fun load(id: String): LoadResult

    /** 저장된 문서 id 목록. 순서는 보장하지 않는다. */
    public suspend fun list(): List<String>

    /** 지웠거나 원래 없었으면 `true`를 돌려준다. */
    public suspend fun delete(id: String): Boolean
}

public sealed interface SaveResult {
    public data object Saved : SaveResult

    /** 저장하지 못했다. 이전에 저장한 문서는 그대로 남는다. */
    public data class Failed(val reason: SaveFailure) : SaveResult
}

public enum class SaveFailure {
    InvalidId,

    /** 디스크 공간 부족이나 권한 문제 같은 입출력 오류. */
    IoError,
}

public sealed interface LoadResult {
    public data class Loaded(val document: SceneDocument) : LoadResult

    public data object NotFound : LoadResult

    /** 파일은 있지만 손상되었거나, 스키마 버전이 다르거나, 너무 커서 읽을 수 없다. 파일은 지우지 않는다. */
    public data class Failed(val error: SceneLoadError) : LoadResult

    public data class IoFailed(val reason: SaveFailure) : LoadResult
}
