package com.naury.kinetica.showcase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KButtonSize
import com.naury.kinetica.ui.KChatBubble
import com.naury.kinetica.ui.KChatSide
import com.naury.kinetica.ui.KCodeBlock
import com.naury.kinetica.ui.KIconButton
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KMarkdownText
import com.naury.kinetica.ui.KMenuEntry
import com.naury.kinetica.ui.KMorphMenu
import com.naury.kinetica.ui.KPromptAttachment
import com.naury.kinetica.ui.KPromptInput
import com.naury.kinetica.ui.KSegmentedControl
import com.naury.kinetica.ui.KSwitch
import com.naury.kinetica.ui.KThinkingIndicator
import com.naury.kinetica.ui.KThinkingStep
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.LocalKineticaContentColor
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** AI 채팅 앱용 묶음: 마크다운, 코드 블록, 프롬프트 입력창, 생각 중 표시. 맨 위는 이 넷을 엮은 채팅 화면이다. */
internal fun LazyListScope.aiKitDemos() {
    item { AiChatCard() }
    item { MarkdownCard() }
    item { CodeBlockCard() }
    item { PromptInputCard() }
    item { ThinkingCard() }
}

/** 답변 하나의 진행 */
private enum class ReplyPhase { Thinking, Answering, Done, Stopped }

@Stable
private class ChatTurn(val question: String) {
    var phase by mutableStateOf(ReplyPhase.Thinking)
    var stepsDone by mutableIntStateOf(0)
    var answer by mutableStateOf("")
}

@Composable
private fun AiChatCard() {
    val turns = remember { mutableStateListOf<ChatTurn>() }
    val prompt = rememberTextFieldState()
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    val current = turns.lastOrNull()
    val generating = current != null && (current.phase == ReplyPhase.Thinking || current.phase == ReplyPhase.Answering)
    fun ask(question: String) {
        val turn = ChatTurn(question)
        turns += turn
        job = scope.launch { streamReply(turn) }
    }
    DemoCard(
        "AI 채팅",
        "질문을 보내면 생각 중 표시가 단계를 채운 뒤 답변이 마크다운과 코드 블록으로 차오릅니다. 생성 중에는 보내기 버튼이 멈춤 버튼으로 바뀝니다.",
    ) {
        if (turns.isEmpty()) {
            KButton("예시 질문 보내기", { ask(SAMPLE_QUESTION) }, variant = KVariant.Tonal, size = KButtonSize.Small)
        }
        for (turn in turns) ChatTurnView(turn)
        KPromptInput(
            prompt,
            onSend = {
                ask(prompt.text.toString().trim())
                prompt.clearText()
            },
            modifier = Modifier.fillMaxWidth(),
            generating = generating,
            onStop = {
                job?.cancel()
                current?.phase = ReplyPhase.Stopped
            },
            leading = { ModelMenu() },
        )
    }
}

private suspend fun streamReply(turn: ChatTurn) {
    for (step in THINKING_STEPS.indices) {
        delay(STEP_MS)
        turn.stepsDone = step + 1
    }
    turn.phase = ReplyPhase.Answering
    var shown = 0
    while (shown < SAMPLE_ANSWER.length) {
        delay(CHUNK_MS)
        shown = min(SAMPLE_ANSWER.length, shown + CHUNK)
        turn.answer = SAMPLE_ANSWER.substring(0, shown)
    }
    turn.phase = ReplyPhase.Done
}

@Composable
private fun ChatTurnView(turn: ChatTurn) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KChatBubble(turn.question, KChatSide.Sent)
        val steps = THINKING_STEPS.mapIndexed { index, title -> KThinkingStep("s$index", title, done = index < turn.stepsDone) }
        KThinkingIndicator(thinking = turn.phase == ReplyPhase.Thinking, steps = steps)
        if (turn.answer.isNotEmpty()) KMarkdownText(turn.answer, Modifier.fillMaxWidth())
        if (turn.phase == ReplyPhase.Stopped) {
            Note("답변을 멈췄습니다.")
        }
    }
}

@Composable
private fun ModelMenu() {
    var model by rememberSaveable { mutableStateOf(MODELS.first()) }
    var open by remember { mutableStateOf(false) }
    KMorphMenu(model, open, { open = it }, MODELS.map { name -> KMenuEntry.Action(name) { model = name } })
}

