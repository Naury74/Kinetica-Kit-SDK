package com.naury.kinetica.ui

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 편집 SDK 샘플 앱에 붙이며 나온 1차 피드백: 칩 폭, 목록 Painter 아이콘, 다이얼로그 취소 버튼. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class FrameKitFeedbackTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `줄을 넘기는 칩 묶음은 칩을 켜고 꺼도 폭과 자리가 그대로다`() {
        // 폭이 바뀌면 뒤 칩들이 다른 줄로 옮겨 가 연달아 누를 때 손가락 아래 칩이 바뀌었다.
        val labels = listOf("전체", "사진", "동영상", "스크린샷", "즐겨찾기", "셀피", "인물", "풍경")
        var picked by mutableStateOf(labels.indices.toSet())
        compose.setContent {
            KineticaTheme(surface = SurfaceStyles.CleanLight) {
                KChipGroup(labels, picked, { picked = it }, Modifier.width(320.dp), selection = KChipSelection.Multiple)
            }
        }
        val before = labels.map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot }
        compose.onNodeWithText("전체").performClick()
        compose.onNodeWithText("사진").performClick()
        compose.waitForIdle()
        val after = labels.map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot }
        assertThat(picked).doesNotContain(0)
        assertThat(after.map { it.width }).isEqualTo(before.map { it.width })
        assertThat(after.map { it.top }).isEqualTo(before.map { it.top })
    }

    @Test
    fun `목록 앞 아이콘에 Painter를 넣을 수 있다`() {
        compose.setContent {
            KineticaTheme(surface = SurfaceStyles.CleanLight) {
                KListItem("저장 공간", leading = KListLeading.PainterIcon(ColorPainter(Color.Red), container = Color.Blue))
            }
        }
        compose.onNodeWithText("저장 공간").assertExists()
    }

    @Test
    fun `취소 버튼은 동작 없이 만들고 누르면 다이얼로그만 닫힌다`() {
        var open by mutableStateOf(true)
        var deleted = false
        compose.setContent {
            KAlertDialog(
                open,
                { open = false },
                "삭제할까요?",
                listOf(
                    KDialogAction("취소", KDialogActionStyle.Cancel),
                    KDialogAction("삭제", KDialogActionStyle.Destructive) {
                        deleted = true
                    },
                ),
            )
        }
        compose.onNodeWithText("취소").performClick()
        compose.waitForIdle()
        assertThat(open).isFalse()
        assertThat(deleted).isFalse()
    }
}
