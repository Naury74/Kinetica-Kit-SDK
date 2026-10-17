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
import kotlin.math.cos
import kotlin.math.sin

/**
 * 앱 화면에서 자주 쓰는 선 아이콘 63개. 아이콘 라이브러리 없이 24 격자에 1.8 굵기 선으로 그렸고, 기본 크기는 24dp다.
 * 색은 `Icon`의 tint나 컴포넌트의 글자 색이 입히므로 그대로 [KIconButton], [KButton], `Icon`에 넘기면 된다.
 *
 * 방향이 있는 아이콘([ChevronBack], [ChevronForward], [ArrowBack], [ArrowForward], [Send])은 autoMirror라서
 * RTL 레이아웃에서 좌우가 저절로 뒤집힌다. 아이콘은 처음 읽을 때 한 번만 만든다.
 *
 * ```
 * KIconButton(KIcons.Share, "공유", onClick = ::share)
 * ```
 */
public object KIcons {
    /** 집. 홈 탭 */
    public val Home: ImageVector by icon("home") {
        moveTo(4f, 11f)
        lineTo(12f, 4f)
        lineTo(20f, 11f)
        moveTo(6f, 9.5f)
        lineTo(6f, 19.5f)
        lineTo(18f, 19.5f)
        lineTo(18f, 9.5f)
        moveTo(10f, 19.5f)
        lineTo(10f, 14.5f)
        lineTo(14f, 14.5f)
        lineTo(14f, 19.5f)
    }

    /** 돋보기. 검색 */
    public val Search: ImageVector by icon("search") {
        circle(10.5f, 10.5f, 6.5f)
        moveTo(15.3f, 15.3f)
        lineTo(20f, 20f)
    }

    /** 톱니바퀴. 설정 */
    public val Settings: ImageVector by icon("settings") {
        for (i in 0 until GEAR_TEETH) {
            val base = i * 2 * Math.PI / GEAR_TEETH
            val half = Math.PI / GEAR_TEETH
            point(base - half * 0.4, GEAR_OUTER, first = i == 0)
            point(base + half * 0.4, GEAR_OUTER)
            point(base + half * 0.75, GEAR_INNER)
            point(base + half * 1.25, GEAR_INNER)
        }
        close()
        circle(12f, 12f, 2.8f)
    }

    /** 더하기. 추가, 새로 만들기 */
    public val Plus: ImageVector by icon("plus") {
        moveTo(12f, 5f)
        lineTo(12f, 19f)
        moveTo(5f, 12f)
        lineTo(19f, 12f)
    }

    /** 빼기. 수량 줄이기, 접기 */
    public val Minus: ImageVector by icon("minus") {
        moveTo(5f, 12f)
        lineTo(19f, 12f)
    }

    /** X 표. 닫기, 지우기 */
    public val Close: ImageVector by icon("close") {
        moveTo(6f, 6f)
        lineTo(18f, 18f)
        moveTo(18f, 6f)
        lineTo(6f, 18f)
    }

    /** 체크 표시. 완료, 선택됨 */
    public val Check: ImageVector by icon("check") {
        moveTo(5f, 12.5f)
        lineTo(10f, 17.5f)
        lineTo(19f, 7f)
    }

    /** 가로 점 세 개. 더보기 메뉴 */
    public val More: ImageVector by icon("more") {
        circle(5.5f, 12f, DOT)
        circle(12f, 12f, DOT)
        circle(18.5f, 12f, DOT)
    }

    /** 세로 점 세 개. 앱 바 끝의 더보기 메뉴 */
    public val MoreVertical: ImageVector by icon("more-vertical") {
        circle(12f, 5.5f, DOT)
        circle(12f, 12f, DOT)
        circle(12f, 18.5f, DOT)
    }

    /** 가로줄 세 개. 내비게이션 서랍 메뉴 */
    public val Menu: ImageVector by icon("menu") {
        moveTo(4f, 7f)
        lineTo(20f, 7f)
        moveTo(4f, 12f)
        lineTo(20f, 12f)
        moveTo(4f, 17f)
        lineTo(20f, 17f)
    }

    /** 뒤로 가는 꺾쇠. 상단 바 뒤로 버튼. RTL에서 뒤집힌다 */
    public val ChevronBack: ImageVector by icon("chevron-back", mirrored = true) {
        moveTo(15f, 5f)
        lineTo(8f, 12f)
        lineTo(15f, 19f)
    }

