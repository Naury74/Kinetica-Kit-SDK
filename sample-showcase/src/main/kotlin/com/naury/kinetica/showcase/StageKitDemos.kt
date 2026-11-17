package com.naury.kinetica.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naury.kinetica.ui.KButtonSize
import com.naury.kinetica.ui.KIconButton
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KNotification
import com.naury.kinetica.ui.KNotificationStack
import com.naury.kinetica.ui.KShineButton
import com.naury.kinetica.ui.KStretchyHeader
import com.naury.kinetica.ui.KStretchyHeaderBar
import com.naury.kinetica.ui.KTone
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.KWordRotate
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.rememberKStretchyHeaderState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 늘어나는 헤더, 알림 묶음, 단어 회전 문구, 빛나는 버튼 데모 카드들. */
internal fun LazyListScope.stageKitDemos() {
    item { StretchyHeaderCard() }
    item { NotificationStackCard() }
    item { WordRotateCard() }
    item { ShineButtonCard() }
}

private class Spot(val name: String, val note: String, val time: String)

private val spots = listOf(
    Spot("성산일출봉", "해 뜨기 30분 전에 오르면 붐비지 않아요", "1시간 30분"),
    Spot("우도", "전기 자전거로 섬 한 바퀴", "반나절"),
    Spot("섭지코지", "바닷바람 맞으며 걷는 산책길", "40분"),
    Spot("광치기 해변", "썰물 때 이끼 바위가 드러나요", "30분"),
    Spot("김녕 해수욕장", "에메랄드빛 얕은 바다", "2시간"),
    Spot("비자림", "천 년 된 비자나무 숲길", "1시간"),
    Spot("월정리 카페 거리", "노을 보며 커피 한 잔", "1시간"),
    Spot("만장굴", "용암이 지나간 동굴 탐험", "1시간"),
)

@Composable
private fun StretchyHeaderCard() {
    DemoCard(
        "늘어나는 헤더",
        "여행지 상세 화면처럼 대표 이미지가 목록 맨 위에 놓입니다. 맨 위에서 더 당기면 이미지가 늘어나고, 올리면 느리게 사라지며 제목이 상단 바로 넘어갑니다.",
    ) {
        val list = rememberLazyListState()
        val header = rememberKStretchyHeaderState(list)
        var saved by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxWidth().height(460.dp).clip(innerShape)) {
            LazyColumn(Modifier.fillMaxSize().nestedScroll(header.connection), state = list) {
                item {
                    KStretchyHeader(header, "제주 동쪽 한 바퀴", subtitle = "성산에서 월정리까지, 2박 3일", height = 260.dp) {
                        SunsetArt(Modifier.fillMaxSize().semantics { contentDescription = "노을 지는 성산일출봉" })
                    }
                }
                items(spots, key = { it.name }) { SpotRow(it) }
            }
            KStretchyHeaderBar(
                header,
                "제주 동쪽 한 바퀴",
                onBack = {},
                windowInsets = WindowInsets(0),
                actions = { ink ->
                    KIconButton(
                        KIcons.Bookmark,
                        if (saved) "저장 취소" else "저장",
                        { saved = !saved },
                        variant = KVariant.Text,
                        tone = KTone.Neutral,
                        contentColor = ink,
                    )
                },
            )
        }
    }
}

@Composable
private fun SpotRow(spot: Spot) {
    val ink = LocalKineticaContentColor.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(SPOT_ICON_BOX).clip(RoundedCornerShape(SPOT_ICON_CORNER)).background(SpotTint), Alignment.Center) {
            ShowcaseIcon(KIcons.Location, SPOT_ICON, SpotInk)
        }
        Column(Modifier.weight(1f)) {
            Label(spot.name, TITLE_SP, FontWeight.SemiBold)
            Label(spot.note, SMALL_SP, color = ink.copy(alpha = MUTED))
        }
        Label(spot.time, SMALL_SP, color = ink.copy(alpha = MUTED))
    }
}

/** 하늘, 해, 오름, 바다를 그린 대표 이미지 대신의 그림. 붓과 경로는 크기가 바뀔 때만 만든다. */
@Composable
private fun SunsetArt(modifier: Modifier) {
    Box(
        modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val sky = Brush.verticalGradient(SkyColors)
            val sea = Brush.verticalGradient(SeaColors, startY = h * SEA_TOP, endY = h)
            val sun = Offset(w * SUN_X, h * SUN_Y)
            val hill = Path().apply {
                val at = HILL.iterator()
                moveTo(0f, h * at.next())
                cubicTo(w * at.next(), h * at.next(), w * at.next(), h * at.next(), w * at.next(), h * at.next())
                cubicTo(w * at.next(), h * at.next(), w * at.next(), h * at.next(), w, h * at.next())
                lineTo(w, h * HILL_BASE)
                lineTo(0f, h * HILL_BASE)
                close()
            }
            onDrawBehind {
                drawRect(sky)
                drawCircle(SunColor, radius = h * SUN_RADIUS, center = sun)
                drawPath(hill, HillColor)
                drawRect(sea, topLeft = Offset(0f, h * SEA_EDGE))
            }
        },
    )
}

