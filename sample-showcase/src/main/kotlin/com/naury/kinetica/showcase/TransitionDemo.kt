// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naury.kinetica.core.scene.SceneAction
import com.naury.kinetica.core.scene.SceneTransitionEdge
import com.naury.kinetica.core.scene.SceneTransitionKind
import com.naury.kinetica.core.scene.SceneTransitionSpec
import com.naury.kinetica.motion.TransitionEnd
import com.naury.kinetica.player.RemoteKineticaPlayer
import com.naury.kinetica.player.SceneTransition
import com.naury.kinetica.player.SharedScenePlayer
import com.naury.kinetica.player.dragToDismiss
import com.naury.kinetica.player.rememberTransitionDriver
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.KChipGroup
import com.naury.kinetica.ui.KSegmentedControl
import com.naury.kinetica.ui.KSlider
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.kClickable

/**
 * 장면 전환 데모. 첫 탭은 홈, 상품, 결제 세 화면이 든 서버 번들을 `RemoteKineticaPlayer(bundle, sceneId)`로 재생하며 14가지 전환과 방향을 고른다.
 * 둘째 탭은 진행도 슬라이더로 `SceneTransition(from, to, spec, progress)`의 중간 프레임을 멈춰 본다. 셋째 탭은 카드 장면이 morphKey로
 * 상세 장면이 되는 예다. 편집기 없이 재생기 모듈만 쓴다.
 */
@Composable
internal fun TransitionDemo(onBack: () -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = GalleryScenes.items.firstOrNull { it.id == selectedId }
    SharedTransitionLayout(Modifier.fillMaxSize().background(DARK)) {
        AnimatedContent(targetState = selected, label = "transition-demo") { item ->
            if (item == null) {
                TransitionHome(this@SharedTransitionLayout, this, onBack) { selectedId = it.id }
            } else {
                SceneDetail(item, this@SharedTransitionLayout, this) { selectedId = null }
            }
        }
    }
}

@Composable
private fun TransitionHome(
    shared: SharedTransitionScope,
    visibility: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onOpen: (GalleryItem) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val choice = rememberSaveable(saver = TransitionChoice.Saver) { TransitionChoice() }
    KineticaScaffold(
        Modifier.fillMaxSize(),
        surface = demoSurface(TRANSITION_DEMO_LOOK),
        background = { DemoBackdrop() },
        topBar = { DemoTopBar("장면 전환", "서버 번들의 화면을 14가지 전환으로 오갑니다", onBack) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            KSegmentedControl(
                TABS,
                tab,
                { tab = it },
                Modifier.align(Alignment.CenterHorizontally).padding(horizontal = readingPadding(16.dp)).widthIn(max = READING_WIDTH)
                    .fillMaxWidth(),
            )
            Box(Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp)) {
                when (tab) {
                    0 -> BundleTab(choice)
                    1 -> ScrubTab(choice)
                    else -> SceneGrid(shared, visibility, onOpen)
                }
            }
        }
    }
}

/** 두 탭이 함께 쓰는 전환 종류와 방향. */
private class TransitionChoice(kind: Int = DEFAULT_KIND, edge: Int = 0) {
    var kind by mutableIntStateOf(kind)
    var edge by mutableIntStateOf(edge)

    val selectedKind: SceneTransitionKind get() = KINDS[kind]
    val directional: Boolean get() = selectedKind in DIRECTIONAL

    /** 고른 종류와 방향의 전환. 결제 버튼이 화면 아래에 있어 iris는 그 근처에서 퍼진다. */
    fun spec(): SceneTransitionSpec = SceneTransitionSpec(
        selectedKind,
        from = SceneTransitionEdge.entries[edge],
        durationMs = if (selectedKind in SLOW) SLOW_MS else SceneTransitionSpec.DEFAULT_DURATION_MS,
        originX = 0.5,
        originY = 0.9,
    )

    companion object {
        val Saver = androidx.compose.runtime.saveable.Saver<TransitionChoice, IntArray>(
            save = { intArrayOf(it.kind, it.edge) },
            restore = { TransitionChoice(it[0], it[1]) },
        )
    }
}

