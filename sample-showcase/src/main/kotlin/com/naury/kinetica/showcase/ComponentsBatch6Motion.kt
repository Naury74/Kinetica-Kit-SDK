// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KBadge
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KButtonSize
import com.naury.kinetica.ui.KCircularReveal
import com.naury.kinetica.ui.KCopyButton
import com.naury.kinetica.ui.KFlyToHost
import com.naury.kinetica.ui.KFullScreenModal
import com.naury.kinetica.ui.KMorphGlyph
import com.naury.kinetica.ui.KMorphIcons
import com.naury.kinetica.ui.KScrollEffect
import com.naury.kinetica.ui.KSegmentedControl
import com.naury.kinetica.ui.KShapeToken
import com.naury.kinetica.ui.KSharedTransitionHost
import com.naury.kinetica.ui.KSurface
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.MorphIcon
import com.naury.kinetica.ui.floatUp
import com.naury.kinetica.ui.flyToSource
import com.naury.kinetica.ui.flyToTarget
import com.naury.kinetica.ui.holdToConfirm
import com.naury.kinetica.ui.kClickable
import com.naury.kinetica.ui.kOrigin
import com.naury.kinetica.ui.kSharedBounds
import com.naury.kinetica.ui.kSharedElement
import com.naury.kinetica.ui.rememberKFloatUpState
import com.naury.kinetica.ui.rememberKFlyToState
import com.naury.kinetica.ui.rememberKStaggerState
import com.naury.kinetica.ui.scrollTransform
import com.naury.kinetica.ui.staggerIn

/** 모달, 원형 드러내기, 공유 요소 전환. 셋 다 예측형 뒤로 가기 제스처를 따라간다. */
@Composable
internal fun TransitionsCard() {
    var modal by rememberSaveable { mutableStateOf(false) }
    var dark by rememberSaveable { mutableStateOf(false) }
    var origin by remember { mutableStateOf<Offset?>(null) }
    DemoCard("화면 전환", "모달은 아래에서 올라오고 아래로 끌면 닫힙니다. 원형 드러내기는 누른 버튼에서 퍼집니다. 사진을 누르면 상세 화면으로 커지며, 셋 모두 뒤로 가기 제스처를 따라 되감깁니다.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KButton("새 게시물", { modal = true }, size = KButtonSize.Small)
            KButton(if (dark) "밝게" else "어둡게", {
                dark = !dark
            }, Modifier.kOrigin { origin = it }, variant = KVariant.Tonal, size = KButtonSize.Small)
        }
        Box(Modifier.fillMaxWidth().height(120.dp).clip(kitShape { KShapeToken.Medium }).background(Color(0xFFF2F2F7))) {
            Label("밝은 패널", 15, FontWeight.SemiBold, Modifier.align(Alignment.Center), color = Color(0xFF15161A))
            KCircularReveal(dark, Modifier.fillMaxSize(), origin = origin, onBack = { dark = false }) {
                Box(Modifier.fillMaxSize().background(Color(0xFF15161A)), contentAlignment = Alignment.Center) {
                    Label("어두운 패널", 15, FontWeight.SemiBold, color = Color.White)
                }
            }
        }
        SharedPhotos()
    }
    KFullScreenModal(modal, { modal = false }, title = "새 게시물") { ComposeSheet { modal = false } }
}

@Composable
private fun ComposeSheet(onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("새 게시물", 22, FontWeight.Bold, Modifier.weight(1f))
            KButton("닫기", onClose, variant = KVariant.Text, size = KButtonSize.Small)
        }
        Label("아래로 끌거나 뒤로 가기 제스처를 하면 닫힙니다. 목록이 맨 위에 있을 때는 목록을 끌어도 이어서 닫힙니다.", 14)
        repeat(8) { i ->
            KSurface(Modifier.fillMaxWidth().height(72.dp), shape = kitShape { KShapeToken.Medium }) {
                Label("초안 ${i + 1}", 15, modifier = Modifier.align(Alignment.CenterStart).padding(16.dp))
            }
        }
    }
}

