// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KAlert
import com.naury.kinetica.ui.KBanner
import com.naury.kinetica.ui.KBannerAction
import com.naury.kinetica.ui.KBufferProgressBar
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KButtonSize
import com.naury.kinetica.ui.KCard
import com.naury.kinetica.ui.KEmptyIcon
import com.naury.kinetica.ui.KEmptyIconKind
import com.naury.kinetica.ui.KEmptyState
import com.naury.kinetica.ui.KHapticType
import com.naury.kinetica.ui.KListItem
import com.naury.kinetica.ui.KLoadingIndicator
import com.naury.kinetica.ui.KLoadingStyle
import com.naury.kinetica.ui.KNoticeBar
import com.naury.kinetica.ui.KRefreshBox
import com.naury.kinetica.ui.KRefreshIndicator
import com.naury.kinetica.ui.KSegmentedControl
import com.naury.kinetica.ui.KSegmentedProgressBar
import com.naury.kinetica.ui.KSkeletonCard
import com.naury.kinetica.ui.KSkeletonListItem
import com.naury.kinetica.ui.KSkeletonStyle
import com.naury.kinetica.ui.KSnackbarHost
import com.naury.kinetica.ui.KStatus
import com.naury.kinetica.ui.KStepProgressBar
import com.naury.kinetica.ui.KToastAction
import com.naury.kinetica.ui.KToastPosition
import com.naury.kinetica.ui.KToaster
import com.naury.kinetica.ui.KTone
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.borderSpinner
import com.naury.kinetica.ui.rememberKHaptics
import com.naury.kinetica.ui.rememberKSnackbarState
import com.naury.kinetica.ui.rememberKToasterState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 피드백·로딩 탭: 토스트와 스낵바, 배너와 알림 상자, 로딩 표시, 진행 막대, 스켈레톤, 빈 화면, 새로 고침, 햅틱을 눌러 본다. */
@Composable
internal fun FeedbackTab(padding: PaddingValues, connection: NestedScrollConnection) {
    val toaster = rememberKToasterState()
    val snackbar = rememberKSnackbarState()
    var bottom by rememberSaveable { mutableStateOf(false) }
    var rich by rememberSaveable { mutableStateOf(false) }
    Box {
        ComponentTabList(padding, connection) {
            item { ToastCard(toaster, bottom, { bottom = it }, rich, { rich = it }) }
            item { SnackbarCard(snackbar) }
            item { BannerCard() }
            item { LoadingCard() }
            item { ProgressVariantsCard() }
            item { SkeletonCard() }
            item { EmptyCard() }
            item { RefreshCard() }
            item { HapticsCard() }
        }
        KToaster(toaster, position = if (bottom) KToastPosition.Bottom else KToastPosition.Top, richColors = rich)
        KSnackbarHost(snackbar, Modifier.padding(bottom = 96.dp))
    }
}

@Composable
private fun ToastCard(
    toaster: com.naury.kinetica.ui.KToasterState,
    bottom: Boolean,
    onBottom: (Boolean) -> Unit,
    rich: Boolean,
    onRich: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var uploads by rememberSaveable { mutableIntStateOf(0) }
    DemoCard("토스트", "종류마다 아이콘과 색이 다르고, 여러 개를 띄우면 겹쳐 쌓입니다. 쌓인 토스트를 누르면 펼쳐지고, 옆으로 쓸면 닫힙니다.") {
        KSegmentedControl(listOf("위", "아래"), if (bottom) 1 else 0, { onBottom(it == 1) }, Modifier.fillMaxWidth())
        KSegmentedControl(listOf("아이콘만", "바탕 색"), if (rich) 1 else 0, { onRich(it == 1) }, Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KButton("정보", { toaster.show("새 버전을 내려받을 수 있어요", KStatus.Info) }, variant = KVariant.Tonal, size = KButtonSize.Small)
            KButton("성공", {
                toaster.show("사진 3장을 올렸어요", KStatus.Success, description = "앨범 '여행'에 저장했어요")
            }, variant = KVariant.Tonal, tone = KTone.Positive, size = KButtonSize.Small)
            KButton("경고", { toaster.show("저장 공간이 거의 찼어요", KStatus.Warning) }, variant = KVariant.Tonal, size = KButtonSize.Small)
            KButton(
                "오류",
                { toaster.show("결제하지 못했어요", KStatus.Error, action = KToastAction("다시") { toaster.show("다시 시도할게요") }) },
                variant = KVariant.Tonal,
                tone = KTone.Destructive,
                size = KButtonSize.Small,
            )
        }
        KButton(
            "로딩에서 결과로 바뀌는 토스트",
            {
                // 세 번째 올리기마다 실패해 오류로 바뀌는 모습도 보여 준다.
                val failing = ++uploads % 3 == 0
                scope.launch {
                    toaster.promise("사진을 올리는 중…", success = { n: Int -> "사진 ${n}장을 올렸어요" }, error = { "네트워크가 끊겨 올리지 못했어요" }) {
                        delay(1_600)
                        check(!failing) { "연결 끊김" }
                        12
                    }
                }
            },
            fullWidth = true,
        )
    }
}