/** 번들 재생. 장면 안 버튼을 누르면 다음 화면으로, 뒤로 가기나 장면의 뒤로 버튼을 누르면 `forward = false`로 같은 전환을 되감는다. */
@Composable
private fun BundleTab(choice: TransitionChoice) {
    val bundle = remember { TransitionScenes.loader() }
    var history by rememberSaveable { mutableStateOf(listOf(TransitionScenes.HOME)) }
    var forward by rememberSaveable { mutableStateOf(true) }
    val back = {
        if (history.size > 1) {
            forward = false
            history = history.dropLast(1)
        }
    }
    BackHandler(enabled = history.size > 1, onBack = back)
    val spec = choice.spec()
    TwoPane(
        phone = {
            RemoteKineticaPlayer(
                bundle,
                history.last(),
                Modifier.fillMaxSize(),
                transition = spec,
                forward = forward,
                onAction = { raw ->
                    val action = SceneAction.parse(raw)
                    val next = action["scene"]
                    when {
                        action.name == "back" -> back()

                        next == TransitionScenes.HOME -> {
                            forward = true
                            history = listOf(next)
                        }

                        next != null -> {
                            forward = true
                            history = history + next
                        }
                    }
                },
            )
        },
    ) {
        Label(history.joinToString(" → ") { TransitionScenes.names.getValue(it) }, 15, FontWeight.SemiBold)
        Label(
            "장면 안의 상품과 버튼을 눌러 보세요. 뒤로 가기는 forward = false로 같은 전환을 거꾸로 재생합니다.",
            13,
            color = LocalKineticaContentColor.current.copy(alpha = 0.7f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("뒤로", filled = history.size > 1, onClick = back)
            Pill("처음으로") {
                forward = false
                history = listOf(TransitionScenes.HOME)
            }
        }
        TransitionPickers(choice)
    }
}

/** 진행도 슬라이더로 두 장면 사이의 중간 프레임을 멈춰 본다. */
@Composable
private fun ScrubTab(choice: TransitionChoice) {
    var pair by rememberSaveable { mutableIntStateOf(0) }
    var backward by rememberSaveable { mutableStateOf(false) }
    var progress by rememberSaveable { mutableFloatStateOf(0.5f) }
    val first = TransitionScenes.order[pair]
    val second = TransitionScenes.order[pair + 1]
    // 뒤로 가는 전환은 지금 화면(두 번째)에서 이전 화면(첫 번째)으로 가며 forward = false로 그린다.
    val (from, to) = if (backward) second to first else first to second
    val spec = choice.spec()
    TwoPane(
        phone = {
            SceneTransition(
                TransitionScenes.documents.getValue(from),
                TransitionScenes.documents.getValue(to),
                spec,
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                forward = !backward,
            )
        },
    ) {
        Label("${TransitionScenes.names[from]} → ${TransitionScenes.names[to]}, 진행도 ${(progress * 100).toInt()}%", 15, FontWeight.SemiBold)
        KSlider(progress, { progress = it }, Modifier.fillMaxWidth().semantics { contentDescription = "전환 진행도" })
        KSegmentedControl(listOf("홈과 상품", "상품과 결제"), pair, { pair = it }, Modifier.fillMaxWidth())
        KSegmentedControl(listOf("앞으로", "뒤로"), if (backward) 1 else 0, { backward = it == 1 }, Modifier.fillMaxWidth())
        Label(
            "예측형 뒤로 가기나 끌어서 닫기처럼 손가락을 따라가는 전환은 이 함수에 진행도를 그대로 넘깁니다.",
            13,
            color = LocalKineticaContentColor.current.copy(alpha = 0.7f),
        )
        TransitionPickers(choice)
    }
}

@Composable
private fun ColumnScope.TransitionPickers(choice: TransitionChoice) {
    val faint = LocalKineticaContentColor.current.copy(alpha = 0.6f)
    Label("전환", 13, FontWeight.SemiBold, Modifier.padding(top = 8.dp), color = faint)
    KChipGroup(KINDS.map { it.jsonName }, setOf(choice.kind), { choice.kind = it.first() })
    Label(if (choice.directional) "방향" else "방향, 이 전환은 방향을 쓰지 않습니다", 13, FontWeight.SemiBold, Modifier.padding(top = 8.dp), color = faint)
    KChipGroup(
        SceneTransitionEdge.entries.map {
            it.jsonName
        },
        setOf(choice.edge),
        { choice.edge = it.first() },
        enabled = choice.directional,
    )
}

/**
 * 휴대폰 모양 틀과 조작부. 넓은 화면에서는 틀을 왼쪽, 조작부를 오른쪽에 두고, 좁은 화면에서는 틀 아래로 조작부가 이어지며 함께 스크롤된다.
 * 장면이 1080×1920이라 틀도 같은 비율이다.
 */
@Composable
private fun TwoPane(phone: @Composable () -> Unit, controls: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val bottom = navigationBarBottom() + 24.dp
        if (wide) {
            Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Box(Modifier.weight(0.45f).fillMaxSize().padding(bottom = bottom), contentAlignment = Alignment.TopCenter) {
                    PhoneFrame(Modifier, phone)
                }
                Column(
                    Modifier.weight(0.55f).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = bottom),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = controls,
                )
            }
        } else {
            val phoneHeight: Dp = (maxHeight * 0.55f).coerceIn(280.dp, 560.dp)
            Column(
                Modifier.fillMaxSize().verticalScroll(
                    rememberScrollState(),
                ).padding(horizontal = readingPadding(16.dp)).padding(bottom = bottom),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PhoneFrame(Modifier.align(Alignment.CenterHorizontally).height(phoneHeight), phone)
                Column(
                    Modifier.widthIn(max = READING_WIDTH).fillMaxWidth().padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = controls,
                )
            }
        }
    }
}

