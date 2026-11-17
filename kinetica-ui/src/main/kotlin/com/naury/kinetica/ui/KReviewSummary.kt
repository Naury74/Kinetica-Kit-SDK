package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import java.text.NumberFormat

/**
 * 리뷰 요약. 평균 별점을 큰 숫자와 별(소수 자리만큼 별 일부를 채움)로, 리뷰 수와 점수별 분포 막대를 함께 보인다.
 * 분포 막대는 처음 보일 때 높은 점수부터 차례로 차오르고, '애니메이션 삭제' 설정에서는 처음부터 다 찬 모양이다.
 * 비율은 가장 큰 나머지 방식으로 반올림해 합이 100%가 된다. 막대는 RTL에서 오른쪽부터 찬다.
 *
 * [onFilter]가 있으면 막대 줄을 눌러 그 점수로 거를 수 있고, 고른 줄을 다시 누르면 거르기를 푼다. 고른 줄 밖의 막대는 옅게 한다.
 * 폭이 300dp보다 좁으면 평균과 분포를 위아래로, 넓으면 나란히 놓는다.
 *
 * TalkBack은 누를 수 없으면 '평균 4.6점, 리뷰 1,204개, 5점 72%, 4점 15%, …'를 한 번에 읽는다.
 * 누를 수 있으면 평균과 리뷰 수를 먼저 읽고, 점수 줄마다 '5점 72%, 리뷰 869개'를 목록처럼 읽으며 두 번 탭하면 거른다.
 *
 * ```kotlin
 * var stars by remember { mutableStateOf<Int?>(null) }
 * KReviewSummary(4.6f, 1_204, listOf(869, 181, 84, 36, 34), selectedStars = stars, onFilter = { stars = it })
 * ```
 *
 * @param average 평균 별점(0..점수 수). 범위 밖이거나 유한하지 않으면 [IllegalArgumentException]을 던진다.
 * @param reviewCount 리뷰 수. 음수면 [IllegalArgumentException]을 던진다. 별점 없는 리뷰가 있으면 분포 합과 달라도 된다.
 * @param distribution 높은 점수부터 낮은 점수까지 차례로 그 점수를 준 리뷰 수. 크기가 점수 수(보통 5)이고 1..10개여야 하며,
 *   음수가 있으면 [IllegalArgumentException]을 던진다.
 * @param selectedStars 지금 거르고 있는 점수. 범위 밖이면 고른 줄이 없는 것으로 본다.
 * @param onFilter 막대 줄을 누르면 그 점수를, 고른 줄을 다시 누르면 `null`을 넘긴다. `null`이면 누를 수 없다.
 * @param color 별과 막대 색
 * @param countFormatter 리뷰 수 문자열
 */
@Composable
public fun KReviewSummary(
    average: Float,
    reviewCount: Int,
    distribution: List<Int>,
    modifier: Modifier = Modifier,
    selectedStars: Int? = null,
    onFilter: ((Int?) -> Unit)? = null,
    color: Color = Color(REVIEW_GOLD),
    countFormatter: (Int) -> String = { NumberFormat.getIntegerInstance().format(it) },
) {
    require(distribution.size in 1..MAX_STARS) { "distribution은 1..${MAX_STARS}개여야 합니다." }
    require(distribution.all { it >= 0 }) { "distribution에 음수를 넣을 수 없습니다." }
    require(reviewCount >= 0) { "reviewCount는 0 이상이어야 합니다." }
    require(average.isFinite() && average in 0f..distribution.size.toFloat()) { "average는 0..${distribution.size} 안의 값이어야 합니다." }
    val stars = distribution.size
    // 호스트가 같은 SnapshotStateList를 고쳐도 참조가 그대로라 remember 키로는 모르므로, 목록 읽기를 추적한다.
    val percents by remember(distribution) {
        derivedStateOf { roundedPercents(DoubleArray(distribution.size) { distribution[it].toDouble() }) }
    }
    val picked = selectedStars?.takeIf { it in 1..stars }
    val reveal = rememberChartReveal(REVIEW_REVEAL_MS)
    val dim = animateFloatAsState(if (picked != null) 1f else 0f, spring(), label = "review-dim")
    val averageText =
        remember(average) {
            NumberFormat.getNumberInstance().apply {
                maximumFractionDigits = 1
                minimumFractionDigits = 1
            }.format(average)
        }
    val countText = pluralStringResource(R.plurals.kinetica_ui_review_count, reviewCount, countFormatter(reviewCount))
    val head = joinChartParts(listOf(stringResource(R.string.kinetica_ui_review_average, averageText), countText))
    val rows = List(stars) { i -> stringResource(R.string.kinetica_ui_data_pair, starsName(stars - i), "${percents[i]}%") }
    val look = ReviewLook(color, LocalKineticaContentColor.current, reveal, dim, picked)
    val outer = if (onFilter ==
        null
    ) {
        Modifier.clearAndSetSemantics { contentDescription = joinChartParts(listOf(head) + rows) }
    } else {
        Modifier
    }
    BoxWithConstraints(modifier.then(outer)) {
        val wide = maxWidth >= WIDE_WIDTH
        val headPart: @Composable () -> Unit = {
            ReviewHead(
                ReviewHeadText(averageText, countText, head),
                average,
                stars,
                look,
                interactive =
                    onFilter != null,
            )
        }
        val barsPart: @Composable (Modifier) -> Unit = { barsModifier ->
            ReviewBars(distribution, percents, rows, look, countFormatter, onFilter, barsModifier)
        }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(SECTION_GAP), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.widthIn(min = HEAD_MIN_WIDTH)) { headPart() }
                barsPart(Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(SECTION_GAP), horizontalAlignment = Alignment.CenterHorizontally) {
                headPart()
                barsPart(Modifier.fillMaxWidth())
            }
        }
    }
}

