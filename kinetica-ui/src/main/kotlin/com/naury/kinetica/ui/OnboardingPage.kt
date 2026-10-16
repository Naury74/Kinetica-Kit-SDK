package com.naury.kinetica.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * [KOnboarding]의 한 페이지.
 *
 * @property title 큰 제목. TalkBack에서 제목으로 읽힌다.
 * @property body 제목 아래 설명
 * @property background 이 페이지의 배경색. 페이지를 넘기는 동안 다음 페이지 색과 섞인다. 지정하지 않으면 기본 색을 차례로 쓴다.
 * @property art 위쪽에 크게 놓을 그림. 넘길 때 페이지보다 느리게 움직이며 작아진다.
 */
@Immutable
public class OnboardingPage(
    public val title: String,
    public val body: String,
    public val background: Color = Color.Unspecified,
    public val art: @Composable () -> Unit = {},
)
