package com.naury.kinetica.render

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.SceneDocument
import java.io.IOException
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 이미지 노드의 URI를 읽는 기본 구현. `content://`·`android.resource://`만 열고 다른 scheme(웹·파일)은 열지 않는다(docs 14).
 *
 * 디코딩 전에 크기를 읽어 [maxPixels] 안으로 줄여 디코딩한다. API 28 이상은 EXIF 회전을 반영하고, 26·27에서는 반영하지 않는다.
 * 권한이 없거나 열 수 없거나 그림이 아니면 `null`이다. 입출력은 [ioDispatcher]에서 한다.
 *
 * @param maxPixels 디코딩한 그림의 최대 픽셀 수(기본 4 MP)
 */
public class ContentImageLoader(
    context: Context,
    private val maxPixels: Int = DEFAULT_MAX_PIXELS,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    init {
        require(maxPixels > 0) { "maxPixels는 0보다 커야 합니다." }
    }

    public suspend fun load(uri: String): ImageBitmap? = withContext(ioDispatcher) {
        val parsed = Uri.parse(uri)
        if (parsed.scheme !in ALLOWED_SCHEMES) return@withContext null
        try {
            decode(parsed)?.asImageBitmap()
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun decode(uri: Uri): Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            val scale = scaleFor(info.size.width, info.size.height)
            if (scale <
                1.0
            ) {
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            var sample = 1
            while (bounds.outWidth.toLong() * bounds.outHeight / (sample.toLong() * sample) > maxPixels) sample *= 2
            resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
    }

    private fun scaleFor(width: Int, height: Int): Double {
        val pixels = width.toLong() * height
        return if (pixels <= maxPixels) 1.0 else sqrt(maxPixels.toDouble() / pixels)
    }

    public companion object {
        public const val DEFAULT_MAX_PIXELS: Int = 4_000_000
        private val ALLOWED_SCHEMES = setOf(ContentResolver.SCHEME_CONTENT, ContentResolver.SCHEME_ANDROID_RESOURCE)
    }
}

/**
 * [document]의 이미지 노드 URI를 [loader]로 읽어 그리기에 쓸 [SceneImageProvider]를 돌려준다. 읽기는 뒤에서 하며,
 * 다 읽힌 그림부터 다시 그려진다. 읽지 못한 그림은 빈 틀로 남는다. 한 번 읽은 그림은 이 구성 동안 다시 읽지 않는다.
 */
@Composable
public fun rememberSceneImages(document: SceneDocument, loader: ContentImageLoader? = null): SceneImageProvider {
    val context = LocalContext.current
    val actualLoader = remember(loader, context) { loader ?: ContentImageLoader(context) }
    val images = remember(actualLoader) { mutableStateMapOf<String, ImageBitmap>() }
    val failed = remember(actualLoader) { HashSet<String>() }
    val uris = remember(document) { document.nodes.values.mapNotNull { (it.content as? NodeContent.Image)?.uri }.toSet() }
    LaunchedEffect(uris, actualLoader) {
        uris.filter { it !in images && it !in failed }.forEach { uri ->
            val image = actualLoader.load(uri)
            if (image != null) images[uri] = image else failed += uri
        }
    }
    return remember(images) { SceneImageProvider { uri -> images[uri] } }
}
