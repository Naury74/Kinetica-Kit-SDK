package com.naury.kinetica.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * kinetica-ui 컴포넌트가 쓰는 글자 단계. `KineticaTheme.typography`로 읽어, Material Typography 없이 직접 만든 화면의 글자를
 * 키트 컴포넌트와 같은 크기와 굵기로 맞춘다.
 *
 * 각 단계에는 [KineticaTheme]의 `textStyle` 글꼴(fontFamily 등)이 입혀 있고, 굵기는 [KineticaDesign.titleWeight]와
 * [KineticaDesign.labelWeight]를 따른다. 색은 정하지 않으므로(`Color.Unspecified`) 쓰는 쪽이 `copy(color = ...)`로 넣는다.
 * 크기는 sp라 시스템 글꼴 크기 설정을 따른다.
 *
 * 지금은 읽기 전용이다. 값을 바꿔도 키트 컴포넌트의 글자는 바뀌지 않는다.
 *
 * @property largeTitle 34sp. 크게 접히는 제목([KLargeTitle])
 * @property titleLarge 26sp. 큰 상단 바 제목
 * @property title 20sp. 다이얼로그, 시트, 빈 화면 제목
 * @property titleSmall 17sp. 접힌 상단 바 제목, 작은 판의 제목
 * @property body 16sp. 목록 행 제목, 메뉴 항목, 입력란 글자
 * @property bodySmall 15sp, 줄 높이 21sp. 다이얼로그와 빈 화면 본문
 * @property supporting 14sp, 줄 높이 19sp. 목록 보조 글, 툴팁, 스낵바
 * @property caption 13sp, 줄 높이 18sp. 목록 묶음 아래 설명, 입력란 오류 글, 부가 정보
 * @property captionSmall 12sp. 목록 위 작은 글, 카드 머리말, 아이콘 아래 이름
 * @property micro 11sp. 탭 바 이름, 배지 숫자, 차트 눈금
 * @property labelLarge 18sp 라벨 굵기. 큰 버튼 글자
 * @property label 16sp 라벨 굵기. 기본 버튼 글자
 * @property labelSmall 14sp 라벨 굵기. 작은 버튼 글자
 */
@Immutable
@Suppress("LongParameterList") // 단계마다 이름 있는 값 하나씩이다.
public class KineticaTypography internal constructor(
    public val largeTitle: TextStyle,
    public val titleLarge: TextStyle,
    public val title: TextStyle,
    public val titleSmall: TextStyle,
    public val body: TextStyle,
    public val bodySmall: TextStyle,
    public val supporting: TextStyle,
    public val caption: TextStyle,
    public val captionSmall: TextStyle,
    public val micro: TextStyle,
    public val labelLarge: TextStyle,
    public val label: TextStyle,
    public val labelSmall: TextStyle,
) {
    override fun equals(other: Any?): Boolean = other is KineticaTypography && all == other.all

    override fun hashCode(): Int = all.hashCode()

    override fun toString(): String = "KineticaTypography(title=$title, body=$body, label=$label)"

    private val all: List<TextStyle>
        get() = listOf(
            largeTitle, titleLarge, title, titleSmall, body, bodySmall, supporting, caption, captionSmall, micro,
            labelLarge, label, labelSmall,
        )

    internal companion object {
        /** [font]의 글꼴과 [design]의 굵기로 만든 글자 단계 */
        fun of(font: TextStyle, design: KineticaDesign): KineticaTypography {
            val base = fontOf(font)
            fun step(size: Int, weight: FontWeight, lineHeight: Int? = null): TextStyle = base.merge(
                TextStyle(
                    fontSize = size.sp,
                    fontWeight = weight,
                    lineHeight = lineHeight?.sp ?: TextStyle.Default.lineHeight,
                ),
            )
            val labelWeight = design.label(FontWeight.SemiBold)
            return KineticaTypography(
                largeTitle = step(LARGE_TITLE, design.title(FontWeight.Bold)),
                titleLarge = step(TITLE_LARGE, design.title(FontWeight.SemiBold)),
                title = step(TITLE, design.title(FontWeight.SemiBold)),
                titleSmall = step(TITLE_SMALL, design.title(FontWeight.SemiBold)),
                body = step(BODY, FontWeight.Normal),
                bodySmall = step(BODY_SMALL, FontWeight.Normal, BODY_SMALL_LINE),
                supporting = step(SUPPORTING, FontWeight.Normal, SUPPORTING_LINE),
                caption = step(CAPTION, FontWeight.Normal, CAPTION_LINE),
                captionSmall = step(CAPTION_SMALL, FontWeight.Medium),
                micro = step(MICRO, FontWeight.Medium),
                labelLarge = step(LABEL_LARGE, labelWeight),
                label = step(LABEL, labelWeight),
                labelSmall = step(LABEL_SMALL, labelWeight),
            )
        }
    }
}

private const val LARGE_TITLE = 34
private const val TITLE_LARGE = 26
private const val TITLE = 20
private const val TITLE_SMALL = 17
private const val BODY = 16
private const val BODY_SMALL = 15
private const val BODY_SMALL_LINE = 21
private const val SUPPORTING = 14
private const val SUPPORTING_LINE = 19
private const val CAPTION = 13
private const val CAPTION_LINE = 18
private const val CAPTION_SMALL = 12
private const val MICRO = 11
private const val LABEL_LARGE = 18
private const val LABEL = 16
private const val LABEL_SMALL = 14
