package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.roundToInt

/** [KPriceTag] 글자 크기 */
public enum class KPriceSize(internal val price: TextUnit, internal val small: TextUnit) {
    /** 목록 행과 장바구니 줄 */
    Small(15.sp, 12.sp),

    /** 상품 카드 */
    Medium(19.sp, 13.sp),

    /** 상품 상세 화면과 요금제 카드 */
    Large(30.sp, 15.sp),
}

/** 가격 글자를 만드는 도우미 */
public object KPriceDefaults {
    /** [amount]를 [currency] 통화 형식으로 쓴다. 예: 한국어 기기에서 KRW 89000 → "₩89,000" */
    @JvmStatic
    public fun format(amount: Double, currency: Currency, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getCurrencyInstance(locale).apply {
            this.currency = currency
            // 기기 언어의 소수 자릿수가 아니라 통화의 소수 자릿수를 따른다. 미국 영어에서 원화를 쓰면 ₩89,000.00이 되지 않게 한다.
            minimumFractionDigits = currency.defaultFractionDigits.coerceAtLeast(0)
            maximumFractionDigits = currency.defaultFractionDigits.coerceAtLeast(0)
        }.format(amount)

    /** 정가 [original]에서 [price]로 내린 할인율(%). 정가가 0 이하이거나 가격이 더 비싸면 0이다. */
    @JvmStatic
    public fun discountPercent(price: Double, original: Double): Int =
        if (original <= 0.0 || price >= original) 0 else ((1 - price / original) * PERCENT).roundToInt()
}

/**
 * 가격표. 할인율, 판매가, 취소선을 그은 정가를 한 줄에 놓는다. 할인율은 [KineticaColors.destructive]로 칠하고 대비가 모자라면 진하게 고친다.
 * 줄이 모자라면 정가가 다음 줄로 내려간다. TalkBack은 '89,000원, 정가 110,000원, 19% 할인'처럼 한 번에 읽는다.
 *
 * ```kotlin
 * KPriceTag("₩89,000", originalPrice = "₩110,000", discountPercent = KPriceDefaults.discountPercent(89_000.0, 110_000.0))
 * ```
 *
 * @param price 판매가 글자. 형식은 [KPriceDefaults.format]으로 만들 수 있다.
 * @param originalPrice 정가 글자. `null`이면 숨긴다.
 * @param discountPercent 할인율(%). `null`이거나 0 이하면 숨긴다.
 * @param period 가격 뒤 기간 글자. 예: "/월"
 */
@Composable
public fun KPriceTag(
    price: String,
    modifier: Modifier = Modifier,
    originalPrice: String? = null,
    discountPercent: Int? = null,
    period: String? = null,
    size: KPriceSize = KPriceSize.Medium,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val ink = surface.contentColor
    val sale = discountPercent?.takeIf { it > 0 }
    val description = joinChartParts(
        listOfNotNull(
            price + period.orEmpty(),
            originalPrice?.let { stringResource(R.string.kinetica_ui_price_original, it) },
            sale?.let { stringResource(R.string.kinetica_ui_price_discount, it) },
        ),
    )
    FlowRow(
        modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.Bottom,
    ) {
        if (sale != null) {
            val red = readableOn(KineticaTheme.colors.destructive, surface.inkBackground())
            ThemedText("$sale%", style = TextStyle(color = red, fontSize = size.price, fontWeight = FontWeight.Bold))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            ThemedText(price, style = TextStyle(color = ink, fontSize = size.price, fontWeight = FontWeight.Bold))
            period?.let {
                ThemedText(
                    it,
                    Modifier.padding(start = 2.dp, bottom = 2.dp),
                    style = TextStyle(color = ink.copy(alpha = MUTED), fontSize = size.small),
                )
            }
        }
        originalPrice?.let {
            ThemedText(
                it,
                Modifier.padding(bottom = 2.dp),
                style = TextStyle(color = ink.copy(alpha = STRUCK), fontSize = size.small, textDecoration = TextDecoration.LineThrough),
            )
        }
    }
}

/**
 * 요금제 카드. 이름, 설명, 가격, 기능 목록을 담고 눌러서 고른다(라디오). 고르면 테두리가 강조색으로 굵어지고 체크 원이 나온다.
 * [recommendedLabel]을 주면 위 테두리에 걸친 배지를 달아 추천 요금제를 표시한다. 같은 묶음 카드의 부모에 `Modifier.selectableGroup()`을 붙인다.
 * [ctaLabel]과 [onCta]를 주면 아래에 버튼을 둔다. 고른 카드의 버튼만 채운 모양이다.
 *
 * ```kotlin
 * KPricingCard("프로", "₩9,900", plan == Pro, { plan = Pro }, period = "/월", features = listOf("광고 없음", "기기 5대"), recommendedLabel = "추천")
 * ```
 *
 * @param features 기능 목록. 줄마다 앞에 체크 표시를 붙인다.
 */
@Composable
public fun KPricingCard(
    title: String,
    price: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    period: String? = null,
    description: String? = null,
    originalPrice: String? = null,
    features: List<String> = emptyList(),
    recommendedLabel: String? = null,
    ctaLabel: String? = null,
    onCta: (() -> Unit)? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    Box(modifier) {
        KSelectableCard(
            selected,
            { onSelect() },
            Modifier.fillMaxWidth().padding(top = if (recommendedLabel != null) BADGE_ROOM else 0.dp),
            role = Role.RadioButton,
            contentPadding = 20.dp,
            surface = surface,
        ) {
            val ink = LocalKineticaContentColor.current
            Column(Modifier.padding(end = CHECK_ROOM), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ThemedText(
                    title,
                    Modifier.semantics {
                        heading()
                    },
                    style = TextStyle(color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                )
                description?.let { ThemedText(it, style = TextStyle(color = ink.copy(alpha = MUTED), fontSize = 13.sp)) }
            }
            KPriceTag(price, Modifier.padding(top = 4.dp), originalPrice, period = period, size = KPriceSize.Large, surface = surface)
            if (features.isNotEmpty()) {
                Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    features.forEach { feature ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Glyph(KGlyphs.Check, KineticaTheme.colors.accent, Modifier.padding(top = 1.dp), size = 18.dp)
                            ThemedText(feature, style = TextStyle(color = ink, fontSize = 14.sp, lineHeight = 20.sp))
                        }
                    }
                }
            }
            if (ctaLabel != null && onCta != null) {
                KButton(
                    ctaLabel,
                    onCta,
                    Modifier.padding(top = 8.dp),
                    variant = if (selected) KVariant.Filled else KVariant.Tonal,
                    fullWidth = true,
                    surface = surface,
                )
            }
        }
        recommendedLabel?.let {
            KBadge(it, Modifier.align(Alignment.TopCenter), tone = KTone.Accent, surface = surface)
        }
    }
}

