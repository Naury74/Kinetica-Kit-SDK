package com.example.consumer.m3

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KCodeInput
import com.naury.kinetica.ui.KPasswordField
import com.naury.kinetica.ui.KSheet
import com.naury.kinetica.ui.KTagInput
import com.naury.kinetica.ui.passwordStrength

/** 시험용 인증번호. 실제 앱이라면 서버가 확인한다. */
const val TEST_CODE = "123456"

/**
 * 가입 화면. Material 입력란과 글래스 입력 컴포넌트를 섞고, 인증번호 6자리를 다 채우면 환영 화면으로 넘어간다.
 * 시트 안에서도 같은 입력 컴포넌트가 키보드 이동을 따르는지 보려고 PIN 바꾸기 시트를 둔다.
 */
@Composable
fun SignUpScreen(onVerified: (String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var interests by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var codeSent by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var codeError by rememberSaveable { mutableStateOf(false) }
    var pinSheet by rememberSaveable { mutableStateOf(false) }
    var pinResult by rememberSaveable { mutableStateOf("없음") }
    val focus = LocalFocusManager.current
    val codeFocus = remember { FocusRequester() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("강도: ${if (password.isEmpty()) "-" else passwordStrength(password).name}", Modifier.testTag("suStrength"))
        OutlinedTextField(
            email, { email = it }, Modifier.fillMaxWidth().testTag("suEmail"), label = { Text("이메일") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        // Next에 콜백을 주지 않으면 기본 동작대로 다음 입력란(관심사)으로 간다.
        KPasswordField(
            password, { password = it }, label = "비밀번호", modifier = Modifier.testTag("suPassword"), showChecklist = true,
            imeAction = ImeAction.Next, fieldModifier = Modifier.testTag("suPasswordField"),
        )
        // 글자가 있으면 완료 키가 칩을 만들고, 빈 입력란에서 누르면 기본 동작대로 키보드가 내려간다.
        KTagInput(
            interests, { interests = it }, Modifier.fillMaxWidth().testTag("suTags"), placeholder = "관심사",
            suggestions = listOf("디자인", "개발", "사진", "여행", "음악", "디저트"),
            fieldModifier = Modifier.testTag("suTagsField"),
        )
        Text("관심사: ${interests.joinToString(",")}", Modifier.testTag("suTagsValue"))
        Button({ codeSent = true; code = ""; codeError = false }, Modifier.testTag("suSendCode")) { Text("인증번호 받기") }
        if (codeSent) {
            KCodeInput(
                code,
                { code = it; codeError = false },
                Modifier.testTag("suCode").focusRequester(codeFocus),
                error = codeError,
                onComplete = { entered ->
                    if (entered == TEST_CODE) {
                        focus.clearFocus()
                        onVerified(email)
                    } else {
                        codeError = true
                        code = ""
                    }
                },
            )
            Text(if (codeError) "인증번호가 틀렸어요" else "", Modifier.testTag("suCodeError"))
        }
        Text("PIN: $pinResult", Modifier.testTag("pinResult"))
        TextButton({ pinSheet = true }, Modifier.testTag("suOpenPinSheet")) { Text("PIN 바꾸기") }
    }

    KSheet(pinSheet, { pinSheet = false }, title = "PIN 바꾸기") {
        PinChangeForm(onDone = { pinResult = it; pinSheet = false })
    }
}

/** 시트 안의 폼. 현재 비밀번호에서 '다음'을 누르면 PIN 칸으로 가고, PIN 4자리를 다 채우면 닫힌다. */
@Composable
private fun PinChangeForm(onDone: (String) -> Unit) {
    var current by rememberSaveable { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }
    val pinFocus = remember { FocusRequester() }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KPasswordField(
            current, { current = it }, label = "현재 비밀번호", modifier = Modifier.testTag("pinCurrent"), showStrength = false,
            imeAction = ImeAction.Next, keyboardActions = KeyboardActions(onNext = { pinFocus.requestFocus() }),
            fieldModifier = Modifier.testTag("pinCurrentField"),
        )
        KCodeInput(
            pin, { pin = it }, Modifier.testTag("pinCode").focusRequester(pinFocus), length = 4, mask = true,
            onComplete = { onDone("변경됨:${current.length}:${it.length}") },
        )
    }
}

/** 인증을 마친 뒤 오는 화면. */
@Composable
fun WelcomeScreen(email: String) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("환영합니다 $email", Modifier.testTag("welcome"))
    }
}