@Composable
private fun SnackbarCard(snackbar: com.naury.kinetica.ui.KSnackbarState) {
    val scope = rememberCoroutineScope()
    DemoCard("스낵바", "문구는 두 줄까지 보이고, 동작 버튼 글자가 길면 버튼이 아래 줄로 내려갑니다. 오른쪽 링이 닫기 버튼입니다.") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KButton("긴 동작", {
                scope.launch { snackbar.show("대화방 3개를 보관함으로 옮겼어요. 보관함은 설정에서 볼 수 있어요.", "보관함으로 이동하기") }
            }, variant = KVariant.Tonal, size = KButtonSize.Small)
            KButton("오류", {
                scope.launch { snackbar.show("메시지를 보내지 못했어요", "다시 보내기", status = KStatus.Error) }
            }, variant = KVariant.Tonal, tone = KTone.Destructive, size = KButtonSize.Small)
            KButton("성공", {
                scope.launch { snackbar.show("주소를 복사했어요", status = KStatus.Success) }
            }, variant = KVariant.Tonal, tone = KTone.Positive, size = KButtonSize.Small)
        }
    }
}

@Composable
private fun BannerCard() {
    var offline by rememberSaveable { mutableStateOf(true) }
    DemoCard("배너와 알림 상자", "배너는 사용자가 처리할 때까지 화면 위에 남고, 알림 상자는 본문 안에서 정보를 전합니다.") {
        KBanner(
            offline,
            "인터넷에 연결되어 있지 않아요. 저장한 내용은 연결되면 올라가요.",
            status = KStatus.Warning,
            title = "오프라인",
            actions = listOf(KBannerAction("다시 시도") { offline = false }),
            onDismiss = { offline = false },
        )
        if (!offline) KButton("배너 다시 보기", { offline = true }, variant = KVariant.Text, size = KButtonSize.Small)
        KAlert("카드 유효 기간이 이번 달에 끝나요. 결제가 막히지 않게 새 카드를 등록해 주세요.", title = "결제 수단 확인")
        KAlert("백업을 마쳤어요", status = KStatus.Success, variant = KVariant.Outlined)
        KAlert("동기화하지 못한 파일이 2개 있어요", status = KStatus.Warning, variant = KVariant.Glass)
        KAlert("계정이 잠겼어요", status = KStatus.Error, variant = KVariant.Filled, action = {
            KButton("잠금 풀기", {}, variant = KVariant.Glass, size = KButtonSize.Small)
        })
        KNoticeBar("10월 20일 새벽 2시부터 4시까지 서버 점검이 있어요. 그동안 주문과 결제를 쓸 수 없어요.")
        KNoticeBar("첫 주문 무료 배송 쿠폰이 도착했어요", status = KStatus.Info, onClick = {})
    }
}

@Composable
private fun LoadingCard() {
    var syncing by rememberSaveable { mutableStateOf(true) }
    DemoCard("로딩 표시", "점, 물결 막대, 도형 모핑, 궤도, 펄스 다섯 가지입니다. '애니메이션 삭제'를 켜면 멈춘 아이콘과 글자로 바뀝니다.") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            KLoadingStyle.entries.forEach { KLoadingIndicator(style = it, size = 44.dp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            KLoadingIndicator(contained = true, size = 56.dp)
            Label("원판 위에 올린 모핑 로딩", 14, color = LocalKineticaContentColor.current.copy(alpha = 0.7f))
        }
        KCard(
            Modifier.fillMaxWidth().borderSpinner(syncing, RoundedCornerShape(24.dp)),
            onClick = { syncing = !syncing },
            shape = RoundedCornerShape(24.dp),
        ) {
            Label(if (syncing) "동기화 중" else "동기화 완료", 16, FontWeight.SemiBold)
            Label("카드를 누르면 테두리 로딩을 켜고 끕니다", 13, color = LocalKineticaContentColor.current.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun ProgressVariantsCard() {
    var story by remember { mutableFloatStateOf(0f) }
    var step by rememberSaveable { mutableIntStateOf(2) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) story = (story + (now - last) / 1e9f / 15f) % 1f
                last = now
            }
        }
    }
    DemoCard("진행 막대", "스토리처럼 칸으로 나뉜 막대, 미리 받은 구간이 보이는 막대, 단계 막대입니다.") {
        KSegmentedProgressBar(story, segments = 5)
        KBufferProgressBar((story * 2) % 1f, minOf(1f, (story * 2) % 1f + 0.25f))
        KStepProgressBar(step, 5)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KButton("이전", { step = (step - 1).coerceAtLeast(1) }, variant = KVariant.Tonal, size = KButtonSize.Small)
            KButton("다음", { step = (step + 1).coerceAtMost(5) }, size = KButtonSize.Small)
        }
    }
}