@Composable
private fun MarkdownCard() {
    var opened by remember { mutableStateOf<String?>(null) }
    DemoCard("마크다운", "제목, 강조, 목록, 인용, 코드 블록을 그립니다. 링크를 누르면 주소를 앱이 받습니다.") {
        KMarkdownText(RELEASE_NOTES, Modifier.fillMaxWidth(), onLinkClick = { opened = it })
        opened?.let { Note("받은 링크: $it") }
    }
}

@Composable
private fun CodeBlockCard() {
    var language by rememberSaveable { mutableIntStateOf(0) }
    var numbers by rememberSaveable { mutableStateOf(true) }
    var wrap by rememberSaveable { mutableStateOf(false) }
    var dark by rememberSaveable { mutableStateOf(true) }
    DemoCard("코드 블록", "언어별 구문 강조와 복사 버튼이 있습니다. 긴 줄은 가로로 스크롤하거나 접을 수 있습니다.") {
        KSegmentedControl(CODE_SAMPLES.map { it.first }, language, { language = it }, Modifier.fillMaxWidth())
        val (name, code) = CODE_SAMPLES[language]
        KCodeBlock(code, Modifier.fillMaxWidth(), language = name, showLineNumbers = numbers, wrapLines = wrap, dark = dark)
        SwitchRow("줄 번호", numbers) { numbers = it }
        SwitchRow("줄 바꿈", wrap) { wrap = it }
        SwitchRow("어두운 색", dark) { dark = it }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Label(title, ROW_TEXT, modifier = Modifier.weight(1f))
        KSwitch(checked, onChange)
    }
}

@Composable
private fun PromptInputCard() {
    val prompt = rememberTextFieldState()
    var generating by rememberSaveable { mutableStateOf(false) }
    val files = remember { SAMPLE_FILES.toMutableStateList() }
    var sent by remember { mutableStateOf<String?>(null) }
    DemoCard("프롬프트 입력창", "첨부 칩과 모델 선택 칩을 함께 둡니다. 생성 중이면 테두리에 빛이 돌고 버튼이 멈춤으로 바뀝니다. 키보드에서는 Enter로 보내고 Shift+Enter로 줄을 바꿉니다.") {
        KPromptInput(
            prompt,
            onSend = {
                sent = prompt.text.toString()
                prompt.clearText()
                generating = true
            },
            modifier = Modifier.fillMaxWidth(),
            generating = generating,
            onStop = { generating = false },
            attachments = files,
            onRemoveAttachment = { files.remove(it) },
            leading = {
                KIconButton(KIcons.Plus, "파일 첨부", {
                    if (files.size <
                        SAMPLE_FILES.size
                    ) {
                        files.add(SAMPLE_FILES[files.size])
                    }
                }, variant = KVariant.Text)
                ModelMenu()
            },
        )
        SwitchRow("생성 중", generating) { generating = it }
        sent?.let { Note("보낸 글: $it") }
        KButton("예시 질문 채우기", { prompt.setTextAndPlaceCursorAtEnd(SAMPLE_QUESTION) }, variant = KVariant.Tonal, size = KButtonSize.Small)
    }
}

@Composable
private fun ThinkingCard() {
    var run by remember { mutableIntStateOf(0) }
    var thinking by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(THINKING_STEPS.size) }
    LaunchedEffect(run) {
        if (run == 0) return@LaunchedEffect
        thinking = true
        done = 0
        for (step in THINKING_STEPS.indices) {
            delay(STEP_MS * 2)
            done = step + 1
        }
        thinking = false
    }
    DemoCard("생각 중 표시", "생각하는 동안 글자가 반짝이며 경과 시간을 셉니다. 눌러 펼치면 추론 단계가 보이고, 끝나면 생각한 시간으로 접힙니다.") {
        val steps = THINKING_STEPS.mapIndexed { index, title -> KThinkingStep("t$index", title, done = index < done) }
        KThinkingIndicator(thinking = thinking, steps = steps, initiallyExpanded = true)
        KButton("다시 생각하기", { run++ }, variant = KVariant.Tonal, size = KButtonSize.Small, enabled = !thinking)
        Note("완료 $done/${THINKING_STEPS.size}단계")
    }
}

