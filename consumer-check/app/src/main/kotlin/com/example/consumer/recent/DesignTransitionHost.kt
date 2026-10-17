package com.example.consumer.recent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.consumer.SampleScene
import com.naury.kinetica.core.scene.SceneTransitionEdge
import com.naury.kinetica.core.scene.SceneTransitionKind
import com.naury.kinetica.core.scene.SceneTransitionSpec
import com.naury.kinetica.player.AnimatedScene
import com.naury.kinetica.player.KineticaPlayer
import com.naury.kinetica.player.SceneTransition
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KCard
import com.naury.kinetica.ui.KNavTransitions
import com.naury.kinetica.ui.KRevealShape
import com.naury.kinetica.ui.KSegmentedControl
import com.naury.kinetica.ui.KSlider
import com.naury.kinetica.ui.KTransitionContent
import com.naury.kinetica.ui.KTransitions
import com.naury.kinetica.ui.KineticaDesigns
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.PushNavigation
import com.naury.kinetica.ui.SurfaceStyles
import com.naury.kinetica.ui.kTransform
import com.naury.kinetica.ui.rememberKNavTransition

/**
 * 시나리오: 디자인 언어, 화면 이동 전환, 장면 전환을 함께 쓰는 호스트 화면.
 * 앱 전체를 Fluent 형태의 단색 표면으로 감싸고, 목록에서 상세는 CardStack, 탭은 TabSlide, 단계는 AnimatedContent와 KNavTransitions,
 * 서버 장면 두 개 사이는 AnimatedScene(앞뒤)과 SceneTransition(진행도 슬라이더)으로 바꾼다.
 */
@Composable
fun DesignTransitionScreen() {
    var design by rememberSaveable { mutableIntStateOf(3) }
    KineticaTheme(surface = SurfaceStyles.CleanLight, design = KineticaDesigns.all[design].second) {
        var stack by rememberSaveable { mutableStateOf(listOf("home")) }
        PushNavigation(stack, onBack = { stack = stack.dropLast(1) }, transition = KTransitions.CardStack) { screen ->
            Column(
                Modifier.fillMaxSize().background(Color.White).padding(16.dp).testTag("screen_$screen"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("디자인 ${KineticaDesigns.all[design].first}", Modifier.testTag("designName"))
                KSegmentedControl(KineticaDesigns.all.map { it.first }.take(3), design.coerceAtMost(2), { design = it })
                when (screen) {
                    "home" -> HomePane { stack = stack + it }
                    else -> ScenePane()
                }
            }
        }
    }
}

@Composable
private fun HomePane(open: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    KSegmentedControl(listOf("오늘", "이번 주"), tab, { tab = it }, Modifier.fillMaxWidth())
    KTransitionContent(tab, Modifier.fillMaxWidth().height(80.dp), transition = KTransitions.TabSlide, isForward = { a, b -> b > a }) { page ->
        KCard(Modifier.fillMaxWidth().testTag("tab_$page")) { Text(if (page == 0) "오늘 할 일" else "이번 주 할 일") }
    }
    val nav = rememberKNavTransition(KNavTransitions.SharedAxisX)
    AnimatedContent(step, transitionSpec = { kTransform(nav, forward = targetState > initialState) }, label = "step") { value ->
        Text("단계 ${value + 1}", Modifier.testTag("step_$value"))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KButton("다음 단계", { step = (step + 1) % 3 }, Modifier.testTag("nextStep"))
        KButton("장면 열기", { open("scene") }, Modifier.testTag("openScene"))
    }
    // 원 모양으로 드러내는 전환과 아래에서 올라오는 덮기는 만들기만 해 공개 API가 컴파일되는지 본다.
    KTransitions.reveal(KRevealShape.Circle) { null }
    KTransitions.cover(com.naury.kinetica.ui.KTransitionEdge.Bottom)
}

@Composable
private fun ScenePane() {
    val first = SampleScene.document
    val second = secondScene(first)
    var showSecond by rememberSaveable { mutableStateOf(false) }
    var progress by rememberSaveable { mutableFloatStateOf(0.5f) }
    val spec = SceneTransitionSpec(SceneTransitionKind.Push, from = SceneTransitionEdge.End, durationMs = 300)
    Box(Modifier.fillMaxWidth().height(160.dp).testTag("animatedScene")) {
        AnimatedScene(if (showSecond) second else first, transition = spec, forward = showSecond) { doc ->
            KineticaPlayer(doc, Modifier.fillMaxSize())
        }
    }
    KButton(if (showSecond) "뒤로" else "다음 장면", { showSecond = !showSecond }, Modifier.testTag("toggleScene"))
    SceneTransition(first, second, spec.copy(kind = SceneTransitionKind.Cube), { progress }, Modifier.fillMaxWidth().height(160.dp).testTag("frame"))
    KSlider(progress, { progress = it }, Modifier.testTag("progress"))
}

/** id만 다른 두 번째 장면. 재생기는 id가 바뀌면 전환한다. */
private fun secondScene(first: com.naury.kinetica.core.scene.SceneDocument) = first.copy(documentId = "second")

/** [DesignTransitionScreen]만 띄우는 화면 */
class DesignTransitionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DesignTransitionScreen() }
    }
}
