// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KAvatar
import com.naury.kinetica.ui.KAvatarSize
import com.naury.kinetica.ui.KBarChart
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KDialog
import com.naury.kinetica.ui.KDisclosureRow
import com.naury.kinetica.ui.KLargeTitleBar
import com.naury.kinetica.ui.KListItem
import com.naury.kinetica.ui.KListLeading
import com.naury.kinetica.ui.KListSection
import com.naury.kinetica.ui.KListTrailing
import com.naury.kinetica.ui.KLoadingIndicator
import com.naury.kinetica.ui.KProgressBar
import com.naury.kinetica.ui.KRadioRow
import com.naury.kinetica.ui.KSearchBar
import com.naury.kinetica.ui.KSlider
import com.naury.kinetica.ui.KSurface
import com.naury.kinetica.ui.KSwitchRow
import com.naury.kinetica.ui.KTextField
import com.naury.kinetica.ui.KValueRow
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.PushNavigation
import com.naury.kinetica.ui.SurfaceStyles
import com.naury.kinetica.ui.rememberKLargeTitleState
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * 설정 앱: 큰 제목 목록에서 Wi-Fi, 디스플레이, 배터리 하위 화면으로 들어간다. One UI 단색 표면이라 연회색 바탕 위 흰 묶음 목록으로 보인다.
 * 스위치와 라디오, 슬라이더 값은 화면을 오가도 남고, Wi-Fi는 암호를 넣으면 연결 중 표시 뒤에 연결됨으로 바뀐다.
 */
@Composable
internal fun SettingsApp(onBack: () -> Unit) {
    var stack by rememberSaveable { mutableStateOf(listOf("root")) }
    val state = rememberIntMap(WIFI to 1, BT to 0, DND to 0, AUTO to 1, THEME to 2, NETWORK to 0, SAVER to 0)
    var brightness by rememberSaveable { mutableFloatStateOf(0.6f) }
    var fontScale by rememberSaveable { mutableFloatStateOf(1f) }
    val pop = { stack = stack.dropLast(1) }
    val open = { screen: String -> stack = stack + screen }
    AppTheme(SETTINGS_LOOK) {
        PushNavigation(stack, onBack = pop) { screen ->
            when (screen) {
                "root" -> SubPage("설정", onBack) { rootPage(state, open) }

                "wifi" -> SubPage("Wi-Fi", pop) { wifiPage(state) }

                "display" -> SubPage("디스플레이", pop) {
                    displayPage(state, brightness, { brightness = it }, fontScale) { fontScale = it }
                }

                else -> SubPage("배터리", pop) { batteryPage(state) }
            }
        }
    }
}

private fun LazyListScope.rootPage(state: SnapshotStateMap<Int, Int>, open: (String) -> Unit) {
    item("search") {
        var query by rememberSaveable { mutableStateOf("") }
        KSearchBar(query, { query = it }, {}, placeholder = "설정 검색")
    }
    item("account") {
        KSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
            KListItem(
                "김하늘",
                supporting = "계정, 백업, 연결된 기기",
                leading = KListLeading.Custom { KAvatar(KAvatar("김하늘", Color(0xFF3E91FF)), size = KAvatarSize.Large) },
                trailing = KListTrailing.Disclosure(),
                onClick = {},
            )
        }
    }
    item("network") { ConnectionSection(state, open) }
    item("device") { DeviceSection(state, open) }
    item("privacy") {
        KListSection(header = "개인정보", footer = "앱이 위치나 카메라를 쓰면 상태 표시줄에 점이 보입니다.") {
            item { KDisclosureRow("보안 및 개인정보 보호", {}, leading = icon(AppIcons.Shield, Color(0xFF5E5CE6))) }
            item { KDisclosureRow("언어", {}, value = "한국어", leading = icon(AppIcons.Globe, Color(0xFF8E8E93))) }
            item { KValueRow("소프트웨어 버전", "1.0.0-alpha01", leading = icon(AppIcons.Info, Color(0xFF8E8E93))) }
        }
    }
}

@Composable
private fun ConnectionSection(state: SnapshotStateMap<Int, Int>, open: (String) -> Unit) {
    KListSection(header = "연결") {
        item {
            KListItem(
                "Wi-Fi",
                supporting = if (state[WIFI] == 1) NETWORKS[state[NETWORK] ?: 0] else "꺼짐",
                leading = icon(AppIcons.Wifi, Color(0xFF3E91FF)),
                trailing = KListTrailing.Disclosure(),
                onClick = { open("wifi") },
            )
        }
        item { KSwitchRow("블루투스", state[BT] == 1, { state[BT] = if (it) 1 else 0 }, leading = icon(AppIcons.Bluetooth, Color(0xFF3E91FF))) }
    }
}