/** 사진 격자에서 누른 사진이 상세 화면 머리 사진으로 커진다. */
@Composable
private fun SharedPhotos() {
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    Box(Modifier.fillMaxWidth().height(220.dp).clip(kitShape { KShapeToken.Medium })) {
        KSharedTransitionHost(selected, onBack = if (selected != null) ({ selected = null }) else null, backTarget = null) { photo ->
            if (photo == null) {
                Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PHOTO_GRADIENTS.forEachIndexed { i, colors ->
                        Box(
                            Modifier
                                // 상세 화면이 들어갈 높이를 비워 두면 격자 아래가 휑하므로 사진을 세로로 길게 채운다.
                                .weight(1f)
                                .fillMaxHeight()
                                .kSharedElement("photo-$i")
                                .clip(kitShape { KShapeToken.Medium })
                                .background(Brush.linearGradient(colors))
                                .kClickable(kitShape { KShapeToken.Medium }, onClickLabel = "사진 ${i + 1} 열기") { selected = i },
                        )
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().kSharedBounds("detail")) {
                    Box(
                        Modifier.fillMaxWidth().height(
                            150.dp,
                        ).kSharedElement("photo-$photo").background(Brush.linearGradient(PHOTO_GRADIENTS[photo])),
                    )
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Label("사진 ${photo + 1}", 16, FontWeight.SemiBold, Modifier.weight(1f))
                        KButton("닫기", { selected = null }, variant = KVariant.Text, size = KButtonSize.Small)
                    }
                }
            }
        }
    }
}

/** 차례 등장과 스크롤 변형 */
@Composable
internal fun ListMotionCard() {
    var round by remember { mutableIntStateOf(0) }
    var effect by rememberSaveable { mutableIntStateOf(0) }
    DemoCard("목록 모션", "다시 보기를 누르면 항목이 차례로 떠오릅니다. 아래 목록은 위아래 가장자리로 갈수록 작아지거나 눕습니다.") {
        androidx.compose.runtime.key(round) {
            val stagger = rememberKStaggerState()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(4) { i ->
                    KSurface(Modifier.fillMaxWidth().height(44.dp).staggerIn(i, stagger), shape = kitShape { KShapeToken.Medium }) {
                        Label("알림 ${i + 1}", 14, modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp))
                    }
                }
            }
        }
        KButton("다시 보기", { round++ }, variant = KVariant.Tonal, size = KButtonSize.Small)
        KSegmentedControl(listOf("작아짐", "휠", "위로 쌓임"), effect, { effect = it }, Modifier.fillMaxWidth())
        val list = rememberLazyListState()
        val kind = KScrollEffect.entries[effect]
        LazyColumn(
            Modifier.fillMaxWidth().height(260.dp),
            state = list,
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(40) { i ->
                KSurface(
                    Modifier.fillMaxWidth().height(48.dp).scrollTransform(list, i, kind, edge = 90.dp),
                    shape = kitShape { KShapeToken.Medium },
                ) {
                    Label("항목 ${i + 1}", 14, modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp))
                }
            }
        }
    }
}

