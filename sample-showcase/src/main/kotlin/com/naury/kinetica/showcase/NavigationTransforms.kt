// 화면 모양 값(간격·크기·색)과 예시 문구는 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KContainerTransform
import com.naury.kinetica.ui.KExpandHost
import com.naury.kinetica.ui.KExpandState
import com.naury.kinetica.ui.KFab
import com.naury.kinetica.ui.KIconButton
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KImageViewer
import com.naury.kinetica.ui.KImageViewerState
import com.naury.kinetica.ui.KSurface
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.expandSource
import com.naury.kinetica.ui.imageViewerSource
import com.naury.kinetica.ui.jellyPress
import com.naury.kinetica.ui.kClickable
import com.naury.kinetica.ui.rememberKExpandState
import com.naury.kinetica.ui.rememberKImageViewerState

/** 컨테이너 변환 예시의 상태. 변환 모양마다 [KExpandHost]를 하나씩 둔다. */
@Stable
internal class TransformDemo(
    val card: KExpandState,
    val row: KExpandState,
    val fab: KExpandState,
    val viewer: KImageViewerState,
    val photos: List<Painter>,
)

@Composable
internal fun rememberTransformDemo(): TransformDemo {
    val card = rememberKExpandState()
    val row = rememberKExpandState()
    val fab = rememberKExpandState()
    val viewer = rememberKImageViewerState()
    return remember(card, row, fab, viewer) {
        TransformDemo(card, row, fab, viewer, PHOTO_COLORS.take(PHOTO_COUNT).map { GradientPhoto(it) })
    }
}

/** 카드, 목록 행, 플로팅 버튼, 사진이 그 자리에서 화면으로 커지는 예 */
internal fun LazyListScope.transformItems(demo: TransformDemo) {
    item("transform-title") {
        Column(Modifier.widthIn(max = READING_WIDTH).fillMaxWidth().padding(top = 8.dp)) {
            Label("컨테이너 변환", 20, FontWeight.Bold)
            Label(
                "작은 요소가 그 자리에서 상세 화면으로 커집니다. 열린 화면을 아래로 끌거나 뒤로 가면 원래 자리로 줄어듭니다.",
                13,
                color = LocalKineticaContentColor.current.copy(alpha = 0.7f),
            )
        }
    }
    STORIES.forEachIndexed { index, story -> item("story$index") { StoryCard(story, demo.card, index) } }
    item("transform-rows") { MailRowsCard(demo.row) }
    item("transform-fab-photos") { FabAndPhotosCard(demo) }
}

@Composable
private fun StoryCard(story: NavStory, expand: KExpandState, index: Int) {
    Box(
        Modifier
            .widthIn(max = READING_WIDTH)
            .fillMaxWidth()
            .expandSource(expand, index)
            .jellyPress(0.03f)
            .clip(STORY_SHAPE)
            .kClickable(STORY_SHAPE) { expand.expand(index) }
            .semantics { contentDescription = "${story.title} 열기" },
    ) { StoryCover(story) }
}

/** 카드와 상세 화면 윗부분이 똑같이 생겨야 커지는 동안 화면이 튀지 않는다. */
@Composable
private fun StoryCover(story: NavStory) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.6f).background(Brush.linearGradient(story.colors)),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(Modifier.padding(24.dp)) {
            Label(story.kicker, 13, FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f))
            Label(story.title, 28, FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun MailRowsCard(expand: KExpandState) {
    DemoCard(
        "목록 행",
        "KContainerTransform.listRow는 행 자리에서 상자가 화면 전체로 커지고, 상세 내용은 상자가 반쯤 커진 뒤 나타납니다.",
        Modifier.widthIn(max = READING_WIDTH),
    ) {
        MAILS.forEachIndexed { index, mail ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .expandSource(expand, index)
                    .clip(INNER_SHAPE)
                    .kClickable(INNER_SHAPE, onClickLabel = "열기") { expand.expand(index) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(PHOTO_COLORS[index % PHOTO_COLORS.size])))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Label(mail.first, 15, FontWeight.SemiBold)
                    Label(mail.second, 13, color = LocalKineticaContentColor.current.copy(alpha = 0.65f))
                }
            }
        }
    }
}

