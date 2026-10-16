package com.example.consumer.m3

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KDialog
import com.naury.kinetica.ui.KSheet
import com.naury.kinetica.ui.shake

/** 시트와 다이얼로그 안에 Material 입력 폼을 넣는 화면. */
@Composable
fun FormScreen() {
    var sheet by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf("없음") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("제출: $submitted", Modifier.testTag("submitted"))
        Button({ sheet = true }, Modifier.testTag("openFormSheet")) { Text("주소 입력 시트") }
        Button({ dialog = true }, Modifier.testTag("openFormDialog")) { Text("로그인 다이얼로그") }
    }

    KSheet(sheet, { sheet = false }, title = "배송지") {
        AddressForm(onSubmit = { submitted = it; sheet = false })
    }
    KDialog(dialog, { dialog = false }, title = "로그인") {
        LoginForm(onSubmit = { submitted = it; dialog = false })
    }
}

@Composable
private fun AddressForm(onSubmit: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var memo by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var errors by rememberSaveable { mutableIntStateOf(0) }
    val focus = LocalFocusManager.current
    fun submit() {
        error = when {
            name.isBlank() -> "이름을 입력하세요"
            phone.length < 9 -> "전화번호를 확인하세요"
            address.isBlank() -> "주소를 입력하세요"
            else -> null
        }
        if (error == null) {
            focus.clearFocus()
            onSubmit("$name/$phone/$address/$memo")
        } else {
            errors++
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val next = KeyboardOptions(imeAction = ImeAction.Next)
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().testTag("fieldName"), label = { Text("이름") }, keyboardOptions = next, singleLine = true)
        OutlinedTextField(
            phone, { phone = it }, Modifier.fillMaxWidth().testTag("fieldPhone"), label = { Text("전화") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next), singleLine = true,
        )
        OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth().testTag("fieldAddress"), label = { Text("주소") }, keyboardOptions = next, singleLine = true)
        OutlinedTextField(
            memo, { memo = it }, Modifier.fillMaxWidth().testTag("fieldMemo"), label = { Text("메모") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            singleLine = true,
        )
        error?.let { Text(it, Modifier.testTag("formError").shake(errors)) }
        Button({ submit() }, Modifier.fillMaxWidth().testTag("formSubmit")) { Text("저장") }
        // ImeAction.Next 기본 동작 외에, 호스트가 직접 다음 칸으로 옮기는 경우도 흔하다.
        Button({ focus.moveFocus(FocusDirection.Down) }, Modifier.testTag("formNextManual")) { Text("다음 칸") }
    }
}

@Composable
private fun LoginForm(onSubmit: (String) -> Unit) {
    var id by rememberSaveable { mutableStateOf("") }
    var pw by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(id, { id = it }, Modifier.fillMaxWidth().testTag("loginId"), label = { Text("아이디") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
        OutlinedTextField(pw, { pw = it }, Modifier.fillMaxWidth().testTag("loginPw"), label = { Text("비밀번호") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (id.isNotBlank() && pw.length >= 4) onSubmit("login:$id") else error = "아이디와 비밀번호를 확인하세요" }))
        error?.let { Text(it, Modifier.testTag("loginError")) }
        Button({ if (id.isNotBlank() && pw.length >= 4) onSubmit("login:$id") else error = "아이디와 비밀번호를 확인하세요" }, Modifier.testTag("loginSubmit")) { Text("로그인") }
    }
}
