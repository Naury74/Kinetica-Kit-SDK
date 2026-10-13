package com.naury.kinetica.render

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContentImageLoaderTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun register(uri: String, width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF4466FF.toInt()) }
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        shadowOf(context.contentResolver).registerInputStreamSupplier(Uri.parse(uri)) { bytes.inputStream() }
    }

    @Test
    fun `content URI를 읽고 픽셀 상한 안으로 줄인다`() = runBlocking<Unit> {
        register("content://test/big", 400, 300)
        val loader = ContentImageLoader(context, maxPixels = 30_000, ioDispatcher = Dispatchers.Unconfined)

        val image = checkNotNull(loader.load("content://test/big"))

        assertThat(image.width.toLong() * image.height).isAtMost(30_000L)
        assertThat(image.width).isGreaterThan(image.height)
    }

    @Test
    fun `웹·파일 URI와 열 수 없는 URI는 null이다`() = runBlocking<Unit> {
        val loader = ContentImageLoader(context, ioDispatcher = Dispatchers.Unconfined)

        assertThat(loader.load("https://example.com/a.png")).isNull()
        assertThat(loader.load("file:///sdcard/a.png")).isNull()
        assertThat(loader.load("content://test/missing")).isNull()
    }
}
