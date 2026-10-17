// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KAssistChip
import com.naury.kinetica.ui.KAvatar
import com.naury.kinetica.ui.KAvatarData
import com.naury.kinetica.ui.KAvatarSize
import com.naury.kinetica.ui.KAvatarStatus
import com.naury.kinetica.ui.KBadge
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KButtonGroup
import com.naury.kinetica.ui.KButtonSize
import com.naury.kinetica.ui.KCard
import com.naury.kinetica.ui.KChipGroup
import com.naury.kinetica.ui.KChipRow
import com.naury.kinetica.ui.KExtendedFab
import com.naury.kinetica.ui.KFab
import com.naury.kinetica.ui.KFabSize
import com.naury.kinetica.ui.KFilterChip
import com.naury.kinetica.ui.KGroupItem
import com.naury.kinetica.ui.KGroupSelection
import com.naury.kinetica.ui.KGroupStyle
import com.naury.kinetica.ui.KHorizontalCard
import com.naury.kinetica.ui.KIconButton
import com.naury.kinetica.ui.KIconButtonSizes
import com.naury.kinetica.ui.KIconToggleButton
import com.naury.kinetica.ui.KInputChip
import com.naury.kinetica.ui.KMediaCard
import com.naury.kinetica.ui.KSelectableCard
import com.naury.kinetica.ui.KShapeToken
import com.naury.kinetica.ui.KSplitButton
import com.naury.kinetica.ui.KStatus
import com.naury.kinetica.ui.KStatusBadge
import com.naury.kinetica.ui.KSuggestionChip
import com.naury.kinetica.ui.KToggleGroup
import com.naury.kinetica.ui.KTone
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.LocalKineticaContentColor
import kotlinx.coroutines.delay

/** 버튼, 칩, 카드 탭: 변형과 색 역할을 모두 보여 주고, 쇼핑과 프로필 화면에 실제로 놓아 본다. */
@Composable
internal fun ButtonsChipsCardsTab(padding: PaddingValues, connection: NestedScrollConnection) {
    ComponentTabList(padding, connection) {
        item { ShoppingScene() }
        item { ProfileScene() }
        item { VariantMatrixCard() }
        item { IconButtonsCard() }
        item { SplitAndGroupCard() }
        item { ChipsCard() }
        item { CardsCard() }
        item { BadgesCard() }
    }
}

@Composable
private fun ShoppingScene() {
    var liked by rememberSaveable { mutableStateOf(false) }
    var size by rememberSaveable { mutableStateOf(setOf(1)) }
    var colors by rememberSaveable { mutableStateOf(setOf(0)) }
    DemoCard("쇼핑 장면", "사진 카드 위에 할인 배지와 찜 버튼을 얹고, 크기는 이어 붙인 버튼 묶음으로, 색은 칩으로 고릅니다. 담기를 누르면 로딩 뒤 체크로 바뀝니다.") {
        Box {
            KMediaCard(
                { Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFFFD3A5), Color(0xFFFD6585))))) },
                "러닝화 에어 라이트",
                Modifier.fillMaxWidth(),
                subtitle = "₩89,000  ·  리뷰 1,204",
                eyebrow = "오늘만 20% 할인",
                aspectRatio = 16f / 10f,
                shape = kitShape { KShapeToken.Medium },
                trailing = {
                    KIconToggleButton(liked, {
                        liked = it
                    }, Batch2Icons.Heart, "찜", checkedIcon = Batch2Icons.HeartFilled, tone = KTone.Destructive)
                },
            )
            Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KBadge("-20%", tone = KTone.Destructive)
                KBadge("무료 배송", tone = KTone.Neutral, variant = KVariant.Tonal)
            }
        }
        Label("사이즈", 13, FontWeight.SemiBold)
        KToggleGroup(listOf("240", "250", "260", "270").map { KGroupItem(it) }, size, { size = it }, Modifier.fillMaxWidth())
        Label("색상", 13, FontWeight.SemiBold)
        KChipGroup(listOf("코랄", "블랙", "화이트", "민트", "라벤더"), colors, { colors = it }, wrap = false)
        CartButton()
    }
}