    /** 앞으로 가는 꺾쇠. 목록 행 끝 표시. RTL에서 뒤집힌다 */
    public val ChevronForward: ImageVector by icon("chevron-forward", mirrored = true) {
        moveTo(9f, 6f)
        lineTo(15f, 12f)
        lineTo(9f, 18f)
    }

    /** 위 꺾쇠. 펼친 것 접기 */
    public val ChevronUp: ImageVector by icon("chevron-up") {
        moveTo(6f, 15f)
        lineTo(12f, 9f)
        lineTo(18f, 15f)
    }

    /** 아래 꺾쇠. 펼치기, 드롭다운 */
    public val ChevronDown: ImageVector by icon("chevron-down") {
        moveTo(6f, 9f)
        lineTo(12f, 15f)
        lineTo(18f, 9f)
    }

    /** 왼쪽 화살표. 뒤로 가기. RTL에서 뒤집힌다 */
    public val ArrowBack: ImageVector by icon("arrow-back", mirrored = true) {
        moveTo(20f, 12f)
        lineTo(5f, 12f)
        moveTo(11f, 6f)
        lineTo(5f, 12f)
        lineTo(11f, 18f)
    }

    /** 오른쪽 화살표. 다음, 진행. RTL에서 뒤집힌다 */
    public val ArrowForward: ImageVector by icon("arrow-forward", mirrored = true) {
        moveTo(4f, 12f)
        lineTo(19f, 12f)
        moveTo(13f, 6f)
        lineTo(19f, 12f)
        lineTo(13f, 18f)
    }

    /** 위 화살표. 올리기, 보낸 돈, 맨 위로 */
    public val ArrowUp: ImageVector by icon("arrow-up") {
        moveTo(12f, 19f)
        lineTo(12f, 5f)
        moveTo(6f, 11f)
        lineTo(12f, 5f)
        lineTo(18f, 11f)
    }

    /** 아래 화살표. 내리기, 받은 돈 */
    public val ArrowDown: ImageVector by icon("arrow-down") {
        moveTo(12f, 5f)
        lineTo(12f, 19f)
        moveTo(6f, 13f)
        lineTo(12f, 19f)
        lineTo(18f, 13f)
    }

    /** 시계 방향으로 도는 화살표. 새로고침, 다시 시도 */
    public val Refresh: ImageVector by icon("refresh") {
        moveTo(19.5f, 12f)
        arcTo(7.5f, 7.5f, 0f, true, true, 17.3f, 6.7f)
        moveTo(18f, 3.2f)
        lineTo(18f, 7.2f)
        lineTo(14f, 7.2f)
    }

    /** 길이가 줄어드는 가로줄 세 개. 필터, 정렬 */
    public val Filter: ImageVector by icon("filter") {
        moveTo(4f, 7f)
        lineTo(20f, 7f)
        moveTo(7f, 12f)
        lineTo(17f, 12f)
        moveTo(10f, 17f)
        lineTo(14f, 17f)
    }

    /** 이어진 점 세 개. 공유 */
    public val Share: ImageVector by icon("share") {
        circle(17.5f, 5.5f, 2.5f)
        circle(6.5f, 12f, 2.5f)
        circle(17.5f, 18.5f, 2.5f)
        moveTo(8.7f, 10.8f)
        lineTo(15.3f, 6.8f)
        moveTo(8.7f, 13.2f)
        lineTo(15.3f, 17.2f)
    }

    /** 연필. 편집, 새 글 */
    public val Edit: ImageVector by icon("edit") {
        moveTo(4f, 20f)
        lineTo(4.8f, 16f)
        lineTo(15.5f, 5.3f)
        arcTo(2.2f, 2.2f, 0f, false, true, 18.7f, 8.5f)
        lineTo(8f, 19.2f)
        close()
        moveTo(13.5f, 7.3f)
        lineTo(16.7f, 10.5f)
    }

    /** 휴지통. 삭제 */
    public val Trash: ImageVector by icon("trash") {
        moveTo(4f, 7f)
        lineTo(20f, 7f)
        moveTo(9f, 7f)
        lineTo(9f, 4.5f)
        lineTo(15f, 4.5f)
        lineTo(15f, 7f)
        moveTo(6f, 7f)
        lineTo(7f, 20f)
        lineTo(17f, 20f)
        lineTo(18f, 7f)
        moveTo(10f, 11f)
        lineTo(10f, 16f)
        moveTo(14f, 11f)
        lineTo(14f, 16f)
    }

