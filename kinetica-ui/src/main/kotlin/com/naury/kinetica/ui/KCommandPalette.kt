package com.naury.kinetica.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource

/**
 * [KCommandPalette]의 명령 하나.
 *
 * @property id 최근 명령 기록과 목록 키에 쓰는 값. 모든 묶음을 통틀어 겹치지 않아야 한다.
 * @property title 목록에 보이고 검색어와 맞춰 보는 이름
 * @property subtitle 이름 아래 작은 설명. 검색 대상은 아니다.
 * @property icon 줄 앞 아이콘. `null`이면 자리를 비운다.
 * @property shortcut 줄 끝에 키캡으로 보일 단축키 글. `+`로 나누면 키캡이 여럿이 된다(`"Ctrl+Shift+P"`). 표시만 하고 키를 등록하지는 않는다.
 * @property keywords 이름에 없지만 이 명령을 찾을 때 쓸 만한 말. 맞으면 결과에 넣되 강조는 하지 않는다.
 * @property enabled `false`면 흐리게 보이고 고를 수도 실행할 수도 없다.
 * @property onRun 실행할 때 부른다. 판이 닫힌 뒤에 부르므로 여기서 다이얼로그를 열어도 된다.
 */
@Immutable
public data class KCommand(
    val id: String,
    val title: String,
    val onRun: () -> Unit,
    val subtitle: String? = null,
    val icon: ImageVector? = null,
    val shortcut: String? = null,
    val keywords: List<String> = emptyList(),
    val enabled: Boolean = true,
)

/** 제목 아래에 모아 보일 명령 묶음. [title]이 비어 있으면 제목 줄 없이 명령만 놓는다. */
@Immutable
public data class KCommandSection(val title: String, val commands: List<KCommand>)

/**
 * [KCommandPalette]가 열렸는지와 최근에 실행한 명령. [rememberKCommandPaletteState]로 만들면 화면 회전 뒤에도 남는다.
 *
 * @param maxRecent 기억할 최근 명령 수. 0이면 최근 명령을 두지 않는다. 음수면 [IllegalArgumentException]을 던진다.
 */
@Stable
public class KCommandPaletteState(
    initiallyOpen: Boolean = false,
    recentIds: List<String> = emptyList(),
    public val maxRecent: Int = DEFAULT_MAX_RECENT,
) {
    init {
        require(maxRecent >= 0) { "maxRecent는 0 이상이어야 합니다." }
    }

    /** 판이 열려 있다. 닫힘 애니메이션 중에는 이미 false다. */
    public var isOpen: Boolean by mutableStateOf(initiallyOpen)
        private set

    /** 최근에 실행한 명령 id. 가장 최근 것이 맨 앞이고 [maxRecent]개를 넘지 않는다. */
    public var recentIds: List<String> by mutableStateOf(recentIds.distinct().take(maxRecent))
        private set

    public fun open() {
        isOpen = true
    }

    public fun close() {
        isOpen = false
    }

    public fun toggle() {
        isOpen = !isOpen
    }

    /** [id]를 최근 명령 맨 앞에 넣는다. 판에서 명령을 실행하면 저절로 부른다. */
    public fun addRecent(id: String) {
        recentIds = (listOf(id) + recentIds.filter { it != id }).take(maxRecent)
    }

    public fun clearRecent() {
        recentIds = emptyList()
    }

    /**
     * [event]가 Ctrl+[key]나 Meta(Command)+[key]를 누른 것이면 판을 열고 닫고 true를 돌려준다. 아니면 아무것도 하지 않고 false다.
     * 화면에 키 초점을 받은 요소가 없어 [commandPaletteShortcut]이 키를 받지 못할 때 호스트 Activity의 `dispatchKeyEvent`에서
     * `state.handleShortcut(KeyEvent(event))`처럼 부른다.
     */
    public fun handleShortcut(event: KeyEvent, key: Key = Key.K): Boolean {
        val pressed = event.type == KeyEventType.KeyDown && event.key == key
        val chord = (event.isCtrlPressed || event.isMetaPressed) && !event.isAltPressed
        if (!pressed || !chord) return false
        toggle()
        return true
    }

    public companion object {
        /** [rememberSaveable]용 저장 방법. 열림 여부와 최근 명령, 최대 개수를 저장한다. */
        public val Saver: Saver<KCommandPaletteState, Any> = Saver(
            save = { arrayListOf(it.isOpen, it.maxRecent, ArrayList(it.recentIds)) },
            restore = {
                val list = it as List<*>
                KCommandPaletteState(list[0] as Boolean, (list[2] as List<*>).map { id -> id as String }, list[1] as Int)
            },
        )
    }
}

