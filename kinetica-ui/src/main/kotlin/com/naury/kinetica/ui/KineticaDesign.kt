package com.naury.kinetica.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 표면이 바탕에서 떨어져 보이게 하는 방식. [SurfaceStyle]의 그림자와 테두리를 이 값에 맞게 바꿔 그린다. */
public enum class KDepth {
    /** 표면 스타일 그대로 부드러운 그림자를 드리운다. */
    Shadow,

    /** 그림자 대신 [KineticaDesign.borderWidth] 두께의 옅은 테두리로 구분한다. 다이얼로그처럼 떠 있는 판은 옅은 그림자를 남긴다. */
    Border,

    /** 그림자와 테두리 없이 색 차이로만 구분한다. 떠 있는 판에는 그림자를 남긴다. */
    Flat,

    /** 글자 색으로 두꺼운 테두리를 두르고, 오른쪽 아래로 비낀 단단한 그림자를 깐다(네오 브루탈리즘). 채운 버튼도 같은 테두리를 두른다. */
    Offset,
}

/** 컴포넌트 높이와 안쪽 여백의 밀도 */
public enum class KDensity(internal val height: Float, internal val padding: Float) {
    /** 높이를 약 14%, 여백을 25% 줄인다. 데스크톱 앱이나 정보가 많은 화면에 쓴다. */
    Compact(COMPACT_HEIGHT, COMPACT_PADDING),

    /** 기본 높이와 여백 */
    Regular(1f, 1f),

    /** 높이를 약 12%, 여백을 25% 늘린다. 손가락으로 누르기 편하고 여유 있어 보인다. */
    Spacious(SPACIOUS_HEIGHT, SPACIOUS_PADDING),
}

/** 목록 섹션 안 행을 나누는 방식 */
public enum class KListSeparator {
    /** 판 하나에 행을 담고, 행 글자 시작부터 얇은 구분선을 긋는다. */
    Inset,

    /** 판 하나에 행을 담고, 판 끝에서 끝까지 구분선을 긋는다. */
    Full,

    /** 구분선 없이 판 하나에 행을 담는다. */
    None,

    /** 행마다 따로 판을 두고 2dp 틈을 둔다. 섹션 바깥 모서리는 크게, 행 사이 모서리는 작게 둥글린다. */
    Gap,
}

/** [KSlider]와 [KRangeSlider] 손잡이 모양 */
public enum class KSliderThumb {
    /** 가로로 긴 흰 캡슐. 잡으면 커지고 반투명해진다. */
    Pill,

    /** 흰 원 */
    Circle,

    /** 강조색 세로 막대. 트랙은 손잡이 양옆에서 끊긴다. */
    Bar,

    /** 흰 원 안에 강조색 점. 잡으면 점이 커진다. */
    Ring,

    /** 글자 색 테두리를 두른 정사각형 */
    Square,
}

/**
 * 컨트롤의 기본 모양. 컴포넌트에 직접 넘긴 값이 있으면 그 값이 우선한다.
 *
 * 체크박스 상자 모서리는 [KineticaShapes.checkbox], 라디오 테두리는 [KineticaShapes.radio]가 정한다.
 *
 * @property switch [KSwitch] 모양
 * @property sliderThumb [KSlider]와 [KRangeSlider] 손잡이 모양
 */
@Immutable
public data class KineticaControls(val switch: KSwitchVariant = KSwitchVariant.Capsule, val sliderThumb: KSliderThumb = KSliderThumb.Pill)

/**
 * 모션 성격. 인디케이터 이동, 스위치 손잡이, 다이얼로그 열림, 버튼 눌림처럼 공간이 움직이는 애니메이션이 이 스프링을 쓴다.
 * '애니메이션 삭제' 설정이 켜져 있으면 이 값과 상관없이 바로 바뀐다.
 *
 * 생성할 때 [damping]이 0 이하거나 [stiffness]가 0 이하거나 [press]가 0..0.3을 벗어나면 [IllegalArgumentException]을 던진다.
 *
 * @property damping 감쇠비. 1이면 넘치지 않고 멈추고, 작을수록 크게 출렁인다.
 * @property stiffness 강성. 클수록 빨리 도착한다.
 * @property press 누를 때 줄어드는 비율(0..0.3). 0이면 크기가 바뀌지 않는다.
 */