/** 담기를 누르면 로딩 링이 돌다가 원으로 줄며 체크가 그려지고, 잠시 뒤 원래 버튼으로 돌아온다. */
@Composable
private fun CartButton() {
    var stage by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(stage) {
        when (stage) {
            1 -> {
                delay(1_100)
                stage = 2
            }

            2 -> {
                delay(1_500)
                stage = 0
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        KIconButton(Batch2Icons.Bag, "장바구니 보기", {}, variant = KVariant.Tonal, size = KIconButtonSizes.Large)
        KButton(
            "장바구니에 담기",
            { stage = 1 },
            Modifier.weight(1f),
            size = KButtonSize.Large,
            loading = stage == 1,
            success = stage == 2,
            fullWidth = true,
        )
    }
}

@Composable
private fun ProfileScene() {
    var following by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(setOf(0)) }
    DemoCard("프로필 장면", "아바타의 상태 점, 상태 배지, 팔로우 토글과 분리된 버튼 묶음, 관심사 칩, 가로 카드로 마이페이지를 꾸몄습니다.") {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            KAvatar(KAvatarData("김하늘"), size = KAvatarSize.ExtraLarge, status = KAvatarStatus.Online)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label("김하늘", 20, FontWeight.Bold)
                Label("@haneul · 서울", 13, color = LocalKineticaContentColor.current.copy(alpha = 0.7f))
                KStatusBadge("크리에이터 인증", KStatus.Info)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KButton(
                if (following) "팔로잉" else "팔로우",
                { following = !following },
                Modifier.weight(1f),
                variant = if (following) KVariant.Tonal else KVariant.Filled,
                icon = if (following) null else Batch2Icons.Plus,
                fullWidth = true,
            )
            KButtonGroup(
                listOf(
                    KGroupItem(icon = Batch2Icons.Chat, contentDescription = "메시지"),
                    KGroupItem(icon = Batch2Icons.Share, contentDescription = "공유"),
                ),
                {},
                variant = KVariant.Tonal,
                tone = KTone.Neutral,
                style = KGroupStyle.Connected,
            )
        }
        KChipRow {
            listOf("러닝", "필름 카메라", "브런치", "재즈").forEach { KSuggestionChip(it, {}) }
        }
        KToggleGroup(listOf("게시물", "저장됨", "태그됨").map { KGroupItem(it) }, tab, { tab = it }, Modifier.fillMaxWidth())
        KHorizontalCard(
            "한강 야간 러닝 10km 기록",
            Modifier.fillMaxWidth(),
            subtitle = "어제 · 좋아요 128",
            thumbnail = { Box(Modifier.fillMaxSize().background(Brush.linearGradient(PHOTO_COLORS[3]))) },
            trailing = { KBadge(12, tone = KTone.Accent) },
            onClick = {},
        )
    }
}

@Composable
private fun VariantMatrixCard() {
    DemoCard("버튼 변형과 색 역할", "모양 여섯 가지와 색 역할 네 가지를 자유롭게 조합합니다. 위 스타일 버튼으로 표면을 글래스, 단색, 톤으로 바꿔 보세요.") {
        KVariant.entries.forEach { variant ->
            Label(variant.name, 12, FontWeight.SemiBold, color = LocalKineticaContentColor.current.copy(alpha = 0.6f))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                KTone.entries.forEach { tone ->
                    KButton(TONE_NAMES.getValue(tone), {}, variant = variant, tone = tone, size = KButtonSize.Small)
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KButton("다음", {}, variant = KVariant.Tonal, trailingIcon = Batch2Icons.Arrow)
            KButton("새로 만들기", {}, icon = Batch2Icons.Plus)
            KButton("꺼짐", {}, enabled = false)
        }
        KButton("주문하기", {}, size = KButtonSize.Large, fullWidth = true)
    }
}

@Composable
private fun IconButtonsCard() {
    var bookmarked by rememberSaveable { mutableStateOf(true) }
    var muted by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(true) }
    DemoCard("아이콘 버튼과 FAB", "토글 버튼은 켜질 때 색이 가운데에서 퍼집니다. 확장 FAB는 목록을 내리면 아이콘만 남도록 접히는데, 여기서는 버튼으로 접어 봅니다.") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            KIconToggleButton(bookmarked, { bookmarked = it }, Batch2Icons.Bookmark, "북마크", checkedIcon = Batch2Icons.BookmarkFilled)
            KIconToggleButton(muted, { muted = it }, Batch2Icons.Bell, "알림 끄기", variant = KVariant.Outlined, tone = KTone.Neutral)
            KIconButton(Batch2Icons.Share, "공유", {}, size = KIconButtonSizes.Small, variant = KVariant.Tonal)
            // shape 파라미터로 하나만 모양을 바꾸는 예라 형태 설정과 상관없이 12dp로 둔다.
            KIconButton(Batch2Icons.Share, "공유", {}, variant = KVariant.Outlined, shape = RoundedCornerShape(12.dp))
            KIconButton(Batch2Icons.Share, "공유", {}, size = KIconButtonSizes.Large, variant = KVariant.Filled)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            KFab(Batch2Icons.Plus, "추가", {}, size = KFabSize.Small)
            KFab(Batch2Icons.Plus, "추가", {}, variant = KVariant.Tonal)
            KFab(Batch2Icons.Pen, "쓰기", {}, size = KFabSize.Large)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            KExtendedFab("새 메일", Batch2Icons.Pen, {}, expanded = expanded)
            Pill(if (expanded) "접기" else "펼치기") { expanded = !expanded }
        }
    }
}

