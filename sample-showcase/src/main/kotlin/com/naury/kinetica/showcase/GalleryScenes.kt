// 장면 배치 좌표·크기는 시연용 데이터라 이름 붙인 상수로 빼지 않는다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.motion.MorphResult
import com.naury.kinetica.motion.SceneMorph

/**
 * 목록 카드 장면과 상세 장면 한 쌍. 같은 morphKey의 노드가 전환 때 이어진다.
 * 상세 장면은 화면 비율에 맞춰 만들기 위해 [detail]에 높이(문서 단위, 폭 1080 기준)를 넘긴다.
 */
data class GalleryItem(val id: String, val title: String, val color: Long) {
    /** 목록 칸에 그리는 1080×1920 카드 장면 */
    val card: SceneDocument by lazy { GalleryScenes.card(this, GalleryScenes.CARD_HEIGHT) }

    /** 높이가 [height]인 상세 장면. 그림은 위쪽 끝에 붙고, 남는 높이는 그림과 본문이 나눠 쓴다. */
    fun detail(height: Double): SceneDocument = GalleryScenes.detail(this, height)

    /**
     * 카드에서 높이 [height]인 상세 장면으로 가는 전환. 전환은 상세 장면 artboard에 그리므로, 시작 모습이 목록 카드와 같도록
     * 카드 노드를 늘어난 높이의 가운데로 옮겨 둔다.
     */
    fun morph(height: Double): SceneMorph = (SceneMorph.create(GalleryScenes.card(this, height), detail(height)) as MorphResult.Ready).morph
}

object GalleryScenes {
    internal const val CARD_HEIGHT = 1920.0
    private const val WIDTH = 1080.0
    private val background = ArgbColor(0xFF15181E.toInt())
    private val white = ArgbColor(0xFFF2F4F8.toInt())
    private val muted = ArgbColor(0xFF9AA3B2.toInt())

    val items: List<GalleryItem> = listOf(
        GalleryItem("aurora", "오로라", 0xFF7C8CFF),
        GalleryItem("lagoon", "바다", 0xFF3EC1D3),
        GalleryItem("ember", "노을", 0xFFF0B35A),
        GalleryItem("moss", "숲", 0xFF5ED6A0),
    )

    /** 카드 장면. [height]가 카드 높이보다 크면 artboard만 늘리고 노드는 세로 가운데로 옮긴다. */
    internal fun card(item: GalleryItem, height: Double): SceneDocument {
        val fill = ArgbColor(item.color.toInt())
        val top = ((height - CARD_HEIGHT) / 2).coerceAtLeast(0.0)
        return scene(
            "${item.id}-card",
            maxOf(height, CARD_HEIGHT),
            // 카드 칸이 세로로 길어 그림을 위쪽에 크게 두고, 제목과 설명을 아래에 둬 빈 곳이 없게 한다.
            shape("hero", NodeContent.Rectangle(Size(900.0, 1180.0), cornerRadius = 56.0), 90.0, top + 90.0, fill),
            shape("title", NodeContent.Text(Size(900.0, 140.0), item.title, fontSize = 96.0), 90.0, top + 1340.0, white),
            shape("body", NodeContent.Text(Size(900.0, 200.0), "눌러서 열기", fontSize = 56.0), 90.0, top + 1500.0, muted),
        )
    }

    internal fun detail(item: GalleryItem, height: Double): SceneDocument {
        val fill = ArgbColor(item.color.toInt())
        val h = maxOf(height, CARD_HEIGHT)
        val hero = (h * HERO_RATIO).coerceAtLeast(900.0)
        return scene(
            "${item.id}-detail",
            h,
            shape("hero", NodeContent.Rectangle(Size(WIDTH, hero), cornerRadius = 0.0), 0.0, 0.0, fill),
            shape("title", NodeContent.Text(Size(936.0, 140.0), item.title, fontSize = 96.0), 72.0, hero + 60.0, white),
            shape(
                "body",
                NodeContent.Text(Size(936.0, 400.0), "카드의 그림과 제목이 같은 morphKey로 이어지며 상세 화면으로 바뀝니다.", fontSize = 44.0),
                72.0,
                hero + 240.0,
                muted,
            ),
        )
    }

    private fun shape(key: String, content: NodeContent, x: Double, y: Double, fill: ArgbColor) = SceneNode(
        id = NodeId(key),
        content = content,
        transform = Transform2D(translationX = x, translationY = y),
        style = NodeStyle(fill = fill),
        metadata = mapOf(SceneMorph.MORPH_KEY to key),
    )

    private fun scene(id: String, height: Double, vararg nodes: SceneNode) = SceneDocument(
        documentId = id,
        artboard = Artboard(WIDTH, height, background),
        nodes = nodes.associateBy { it.id },
        rootOrder = nodes.map { it.id },
    )

    /** 상세 장면에서 그림이 차지하는 높이 비율. 1080×1920일 때 1100이던 비율이다. */
    private const val HERO_RATIO = 1100.0 / 1920.0
}
