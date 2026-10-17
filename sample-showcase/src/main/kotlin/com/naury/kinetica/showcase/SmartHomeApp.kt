// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
// 예시 데이터 클래스는 필드를 생성자에 나란히 두는 편이 읽기 쉬워 인자가 많다.
@file:Suppress("MagicNumber", "LongParameterList")

package com.naury.kinetica.showcase

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.KGauge
import com.naury.kinetica.ui.KGaugeSegment
import com.naury.kinetica.ui.KGroupItem
import com.naury.kinetica.ui.KIconButton
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KSegmentedControl
import com.naury.kinetica.ui.KSlider
import com.naury.kinetica.ui.KSurface
import com.naury.kinetica.ui.KSwitch
import com.naury.kinetica.ui.KToggleGroup
import com.naury.kinetica.ui.KTone
import com.naury.kinetica.ui.KTopAppBar
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.PushNavigation
import com.naury.kinetica.ui.Sparkline
import com.naury.kinetica.ui.SurfaceStyle
import com.naury.kinetica.ui.jellyPress
import kotlin.math.roundToInt

/**
 * 스마트 홈 앱: 방별 기기 타일과 장면, 조명 상세, 냉난방 상세 세 화면. 따뜻한 실내 그라디언트 위에 Obsidian 글래스를 얹은 어두운 화면이다.
 * 장면을 누르면 여러 기기가 한꺼번에 켜지거나 꺼지고, 조명 밝기와 색 온도를 바꾸면 미리보기 빛이 따라 바뀐다.
 */
@Composable
internal fun SmartHomeApp(onBack: () -> Unit) {
    var stack by rememberSaveable { mutableStateOf(listOf("home")) }
    // 기기 id → 켜짐(1)/꺼짐(0), 기기 id → 수준(밝기 %, 온도 ℃, 풍량 단계)
    val on = rememberIntMap(DEVICES.map { it.id to if (it.startsOn) 1 else 0 })
    val level = rememberIntMap(DEVICES.map { it.id to it.level })
    val pop = { stack = stack.dropLast(1) }
    AppTheme(SMART_HOME_LOOK) {
        PushNavigation(stack, onBack = pop) { screen ->
            when {
                screen == "home" -> HomeDevices(on, level, onBack) { stack = stack + "device/$it" }

                else -> {
                    val device = DEVICES.first { it.id == screen.substringAfter('/').toInt() }
                    if (device.kind == Kind.Climate) ClimateDetail(device, on, level, pop) else LightDetail(device, on, level, pop)
                }
            }
        }
    }
}

@Composable
private fun RoomGlow() {
    val dark = SMART_HOME_LOOK.isDark()
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Brush.verticalGradient(
                if (dark) {
                    listOf(
                        Color(0xFF1A1420),
                        Color(0xFF2B1D24),
                        Color(0xFF3A2620),
                    )
                } else {
                    listOf(Color(0xFFF7E8DA), Color(0xFFF2D4C2), Color(0xFFE9C1AE))
                },
            ),
        )
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFFFB347).copy(alpha = 0.45f), Color.Transparent),
                Offset(
                    size.width * 0.8f,
                    size.height * 0.15f,
                ),
                size.width * 0.7f,
            ),
            size.width * 0.7f,
            Offset(size.width * 0.8f, size.height * 0.15f),
        )
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFF8A6BFF).copy(alpha = 0.3f), Color.Transparent),
                Offset(
                    size.width * 0.1f,
                    size.height * 0.75f,
                ),
                size.width * 0.8f,
            ),
            size.width * 0.8f,
            Offset(size.width * 0.1f, size.height * 0.75f),
        )
    }
}

