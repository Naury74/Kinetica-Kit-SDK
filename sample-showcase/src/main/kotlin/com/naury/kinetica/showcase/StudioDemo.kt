// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.naury.kinetica.core.scene.DataValue
import com.naury.kinetica.core.scene.SceneData
import com.naury.kinetica.editor.KineticaEditor
import com.naury.kinetica.editor.StudioTheme
import com.naury.kinetica.history.KineticaSession
import com.naury.kinetica.history.SessionOpenResult
import com.naury.kinetica.player.KineticaPlayer
import com.naury.kinetica.player.SceneFit
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * 화면 만들기: 운영자가 앱 안에서 화면을 디자인한다(도형·글자·자동 배치·컴포넌트·움직임·호스트 위젯·데이터 연결).
 * "미리보기"로 실제 사용자 화면을 본다. 만든 문서는 서버 화면(JSON)으로 내려보낼 수 있다.
 */
@Composable
internal fun StudioDemo(onBack: () -> Unit, model: StudioDemoModel = viewModel()) {
    var previewing by rememberSaveable { mutableStateOf(false) }
    var sales by remember { mutableStateOf(List(7) { Random.nextFloat() }) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            sales = sales.drop(1) + Random.nextFloat()
        }
    }
    val slots = remember(sales) { HostWidgets.slots(sales) }
    val data = SceneData.of(
        DashboardScene.SALES_TODAY to DataValue.Number(1_284_000.0),
        DashboardScene.ORDER_COUNT to DataValue.Number(42.0),
        DashboardScene.STORE_OPEN to DataValue.Bool(true),
        DashboardScene.GREETING to DataValue.Text("안녕하세요, 사장님"),
    )
    val theme = StudioTheme.system()
    Column(Modifier.fillMaxSize().background(theme.canvasBackground).statusBarsPadding().navigationBarsPadding()) {
        if (previewing) {
            val document by model.session.document.collectAsStateWithLifecycle()
            TextButton(onClick = { previewing = false }) { Text("‹ 편집으로 돌아가기", color = theme.accent) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                KineticaPlayer(document, Modifier.fillMaxSize(), data = data, slots = slots, fit = SceneFit.Responsive)
            }
        } else {
            KineticaEditor(
                model.session,
                Modifier.weight(1f).fillMaxWidth(),
                theme = theme,
                slots = slots,
                hostViews = HostWidgets.options,
                data = data,
                toolbarEnd = {
                    TextButton(onClick = onBack) { Text("나가기", color = theme.accent) }
                    TextButton(onClick = { previewing = true }) { Text("미리보기", color = theme.accent) }
                },
            )
        }
    }
}

/** 편집 세션은 화면보다 오래 살아 화면을 돌려도 실행 취소 기록이 남는다. */
internal class StudioDemoModel : ViewModel() {
    val session: KineticaSession = (KineticaSession.create(DashboardScene.create()) as SessionOpenResult.Opened).session

    override fun onCleared() = session.close()
}