private class ReviewLook(
    val color: Color,
    val ink: Color,
    val reveal: Animatable<Float, AnimationVector1D>,
    val dim: State<Float>,
    val picked: Int?,
)

private class ReviewHeadText(val average: String, val count: String, val description: String)

@Composable
private fun ReviewHead(text: ReviewHeadText, average: Float, stars: Int, look: ReviewLook, interactive: Boolean) {
    val semantics = if (interactive) Modifier.clearAndSetSemantics { contentDescription = text.description } else Modifier
    Column(semantics, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(HEAD_GAP)) {
        ThemedText(text.average, style = TextStyle(color = look.ink, fontSize = AVERAGE_TEXT, fontWeight = FontWeight.Bold))
        KRating(average, null, Modifier.clearAndSetSemantics { }, max = stars, starSize = HEAD_STAR, color = look.color)
        ThemedText(
            text.count,
            style = TextStyle(color = look.ink.copy(alpha = MUTED_ALPHA), fontSize = COUNT_TEXT, textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun ReviewBars(
    distribution: List<Int>,
    percents: IntArray,
    rows: List<String>,
    look: ReviewLook,
    countFormatter: (Int) -> String,
    onFilter: ((Int?) -> Unit)?,
    modifier: Modifier,
) {
    val stars = distribution.size
    val list = if (onFilter != null) Modifier.semantics { collectionInfo = CollectionInfo(stars, 1) } else Modifier
    val filterLabel = stringResource(R.string.kinetica_ui_review_filter)
    Column(modifier.then(list), verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
        for (i in 0 until stars) {
            val score = stars - i
            val count = pluralStringResource(R.plurals.kinetica_ui_review_count, distribution[i], countFormatter(distribution[i]))
            val description = joinChartParts(listOf(rows[i], count))
            val selected = look.picked == score
            val press = if (onFilter == null) {
                Modifier
            } else {
                val toggle = { onFilter(if (selected) null else score) }
                // 줄 안 숫자와 비율 글자를 또 읽지 않도록 이름과 동작을 이 노드에 다시 둔다.
                Modifier
                    .clearAndSetSemantics {
                        contentDescription = description
                        this.selected = selected
                        role = Role.Button
                        collectionItemInfo = CollectionItemInfo(i, 1, 0, 1)
                        onClick(filterLabel) {
                            toggle()
                            true
                        }
                    }.kClickable(RoundedCornerShape(ROW_CORNER), onClickLabel = filterLabel, onClick = toggle)
            }
            ReviewRow(score, percents[i], i, look, press)
        }
    }
}

@Composable
private fun ReviewRow(score: Int, percent: Int, order: Int, look: ReviewLook, modifier: Modifier) {
    val muted = TextStyle(color = look.ink.copy(alpha = MUTED_ALPHA), fontSize = ROW_TEXT)
    val start = order * ROW_STAGGER
    val share = percent / PERCENT
    Row(
        modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT).padding(horizontal = ROW_PAD),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ROW_PAD),
    ) {
        ThemedText(score.toString(), Modifier.widthIn(min = SCORE_WIDTH), style = muted.copy(textAlign = TextAlign.Center))
        Box(
            Modifier
                .weight(1f)
                .height(BAR_HEIGHT)
                .drawBehind {
                    val radius = CornerRadius(size.height / 2f)
                    drawRoundRect(look.ink.copy(alpha = TRACK_ALPHA), cornerRadius = radius)
                    val grow = chartStage(look.reveal.value, start, start + ROW_SPAN)
                    val width = size.width * share * grow
                    if (width <= 0f) return@drawBehind
                    val alpha = if (look.picked == score) 1f else lerp(1f, CHART_DIM_ALPHA, look.dim.value)
                    val left = if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
                    drawRoundRect(look.color, Offset(left, 0f), Size(width, size.height), radius, alpha = alpha)
                },
        )
        ThemedText("$percent%", Modifier.widthIn(min = PERCENT_WIDTH), style = muted.copy(textAlign = TextAlign.End))
    }
}

@Composable
private fun starsName(score: Int): String = pluralStringResource(R.plurals.kinetica_ui_review_stars, score, score)

private const val REVIEW_GOLD = 0xFFFFB400
private const val MAX_STARS = 10
private const val REVIEW_REVEAL_MS = 1_000
private const val ROW_STAGGER = 0.1f
private const val ROW_SPAN = 0.6f
private const val PERCENT = 100f
private const val MUTED_ALPHA = 0.66f
private const val TRACK_ALPHA = 0.1f
private val WIDE_WIDTH = 300.dp
private val SECTION_GAP = 20.dp
private val HEAD_MIN_WIDTH = 112.dp
private val HEAD_GAP = 4.dp
private val HEAD_STAR = 18.dp
private val ROW_GAP = 2.dp
private val ROW_HEIGHT = 28.dp
private val ROW_PAD = 8.dp
private val ROW_CORNER = 8.dp
private val SCORE_WIDTH = 12.dp
private val PERCENT_WIDTH = 36.dp
private val BAR_HEIGHT = 8.dp
private val AVERAGE_TEXT = 44.sp
private val COUNT_TEXT = 13.sp
private val ROW_TEXT = 13.sp