@Composable
private fun SplitAndGroupCard() {
    var menu by rememberSaveable { mutableStateOf(false) }
    var format by rememberSaveable { mutableStateOf(setOf(0)) }
    var view by rememberSaveable { mutableStateOf(setOf(0)) }
    var sent by rememberSaveable { mutableStateOf("") }
    DemoCard("분할 버튼과 버튼 묶음", "버튼 묶음은 누르고 있는 칸이 넓어지고 양옆 칸이 비켜 줍니다. 묶음 전체 폭은 그대로라 주변이 흔들리지 않습니다.") {
        KSplitButton("보내기", { sent = "지금 보냈어요" }, { menu = !menu }, menuExpanded = menu, icon = Batch2Icons.Send)
        AnimatedVisibility(menu) {
            KChipRow {
                listOf("예약 전송", "임시 저장", "나에게 보내기").forEach {
                    KAssistChip(it, {
                        sent = it
                        menu = false
                    })
                }
            }
        }
        if (sent.isNotEmpty()) Label(sent, 13, color = LocalKineticaContentColor.current.copy(alpha = 0.7f))
        KButtonGroup(listOf("복사", "붙여넣기", "잘라내기").map { KGroupItem(it) }, {})
        KToggleGroup(
            listOf(KGroupItem("굵게"), KGroupItem("기울임"), KGroupItem("밑줄")),
            format,
            { format = it },
            selection = KGroupSelection.Multiple,
            style = KGroupStyle.Separated,
            variant = KVariant.Outlined,
        )
        KToggleGroup(
            listOf(KGroupItem("목록", Batch2Icons.Rows), KGroupItem("격자", Batch2Icons.Grid)),
            view,
            { view = it },
            variant = KVariant.Glass,
        )
    }
}

@Composable
private fun ChipsCard() {
    var filters by rememberSaveable { mutableStateOf(setOf("무료 배송")) }
    var people by rememberSaveable { mutableStateOf(listOf("민지", "Ana", "준호")) }
    var sort by rememberSaveable { mutableStateOf(setOf(0)) }
    DemoCard("칩", "필터 칩은 켜면 체크가 그려지고, 입력 칩은 ×로 지웁니다. 많은 칩은 한 줄로 두고 가로로 스크롤합니다.") {
        KChipRow {
            listOf("무료 배송", "오늘 도착", "할인", "리뷰 4점 이상", "새 상품").forEach { name ->
                KFilterChip(name, name in filters, { filters = if (it) filters + name else filters - name })
            }
        }
        KChipGroup(listOf("추천순", "가격 낮은순", "가격 높은순", "리뷰 많은순"), sort, { sort = it }, variant = KVariant.Outlined, wrap = false)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            people.forEach { name ->
                KInputChip(name, { people = people - name }, avatar = { KAvatar(KAvatarData(name), size = KAvatarSize.ExtraSmall) })
            }
            if (people.isEmpty()) KSuggestionChip("받는 사람 되돌리기", { people = listOf("민지", "Ana", "준호") })
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KAssistChip("일정에 추가", {}, leadingIcon = Batch2Icons.Calendar)
            KAssistChip("길 찾기", {}, leadingIcon = Batch2Icons.Pin)
            KSuggestionChip("좋아요!", {})
            KSuggestionChip("곧 갈게요", {})
        }
    }
}

