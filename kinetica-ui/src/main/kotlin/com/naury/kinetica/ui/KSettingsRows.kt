package com.naury.kinetica.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// 설정 화면 행 프리셋. 모두 KListItem 위에 얹은 것이라 KListSection 안에서 구분선과 모서리가 맞는다.

/**
 * 스위치 행. 행 어디를 눌러도 켜고 끄며, TalkBack은 행 전체를 스위치 하나로 읽는다.
 *
 * ```kotlin
 * KSwitchRow("블루투스", bluetooth, { bluetooth = it }, leading = KListLeading.Icon(Icons.Bluetooth, container = Color(0xFF0A84FF)))
 * ```
 *
 * @param subtitle 제목 아래 설명. 두 줄까지 보인다.
 * @param leading 앞쪽 아이콘이나 아바타
 * @param variant 스위치 모양
 */
@Composable
public fun KSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: KListLeading? = null,
    enabled: Boolean = true,
    variant: KSwitchVariant = KSwitchVariant.Capsule,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    KListItem(
        title,
        modifier,
        supporting = subtitle,
        leading = leading,
        trailing = KListTrailing.Switch(checked, onCheckedChange, variant),
        enabled = enabled,
        supportingMaxLines = SUBTITLE_LINES,
        surface = surface,
    )
}

/**
 * 체크박스 행. 행 어디를 눌러도 체크가 바뀌고, TalkBack은 행 전체를 체크박스 하나로 읽는다.
 *
 * @param shape 체크박스 모양
 */
@Composable
public fun KCheckboxRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: KListLeading? = null,
    enabled: Boolean = true,
    shape: KCheckboxShape = KCheckboxShape.Square,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    KListItem(
        title,
        modifier,
        supporting = subtitle,
        leading = leading,
        trailing = KListTrailing.Checkbox(checked, onCheckedChange, shape),
        enabled = enabled,
        supportingMaxLines = SUBTITLE_LINES,
        surface = surface,
    )
}

/**
 * 라디오 행. 하나만 고르는 설정(화면 모드, 글자 크기 등)을 [KListSection]에 나열할 때 쓴다.
 * 섹션에 `Modifier.selectableGroup()`을 붙이면 TalkBack이 묶음으로 읽는다.
 */
@Composable
public fun KRadioRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: KListLeading? = null,
    enabled: Boolean = true,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    KListItem(
        title,
        modifier,
        supporting = subtitle,
        leading = leading,
        trailing = KListTrailing.Radio(selected, onSelect),
        enabled = enabled,
        supportingMaxLines = SUBTITLE_LINES,
        surface = surface,
    )
}

/**
 * 값 행. 제목과 현재 값을 나란히 보여 준다. 버전, 저장 공간처럼 읽기만 하는 정보나 눌러서 바꾸는 값에 쓴다.
 *
 * @param onClick 눌렀을 때. `null`이면 누를 수 없는 행이다.
 */
@Composable
public fun KValueRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: KListLeading? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    KListItem(
        title,
        modifier,
        supporting = subtitle,
        leading = leading,
        trailing = KListTrailing.Value(value),
        onClick = onClick,
        enabled = enabled,
        supportingMaxLines = SUBTITLE_LINES,
        surface = surface,
    )
}

/**
 * 다음 화면으로 넘어가는 행. 끝에 꺾쇠를 그리고, [value]가 있으면 그 앞에 지금 값을 옅게 쓴다.
 *
 * @param value 꺾쇠 앞 현재 값. 예: Wi-Fi 이름, "한국어"
 */
@Composable
public fun KDisclosureRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    subtitle: String? = null,
    leading: KListLeading? = null,
    enabled: Boolean = true,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    KListItem(
        title,
        modifier,
        supporting = subtitle,
        leading = leading,
        trailing = KListTrailing.Disclosure(value),
        onClick = onClick,
        enabled = enabled,
        supportingMaxLines = SUBTITLE_LINES,
        surface = surface,
    )
}

private const val SUBTITLE_LINES = 2
