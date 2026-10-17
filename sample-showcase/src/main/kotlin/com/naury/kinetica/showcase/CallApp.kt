// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
// 예시 데이터 클래스는 필드를 생성자에 나란히 두는 편이 읽기 쉬워 인자가 많다.
@file:Suppress("MagicNumber", "LongParameterList")

package com.naury.kinetica.showcase

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.KActionSheet
import com.naury.kinetica.ui.KAvatar
import com.naury.kinetica.ui.KAvatarData
import com.naury.kinetica.ui.KAvatarSize
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KDialogAction
import com.naury.kinetica.ui.KDialogActionStyle
import com.naury.kinetica.ui.KIconButton
import com.naury.kinetica.ui.KIconToggleButton
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KIndexBar
import com.naury.kinetica.ui.KIndexBarDefaults
import com.naury.kinetica.ui.KIslandContent
import com.naury.kinetica.ui.KIslandHost
import com.naury.kinetica.ui.KIslandResult
import com.naury.kinetica.ui.KKeypad
import com.naury.kinetica.ui.KKeypadMode
import com.naury.kinetica.ui.KListItem
import com.naury.kinetica.ui.KListLeading
import com.naury.kinetica.ui.KListSection
import com.naury.kinetica.ui.KListTrailing
import com.naury.kinetica.ui.KSheet
import com.naury.kinetica.ui.KSlideToConfirm
import com.naury.kinetica.ui.KSurface
import com.naury.kinetica.ui.KTab
import com.naury.kinetica.ui.KTabBar
import com.naury.kinetica.ui.KToaster
import com.naury.kinetica.ui.KTone
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.LocalGlassBackdrop
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.LocalScaffoldBackground
import com.naury.kinetica.ui.PushNavigation
import com.naury.kinetica.ui.RollingText
import com.naury.kinetica.ui.SurfaceStyle
import com.naury.kinetica.ui.glowBorder
import com.naury.kinetica.ui.jellyPress
import com.naury.kinetica.ui.rememberKIslandState
import com.naury.kinetica.ui.rememberKToasterState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 전화 앱: 최근 기록, 연락처(색인 막대), 키패드 세 탭과 걸려 오는 전화, 음성 통화, 그룹 영상 통화 화면. 짙은 그라디언트 위 Liquid 글래스다.
 * 음성 통화 중에 '작게'를 누르면 목록으로 돌아가고 통화 시간이 아일랜드에 남는다. 영상은 카메라 대신 움직이는 그라디언트로 흉내 낸다.
 */
@Composable
internal fun CallApp(onBack: () -> Unit) {
    var stack by rememberSaveable { mutableStateOf(listOf("main")) }
    // 진행 중인 통화 상대(없으면 -1)와 시작 시각(ms)
    var peer by rememberSaveable { mutableIntStateOf(-1) }
    var startedAt by rememberSaveable { mutableLongStateOf(0L) }
    val island = rememberKIslandState()
    val toaster = rememberKToasterState()
    val pop = { stack = stack.dropLast(1) }
    val startCall = { who: Int, video: Boolean ->
        peer = who
        startedAt = System.currentTimeMillis()
        stack = listOf("main", if (video) "video" else "voice")
    }
    val endCall = {
        peer = -1
        stack = listOf("main")
    }
    val onCallScreen = stack.last() == "voice" || stack.last() == "video"
    // 통화 화면을 벗어나 있는 동안 아일랜드에 통화 시간을 띄우고, 누르면 통화 화면으로 돌아간다.
    // 통화 화면으로 돌아오거나 통화가 끝나면 이 효과가 취소되며 아일랜드도 닫힌다.
    LaunchedEffect(peer, onCallScreen) {
        if (peer < 0 || onCallScreen) return@LaunchedEffect
        val content = {
            KIslandContent(CALL_CONTACTS[peer].name, "통화 중", KIcons.Mic, Color(0xFF34C759), compactText = elapsed(startedAt))
        }
        // show는 알림이 닫힐 때까지 돌아오지 않으므로, 떠 있는 알림의 통화 시간은 따로 update로 바꾼다.
        val ticker = launch {
            while (true) {
                delay(1_000)
                island.update(content())
            }
        }
        val result = island.show(content(), durationMs = null, key = "call", replaceShowing = true)
        ticker.cancel()
        if (result == KIslandResult.Clicked) stack = stack + "voice"
    }
    AppTheme(CALL_LOOK) {
        Box(Modifier.fillMaxSize()) {
            PushNavigation(stack, onBack = pop) { screen ->
                when (screen) {
                    "main" -> CallHome(onBack, startCall) { stack = stack + "incoming" }

                    "incoming" -> IncomingCall(
                        onAnswer = { startCall(0, false) },
                        onDecline = pop,
                        onMessage = { text ->
                            toaster.show("메시지를 보냈어요", description = text)
                            pop()
                        },
                    )

                    "voice" -> VoiceCall(peer.coerceAtLeast(0), startedAt, onMinimize = pop, onVideo = {
                        stack = listOf("main", "video")
                    }, onEnd = endCall)

                    else -> VideoCall(startedAt, island, onEnd = endCall)
                }
            }
            KIslandHost(island)
            KToaster(toaster)
        }
    }
}

