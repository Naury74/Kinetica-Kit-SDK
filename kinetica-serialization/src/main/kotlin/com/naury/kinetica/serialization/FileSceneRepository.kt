package com.naury.kinetica.serialization

import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 문서마다 [directory] 안에 `<id>.kinetica.json` 파일 하나로 저장한다. [directory]에는 `Context.filesDir` 아래 같은 앱 전용 폴더를 넘긴다.
 *
 * 저장할 때는 같은 폴더의 임시 파일에 쓰고 fsync로 디스크에 반영한 뒤 이름을 바꿔 교체한다. 그래서 저장 중에 앱이 죽거나
 * 실패해도 이전 파일은 깨지지 않는다. 읽을 때는 [codec]의 크기 제한과 검증을 그대로 거친다. 모든 파일 입출력은 [ioDispatcher]에서 한다.
 */
public class FileSceneRepository(
    private val directory: File,
    private val codec: SceneCodec = SceneCodec(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SceneRepository {
    override suspend fun save(id: String, document: SceneDocument): SaveResult = withContext(ioDispatcher) {
        val target = fileFor(id) ?: return@withContext SaveResult.Failed(SaveFailure.InvalidId)
        val temp = File(directory, "$id$TEMP_SUFFIX")
        try {
            directory.mkdirs()
            FileOutputStream(temp).use { stream ->
                stream.write(codec.encode(document).toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            replace(temp, target)
            SaveResult.Saved
        } catch (_: IOException) {
            temp.delete()
            SaveResult.Failed(SaveFailure.IoError)
        }
    }

    override suspend fun load(id: String): LoadResult = withContext(ioDispatcher) {
        val file = fileFor(id) ?: return@withContext LoadResult.IoFailed(SaveFailure.InvalidId)
        if (!file.isFile) return@withContext LoadResult.NotFound
        try {
            val length = file.length()
            if (length > codec.maxJsonBytes) return@withContext LoadResult.Failed(SceneLoadError.TooLarge(length, codec.maxJsonBytes))
            when (val result = codec.decode(file.readBytes())) {
                is DecodeResult.Success -> LoadResult.Loaded(result.document)
                is DecodeResult.Failure -> LoadResult.Failed(result.error)
            }
        } catch (_: IOException) {
            LoadResult.IoFailed(SaveFailure.IoError)
        }
    }

    override suspend fun list(): List<String> = withContext(ioDispatcher) {
        directory.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(SUFFIX) }
            .map { it.name.removeSuffix(SUFFIX) }
            .filter(NodeId::isValid)
    }

    override suspend fun delete(id: String): Boolean = withContext(ioDispatcher) {
        val file = fileFor(id) ?: return@withContext false
        !file.exists() || file.delete()
    }

    private fun fileFor(id: String): File? = if (NodeId.isValid(id)) File(directory, "$id$SUFFIX") else null

    private fun replace(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val SUFFIX = ".kinetica.json"
        const val TEMP_SUFFIX = ".kinetica.json.partial"
    }
}