/**
 * 월간·연간 결제 기간 전환. [KSegmentedControl] 옆에 연간 할인 배지([yearlyBadge])를 붙인다.
 * 요금제 카드 묶음 위에 두고, 바뀐 값으로 카드 가격을 바꾼다.
 *
 * ```kotlin
 * KBillingPeriodToggle(yearly, { yearly = it }, yearlyBadge = "2개월 무료")
 * ```
 *
 * @param monthlyLabel 월간 칸 이름. `null`이면 기기 언어의 기본 이름이다.
 * @param yearlyLabel 연간 칸 이름. `null`이면 기기 언어의 기본 이름이다.
 */
@Composable
public fun KBillingPeriodToggle(
    yearly: Boolean,
    onYearlyChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    yearlyBadge: String? = null,
    monthlyLabel: String? = null,
    yearlyLabel: String? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val options = listOf(
        monthlyLabel ?: stringResource(R.string.kinetica_ui_billing_monthly),
        yearlyLabel ?: stringResource(R.string.kinetica_ui_billing_yearly),
    )
    // 큰 글자에서 한 줄에 들어가지 않으면 배지를 다음 줄로 내린다.
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        KSegmentedControl(options, if (yearly) 1 else 0, { onYearlyChange(it == 1) }, Modifier.widthIn(min = TOGGLE_MIN), surface = surface)
        yearlyBadge?.let { KBadge(it, tone = KTone.Positive, variant = KVariant.Tonal, surface = surface) }
    }
}

private const val MUTED = 0.64f
private const val STRUCK = 0.5f
private const val PERCENT = 100
private val BADGE_ROOM = 11.dp
private val CHECK_ROOM = 28.dp
private val TOGGLE_MIN = 200.dp