@Immutable
public data class KineticaMotion(val damping: Float, val stiffness: Float, val press: Float) {
    init {
        require(damping > 0f && stiffness > 0f) { "damping과 stiffness는 0보다 커야 합니다." }
        require(press in 0f..MAX_PRESS) { "press는 0..0.3이어야 합니다." }
    }

    /** 이 모션의 스프링 */
    public fun <T> spring(): AnimationSpec<T> = androidx.compose.animation.core.spring(dampingRatio = damping, stiffness = stiffness)

    public companion object {
        /** 통통 튀며 도착한다. 젤리처럼 눌린다. */
        @JvmField public val Bouncy: KineticaMotion = KineticaMotion(damping = 0.62f, stiffness = 420f, press = 0.07f)

        /** 크게 넘치지 않고 빠르게 붙는다. 살짝 튀는 정도는 남긴다. */
        @JvmField public val Expressive: KineticaMotion = KineticaMotion(damping = 0.8f, stiffness = 700f, press = 0.05f)

        /** 넘치지 않고 부드럽게 멈춘다. */
        @JvmField public val Calm: KineticaMotion = KineticaMotion(damping = 1f, stiffness = 260f, press = 0.03f)

        /** 넘치지 않고 빠르게 멈춘다. 눌러도 크기가 거의 바뀌지 않는다. */
        @JvmField public val Snappy: KineticaMotion = KineticaMotion(damping = 1f, stiffness = 900f, press = 0.02f)
    }
}

/**
 * 디자인 언어. 색과 블러를 정하는 [SurfaceStyle]과 따로, 컴포넌트의 '형태'를 정한다. 모서리 모양, 깊이 표현, 테두리 두께, 밀도,
 * 글자 굵기, 탭 인디케이터, 컨트롤 모양, 모션 성격이 함께 바뀐다. 표면 스타일과 자유롭게 조합한다(예: 글래스 표면에 브루탈 형태).
 *
 * [KineticaTheme]의 `design`으로 앱 전체에 적용하고, 컴포넌트마다 `shape`, `variant` 같은 파라미터로 덮어쓴다. 프리셋은 [KineticaDesigns]에 있다.
 * 생성할 때 [borderWidth]가 음수면 [IllegalArgumentException]을 던진다.
 *
 * @property shapes 모서리 체계와 컴포넌트 역할별 모양
 * @property depth 표면을 바탕에서 띄워 보이는 방식
 * @property borderWidth [KDepth.Border]와 [KDepth.Offset]의 테두리, [KVariant.Outlined]의 테두리 두께
 * @property density 높이와 안쪽 여백
 * @property labelWeight 버튼, 탭, 칩 이름의 굵기
 * @property titleWeight 다이얼로그, 시트, 카드 제목의 굵기
 * @property indicator 탭과 탭 바, 세그먼트 컨트롤의 선택 인디케이터 모양
 * @property listSeparator 목록 섹션 안 행을 나누는 방식
 * @property controls 스위치, 체크박스, 슬라이더 모양
 * @property motion 모션 성격
 */
@Immutable
public data class KineticaDesign(
    val shapes: KineticaShapes = KineticaShapes(),
    val depth: KDepth = KDepth.Shadow,
    val borderWidth: Dp = 1.dp,
    val density: KDensity = KDensity.Regular,
    val labelWeight: FontWeight = FontWeight.SemiBold,
    val titleWeight: FontWeight = FontWeight.Bold,
    val indicator: KTabIndicator = KTabIndicator.Pill,
    val listSeparator: KListSeparator = KListSeparator.Inset,
    val controls: KineticaControls = KineticaControls(),
    val motion: KineticaMotion = KineticaMotion.Bouncy,
) {
    init {
        require(borderWidth >= 0.dp) { "borderWidth는 0 이상이어야 합니다." }
    }
}