@Composable
private fun HomeDevices(on: SnapshotStateMap<Int, Int>, level: SnapshotStateMap<Int, Int>, onBack: () -> Unit, onOpen: (Int) -> Unit) {
    var room by rememberSaveable { mutableIntStateOf(0) }
    var scene by rememberSaveable { mutableIntStateOf(-1) }
    val shown = DEVICES.filter { room == 0 || it.room == room }
    KineticaScaffold(
        Modifier.fillMaxSize(),
        systemBarIcons = false,
        background = { RoomGlow() },
        topBar = {
            KTopAppBar("우리 집", onBack = onBack, subtitle = "실내 23℃ · 습도 45%", actions = {
                KIconButton(KIcons.Plus, "기기 추가", {}, variant = KVariant.Text, tone = KTone.Neutral)
            })
        },
    ) { padding ->
        LazyVerticalGrid(
            GridCells.Adaptive(156.dp),
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = appPadding(16.dp, 960.dp),
                end = appPadding(16.dp, 960.dp),
                top = padding.calculateTopPadding() + 8.dp,
                bottom = navigationBarBottom() + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { EnergyCard(on) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Label("장면", 16, FontWeight.Bold, Modifier.padding(start = 4.dp, top = 4.dp))
                    KToggleGroup(
                        SCENES.map { KGroupItem(it.name, it.icon) },
                        if (scene >= 0) setOf(scene) else emptySet(),
                        { picked ->
                            scene = picked.firstOrNull() ?: -1
                            SCENES.getOrNull(scene)?.let { s -> DEVICES.forEach { d -> on[d.id] = if (d.id in s.turnsOn) 1 else 0 } }
                        },
                        Modifier.fillMaxWidth(),
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                KSegmentedControl(ROOMS, room, { room = it }, Modifier.fillMaxWidth().padding(top = 6.dp))
            }
            items(shown, key = { it.id }) { device ->
                DeviceTile(device, on[device.id] == 1, level[device.id] ?: 0, { on[device.id] = if (it) 1 else 0 }) { onOpen(device.id) }
            }
        }
    }
}

@Composable
private fun EnergyCard(on: SnapshotStateMap<Int, Int>) {
    val active = on.values.count { it == 1 }
    KSurface(Modifier.fillMaxWidth(), shape = kitShape { card }) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label("켜진 기기 ${active}개", 15, FontWeight.SemiBold)
                Label(
                    "오늘 전력 ${"%.1f".format(3.2f + active * 0.4f)}kWh · 어제보다 12% 적어요",
                    13,
                    color = LocalKineticaContentColor.current.copy(alpha = 0.7f),
                )
            }
            Sparkline(listOf(3f, 4f, 3.5f, 5f, 4.2f, 3.8f, 3.2f + active * 0.4f), Modifier.size(90.dp, 36.dp), color = Color(0xFFFFB347))
        }
    }
}

@Composable
private fun DeviceTile(device: Device, isOn: Boolean, level: Int, onToggle: (Boolean) -> Unit, onOpen: () -> Unit) {
    val accent = device.color
    val ink = LocalKineticaContentColor.current
    val iconBg by animateColorAsState(if (isOn) accent else ink.copy(alpha = 0.12f), label = "tile")
    KSurface(onOpen, Modifier.fillMaxWidth().jellyPress(0.04f), shape = kitShape { card }, onClickLabel = "${device.name} 자세히") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(iconBg, CircleShape), Alignment.Center) {
                    ShowcaseIcon(device.icon, 22.dp, if (isOn) Color(0xFF1A1208) else ink)
                }
                Box(Modifier.weight(1f))
                KSwitch(isOn, onToggle)
            }
            Column {
                Label(device.name, 15, FontWeight.SemiBold)
                Label(
                    when {
                        !isOn -> "꺼짐"
                        device.kind == Kind.Climate -> "$level℃로 맞춤"
                        device.kind == Kind.Light -> "켜짐 · $level%"
                        else -> "켜짐"
                    },
                    13,
                    color = ink.copy(alpha = 0.65f),
                )
            }
        }
    }
}

@Composable
private fun LightDetail(device: Device, on: SnapshotStateMap<Int, Int>, level: SnapshotStateMap<Int, Int>, onBack: () -> Unit) {
    var warmth by rememberSaveable { mutableIntStateOf(1) }
    val isOn = on[device.id] == 1
    val brightness = level[device.id] ?: 0
    val glow by animateFloatAsState(if (isOn) brightness / 100f else 0f, label = "glow")
    val tint = WARMTH[warmth].second
    DetailScaffold(device.name, onBack) { modifier ->
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.size(220.dp), Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.85f * glow), Color.Transparent)), size.minDimension / 2)
                }
                Box(Modifier.size(96.dp).background(if (isOn) tint else Color.Gray.copy(alpha = 0.4f), CircleShape), Alignment.Center) {
                    ShowcaseIcon(AppIcons.Bulb, 44.dp, Color(0xFF1A1208))
                }
            }
            Label(if (isOn) "$brightness%" else "꺼짐", 34, FontWeight.Bold)
            KSurface(Modifier.fillMaxWidth(), shape = kitShape { card }) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label("전원", 16, FontWeight.SemiBold, Modifier.weight(1f))
                        KSwitch(isOn, { on[device.id] = if (it) 1 else 0 })
                    }
                    Label("밝기", 14, color = LocalKineticaContentColor.current.copy(alpha = 0.7f))
                    KSlider(brightness.toFloat(), {
                        level[device.id] = it.roundToInt()
                        on[device.id] = 1
                    }, valueRange = 0f..100f, accent = tint)
                    Label("빛 색", 14, color = LocalKineticaContentColor.current.copy(alpha = 0.7f))
                    KSegmentedControl(WARMTH.map { it.first }, warmth, { warmth = it }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ClimateDetail(device: Device, on: SnapshotStateMap<Int, Int>, level: SnapshotStateMap<Int, Int>, onBack: () -> Unit) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var fan by rememberSaveable { mutableIntStateOf(2) }
    val target = level[device.id] ?: 24
    DetailScaffold(device.name, onBack) { modifier ->
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            KGauge(
                target.toFloat(),
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                range = 16f..30f,
                label = if (on[device.id] == 1) "희망 온도" else "꺼짐",
                segments = listOf(
                    KGaugeSegment(21f, Color(0xFF5AC8FA), "시원"),
                    KGaugeSegment(25f, Color(0xFF34C759), "알맞음"),
                    KGaugeSegment(30f, Color(0xFFFF9F0A), "따뜻"),
                ),
                valueFormatter = { "${it.roundToInt()}℃" },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                KIconButton(KIcons.ArrowDown, "1도 내리기", {
                    level[device.id] = (target - 1).coerceAtLeast(16)
                }, size = 56.dp, variant = KVariant.Tonal)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Label("지금 실내", 12, color = LocalKineticaContentColor.current.copy(alpha = 0.65f))
                    Label("26℃", 22, FontWeight.Bold)
                }
                KIconButton(KIcons.ArrowUp, "1도 올리기", {
                    level[device.id] = (target + 1).coerceAtMost(30)
                }, size = 56.dp, variant = KVariant.Tonal)
            }
            KSurface(Modifier.fillMaxWidth(), shape = kitShape { card }) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label("전원", 16, FontWeight.SemiBold, Modifier.weight(1f))
                        KSwitch(on[device.id] == 1, { on[device.id] = if (it) 1 else 0 })
                    }
                    KToggleGroup(
                        listOf(KGroupItem("냉방", AppIcons.Fan), KGroupItem("난방", KIcons.Sun), KGroupItem("자동", AppIcons.Thermo)),
                        setOf(mode),
                        { mode = it.firstOrNull() ?: mode },
                        Modifier.fillMaxWidth(),
                    )
                    Label("바람 세기 ${listOf("미풍", "약", "중", "강")[fan]}", 14, color = LocalKineticaContentColor.current.copy(alpha = 0.7f))
                    KSlider(fan.toFloat(), { fan = it.roundToInt() }, valueRange = 0f..3f, steps = 2)
                }
            }
        }
    }
}

