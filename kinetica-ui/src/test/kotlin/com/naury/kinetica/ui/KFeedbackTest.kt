package com.naury.kinetica.ui

import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class KFeedbackTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var scope: CoroutineScope

    private fun liveRegion(mode: LiveRegionMode) = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, mode)

    private fun toaster(state: KToasterState, position: KToastPosition = KToastPosition.Top, rtl: Boolean = false, fontScale: Float = 1f) {
        compose.setContent {
            scope = rememberCoroutineScope()
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalDensity provides Density(density.density, fontScale),
            ) {
                KineticaScaffold(Modifier.fillMaxSize(), surface = SurfaceStyles.CleanLight) {
                    Box(Modifier.fillMaxSize()) { BasicText("내용") }
                    KToaster(state, position = position)
                }
            }
        }
        compose.mainClock.autoAdvance = false
    }

    /** 시계를 멈춘 채 [ms]만큼 흘린다. 자동으로 흘리면 waitForIdle이 표시 시간까지 건너뛴다. */
    private fun step(ms: Long = 500) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(ms)
    }

    private fun withReducedMotion(block: () -> Unit) {
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            block()
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
    }

    @Test
    fun `토스트는 종류별 아이콘을 달고 오류만 바로 읽히게 알린다`() {
        val state = KToasterState()
        toaster(state)
        state.show("저장했어요", KStatus.Success)
        state.show("결제하지 못했어요", KStatus.Error)
        step()
        compose.onNodeWithText("저장했어요", substring = true).assert(liveRegion(LiveRegionMode.Polite))
        compose.onNodeWithText("결제하지 못했어요", substring = true)
            .assert(liveRegion(LiveRegionMode.Assertive))
            .assertContentDescriptionContains("Error")
        assertThat(state.messages).containsExactly("저장했어요", "결제하지 못했어요").inOrder()
        // 둘이 쌓여 누를 수 있게 되어도 역할은 없다. 있으면 TalkBack이 알림 앞에 "버튼"부터 읽는다.
        compose.onNodeWithText("저장했어요", substring = true)
            .assert(hasClickAction())
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
    }

    @Test
    fun `토스트는 시간이 지나면 사라지고 펼친 동안에는 기다린다`() {
        val state = KToasterState()
        toaster(state)
        state.show("하나", durationMs = 1_000)
        state.show("둘", durationMs = 1_000)
        state.expanded = true
        step(3_000)
        assertThat(state.messages).containsExactly("하나", "둘").inOrder()

        state.expanded = false
        step(1_500)
        assertThat(state.messages).isEmpty()
        step(500)
        compose.onNodeWithText("하나", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `쌓인 토스트를 누르면 펼치고 넘치는 토스트는 앞의 것이 닫힐 때까지 기다린다`() {
        val state = KToasterState()
        toaster(state)
        val ids = (1..4).map { state.show("토스트 $it", durationMs = 60_000) }
        step()
        compose.onNodeWithText("토스트 1", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("토스트 4", substring = true).performClick()
        step()
        assertThat(state.expanded).isTrue()

        compose.onNodeWithText("토스트 4", substring = true).performSemanticsAction(SemanticsActions.Dismiss)
        step()
        compose.onNodeWithText("토스트 1", useUnmergedTree = true).assertExists()
        assertThat(state.messages).doesNotContain("토스트 4")
        assertThat(state.update(ids[3], "늦은 결과", KStatus.Success)).isFalse()
    }

    @Test
    fun `펼친 토스트는 큰 글자와 RTL에서도 서로 겹치지 않는다`() {
        val state = KToasterState()
        var position by mutableStateOf(KToastPosition.Top)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(density.density, 2f),
            ) {
                KineticaScaffold(Modifier.fillMaxSize(), surface = SurfaceStyles.CleanLight) { KToaster(state, position = position) }
            }
        }
        compose.mainClock.autoAdvance = false
        state.show("사진 3장을 올렸어요", KStatus.Success, description = "앨범 '여행'에 저장했어요", durationMs = 60_000)
        state.show("동기화 중 문제가 생겼어요. 잠시 뒤 다시 시도해 주세요.", KStatus.Warning, durationMs = 60_000)
        state.show("결제하지 못했어요", KStatus.Error, action = KToastAction("다시") {}, durationMs = 60_000)
        state.expanded = true
        for (edge in KToastPosition.entries) {
            position = edge
            step(1_000)
            val boxes = listOf("사진 3장", "동기화 중", "결제하지").map {
                val b = compose.onNodeWithText(it, substring = true).getUnclippedBoundsInRoot()
                Rect(b.left.value, b.top.value, b.right.value, b.bottom.value)
            }
            for (i in boxes.indices) {
                for (j in i + 1 until boxes.size) assertThat(boxes[i].overlaps(boxes[j])).isFalse()
            }
        }
    }

    @Test
    fun `promise는 로딩 토스트를 성공이나 실패로 바꾸고 결과를 돌려준다`() {
        val state = KToasterState()
        toaster(state)
        val gate = CompletableDeferred<Int>()
        var ok: Result<Int>? = null
        var bad: Result<Int>? = null
        scope.launch { ok = state.promise("올리는 중", success = { n: Int -> "$n 장 올림" }, error = { "실패" }) { gate.await() } }
        step()
        assertThat(state.entries.single().loading).isTrue()
        gate.complete(3)
        step()
        assertThat(ok?.getOrNull()).isEqualTo(3)
        assertThat(state.messages).containsExactly("3 장 올림")
        assertThat(state.entries.single().status).isEqualTo(KStatus.Success)

        scope.launch { bad = state.promise("보내는 중", success = { _: Int -> "보냄" }, error = { "보내지 못했어요: ${it.message}" }) { error("끊김") } }
        step()
        assertThat(bad?.isFailure).isTrue()
        assertThat(state.messages).contains("보내지 못했어요: 끊김")
        compose.onNodeWithText("보내지 못했어요: 끊김", substring = true).assert(liveRegion(LiveRegionMode.Assertive))
    }

    @Test
    fun `기존 토스트의 색 역할은 상태 아이콘이 되고 오류는 바로 읽힌다`() {
        val state = KToastState()
        compose.setContent {
            scope = rememberCoroutineScope()
            KineticaScaffold(Modifier.fillMaxSize()) { KToastHost(state) }
        }
        compose.mainClock.autoAdvance = false
        scope.launch { state.show("삭제하지 못했어요", tone = KTone.Destructive) }
        step()
        compose.onNodeWithText("삭제하지 못했어요", substring = true)
            .assert(liveRegion(LiveRegionMode.Assertive))
            .assertContentDescriptionContains("Error")
    }

    @Test
    fun `스낵바는 긴 동작 버튼을 문구 아래 줄로 내리고 오류 스낵바는 바로 읽힌다`() {
        val state = KSnackbarState()
        compose.setContent {
            scope = rememberCoroutineScope()
            KineticaScaffold(Modifier.fillMaxSize()) { KSnackbarHost(state) }
        }
        compose.mainClock.autoAdvance = false
        scope.launch { state.show("대화방 3개를 보관함으로 옮겼어요", "보관함으로 이동하기 버튼", 60_000, KStatus.Error) }
        step()
        val message = compose.onNodeWithText("대화방 3개를 보관함으로 옮겼어요", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val action = compose.onNodeWithText("보관함으로 이동하기 버튼").getUnclippedBoundsInRoot()
        assertThat(action.top.value).isAtLeast(message.bottom.value)
        compose.onNode(liveRegion(LiveRegionMode.Assertive)).assertExists()
        compose.onNodeWithText("보관함으로 이동하기 버튼").performClick()
        step()
        assertThat(state.currentMessage).isNull()
    }

    @Test
    fun `배너는 보일 때 알리고 동작과 닫기를 부르며 알림 상자는 상태 이름을 읽힌다`() {
        var visible by mutableStateOf(true)
        var retried = false
        compose.setContent {
            Column {
                KBanner(
                    visible,
                    "오프라인이에요",
                    status = KStatus.Warning,
                    actions = listOf(
                        KBannerAction("다시 시도") {
                            retried = true
                        },
                    ),
                    onDismiss = {
                        visible =
                            false
                    },
                )
                KAlert("카드가 곧 만료돼요", Modifier.testTag("alert"), status = KStatus.Warning, title = "결제 수단")
            }
        }
        compose.onNodeWithText("오프라인이에요").assertExists()
        compose.onNode(liveRegion(LiveRegionMode.Polite)).assertExists()
        compose.onNodeWithText("다시 시도").performClick()
        assertThat(retried).isTrue()
        compose.onAllNodesWithContentDescription("Warning").assertCountEquals(2)
        compose.onNodeWithContentDescription("Dismiss").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("오프라인이에요").assertDoesNotExist()
        compose.onNodeWithText("결제 수단").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun `공지 막대는 긴 글을 한 줄로 흘린다`() {
        compose.setContent { Box(Modifier.width(240.dp)) { KNoticeBar(LONG_NOTICE, Modifier.testTag("bar")) } }
        assertThat(compose.onNodeWithTag("bar").getUnclippedBoundsInRoot().height).isEqualTo(44.dp)
    }

    @Test
    fun `공지 막대는 애니메이션 삭제에서 흐르지 않고 줄을 바꿔 모두 보여 준다`() = withReducedMotion {
        compose.setContent { Box(Modifier.width(240.dp)) { KNoticeBar(LONG_NOTICE, Modifier.testTag("bar")) } }
        assertThat(compose.onNodeWithTag("bar").getUnclippedBoundsInRoot().height).isGreaterThan(60.dp)
        compose.onNodeWithText(LONG_NOTICE).assertExists()
    }

    @Test
    fun `로딩 표시는 진행 정도를 모르는 진행 표시로 읽힌다`() {
        compose.setContent {
            Row {
                KLoadingStyle.entries.forEach { KLoadingIndicator(Modifier.testTag(it.name), style = it, label = "${it.name} 로딩") }
            }
        }
        KLoadingStyle.entries.forEach { style ->
            compose.onNodeWithTag(style.name)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate))
                .assertContentDescriptionContains("${style.name} 로딩")
        }
        compose.onNodeWithText("Loading…", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `로딩 표시는 애니메이션 삭제면 멈춘 아이콘과 글자로 바뀐다`() = withReducedMotion {
        compose.setContent { KLoadingIndicator() }
        compose.onNodeWithText("Loading…", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `로딩과 스켈레톤과 테두리 로딩을 담은 300행 목록은 애니메이션 중에 다시 구성하지 않는다`() {
        var compositions = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            KineticaTheme(surface = SurfaceStyles.CleanLight) {
                LazyColumn(Modifier.size(360.dp, 640.dp)) {
                    items(300) { i ->
                        SideEffect { compositions++ }
                        Row(Modifier.borderSpinner(true, RoundedCornerShape(12.dp))) {
                            KLoadingIndicator(style = KLoadingStyle.entries[i % KLoadingStyle.entries.size], size = 24.dp)
                            KSkeletonListItem(Modifier.weight(1f), style = if (i % 2 == 0) KSkeletonStyle.Pulse else KSkeletonStyle.Shimmer)
                            KSegmentedProgressBar(0.5f, 3, Modifier.width(40.dp))
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(800)
        val settled = compositions
        assertThat(settled).isGreaterThan(0)
        compose.mainClock.advanceTimeBy(2_000)
        assertThat(compositions).isEqualTo(settled)
    }

    @Test
    fun `진행 막대 변형은 비율과 단계를 읽힌다`() {
        compose.setContent {
            Column(Modifier.width(300.dp)) {
                KSegmentedProgressBar(0.5f, 4, Modifier.testTag("seg"))
                KBufferProgressBar(0.2f, 0.6f, Modifier.testTag("buf"))
                KStepProgressBar(2, 5, Modifier.testTag("step"))
            }
        }
        compose.onNodeWithTag("seg").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(
                    0.5f,
                    0f..1f,
                ),
            ),
        )
        compose.onNodeWithTag("buf").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(
                    0.2f,
                    0f..1f,
                ),
            ),
        )
        compose.onNodeWithTag("step").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Step 2 of 5"))
    }

    @Test
    fun `스켈레톤 프리셋은 불러오는 중으로 읽히고 행 높이는 목록 행과 거의 같다`() {
        compose.setContent {
            Column(Modifier.width(320.dp)) {
                KSkeletonListItem(Modifier.testTag("row"))
                KListItem("민지", Modifier.testTag("real"), supporting = "내일 만나요")
                KSkeletonCard(Modifier.testTag("card"))
                KSkeletonAvatar(Modifier.testTag("avatar"))
            }
        }
        listOf("row", "card", "avatar").forEach { compose.onNodeWithTag(it).assertContentDescriptionContains("Loading") }
        val skeleton = compose.onNodeWithTag("row").getUnclippedBoundsInRoot().height.value
        val real = compose.onNodeWithTag("real").getUnclippedBoundsInRoot().height.value
        // 글꼴 줄 높이는 기기마다 조금씩 달라 몇 dp 차이는 허용한다.
        assertThat(skeleton).isWithin(4f).of(real)
    }

    @Test
    fun `빈 화면 그림은 모든 종류를 그린다`() {
        compose.setContent {
            Row { KEmptyIconKind.entries.forEach { KEmptyIcon(it, Modifier.testTag(it.name).size(36.dp), color = Color.Red) } }
        }
        KEmptyIconKind.entries.forEach { kind ->
            val map = compose.onNodeWithTag(kind.name).captureToImage().toPixelMap()
            var lit = 0
            for (x in 0 until map.width step 2) for (y in 0 until map.height step 2) if (map[x, y].alpha > 0.5f) lit++
            assertThat(lit).isGreaterThan(20)
        }
    }

    @Test
    fun `햅틱은 종류를 시스템 상수로 옮기고 테마에서 끄면 울리지 않는다`() {
        assertThat(hapticConstant(KHapticType.Success, 34)).isEqualTo(HapticFeedbackConstants.CONFIRM)
        assertThat(hapticConstant(KHapticType.Success, 26)).isEqualTo(HapticFeedbackConstants.CONTEXT_CLICK)
        assertThat(hapticConstant(KHapticType.Error, 30)).isEqualTo(HapticFeedbackConstants.REJECT)
        assertThat(hapticConstant(KHapticType.Edge, 34)).isEqualTo(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)
        assertThat(hapticConstant(KHapticType.Selection, 28)).isEqualTo(HapticFeedbackConstants.CLOCK_TICK)
        var on: KHaptics? = null
        var off: KHaptics? = null
        compose.setContent {
            on = rememberKHaptics()
            CompositionLocalProvider(LocalKineticaHaptics provides false) { off = rememberKHaptics() }
        }
        assertThat(requireNotNull(on).isEnabled).isTrue()
        assertThat(requireNotNull(off).perform(KHapticType.Success)).isFalse()
    }

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test
    fun `새로 고침 표시 변형도 사용자 지정 동작으로 새로 고친다`() {
        var indicator by mutableStateOf(KRefreshIndicator.Drop)
        var refreshed = 0
        compose.setContent {
            KRefreshBox(false, { refreshed++ }, Modifier.testTag("box").fillMaxWidth().size(300.dp), indicator = indicator) {
                LazyColumn { items(10) { BasicText("행 $it") } }
            }
        }
        for (each in KRefreshIndicator.entries) {
            indicator = each
            compose.waitForIdle()
            compose.onNodeWithTag("box").performCustomAccessibilityActionWithLabel("Refresh")
        }
        assertThat(refreshed).isEqualTo(KRefreshIndicator.entries.size)
    }

    @Test
    fun `단색 표면에서는 토스트와 배너와 로딩이 배경을 기록하지 않는다`() {
        var backdrop: GlassBackdrop? = null
        val state = KToasterState()
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                backdrop = LocalGlassBackdrop.current
                Column {
                    KBanner(true, "안내")
                    KLoadingIndicator(contained = true)
                    KSkeletonCard()
                }
                KToaster(state)
            }
        }
        state.show("저장했어요", KStatus.Success)
        compose.waitForIdle()
        assertThat(backdrop).isNull()
    }
}

private const val LONG_NOTICE = "10월 20일 새벽 2시부터 4시까지 서버 점검이 있어요. 그동안 주문과 결제를 쓸 수 없어요."
