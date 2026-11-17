package com.naury.kinetica.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * [KNotificationStack]에 넣는 알림 하나.
 *
 * @property id 알림을 가리키는 값. 묶음 안에서 겹치면 안 된다. 밀어 지우는 중인 위치 같은 카드 상태가 이 값에 묶인다.
 * @property title 굵게 보이는 첫 줄. 보통 보낸 사람이나 주문 이름이다.
 * @property text 본문. 세 줄까지 보이고 넘치면 말줄임표로 끝난다.
 * @property time 오른쪽 위에 놓는 시각 글. 예: "지금", "5분 전"
 */
@Immutable
public class KNotification(public val id: String, public val title: String, public val text: String, public val time: String)

/**
 * [KNotificationStack]이 펼쳐졌는지. 화면을 회전해도 유지된다. 알림이 하나 이하로 줄면 묶음이 저절로 접힌다.
 */
@Stable
public class KNotificationStackState internal constructor(initialExpanded: Boolean) {
    /** 묶음을 펼쳐 알림을 목록으로 보이는 중이면 true */
    public var expanded: Boolean by mutableStateOf(initialExpanded)
        private set

    /** 묶음을 펼친다. 알림이 하나 이하면 다음 구성에서 다시 접힌다. */
    public fun expand() {
        expanded = true
    }

    /** 묶음을 다시 겹쳐 쌓는다. */
    public fun collapse() {
        expanded = false
    }

    internal companion object {
        val Saver: Saver<KNotificationStackState, Boolean> = Saver(save = { it.expanded }, restore = { KNotificationStackState(it) })
    }
}

/** [KNotificationStack]의 펼침 상태를 만든다. [expanded]는 처음 값이다. */
@Composable
public fun rememberKNotificationStackState(expanded: Boolean = false): KNotificationStackState =
    rememberSaveable(saver = KNotificationStackState.Saver) { KNotificationStackState(expanded) }
