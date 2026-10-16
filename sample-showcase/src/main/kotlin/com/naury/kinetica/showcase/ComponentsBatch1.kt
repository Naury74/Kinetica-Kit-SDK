package com.naury.kinetica.showcase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KCheckboxRow
import com.naury.kinetica.ui.KCheckboxShape
import com.naury.kinetica.ui.KDisclosureRow
import com.naury.kinetica.ui.KListItem
import com.naury.kinetica.ui.KListLeading
import com.naury.kinetica.ui.KListSection
import com.naury.kinetica.ui.KListSectionStyle
import com.naury.kinetica.ui.KListTrailing
import com.naury.kinetica.ui.KRadioRow
import com.naury.kinetica.ui.KSegmentedControl
import com.naury.kinetica.ui.KSwitchRow
import com.naury.kinetica.ui.KSwitchVariant
import com.naury.kinetica.ui.KTextArea
import com.naury.kinetica.ui.KTextField
import com.naury.kinetica.ui.KTextFieldVariant
import com.naury.kinetica.ui.KTriStateCheckbox
import com.naury.kinetica.ui.KValueRow
import com.naury.kinetica.ui.LocalKineticaContentColor

/**
 * 폼과 목록: 설정 앱처럼 꾸민 목록 섹션, 스위치 모양 고르기, 라디오와 체크박스, 입력란 모양 고르기를 입력 탭 맨 위에 놓는다.
 */
internal fun LazyListScope.formsAndLists() {
    item { FormsIntro() }
    item { AccountSection() }
    item { ConnectionSection() }
    item { DisplaySection() }
    item { ProfileFormSection() }
    item { FieldVariantsCard() }
    item { ConsentSection() }
    item { InboxSection() }
    item { AboutSection() }
}