/**
 * 디자인 언어 프리셋. 이름은 그 디자인 체계를 닮았다는 뜻이고, 색은 표면 스타일([SurfaceStyles])과 [KineticaColors]가 따로 정한다.
 *
 * | 프리셋 | 모서리 | 깊이 | 밀도 | 인디케이터 | 컨트롤 | 모션 |
 * | --- | --- | --- | --- | --- | --- | --- |
 * | [Cupertino] | 캡슐 버튼, 24dp 카드 | 부드러운 그림자 | 보통 | 옅은 캡슐 | 캡슐 스위치 | 통통 튐 |
 * | [Material] | 캡슐 버튼, 12dp 카드, 8dp 칩, 28dp 다이얼로그 | 평면 | 보통 | 채운 캡슐 | 체크 스위치, 막대 손잡이 | 빠르고 살짝 튐 |
 * | [OneUi] | 캡슐 버튼, 28dp 카드 | 평면 | 넉넉함 | 옅은 캡슐 | 얇은 스위치, 원 손잡이 | 차분함 |
 * | [Fluent] | 4dp 버튼, 8dp 카드 | 테두리 | 조밀함 | 밑줄 | 테두리 스위치, 링 손잡이 | 빠름 |
 * | [Brutal] | 각진 모서리 | 단단한 오프셋 그림자 | 보통 | 채운 칸 | 사각 스위치와 손잡이 | 빠름 |
 * | [Soft] | 스쿼클, 큰 반지름 | 부드러운 그림자 | 넉넉함 | 옅은 칸 | 캡슐 스위치, 원 손잡이 | 차분함 |
 */
public object KineticaDesigns {
    /** iOS를 닮은 형태. 캡슐 버튼과 칩, 큰 둥근 카드, 얇은 구분선. [KineticaTheme]의 기본값이다. */
    @JvmField public val Cupertino: KineticaDesign = KineticaDesign()

    /** Material 3 Expressive를 닮은 형태. 역할마다 다른 모서리, 채운 인디케이터, 행마다 따로 놓인 목록. */
    @JvmField
    public val Material: KineticaDesign = KineticaDesign(
        shapes = KineticaShapes(
            extraSmall = 4.dp,
            small = 8.dp,
            medium = 12.dp,
            large = 16.dp,
            extraLarge = 28.dp,
            huge = 28.dp,
            chip = KShapeToken.Small,
            card = KShapeToken.Medium,
            field = KShapeToken.ExtraSmall,
            menu = KShapeToken.Large,
            toast = KShapeToken.ExtraSmall,
            tabBar = KShapeToken.ExtraLarge,
            listSection = KShapeToken.Large,
            checkbox = KShapeToken.ExtraSmall,
            image = KShapeToken.Medium,
        ),
        depth = KDepth.Flat,
        labelWeight = FontWeight.Medium,
        titleWeight = FontWeight.Normal,
        indicator = KTabIndicator.Filled,
        listSeparator = KListSeparator.Gap,
        controls = KineticaControls(switch = KSwitchVariant.Check, sliderThumb = KSliderThumb.Bar),
        motion = KineticaMotion.Expressive,
    )

    /** One UI를 닮은 형태. 아주 둥근 카드와 목록 판, 넉넉한 높이, 굵은 제목. */
    @JvmField
    public val OneUi: KineticaDesign = KineticaDesign(
        shapes = KineticaShapes(
            extraSmall = 8.dp,
            small = 12.dp,
            medium = 18.dp,
            large = 26.dp,
            extraLarge = 28.dp,
            huge = 32.dp,
            card = KShapeToken.Large,
            field = KShapeToken.Full,
            menu = KShapeToken.Large,
            toast = KShapeToken.Full,
            listSection = KShapeToken.Large,
            checkbox = KShapeToken.Full,
            image = KShapeToken.Medium,
        ),
        depth = KDepth.Flat,
        density = KDensity.Spacious,
        labelWeight = FontWeight.SemiBold,
        titleWeight = FontWeight.ExtraBold,
        listSeparator = KListSeparator.Inset,
        controls = KineticaControls(switch = KSwitchVariant.Slim, sliderThumb = KSliderThumb.Circle),
        motion = KineticaMotion.Calm,
    )

    /** Fluent 2를 닮은 형태. 작은 반지름, 그림자 대신 테두리, 조밀한 높이, 밑줄 인디케이터. */
    @JvmField
    public val Fluent: KineticaDesign = KineticaDesign(
        shapes = KineticaShapes(
            extraSmall = 2.dp,
            small = 4.dp,
            medium = 4.dp,
            large = 8.dp,
            extraLarge = 8.dp,
            huge = 8.dp,
            button = KShapeToken.Small,
            chip = KShapeToken.Small,
            card = KShapeToken.Large,
            field = KShapeToken.Small,
            search = KShapeToken.Small,
            menu = KShapeToken.Large,
            toast = KShapeToken.Small,
            tabBar = KShapeToken.Large,
            indicator = KShapeToken.Small,
            listSection = KShapeToken.Large,
            checkbox = KShapeToken.Small,
            badge = KShapeToken.Full,
            image = KShapeToken.Small,
        ),
        depth = KDepth.Border,
        density = KDensity.Compact,
        labelWeight = FontWeight.Normal,
        titleWeight = FontWeight.SemiBold,
        indicator = KTabIndicator.Underline,
        listSeparator = KListSeparator.Full,
        controls = KineticaControls(switch = KSwitchVariant.Outline, sliderThumb = KSliderThumb.Ring),
        motion = KineticaMotion.Snappy,
    )

