// 아이콘 좌표는 24 격자 위 그림이라 이름을 붙이는 것보다 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 통화 화면에 쓰는 선 아이콘. [KIcons]와 같은 24 격자, 1.8 굵기 선이다. 마이크, 카메라, 끊기 같은 기본 아이콘은 [KIcons]에 있다.
 *
 * ```kotlin
 * KCallControlButton(KCallIcons.ScreenShare, "화면 공유", onClick = ::share)
 * ```
 */
public object KCallIcons {
    /** 화살표가 나가는 모니터. 화면 공유 */
    public val ScreenShare: ImageVector by callIcon("screen-share") {
        roundRect(3f, 4.5f, 21f, 16.5f, 2f)
        moveTo(8.5f, 20f)
        lineTo(15.5f, 20f)
        moveTo(12f, 13.5f)
        lineTo(12f, 7.5f)
        moveTo(9.5f, 10f)
        lineTo(12f, 7.5f)
        lineTo(14.5f, 10f)
    }

    /** 편 손. 손 들기 */
    public val Hand: ImageVector by callIcon("hand") {
        moveTo(7.5f, 12.5f)
        lineTo(7.5f, 6.5f)
        curveTo(7.5f, 5.2f, 9.5f, 5.2f, 9.5f, 6.5f)
        lineTo(9.5f, 11f)
        moveTo(9.5f, 10.5f)
        lineTo(9.5f, 4.8f)
        curveTo(9.5f, 3.4f, 11.5f, 3.4f, 11.5f, 4.8f)
        lineTo(11.5f, 10.5f)
        moveTo(11.5f, 10.5f)
        lineTo(11.5f, 5.5f)
        curveTo(11.5f, 4.2f, 13.5f, 4.2f, 13.5f, 5.5f)
        lineTo(13.5f, 11f)
        moveTo(13.5f, 11f)
        lineTo(13.5f, 7.5f)
        curveTo(13.5f, 6.2f, 15.5f, 6.2f, 15.5f, 7.5f)
        lineTo(15.5f, 14f)
        curveTo(15.5f, 18f, 13.5f, 20.5f, 10.5f, 20.5f)
        curveTo(8f, 20.5f, 6.8f, 19f, 5.5f, 16.5f)
        lineTo(4.2f, 13.8f)
        curveTo(3.6f, 12.6f, 5.2f, 11.6f, 6f, 12.6f)
        lineTo(7.5f, 14.5f)
    }

    /** 돌아가는 화살표가 있는 카메라. 전면과 후면 카메라 바꾸기 */
    public val SwitchCamera: ImageVector by callIcon("switch-camera") {
        moveTo(3.5f, 8.5f)
        lineTo(7.5f, 8.5f)
        lineTo(9f, 6f)
        lineTo(15f, 6f)
        lineTo(16.5f, 8.5f)
        lineTo(20.5f, 8.5f)
        lineTo(20.5f, 19f)
        lineTo(3.5f, 19f)
        close()
        moveTo(9.2f, 12.3f)
        arcTo(3f, 3f, 0f, false, true, 14.8f, 12.3f)
        moveTo(14.8f, 15.2f)
        arcTo(3f, 3f, 0f, false, true, 9.2f, 15.2f)
        moveTo(13.6f, 11.6f)
        lineTo(14.8f, 12.3f)
        lineTo(15.4f, 11f)
        moveTo(10.4f, 15.9f)
        lineTo(9.2f, 15.2f)
        lineTo(8.6f, 16.5f)
    }

    /** 블루투스 */
    public val Bluetooth: ImageVector by callIcon("bluetooth") {
        moveTo(7f, 7.5f)
        lineTo(16.5f, 16f)
        lineTo(12f, 20f)
        lineTo(12f, 4f)
        lineTo(16.5f, 8f)
        lineTo(7f, 16.5f)
    }

    /** 헤드폰. 유선 이어폰 */
    public val Headphones: ImageVector by callIcon("headphones") {
        moveTo(4f, 17f)
        lineTo(4f, 12.5f)
        arcTo(8f, 8f, 0f, false, true, 20f, 12.5f)
        lineTo(20f, 17f)
        roundRect(4f, 13.5f, 8f, 20f, 1.5f)
        roundRect(16f, 13.5f, 20f, 20f, 1.5f)
    }

