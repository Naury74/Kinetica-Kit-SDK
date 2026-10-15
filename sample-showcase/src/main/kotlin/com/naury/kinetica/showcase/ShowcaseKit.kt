// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naury.kinetica.ui.GlassSurface
import com.naury.kinetica.ui.LocalGlassContentColor
import com.naury.kinetica.ui.jellyPress

/** 글래스 상단 바. 뒤로 가기 버튼, 제목, 화면을 한 줄로 설명하는 부제를 보여 준다. */
@Composable
internal fun DemoTopBar(title: String, subtitle: String, onBack: () -> Unit) {
    GlassSurface(
        Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = "뒤로" },
                contentAlignment = Alignment.Center,
            ) { Label("‹", 26, FontWeight.Bold) }
            Column(Modifier.padding(start = 6.dp)) {
                Label(title, 17, FontWeight.Bold)
                Label(subtitle, 12, color = LocalGlassContentColor.current.copy(alpha = 0.65f))
            }
        }
    }
}

/** 제목과 설명이 있는 글래스 카드. 시연할 내용은 [content]에 넣는다. */
@Composable
internal fun DemoCard(title: String, description: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label(title, 18, FontWeight.Bold)
            Label(description, 13, color = LocalGlassContentColor.current.copy(alpha = 0.7f))
            content()
        }
    }
}

/** 캡슐 모양 버튼. [filled]면 배경을 채워 그리고, 누르면 젤리처럼 눌린다. */
@Composable
internal fun Pill(text: String, modifier: Modifier = Modifier, filled: Boolean = false, onClick: () -> Unit) {
    val ink = LocalGlassContentColor.current
    if (filled) {
        Box(
            modifier.jellyPress().background(
                ink,
                CircleShape,
            ).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            BasicText(
                text,
                style = TextStyle(
                    color = if (ink.isLight()) Color.Black else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    } else {
        GlassSurface(modifier.jellyPress().clickable(role = Role.Button, onClick = onClick), shape = CircleShape) {
            Box(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) { Label(text, 14, FontWeight.SemiBold) }
        }
    }
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

/** 이 화면이 보이는 동안 상태 표시줄 아이콘을 [darkBackground]면 밝게, 아니면 어둡게 두고, 사라지면 원래대로 돌린다. */
@Composable
internal fun StatusBarIcons(darkBackground: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(darkBackground) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = !darkBackground
        onDispose { if (before != null) controller.isAppearanceLightStatusBars = before }
    }
}