@Composable
private fun FabAndPhotosCard(demo: TransformDemo) {
    DemoCard(
        "플로팅 버튼과 사진",
        "버튼은 KContainerTransform.fab으로 모서리가 펴지며 화면이 되고, 사진은 KImageViewer가 썸네일 자리에서 커지며 열립니다.",
        Modifier.widthIn(max = READING_WIDTH),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            demo.photos.forEachIndexed { index, photo ->
                Image(
                    photo,
                    "사진 ${index + 1}",
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .imageViewerSource(demo.viewer, index)
                        .clip(INNER_SHAPE)
                        .kClickable(INNER_SHAPE, role = Role.Image) { demo.viewer.open(index) },
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Label("버튼을 누르면 메모 화면이 됩니다", 14, modifier = Modifier.weight(1f), color = LocalKineticaContentColor.current.copy(alpha = 0.7f))
            KFab(KIcons.Edit, "새 메모", { demo.fab.expand(0) }, Modifier.expandSource(demo.fab, 0))
        }
    }
}

/**
 * 열린 상세 화면들. 상태 표시줄 아이콘 밝기를 정하도록 열린 화면의 배경이 어두운지 [onOverlayDark]로 알린다. 열린 화면이 없으면 `null`이다.
 */
@Composable
internal fun TransformHosts(demo: TransformDemo, onOverlayDark: (Boolean?) -> Unit) {
    val dark = KineticaTheme.surface.isDark
    val rowColor = if (dark) Color(0xFF1C1F26) else Color(0xFFF7F7FA)
    val accent = KineticaTheme.colors.accent
    KExpandHost(demo.card, cornerRadius = 28.dp, title = "이야기") { key -> StoryDetail(STORIES[key as Int], demo.card) }
    KExpandHost(demo.row, transform = KContainerTransform.listRow(rowColor), title = "메일") { key ->
        MailDetail(key as Int, rowColor, demo.row)
    }
    KExpandHost(demo.fab, transform = KContainerTransform.fab(accent), title = "새 메모") { NoteDetail(accent, demo.fab) }
    KImageViewer(demo.viewer, demo.photos, contentDescriptions = demo.photos.indices.map { "사진 ${it + 1}" })
    val overlay = when {
        demo.card.expandedKey != null || demo.fab.expandedKey != null -> true
        demo.row.expandedKey != null -> dark
        else -> null
    }
    SideEffect { onOverlayDark(overlay) }
}

@Composable
private fun StoryDetail(story: NavStory, expand: KExpandState) {
    Box(Modifier.fillMaxSize().background(Color(0xFFF7F7FA))) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            StoryCover(story)
            // 넓은 화면에서도 본문 한 줄이 너무 길지 않게 읽기 폭으로 묶어 가운데 둔다.
            Column(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = READING_WIDTH)
                    .padding(24.dp)
                    .padding(bottom = navigationBarBottom()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Label("아래로 끌면 작아지면서 원래 카드로 돌아갑니다.", 15, FontWeight.SemiBold, color = Color(0xFF15161A))
                repeat(6) { Label(story.body, 16, color = Color(0xFF3A3B40)) }
            }
        }
        CloseButton(Modifier.align(Alignment.TopEnd)) { expand.collapse() }
    }
}

@Composable
private fun MailDetail(index: Int, background: Color, expand: KExpandState) {
    val (sender, subject) = MAILS[index]
    Box(Modifier.fillMaxSize().background(background)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .padding(top = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Label(subject, 26, FontWeight.Bold, Modifier.widthIn(max = READING_WIDTH))
            Label(sender, 15, FontWeight.SemiBold, color = LocalKineticaContentColor.current.copy(alpha = 0.7f))
            repeat(4) {
                Label(
                    "행과 상세 화면의 모양이 많이 다를 때는 내용을 그대로 키우지 않고, 같은 색 상자가 먼저 커진 뒤 상세 내용이 나타나게 합니다.",
                    16,
                    modifier = Modifier.widthIn(max = READING_WIDTH),
                )
            }
        }
        CloseButton(Modifier.align(Alignment.TopEnd)) { expand.collapse() }
    }
}

@Composable
private fun NoteDetail(accent: Color, expand: KExpandState) {
    CompositionLocalProvider(LocalKineticaContentColor provides Color.White) {
        Box(Modifier.fillMaxSize().background(accent)) {
            Column(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp).padding(top = 48.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Label("새 메모", 30, FontWeight.Bold)
                Label("버튼 색과 같은 화면이 버튼 자리에서 커졌습니다. 닫으면 다시 동그란 버튼으로 줄어듭니다.", 16, color = Color.White.copy(alpha = 0.85f))
            }
            CloseButton(Modifier.align(Alignment.TopEnd)) { expand.collapse() }
        }
    }
}

@Composable
private fun CloseButton(modifier: Modifier, onClick: () -> Unit) {
    KIconButton(
        KIcons.Close,
        "닫기",
        onClick,
        modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
            .padding(16.dp),
    )
}

/** 컨테이너 변환 예시에서 쓰는 카드 하나 */
internal class NavStory(val kicker: String, val title: String, val colors: List<Color>, val body: String)

private const val PHOTO_COUNT = 4
private val STORY_SHAPE = RoundedCornerShape(28.dp)
private val MAILS = listOf(
    "김하늘" to "다음 주 회의 자료 공유드립니다",
    "여행 앱" to "예약하신 숙소 확인 안내",
    "이도윤" to "사진 보내 드려요",
)
private val STORIES = listOf(
    NavStory(
        "오늘의 앱",
        "빛으로 그린 도시",
        listOf(Color(0xFF141E30), Color(0xFF8E54E9)),
        "밤의 도시를 빛의 줄기로 담는 사진 앱입니다. 셔터를 오래 열어 차의 불빛을 길게 남깁니다.",
    ),
    NavStory(
        "새 기능",
        "바다를 담는 법",
        listOf(Color(0xFF00C6FF), Color(0xFF0072FF)),
        "물결이 멈춘 듯한 순간을 잡으려면 빠른 셔터와 낮은 시선이 필요합니다.",
    ),
)