@Composable
private fun NotificationStackCard() {
    val messages = remember { mutableStateListOf<KNotification>().apply { addAll(sampleMessages()) } }
    val delivery = remember { mutableStateListOf<KNotification>().apply { addAll(sampleDelivery()) } }
    DemoCard(
        "알림 묶음",
        "같은 앱 알림이 겹쳐 쌓입니다. 누르면 목록으로 펼쳐지고, 카드를 옆으로 끝까지 밀면 지워집니다. 접힌 묶음을 밀면 묶음 전체가 지워집니다.",
    ) {
        KNotificationStack(
            "메시지",
            messages,
            onDismiss = { id -> messages.removeAll { it.id == id } },
            onDismissAll = { messages.clear() },
            icon = { AppBadge(KIcons.Message, MessageGreen) },
        )
        KNotificationStack(
            "배달",
            delivery,
            onDismiss = { id -> delivery.removeAll { it.id == id } },
            onDismissAll = { delivery.clear() },
            icon = { AppBadge(KIcons.Bag, DeliveryOrange) },
        )
        if (messages.size + delivery.size < 2) {
            Pill("알림 다시 받기") {
                messages.clear()
                messages.addAll(sampleMessages())
                delivery.clear()
                delivery.addAll(sampleDelivery())
            }
        }
    }
}

@Composable
private fun AppBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Box(Modifier.size(APP_BADGE).clip(RoundedCornerShape(APP_BADGE_CORNER)).background(color), Alignment.Center) {
        ShowcaseIcon(icon, APP_BADGE_ICON, Color.White)
    }
}

private fun sampleMessages() = listOf(
    KNotification("m1", "지수", "오늘 저녁 7시 성수 어때? 새로 생긴 파스타집 예약해 둘게", "지금"),
    KNotification("m2", "가족방", "엄마: 주말에 김장하니까 토요일 아침에 와", "3분 전"),
    KNotification("m3", "민호", "사진 12장을 보냈습니다", "12분 전"),
    KNotification("m4", "팀 채널", "회의실이 4층 큰 방으로 바뀌었어요", "28분 전"),
    KNotification("m5", "서연", "고마워! 다음엔 내가 살게", "1시간 전"),
)

private fun sampleDelivery() = listOf(
    KNotification("d1", "라이더가 가게에서 출발했어요", "도착까지 약 12분, 우리동네 마라탕", "지금"),
    KNotification("d2", "주문을 받았어요", "가게에서 음식을 준비하고 있어요", "8분 전"),
)

@Composable
private fun WordRotateCard() {
    DemoCard("단어 회전 문구", "랜딩 화면 머리글에서 한 단어만 돌아가며 바뀝니다. 화면 밖으로 나가거나 '애니메이션 삭제'가 켜져 있으면 멈춥니다.") {
        KWordRotate(
            listOf("산책", "캠핑", "독서", "자전거 타기", "빨래"),
            prefix = "오늘은",
            suffix = "하기 좋은 날",
            style = HeadlineStyle,
        )
        Label("맑음 18°, 미세먼지 좋음", BODY_SP, color = LocalKineticaContentColor.current.copy(alpha = MUTED))
    }
}

@Composable
private fun ShineButtonCard() {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    DemoCard("빛나는 버튼", "화면의 주 동작 하나에 빛 띠가 주기적으로 지나갑니다. 누르면 살짝 눌리고, 불러오는 동안에는 빛이 멈춥니다.") {
        KShineButton(
            "시작하기",
            onClick = {
                scope.launch {
                    loading = true
                    delay(LOADING_MS)
                    loading = false
                    done = true
                    delay(DONE_MS)
                    done = false
                }
            },
            size = KButtonSize.Large,
            fullWidth = true,
            loading = loading,
            success = done,
            trailingIcon = KIcons.ArrowForward,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            KShineButton("프리미엄 구독", onClick = {}, tone = KTone.Positive, modifier = Modifier.weight(1f))
            KShineButton("지금 신청", onClick = {}, tone = KTone.Destructive, modifier = Modifier.weight(1f), period = SLOW_SHINE_MS)
        }
    }
}

private val SPOT_ICON_BOX = 48.dp
private val SPOT_ICON_CORNER = 14.dp
private val SPOT_ICON = 22.dp
private val SpotTint = Color(0x2E3FA796)
private val SpotInk = Color(0xFF2E8B7A)
private const val TITLE_SP = 16
private const val SMALL_SP = 13
private const val BODY_SP = 14
private const val MUTED = 0.65f
private val SkyColors = listOf(Color(0xFF2B3A67), Color(0xFFB0558F), Color(0xFFFF9A62), Color(0xFFFFD39A))
private val SeaColors = listOf(Color(0xFF3A6C8F), Color(0xFF1C3550))
private val SunColor = Color(0xFFFFE7B8)
private val HillColor = Color(0xFF3B2E4A)
private const val SEA_TOP = 0.72f
private const val SUN_X = 0.68f
private const val SUN_Y = 0.6f
private const val SUN_RADIUS = 0.12f
private const val HILL_BASE = 0.76f
private const val SEA_EDGE = 0.74f

// 오름 윤곽: 시작 높이, 첫 곡선 조절점 둘과 끝점, 둘째 곡선 조절점 둘과 끝 높이 (가로, 세로 비율)
private val HILL = floatArrayOf(0.74f, 0.18f, 0.5f, 0.32f, 0.42f, 0.44f, 0.56f, 0.52f, 0.66f, 0.6f, 0.7f, 0.73f)
private val MessageGreen = Color(0xFF34C759)
private val DeliveryOrange = Color(0xFFFF8A3D)
private val APP_BADGE = 32.dp
private val APP_BADGE_CORNER = 9.dp
private val APP_BADGE_ICON = 18.dp
private val HeadlineStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
private const val LOADING_MS = 1400L
private const val DONE_MS = 1600L
private const val SLOW_SHINE_MS = 3600
