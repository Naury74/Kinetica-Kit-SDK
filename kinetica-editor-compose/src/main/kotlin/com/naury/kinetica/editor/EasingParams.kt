package com.naury.kinetica.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.naury.kinetica.core.animation.EasingSpec
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 스프링과 계단 easing의 값을 슬라이더로 바꾼다. 베지어와 선형에는 아무것도 보여 주지 않는다.
 *
 * 끄는 동안은 [onDraft]로 그래프만 바꾸고, 손을 뗄 때 [onCommit]을 한 번 불러 실행 취소 한 단계로 남긴다.
 */
@Composable
internal fun EasingParams(easing: EasingSpec, theme: StudioTheme, onDraft: (EasingSpec) -> Unit, onCommit: () -> Unit) {
    when (easing) {
        is EasingSpec.Spring -> Column {
            ParamSlider(
                ParamSpec(R.string.kinetica_easing_damping, DAMPING_MIN..DAMPING_MAX),
                easing.dampingRatio.toFloat(),
                String.format(Locale.ROOT, "%.2f", easing.dampingRatio),
                theme,
                onCommit,
            ) { onDraft(easing.copy(dampingRatio = roundDamping(it))) }
            ParamSlider(
                ParamSpec(R.string.kinetica_easing_stiffness, STIFFNESS_MIN..STIFFNESS_MAX),
                easing.stiffness.toFloat(),
                easing.stiffness.roundToInt().toString(),
                theme,
                onCommit,
            ) { onDraft(easing.copy(stiffness = it.roundToInt().toDouble())) }
        }

        is EasingSpec.Steps -> ParamSlider(
            ParamSpec(R.string.kinetica_easing_step_count, STEPS_MIN.toFloat()..STEPS_MAX.toFloat(), STEPS_MAX - STEPS_MIN - 1),
            easing.count.toFloat(),
            easing.count.toString(),
            theme,
            onCommit,
        ) { onDraft(EasingSpec.Steps(it.roundToInt().coerceIn(STEPS_MIN, STEPS_MAX))) }

        else -> Unit
    }
}

/** 슬라이더 하나의 이름, 범위, 중간 눈금 수. */
private class ParamSpec(val label: Int, val range: ClosedFloatingPointRange<Float>, val steps: Int = 0)

@Composable
private fun ParamSlider(
    spec: ParamSpec,
    value: Float,
    valueText: String,
    theme: StudioTheme,
    onCommit: () -> Unit,
    onValue: (Float) -> Unit,
) {
    val label = stringResource(spec.label)
    Column(Modifier.fillMaxWidth()) {
        Row {
            Text(label, color = theme.onPanel, modifier = Modifier.weight(1f))
            Text(valueText, color = theme.onPanel)
        }
        // TalkBack이 백분율 대신 실제 값을 읽도록 상태 설명을 값으로 둔다.
        Slider(
            value = value.coerceIn(spec.range),
            onValueChange = onValue,
            valueRange = spec.range,
            steps = spec.steps,
            onValueChangeFinished = onCommit,
            colors = SliderDefaults.colors(thumbColor = theme.accent, activeTrackColor = theme.accent),
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = valueText
            },
        )
    }
}

private fun roundDamping(value: Float): Double = (value * DAMPING_ROUNDING).roundToInt() / DAMPING_ROUNDING

// 감쇠비 0은 영원히 흔들리므로 아래 끝을 조금 띄운다. 강성 상한은 1초 구간에서 흔들림이 보일 만큼으로 잡았다.
private const val DAMPING_MIN = 0.05f
private const val DAMPING_MAX = 2f
private const val DAMPING_ROUNDING = 100.0
private const val STIFFNESS_MIN = 10f
private const val STIFFNESS_MAX = 1_500f
private const val STEPS_MIN = 1
private const val STEPS_MAX = 20