    /** 겹친 두 장. 복사 */
    public val Copy: ImageVector by icon("copy") {
        roundRect(8.5f, 8.5f, 20f, 20f, 2.5f)
        moveTo(15.5f, 8.5f)
        lineTo(15.5f, 6.5f)
        arcTo(2.5f, 2.5f, 0f, false, false, 13f, 4f)
        lineTo(6.5f, 4f)
        arcTo(2.5f, 2.5f, 0f, false, false, 4f, 6.5f)
        lineTo(4f, 13f)
        arcTo(2.5f, 2.5f, 0f, false, false, 6.5f, 15.5f)
        lineTo(8.5f, 15.5f)
    }

    /** 사슬 고리. 링크 */
    public val Link: ImageVector by icon("link") {
        moveTo(10f, 14f)
        lineTo(14f, 10f)
        moveTo(11f, 7f)
        lineTo(12.5f, 5.5f)
        arcTo(3.9f, 3.9f, 0f, false, true, 18.5f, 11.5f)
        lineTo(17f, 13f)
        moveTo(13f, 17f)
        lineTo(11.5f, 18.5f)
        arcTo(3.9f, 3.9f, 0f, false, true, 5.5f, 12.5f)
        lineTo(7f, 11f)
    }

    /** 아래로 내려받는 화살표. 다운로드, 저장 */
    public val Download: ImageVector by icon("download") {
        moveTo(12f, 4f)
        lineTo(12f, 15f)
        moveTo(7f, 10f)
        lineTo(12f, 15f)
        lineTo(17f, 10f)
        moveTo(5f, 20f)
        lineTo(19f, 20f)
    }

    /** 위로 올리는 화살표. 업로드 */
    public val Upload: ImageVector by icon("upload") {
        moveTo(12f, 15f)
        lineTo(12f, 4f)
        moveTo(7f, 9f)
        lineTo(12f, 4f)
        lineTo(17f, 9f)
        moveTo(5f, 20f)
        lineTo(19f, 20f)
    }

    /** 종이비행기. 보내기. RTL에서 뒤집힌다 */
    public val Send: ImageVector by icon("send", mirrored = true) {
        moveTo(4f, 12f)
        lineTo(20f, 4f)
        lineTo(14f, 20f)
        lineTo(11f, 13f)
        close()
        moveTo(11f, 13f)
        lineTo(20f, 4f)
    }

    /** 책갈피. 저장, 나중에 보기 */
    public val Bookmark: ImageVector by icon("bookmark") {
        moveTo(6f, 4f)
        lineTo(18f, 4f)
        lineTo(18f, 20f)
        lineTo(12f, 15.5f)
        lineTo(6f, 20f)
        close()
    }

    /** 하트. 좋아요, 찜 */
    public val Heart: ImageVector by icon("heart") {
        moveTo(12f, 20f)
        curveTo(5f, 15.5f, 3f, 12f, 3f, 9f)
        curveTo(3f, 6.2f, 5.2f, 4.5f, 7.5f, 4.5f)
        curveTo(9.4f, 4.5f, 11f, 5.6f, 12f, 7.2f)
        curveTo(13f, 5.6f, 14.6f, 4.5f, 16.5f, 4.5f)
        curveTo(18.8f, 4.5f, 21f, 6.2f, 21f, 9f)
        curveTo(21f, 12f, 19f, 15.5f, 12f, 20f)
        close()
    }

    /** 별. 즐겨찾기, 평점 */
    public val Star: ImageVector by icon("star") {
        moveTo(12f, 3.5f)
        lineTo(14.6f, 9f)
        lineTo(20.5f, 9.6f)
        lineTo(16f, 13.6f)
        lineTo(17.3f, 19.5f)
        lineTo(12f, 16.5f)
        lineTo(6.7f, 19.5f)
        lineTo(8f, 13.6f)
        lineTo(3.5f, 9.6f)
        lineTo(9.4f, 9f)
        close()
    }

    /** 눈. 보기, 비밀번호 보이기 */
    public val Eye: ImageVector by icon("eye") {
        eye()
    }

    /** 사선이 그어진 눈. 숨기기, 비밀번호 가리기 */
    public val EyeOff: ImageVector by icon("eye-off") {
        eye()
        moveTo(4f, 4f)
        lineTo(20f, 20f)
    }

