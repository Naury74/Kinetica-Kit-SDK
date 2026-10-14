package com.naury.kinetica.export

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.naury.kinetica.core.geometry.Mat3
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneGraph
import com.naury.kinetica.motion.TimelineEvaluator
import com.naury.kinetica.render.ContentImageLoader
import com.naury.kinetica.render.SceneFonts
import com.naury.kinetica.render.SceneImageProvider
import com.naury.kinetica.render.drawScene
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * 장면을 PNG로 내보낸다(docs 13). 화면과 같은 [drawScene]으로 그리므로 미리보기와 결과가 같다.
 *
 * 크기 상한과 남은 메모리를 먼저 확인하고, 그림은 [renderDispatcher]에서, 쓰기는 [ioDispatcher]에서 한다.
 * 색 공간은 sRGB, 알파는 premultiplied가 아닌 PNG 표준을 따른다. 출력 위치는 호스트가 정하며 공유는 하지 않는다.
 *
 * @param images 이미지 노드에 쓸 이미지. `null`이면 내보내기 전에 문서의 `content://` 이미지를 [ContentImageLoader]로 읽는다.
 * @param fonts 글자 노드의 글꼴 이름을 실제 글꼴로 바꾼다. 화면에 준 [com.naury.kinetica.render.LocalSceneFonts]와 같은 것을 준다.
 */
public class PngExporter(
    context: Context,
    private val images: SceneImageProvider? = null,
    private val fonts: SceneFonts = SceneFonts.Default,
    private val renderDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val fontResolver = createFontFamilyResolver(context.applicationContext)
    private val loader = ContentImageLoader(context, ioDispatcher = ioDispatcher)

    /** [output]에 PNG를 쓴다. [output]은 닫지 않는다. */
    public suspend fun export(document: SceneDocument, request: ExportRequest, output: OutputStream): ExportResult {
        val bitmap = when (val rendered = render(document, request)) {
            is Rendered.Image -> rendered.bitmap
            is Rendered.Failed -> return ExportResult.Failure(rendered.error)
        }
        return try {
            withContext(ioDispatcher) {
                val written = bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output)
                if (written) ExportResult.Success(bitmap.width, bitmap.height) else ExportResult.Failure(ExportError.EncoderFailure)
            }
        } catch (_: IOException) {
            ExportResult.Failure(ExportError.WriteFailed)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * [target] 파일로 내보낸다. 같은 폴더의 숨김 임시 파일에 쓴 뒤 이름을 바꾸므로, 실패하거나 취소되면 임시 파일만 지워지고
     * 이전 [target]은 그대로다.
     */
    public suspend fun exportToFile(document: SceneDocument, request: ExportRequest, target: File): ExportResult {
        val temp = File(target.parentFile, ".${target.name}.partial")
        var published = false
        return try {
            val result = withContext(ioDispatcher) { FileOutputStream(temp).use { stream -> export(document, request, stream) } }
            if (result is ExportResult.Success) {
                withContext(ioDispatcher) { if (!temp.renameTo(target)) throw IOException("rename") }
                published = true
            }
            result
        } catch (_: IOException) {
            ExportResult.Failure(ExportError.WriteFailed)
        } finally {
            if (!published) temp.delete()
        }
    }

    /** 호스트가 받은 [uri](예: SAF 문서)에 쓴다. 실패하면 일부만 쓰였을 수 있으므로 호스트가 정리한다. */
    public suspend fun exportToUri(document: SceneDocument, request: ExportRequest, resolver: ContentResolver, uri: Uri): ExportResult =
        try {
            withContext(ioDispatcher) {
                val stream = resolver.openOutputStream(uri, "wt") ?: throw IOException("openOutputStream")
                stream.use { export(document, request, it) }
            }
        } catch (_: IOException) {
            ExportResult.Failure(ExportError.WriteFailed)
        } catch (_: SecurityException) {
            ExportResult.Failure(ExportError.WriteFailed)
        }

    /** 호스트가 이미지를 주지 않았으면 문서의 이미지를 미리 읽어 둔다. 그리기 중에는 입출력을 하지 않는다. */
    private suspend fun imagesFor(document: SceneDocument): SceneImageProvider {
        images?.let { return it }
        val uris = document.nodes.values.mapNotNull { (it.content as? NodeContent.Image)?.uri }.toSet()
        val loaded = uris.mapNotNull { uri -> loader.load(uri)?.let { uri to it } }.toMap()
        return SceneImageProvider { loaded[it] }
    }

    private suspend fun render(document: SceneDocument, request: ExportRequest): Rendered {
        val provider = imagesFor(document)
        return draw(document, request, provider)
    }

    private suspend fun draw(document: SceneDocument, request: ExportRequest, images: SceneImageProvider): Rendered =
        withContext(renderDispatcher) {
            val artboard = document.artboard
            val width = request.widthPx
            val mockup = request.mockup?.let { MockupLayout(it, width, artboard.height / artboard.width) }
            val height = mockup?.height ?: (width * artboard.height / artboard.width).roundToInt()
            val pixels = width.toLong() * height
            when {
                width <= 0 || height <= 0 -> return@withContext Rendered.Failed(ExportError.InvalidSize)
                pixels > request.maxPixels -> return@withContext Rendered.Failed(ExportError.DimensionsExceeded)
                pixels * BYTES_PER_PIXEL > availableMemory() -> return@withContext Rendered.Failed(ExportError.OutOfMemoryPrevented)
            }
            val timeline = document.timelines.firstOrNull()
            val evaluated = if (timeline == null) document else TimelineEvaluator.evaluate(document, timeline, request.timeMs)
            val scene = if (request.transparentBackground) evaluated.copy(artboard = artboard.copy(background = null)) else evaluated
            val image = ImageBitmap(width, height)
            ensureActive()
            val density = Density(1f)
            val measurer = TextMeasurer(fontResolver, density, LayoutDirection.Ltr)
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(width.toFloat(), height.toFloat())) {
                if (mockup == null) {
                    if (!request.transparentBackground && artboard.background == null) drawRect(Color.White)
                    drawScene(SceneGraph(scene), Mat3.scale(width / artboard.width, height / artboard.height), measurer, images, fonts)
                } else {
                    drawMockup(mockup, scene, measurer, images)
                }
            }
            ensureActive()
            Rendered.Image(image.asAndroidBitmap())
        }

    /** 기기 틀을 그리고 화면 자리 안으로 잘라 장면을 그린다. */
    private fun DrawScope.drawMockup(mockup: MockupLayout, scene: SceneDocument, measurer: TextMeasurer, images: SceneImageProvider) {
        with(mockup) { drawBehindScreen() }
        val screen = mockup.screen
        clipPath(Path().apply { addRoundRect(screen) }) {
            if (scene.artboard.background ==
                null
            ) {
                drawRect(Color.White, topLeft = Offset(screen.left, screen.top), size = mockup.screenSize)
            }
            val toScreen = Mat3.translation(screen.left.toDouble(), screen.top.toDouble()) *
                Mat3.scale(screen.width / scene.artboard.width, screen.height / scene.artboard.height)
            drawScene(SceneGraph(scene), toScreen, measurer, images, fonts)
        }
        with(mockup) { drawOverScreen() }
    }

    private fun availableMemory(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
    }

    private sealed interface Rendered {
        class Image(val bitmap: Bitmap) : Rendered

        class Failed(val error: ExportError) : Rendered
    }

    private companion object {
        const val PNG_QUALITY = 100
        const val BYTES_PER_PIXEL = 4L
    }
}