@Composable
private fun DeviceSection(state: SnapshotStateMap<Int, Int>, open: (String) -> Unit) {
    KListSection(header = "기기") {
        item {
            KDisclosureRow(
                "디스플레이",
                { open("display") },
                value = listOf("밝게", "어둡게", "시스템")[state[THEME] ?: 2],
                leading = icon(AppIcons.Display, Color(0xFF34C759)),
            )
        }
        item {
            KDisclosureRow(
                "배터리",
                { open("battery") },
                value = if (state[SAVER] == 1) "절전 모드" else "78%",
                leading = icon(AppIcons.Battery, Color(0xFF30B94D)),
            )
        }
        item {
            KSwitchRow(
                "방해 금지",
                state[DND] == 1,
                { state[DND] = if (it) 1 else 0 },
                subtitle = "밤 11시부터 아침 7시까지",
                leading = icon(AppIcons.Moon, Color(0xFF7D5CFF)),
            )
        }
    }
}

private fun icon(vector: androidx.compose.ui.graphics.vector.ImageVector, color: Color) =
    KListLeading.Icon(vector, tint = Color.White, container = color)

@Composable
private fun SubPage(title: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    val list = rememberLazyListState()
    val bar = rememberKLargeTitleState(list)
    KineticaScaffold(
        Modifier.fillMaxSize(),
        systemBarIcons = false,
        topBar = { KLargeTitleBar(bar, title, onBack = onBack, contentMaxWidth = 680.dp) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().nestedScroll(bar.connection),
            state = list,
            contentPadding = PaddingValues(
                start = appPadding(16.dp, 680.dp),
                end = appPadding(16.dp, 680.dp),
                top = padding.calculateTopPadding() + 8.dp,
                bottom = navigationBarBottom() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

private fun LazyListScope.wifiPage(state: SnapshotStateMap<Int, Int>) {
    item("switch") {
        KSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
            KSwitchRow("Wi-Fi", state[WIFI] == 1, { state[WIFI] = if (it) 1 else 0 })
        }
    }
    item("networks") { WifiNetworks(state) }
}

@Composable
private fun WifiNetworks(state: SnapshotStateMap<Int, Int>) {
    var asking by rememberSaveable { mutableIntStateOf(-1) }
    var joining by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(joining) {
        if (joining >= 0) {
            delay(1_500)
            state[NETWORK] = joining
            joining = -1
        }
    }
    if (state[WIFI] != 1) {
        Label(
            "Wi-Fi를 켜면 주변 네트워크가 보입니다.",
            14,
            modifier = Modifier.padding(start = 8.dp),
            color = LocalKineticaContentColor.current.copy(alpha = 0.6f),
        )
        return
    }
    KListSection(header = "네트워크") {
        NETWORKS.forEachIndexed { index, name ->
            item(index) {
                KListItem(
                    name,
                    supporting = when {
                        joining == index -> "연결하는 중…"
                        state[NETWORK] == index -> "연결됨"
                        index == 3 -> "공개 네트워크"
                        else -> "보안"
                    },
                    leading = KListLeading.Icon(AppIcons.Wifi),
                    trailing = networkTrailing(joining == index, state[NETWORK] == index, open = index == 3),
                    onClick = {
                        if (state[NETWORK] != index) {
                            if (index == 3) joining = index else asking = index
                        }
                    },
                )
            }
        }
    }
    PasswordDialog(asking, { asking = -1 }) {
        joining = asking
        asking = -1
    }
}

/** 연결 중이면 로딩 표시, 연결됐으면 체크, 보안 네트워크면 자물쇠, 공개 네트워크면 아무것도 두지 않는다. */
private fun networkTrailing(joining: Boolean, connected: Boolean, open: Boolean): KListTrailing? = when {
    joining -> KListTrailing.Custom { KLoadingIndicator() }
    connected -> KListTrailing.Custom { ShowcaseIcon(AppIcons.Check, 20.dp, KineticaTheme.colors.accent) }
    open -> null
    else -> KListTrailing.Custom { ShowcaseIcon(AppIcons.Lock, 18.dp, LocalKineticaContentColor.current.copy(alpha = 0.5f)) }
}

/** 보안 네트워크 [index]의 암호를 묻는 다이얼로그. -1이면 닫혀 있다. */
@Composable
private fun PasswordDialog(index: Int, onDismiss: () -> Unit, onJoin: () -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    KDialog(index >= 0, onDismiss, title = NETWORKS.getOrNull(index)) {
        Label(NETWORKS.getOrNull(index) ?: "", 20, FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        KTextField(password, { password = it }, "암호", visualTransformation = PasswordVisualTransformation(), supportingText = "8자 이상")
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KButton("취소", onDismiss, Modifier.weight(1f), variant = KVariant.Tonal)
            KButton("연결", {
                password = ""
                onJoin()
            }, Modifier.weight(1f), enabled = password.length >= 8)
        }
    }
}

private fun LazyListScope.displayPage(
    state: SnapshotStateMap<Int, Int>,
    brightness: Float,
    onBrightness: (Float) -> Unit,
    fontScale: Float,
    onFontScale: (Float) -> Unit,
) {
    item("theme") {
        KListSection(header = "모드") {
            listOf("밝게", "어둡게", "시스템 설정 따르기").forEachIndexed { i, label ->
                item(i) { KRadioRow(label, state[THEME] == i, { state[THEME] = i }) }
            }
        }
    }
    item("brightness") {
        KListSection(header = "밝기") {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShowcaseIcon(AppIcons.Sun, 18.dp)
                        KSlider(brightness, onBrightness, Modifier.weight(1f).padding(horizontal = 12.dp))
                        ShowcaseIcon(AppIcons.Sun, 24.dp)
                    }
                }
            }
            item { KSwitchRow("자동 밝기", state[AUTO] == 1, { state[AUTO] = if (it) 1 else 0 }, subtitle = "주변 빛에 맞춰 밝기를 바꿉니다") }
        }
    }
    item("font") {
        KListSection(header = "글자 크기", footer = "이 화면의 미리보기만 바뀝니다. 앱 글자 크기는 시스템 설정을 따릅니다.") {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Label("가나다라 Aa 123", (16 * fontScale).roundToInt(), FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShowcaseIcon(AppIcons.TextSize, 16.dp)
                        KSlider(fontScale, onFontScale, Modifier.weight(1f).padding(horizontal = 12.dp), valueRange = 0.8f..1.6f, steps = 3)
                        ShowcaseIcon(AppIcons.TextSize, 24.dp)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.batteryPage(state: SnapshotStateMap<Int, Int>) {
    item("level") {
        KSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Label("78%", 34, FontWeight.Bold)
                    Label(
                        if (state[SAVER] ==
                            1
                        ) {
                            "약 1일 4시간 남음"
                        } else {
                            "약 19시간 남음"
                        },
                        14,
                        modifier = Modifier.padding(start = 10.dp, bottom = 6.dp),
                    )
                }
                KProgressBar(0.78f, Modifier.fillMaxWidth(), color = Color(0xFF30B94D), thickness = 12.dp)
            }
        }
    }
    item("saver") {
        KSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
            KSwitchRow("절전 모드", state[SAVER] == 1, { state[SAVER] = if (it) 1 else 0 }, subtitle = "백그라운드 작업과 화면 주사율을 줄입니다")
        }
    }
    item("usage") {
        KSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label("지난 7일 사용 시간", 15, FontWeight.SemiBold)
                KBarChart(
                    listOf(4.2f, 5.1f, 3.8f, 6.4f, 5.5f, 7.2f, 3.1f),
                    Modifier.fillMaxWidth().height(140.dp),
                    labels = listOf("월", "화", "수", "목", "금", "토", "일"),
                    valueFormatter = { "${it}시간" },
                )
            }
        }
    }
}

private const val WIFI = 1
private const val BT = 2
private const val DND = 3
private const val AUTO = 4
private const val THEME = 5
private const val NETWORK = 6
private const val SAVER = 7
private val NETWORKS = listOf("우리집_5G", "우리집_2.4G", "302호", "카페 손님용", "하늘통신 Free")

internal val SETTINGS_LOOK = AppLook(
    SurfaceStyles.OneUi,
    SurfaceStyles.OneUiDark,
    defaultDark = false,
    accent = Color(0xFF3E91FF),
)