    /** 자물쇠. 잠금, 보안 */
    public val Lock: ImageVector by icon("lock") {
        roundRect(5f, 10.5f, 19f, 20.5f, 2.5f)
        moveTo(8f, 10.5f)
        lineTo(8f, 7.5f)
        arcTo(4f, 4f, 0f, false, true, 16f, 7.5f)
        lineTo(16f, 10.5f)
    }

    /** 종. 알림 */
    public val Bell: ImageVector by icon("bell") {
        moveTo(6f, 15.5f)
        lineTo(6f, 10.5f)
        curveTo(6f, 6.5f, 9f, 4.5f, 12f, 4.5f)
        curveTo(15f, 4.5f, 18f, 6.5f, 18f, 10.5f)
        lineTo(18f, 15.5f)
        lineTo(20f, 17.5f)
        lineTo(4f, 17.5f)
        close()
        moveTo(10f, 20.5f)
        lineTo(14f, 20.5f)
    }

    /** 동그라미 안 i. 정보, 도움말 */
    public val Info: ImageVector by icon("info") {
        circle(12f, 12f, 8.5f)
        moveTo(12f, 11f)
        lineTo(12f, 16.5f)
        circle(12f, 7.8f, 0.4f)
    }

    /** 봉투. 메일 */
    public val Mail: ImageVector by icon("mail") {
        roundRect(3.5f, 5.5f, 20.5f, 18.5f, 2.5f)
        moveTo(4.5f, 7.5f)
        lineTo(12f, 13f)
        lineTo(19.5f, 7.5f)
    }

    /** 네모난 말풍선. 메시지, 채팅 */
    public val Message: ImageVector by icon("message") {
        moveTo(4f, 6f)
        curveTo(4f, 4.9f, 4.9f, 4f, 6f, 4f)
        lineTo(18f, 4f)
        curveTo(19.1f, 4f, 20f, 4.9f, 20f, 6f)
        lineTo(20f, 15f)
        curveTo(20f, 16.1f, 19.1f, 17f, 18f, 17f)
        lineTo(9f, 17f)
        lineTo(5f, 20.5f)
        lineTo(5f, 17f)
        curveTo(4.4f, 16.7f, 4f, 16f, 4f, 15f)
        close()
    }

    /** 받은편지함 트레이. 받은편지함 */
    public val Inbox: ImageVector by icon("inbox") {
        moveTo(4f, 13f)
        lineTo(4f, 19f)
        lineTo(20f, 19f)
        lineTo(20f, 13f)
        lineTo(17f, 5f)
        lineTo(7f, 5f)
        close()
        moveTo(4f, 13f)
        lineTo(9f, 13f)
        lineTo(10f, 15.5f)
        lineTo(14f, 15.5f)
        lineTo(15f, 13f)
        lineTo(20f, 13f)
    }

    /** 뚜껑 있는 상자. 보관 */
    public val Archive: ImageVector by icon("archive") {
        moveTo(4f, 5f)
        lineTo(20f, 5f)
        lineTo(20f, 9f)
        lineTo(4f, 9f)
        close()
        moveTo(5.5f, 9f)
        lineTo(5.5f, 19f)
        lineTo(18.5f, 19f)
        lineTo(18.5f, 9f)
        moveTo(10f, 13f)
        lineTo(14f, 13f)
    }

    /** 수화기. 전화 걸기 */
    public val Call: ImageVector by icon("call") {
        moveTo(5.2f, 3.5f)
        lineTo(8.3f, 3.5f)
        lineTo(10f, 7.8f)
        lineTo(7.9f, 9.6f)
        curveTo(9f, 12f, 12f, 15f, 14.4f, 16.1f)
        lineTo(16.2f, 14f)
        lineTo(20.5f, 15.7f)
        lineTo(20.5f, 18.8f)
        curveTo(20.5f, 19.8f, 19.7f, 20.6f, 18.7f, 20.5f)
        curveTo(10.4f, 19.9f, 4.1f, 13.6f, 3.5f, 5.3f)
        curveTo(3.4f, 4.3f, 4.2f, 3.5f, 5.2f, 3.5f)
        close()
    }