private fun elapsed(startedAt: Long): String {
    val seconds = ((System.currentTimeMillis() - startedAt) / 1_000).coerceAtLeast(0)
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}

/** 1초마다 바뀌는 통화 시간 글자. */
@Composable
internal fun rememberCallElapsed(startedAt: Long): String {
    var text by remember { mutableStateOf(elapsed(startedAt)) }
    LaunchedEffect(startedAt) {
        while (true) {
            text = elapsed(startedAt)
            delay(1_000)
        }
    }
    return text
}

@Composable
private fun CallBackdrop() {
    val dark = CALL_LOOK.isDark()
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Brush.linearGradient(if (dark) listOf(Color(0xFF0E1A2B), Color(0xFF1B1035)) else listOf(Color(0xFFDDE8FF), Color(0xFFF1E3FF))),
        )
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFF34C759).copy(alpha = 0.25f), Color.Transparent), Offset(size.width, 0f), size.width),
            size.width,
            Offset(size.width, 0f),
        )
    }
}

@Composable
private fun CallHome(onBack: () -> Unit, onCall: (Int, Boolean) -> Unit, onIncoming: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    KineticaScaffold(
        Modifier.fillMaxSize(),
        systemBarIcons = false,
        background = { CallBackdrop() },
        topBar = {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KIconButton(KIcons.ChevronBack, "뒤로", onBack, variant = KVariant.Text, tone = KTone.Neutral)
                Label(CALL_TABS[tab].label, 24, FontWeight.Bold, Modifier.weight(1f).padding(start = 4.dp))
                Pill("전화 받아 보기", Modifier.padding(end = 8.dp), onClick = onIncoming)
            }
        },
        bottomBar = { KTabBar(CALL_TABS, tab, { tab = it }) },
    ) { padding ->
        when (tab) {
            0 -> Recents(padding, onCall)
            1 -> Contacts(padding, onCall)
            else -> Dialer(padding) { onCall(4, false) }
        }
    }
}

