package com.naury.kinetica.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** docs/36-quick-start.md의 예제가 지금 API로 컴파일되고 함께 그려지는지 확인한다. 문서를 고치면 여기도 함께 고친다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class QuickStartTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `빠른 시작 예제는 Material 테마 안에서 함께 그려지고 토스트가 뜬다`() {
        compose.setContent {
            MaterialTheme {
                KineticaTheme(
                    colors = KineticaColors(accent = MaterialTheme.colorScheme.primary, onAccent = MaterialTheme.colorScheme.onPrimary),
                    textStyle = MaterialTheme.typography.bodyLarge,
                ) {
                    KineticaScaffold { padding ->
                        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) { Examples() }
                        // 화면 아래에 늘 떠 있는 시트라서 스크롤 목록 밖, 화면 루트에 둔다.
                        val sheet = rememberKDetentSheetState(listOf(Detent.Peek(120.dp), Detent.Fraction(0.5f), Detent.Full))
                        KDetentSheet(sheet, title = "주변 장소") { Text("장소") }
                    }
                }
            }
        }
        compose.onNodeWithText("저장").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("저장했어요").assertExists()
    }

    @androidx.compose.runtime.Composable
    private fun Examples() {
        Card(Modifier.jellyPress().kBadge(3)) { Text("카드") }

        var open by remember { mutableStateOf(false) }
        KSheet(open, onDismissRequest = { open = false }, title = "공유") { Text("링크 복사") }

        var confirm by remember { mutableStateOf(false) }
        KDialog(visible = confirm, onDismissRequest = { confirm = false }, title = "주문 확인") {
            Text("결제할까요?")
            KButton("결제", onClick = { confirm = false })
        }

        val toast = rememberKToastState()
        val scope = rememberCoroutineScope()
        KToastHost(toast)
        KButton("저장", onClick = { scope.launch { toast.show("저장했어요") } })

        var tab by rememberSaveable { mutableIntStateOf(0) }
        KTabBar(listOf(KTab("홈"), KTab("검색")), tab, onSelect = { tab = it })

        var volume by remember { mutableFloatStateOf(0.4f) }
        KSlider(volume, onValueChange = { volume = it }, Modifier.fillMaxWidth())

        KLineChart(
            points = listOf(3f, 5f, 4f, 8f, 6f),
            Modifier.fillMaxWidth().height(160.dp),
            labels = listOf("월", "화", "수", "목", "금"),
        )

        var selection by remember { mutableStateOf<KCalendarSelection>(KCalendarSelection.Single()) }
        KCalendar(rememberKCalendarState(), selection, onSelectionChange = { selection = it })

        SwipeActionsRow(endActions = listOf(SwipeAction("삭제", Color.Red, removesRow = true) {})) { Text("메일") }

        val shape = RoundedCornerShape(20.dp)
        Box(
            Modifier
                .jellyPress()
                .glowBorder(shape)
                .glass(shape = shape)
                .clip(shape)
                .clickable {}
                .padding(16.dp),
        ) { Text("항목") }
    }
}
