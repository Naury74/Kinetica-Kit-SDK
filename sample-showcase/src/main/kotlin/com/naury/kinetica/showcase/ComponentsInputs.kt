// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.GlassCodeInput
import com.naury.kinetica.ui.GlassColorPicker
import com.naury.kinetica.ui.GlassPasswordField
import com.naury.kinetica.ui.GlassResult
import com.naury.kinetica.ui.GlassResultMark
import com.naury.kinetica.ui.GlassSignaturePad
import com.naury.kinetica.ui.GlassTagInput
import com.naury.kinetica.ui.LocalGlassContentColor
import com.naury.kinetica.ui.PasswordStrength
import com.naury.kinetica.ui.passwordStrength
import com.naury.kinetica.ui.rememberSignaturePadState
import kotlinx.coroutines.delay

/** 입력 탭: 인증번호, 태그, 색 선택, 서명, 비밀번호. */
@Composable
internal fun InputTab(padding: PaddingValues, connection: NestedScrollConnection) {
    ComponentTabList(padding, connection) {
        item { CodeCard() }
        item { TagCard() }
        item { ColorCard() }
        item { SignatureCard() }
        item { PasswordCard() }
    }
}

@Composable
private fun CodeCard() {
    var code by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf<String?>(null) }
    var result by rememberSaveable { mutableStateOf<GlassResult?>(null) }
    LaunchedEffect(submitted) {
        val sent = submitted ?: return@LaunchedEffect
        result = GlassResult.Loading
        delay(1200)
        result = if (sent == SAMPLE_CODE) GlassResult.Success else GlassResult.Failure
    }
    DemoCard("인증번호", "${SAMPLE_CODE}을 넣으면 확인 표시가, 다른 번호를 넣으면 실패 표시가 그려집니다. 붙여 넣기와 SMS 자동 채우기도 받습니다.") {
        GlassCodeInput(
            code,
            {
                code = it
                if (it.length < SAMPLE_CODE.length) {
                    submitted = null
                    result = null
                }
            },
            Modifier.fillMaxWidth(),
            error = result == GlassResult.Failure,
            onComplete = { submitted = it },
        )
        result?.let { state ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassResultMark(state, size = 56.dp)
                Label(CODE_MESSAGES.getValue(state), 15, FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TagCard() {
    var tags by rememberSaveable { mutableStateOf(listOf("디자인", "모션")) }
    DemoCard("태그", "쉼표나 완료 키를 누르면 칩이 됩니다. 이미 있는 태그를 넣으면 그 칩이 흔들리고, 빈 입력란에서 지우기를 두 번 누르면 마지막 칩이 지워집니다.") {
        GlassTagInput(tags, { tags = it }, Modifier.fillMaxWidth(), placeholder = "태그 추가", suggestions = TAG_SUGGESTIONS)
    }
}

@Composable
private fun ColorCard() {
    var color by rememberSaveable(saver = colorStateSaver) { mutableStateOf(Color(0xFF5B6CFF)) }
    val shown by animateColorAsState(color, label = "preview")
    DemoCard("색 선택", "고리에서 색조를, 가운데 사각형에서 채도와 명도를 고릅니다. 고른 색이 위 카드에 바로 반영됩니다.") {
        Box(Modifier.fillMaxWidth().heightIn(min = 96.dp).background(shown, INNER_SHAPE).padding(18.dp)) {
            val ink = if (shown.isLight() || shown.alpha < 0.4f) Color(0xFF15161A) else Color.White
            Column {
                Label("미리보기 카드", 18, FontWeight.Bold, color = ink)
                Label("이번 달 사용 금액 128,400원", 13, color = ink.copy(alpha = 0.8f))
            }
        }
        GlassColorPicker(color, { color = it }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun SignatureCard() {
    val signature = rememberSignaturePadState()
    var saved by remember { mutableStateOf<ImageBitmap?>(null) }
    DemoCard("서명", "빠르게 그으면 가늘게, 천천히 그으면 굵게 그려집니다. 저장을 누르면 이미지로 내보낸 결과를 아래에 보여 줍니다.") {
        GlassSignaturePad(Modifier.fillMaxWidth().height(180.dp), signature)
        Pill("저장", filled = true) {
            if (!signature.isEmpty) saved = signature.toImageBitmap(width = 600, color = Color(0xFF15161A), background = Color.White)
        }
        saved?.let { image ->
            Image(
                image,
                "저장한 서명",
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Color.White, INNER_SHAPE)
                    .border(1.dp, LocalGlassContentColor.current.copy(alpha = 0.2f), INNER_SHAPE),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun PasswordCard() {
    var password by rememberSaveable { mutableStateOf("") }
    val strength = passwordStrength(password)
    DemoCard("비밀번호", "눈 아이콘으로 보이거나 숨깁니다. 입력하는 동안 강도 막대와 조건 목록이 바뀝니다.") {
        GlassPasswordField(password, { password = it }, "새 비밀번호", Modifier.fillMaxWidth(), showChecklist = true)
        if (password.isNotEmpty()) Label("passwordStrength() 결과: ${STRENGTH_NAMES.getValue(strength)}", 13)
    }
}

private const val SAMPLE_CODE = "123456"
private val CODE_MESSAGES = mapOf(
    GlassResult.Loading to "확인하는 중…",
    GlassResult.Success to "인증되었습니다",
    GlassResult.Failure to "인증번호가 맞지 않아요",
    GlassResult.Warning to "잠시 후 다시 시도해 주세요",
)
private val STRENGTH_NAMES = mapOf(PasswordStrength.Weak to "약함", PasswordStrength.Medium to "보통", PasswordStrength.Strong to "강함")
private val TAG_SUGGESTIONS = listOf("디자인", "개발", "기획", "모션", "마케팅", "데이터", "디자인 시스템", "접근성")