@Composable
private fun Recents(padding: PaddingValues, onCall: (Int, Boolean) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = appPadding(16.dp, 640.dp),
            end = appPadding(16.dp, 640.dp),
            top = padding.calculateTopPadding() + 4.dp,
            bottom = padding.calculateBottomPadding() + 16.dp,
        ),
    ) {
        item {
            KListSection(header = "오늘") {
                RECENTS.forEachIndexed { i, r ->
                    item(i) {
                        KListItem(
                            CALL_CONTACTS[r.who].name + if (r.count > 1) " (${r.count})" else "",
                            supporting = "${if (r.missed) {
                                "부재중 · "
                            } else if (r.outgoing) {
                                "발신 · "
                            } else {
                                ""
                            }}${r.kind} · ${r.time}",
                            leading = KListLeading.Custom { KAvatar(CALL_CONTACTS[r.who].avatar, size = KAvatarSize.Medium) },
                            trailing = KListTrailing.Custom {
                                KIconButton(if (r.video) KIcons.Video else ShowcaseIcons.Phone, "${CALL_CONTACTS[r.who].name}에게 다시 걸기", {
                                    onCall(r.who, r.video)
                                }, variant = KVariant.Text)
                            },
                            onClick = { onCall(r.who, false) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Contacts(padding: PaddingValues, onCall: (Int, Boolean) -> Unit) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val sorted = remember { CALL_CONTACTS.withIndex().sortedBy { it.value.name } }
    val groups = sorted.groupBy { KIndexBarDefaults.keyOf(it.value.name) }
    // 색인 글자마다 목록에서 그 머리글이 놓인 위치
    val starts = remember(groups) {
        var at = 0
        groups.mapValues { (_, rows) -> at.also { at += rows.size + 1 } }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = list,
            contentPadding = PaddingValues(
                start = appPadding(16.dp, 640.dp),
                end = appPadding(16.dp, 640.dp) + 24.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            groups.forEach { (key, rows) ->
                item("h-$key") {
                    Label(
                        key,
                        14,
                        FontWeight.Bold,
                        Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
                        color = KineticaTheme.colors.accent,
                    )
                }
                items(rows, key = { it.index }) { (index, c) ->
                    KListItem(
                        c.name,
                        supporting = c.number,
                        leading = KListLeading.Custom { KAvatar(c.avatar, size = KAvatarSize.Medium) },
                        trailing = KListTrailing.Custom {
                            Row {
                                KIconButton(ShowcaseIcons.Phone, "${c.name}에게 전화", { onCall(index, false) }, variant = KVariant.Text)
                                KIconButton(KIcons.Video, "${c.name}에게 영상 통화", { onCall(index, true) }, variant = KVariant.Text)
                            }
                        },
                    )
                }
            }
        }
        KIndexBar(
            KIndexBarDefaults.Hangul,
            { i ->
                val label = KIndexBarDefaults.Hangul[i]
                val target = starts.entries.lastOrNull { KIndexBarDefaults.Hangul.indexOf(it.key) <= i }?.value ?: 0
                scope.launch { list.scrollToItem(if (label in starts) starts.getValue(label) else target) }
            },
            Modifier.align(
                Alignment.CenterEnd,
            ).padding(end = 4.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
        )
    }
}

@Composable
private fun Dialer(padding: PaddingValues, onCall: () -> Unit) {
    var number by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Label(formatPhone(number).ifEmpty { " " }, 32, FontWeight.SemiBold, Modifier.padding(horizontal = 24.dp))
        Label(
            CALL_CONTACTS.firstOrNull { it.number.filter(Char::isDigit) == number }?.name ?: if (number.length >= 3) "번호 추가" else " ",
            14,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            color = KineticaTheme.colors.accent,
        )
        KKeypad(
            onDigit = { if (number.length < 13) number += it },
            onBackspace = { number = number.dropLast(1) },
            mode = KKeypadMode.Phone,
            onClear = { number = "" },
            maxWidth = 320.dp,
            extraKey = {
                KIconButton(
                    ShowcaseIcons.Phone,
                    "전화 걸기",
                    onCall,
                    Modifier.jellyPress(),
                    variant = KVariant.Filled,
                    size = 72.dp,
                    enabled = number.length >= 3,
                )
            },
        )
    }
}

private fun formatPhone(digits: String): String = when {
    digits.any { !it.isDigit() } || digits.length <= 3 -> digits
    digits.length <= 7 -> "${digits.take(3)}-${digits.drop(3)}"
    else -> "${digits.take(3)}-${digits.substring(3, 7)}-${digits.drop(7)}"
}

@Composable
private fun IncomingCall(onAnswer: () -> Unit, onDecline: () -> Unit, onMessage: (String) -> Unit) {
    var replies by remember { mutableStateOf(false) }
    val pulse = rememberInfiniteTransition(label = "ring")
    val ring by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1_600, easing = LinearEasing)), label = "ring")
    val caller = CALL_CONTACTS[0]
    KineticaScaffold(Modifier.fillMaxSize(), systemBarIcons = false, edgeFade = false, background = {
        CallerBackdrop(caller.avatar.color)
    }) { _ ->
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Label("휴대전화", 15, color = Color.White.copy(alpha = 0.75f))
            Label(caller.name, 34, FontWeight.Bold, Modifier.padding(top = 4.dp), color = Color.White)
            Spacer(Modifier.height(40.dp))
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(200.dp)) {
                    drawCircle(Color.White.copy(alpha = 0.25f * (1 - ring)), size.minDimension / 2 * (0.55f + 0.45f * ring))
                }
                KAvatar(caller.avatar, size = KAvatarSize.ExtraLarge)
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundAction("거절", KIcons.CallEnd, Color(0xFFFF3B30), onDecline)
                RoundAction("메시지", KIcons.Message, Color.White.copy(alpha = 0.22f)) { replies = true }
            }
            Spacer(Modifier.height(24.dp))
            KSlideToConfirm("밀어서 받기", onAnswer, Modifier.widthIn(max = 420.dp).fillMaxWidth())
        }
    }
    KActionSheet(
        replies,
        { replies = false },
        QUICK_REPLIES.map { text -> KDialogAction(text) { onMessage(text) } } + KDialogAction("취소", KDialogActionStyle.Preferred) {},
        title = "메시지로 답하기",
    )
}

