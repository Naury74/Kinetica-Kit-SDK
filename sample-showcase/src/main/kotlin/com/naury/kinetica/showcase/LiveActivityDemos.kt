// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naury.kinetica.ui.KChatInput
import com.naury.kinetica.ui.KCountdownPill
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KIslandDefaults
import com.naury.kinetica.ui.KLiveActivity
import com.naury.kinetica.ui.KLiveActivityHost
import com.naury.kinetica.ui.KLiveActivityPosition
import com.naury.kinetica.ui.KLiveActivityState
import com.naury.kinetica.ui.KLiveWaveform
import com.naury.kinetica.ui.KPillProgress
import com.naury.kinetica.ui.KSegmentedControl
import com.naury.kinetica.ui.KSurface
import com.naury.kinetica.ui.KTimePill
import com.naury.kinetica.ui.KVoiceRecorderButton
import com.naury.kinetica.ui.KVoiceRecordingBar
import com.naury.kinetica.ui.KWaveform
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.PlayPauseIcon
import com.naury.kinetica.ui.countdownText
import com.naury.kinetica.ui.rememberKLiveActivityState
import com.naury.kinetica.ui.rememberKVoiceRecorderState
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 오래 떠 있는 활동, 시간 캡슐, 음성 녹음 버튼, 파형 데모 카드들. */
internal fun LazyListScope.liveActivityDemos() {
    item { LiveActivityCard() }
    item { TimePillsCard() }
    item { VoiceRecorderCard() }
    item { WaveformCard() }
}

@Composable
private fun LiveActivityCard() {
    val live = rememberKLiveActivityState()
    var position by rememberSaveable { mutableIntStateOf(1) }
    val delivery = remember { Animatable(0.15f) }
    val delivering = live.activities.any { it.id == "delivery" }
    LaunchedEffect(delivering) {
        if (!delivering) return@LaunchedEffect
        delivery.snapTo(0.15f)
        delivery.animateTo(0.9f, tween(60_000, easing = LinearEasing))
    }
    DemoCard(
        "라이브 액티비티",
        "배달, 타이머, 녹음처럼 오래 이어지는 활동을 캡슐로 띄웁니다. 둘을 켜면 뒤 활동이 원으로 떨어져 나오고, 누르거나 길게 누르면 큰 화면으로 부풉니다. " +
            "펼친 화면은 다시 누르거나 가장자리 쪽으로 밀면 접힙니다.",
    ) {
        KSegmentedControl(listOf("화면 위", "화면 아래"), position, { position = it }, Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleActivity(live, "delivery", "배달") { deliveryActivity(live, delivery) }
            ToggleActivity(live, "timer", "타이머") { timerActivity(live) }
            ToggleActivity(live, "rec", "녹음") { recordingActivity(live) }
        }
        Label("켠 활동: ${live.activities.size}개", 13, color = LocalKineticaContentColor.current.copy(alpha = 0.7f))
        KLiveActivityHost(
            live,
            position = if (position == 0) KLiveActivityPosition.Top else KLiveActivityPosition.Bottom,
            edgePadding = if (position == 0) Dp.Unspecified else 96.dp,
        )
    }
}

@Composable
private fun ToggleActivity(live: KLiveActivityState, id: String, name: String, make: () -> KLiveActivity) {
    val on = live.activities.any { it.id == id }
    Pill(if (on) "$name 끝내기" else "$name 시작", filled = on) { if (on) live.end(id) else live.start(make()) }
}

private fun deliveryActivity(live: KLiveActivityState, progress: Animatable<Float, *>): KLiveActivity = KLiveActivity(
    "delivery",
    "배달",
    "도착까지 12분",
    leading = { ShowcaseIcon(KIcons.Bag, 20.dp, Color(0xFFFFB340)) },
    trailing = { Label("12분", 14, FontWeight.SemiBold) },
    minimal = { ShowcaseIcon(KIcons.Bag, 18.dp, Color(0xFFFFB340)) },
    progress = { progress.value },
    accent = Color(0xFFFFB340),
    expanded = { DeliveryPanel(onDone = { live.end("delivery") }) },
)