@Composable
private fun SkeletonCard() {
    var loading by rememberSaveable { mutableStateOf(true) }
    var pulse by rememberSaveable { mutableStateOf(false) }
    val style = if (pulse) KSkeletonStyle.Pulse else KSkeletonStyle.Shimmer
    DemoCard("스켈레톤", "목록 행, 카드, 아바타 프리셋입니다. 빛이 흐르는 모양과 깜박이는 모양 중에서 고릅니다.") {
        KSegmentedControl(listOf("빛 흐름", "깜박임"), if (pulse) 1 else 0, { pulse = it == 1 }, Modifier.fillMaxWidth())
        if (loading) {
            KSkeletonListItem(style = style, trailing = true)
            KSkeletonListItem(style = style, lines = 1)
            KSkeletonCard(style = style)
        } else {
            KListItem("민지", supporting = "내일 오후 3시에 만나요", onClick = {})
            KListItem("하늘", onClick = {})
            KCard(Modifier.fillMaxWidth()) { Label("불러온 카드", 16, FontWeight.SemiBold) }
        }
        KButton(if (loading) "불러오기 끝내기" else "다시 불러오기", { loading = !loading }, variant = KVariant.Tonal, size = KButtonSize.Small)
    }
}

@Composable
private fun EmptyCard() {
    var kind by rememberSaveable { mutableIntStateOf(4) }
    val kinds = KEmptyIconKind.entries
    val titles = listOf("결과가 없어요", "오프라인이에요", "받은 편지가 없어요", "불러오지 못했어요", "볼 수 없는 내용이에요", "검색해 보세요", "모두 끝냈어요", "점검 중이에요")
    DemoCard("빈 화면 그림", "그림 8가지 중 새로 넣은 권한 없음, 검색 전, 완료, 점검 중을 고를 수 있습니다.") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            kinds.forEachIndexed { i, k ->
                KButton(
                    titles[i].take(6),
                    { kind = i },
                    variant = if (i == kind) KVariant.Filled else KVariant.Tonal,
                    size = KButtonSize.Small,
                )
            }
        }
        KEmptyState(titles[kind], Modifier.fillMaxWidth(), art = { KEmptyIcon(kinds[kind]) })
    }
}

@Composable
private fun RefreshCard() {
    var indicator by rememberSaveable { mutableIntStateOf(1) }
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            delay(1_500)
            refreshing = false
        }
    }
    DemoCard("당겨서 새로 고침", "아래 목록을 당겨 보세요. 물방울, 원 화살표, 점 세 개 중에서 고릅니다.") {
        KSegmentedControl(listOf("물방울", "원 화살표", "점"), indicator, { indicator = it }, Modifier.fillMaxWidth())
        KRefreshBox(
            refreshing,
            { refreshing = true },
            Modifier.fillMaxWidth().height(220.dp),
            indicator = KRefreshIndicator.entries[indicator],
        ) {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(12) { i -> KListItem("새 소식 ${i + 1}", supporting = "당기면 새로 고칩니다") }
            }
        }
    }
}

@Composable
private fun HapticsCard() {
    val haptics = rememberKHaptics()
    DemoCard("햅틱", "KHaptics로 키트 컴포넌트와 같은 진동을 냅니다. LocalKineticaHaptics로 앱 전체에서 끌 수 있습니다.") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "선택" to KHapticType.Selection,
                "틱" to KHapticType.Tick,
                "성공" to KHapticType.Success,
                "경고" to KHapticType.Warning,
                "오류" to KHapticType.Error,
                "끝 닿음" to KHapticType.Edge,
                "길게 누름" to KHapticType.LongPress,
            ).forEach { (name, type) ->
                KButton(name, { haptics.perform(type) }, variant = KVariant.Tonal, size = KButtonSize.Small)
            }
        }
        if (!haptics.isEnabled) Label("이 기기에서는 터치 진동이 꺼져 있습니다", 13)
    }
}