@Composable
private fun CallerBackdrop(color: Color) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(color.copy(alpha = 0.9f), Color(0xFF14121C))))
    }
}

@Composable
private fun RoundAction(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        KSurface(onClick, Modifier.size(64.dp).jellyPress(), shape = CircleShape) {
            Box(Modifier.fillMaxSize().background(color, CircleShape), Alignment.Center) { ShowcaseIcon(icon, 28.dp, Color.White) }
        }
        Label(label, 13, modifier = Modifier.padding(top = 6.dp), color = Color.White)
    }
}

@Composable
private fun VoiceCall(who: Int, startedAt: Long, onMinimize: () -> Unit, onVideo: () -> Unit, onEnd: () -> Unit) {
    var muted by rememberSaveable { mutableStateOf(false) }
    var speaker by rememberSaveable { mutableStateOf(false) }
    var held by rememberSaveable { mutableStateOf(false) }
    val time = rememberCallElapsed(startedAt)
    val contact = CALL_CONTACTS[who]
    KineticaScaffold(Modifier.fillMaxSize(), systemBarIcons = false, edgeFade = false, background = {
        CallerBackdrop(contact.avatar.color)
    }) { _ ->
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth()) {
                KIconButton(KIcons.ArrowDown, "작게 보기", onMinimize, variant = KVariant.Glass)
            }
            Spacer(Modifier.height(24.dp))
            KAvatar(contact.avatar, size = KAvatarSize.ExtraLarge)
            Label(contact.name, 30, FontWeight.Bold, Modifier.padding(top = 16.dp), color = Color.White)
            RollingText(
                if (held) "대기 중" else time,
                Modifier.padding(top = 6.dp),
                style = TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 18.sp, fontWeight = FontWeight.Medium),
            )
            Spacer(Modifier.weight(1f))
            KSurface(Modifier.widthIn(max = 420.dp).fillMaxWidth(), shape = RoundedCornerShape(32.dp)) {
                Column(Modifier.padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ToggleAction("음소거", KIcons.MicOff, muted) { muted = it }
                        ToggleAction("키패드", KIcons.Dialpad, false) {}
                        ToggleAction("스피커", KIcons.Speaker, speaker) { speaker = it }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ToggleAction("통화 추가", KIcons.Plus, false) {}
                        ToggleAction("영상", KIcons.Video, false) { onVideo() }
                        ToggleAction("대기", KIcons.Clock, held) { held = it }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            KIconButton(
                KIcons.CallEnd,
                "통화 끊기",
                onEnd,
                Modifier.jellyPress(),
                variant = KVariant.Filled,
                tone = KTone.Destructive,
                size = 72.dp,
            )
        }
    }
}