/** 화면 회전 뒤에도 남는 [KCommandPaletteState]를 만든다. */
@Composable
public fun rememberKCommandPaletteState(maxRecent: Int = DEFAULT_MAX_RECENT): KCommandPaletteState =
    rememberSaveable(saver = KCommandPaletteState.Saver) { KCommandPaletteState(maxRecent = maxRecent) }

/**
 * 이 요소 안에 키 초점이 있을 때 Ctrl+[key](macOS 키보드면 Command+[key])를 누르면 [state]의 판을 열고 닫는다.
 * 화면 루트에 붙인다. 키 초점이 아무 데도 없으면 Compose가 키를 받지 않으므로, 그런 화면이면 [KCommandPaletteState.handleShortcut]을
 * Activity에서 부른다.
 *
 * ```kotlin
 * Box(Modifier.fillMaxSize().commandPaletteShortcut(palette)) { … }
 * ```
 */
public fun Modifier.commandPaletteShortcut(state: KCommandPaletteState, key: Key = Key.K): Modifier =
    onPreviewKeyEvent { state.handleShortcut(it, key) }

/**
 * 키보드 단축키로 여는 명령 검색 판. 검색어를 넣으면 묶음마다 맞는 명령을 걸러 점수 순으로 놓고, 제목에서 맞은 글자를 강조한다.
 * 검색어가 비어 있으면 최근에 실행한 명령을 맨 위에 보인다. 아무것도 맞지 않으면 결과 없음 화면을 보인다.
 *
 * 열리면 검색어 칸에 초점이 간다. 위아래 방향키로 명령을 고르고 Enter로 실행하며, Esc나 뒤로 가기, 바깥 누르기로 닫는다.
 * 명령을 누르거나 실행하면 판을 닫고 [KCommand.onRun]을 부른 뒤 최근 명령에 넣는다. 열 때마다 검색어는 비어 있다.
 *
 * 600dp 이상 넓은 창에서는 화면 가운데에 고정 높이 판으로, 좁은 창에서는 화면 위쪽에 내용 높이만큼 뜬다.
 * 판은 시스템 바와 키보드를 피한다. TalkBack에는 결과 수가 바뀔 때마다 알린다. '애니메이션 삭제' 설정에서는 바로 열고 닫는다.
 * [KineticaScaffold] 안에서는 오버레이 레이어에, 밖에서는 전체 화면 대화 상자 창에 띄운다.
 *
 * ```kotlin
 * val palette = rememberKCommandPaletteState()
 * Box(Modifier.commandPaletteShortcut(palette)) { … }
 * KCommandPalette(palette, listOf(KCommandSection("파일", listOf(KCommand("new", "새 문서", ::newDoc, shortcut = "Ctrl+N")))))
 * ```
 *
 * @param placeholder 검색어가 비어 있을 때 보이는 안내 글. TalkBack은 이 글을 입력란 이름으로 읽는다.
 * @param empty 결과 없음 화면을 바꾼다. 검색어를 받는다. `null`이면 기본 화면이다.
 */
@Composable
public fun KCommandPalette(
    state: KCommandPaletteState,
    sections: List<KCommandSection>,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.kinetica_ui_palette_placeholder),
    surface: SurfaceStyle = KineticaTheme.surface,
    empty: (@Composable (query: String) -> Unit)? = null,
) {
    val wide = !rememberKWindowLayout().isCompact
    val design = KineticaTheme.design
    val still = reducedMotion()
    // 열고 닫는 동안 바깥 람다는 프레임마다 다시 구성되므로, 판이 같은 값을 받아 건너뛰도록 묶음을 기억해 둔다.
    val spec =
        remember(state, sections, placeholder, surface, wide, empty) { PaletteSpec(state, sections, placeholder, surface, wide, empty) }
    KOverlay(state.isOpen) { transition ->
        val progress by transition.animateFloat(
            transitionSpec = {
                when {
                    still -> tween(0)
                    targetState -> design.motion.tuned(OPEN_DAMPING, OPEN_STIFFNESS)
                    else -> tween(CLOSE_MS)
                }
            },
            label = "command-palette",
        ) { if (it) 1f else 0f }
        BackHandler(enabled = transition.targetState) { state.close() }
        Box(Modifier.fillMaxSize()) {
            Scrim(progress.coerceIn(0f, 1f)) { state.close() }
            PalettePanel(spec, { progress }, modifier)
        }
    }
}

/** 판 하나를 그리는 데 필요한 값 */
@Stable
internal class PaletteSpec(
    val state: KCommandPaletteState,
    val sections: List<KCommandSection>,
    val placeholder: String,
    val surface: SurfaceStyle,
    val wide: Boolean,
    val empty: (@Composable (String) -> Unit)?,
)

private const val DEFAULT_MAX_RECENT = 5
private const val OPEN_DAMPING = 0.82f
private const val OPEN_STIFFNESS = 520f
private const val CLOSE_MS = 160