/** 설명 글보다 흐린 작은 글 */
@Composable
private fun Note(text: String) {
    Label(text, NOTE_TEXT, color = LocalKineticaContentColor.current.copy(alpha = NOTE_ALPHA))
}

private const val NOTE_TEXT = 13
private const val NOTE_ALPHA = 0.6f
private const val ROW_TEXT = 15
private const val STEP_MS = 900L
private const val CHUNK_MS = 28L
private const val CHUNK = 6

private val MODELS = listOf("Kinetica 4 Pro", "Kinetica 4 Fast", "Kinetica 4 Mini")

private val THINKING_STEPS = listOf("질문 의도 파악", "Compose 성능 자료 찾기", "예제 코드 구성")

private const val SAMPLE_QUESTION = "LazyColumn 스크롤이 끊겨요. 어떻게 고치나요?"

private val SAMPLE_FILES = listOf(
    KPromptAttachment("trace", "scroll-trace.perfetto", "2.4MB"),
    KPromptAttachment("shot", "목록 화면.png", "820KB"),
    KPromptAttachment("log", "jank.log", "36KB"),
)

private val SAMPLE_ANSWER = """
    ## 목록을 부드럽게 만드는 법

    `LazyColumn`이 끊긴다면 대부분 **재구성이 너무 자주** 일어나는 경우예요. 아래 세 가지를 먼저 확인해 보세요.

    1. 항목마다 `key`를 지정하기
    2. 스크롤 위치처럼 매 프레임 바뀌는 값은 *그리기 단계*에서 읽기
       - `Modifier.graphicsLayer { }` 안에서 읽기
       - `derivedStateOf`로 묶기
    3. 무거운 계산은 `remember`로 캐시하기

    ```kotlin
    LazyColumn {
        items(messages, key = { it.id }) { message ->
            // 같은 id면 상태와 측정 결과를 다시 쓴다.
            MessageRow(message, modifier = Modifier.animateItem())
        }
    }
    ```

    > 측정은 릴리스 빌드에서 하세요. 디버그 빌드는 ~~조금~~ 몇 배 느립니다.

    ---

    더 자세한 내용은 [Compose 성능 가이드](https://developer.android.com/develop/ui/compose/performance)를 참고하세요.
""".trimIndent()

private val RELEASE_NOTES = """
    # Kinetica 1.0 출시 노트
    이번 판은 **AI 채팅 화면**에 필요한 부품을 담았습니다.

    ### 새로 들어온 것
    - 마크다운 렌더러 `KMarkdownText`
      - 스트리밍 중에도 *깨지지 않는* 파서
    - 구문 강조 코드 블록
    - ~~임시 입력창~~ 정식 프롬프트 입력창

    > 기존 `KChatInput`은 그대로 쓸 수 있습니다.

    자세한 내용은 [문서](https://kinetica.dev/docs/ai)나 <https://kinetica.dev/changelog>를 보세요.
""".trimIndent()

private val CODE_SAMPLES = listOf(
    "kotlin" to """
        @Composable
        fun Greeting(name: String, modifier: Modifier = Modifier) {
            val count by remember { mutableIntStateOf(0) } // 누른 횟수
            Text("안녕하세요, ${'$'}name! 지금까지 ${'$'}count번 눌렀어요.", modifier.padding(16.dp))
        }
    """.trimIndent(),
    "python" to """
        def fibonacci(n: int) -> list[int]:
            # 처음 n개의 피보나치 수
            seq = [0, 1]
            while len(seq) < n:
                seq.append(seq[-1] + seq[-2])
            return seq[:n]
    """.trimIndent(),
    "json" to """
        {
          "model": "kinetica-4-pro",
          "stream": true,
          "temperature": 0.7,
          "max_tokens": 1024,
          "tools": null
        }
    """.trimIndent(),
    "shell" to """
        # 릴리스 빌드를 설치하고 프레임 시간을 잰다
        ./gradlew :sample-showcase:installRelease
        adb shell dumpsys gfxinfo com.naury.kinetica.showcase reset
        echo "목록을 10초 동안 스크롤하세요" && sleep 10
    """.trimIndent(),
)
