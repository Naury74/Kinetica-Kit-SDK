// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.BeforeAfterSlider
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KChipGroup
import com.naury.kinetica.ui.KEmptyIcon
import com.naury.kinetica.ui.KEmptyIconKind
import com.naury.kinetica.ui.KEmptyState
import com.naury.kinetica.ui.KImageViewer
import com.naury.kinetica.ui.KImageViewerState
import com.naury.kinetica.ui.KOnboarding
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.OnboardingPage
import com.naury.kinetica.ui.imageViewerSource
import com.naury.kinetica.ui.kClickable
import com.naury.kinetica.ui.rememberKImageViewerState

/** 미디어 탭: 사진 뷰어, 전후 비교, 온보딩, 빈 화면. [onOnboarding]은 온보딩을 전체 화면으로 다시 띄운다. */
@Composable
internal fun MediaTab(padding: PaddingValues, connection: NestedScrollConnection, onOnboarding: () -> Unit) {
    val viewer = rememberKImageViewerState()
    val photos = remember { PHOTO_COLORS.map { GradientPhoto(it) } }
    ComponentTabList(padding, connection) {
        item { PhotoGridCard(viewer, photos) }
        item { CompareCard() }
        item {
            DemoCard("온보딩", "넘길 때마다 그림이 한 박자 늦게 따라오고 배경색이 섞이며 바뀝니다. 마지막 페이지에 가까워지면 버튼이 '시작하기'로 늘어납니다.") {
                Pill("온보딩 다시 보기", filled = true, onClick = onOnboarding)
            }
        }
        item { EmptyStateCard() }
    }
    KImageViewer(viewer, photos, contentDescriptions = PHOTO_COLORS.indices.map { "사진 ${it + 1}" })
}

@Composable
private fun PhotoGridCard(viewer: KImageViewerState, photos: List<Painter>) {
    DemoCard("사진 뷰어", "썸네일을 누르면 그 자리에서 커지며 열립니다. 두 손가락으로 확대하고, 위아래로 끌면 닫힙니다.") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            photos.indices.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { index ->
                        Image(
                            photos[index],
                            "사진 ${index + 1}",
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .imageViewerSource(viewer, index)
                                .clip(INNER_SHAPE)
                                .kClickable(INNER_SHAPE, role = Role.Image) { viewer.open(index) },
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareCard() {
    DemoCard("전후 비교", "가운데 손잡이를 좌우로 끌거나 원하는 위치를 누르세요. 세로 스크롤은 그대로 됩니다.") {
        BeforeAfterSlider(
            before = { Landscape(vivid = false, Modifier.fillMaxSize()) },
            after = { Landscape(vivid = true, Modifier.fillMaxSize()) },
            modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(INNER_SHAPE),
        )
    }
}

@Composable
private fun EmptyStateCard() {
    var kind by remember { mutableIntStateOf(0) }
    val empty = EMPTY_STATES[kind]
    DemoCard("빈 화면", "검색 결과가 없거나 오프라인일 때 보여 줄 화면입니다. 그림이 천천히 떠 있습니다.") {
        KChipGroup(EMPTY_STATES.map { it.chip }, setOf(kind), { kind = it.firstOrNull() ?: kind })
        KEmptyState(
            empty.title,
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            message = empty.message,
            art = { KEmptyIcon(empty.kind, Modifier.size(72.dp)) },
            action = { KButton(empty.action, {}, variant = KVariant.Tonal) },
        )
    }
}

/** 전체 화면 온보딩. 끝나거나 건너뛰거나 뒤로 가면 [onFinish]를 부른다. */
@Composable
internal fun OnboardingAgain(onFinish: () -> Unit) {
    BackHandler(onBack = onFinish)
    StatusBarIcons(darkBackground = true)
    KOnboarding(
        listOf(
            OnboardingPage("사진을 한곳에", "기기와 클라우드에 흩어진 사진을 모아 봅니다.", Color(0xFF3B4BD8), art = { OnboardingArt(ShowcaseIcons.Grid) }),
            OnboardingPage("함께 보기", "앨범을 가족, 친구와 나누고 같이 꾸밉니다.", Color(0xFFD8456B), art = { OnboardingArt(ShowcaseIcons.Cloud) }),
            OnboardingPage("추억 알림", "작년 오늘 찍은 사진을 아침마다 알려 드려요.", Color(0xFF16896B), art = { OnboardingArt(ShowcaseIcons.Bell) }),
        ),
        onFinish = onFinish,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun OnboardingArt(icon: ImageVector) {
    Image(rememberVectorPainter(icon), null, Modifier.size(140.dp), colorFilter = ColorFilter.tint(Color.White))
}

/** 해가 걸린 산 풍경. [vivid]가 `false`면 보정 전 사진처럼 색을 뺀다. */
@Composable
internal fun Landscape(vivid: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sky = if (vivid) listOf(Color(0xFF2E7BFF), Color(0xFFFF9A62)) else listOf(Color(0xFF7D838C), Color(0xFFB5B0AA))
        drawRect(Brush.verticalGradient(sky))
        drawCircle(
            if (vivid) Color(0xFFFFE07A) else Color(0xFFD6D2CA),
            radius = size.minDimension * 0.12f,
            center = Offset(size.width * 0.72f, size.height * 0.38f),
        )
        drawRidge(listOf(0.70f, 0.45f, 0.68f, 0.50f, 0.62f), if (vivid) Color(0xFF3D4E9E) else Color(0xFF6B6F76))
        drawRidge(listOf(0.85f, 0.72f, 0.80f, 0.66f, 0.78f), if (vivid) Color(0xFF1F7A5A) else Color(0xFF505358))
    }
}

/** 높이 비율 [peaks]를 가로로 고르게 이어 아래를 채운 능선. */
private fun DrawScope.drawRidge(peaks: List<Float>, color: Color) {
    val path = Path()
    path.moveTo(0f, size.height * peaks.first())
    peaks.forEachIndexed { i, y -> path.lineTo(size.width * i / (peaks.size - 1), size.height * y) }
    path.lineTo(size.width, size.height)
    path.lineTo(0f, size.height)
    path.close()
    drawPath(path, color)
}

/** 그라디언트로 그린 가짜 사진. 세로로 긴 3:4 비율이라 뷰어에서 위아래 여백 없이 찬다. */
private class GradientPhoto(private val colors: List<Color>) : Painter() {
    override val intrinsicSize: Size = Size(1200f, 1600f)

    override fun DrawScope.onDraw() {
        drawRect(Brush.linearGradient(colors))
        drawCircle(
            Color.White.copy(alpha = 0.3f),
            radius = size.minDimension * 0.18f,
            center = Offset(
                size.width * 0.68f,
                size.height * 0.3f,
            ),
        )
    }
}

private class EmptyExample(val chip: String, val kind: KEmptyIconKind, val title: String, val message: String, val action: String)

private val EMPTY_STATES = listOf(
    EmptyExample("검색", KEmptyIconKind.NoResults, "검색 결과가 없어요", "다른 검색어로 다시 찾아보세요.", "검색어 지우기"),
    EmptyExample("오프라인", KEmptyIconKind.Offline, "인터넷에 연결되어 있지 않아요", "연결되면 자동으로 다시 불러옵니다.", "다시 시도"),
    EmptyExample("받은편지함", KEmptyIconKind.EmptyInbox, "새 메일이 없어요", "받은 메일을 모두 확인했습니다.", "새로 고침"),
    EmptyExample("오류", KEmptyIconKind.Error, "불러오지 못했어요", "잠시 후 다시 시도해 주세요.", "다시 시도"),
)
