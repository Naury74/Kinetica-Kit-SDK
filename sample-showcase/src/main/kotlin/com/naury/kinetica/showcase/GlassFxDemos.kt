// 화면 모양 값(간격, 크기, 색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.Detent
import com.naury.kinetica.ui.GlassBackdrop
import com.naury.kinetica.ui.KDetentSheet
import com.naury.kinetica.ui.KFloatingTabBar
import com.naury.kinetica.ui.KGlassGroup
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KLensThumb
import com.naury.kinetica.ui.KSegmentedControl
import com.naury.kinetica.ui.KSlider
import com.naury.kinetica.ui.KSwitch
import com.naury.kinetica.ui.KTab
import com.naury.kinetica.ui.LocalGlassBackdrop
import com.naury.kinetica.ui.glass
import com.naury.kinetica.ui.glassBackdrop
import com.naury.kinetica.ui.glassLens
import com.naury.kinetica.ui.kClickable
import com.naury.kinetica.ui.rememberGlassBackdrop
import com.naury.kinetica.ui.rememberKDetentSheetState
import com.naury.kinetica.ui.scrollEdgeBlur
import com.naury.kinetica.ui.verticalFadingEdges
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * iOS 26 Liquid Glass 계열 효과 묶음: 렌즈 손잡이, 탭 바 렌즈, 녹아 붙는 글래스, 점진 블러, 떠 있는 글래스 시트.
 * 맨 위 비교 칸에서 효과를 끄면 같은 화면을 기존 동작으로 그려, 같은 조작으로 프레임 시간을 번갈아 잴 수 있다.
 */
internal fun LazyListScope.glassFxDemos(mode: MutableIntState) {
    item {
        DemoCard("효과 비교", "효과를 끄면 아래 카드가 모두 기존 동작으로 바뀝니다. 같은 조작을 번갈아 해 보며 차이를 비교하세요.") {
            KSegmentedControl(listOf("효과 켜기", "효과 끄기"), mode.intValue, { mode.intValue = it }, Modifier.fillMaxWidth())
        }
    }
    item { LensControlsCard(mode.intValue == 0) }
    item { LensRulerCard(mode.intValue == 0) }
    item { LensTabBarCard(mode.intValue == 0) }
    item { GlassGroupCard(mode.intValue == 0) }
    item { EdgeBlurCard(mode.intValue == 0) }
    item { FloatingSheetCard(mode.intValue == 0) }
}

@Composable
private fun LensControlsCard(fx: Boolean) {
    var volume by rememberSaveable { mutableFloatStateOf(0.4f) }
    var wifi by rememberSaveable { mutableStateOf(true) }
    var range by rememberSaveable { mutableIntStateOf(0) }
    DemoCard(
        "유리 렌즈 손잡이",
        "손잡이를 잡고 있는 동안 유리 방울로 부풀어 아래 트랙을 확대합니다. 세그먼트는 선택된 칸을 잡고 옆으로 끌어 옮길 수 있습니다.",
    ) {
        KSlider(volume, { volume = it }, Modifier.fillMaxWidth(), label = "볼륨", lens = fx)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label("와이파이", 15, modifier = Modifier.weight(1f))
            KSwitch(wifi, { wifi = it }, lens = fx)
        }
        KSegmentedControl(listOf("하루", "한 주", "한 달", "한 해"), range, { range = it }, Modifier.fillMaxWidth(), lens = fx)
    }
}

/** 공개 수정자로 직접 만든 렌즈. 눈금 위 손잡이를 끄는 동안 숫자를 확대해 보여 준다. */
@Composable
private fun LensRulerCard(fx: Boolean) {
    val grab = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var x by remember { mutableFloatStateOf(0.5f) }
    var width by remember { mutableIntStateOf(1) }
    DemoCard("렌즈 수정자", "glassLens를 직접 붙인 눈금자입니다. 손잡이를 끌면 렌즈가 지나가는 숫자를 확대하고, 놓으면 불투명한 손잡이로 돌아옵니다.") {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .onSizeChanged { width = it.width.coerceAtLeast(1) }
                .glassLens(progress = { if (fx) grab.value else 0f }, center = { Offset(it.width * x, it.height / 2) })
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { scope.launch { grab.animateTo(1f, spring(Spring.DampingRatioMediumBouncy)) } },
                        onDragEnd = { scope.launch { grab.animateTo(0f, spring(Spring.DampingRatioMediumBouncy)) } },
                        onDragCancel = { scope.launch { grab.animateTo(0f) } },
                    ) { change, delta ->
                        x = (x + delta / width).coerceIn(0f, 1f)
                        change.consume()
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (n in 0..100 step 10) Label("$n", 12, FontWeight.SemiBold)
            }
            KLensThumb(
                progress = { if (fx) grab.value else 0f },
                modifier = Modifier.offset { IntOffset((width * x - 18.dp.toPx()).roundToInt(), 0) },
            )
        }
    }
}

@Composable
private fun LensTabBarCard(fx: Boolean) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(KTab("홈", KIcons.Home), KTab("검색", KIcons.Search), KTab("알림", KIcons.Bell), KTab("설정", KIcons.Settings))
    DemoCard("탭 바 렌즈", "선택된 탭을 잡고 끌면 인디케이터가 렌즈가 되어 지나가는 탭을 확대합니다. 놓으면 가까운 탭에 붙습니다.") {
        KFloatingTabBar(tabs, tab, { tab = it }, lens = fx)
    }
}

/**
 * 떠 있는 버튼 묶음. 가운데 버튼을 누르면 위성 버튼이 펼쳐지며 액체처럼 갈라지고, 다시 누르면 녹아 붙는다.
 * 효과를 끄면 같은 버튼을 따로 둔 글래스 판으로 그린다.
 */