@Composable
private fun DeliveryPanel(onDone: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShowcaseIcon(KIcons.Bag, 28.dp, Color(0xFFFFB340))
            Column(Modifier.weight(1f)) {
                Label("배달 중", 13, color = LocalKineticaContentColor.current.copy(alpha = 0.65f))
                Label("도착까지 12분", 20, FontWeight.Bold)
            }
        }
        Label("라이더가 가게에서 출발했어요. 문 앞에 두고 벨을 눌러 드릴게요.", 14)
        Pill("받았어요", filled = true, onClick = onDone)
    }
}

private fun timerActivity(live: KLiveActivityState): KLiveActivity {
    val total = 5 * 60_000L
    val ends = System.currentTimeMillis() + total
    return KLiveActivity(
        "timer",
        "타이머",
        "5분 타이머",
        leading = { ShowcaseIcon(KIcons.Clock, 20.dp, Color(0xFFFF9F0A)) },
        trailing = { KCountdownPill(ends, total, accent = Color(0xFFFF9F0A), onFinished = { live.end("timer") }) },
        minimal = { KCountdownPill(ends, total, progress = KPillProgress.None, textStyle = SMALL_TIME) },
        expanded = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Label("타이머", 13, color = LocalKineticaContentColor.current.copy(alpha = 0.65f))
                KCountdownPill(ends, total, progress = KPillProgress.Bar, accent = Color(0xFFFF9F0A), textStyle = BIG_TIME, label = "타이머")
                Pill("멈추기", filled = true) { live.end("timer") }
            }
        },
    )
}

private fun recordingActivity(live: KLiveActivityState): KLiveActivity {
    val started = System.currentTimeMillis()
    return KLiveActivity(
        "rec",
        "음성 녹음",
        "녹음 중",
        leading = { ShowcaseIcon(KIcons.Mic, 18.dp, Color(0xFFFF453A)) },
        trailing = { KTimePill(started, dotColor = Color(0xFFFF453A)) },
        minimal = { ShowcaseIcon(KIcons.Mic, 18.dp, Color(0xFFFF453A)) },
        expanded = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Label("음성 녹음", 16, FontWeight.SemiBold, Modifier.weight(1f))
                    KTimePill(started, label = "녹음 시간", dotColor = Color(0xFFFF453A))
                }
                val level = rememberFakeLevel(active = true)
                KLiveWaveform({ level.floatValue }, Modifier.fillMaxWidth().height(36.dp), color = Color(0xFFFF453A))
                Pill("녹음 끝내기", filled = true) { live.end("rec") }
            }
        },
    )
}

@Composable
private fun TimePillsCard() {
    var round by remember { mutableIntStateOf(0) }
    val ends = remember(round) { System.currentTimeMillis() + 45_000L }
    val started = remember(round) { System.currentTimeMillis() }
    DemoCard("시간 캡슐", "초가 바뀔 때 숫자가 굴러가고, 링과 막대가 남은 몫만큼 부드럽게 줄어듭니다. 라이브 액티비티 슬롯에 그대로 넣을 수 있습니다.") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            KCountdownPill(ends, 45_000L, label = "남은 시간", surface = KineticaTheme.surface)
            KCountdownPill(ends, 45_000L, label = "남은 시간", progress = KPillProgress.Bar, surface = KineticaTheme.surface)
            KTimePill(started, label = "통화 시간", dotColor = Color(0xFF30D158), surface = KineticaTheme.surface)
            KTimePill(started, label = "녹음 시간", dotColor = Color(0xFFFF453A), surface = KIslandDefaults.Black)
        }
        Pill("처음부터") { round++ }
    }
}