@Composable
private fun PhoneFrame(modifier: Modifier, content: @Composable () -> Unit) {
    // 미끄러지는 전환이 틀 밖으로 나가지 않게 자른다.
    Box(
        modifier
            .aspectRatio(1080f / 1920f, matchHeightConstraintsFirst = true)
            .clip(PHONE_SHAPE)
            .background(Color.White)
            .border(1.dp, LocalKineticaContentColor.current.copy(alpha = 0.12f), PHONE_SHAPE),
    ) { content() }
}

@Composable
private fun SceneGrid(shared: SharedTransitionScope, visibility: AnimatedVisibilityScope, onOpen: (GalleryItem) -> Unit) {
    LazyVerticalGrid(
        // 폰에서도 넓은 화면에서도 두 열로 둔다. 넓은 화면에서 네 장이 한 줄에 작게 놓이고 아래가 비지 않게 한다.
        columns = GridCells.Fixed(2),
        // 카드 칸마다 6dp 여백이 있어 그만큼 빼면 카드 가장자리가 상단 바 캡슐과 맞는다. 넓은 화면에서는 같은 읽기 폭으로 가운데 둔다.
        contentPadding = PaddingValues(
            start = readingPadding(16.dp) - 6.dp,
            end = readingPadding(16.dp) - 6.dp,
            bottom = navigationBarBottom() + 24.dp,
        ),
    ) {
        items(GalleryScenes.items, key = { it.id }) { item ->
            with(shared) {
                SharedScenePlayer(
                    key = item.id,
                    document = item.card,
                    animatedVisibilityScope = visibility,
                    modifier = Modifier
                        .padding(6.dp)
                        .aspectRatio(1080f / 1920f)
                        .clip(RoundedCornerShape(20.dp))
                        .kClickable(RoundedCornerShape(20.dp)) { onOpen(item) }
                        .semantics { contentDescription = "${item.title} 열기" },
                )
            }
        }
    }
}

/** 카드 장면에서 상세 장면으로 morph한다. 완전히 닫혔을 때 [onClosed]를 한 번만 부른다. */
@Composable
private fun SceneDetail(item: GalleryItem, shared: SharedTransitionScope, visibility: AnimatedVisibilityScope, onClosed: () -> Unit) {
    val opening = rememberTransitionDriver()
    val closing = rememberTransitionDriver(onEnd = { if (it == TransitionEnd.Completed) onClosed() })
    LaunchedEffect(opening) { opening.animateTo(forward = true) }
    BackHandler { closing.animateTo(forward = true) }
    val open by opening.state.collectAsState()
    val close by closing.state.collectAsState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val travel = with(LocalDensity.current) { maxHeight.toPx() } * 0.6f
        // 상세 장면 높이를 화면 비율에 맞춰 위아래에 빈 띠가 생기지 않게 한다.
        val height = 1080.0 * maxHeight.value / maxWidth.value.coerceAtLeast(1f)
        val morph = remember(item, height) { item.morph(height) }
        with(shared) {
            Box(Modifier.fillMaxSize().sharedBounds(rememberSharedContentState(item.id), visibility).dragToDismiss(closing, travel)) {
                SceneTransition(morph, open.progress * (1.0 - close.progress), Modifier.fillMaxSize(), background = DARK)
                Label(
                    "아래로 끌어 닫기",
                    13,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(16.dp),
                )
            }
        }
    }
}

private val TABS = listOf("번들 재생", "중간 프레임", "카드 morph")

/** 칩으로 고르는 전환. 바로 바뀌는 None은 뺀다. */
private val KINDS = SceneTransitionKind.entries.filter { it != SceneTransitionKind.None }
private val DEFAULT_KIND = KINDS.indexOf(SceneTransitionKind.Push)
private val DIRECTIONAL = setOf(
    SceneTransitionKind.Slide,
    SceneTransitionKind.Push,
    SceneTransitionKind.Cover,
    SceneTransitionKind.Uncover,
    SceneTransitionKind.Wipe,
    SceneTransitionKind.Rotate,
    SceneTransitionKind.Flip,
    SceneTransitionKind.Cube,
)

// 3D로 도는 전환은 기본 시간이면 너무 빨라 무슨 일이 일어났는지 보기 어렵다.
private val SLOW = setOf(SceneTransitionKind.Rotate, SceneTransitionKind.Flip, SceneTransitionKind.Cube, SceneTransitionKind.Dissolve)
private const val SLOW_MS = 650
private val PHONE_SHAPE = RoundedCornerShape(28.dp)

// 장면 바탕색과 같게 둬서 화면 비율이 달라도 위아래에 띠가 보이지 않게 한다.
private val DARK = Color(0xFF15181E)

private val TRANSITION_DEMO_LOOK = DemoLook(GlassStyles.Liquid, GlassStyles.LiquidDark, defaultDark = true)
