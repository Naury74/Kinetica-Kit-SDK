// 파일 이름은 대표 컴포저블인 KWizard를 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * [KWizard]의 지금 단계. [rememberKWizardState]로 만들며 화면 회전 뒤에도 유지된다.
 *
 * @property stepCount 단계 수
 */
@Stable
public class KWizardState(public val stepCount: Int, initialStep: Int = 0) {
    init {
        require(stepCount >= 1) { "stepCount는 1 이상이어야 합니다." }
    }

    /** 지금 단계 번호(0부터) */
    public var current: Int by mutableIntStateOf(initialStep.coerceIn(0, stepCount - 1))
        private set

    internal var forward: Boolean = true
        private set

    /** 첫 단계가 아닌지 */
    public val canGoBack: Boolean get() = current > 0

    /** 마지막 단계인지 */
    public val isLast: Boolean get() = current == stepCount - 1

    /** 다음 단계로 간다. 마지막 단계면 아무것도 하지 않고 false를 돌려준다. */
    public fun next(): Boolean = goTo(current + 1)

    /** 앞 단계로 간다. 첫 단계면 false를 돌려준다. */
    public fun back(): Boolean = goTo(current - 1)

    /** [step]으로 간다. 범위 밖이면 false를 돌려준다. */
    public fun goTo(step: Int): Boolean {
        if (step !in 0 until stepCount || step == current) return false
        forward = step > current
        current = step
        return true
    }

    internal companion object {
        val Saver: Saver<KWizardState, IntArray> = Saver({ intArrayOf(it.stepCount, it.current) }, { KWizardState(it[0], it[1]) })
    }
}

/** [stepCount]단계 마법사 상태를 만든다. 1 미만이면 [IllegalArgumentException]을 던진다. */
@Composable
public fun rememberKWizardState(stepCount: Int, initialStep: Int = 0): KWizardState =
    rememberSaveable(stepCount, saver = KWizardState.Saver) { KWizardState(stepCount, initialStep) }

/**
 * 단계 마법사. 위에 [KStepIndicator], 가운데 지금 단계 내용, 아래에 '이전'과 '다음'(마지막은 '완료') 버튼을 놓는다.
 * 단계가 바뀌면 내용이 진행 방향으로 밀려나고 들어온다(RTL에서는 반대). '애니메이션 삭제' 설정에서는 옅어지기만 한다.
 * [handleBack]이면 첫 단계가 아닐 때 시스템 뒤로 가기가 앞 단계로 돌아간다.
 *
 * ```kotlin
 * val wizard = rememberKWizardState(3)
 * KWizard(wizard, listOf("계정", "프로필", "확인"), onFinish = ::submit, nextEnabled = valid) { step -> SignUpStep(step) }
 * ```
 *
 * @param steps 단계 이름. 크기가 [KWizardState.stepCount]와 다르면 [IllegalArgumentException]을 던진다.
 * @param onFinish 마지막 단계에서 '완료'를 눌렀을 때
 * @param nextEnabled 지금 단계를 넘어갈 수 있는지. 입력 검증이 끝나지 않았으면 false
 * @param indicator 단계 표시 모양
 */
@Composable
public fun KWizard(
    state: KWizardState,
    steps: List<String>,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    nextEnabled: Boolean = true,
    indicator: KStepStyle = KStepStyle.Numbered,
    handleBack: Boolean = true,
    surface: SurfaceStyle = KineticaTheme.surface,
    content: @Composable (step: Int) -> Unit,
) {
    require(steps.size == state.stepCount) { "steps 수는 stepCount와 같아야 합니다." }
    BackHandler(handleBack && state.canGoBack) { state.back() }
    val still = reducedMotion()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        KStepIndicator(steps, state.current, Modifier.fillMaxWidth(), style = indicator, onStepClick = {
            state.goTo(it)
        }, surface = surface)
        AnimatedContent(
            targetState = state.current,
            transitionSpec = {
                if (still) {
                    fadeIn() togetherWith fadeOut()
                } else {
                    val sign = (if (state.forward) 1 else -1) * (if (rtl) -1 else 1)
                    (slideInHorizontally { it / SLIDE_PART * sign } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / SLIDE_PART * sign } + fadeOut())
                }
            },
            label = "wizard-step",
        ) { step -> Box(Modifier.fillMaxWidth()) { content(step) } }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.canGoBack) {
                KButton(
                    stringResource(R.string.kinetica_ui_wizard_back),
                    { state.back() },
                    Modifier.weight(1f),
                    variant = KVariant.Tonal,
                    tone = KTone.Neutral,
                    surface = surface,
                )
            }
            KButton(
                stringResource(if (state.isLast) R.string.kinetica_ui_wizard_finish else R.string.kinetica_ui_wizard_next),
                { if (state.isLast) onFinish() else state.next() },
                Modifier.weight(1f),
                enabled = nextEnabled,
                surface = surface,
            )
        }
    }
}

private const val SLIDE_PART = 3
