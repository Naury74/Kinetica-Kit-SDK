package com.naury.kinetica.serialization

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.testing.TestScenes
import com.naury.kinetica.core.testing.TestScenes.id
import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileSceneRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `저장한 문서를 똑같이 다시 연다`() = runTest {
        val repository = FileSceneRepository(folder.root, ioDispatcher = StandardTestDispatcher(testScheduler))
        val document = TestScenes.sample()

        assertThat(repository.save("studio", document)).isEqualTo(SaveResult.Saved)

        assertThat(repository.load("studio")).isEqualTo(LoadResult.Loaded(document))
        assertThat(repository.list()).containsExactly("studio")
        assertThat(folder.root.list()!!.toList()).containsExactly("studio.kinetica.json")
    }

    @Test
    fun `다시 저장하면 교체하고 없는 문서는 NotFound다`() = runTest {
        val repository = FileSceneRepository(folder.root, ioDispatcher = StandardTestDispatcher(testScheduler))
        val first = TestScenes.sample()
        val second = first.copy(
            nodes =
                first.nodes + (id("a") to first.nodes.getValue(id("a")).copy(transform = Transform2D(translationX = 9.0))),
        )

        repository.save("s", first)
        repository.save("s", second)

        assertThat(repository.load("s")).isEqualTo(LoadResult.Loaded(second))
        assertThat(repository.load("missing")).isEqualTo(LoadResult.NotFound)
    }

    @Test
    fun `손상된 파일은 오류로 알리고 지우지 않는다`() = runTest {
        val repository = FileSceneRepository(folder.root, ioDispatcher = StandardTestDispatcher(testScheduler))
        val file = File(folder.root, "broken.kinetica.json").apply { writeText("{\"schemaVersion\":1,") }

        val result = repository.load("broken")

        assertThat(result).isInstanceOf(LoadResult.Failed::class.java)
        assertThat(file.exists()).isTrue()
    }

    @Test
    fun `크기 상한을 넘는 파일은 읽지 않는다`() = runTest {
        val repository = FileSceneRepository(folder.root, SceneCodec(maxJsonBytes = 10), StandardTestDispatcher(testScheduler))
        File(folder.root, "big.kinetica.json").writeText("x".repeat(11))

        assertThat(repository.load("big")).isEqualTo(LoadResult.Failed(SceneLoadError.TooLarge(11, 10)))
    }

    @Test
    fun `경로 문자가 든 id는 거부한다`() = runTest {
        val repository = FileSceneRepository(folder.root, ioDispatcher = StandardTestDispatcher(testScheduler))

        assertThat(repository.save("../escape", TestScenes.sample())).isEqualTo(SaveResult.Failed(SaveFailure.InvalidId))
        assertThat(repository.save("a/b", TestScenes.sample())).isEqualTo(SaveResult.Failed(SaveFailure.InvalidId))
        assertThat(repository.load("../escape")).isEqualTo(LoadResult.IoFailed(SaveFailure.InvalidId))
        assertThat(folder.root.parentFile.list()!!.none { it.contains("escape") }).isTrue()
    }

    @Test
    fun `저장에 실패해도 이전 파일은 그대로다`() = runTest {
        val repository = FileSceneRepository(folder.root, ioDispatcher = StandardTestDispatcher(testScheduler))
        repository.save("keep", TestScenes.sample())
        // 임시 파일 자리에 폴더를 만들어 쓰기를 실패시킨다.
        File(folder.root, "keep.kinetica.json.partial").mkdirs()

        val result = repository.save("keep", SceneDocumentFixtures.changed())

        assertThat(result).isEqualTo(SaveResult.Failed(SaveFailure.IoError))
        assertThat(repository.load("keep")).isEqualTo(LoadResult.Loaded(TestScenes.sample()))
    }

    @Test
    fun `저장소 codec 한도를 넘는 문서는 예외 없이 저장 실패로 알린다`() = runTest {
        val strict = SceneCodec(limits = com.naury.kinetica.core.scene.SceneLimits(maxExtensionBytes = 16))
        val repository = FileSceneRepository(folder.root, strict, ioDispatcher = StandardTestDispatcher(testScheduler))
        val large = TestScenes.sample().copy(extensions = mapOf("host.note" to "x".repeat(64)))

        assertThat(repository.save("big", large)).isEqualTo(SaveResult.Failed(SaveFailure.InvalidDocument))
        assertThat(repository.load("big")).isEqualTo(LoadResult.NotFound)
    }

    @Test
    fun `지우기`() = runTest {
        val repository = FileSceneRepository(folder.root, ioDispatcher = StandardTestDispatcher(testScheduler))
        repository.save("gone", TestScenes.sample())

        assertThat(repository.delete("gone")).isTrue()
        assertThat(repository.load("gone")).isEqualTo(LoadResult.NotFound)
        assertThat(repository.delete("gone")).isTrue()
    }

    private object SceneDocumentFixtures {
        fun changed() = TestScenes.sample().copy(documentId = "changed")
    }
}