@Composable
private fun VoiceRecorderCard() {
    val recorder = rememberKVoiceRecorderState()
    var draft by rememberSaveable { mutableStateOf("") }
    val sent = remember { mutableStateListOf<VoiceClip>() }
    val level = rememberFakeLevel(recorder.isActive)
    var note by remember { mutableStateOf("마이크를 길게 눌러 보세요.") }
    DemoCard(
        "음성 녹음 버튼",
        "길게 눌러 녹음하고, 떼면 보냅니다. 누른 채 위로 밀면 잠겨서 손을 떼도 이어지고, 시작 쪽으로 밀면 취소됩니다. " +
            "채팅 입력줄의 voiceButton 자리에 넣었습니다.",
    ) {
        sent.forEach { VoiceClipRow(it) }
        Label(note, 13, color = LocalKineticaContentColor.current.copy(alpha = 0.7f))
        // 레일이 버튼 위로 나오므로 입력줄 위에 자리를 둔다.
        Box(Modifier.padding(top = 72.dp)) {
            KChatInput(
                draft,
                { draft = it },
                onSend = { draft = "" },
                voiceButton = {
                    KVoiceRecorderButton(
                        onStart = { note = "녹음 중" },
                        onSend = { ms ->
                            sent += VoiceClip(sent.size, ms.coerceAtLeast(1_000L))
                            note = "${countdownText(ms)} 길이로 보냈어요."
                        },
                        onLock = { note = "잠겼어요. 손을 떼도 이어서 녹음합니다." },
                        onCancel = { note = "녹음을 취소했어요." },
                        level = { level.floatValue },
                        state = recorder,
                    )
                },
            )
            if (recorder.isActive) KVoiceRecordingBar(recorder, { level.floatValue }, Modifier.matchParentSize().padding(end = 52.dp))
        }
    }
}

/** 보낸 음성 메시지 하나. [samples]는 보기용 가짜 파형이다. */
private class VoiceClip(val id: Int, val durationMs: Long) {
    val samples: FloatArray = Random(id).let { r -> FloatArray(80) { 0.2f + r.nextFloat() * 0.8f } }
}

@Composable
private fun VoiceClipRow(clip: VoiceClip) {
    val progress = remember { Animatable(0f) }
    var playing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        if (progress.value >= 1f) progress.snapTo(0f)
        val left = ((1f - progress.value) * clip.durationMs).toInt()
        progress.animateTo(1f, tween(left, easing = LinearEasing))
        playing = false
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PlayButton(playing) { playing = !playing }
        KWaveform(
            clip.samples,
            { progress.value },
            Modifier.weight(1f).height(32.dp),
            onSeek = { target -> scope.launch { progress.snapTo(target) } },
            label = "음성 메시지",
            durationMillis = clip.durationMs,
        )
        Label(countdownText(clip.durationMs), 13)
    }
}

@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    KSurface(onClick, Modifier.size(40.dp).semantics { contentDescription = if (playing) "일시정지" else "재생" }, shape = CircleShape) {
        Box(Modifier.align(Alignment.Center)) { PlayPauseIcon(playing, Modifier.size(20.dp)) }
    }
}

@Composable
private fun WaveformCard() {
    var live by remember { mutableStateOf(true) }
    val level = rememberFakeLevel(live)
    val clip = remember { VoiceClip(7, 23_000L) }
    DemoCard("파형", "위는 녹음 중 소리 크기를 흘려 보이는 막대이고, 아래는 재생 위치 파형입니다. 아래 파형은 누르거나 끌어서 위치를 옮깁니다.") {
        KLiveWaveform({ level.floatValue }, Modifier.fillMaxWidth().height(40.dp), active = live)
        Pill(if (live) "멈추기" else "다시 듣기") { live = !live }
        VoiceClipRow(clip)
    }
}

/** 녹음 대신 쓰는 가짜 소리 크기. 말하듯 오르내린다. 값은 그리기 단계에서만 읽는다. */
@Composable
private fun rememberFakeLevel(active: Boolean): MutableFloatState {
    val level = remember { mutableFloatStateOf(0f) }
    val tick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(active) {
        while (active) {
            tick.longValue++
            val t = tick.longValue * 0.05f
            level.floatValue = (0.15f + 0.75f * abs(sin(t * 2.3f)) * (0.5f + Random.nextFloat() * 0.5f)).coerceIn(0f, 1f)
            delay(50)
        }
        level.floatValue = 0f
    }
    return level
}

private val SMALL_TIME = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
private val BIG_TIME = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