@Composable
private fun DetailScaffold(title: String, onBack: () -> Unit, content: @Composable (Modifier) -> Unit) {
    KineticaScaffold(
        Modifier.fillMaxSize(),
        systemBarIcons = false,
        background = { RoomGlow() },
        topBar = { KTopAppBar(title, onBack = onBack) },
    ) { padding ->
        content(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(
                start = appPadding(20.dp, 520.dp),
                end = appPadding(20.dp, 520.dp),
                top = padding.calculateTopPadding() + 12.dp,
                bottom = navigationBarBottom() + 32.dp,
            ),
        )
    }
}

private enum class Kind { Light, Climate, Plug }

private class Device(
    val id: Int,
    val name: String,
    val room: Int,
    val kind: Kind,
    val icon: ImageVector,
    val color: Color,
    val startsOn: Boolean,
    val level: Int,
)

private class Scene(val name: String, val icon: ImageVector, val turnsOn: Set<Int>)

private val ROOMS = listOf("전체", "거실", "침실", "주방")
private val DEVICES = listOf(
    Device(1, "거실 천장등", 1, Kind.Light, AppIcons.Bulb, Color(0xFFFFC56B), true, 80),
    Device(2, "스탠드", 1, Kind.Light, AppIcons.Bulb, Color(0xFFFFC56B), false, 40),
    Device(3, "에어컨", 1, Kind.Climate, AppIcons.Thermo, Color(0xFF7FD3FF), true, 24),
    Device(4, "TV", 1, Kind.Plug, AppIcons.Tv, Color(0xFFB59CFF), false, 0),
    Device(5, "침실 조명", 2, Kind.Light, AppIcons.Bulb, Color(0xFFFFC56B), false, 30),
    Device(6, "보일러", 2, Kind.Climate, AppIcons.Thermo, Color(0xFFFF9F6B), false, 22),
    Device(7, "커피 머신", 3, Kind.Plug, AppIcons.Plug, Color(0xFF9BE58B), false, 0),
    Device(8, "주방 조명", 3, Kind.Light, AppIcons.Bulb, Color(0xFFFFC56B), true, 100),
    Device(9, "현관 도어록", 0, Kind.Plug, KIcons.Lock, Color(0xFF9BE58B), true, 0),
)
private val SCENES = listOf(
    Scene("아침", KIcons.Sun, setOf(1, 7, 8, 9)),
    Scene("영화", AppIcons.Tv, setOf(2, 3, 4, 9)),
    Scene("잘 자요", KIcons.Moon, setOf(6, 9)),
    Scene("외출", KIcons.Lock, setOf(9)),
)
private val WARMTH = listOf("따뜻하게" to Color(0xFFFFB45C), "자연광" to Color(0xFFFFE7B8), "차갑게" to Color(0xFFCFE6FF))

internal val SMART_HOME_LOOK = AppLook(
    SurfaceStyle.Glass(GlassStyles.Frosted),
    SurfaceStyle.Glass(GlassStyles.Obsidian),
    defaultDark = true,
    accent = Color(0xFFE08A1E),
    accentDark = Color(0xFFFFB347),
    onAccentDark = Color(0xFF1A1208),
)