    /** 내려놓은 수화기. 통화 끊기 */
    public val CallEnd: ImageVector by icon("call-end") {
        moveTo(3f, 14.5f)
        curveTo(7.5f, 9.5f, 16.5f, 9.5f, 21f, 14.5f)
        lineTo(18.5f, 17f)
        lineTo(15.5f, 15f)
        lineTo(15.5f, 12.8f)
        curveTo(13.2f, 12.1f, 10.8f, 12.1f, 8.5f, 12.8f)
        lineTo(8.5f, 15f)
        lineTo(5.5f, 17f)
        close()
    }

    /** 점 열 개. 전화 키패드 */
    public val Dialpad: ImageVector by icon("dialpad") {
        for (row in 0..2) {
            for (col in 0..2) circle(6.5f + col * 5.5f, 4f + row * 5.5f, KEY_DOT)
        }
        circle(12f, 20.5f, KEY_DOT)
    }

    /** 마이크. 음성 입력, 마이크 켜짐 */
    public val Mic: ImageVector by icon("mic") {
        mic()
    }

    /** 사선이 그어진 마이크. 음소거 */
    public val MicOff: ImageVector by icon("mic-off") {
        mic()
        moveTo(4f, 4f)
        lineTo(20f, 20f)
    }

    /** 비디오카메라. 영상 통화, 카메라 켜짐 */
    public val Video: ImageVector by icon("video") {
        video()
    }

    /** 사선이 그어진 비디오카메라. 카메라 꺼짐 */
    public val VideoOff: ImageVector by icon("video-off") {
        video()
        moveTo(3.5f, 4f)
        lineTo(20f, 20.5f)
    }

    /** 소리가 나는 스피커. 스피커폰, 소리 켜짐 */
    public val Speaker: ImageVector by icon("speaker") {
        moveTo(4f, 9.5f)
        lineTo(7.5f, 9.5f)
        lineTo(12f, 5.5f)
        lineTo(12f, 18.5f)
        lineTo(7.5f, 14.5f)
        lineTo(4f, 14.5f)
        close()
        moveTo(15.5f, 9f)
        arcTo(4f, 4f, 0f, false, true, 15.5f, 15f)
        moveTo(17.8f, 6.5f)
        arcTo(7.5f, 7.5f, 0f, false, true, 17.8f, 17.5f)
    }

    /** 카메라. 사진 찍기 */
    public val Camera: ImageVector by icon("camera") {
        moveTo(3.5f, 8.5f)
        lineTo(7.5f, 8.5f)
        lineTo(9f, 6f)
        lineTo(15f, 6f)
        lineTo(16.5f, 8.5f)
        lineTo(20.5f, 8.5f)
        lineTo(20.5f, 19f)
        lineTo(3.5f, 19f)
        close()
        circle(12f, 13.5f, 3.5f)
    }

    /** 산과 해가 있는 액자. 사진, 갤러리 */
    public val Image: ImageVector by icon("image") {
        roundRect(3.5f, 4.5f, 20.5f, 19.5f, 2.5f)
        moveTo(3.5f, 16f)
        lineTo(8.5f, 11f)
        lineTo(13f, 15.5f)
        lineTo(15.5f, 13f)
        lineTo(20.5f, 18f)
        circle(15.5f, 8.8f, 1.5f)
    }

    /** 모서리가 접힌 문서. 파일, 첨부 */
    public val File: ImageVector by icon("file") {
        moveTo(6f, 3.5f)
        lineTo(14f, 3.5f)
        lineTo(18.5f, 8f)
        lineTo(18.5f, 20.5f)
        lineTo(6f, 20.5f)
        close()
        moveTo(14f, 3.5f)
        lineTo(14f, 8f)
        lineTo(18.5f, 8f)
    }

    /** 달력. 날짜, 일정 */
    public val Calendar: ImageVector by icon("calendar") {
        roundRect(3.5f, 5.5f, 20.5f, 20f, 2.5f)
        moveTo(3.5f, 10f)
        lineTo(20.5f, 10f)
        moveTo(8f, 3.5f)
        lineTo(8f, 7f)
        moveTo(16f, 3.5f)
        lineTo(16f, 7f)
    }

    /** 시계. 시간, 최근 기록 */
    public val Clock: ImageVector by icon("clock") {
        circle(12f, 12f, 8.5f)
        moveTo(12f, 7.5f)
        lineTo(12f, 12f)
        lineTo(15f, 14f)
    }

