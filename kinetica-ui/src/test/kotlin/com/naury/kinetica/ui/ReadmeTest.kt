package com.naury.kinetica.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** README.md 빠른 시작의 예가 지금 API로 컴파일되고 동작하는지 확인한다. README 예를 고치면 여기도 함께 고친다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ReadmeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `빠른 시작 예에서 버튼을 누르면 바텀 시트가 열린다`() {
        compose.setContent {
            val dark = isSystemInDarkTheme()
            KineticaTheme(
                // 단색 표면. 글래스는 SurfaceStyles.Liquid, 톤은 SurfaceStyles.tonal(brandColor)
                surface = if (dark) SurfaceStyles.CleanDark else SurfaceStyles.CleanLight,
                colors = KineticaColors(accent = Color(0xFF3B5BDB)),
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
}