    /** 휴대전화. 수화부로 듣기 */
    public val Phone: ImageVector by callIcon("phone") {
        roundRect(7f, 3f, 17f, 21f, 2.5f)
        moveTo(10.5f, 5.8f)
        lineTo(13.5f, 5.8f)
        moveTo(11f, 18f)
        lineTo(13f, 18f)
    }

    /** 웃는 얼굴. 반응 보내기 */
    public val Reaction: ImageVector by callIcon("reaction") {
        circle(12f, 12f, 8.5f)
        moveTo(8.5f, 14f)
        curveTo(9.8f, 16.5f, 14.2f, 16.5f, 15.5f, 14f)
        moveTo(9.3f, 9.8f)
        lineTo(9.3f, 10.2f)
        moveTo(14.7f, 9.8f)
        lineTo(14.7f, 10.2f)
    }

    /** 압정. 고정 */
    public val Pin: ImageVector by callIcon("pin") {
        moveTo(9f, 4f)
        lineTo(15f, 4f)
        moveTo(10f, 4f)
        lineTo(10f, 9.5f)
        lineTo(7f, 13f)
        lineTo(17f, 13f)
        lineTo(14f, 9.5f)
        lineTo(14f, 4f)
        moveTo(12f, 13f)
        lineTo(12f, 20.5f)
    }

    /** 큰 칸 하나와 작은 칸 셋. 발표자 보기 */
    public val SpeakerView: ImageVector by callIcon("speaker-view") {
        roundRect(3.5f, 4f, 20.5f, 14f, 2f)
        roundRect(3.5f, 16.5f, 8.5f, 20f, 1f)
        roundRect(9.5f, 16.5f, 14.5f, 20f, 1f)
        roundRect(15.5f, 16.5f, 20.5f, 20f, 1f)
    }

    /** 같은 크기 네 칸. 갤러리 보기 */
    public val GalleryView: ImageVector by callIcon("gallery-view") {
        roundRect(3.5f, 3.5f, 11f, 11f, 1.5f)
        roundRect(13f, 3.5f, 20.5f, 11f, 1.5f)
        roundRect(3.5f, 13f, 11f, 20.5f, 1.5f)
        roundRect(13f, 13f, 20.5f, 20.5f, 1.5f)
    }

    /** 큰 창 안 작은 창. 화면 속 화면 */
    public val PictureInPicture: ImageVector by callIcon("pip") {
        roundRect(3f, 5f, 21f, 19f, 2f)
        roundRect(12f, 11.5f, 18.5f, 16.5f, 1f)
    }

    /** 반짝이는 별. 배경 효과 */
    public val Effects: ImageVector by callIcon("effects") {
        moveTo(10f, 4f)
        curveTo(10.6f, 8.2f, 11.8f, 9.4f, 16f, 10f)
        curveTo(11.8f, 10.6f, 10.6f, 11.8f, 10f, 16f)
        curveTo(9.4f, 11.8f, 8.2f, 10.6f, 4f, 10f)
        curveTo(8.2f, 9.4f, 9.4f, 8.2f, 10f, 4f)
        close()
        moveTo(17.5f, 14.5f)
        lineTo(17.5f, 20.5f)
        moveTo(14.5f, 17.5f)
        lineTo(20.5f, 17.5f)
    }

    /** 아래로 향한 꺾쇠가 달린 창. 통화 화면 줄이기 */
    public val Minimize: ImageVector by callIcon("minimize") {
        moveTo(6f, 9.5f)
        lineTo(12f, 15.5f)
        lineTo(18f, 9.5f)
    }
}

private fun callIcon(name: String, block: PathBuilder.() -> Unit): Lazy<ImageVector> = lazy(LazyThreadSafetyMode.PUBLICATION) {
    ImageVector.Builder(
        name = "kinetica-call-$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    ).build()
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, true, true, cx + r, cy)
    arcTo(r, r, 0f, true, true, cx - r, cy)
    close()
}

private fun PathBuilder.roundRect(left: Float, top: Float, right: Float, bottom: Float, r: Float) {
    moveTo(left + r, top)
    lineTo(right - r, top)
    arcTo(r, r, 0f, false, true, right, top + r)
    lineTo(right, bottom - r)
    arcTo(r, r, 0f, false, true, right - r, bottom)
    lineTo(left + r, bottom)
    arcTo(r, r, 0f, false, true, left, bottom - r)
    lineTo(left, top + r)
    arcTo(r, r, 0f, false, true, left + r, top)
    close()
}
