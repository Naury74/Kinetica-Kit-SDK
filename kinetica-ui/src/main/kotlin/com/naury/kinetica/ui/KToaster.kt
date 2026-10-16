package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException

/** [KToaster]가 토스트를 쌓는 가장자리. */
public enum class KToastPosition {
    /** 상태 표시줄 아래(스캐폴드에 상단 바가 있으면 바 아래). 새 토스트가 맨 위에 온다. */
    Top,

    /** 내비게이션 바와 키보드 위(스캐폴드에 하단 바가 있으면 바 위). 새 토스트가 맨 아래에 온다. */
    Bottom,
}

/**
 * 토스트 안의 글자 버튼.
 *
 * @property label 버튼 글자. 짧게("되돌리기", "보기") 쓴다.
 * @property onClick 누르면 부른다. 누르면 토스트도 닫힌다.
 */
@Immutable
public class KToastAction(public val label: String, public val onClick: () -> Unit)

/** 토스트 하나. 모든 값은 [KToasterState.update]로 바뀔 수 있어 상태로 둔다. */
@Stable
internal class ToastEntry(val id: Long) {
    var message by mutableStateOf("")
    var description by mutableStateOf<String?>(null)
    var status by mutableStateOf(KStatus.Neutral)
    var loading by mutableStateOf(false)
    var action by mutableStateOf<KToastAction?>(null)
    var durationMs by mutableLongStateOf(DEFAULT_TOAST_DURATION_MS)

    /** 내용이 바뀔 때마다 늘어난다. 바뀌면 사라지기 타이머를 처음부터 다시 잰다. */
    var version by mutableIntStateOf(0)

    /** 닫히는 중. 사라지는 애니메이션이 끝나면 목록에서 빠진다. */
    var leaving by mutableStateOf(false)

    /** 0이면 숨음, 1이면 다 나타남. 쌓인 순서와 높이 계산에도 쓴다. */
    val presence = Animatable(0f)
}

/**
 * [KToaster]에 띄우는 토스트 목록. 여러 개를 띄우면 겹쳐 쌓이고, 오래된 것부터 저절로 사라진다.
 * 메서드는 메인 스레드에서 부른다. 각 메서드가 돌려주는 id로 나중에 내용을 바꾸거나 닫는다.
 *
 * ```kotlin
 * val toaster = rememberKToasterState()
 * toaster.show("저장했어요", KStatus.Success)
 * scope.launch { toaster.promise("올리는 중", success = { "올렸어요" }, error = { "올리지 못했어요" }) { upload() } }
 * ```
 */
@Stable
public class KToasterState {
    internal val entries = mutableStateListOf<ToastEntry>()
    private var nextId = 1L

    /** 겹친 토스트를 펼쳐 모두 보여 주는 중인지. 펼친 동안에는 사라지기 타이머가 멈춘다. */
    public var expanded: Boolean by mutableStateOf(false)

    /** 지금 떠 있는(닫히는 중이 아닌) 토스트 문구. 오래된 것부터 새것 순서다. */
    public val messages: List<String> get() = entries.filter { !it.leaving }.map { it.message }

    /**
     * 토스트를 띄우고 id를 돌려준다.
     *
     * @param status 종류. 아이콘과 색, TalkBack 알림 우선순위를 정한다. [KStatus.Error]는 읽던 것을 끊고 바로 읽힌다.
     * @param description 문구 아래 작은 글자
     * @param durationMs 표시 시간(ms). 접근성 설정의 '조치를 취할 시간'이 더 길면 그 값을 따른다. 0 이하면 [IllegalArgumentException]을 던진다.
     */
    public fun show(
        message: String,
        status: KStatus = KStatus.Neutral,
        description: String? = null,
        action: KToastAction? = null,
        durationMs: Long = DEFAULT_TOAST_DURATION_MS,
    ): Long {
        require(durationMs > 0) { "durationMs는 0보다 커야 합니다." }
        return add { entry ->
            entry.message = message
            entry.status = status
            entry.description = description
            entry.action = action
            entry.durationMs = durationMs
        }
    }

    /** 돌아가는 로딩 아이콘이 붙은 토스트를 띄운다. 저절로 사라지지 않으므로 [update]로 결과를 알리거나 [dismiss]로 닫는다. */
    public fun loading(message: String, description: String? = null): Long = add { entry ->
        entry.message = message
        entry.description = description
        entry.loading = true
    }

    /**
     * 떠 있는 토스트 [id]의 내용을 바꾼다. 로딩 토스트였다면 로딩이 끝나고, 그때부터 [durationMs] 뒤에 사라진다.
     * 이미 닫혔거나 없는 id면 아무 일도 하지 않고 false를 돌려준다.
     *
     * @param durationMs 0 이하면 [IllegalArgumentException]을 던진다.
     */
    public fun update(
        id: Long,
        message: String,
        status: KStatus,
        description: String? = null,
        durationMs: Long = DEFAULT_TOAST_DURATION_MS,
    ): Boolean {
        require(durationMs > 0) { "durationMs는 0보다 커야 합니다." }
        val entry = entries.firstOrNull { it.id == id && !it.leaving } ?: return false
        entry.message = message
        entry.status = status
        entry.description = description
        entry.loading = false
        entry.durationMs = durationMs
        entry.version++
        return true
    }

    /** 토스트 [id]를 닫는다. 없는 id면 아무 일도 하지 않는다. */
    public fun dismiss(id: Long) {
        entries.firstOrNull { it.id == id }?.leaving = true
        if (entries.count { !it.leaving } <= 1) expanded = false
    }

    /** 떠 있는 토스트를 모두 닫는다. */
    public fun dismissAll() {
        entries.forEach { it.leaving = true }
        expanded = false
    }

    /**
     * [loading] 문구로 로딩 토스트를 띄우고 [block]을 실행한 뒤, 끝나면 같은 토스트를 성공이나 실패로 바꾼다.
     * [block]의 예외는 던지지 않고 [Result]로 돌려준다. 호출한 코루틴이 취소되면 토스트를 닫고 취소를 그대로 전한다.
     *
     * @param success 결과로 성공 문구를 만든다.
     * @param error 예외로 실패 문구를 만든다.
     */
    @Suppress("TooGenericExceptionCaught") // block이 던지는 어떤 실패든 토스트로 알리고 Result로 넘긴다.
    public suspend fun <T> promise(
        loading: String,
        success: (T) -> String,
        error: (Throwable) -> String,
        block: suspend () -> T,
    ): Result<T> {
        val id = loading(loading)
        return try {
            val value = block()
            update(id, success(value), KStatus.Success)
            Result.success(value)
        } catch (cancel: CancellationException) {
            dismiss(id)
            throw cancel
        } catch (failure: Throwable) {
            update(id, error(failure), KStatus.Error)
            Result.failure(failure)
        }
    }

    private fun add(fill: (ToastEntry) -> Unit): Long {
        // 호스트가 없어 사라지는 애니메이션을 돌리지 못한 토스트와 너무 오래 쌓인 토스트를 치운다.
        entries.removeAll { it.leaving && !it.presence.isRunning && it.presence.value == 0f }
        while (entries.size >= MAX_KEPT) entries.removeAt(0)
        val entry = ToastEntry(nextId++).also(fill)
        entries += entry
        return entry.id
    }

    /** 사라지는 애니메이션이 끝난 토스트를 목록에서 뺀다. */
    internal fun remove(entry: ToastEntry) {
        entries.remove(entry)
    }
}

/** [KToasterState.show]의 기본 표시 시간(ms) */
public const val DEFAULT_TOAST_DURATION_MS: Long = 4_000L

private const val MAX_KEPT = 30