@Composable
private fun GlassGroupCard(fx: Boolean) {
    val spread = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var open by rememberSaveable { mutableStateOf(false) }
    val backdrop = rememberGlassBackdrop()
    val toggle = {
        open = !open
        scope.launch { spread.animateTo(if (open) 1f else 0f, spring(0.7f, Spring.StiffnessVeryLow)) }
        Unit
    }
    DemoCard("녹아 붙는 글래스", "가운데 버튼을 누르면 버튼들이 펼쳐지며 갈라지고, 다시 누르면 하나로 녹아 붙습니다. 배경이 판을 통해 비칩니다.") {
        Box(Modifier.fillMaxWidth().height(260.dp).clip(innerShape)) {
            ColorfulBackdrop(backdrop)
            CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                val area = Modifier.align(Alignment.Center).size(220.dp)
                if (fx) {
                    KGlassGroup(area) { Box(Modifier.fillMaxSize()) { FabCluster(spread::value, open, toggle) { glassId(it) } } }
                } else {
                    Box(area) { FabCluster(spread::value, open, toggle) { glass(shape = CircleShape) } }
                }
            }
        }
    }
}

/** 가운데 버튼과 위성 버튼 셋. [plate]가 판 모양 수정자를 붙인다. [spread]는 펼친 정도(0..1)로 배치 단계에서만 읽는다. */
@Composable
private fun BoxScope.FabCluster(spread: () -> Float, open: Boolean, onToggle: () -> Unit, plate: @Composable Modifier.(Any) -> Modifier) {
    val satellites = listOf(KIcons.Camera to "사진", KIcons.Heart to "좋아요", KIcons.Share to "공유")
    satellites.forEachIndexed { i, (icon, name) ->
        val angle = Math.toRadians(-150.0 + i * 60.0)
        Box(
            Modifier
                .align(Alignment.Center)
                .offset {
                    val r = 78.dp.toPx() * spread()
                    IntOffset((cos(angle) * r).roundToInt(), (sin(angle) * r).roundToInt())
                }
                .plate(name)
                .size(52.dp)
                .then(if (open) Modifier.semantics { contentDescription = name } else Modifier.clearAndSetSemantics {}),
            contentAlignment = Alignment.Center,
        ) {
            // 접혀 있으면 가운데 버튼 밑에 겹쳐 있으므로 펼친 만큼만 아이콘을 드러낸다.
            Box(Modifier.graphicsLayer { alpha = (spread() * 2f - 0.6f).coerceIn(0f, 1f) }) { ShowcaseIcon(icon, color = Color.White) }
        }
    }
    Box(
        Modifier
            .align(Alignment.Center)
            .plate("main")
            .size(64.dp)
            .kClickable(CircleShape, onClick = onToggle)
            .semantics { contentDescription = if (open) "접기" else "펼치기" },
        contentAlignment = Alignment.Center,
    ) { ShowcaseIcon(KIcons.Plus, 28.dp, Color.White) }
}

@Composable
private fun EdgeBlurCard(fx: Boolean) {
    val list = rememberLazyListState()
    DemoCard("점진 블러", "목록 위아래 끝에서 내용이 점점 흐려지며 사라집니다. 맨 위에서는 위쪽이 흐려지지 않습니다.") {
        Box(Modifier.fillMaxWidth().height(320.dp).clip(innerShape)) {
            val edge = if (fx) Modifier.scrollEdgeBlur(list, top = 56.dp, bottom = 56.dp) else Modifier.verticalFadingEdges(list, 56.dp)
            LazyColumn(Modifier.fillMaxSize().then(edge), state = list) {
                items(40) { i ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(40.dp).background(RowColors[i % RowColors.size], RoundedCornerShape(12.dp)))
                        Label("사진 ${i + 1}", 15, FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingSheetCard(fx: Boolean) {
    val backdrop = rememberGlassBackdrop()
    val sheet = rememberKDetentSheetState(listOf(Detent.Peek(96.dp), Detent.Fraction(0.5f), Detent.Full))
    DemoCard("떠 있는 글래스 시트", "낮은 높이에서는 가장자리에서 떨어진 유리 판이고, 맨 위로 끌어 올리면 가장자리에 붙으며 불투명해집니다.") {
        Box(Modifier.fillMaxWidth().height(440.dp).clip(innerShape)) {
            ColorfulBackdrop(backdrop)
            CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                KDetentSheet(sheet, title = "주변 장소", floatingGlass = fx, header = {
                    Label("주변 장소", 17, FontWeight.Bold, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(20) { Label("장소 ${it + 1}", 15, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) }
                    }
                }
            }
        }
    }
}

/** 글래스가 비출 색 띠 배경. [backdrop]에 기록해 그 위 글래스가 이 배경을 블러로 비춘다. */
@Composable
private fun BoxScope.ColorfulBackdrop(backdrop: GlassBackdrop) {
    Box(Modifier.matchParentSize().glassBackdrop(backdrop).background(Brush.linearGradient(BackdropColors))) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(8) { Label("배경 글자 줄 ${it + 1}", 16, FontWeight.Bold, color = Color.White.copy(alpha = 0.85f)) }
        }
    }
}

private val BackdropColors = listOf(Color(0xFFFF5F6D), Color(0xFF7B5CFF), Color(0xFF1FB6FF), Color(0xFF29D3A0))
private val RowColors = listOf(Color(0xFFFF8A65), Color(0xFF7986CB), Color(0xFF4DB6AC), Color(0xFFFFD54F), Color(0xFFBA68C8))