@Composable
private fun CardsCard() {
    var plan by rememberSaveable { mutableStateOf(1) }
    DemoCard("카드", "같은 내용을 네 가지 카드 모양으로 놓았습니다. 아래 요금제 카드는 하나만 고를 수 있고, 고르면 테두리가 굵어지며 체크가 튀어나옵니다.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(KVariant.Glass, KVariant.Elevated, KVariant.Filled, KVariant.Outlined).forEach { variant ->
                KCard(Modifier.weight(1f), onClick = {}, variant = variant, contentPadding = 12.dp) {
                    Label(variant.name, 13, FontWeight.SemiBold)
                    Label("카드", 12)
                }
            }
        }
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("월간" to "₩9,900", "연간" to "₩99,000").forEachIndexed { i, (name, price) ->
                KSelectableCard(plan == i, { plan = i }, Modifier.weight(1f), role = Role.RadioButton) {
                    Label(name, 15, FontWeight.SemiBold)
                    Label(price, 20, FontWeight.Bold)
                    if (i == 1) KStatusBadge("2개월 무료", KStatus.Success)
                }
            }
        }
        KCard(Modifier.fillMaxWidth(), variant = KVariant.Filled, tone = KTone.Accent) {
            Label("멤버십 혜택", 16, FontWeight.Bold)
            Label("이번 달 무료 배송 3회가 남았어요.", 13)
        }
    }
}

@Composable
private fun BadgesCard() {
    var unread by rememberSaveable { mutableStateOf(3) }
    DemoCard("배지와 아바타", "숫자 배지는 글자 옆에 놓고, 상태 배지는 주문이나 서버 상태를 짧게 알립니다. 아바타는 이름에 따라 같은 색을 고릅니다.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Label("받은편지함", 15, FontWeight.SemiBold)
            KBadge(unread)
            KBadge("NEW")
            Box(Modifier.weight(1f))
            Pill("+1") { unread++ }
            Pill("읽음") { unread = 0 }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KStatusBadge("배송 중", KStatus.Info)
            KStatusBadge("배송 완료", KStatus.Success)
            KStatusBadge("입금 대기", KStatus.Warning)
            KStatusBadge("결제 실패", KStatus.Error)
            KStatusBadge("보관됨", KStatus.Neutral, variant = KVariant.Outlined)
            KStatusBadge("품절", KStatus.Error, variant = KVariant.Filled, dot = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            KAvatar(KAvatarData("민지"), size = KAvatarSize.ExtraSmall)
            KAvatar(KAvatarData("Ana Lee"), size = KAvatarSize.Small, status = KAvatarStatus.Busy)
            KAvatar(KAvatarData("준호"), status = KAvatarStatus.Away)
            KAvatar(KAvatarData("서연"), size = KAvatarSize.Large, status = KAvatarStatus.Online)
            // 팀 아바타를 둥근 사각으로 바꾸는 예라 형태 설정과 상관없이 16dp로 둔다.
            KAvatar(KAvatarData("팀"), size = KAvatarSize.Large, shape = RoundedCornerShape(16.dp), status = KAvatarStatus.Offline)
        }
    }
}

private val TONE_NAMES = mapOf(KTone.Accent to "강조", KTone.Neutral to "중립", KTone.Destructive to "삭제", KTone.Positive to "완료")

