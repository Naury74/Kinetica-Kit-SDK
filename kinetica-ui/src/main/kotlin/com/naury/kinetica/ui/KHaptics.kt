package com.naury.kinetica.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/** [KHaptics]가 내는 햅틱 종류. 기기가 지원하지 않는 종류는 가장 가까운 진동으로 바꿔 낸다. */
public enum class KHapticType {
    /** 목록이나 휠에서 값 하나를 고를 때. 가장 가벼운 틱이다. */
    Selection,

    /** 슬라이더 눈금이나 색 바퀴처럼 짧은 간격으로 이어지는 틱 */
    Tick,

    /** 저장, 결제, 새로 고침 완료처럼 작업이 끝났을 때 */
    Success,

    /** 되돌릴 수 없는 동작을 고르기 직전이나 입력이 한도에 가까울 때 */
    Warning,

    /** 실패했거나 거절됐을 때 */
    Error,

    /** 끌기나 스크롤이 끝에 닿았을 때, 당겨서 새로 고침이 임계값을 넘었을 때 */
    Edge,

    /** 길게 눌러 편집 모드나 메뉴가 열릴 때 */
    LongPress,
}

/**
 * kinetica-ui 컴포넌트가 쓰는 햅틱을 호스트도 같은 규칙으로 쓰게 한다. [rememberKHaptics]로 얻는다.
 *
 * 시스템 '터치 진동' 설정을 따르며, [LocalKineticaHaptics]가 false인 곳에서는 아무 진동도 내지 않는다.
 * 진동은 화면의 [View]를 거쳐 내므로 진동 권한이 필요 없다.
 *
 * ```kotlin
 * val haptics = rememberKHaptics()
 * KButton("결제", onClick = { pay(); haptics.perform(KHapticType.Success) })
 * ```
 */
@Stable
public class KHaptics internal constructor(private val view: View, private val enabled: Boolean) {
    /** 지금 진동을 낼 수 있는지. [LocalKineticaHaptics]로 껐거나 화면의 햅틱이 꺼져 있으면 false다. */
    public val isEnabled: Boolean get() = enabled && view.isHapticFeedbackEnabled

    /** [type] 햅틱을 낸다. 꺼져 있으면 아무 일도 하지 않고 false를, 냈으면 시스템이 돌려준 값을 돌려준다. */
    public fun perform(type: KHapticType): Boolean {
        if (!isEnabled) return false
        return view.performHapticFeedback(hapticConstant(type, Build.VERSION.SDK_INT))
    }

    /** [KHapticType.Selection] */
    public fun selection(): Boolean = perform(KHapticType.Selection)

    /** [KHapticType.Success] */
    public fun success(): Boolean = perform(KHapticType.Success)

    /** [KHapticType.Warning] */
    public fun warning(): Boolean = perform(KHapticType.Warning)

    /** [KHapticType.Error] */
    public fun error(): Boolean = perform(KHapticType.Error)

    /** [KHapticType.Edge] */
    public fun edge(): Boolean = perform(KHapticType.Edge)
}

/**
 * kinetica-ui 햅틱을 켤지. 기본값은 true다. 앱 설정에 '진동 끄기'가 있으면 화면 루트에서 false를 넣는다.
 * false면 [KHaptics]와 이 키트의 모든 컴포넌트(버튼, 칩, 스테퍼, 휠 피커, 메뉴, 밀어서 동작, 토스트 등)가 진동을 내지 않는다.
 *
 * ```kotlin
 * CompositionLocalProvider(LocalKineticaHaptics provides settings.haptics) { App() }
 * ```
 */
public val LocalKineticaHaptics: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { true }

/** 지금 화면에 붙은 [KHaptics]. [LocalKineticaHaptics]를 따른다. */
@Composable
public fun rememberKHaptics(): KHaptics {
    val view = LocalView.current
    val enabled = LocalKineticaHaptics.current
    return remember(view, enabled) { KHaptics(view, enabled) }
}

/** [type]을 [sdk]에서 쓸 수 있는 시스템 햅틱 상수로 바꾼다. 새 상수가 없는 기기에서는 느낌이 비슷한 옛 상수를 쓴다. */
internal fun hapticConstant(type: KHapticType, sdk: Int): Int = when (type) {
    KHapticType.Selection -> if (sdk >=
        Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    ) {
        HapticFeedbackConstants.SEGMENT_TICK
    } else {
        HapticFeedbackConstants.CLOCK_TICK
    }

    KHapticType.Tick -> if (sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
    } else {
        HapticFeedbackConstants.TEXT_HANDLE_MOVE
    }

    KHapticType.Success -> if (sdk >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK

    KHapticType.Warning, KHapticType.LongPress -> HapticFeedbackConstants.LONG_PRESS

    KHapticType.Error -> if (sdk >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS

    KHapticType.Edge -> if (sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
    } else {
        HapticFeedbackConstants.CLOCK_TICK
    }
}

/**
 * 이 키트의 컴포넌트가 쓰는 [HapticFeedback]. [LocalKineticaHaptics]가 false면 아무 진동도 내지 않는다.
 * 컴포넌트는 [LocalHapticFeedback]을 직접 읽지 않고 이것을 쓴다.
 */
@Composable
internal fun kHapticFeedback(): HapticFeedback {
    val system = LocalHapticFeedback.current
    return if (LocalKineticaHaptics.current) system else SilentHaptics
}

private object SilentHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}
