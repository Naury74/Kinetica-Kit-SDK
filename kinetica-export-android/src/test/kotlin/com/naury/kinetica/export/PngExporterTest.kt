package com.naury.kinetica.export

import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.KeyValue
import com.naury.kinetica.core.animation.Keyframe
import com.naury.kinetica.core.animation.PropertyTrack
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PngExporterTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val exporter =
        PngExporter(
            ApplicationProvider.getApplicationContext(),
            renderDispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
        )

    // 200x400 artboard(초록 배경), 왼쪽 위 100x100 빨간 사각형. 1초 동안 불투명도 1 → 0.
    private val box =
        SceneNode(NodeId("box"), NodeContent.Rectangle(Size(100.0, 100.0)), style = NodeStyle(fill = ArgbColor(0xFFFF0000.toInt())))
    private val document = SceneDocument(
        documentId = "export",
        artboard = Artboard(200.0, 400.0, ArgbColor(0xFF00FF00.toInt())),
        nodes = mapOf(box.id to box),
        rootOrder = listOf(box.id),
        timelines = listOf(
            Timeline(
                "main",
                1_000,
                listOf(
                    PropertyTrack(
                        box.id,
                        AnimatableProperty.Opacity,
                        listOf(Keyframe(0, KeyValue.Scalar(1.0)), Keyframe(1_000, KeyValue.Scalar(0.0))),
                    ),
                ),
            ),
        ),
    )

    private fun exportBytes(request: ExportRequest, scene: SceneDocument = document): Pair<ExportResult, ByteArray> = runBlocking {
        val stream = ByteArrayOutputStream()
        exporter.export(scene, request, stream) to stream.toByteArray()
    }

    @Test
    fun `artboard 비율대로 크기를 정하고 장면을 그린다`() {
        val (result, bytes) = exportBytes(ExportRequest(widthPx = 100))
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        assertThat(result).isEqualTo(ExportResult.Success(100, 200))
        assertThat(bitmap.width to bitmap.height).isEqualTo(100 to 200)
        assertThat(bitmap.getPixel(10, 10)).isEqualTo(0xFFFF0000.toInt())
        assertThat(bitmap.getPixel(80, 150)).isEqualTo(0xFF00FF00.toInt())
    }

    @Test
    fun `요청한 시각의 장면을 내보낸다`() {
        val (_, bytes) = exportBytes(ExportRequest(widthPx = 100, timeMs = 1_000.0))
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        assertThat(bitmap.getPixel(10, 10)).isEqualTo(0xFF00FF00.toInt())
    }

    @Test
    fun `투명 배경을 고르면 artboard 배경을 그리지 않는다`() {
        val (_, bytes) = exportBytes(ExportRequest(widthPx = 100, transparentBackground = true))
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        assertThat(bitmap.getPixel(80, 150) ushr 24).isEqualTo(0)
        assertThat(bitmap.getPixel(10, 10)).isEqualTo(0xFFFF0000.toInt())
    }

    @Test
    fun `크기 상한을 넘거나 잘못된 크기는 그리지 않는다`() {
        assertThat(exportBytes(ExportRequest(widthPx = 4_000, maxPixels = 1_000_000)).first)
            .isEqualTo(ExportResult.Failure(ExportError.DimensionsExceeded))
        assertThat(exportBytes(ExportRequest(widthPx = 0)).first).isEqualTo(ExportResult.Failure(ExportError.InvalidSize))
        assertThat(exportBytes(ExportRequest(widthPx = 100_000, maxPixels = Long.MAX_VALUE)).first)
            .isEqualTo(ExportResult.Failure(ExportError.OutOfMemoryPrevented))
    }

    @Test
    fun `파일로 내보내면 임시 파일 없이 결과만 남고 실패하면 이전 파일이 그대로다`() = runBlocking<Unit> {
        val target = File(folder.root, "scene.png")

        assertThat(exporter.exportToFile(document, ExportRequest(widthPx = 50), target)).isEqualTo(ExportResult.Success(50, 100))
        val first = target.readBytes()
        assertThat(folder.root.list()!!.toList()).containsExactly("scene.png")

        assertThat(
            exporter.exportToFile(document, ExportRequest(widthPx = 0), target),
        ).isEqualTo(ExportResult.Failure(ExportError.InvalidSize))
        assertThat(target.readBytes()).isEqualTo(first)
        assertThat(folder.root.list()!!.toList()).containsExactly("scene.png")
    }
}