/** 이 탭에서만 쓰는 아이콘(24 격자) */
private object Batch2Icons {
    val Plus = line {
        moveTo(12f, 5f)
        lineTo(12f, 19f)
        moveTo(5f, 12f)
        lineTo(19f, 12f)
    }
    val Arrow = line(mirrored = true) {
        moveTo(4f, 12f)
        lineTo(19f, 12f)
        moveTo(13f, 6f)
        lineTo(19f, 12f)
        lineTo(13f, 18f)
    }
    val Heart = line { heart() }
    val HeartFilled = fill { heart() }
    val Bookmark = line { bookmark() }
    val BookmarkFilled = fill { bookmark() }
    val Bell = line {
        moveTo(6f, 16f)
        lineTo(6f, 11f)
        curveTo(6f, 7.5f, 8.5f, 5f, 12f, 5f)
        curveTo(15.5f, 5f, 18f, 7.5f, 18f, 11f)
        lineTo(18f, 16f)
        lineTo(19.5f, 17.5f)
        lineTo(4.5f, 17.5f)
        close()
        moveTo(10f, 20f)
        lineTo(14f, 20f)
    }
    val Share = line {
        moveTo(12f, 4f)
        lineTo(12f, 15f)
        moveTo(8f, 8f)
        lineTo(12f, 4f)
        lineTo(16f, 8f)
        moveTo(6f, 12f)
        lineTo(6f, 20f)
        lineTo(18f, 20f)
        lineTo(18f, 12f)
    }
    val Chat = line {
        moveTo(5f, 6f)
        lineTo(19f, 6f)
        lineTo(19f, 16f)
        lineTo(10f, 16f)
        lineTo(6f, 19.5f)
        lineTo(6f, 16f)
        lineTo(5f, 16f)
        close()
    }
    val Bag = line {
        moveTo(5f, 8f)
        lineTo(19f, 8f)
        lineTo(18f, 20f)
        lineTo(6f, 20f)
        close()
        moveTo(9f, 10f)
        lineTo(9f, 7f)
        curveTo(9f, 5f, 10.5f, 4f, 12f, 4f)
        curveTo(13.5f, 4f, 15f, 5f, 15f, 7f)
        lineTo(15f, 10f)
    }
    val Pen = line {
        moveTo(5f, 19f)
        lineTo(6f, 15f)
        lineTo(15.5f, 5.5f)
        lineTo(18.5f, 8.5f)
        lineTo(9f, 18f)
        close()
    }
    val Send = line {
        moveTo(4f, 12f)
        lineTo(20f, 5f)
        lineTo(14f, 20f)
        lineTo(11f, 13f)
        close()
    }
    val Calendar = line {
        moveTo(5f, 7f)
        lineTo(19f, 7f)
        lineTo(19f, 19f)
        lineTo(5f, 19f)
        close()
        moveTo(5f, 11f)
        lineTo(19f, 11f)
        moveTo(9f, 4.5f)
        lineTo(9f, 8f)
        moveTo(15f, 4.5f)
        lineTo(15f, 8f)
    }
    val Pin = line {
        moveTo(12f, 21f)
        curveTo(8f, 16f, 6f, 13f, 6f, 10f)
        curveTo(6f, 6.5f, 8.7f, 4f, 12f, 4f)
        curveTo(15.3f, 4f, 18f, 6.5f, 18f, 10f)
        curveTo(18f, 13f, 16f, 16f, 12f, 21f)
        close()
        moveTo(12f, 8f)
        lineTo(12f, 12f)
    }
    val Rows = line {
        moveTo(5f, 7f)
        lineTo(19f, 7f)
        moveTo(5f, 12f)
        lineTo(19f, 12f)
        moveTo(5f, 17f)
        lineTo(19f, 17f)
    }
    val Grid = line {
        moveTo(5f, 5f)
        lineTo(11f, 5f)
        lineTo(11f, 11f)
        lineTo(5f, 11f)
        close()
        moveTo(13f, 5f)
        lineTo(19f, 5f)
        lineTo(19f, 11f)
        lineTo(13f, 11f)
        close()
        moveTo(5f, 13f)
        lineTo(11f, 13f)
        lineTo(11f, 19f)
        lineTo(5f, 19f)
        close()
        moveTo(13f, 13f)
        lineTo(19f, 13f)
        lineTo(19f, 19f)
        lineTo(13f, 19f)
        close()
    }

    private fun PathBuilder.heart() {
        moveTo(12f, 19.5f)
        curveTo(7f, 15.5f, 3.5f, 12.5f, 3.5f, 9f)
        curveTo(3.5f, 6.5f, 5.5f, 4.5f, 8f, 4.5f)
        curveTo(9.6f, 4.5f, 11f, 5.4f, 12f, 6.8f)
        curveTo(13f, 5.4f, 14.4f, 4.5f, 16f, 4.5f)
        curveTo(18.5f, 4.5f, 20.5f, 6.5f, 20.5f, 9f)
        curveTo(20.5f, 12.5f, 17f, 15.5f, 12f, 19.5f)
        close()
    }

    private fun PathBuilder.bookmark() {
        moveTo(7f, 4f)
        lineTo(17f, 4f)
        lineTo(17f, 20f)
        lineTo(12f, 16f)
        lineTo(7f, 20f)
        close()
    }

    private fun line(mirrored: Boolean = false, block: PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = mirrored,
    ).path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    ).build()

    private fun fill(block: PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(fill = SolidColor(Color.Black), pathBuilder = block).build()
}
