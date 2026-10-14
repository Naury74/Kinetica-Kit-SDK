// 샘플 화면의 크기·색은 디자인 값이라 이름 붙인 상수로 빼지 않는다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.boardsample

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.naury.kinetica.presets.EffectPresets
import com.naury.kinetica.render.ContentTransitions
import com.naury.kinetica.render.ViewEffects
import com.naury.kinetica.render.animateNumberAsState
import com.naury.kinetica.render.kineticaEffects
import kotlin.math.roundToLong

/**
 * 호스트의 평범한 뷰에 미리 만든 움직임 효과를 붙이는 예. 위쪽은 Compose(`Modifier.kineticaEffects`),
 * 아래쪽은 XML View(`ViewEffects.apply`)다. 효과는 클릭을 가로채지 않으므로 숫자가 올라간다.
 */
class EffectsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { EffectsScreen() } }
    }
}

@Composable
private fun EffectsScreen() {
    var taps by remember { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF3F4F8)).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Compose 뷰 · 눌러 보세요 (눌린 수 $taps)", fontSize = 15.sp, color = Color(0xFF6B7280))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EffectsTile("button", "리플 + 눌림", Color(0xFF5B6CFF), EffectsShape.Pill) { taps++ }
            EffectsTile("card", "기울기 + 올라오기", Color.White, EffectsShape.Card) { taps++ }
            EffectsTile("callToAction", "강조(빛 번짐)", Color(0xFFFF7A59), EffectsShape.Pill) { taps++ }
            EffectsTile("badge", "배지(두근)", Color(0xFFE5484D), EffectsShape.Circle) { taps++ }
            EffectsTile("skeleton", "스켈레톤(반짝임)", Color(0xFFE5E7EB), EffectsShape.Card) { taps++ }
            EffectsTile("attention", "주의(흔들기)", Color(0xFFFFD166), EffectsShape.Card) { taps++ }
            EffectsTile("floating", "둥실", Color(0xFF34C3A0), EffectsShape.Circle) { taps++ }
            EffectsTile("like", "좋아요(터지기)", Color(0xFFFF5C8A), EffectsShape.Circle) { taps++ }
            EffectsTile("liftCard", "떠오르기 + 흐림 걷힘", Color.White, EffectsShape.Card) { taps++ }
            EffectsTile("heartbeat", "콩닥", Color(0xFFE5484D), EffectsShape.Circle) { taps++ }
            EffectsTile("live", "실시간(퍼지는 고리)", Color(0xFF22C55E), EffectsShape.Circle) { taps++ }
        }
        TransitionsDemo()
        CountUpDemo(taps)
        repeat(4) { index ->
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp)).background(Color.White)
                    .kineticaEffects(EffectPresets.listItem(index), RoundedCornerShape(14.dp))
                    .clickable { taps++ }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) { Text("목록 항목 ${index + 1} (차례로 등장)") }
        }
        XmlEffectsDemo { taps++ }
    }
}

/** XML View에 ViewEffects로 붙인 효과들. */
@Composable
private fun XmlEffectsDemo(onTap: () -> Unit) {
    Text("XML View · ViewEffects.apply", fontSize = 15.sp, color = Color(0xFF6B7280))
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                text = "XML 버튼 (리플 + 눌림, 둥근 모서리)"
                gravity = Gravity.CENTER
                setTextColor(android.graphics.Color.WHITE)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFF5B6CFF.toInt())
                    cornerRadius = 16f * resources.displayMetrics.density
                }
                setOnClickListener { onTap() }
                ViewEffects.apply(this, EffectPresets.button, cornerRadiusDp = 16f)
            }
        },
        modifier = Modifier.fillMaxWidth().height(56.dp),
    )
    AndroidView(
        factory = { context ->
            likeAndLive(context, onTap)
        },
        modifier = Modifier.fillMaxWidth().height(96.dp),
    )
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                text = "XML 스켈레톤 (반짝임)"
                gravity = Gravity.CENTER
                setBackgroundColor(0xFFE5E7EB.toInt())
                ViewEffects.apply(this, EffectPresets.skeleton)
            }
        },
        modifier = Modifier.fillMaxWidth().height(56.dp),
    )
}

/** 터지기·퍼지는 고리는 부모 overlay에 그리므로 여백 있는 부모에 넣는다. */
private fun likeAndLive(context: android.content.Context, onTap: () -> Unit): View = android.widget.LinearLayout(context).apply {
    val density = resources.displayMetrics.density
    setPadding((24 * density).toInt(), (24 * density).toInt(), (24 * density).toInt(), (24 * density).toInt())
    gravity = Gravity.CENTER_VERTICAL
    addView(
        TextView(context).apply {
            text = "XML 좋아요"
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFFFF5C8A.toInt())
                cornerRadius = 24f * density
            }
            setOnClickListener { onTap() }
            ViewEffects.apply(this, EffectPresets.like, cornerRadiusDp = 24f)
        },
        android.widget.LinearLayout.LayoutParams((140 * density).toInt(), (48 * density).toInt()),
    )
    addView(
        View(context).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0xFF22C55E.toInt())
            }
            ViewEffects.apply(this, EffectPresets.live, cornerRadiusDp = 8f)
        },
        android.widget.LinearLayout.LayoutParams((16 * density).toInt(), (16 * density).toInt()).apply {
            marginStart = (48 * density).toInt()
        },
    )
}

/** 탭을 바꾸면 내용이 가로로 밀리며 바뀐다(ContentTransitions.sharedAxisX). */
@Composable
private fun TransitionsDemo() {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("오늘", "이번 주", "이번 달")
    Text("화면 전환 · ContentTransitions", fontSize = 15.sp, color = Color(0xFF6B7280))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tabs.forEachIndexed { index, label ->
            Box(
                Modifier.clip(RoundedCornerShape(20.dp))
                    .background(if (index == tab) Color(0xFF5B6CFF) else Color.White)
                    .clickable { tab = index }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text(label, color = if (index == tab) Color.White else Color(0xFF1A1D24)) }
        }
    }
    AnimatedContent(
        tab,
        transitionSpec = { ContentTransitions.sharedAxisX(forward = targetState > initialState) },
        label = "tabs",
    ) { page ->
        Box(
            Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp),
            contentAlignment = Alignment.CenterStart,
        ) { Text("${tabs[page]} 매출 요약", fontSize = 17.sp) }
    }
}

/** 눌린 수에 맞춰 금액이 올라간다(animateNumberAsState). */
@Composable
private fun CountUpDemo(taps: Int) {
    val amount by animateNumberAsState(taps * 12_500.0)
    Text("숫자 올라가기 · animateNumberAsState", fontSize = 15.sp, color = Color(0xFF6B7280))
    Text("%,d원".format(amount.roundToLong()), fontSize = 28.sp, color = Color(0xFF1A1D24))
}

private enum class EffectsShape(val shape: androidx.compose.ui.graphics.Shape) {
    Pill(RoundedCornerShape(28.dp)),
    Card(RoundedCornerShape(16.dp)),
    Circle(RoundedCornerShape(50)),
}

@Composable
private fun EffectsTile(preset: String, label: String, color: Color, shape: EffectsShape, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(width = 150.dp, height = 72.dp)
                .kineticaEffects(EffectPresets.all.getValue(preset), shape.shape)
                .clip(shape.shape)
                .background(color)
                .clickable(onClick = onClick),
        )
        Text(label, fontSize = 13.sp)
    }
}
