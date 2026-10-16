package com.example.consumer.m3

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.BeforeAfterSlider
import com.naury.kinetica.ui.KImageViewer
import com.naury.kinetica.ui.imageViewerSource
import com.naury.kinetica.ui.rememberKImageViewerState
import kotlin.math.roundToInt

/** 사진 앱 화면. 썸네일 격자에서 누른 사진이 KImageViewer로 커지며 열린다. 뷰어는 KineticaScaffold 밖이라 별도 창에 뜬다. */
@Composable
fun PhotosScreen() {
    val photos = rememberSamplePhotos(18)
    val viewer = rememberKImageViewerState()
    Column(Modifier.fillMaxSize()) {
        Text(
            if (viewer.isOpen) "뷰어 열림 ${viewer.page}" else "뷰어 닫힘",
            Modifier.padding(8.dp).testTag("viewerState"),
        )
        LazyVerticalGrid(
            GridCells.Fixed(3),
            Modifier.fillMaxSize().testTag("photoGrid"),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(photos.size) { i ->
                Image(
                    photos[i],
                    "사진 ${i + 1}",
                    Modifier.aspectRatio(1f).imageViewerSource(viewer, i).clickable { viewer.open(i) }.testTag("thumb$i"),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
    KImageViewer(viewer, photos, contentDescriptions = List(photos.size) { "사진 ${it + 1}" })
}

/** 세로 스크롤 글 사이에 놓은 전후 비교 슬라이더. 세로로 끌면 글이 스크롤되고 가로로 끌면 손잡이가 움직여야 한다. */
@Composable
fun CompareScreen() {
    val photos = rememberSamplePhotos(2)
    val scroll = rememberScrollState()
    var split by rememberSaveable { mutableFloatStateOf(0.5f) }
    Column(Modifier.fillMaxSize()) {
        Text("스크롤 ${scroll.value} 위치 ${(split * 100).roundToInt()}", Modifier.padding(8.dp).testTag("compareScroll"))
        TextButton({ split = 0.2f }, Modifier.testTag("compareReset")) { Text("20%로") }
        Column(Modifier.fillMaxSize().verticalScroll(scroll).testTag("compareColumn").padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(4) { Text("보정 전후를 비교하는 글 $it. ".repeat(6), style = MaterialTheme.typography.bodyLarge) }
            BeforeAfterSlider(
                split,
                { split = it },
                before = { Image(photos[0], null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) },
                after = { Image(photos[1], null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) },
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).testTag("compareSlider"),
            )
            repeat(12) { Text("아래 글 $it. ".repeat(10), style = MaterialTheme.typography.bodyLarge) }
            Box(Modifier.fillMaxWidth().height(40.dp).background(Color.Gray).testTag("compareEnd"))
        }
    }
}

/** 실제 사진 대신 쓰는 그라디언트 그림. 가로 사진과 세로 사진을 섞어 비율이 다른 전환도 본다. */
@Composable
fun rememberSamplePhotos(count: Int): List<Painter> = remember(count) {
    List(count) { i ->
        val landscape = i % 3 != 1
        val w = if (landscape) 480 else 360
        val h = if (landscape) 360 else 480
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val hue = (i * 47f) % 360f
        val a = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.7f, 0.9f))
        val b = android.graphics.Color.HSVToColor(floatArrayOf((hue + 80f) % 360f, 0.8f, 0.5f))
        canvas.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), a, b, Shader.TileMode.CLAMP) })
        canvas.drawText("${i + 1}", w / 2f, h / 2f, Paint().apply { color = android.graphics.Color.WHITE; textSize = 96f; textAlign = Paint.Align.CENTER })
        BitmapPainter(bitmap.asImageBitmap())
    }
}