    /** 네오 브루탈리즘. 각진 모서리, 두꺼운 테두리, 오른쪽 아래로 비낀 단단한 그림자, 아주 굵은 글자. */
    @JvmField
    public val Brutal: KineticaDesign = KineticaDesign(
        shapes = KineticaShapes(
            corner = KCornerStyle.Square,
            avatar = KShapeToken(KShapeSize.Full, KCornerStyle.Round),
            radio = KShapeToken(KShapeSize.Full, KCornerStyle.Round),
        ),
        depth = KDepth.Offset,
        borderWidth = 2.dp,
        labelWeight = FontWeight.ExtraBold,
        titleWeight = FontWeight.Black,
        indicator = KTabIndicator.Filled,
        listSeparator = KListSeparator.Full,
        controls = KineticaControls(switch = KSwitchVariant.Block, sliderThumb = KSliderThumb.Square),
        motion = KineticaMotion.Snappy,
    )

    /** 부드러운 형태. 스쿼클 모서리, 큰 반지름, 넉넉한 높이, 차분한 모션. */
    @JvmField
    public val Soft: KineticaDesign = KineticaDesign(
        shapes = KineticaShapes(
            corner = KCornerStyle.Squircle,
            extraSmall = 8.dp,
            small = 12.dp,
            medium = 16.dp,
            large = 24.dp,
            extraLarge = 32.dp,
            huge = 40.dp,
            button = KShapeToken.Small,
            chip = KShapeToken.ExtraSmall,
            card = KShapeToken.ExtraLarge,
            field = KShapeToken.Medium,
            search = KShapeToken.Large,
            menu = KShapeToken.Large,
            toast = KShapeToken.Large,
            tabBar = KShapeToken.Medium,
            indicator = KShapeToken.Small,
            listSection = KShapeToken.ExtraLarge,
            avatar = KShapeToken.Large,
            checkbox = KShapeToken.Small,
            image = KShapeToken.Medium,
        ),
        density = KDensity.Spacious,
        labelWeight = FontWeight.Medium,
        titleWeight = FontWeight.SemiBold,
        indicator = KTabIndicator.Segmented,
        listSeparator = KListSeparator.None,
        controls = KineticaControls(sliderThumb = KSliderThumb.Circle),
        motion = KineticaMotion.Calm,
    )

    /** 모든 프리셋. 이름과 함께 고르기 화면에 늘어놓을 때 쓴다. */
    @JvmStatic
    public val all: List<Pair<String, KineticaDesign>>
        get() = listOf(
            "Cupertino" to Cupertino,
            "Material" to Material,
            "One UI" to OneUi,
            "Fluent" to Fluent,
            "Brutal" to Brutal,
            "Soft" to Soft,
        )
}

/** 지금 적용된 디자인 언어. [KineticaTheme] 밖에서는 [KineticaDesigns.Cupertino]다. */
public val LocalKineticaDesign: ProvidableCompositionLocal<KineticaDesign> = staticCompositionLocalOf { KineticaDesigns.Cupertino }

/** 지금 디자인 언어의 모양 토큰 */
internal val currentShapes: KineticaShapes
    @Composable
    @ReadOnlyComposable
    get() = LocalKineticaDesign.current.shapes

/** [token]을 지금 디자인 언어 모양으로 */
@Composable
@ReadOnlyComposable
internal fun shapeFor(token: KineticaShapes.() -> KShapeToken): androidx.compose.ui.graphics.Shape {
    val shapes = LocalKineticaDesign.current.shapes
    return shapes.shape(shapes.token())
}

/** 밀도에 맞춘 높이. 0.5dp 단위로 반올림한다. */
internal fun KDensity.height(base: Dp): Dp = if (this == KDensity.Regular) base else ((base.value * height * 2).roundToHalf()).dp

