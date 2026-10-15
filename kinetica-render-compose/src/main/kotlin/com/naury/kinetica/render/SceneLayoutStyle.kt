package com.naury.kinetica.render

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.naury.kinetica.core.geometry.Size

/** 노드 크기(문서 단위 = 픽셀)를 부모 제약과 상관없이 그대로 쓴다. */
@Composable
internal fun Modifier.requiredSizePx(size: Size): Modifier = with(LocalDensity.current) {
    requiredSize(size.width.toFloat().toDp(), size.height.toFloat().toDp())
}

/** 캔버스가 이미 문서 단위로 확대되어 있으므로 글자 크기는 density나 글꼴 배율과 상관없는 문서 단위여야 한다. */
internal fun Density.unitsToSp(units: Double): TextUnit = (units.toFloat() / (density * fontScale)).sp

/** 아직 없는 이미지나 호스트 뷰처럼 그릴 수 없는 내용 자리에 표시하는 플레이스홀더 색. */
internal val PLACEHOLDER = Color(PLACEHOLDER_ARGB)

private const val PLACEHOLDER_ARGB = 0xFF8A919E
