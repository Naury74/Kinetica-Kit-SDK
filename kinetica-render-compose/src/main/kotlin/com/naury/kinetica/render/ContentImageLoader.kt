package com.naury.kinetica.render

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.exifinterface.media.ExifInterface
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.SceneDocument
import java.io.IOException
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 이미지 노드의 URI를 읽는 기본 구현. `content://`와 `android.resource://`만 열고, 웹이나 파일 같은 다른 scheme은 열지 않는다(docs 14).
 *
 * 디코딩 전에 크기를 먼저 읽고 [maxPixels] 안으로 줄여서 디코딩한다. EXIF 회전·뒤집기 정보를 반영한다.
 * 권한이 없거나, 열 수 없거나, 이미지가 아니면 `null`을 돌려준다. 입출력은 [ioDispatcher]에서 실행한다.
 *
 * @param maxPixels 디코딩한 이미지의 최대 픽셀 수(기본 4MP)
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
            val bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
            bitmap?.let { applyExif(uri, it) }
        }
    }

    // BitmapFactory는 EXIF 방향을 무시하므로 직접 돌린다. 방향을 읽지 못하면 그대로 둔다.
    private fun applyExif(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = exifMatrix(orientation) ?: return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { if (it !== bitmap) bitmap.recycle() }
    }

    private fun scaleFor(width: Int, height: Int): Double {
        val pixels = width.toLong() * height
        return if (pixels <= maxPixels) 1.0 else sqrt(maxPixels.toDouble() / pixels)
    }

    public companion object {
        /** EXIF 방향 값을 그 방향을 바로 세우는 행렬로 바꾼다. 바꿀 필요가 없으면 `null`. */
        internal fun exifMatrix(orientation: Int): Matrix? {
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)

                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(HALF_TURN)

                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)

                ExifInterface.ORIENTATION_TRANSPOSE -> matrix.apply {
                    setRotate(QUARTER_TURN)
                    postScale(-1f, 1f)
                }

                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(QUARTER_TURN)

                ExifInterface.ORIENTATION_TRANSVERSE -> matrix.apply {
                    setRotate(-QUARTER_TURN)
                    postScale(-1f, 1f)
                }

                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-QUARTER_TURN)

                else -> return null
            }
            return matrix
        }

        private const val QUARTER_TURN = 90f
        private const val HALF_TURN = 180f
        public const val DEFAULT_MAX_PIXELS: Int = 4_000_000
        private val ALLOWED_SCHEMES = setOf(ContentResolver.SCHEME_CONTENT, ContentResolver.SCHEME_ANDROID_RESOURCE)
    }
}

/**
 * [document]의 이미지 노드 URI를 [loader]로 읽어 그리기에 쓸 [SceneImageProvider]를 돌려준다. 백그라운드에서 읽고,
 * 읽기가 끝난 이미지부터 다시 그린다. 읽지 못한 이미지는 플레이스홀더로 남는다. 한 번 읽은 이미지는 이 컴포지션에 있는 동안 다시 읽지 않는다.
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