/** 장바구니로 날아가기, 길게 눌러 삭제, 복사됨, +1 떠오름 */
@Composable
internal fun MicroCard() {
    val fly = rememberKFlyToState()
    val plus = rememberKFloatUpState()
    var cart by rememberSaveable { mutableIntStateOf(0) }
    var likes by rememberSaveable { mutableIntStateOf(128) }
    var deleted by rememberSaveable { mutableStateOf(false) }
    DemoCard("마이크로 인터랙션", "담기를 누르면 상품이 장바구니로 날아갑니다. 빨간 원은 1초 동안 눌러야 삭제됩니다. 쿠폰 코드는 복사하면 체크로 바뀌고, 하트는 +1이 떠오릅니다.") {
        KFlyToHost(fly, Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("러닝화 에어 라이트", 15, FontWeight.SemiBold, Modifier.weight(1f))
                    Box(Modifier.flyToTarget(fly)) {
                        KSurface(Modifier.size(44.dp), shape = CircleShape) {
                            MorphIcon(KMorphIcons.Plus, KMorphIcons.Check, cart > 0, Modifier.align(Alignment.Center))
                        }
                        if (cart > 0) KBadge(cart, Modifier.align(Alignment.TopEnd))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(
                            64.dp,
                        ).flyToSource(fly, "shoe").clip(
                            kitShape {
                                KShapeToken.Medium
                            },
                        ).background(Brush.linearGradient(PHOTO_GRADIENTS[0])),
                    )
                    KButton("담기", { fly.launch("shoe", onArrive = { cart++ }) { ShoeThumb() } }, size = KButtonSize.Small)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            HoldDelete(deleted) { deleted = !deleted }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Label("KINETICA-20", 15, FontWeight.Bold)
                KCopyButton("KINETICA-20", label = "쿠폰 코드", size = 40.dp)
            }
            Row(Modifier.floatUp(plus, color = KineticaTheme.colors.destructive), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).kClickable(CircleShape, onClickLabel = "좋아요") {
                        likes++
                        plus.emit("+1")
                    },
                    contentAlignment = Alignment.Center,
                ) { MorphIcon(KMorphIcons.Heart, KMorphIcons.HeartFilled, likes > 128, color = KineticaTheme.colors.destructive) }
                Label("$likes", 14)
            }
        }
    }
}

@Composable
private fun ShoeThumb() {
    Box(Modifier.fillMaxSize().clip(kitShape { KShapeToken.Medium }).background(Brush.linearGradient(PHOTO_GRADIENTS[0])))
}

@Composable
private fun HoldDelete(deleted: Boolean, onConfirm: () -> Unit) {
    val red = KineticaTheme.colors.destructive
    Box(
        Modifier
            .size(52.dp)
            .holdToConfirm(onConfirm, CircleShape, durationMs = 1_000, color = red, label = if (deleted) "되돌리기" else "길게 눌러 삭제")
            .clip(CircleShape)
            .background(red.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        MorphIcon(KMorphIcons.Close, KMorphIcons.Check, deleted, color = red)
    }
}

/** 모핑 아이콘 다섯 쌍. 누를 때마다 서로 바뀐다. */
@Composable
internal fun MorphCard() {
    var on by rememberSaveable { mutableStateOf(setOf<Int>()) }
    DemoCard("모핑 아이콘", "선의 점을 이어 옮겨 두 아이콘 사이를 모핑합니다. 하트는 안이 차오르며 튑니다. 뒤로 가기 화살표는 RTL에서 뒤집힙니다.") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MORPH_PAIRS.forEachIndexed { i, (name, pair) ->
                val active = i in on
                KSurface(
                    Modifier.size(64.dp).clip(
                        kitShape {
                            KShapeToken.Large
                        },
                    ).kClickable(kitShape { KShapeToken.Large }, onClickLabel = name) {
                        on = if (active) on - i else on + i
                    },
                    shape = kitShape { KShapeToken.Large },
                ) {
                    MorphIcon(
                        pair.first,
                        pair.second,
                        active,
                        Modifier.align(Alignment.Center).size(28.dp),
                        color = LocalKineticaContentColor.current,
                    )
                }
            }
        }
    }
}

private val MORPH_PAIRS: List<Pair<String, Pair<KMorphGlyph, KMorphGlyph>>> = listOf(
    "검색" to (KMorphIcons.Search to KMorphIcons.Close),
    "추가" to (KMorphIcons.Plus to KMorphIcons.Check),
    "메뉴" to (KMorphIcons.Menu to KMorphIcons.ArrowBack),
    "좋아요" to (KMorphIcons.Heart to KMorphIcons.HeartFilled),
    "소리" to (KMorphIcons.VolumeLow to KMorphIcons.VolumeHigh),
)

private val PHOTO_GRADIENTS = listOf(
    listOf(Color(0xFFFFD3A5), Color(0xFFFD6585)),
    listOf(Color(0xFF84FAB0), Color(0xFF8FD3F4)),
    listOf(Color(0xFFA18CD1), Color(0xFFFBC2EB)),
)
