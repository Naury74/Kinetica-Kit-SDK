// 장면 배치 좌표·크기는 시연용 데이터라 이름 붙인 상수로 빼지 않는다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.gallery

import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.motion.SceneMorph

/** 목록 카드 장면과 상세 장면 한 쌍. 같은 morphKey의 노드가 전환 때 이어진다. */
data class GalleryItem(val id: String, val title: String, val card: SceneDocument, val detail: SceneDocument)

object GalleryScenes {
    private val artboard = Artboard(1080.0, 1920.0, ArgbColor(0xFF15181E.toInt()))
    private val white = ArgbColor(0xFFF2F4F8.toInt())
    private val muted = ArgbColor(0xFF9AA3B2.toInt())

    val items: List<GalleryItem> = listOf(
        item("aurora", "Aurora", 0xFF7C8CFF),
        item("lagoon", "Lagoon", 0xFF3EC1D3),
        item("ember", "Ember", 0xFFF0B35A),
        item("moss", "Moss", 0xFF5ED6A0),
    )

    private fun item(id: String, title: String, color: Long): GalleryItem {
        val fill = ArgbColor(color.toInt())
        val card = scene(
            "$id-card",
            shape("hero", NodeContent.Rectangle(Size(900.0, 560.0), cornerRadius = 48.0), 90.0, 240.0, fill),
            shape("title", NodeContent.Text(Size(900.0, 120.0), title, fontSize = 72.0), 90.0, 860.0, white),
        )
        val detail = scene(
            "$id-detail",
            shape("hero", NodeContent.Rectangle(Size(1080.0, 1100.0), cornerRadius = 0.0), 0.0, 0.0, fill),
            shape("title", NodeContent.Text(Size(936.0, 140.0), title, fontSize = 96.0), 72.0, 1160.0, white),
            shape(
                "body",
                NodeContent.Text(Size(936.0, 400.0), "Kinetica scene morph · shared morphKey", fontSize = 44.0),
                72.0,
                1340.0,
                muted,
            ),
        )
        return GalleryItem(id, title, card, detail)
    }

    private fun shape(key: String, content: NodeContent, x: Double, y: Double, fill: ArgbColor) = SceneNode(
        id = NodeId(key),
        content = content,
        transform = Transform2D(translationX = x, translationY = y),
        style = NodeStyle(fill = fill),
        metadata = mapOf(SceneMorph.MORPH_KEY to key),
    )

    private fun scene(id: String, vararg nodes: SceneNode) = SceneDocument(
        documentId = id,
        artboard = artboard,
        nodes = nodes.associateBy { it.id },
        rootOrder = nodes.map { it.id },
    )
}