    /** 위치 핀. 장소, 주소 */
    public val Location: ImageVector by icon("location") {
        moveTo(12f, 21f)
        curveTo(12f, 21f, 5f, 14.5f, 5f, 9.5f)
        curveTo(5f, 5.5f, 8f, 3f, 12f, 3f)
        curveTo(16f, 3f, 19f, 5.5f, 19f, 9.5f)
        curveTo(19f, 14.5f, 12f, 21f, 12f, 21f)
        close()
        circle(12f, 9.5f, 2.2f)
    }

    /** 한 사람. 프로필, 계정 */
    public val Person: ImageVector by icon("person") {
        circle(12f, 8f, 4f)
        moveTo(4.5f, 20.5f)
        curveTo(5f, 16f, 8f, 14f, 12f, 14f)
        curveTo(16f, 14f, 19f, 16f, 19.5f, 20.5f)
    }

    /** 두 사람. 그룹, 참가자 */
    public val Users: ImageVector by icon("users") {
        circle(9f, 8.5f, 3.5f)
        moveTo(2.5f, 19.5f)
        curveTo(3f, 15.5f, 5.5f, 14f, 9f, 14f)
        curveTo(12.5f, 14f, 15f, 15.5f, 15.5f, 19.5f)
        moveTo(15.5f, 5.2f)
        arcTo(3.3f, 3.3f, 0f, false, true, 15.5f, 11.8f)
        moveTo(18f, 14.4f)
        curveTo(20f, 15.2f, 21.2f, 17f, 21.5f, 19.5f)
    }

    /** 바퀴 달린 카트. 장바구니 */
    public val Cart: ImageVector by icon("cart") {
        moveTo(3f, 4f)
        lineTo(5.5f, 4f)
        lineTo(7.6f, 15f)
        lineTo(18f, 15f)
        lineTo(20f, 7.5f)
        lineTo(6.2f, 7.5f)
        circle(9f, 19f, 1.4f)
        circle(16.5f, 19f, 1.4f)
    }

    /** 손잡이 달린 쇼핑백. 쇼핑, 주문 */
    public val Bag: ImageVector by icon("bag") {
        moveTo(5f, 8f)
        lineTo(19f, 8f)
        lineTo(18f, 20f)
        lineTo(6f, 20f)
        close()
        moveTo(9f, 10f)
        lineTo(9f, 7f)
        arcTo(3f, 3f, 0f, false, true, 15f, 7f)
        lineTo(15f, 10f)
    }

    /** 신용카드. 결제 수단 */
    public val Card: ImageVector by icon("card") {
        roundRect(3f, 5.5f, 21f, 18.5f, 2.5f)
        moveTo(3f, 9.5f)
        lineTo(21f, 9.5f)
        moveTo(6.5f, 14.5f)
        lineTo(10f, 14.5f)
    }

    /** 네 칸. 모두 보기, 앱 목록 */
    public val Grid: ImageVector by icon("grid") {
        roundRect(4f, 4f, 10.5f, 10.5f, 2f)
        roundRect(13.5f, 4f, 20f, 10.5f, 2f)
        roundRect(4f, 13.5f, 10.5f, 20f, 2f)
        roundRect(13.5f, 13.5f, 20f, 20f, 2f)
    }

    /** 세로 막대 셋. 통계, 차트 */
    public val Chart: ImageVector by icon("chart") {
        moveTo(4f, 19.5f)
        lineTo(20f, 19.5f)
        moveTo(7f, 15.5f)
        lineTo(7f, 10.5f)
        moveTo(12f, 15.5f)
        lineTo(12f, 5.5f)
        moveTo(17f, 15.5f)
        lineTo(17f, 8.5f)
    }

    /** 해. 밝은 테마 */
    public val Sun: ImageVector by icon("sun") {
        circle(12f, 12f, 4f)
        for (i in 0 until SUN_RAYS) {
            val a = i * 2 * Math.PI / SUN_RAYS
            moveTo(12f + 6.5f * cos(a).toFloat(), 12f + 6.5f * sin(a).toFloat())
            lineTo(12f + 8.5f * cos(a).toFloat(), 12f + 8.5f * sin(a).toFloat())
        }
    }

    /** 초승달. 어두운 테마, 방해 금지 */
    public val Moon: ImageVector by icon("moon") {
        moveTo(19.5f, 14.5f)
        arcTo(8f, 8f, 0f, true, true, 9.5f, 4.5f)
        arcTo(6.5f, 6.5f, 0f, false, false, 19.5f, 14.5f)
        close()
    }

