// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KImageViewer
import com.naury.kinetica.ui.KImageViewerCloseIcon
import com.naury.kinetica.ui.KImageViewerState
import com.naury.kinetica.ui.KSelectionBadge
import com.naury.kinetica.ui.KTopAppBar
import com.naury.kinetica.ui.KTopAppBarDropdownTitle
import com.naury.kinetica.ui.imageViewerSource
import com.naury.kinetica.ui.kClickable
import com.naury.kinetica.ui.rememberKImageViewerState

/**
 * 사진 고르기 데모. 제목을 눌러 앨범을 바꾸고, 썸네일을 누르면 고른 순서가 붙는다.
 * 아래 '크게 보기'로 뷰어를 열면 위 바의 표시로 고르고 아래 필름 줄로 옮겨 다닌다.
 */
@Composable
internal fun PhotoPickerCard() {
    val photos = remember { (PHOTO_COLORS + PHOTO_COLORS.reversed().take(1)).map { GradientPhoto(it) } }
    val selection = remember { mutableStateListOf<Int>() }
    var albumOpen by remember { mutableStateOf(false) }
    var album by remember { mutableStateOf(ALBUMS.first()) }
    val viewer = rememberKImageViewerState()
    fun toggle(index: Int) {
        if (!selection.remove(index)) selection += index
    }
    fun orderOf(index: Int): Int? = selection.indexOf(index).takeIf { it >= 0 }?.plus(1)
    DemoCard("사진 고르기", "제목을 눌러 앨범을 고릅니다. 사진을 누르면 고른 순서가 붙고, 크게 보기에서는 위 버튼으로 고르고 필름 줄로 옮겨 다닙니다.") {
        KTopAppBar(
            title = {
                KTopAppBarDropdownTitle(album, expanded = albumOpen, onClick = { albumOpen = !albumOpen }, onClickLabel = "앨범 고르기")
            },
            windowInsets = WindowInsets(0),
        )
        if (albumOpen) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ALBUMS.forEach { name ->
                    Pill(name, filled = name == album) {
                        album = name
                        albumOpen = false
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            photos.indices.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { index ->
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .imageViewerSource(viewer, index)
                                .clip(RoundedCornerShape(10.dp))
                                .kClickable(RoundedCornerShape(10.dp), role = Role.Checkbox) { toggle(index) },
                        ) {
                            Image(photos[index], "사진 ${index + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            KSelectionBadge(orderOf(index), Modifier.align(Alignment.TopEnd).padding(6.dp))
                        }
                    }
                }
            }
        }
        Pill("크게 보기") { viewer.open(selection.firstOrNull() ?: 0) }
    }
    PickerViewer(viewer, photos, selection, ::toggle, ::orderOf)
}

@Composable
private fun PickerViewer(
    viewer: KImageViewerState,
    photos: List<Painter>,
    selection: List<Int>,
    toggle: (Int) -> Unit,
    orderOf: (Int) -> Int?,
) {
    KImageViewer(
        viewer,
        photos,
        contentDescriptions = photos.indices.map { "사진 ${it + 1}" },
        closeIcon = KImageViewerCloseIcon.Back,
        topActions = { page ->
            Box(Modifier.size(44.dp).viewerChrome(CircleShape), contentAlignment = Alignment.Center) {
                KSelectionBadge(orderOf(page), onClick = { toggle(page) }, size = 26.dp)
            }
        },
        bottomBar = { page ->
            Column(Modifier.padding(bottom = 10.dp)) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(photos) { index, photo ->
                        val current = index == page
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .then(if (current) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
                                .kClickable(RoundedCornerShape(8.dp)) { viewer.open(index) },
                        ) {
                            Image(photo, "사진 ${index + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            orderOf(index)?.let { KSelectionBadge(it, Modifier.align(Alignment.TopEnd).padding(3.dp), size = 16.dp) }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.weight(1f))
                    val done = if (selection.isEmpty()) "완료" else "완료 ${selection.size}"
                    KButton(done, onClick = { viewer.close() }, enabled = selection.isNotEmpty())
                }
            }
        },
    )
}

private val ALBUMS = listOf("최근 항목", "즐겨찾기", "스크린샷")
