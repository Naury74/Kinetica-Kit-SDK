// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.Detent
import com.naury.kinetica.ui.GlassDetentSheet
import com.naury.kinetica.ui.GlassIconButton
import com.naury.kinetica.ui.GlassScaffold
import com.naury.kinetica.ui.GlassSearchBar
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.GlassSurface
import com.naury.kinetica.ui.GlassTab
import com.naury.kinetica.ui.GlassTopTabs
import com.naury.kinetica.ui.Sparkline
import com.naury.kinetica.ui.rememberGlassDetentSheetState
import com.naury.kinetica.ui.tooltipOnLongPress

/** 지도 앱: 그린 지도 위에 여러 높이에 멈추는 시트. 시트 안 탭을 넘기며 장소 목록을 본다. */
@Composable
internal fun MapApp(onBack: () -> Unit) {
    val sheet = rememberGlassDetentSheetState(detents = listOf(Detent.Peek(150.dp), Detent.Fraction(0.5f), Detent.Full))
    var query by remember { mutableStateOf("") }
    GlassScaffold(Modifier.fillMaxSize(), style = GlassStyles.OneUi, background = { FakeMap(Modifier.fillMaxSize()) }) { _ ->
        Box(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(ShowcaseIcons.Back, "뒤로", onBack)
                GlassSurface(Modifier.height(44.dp).tooltipOnLongPress("길게 눌러 도움말을 보여 줍니다"), shape = CircleShape) {
                    Row(
                        Modifier.align(Alignment.Center).padding(start = 12.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShowcaseIcon(ShowcaseIcons.Location, 18.dp)
                        Label("내 위치", 14, FontWeight.SemiBold, Modifier.padding(start = 6.dp))
                    }
                }
            }
            GlassDetentSheet(sheet, title = "장소", header = {
                GlassSearchBar(query, { query = it }, {}, Modifier.padding(horizontal = 16.dp), placeholder = "장소 검색")
            }) {
                PlaceTabs()
            }
        }
    }
}

@Composable
private fun PlaceTabs() {
    val pager = rememberPagerState { PLACE_TABS.size }
    Column(Modifier.fillMaxSize()) {
        GlassTopTabs(PLACE_TABS.map { GlassTab(it) }, pager, Modifier.fillMaxWidth().padding(horizontal = 12.dp))
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = navigationBarBottom() + 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(12) { index -> PlaceRow(PLACE_TABS[page], index) }
            }
        }
    }
}

@Composable
private fun PlaceRow(kind: String, index: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(PIN_COLORS[index % PIN_COLORS.size], CircleShape))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Label("$kind ${index + 1}", 16, FontWeight.SemiBold)
            Label("${(index + 1) * 120}m · 영업 중", 13)
        }
        Sparkline(BUSY.map { it * (1 + index % 3) }, Modifier.size(64.dp, 24.dp), color = PIN_COLORS[index % PIN_COLORS.size])
    }
}

/** 지도 대신 그린 길과 공원. 시트가 이 위에서 블러로 비친다. */
@Composable
private fun FakeMap(modifier: Modifier) {
    Canvas(modifier.background(Color(0xFFEDEAE3))) {
        val park = Color(0xFFBFE3B4)
        drawCircle(park, size.minDimension * 0.25f, Offset(size.width * 0.25f, size.height * 0.3f))
        drawCircle(Color(0xFFA9D3F5), size.minDimension * 0.3f, Offset(size.width * 0.85f, size.height * 0.65f))
        val road = Stroke(width = 28f)
        for (i in 0..6) {
            drawLine(Color.White, Offset(0f, size.height * i / 6f), Offset(size.width, size.height * i / 6f + 180f), road.width)
            drawLine(Color.White, Offset(size.width * i / 5f, 0f), Offset(size.width * i / 5f - 120f, size.height), road.width)
        }
        drawCircle(Color(0xFF0A84FF), 22f, Offset(size.width * 0.55f, size.height * 0.4f))
        drawCircle(Color.White, 22f, Offset(size.width * 0.55f, size.height * 0.4f), style = Stroke(6f))
    }
}

private val PLACE_TABS = listOf("카페", "식당", "공원", "주차장", "약국", "서점")
private val PIN_COLORS = listOf(Color(0xFFFF9F0A), Color(0xFFFF453A), Color(0xFF34C759), Color(0xFF0A84FF), Color(0xFFBF5AF2))
private val BUSY = listOf(2f, 3f, 5f, 8f, 6f, 4f, 7f, 9f, 5f, 3f)