@Composable
private fun ToggleAction(label: String, icon: ImageVector, checked: Boolean, onChange: (Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 72.dp)) {
        KIconToggleButton(
            checked,
            onChange,
            icon,
            label,
            size = 60.dp,
            variant = if (checked) KVariant.Filled else KVariant.Tonal,
            tone = KTone.Neutral,
        )
        Label(label, 12, modifier = Modifier.padding(top = 6.dp))
    }
}

internal class Contact(val name: String, val number: String, color: Color) {
    val avatar = KAvatarData(name, color)
}

private class Recent(
    val who: Int,
    val time: String,
    val kind: String,
    val missed: Boolean = false,
    val outgoing: Boolean = false,
    val video: Boolean = false,
    val count: Int = 1,
)

internal val CALL_CONTACTS = listOf(
    Contact("이서연", "010-2384-1029", Color(0xFFFF7A7A)),
    Contact("박준호", "010-4921-5530", Color(0xFF6C8CFF)),
    Contact("윤아", "010-7712-0384", Color(0xFFB07CFF)),
    Contact("최유나", "010-3307-8812", Color(0xFF3CCFB2)),
    Contact("정민재", "010-5510-2291", Color(0xFFFFB347)),
    Contact("한지우", "010-2201-4470", Color(0xFF5AC8FA)),
    Contact("강다인", "010-8834-1195", Color(0xFFFF6B9A)),
    Contact("오태오", "010-6620-7731", Color(0xFF8BD17C)),
    Contact("문하은", "010-9013-5562", Color(0xFFE0A96D)),
    Contact("서도윤", "010-1145-3378", Color(0xFF7FA7FF)),
    Contact("김엄마", "010-3456-7890", Color(0xFFFF9F0A)),
    Contact("동네 세탁소", "02-334-5521", Color(0xFF8E8E93)),
)
private val RECENTS = listOf(
    Recent(0, "오후 3:12", "휴대전화", missed = true, count = 2),
    Recent(2, "오후 1:40", "영상 통화", video = true),
    Recent(10, "오전 11:05", "휴대전화", outgoing = true),
    Recent(1, "오전 9:22", "휴대전화"),
    Recent(11, "오전 8:57", "일반 전화", outgoing = true),
    Recent(4, "어제", "휴대전화", missed = true),
    Recent(3, "어제", "영상 통화", video = true, outgoing = true),
)
internal val CALL_ME = KAvatarData("김하늘", Color(0xFF8E7CFF))
private val QUICK_REPLIES = listOf("지금 통화할 수 없어요", "회의 중이에요. 끝나고 전화할게요", "곧 다시 걸게요", "문자로 보내 줄래요?")
private val CALL_TABS = listOf(KTab("최근 기록", KIcons.Clock, badge = 2), KTab("연락처", KIcons.Person), KTab("키패드", KIcons.Dialpad))

internal val CALL_LOOK = AppLook(
    SurfaceStyle.Glass(GlassStyles.Liquid),
    SurfaceStyle.Glass(GlassStyles.LiquidDark),
    defaultDark = true,
    accent = Color(0xFF30C85E),
    accentDark = Color(0xFF34D46A),
    onAccentDark = Color(0xFF002910),
)