    /** 이름과 아이콘 짝 63개. 아이콘 고르기 화면이나 목록에 늘어놓을 때 쓴다. 이름은 속성 이름과 같다. */
    @JvmStatic
    public val all: List<Pair<String, ImageVector>>
        get() = listOf(
            "Home" to Home, "Search" to Search, "Settings" to Settings, "Plus" to Plus, "Minus" to Minus,
            "Close" to Close, "Check" to Check, "More" to More, "MoreVertical" to MoreVertical, "Menu" to Menu,
            "ChevronBack" to ChevronBack, "ChevronForward" to ChevronForward, "ChevronUp" to ChevronUp,
            "ChevronDown" to ChevronDown, "ArrowBack" to ArrowBack, "ArrowForward" to ArrowForward,
            "ArrowUp" to ArrowUp, "ArrowDown" to ArrowDown, "Refresh" to Refresh, "Filter" to Filter,
            "Share" to Share, "Edit" to Edit, "Trash" to Trash, "Copy" to Copy, "Link" to Link,
            "Download" to Download, "Upload" to Upload, "Send" to Send, "Bookmark" to Bookmark, "Heart" to Heart,
            "Star" to Star, "Eye" to Eye, "EyeOff" to EyeOff, "Lock" to Lock, "Bell" to Bell,
            "Info" to Info, "Mail" to Mail, "Message" to Message, "Inbox" to Inbox, "Archive" to Archive,
            "Call" to Call, "CallEnd" to CallEnd, "Dialpad" to Dialpad, "Mic" to Mic, "MicOff" to MicOff,
            "Video" to Video, "VideoOff" to VideoOff, "Speaker" to Speaker, "Camera" to Camera, "Image" to Image,
            "File" to File, "Calendar" to Calendar, "Clock" to Clock, "Location" to Location, "Person" to Person,
            "Users" to Users, "Cart" to Cart, "Bag" to Bag, "Card" to Card, "Grid" to Grid,
            "Chart" to Chart, "Sun" to Sun, "Moon" to Moon,
        )
}

private fun icon(name: String, mirrored: Boolean = false, block: PathBuilder.() -> Unit): Lazy<ImageVector> =
    lazy(LazyThreadSafetyMode.PUBLICATION) {
        ImageVector.Builder(
            name = "kinetica-$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = mirrored,
        ).path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = ICON_STROKE,
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

/** 가운데(12, 12)에서 [angle] 라디안, [radius] 거리의 점으로 잇는다. 톱니처럼 둘레를 따라 그리는 모양에 쓴다. */
private fun PathBuilder.point(angle: Double, radius: Float, first: Boolean = false) {
    val x = CENTER + radius * cos(angle).toFloat()
    val y = CENTER + radius * sin(angle).toFloat()
    if (first) moveTo(x, y) else lineTo(x, y)
}

private fun PathBuilder.eye() {
    moveTo(2.5f, 12f)
    curveTo(4.8f, 7.6f, 8.2f, 5.5f, 12f, 5.5f)
    curveTo(15.8f, 5.5f, 19.2f, 7.6f, 21.5f, 12f)
    curveTo(19.2f, 16.4f, 15.8f, 18.5f, 12f, 18.5f)
    curveTo(8.2f, 18.5f, 4.8f, 16.4f, 2.5f, 12f)
    close()
    circle(12f, 12f, 3f)
}

private fun PathBuilder.mic() {
    roundRect(9f, 3.5f, 15f, 14f, 3f)
    moveTo(5.5f, 11f)
    arcTo(6.5f, 6.5f, 0f, false, false, 18.5f, 11f)
    moveTo(12f, 17.5f)
    lineTo(12f, 20.5f)
}

private fun PathBuilder.video() {
    roundRect(3f, 6.5f, 15.5f, 17.5f, 2.5f)
    moveTo(15.5f, 10.5f)
    lineTo(21f, 7.5f)
    lineTo(21f, 16.5f)
    lineTo(15.5f, 13.5f)
}

private const val ICON_STROKE = 1.8f
private const val CENTER = 12f
private const val DOT = 0.9f
private const val KEY_DOT = 1.1f
private const val GEAR_TEETH = 8
private const val GEAR_OUTER = 9.2f
private const val GEAR_INNER = 7f
private const val SUN_RAYS = 8