@Composable
private fun FormsIntro() {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label("폼과 목록", size = TITLE_SIZE, weight = FontWeight.Bold)
        Label(
            "설정 앱처럼 꾸민 목록입니다. 행 어디를 눌러도 스위치와 체크박스가 바뀌고, 둥근 섹션의 첫 행과 끝 행은 모서리에 맞게 눌림 표시가 잘립니다.",
            size = BODY_SIZE,
            color = LocalKineticaContentColor.current.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun AccountSection() {
    KListSection {
        item {
            KListItem(
                "김민지",
                supporting = "Kinetica 계정, 클라우드, 결제",
                leading = KListLeading.Avatar("민", color = FormPink),
                trailing = KListTrailing.Disclosure(),
                onClick = {},
            )
        }
    }
}

@Composable
private fun ConnectionSection() {
    var airplane by rememberSaveable { mutableStateOf(false) }
    var hotspot by rememberSaveable { mutableStateOf(true) }
    var variant by rememberSaveable { mutableIntStateOf(0) }
    val switch = KSwitchVariant.entries[variant]
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KSegmentedControl(listOf("캡슐", "체크", "슬림"), variant, { variant = it }, Modifier.fillMaxWidth())
        KListSection(header = "연결", footer = "비행기 모드를 켜면 Wi-Fi와 블루투스를 뺀 모든 무선 연결이 끊깁니다.") {
            item {
                KSwitchRow("비행기 모드", airplane, { airplane = it }, leading = icon(ShowcaseIcons.Send, FormOrange), variant = switch)
            }
            item {
                KDisclosureRow(
                    "Wi-Fi",
                    {},
                    value = if (airplane) "꺼짐" else "Home_5G",
                    leading = icon(ShowcaseIcons.Wave, FormBlue),
                )
            }
            item { KDisclosureRow("블루투스", {}, value = "켜짐", leading = icon(ShowcaseIcons.Link, FormBlue)) }
            item {
                KSwitchRow(
                    "개인용 핫스팟",
                    hotspot,
                    { hotspot = it },
                    subtitle = "다른 기기가 이 휴대전화의 데이터를 씁니다",
                    leading = icon(ShowcaseIcons.Phone, FormGreen),
                    enabled = !airplane,
                    variant = switch,
                )
            }
        }
    }
}

@Composable
private fun DisplaySection() {
    var mode by rememberSaveable { mutableIntStateOf(2) }
    var autoBrightness by rememberSaveable { mutableStateOf(true) }
    var nightLight by rememberSaveable { mutableStateOf(false) }
    KListSection(Modifier.selectableGroup(), header = "화면 모드") {
        DISPLAY_MODES.forEachIndexed { index, (title, subtitle) ->
            item(key = title) { KRadioRow(title, mode == index, { mode = index }, subtitle = subtitle) }
        }
        item(key = "auto") { KCheckboxRow("자동 밝기", autoBrightness, { autoBrightness = it }) }
        item(key = "night") {
            KCheckboxRow("야간 조명", nightLight, { nightLight = it }, subtitle = "해가 지면 화면을 따뜻한 색으로 바꿉니다")
        }
    }
}

@Composable
private fun ProfileFormSection() {
    var name by rememberSaveable { mutableStateOf("김민지") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    KListSection(header = "개인 정보", footer = "라벨을 왼쪽에 고정하는 Inline 입력란입니다. 섹션 행 안에 바로 넣습니다.") {
        item { KTextField(name, { name = it }, "이름", variant = KTextFieldVariant.Inline) }
        item {
            KTextField(
                email,
                { email = it },
                "이메일",
                variant = KTextFieldVariant.Inline,
                placeholder = "name@example.com",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
        }
        item {
            KTextField(
                phone,
                { phone = it },
                "전화",
                variant = KTextFieldVariant.Inline,
                placeholder = "010-0000-0000",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
        }
    }
}

@Composable
private fun FieldVariantsCard() {
    var variant by rememberSaveable { mutableIntStateOf(0) }
    var nickname by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("12000") }
    var weight by rememberSaveable { mutableStateOf("") }
    var bio by rememberSaveable { mutableStateOf("") }
    val shape = FIELD_VARIANTS[variant].second
    DemoCard("입력란 모양", "같은 폼을 네 가지 모양으로 바꿔 봅니다. 접두와 접미 글자는 라벨이 떠오르면 나타나고, 글자 수 제한을 넘는 입력은 잘립니다.") {
        KSegmentedControl(FIELD_VARIANTS.map { it.first }, variant, { variant = it }, Modifier.fillMaxWidth())
        KTextField(nickname, { nickname = it }, "닉네임", Modifier.fillMaxWidth(), variant = shape, prefix = "@", maxLength = 15)
        KTextField(
            price,
            { price = it.filter(Char::isDigit) },
            "가격",
            Modifier.fillMaxWidth(),
            variant = shape,
            prefix = "₩",
            supportingText = "숫자만 넣습니다",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        KTextField(
            weight,
            { weight = it },
            "몸무게",
            Modifier.fillMaxWidth(),
            variant = shape,
            suffix = "kg",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        KTextArea(bio, { bio = it }, "자기소개", Modifier.fillMaxWidth(), variant = shape, maxLength = 150, placeholder = "좋아하는 것을 적어 보세요")
    }
}

@Composable
private fun ConsentSection() {
    var agreed by rememberSaveable { mutableStateOf(setOf(0)) }
    val all = when (agreed.size) {
        0 -> ToggleableState.Off
        CONSENTS.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    KListSection(header = "약관 동의") {
        item {
            KListItem(
                "모두 동의",
                trailing = KListTrailing.Custom {
                    KTriStateCheckbox(all, null, shape = KCheckboxShape.Circle)
                },
                onClick = { agreed = if (all == ToggleableState.On) emptySet() else CONSENTS.indices.toSet() },
            )
        }
        CONSENTS.forEachIndexed { index, title ->
            item(key = title) {
                KCheckboxRow(
                    title,
                    index in agreed,
                    { agreed = if (it) agreed + index else agreed - index },
                    shape = KCheckboxShape.Circle,
                )
            }
        }
    }
}

@Composable
private fun InboxSection() {
    KListSection(header = "받은 편지함", style = KListSectionStyle.Plain) {
        INBOX.forEach { mail ->
            item(key = mail.from) {
                KListItem(
                    mail.subject,
                    overline = mail.from,
                    supporting = mail.preview,
                    supportingMaxLines = 2,
                    leading = KListLeading.Avatar(mail.from.take(1), color = mail.color),
                    trailing = KListTrailing.Value(mail.time),
                    onClick = {},
                )
            }
        }
    }
}

@Composable
private fun AboutSection() {
    KListSection(header = "정보") {
        item { KValueRow("버전", "1.0.0-alpha01") }
        item { KValueRow("저장 공간", "12.4GB 사용", leading = icon(ShowcaseIcons.Download, FormGray)) }
        item { KDisclosureRow("오픈 소스 라이선스", {}, leading = icon(ShowcaseIcons.Layers, FormIndigo)) }
    }
}

private fun icon(vector: ImageVector, color: Color) = KListLeading.Icon(vector, container = color)

private class InboxMail(val from: String, val subject: String, val preview: String, val time: String, val color: Color)

private val FormPink = Color(0xFFFF6B8A)
private val FormOrange = Color(0xFFFF9500)
private val FormBlue = Color(0xFF0A84FF)
private val FormGreen = Color(0xFF34C759)
private val FormGray = Color(0xFF8E8E93)
private val FormIndigo = Color(0xFF5E5CE6)

private val DISPLAY_MODES = listOf(
    "밝게" to null,
    "어둡게" to null,
    "시스템 설정 따르기" to "기기의 다크 모드 설정을 따릅니다",
)

private val FIELD_VARIANTS = listOf(
    "윤곽선" to KTextFieldVariant.Outlined,
    "채움" to KTextFieldVariant.Filled,
    "밑줄" to KTextFieldVariant.Underline,
    "표면" to KTextFieldVariant.Surface,
)

private val CONSENTS = listOf("서비스 이용약관 (필수)", "개인 정보 수집과 이용 (필수)", "마케팅 정보 받기 (선택)")

private val INBOX = listOf(
    InboxMail("서연", "주말 등산 일정", "토요일 아침 7시에 역 앞에서 만나요. 물이랑 간식은 제가 챙길게요.", "오전 9:41", FormGreen),
    InboxMail("디자인팀", "새 아이콘 검토 요청", "설정 화면에 들어갈 아이콘 12개를 올렸습니다. 금요일까지 의견 주세요.", "어제", FormIndigo),
    InboxMail("택배", "배송이 시작되었습니다", "주문하신 상품이 오늘 출발했습니다. 내일 오후에 도착할 예정입니다.", "월요일", FormOrange),
)

private const val TITLE_SIZE = 22
private const val BODY_SIZE = 13
