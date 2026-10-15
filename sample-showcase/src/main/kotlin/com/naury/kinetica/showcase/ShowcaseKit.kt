// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.naury.kinetica.ui.GlassButtonKind
import com.naury.kinetica.ui.GlassIconButton
import com.naury.kinetica.ui.GlassIndication
import com.naury.kinetica.ui.GlassSurface
import com.naury.kinetica.ui.LocalGlassContentColor
import com.naury.kinetica.ui.jellyPress

/** 글래스 상단 바. 뒤로 가기 버튼, 제목, 화면을 한 줄로 설명하는 부제를 보여 준다. 넓은 화면에서는 읽기 폭에 맞춰 가운데 둔다. */
@Composable
internal fun DemoTopBar(title: String, subtitle: String, onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.TopCenter,
    ) {
        // 바깥 28dp 캡슐 안에 6dp 여백으로 44dp 원형 버튼을 넣어 모서리가 같은 중심으로 굽게 한다.
        GlassSurface(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = READING_WIDTH).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
        ) {
            Row(Modifier.padding(6.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(ShowcaseIcons.Back, "뒤로", onBack, kind = GlassButtonKind.Plain, accent = LocalGlassContentColor.current)
                Column(Modifier.padding(start = 6.dp, top = 4.dp, bottom = 4.dp)) {
                    Label(title, 17, FontWeight.Bold)
                    Label(subtitle, 12, color = LocalGlassContentColor.current.copy(alpha = 0.65f))
                }
            }
        }
    }
}

/** 넓은 화면에서 본문이 지나치게 늘어나지 않도록 쓰는 최대 폭 */
internal val READING_WIDTH = 640.dp

/** 목록 가로 여백. 넓은 화면에서는 본문이 [READING_WIDTH]를 넘지 않도록 양옆 여백을 늘린다. */
@Composable
internal fun readingPadding(minimum: Dp = 20.dp): Dp {
    val width = LocalConfiguration.current.screenWidthDp.dp
    return maxOf(minimum, (width - READING_WIDTH) / 2)
}

/** 내비게이션 바 높이. 스크롤 목록 아래 여백에 더한다. */
@Composable
internal fun navigationBarBottom(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** 제목과 설명이 있는 글래스 카드. 시연할 내용은 [content]에 넣는다. */
@Composable
internal fun DemoCard(title: String, description: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(modifier.fillMaxWidth(), shape = CARD_SHAPE) {
        Column(Modifier.padding(CARD_PADDING), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label(title, 18, FontWeight.Bold)
            Label(description, 13, color = LocalGlassContentColor.current.copy(alpha = 0.7f))
            content()
        }
    }
}

/** 데모 카드 바깥 모서리 */
internal val CARD_SHAPE = RoundedCornerShape(32.dp)

/** 데모 카드 안쪽 여백 */
internal val CARD_PADDING = 20.dp

/** 데모 카드 안에 꽉 차게 넣는 이미지나 상자의 모서리. 바깥 32dp에서 여백 20dp를 뺀 값이라 카드 모서리와 같은 중심으로 굽는다. */
internal val INNER_SHAPE = RoundedCornerShape(12.dp)

/** 캡슐 모양 버튼. [filled]면 배경을 채워 그리고, 누르면 젤리처럼 눌리며 캡슐 모양으로 어두워진다. */
@Composable
internal fun Pill(text: String, modifier: Modifier = Modifier, filled: Boolean = false, onClick: () -> Unit) {
    val ink = LocalGlassContentColor.current
    if (filled) {
        val onInk = if (ink.isLight()) Color.Black else Color.White
        Box(
            modifier
                .jellyPress()
                .defaultMinSize(minHeight = 40.dp)
                .background(ink, CircleShape)
                .clip(CircleShape)
                .clickable(null, GlassIndication(CircleShape, onInk), role = Role.Button, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(text, style = TextStyle(color = onInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
        }
    } else {
        GlassSurface(onClick, modifier.jellyPress().defaultMinSize(minHeight = 40.dp), shape = CircleShape) {
            Box(Modifier.align(Alignment.Center).padding(horizontal = 18.dp, vertical = 10.dp)) { Label(text, 14, FontWeight.SemiBold) }
        }
    }
}

/** 쇼케이스 선 아이콘. [size]는 24 격자 아이콘을 그릴 크기다. */
@Composable
internal fun ShowcaseIcon(
    icon: ImageVector,
    size: Dp = 22.dp,
    color: Color = LocalGlassContentColor.current,
    modifier: Modifier = Modifier,
) {
    Image(rememberVectorPainter(icon), null, modifier.size(size), colorFilter = ColorFilter.tint(color))
}

@Composable
internal fun Label(
    text: String,
    size: Int,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    color: Color = LocalGlassContentColor.current,
) {
    BasicText(text, modifier, style = TextStyle(color = color, fontSize = size.sp, fontWeight = weight, lineHeight = (size * 1.4f).sp))
}

internal fun Color.isLight(): Boolean = red * 0.3f + green * 0.59f + blue * 0.11f > 0.5f

/**
 * 이 화면이 보이는 동안 상태 표시줄 아이콘을 [darkBackground]면 밝게, 아니면 어둡게 둔다.
 * 화면이 쌓이는 곳에서는 사라지는 화면이 늦게 정리되며 값을 덮을 수 있으므로, 맨 위 화면 기준 값 하나만 넘긴다.
 */
@Composable
internal fun StatusBarIcons(darkBackground: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkBackground
    }
}