/** 밀도에 맞춘 안쪽 여백 */
internal fun KDensity.padding(base: Dp): Dp = if (this == KDensity.Regular) base else ((base.value * padding * 2).roundToHalf()).dp

private fun Float.roundToHalf(): Float = kotlin.math.round(this) / 2f

/** 떠 있는 판으로 볼 그림자 크기. 이 이상이면 [KDepth.Flat]과 [KDepth.Border]에서도 그림자를 남긴다. */
internal val FLOATING_ELEVATION: Dp = 14.dp

/** [KDepth.Offset]의 그림자 거리 */
internal fun KineticaDesign.offsetDistance(floating: Boolean): Dp = (borderWidth * OFFSET_FACTOR).coerceAtLeast(MIN_OFFSET) +
    if (floating) FLOATING_EXTRA else 0.dp

/** [KDepth.Border]의 테두리 색 */
internal fun borderInk(ink: Color, dark: Boolean): Color = ink.copy(alpha = if (dark) DARK_BORDER_ALPHA else LIGHT_BORDER_ALPHA)

private const val MAX_PRESS = 0.3f
private const val OFFSET_FACTOR = 2f
private val MIN_OFFSET = 3.dp
private val FLOATING_EXTRA = 2.dp
private const val LIGHT_BORDER_ALPHA = 0.16f
private const val DARK_BORDER_ALPHA = 0.22f

/** [shape]을 [topLeft]에서 [size] 크기로 그린다. 인디케이터처럼 크기가 계속 바뀌는 모양을 그리기 단계에서 그릴 때 쓴다. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShape(
    shape: androidx.compose.ui.graphics.Shape,
    color: Color,
    topLeft: androidx.compose.ui.geometry.Offset,
    size: androidx.compose.ui.geometry.Size,
    style: androidx.compose.ui.graphics.drawscope.DrawStyle = androidx.compose.ui.graphics.drawscope.Fill,
) {
    if (size.width <= 0f || size.height <= 0f) return
    val outline = shape.createOutline(size, layoutDirection, this)
    translate(topLeft.x, topLeft.y) { drawOutline(outline, color, style = style) }
}

/** 고르지 않은 항목 이름의 굵기. [KineticaDesign.labelWeight]보다 한두 단계 가볍게 쓴다. */
internal val KineticaDesign.idleWeight: FontWeight
    get() = FontWeight((labelWeight.weight - IDLE_WEIGHT_STEP).coerceAtLeast(FontWeight.Normal.weight))

private const val IDLE_WEIGHT_STEP = 100

/**
 * 컴포넌트가 직접 맞춘 스프링([damping], [stiffness])을 이 모션 성격에 맞춘다. [KineticaMotion.Bouncy]면 컴포넌트 값을 그대로 쓰고,
 * 다른 성격이면 그 성격의 값을 쓴다. 기본 모양이 바뀌지 않게 하려는 것이다.
 */
internal fun <T> KineticaMotion.tuned(damping: Float, stiffness: Float): androidx.compose.animation.core.SpringSpec<T> =
    if (this == KineticaMotion.Bouncy) {
        androidx.compose.animation.core.spring(dampingRatio = damping, stiffness = stiffness)
    } else {
        androidx.compose.animation.core.spring(dampingRatio = this.damping, stiffness = this.stiffness)
    }

/**
 * 제목 글자 굵기. 컴포넌트가 정한 [base]를 [KineticaDesign.titleWeight]가 기본값(Bold)에서 벗어난 만큼 옮긴다.
 * 기본 디자인 언어에서는 [base] 그대로다.
 */
internal fun KineticaDesign.title(base: FontWeight): FontWeight =
    FontWeight((base.weight + titleWeight.weight - FontWeight.Bold.weight).coerceIn(FontWeight.Normal.weight, FontWeight.Black.weight))

/** 라벨 글자 굵기. [base]를 [KineticaDesign.labelWeight]가 기본값(SemiBold)에서 벗어난 만큼 옮긴다. */
internal fun KineticaDesign.label(base: FontWeight): FontWeight =
    FontWeight((base.weight + labelWeight.weight - FontWeight.SemiBold.weight).coerceIn(FontWeight.Normal.weight, FontWeight.Black.weight))

private const val COMPACT_HEIGHT = 0.86f
private const val COMPACT_PADDING = 0.75f
private const val SPACIOUS_HEIGHT = 1.12f
private const val SPACIOUS_PADDING = 1.25f
