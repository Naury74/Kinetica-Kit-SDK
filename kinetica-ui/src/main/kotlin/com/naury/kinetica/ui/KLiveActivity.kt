package com.naury.kinetica.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** [KLiveActivityHost]를 화면 어디에 둘지. */
public enum class KLiveActivityPosition {
    /** 화면 위 가운데, 카메라 홀 자리. 다이내믹 아일랜드처럼 가운데를 비운 좁은 캡슐이다. */
    Top,

    /** 화면 아래 가운데에 뜨는 넓은 캡슐. One UI의 Now Bar처럼 이름과 상태 글자를 함께 보여 준다. */
    Bottom,
}

/**
 * 오래 떠 있는 활동 하나(배달 진행, 타이머, 녹음 중, 통화 중, 운동 등). [KLiveActivityState.start]로 띄우고
 * 같은 [id]로 [KLiveActivityState.update]를 불러 내용을 바꾼다.
 *
 * 슬롯은 모양마다 다르게 쓴다. compact 캡슐은 [leading]과 [trailing]을 양 끝에 두고, 활동이 둘이면 뒤 활동은 [minimal] 원 하나로 떨어져 나온다.
 * 누르거나 길게 누르면 [expanded] 내용이 보이는 큰 화면으로 모양이 이어서 바뀐다. 슬롯 안 글자와 아이콘 색은 표면 위 글자 색을 따른다.
 *
 * 남은 시간처럼 매초 바뀌는 값은 이 객체를 매초 바꾸지 말고 [KCountdownPill]이나 [KTimePill]처럼 스스로 갱신하는 부품을 슬롯에 넣는다.
 * 매 프레임 바뀌는 진행은 [progress] 람다로 넘기면 그리기 단계에서만 읽는다.
 *
 * @property id 활동을 가리키는 키. 같은 상태 안에서 겹치지 않아야 한다.
 * @property title 활동 이름. TalkBack이 [status]와 함께 읽고, [KLiveActivityPosition.Bottom]의 compact 캡슐에 작게 보인다.
 * @property status 지금 상태를 한 줄로(예: '도착까지 12분'). TalkBack이 읽고, [KLiveActivityPosition.Bottom]의 compact 캡슐에 보인다.
 * @property leading compact 캡슐 시작 쪽 슬롯. 24dp(위 위치)나 32dp(아래 위치) 높이에 맞춘다.
 * @property trailing compact 캡슐 끝 쪽 슬롯. 시간 캡슐이나 짧은 글자를 넣는다.
 * @property expanded 펼친 화면의 내용. 너비는 최대 420dp이고 높이는 내용만큼이다. TalkBack은 이 안의 글자를 그대로 읽으므로
 *   활동 이름과 상태를 글자로 함께 둔다.
 * @property minimal 활동이 둘일 때 떨어져 나온 원 안의 내용. null이면 [leading]을 쓴다.
 * @property progress 진행(0..1). minimal 원 둘레의 링과 아래 위치 compact 캡슐의 가는 막대로 그린다. null이면 그리지 않는다.
 * @property accent 링과 막대 색. 지정하지 않으면 테마 강조색이다.
 */
@Stable
public data class KLiveActivity(
    val id: String,
    val title: String,
    val status: String,
    val leading: @Composable () -> Unit,
    val trailing: @Composable () -> Unit,
    val expanded: @Composable () -> Unit,
    val minimal: (@Composable () -> Unit)? = null,
    val progress: (() -> Float)? = null,
    val accent: Color = Color.Unspecified,
) {
    internal val spoken: String get() = if (status.isEmpty()) title else "$title, $status"
}

/**
 * [KLiveActivityHost]에 띄울 활동 목록과 펼친 활동. 먼저 시작한 활동이 큰 캡슐이 되고, 두 번째 활동은 minimal 원으로 옆에 붙는다.
 * 셋째부터는 앞 활동이 끝날 때까지 보이지 않는다. 메인 스레드에서 쓴다.
 *
 * 같은 상태를 여러 호스트가 그리면 가장 나중에 화면에 들어온 호스트 하나만 그린다. 그 호스트가 사라지면 앞의 호스트가 다시 그린다.
 */
@Stable
public class KLiveActivityState {
    private val items = mutableStateListOf<KLiveActivity>()

    /** 떠 있는 활동. 앞에 있을수록 먼저 보인다. */
    public val activities: List<KLiveActivity> get() = items

    /** 펼친 활동의 [KLiveActivity.id]. 펼친 활동이 없으면 null. */
    public var expandedId: String? by mutableStateOf(null)
        private set

    private val hosts = mutableStateListOf<Any>()

    internal fun attach(host: Any) {
        hosts.add(host)
    }

    internal fun detach(host: Any) {
        hosts.remove(host)
    }

    internal fun isDrawnBy(host: Any): Boolean = hosts.lastOrNull() === host

    /** [activity]를 띄운다. 같은 [KLiveActivity.id]가 이미 있으면 그 자리에서 내용만 바꾼다. */
    public fun start(activity: KLiveActivity) {
        val index = items.indexOfFirst { it.id == activity.id }
        if (index >= 0) items[index] = activity else items += activity
    }

    /**
     * 떠 있는 같은 [KLiveActivity.id]의 활동 내용을 바꾼다. 순서와 펼침 상태는 그대로 둔다.
     *
     * @return 바꿨으면 true, 그 id의 활동이 없으면 false
     */
    public fun update(activity: KLiveActivity): Boolean {
        val index = items.indexOfFirst { it.id == activity.id }
        if (index < 0) return false
        items[index] = activity
        return true
    }

    /**
     * [id]의 활동을 끝내고 내린다. 펼쳐져 있었으면 접는다.
     *
     * @return 내렸으면 true, 그 id의 활동이 없으면 false
     */
    public fun end(id: String): Boolean {
        if (expandedId == id) expandedId = null
        return items.removeAll { it.id == id }
    }

    /**
     * [id]의 활동을 펼친다. 다른 활동이 펼쳐져 있으면 그 활동은 접힌다.
     *
     * @return 펼쳤으면 true, 그 id의 활동이 없으면 false
     */
    public fun expand(id: String): Boolean {
        if (items.none { it.id == id }) return false
        expandedId = id
        return true
    }

    /** 펼친 활동을 접는다. */
    public fun collapse() {
        expandedId = null
    }

    /**
     * [id]의 활동을 맨 앞으로 옮겨 큰 캡슐로 보이게 한다.
     *
     * @return 옮겼으면 true, 그 id의 활동이 없으면 false
     */
    public fun promote(id: String): Boolean {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return false
        if (index > 0) items.add(0, items.removeAt(index))
        return true
    }

    internal fun find(id: String?): KLiveActivity? = id?.let { key -> items.firstOrNull { it.id == key } }
}

/** 화면이 다시 구성되어도 같은 [KLiveActivityState]를 돌려준다. 설정 변경(회전 등)에서는 활동 목록을 지키지 않는다. */
@Composable
public fun rememberKLiveActivityState(): KLiveActivityState = remember { KLiveActivityState() }
