package com.naury.kinetica.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

/** README.md 사용법의 예가 지금 API로 컴파일되고 동작하는지 확인한다. README 예를 고치면 여기도 함께 고친다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ReadmeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `테마와 표면 예에서 버튼을 누르면 바텀 시트가 열린다`() {
        compose.setContent {
            val dark = isSystemInDarkTheme()
            KineticaTheme(
                // 단색 표면. 글래스는 SurfaceStyles.Liquid, 톤은 SurfaceStyles.tonal(brandColor)
                surface = if (dark) SurfaceStyles.CleanDark else SurfaceStyles.CleanLight,
                colors = KineticaColors(accent = Color(0xFF3B5BDB)),
                design = KineticaDesigns.Material,
            ) {
                var sharing by remember { mutableStateOf(false) }
                KineticaScaffold { padding ->
                    Column(Modifier.padding(padding).padding(24.dp)) {
                        KButton("공유하기", onClick = { sharing = true }, fullWidth = true)
                    }
                    KSheet(sharing, onDismissRequest = { sharing = false }) {
                        KListItem("링크 복사", onClick = { sharing = false })
                        KListItem("메시지로 보내기", onClick = { sharing = false })
                    }
                }
            }
        }

        compose.onNodeWithText("링크 복사").assertDoesNotExist()
        compose.onNodeWithText("공유하기").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("링크 복사").assertExists()
    }

    @Test
    fun `다이얼로그와 토스트 예에서 삭제를 누르면 다이얼로그가 닫히고 토스트가 뜬다`() {
        var open: (() -> Unit)? = null
        compose.setContent {
            val toast = rememberKToastState()
            val scope = rememberCoroutineScope()
            var confirm by remember { mutableStateOf(false) }
            open = { confirm = true }

            KAlertDialog(
                visible = confirm,
                onDismissRequest = { confirm = false },
                title = "사진을 삭제할까요?",
                message = "삭제한 사진은 되돌릴 수 없습니다.",
                actions = listOf(
                    KDialogAction("취소"),
                    KDialogAction("삭제", KDialogActionStyle.Destructive) {
                        scope.launch { toast.show("삭제했어요") }
                    },
                ),
            )
            KToastHost(toast)
        }

        compose.runOnIdle { open!!() }
        compose.onNodeWithText("삭제").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("사진을 삭제할까요?").assertDoesNotExist()
        compose.onNodeWithText("삭제했어요").assertExists()
    }
}
