// 샘플 화면의 크기·색은 디자인 값이라 이름 붙인 상수로 빼지 않는다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.BoundProperty
import com.naury.kinetica.core.scene.DataValue
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.SceneData
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.player.RemoteKineticaPlayer
import com.naury.kinetica.player.SceneFit
import com.naury.kinetica.serialization.FileSceneRepository
import com.naury.kinetica.serialization.RemoteSceneLoader
import com.naury.kinetica.serialization.RemoteSceneState
import com.naury.kinetica.serialization.SceneCodec
import com.naury.kinetica.serialization.SceneFetch
import com.naury.kinetica.serialization.SceneFetcher
import java.io.File
import kotlinx.coroutines.delay

/**
 * 서버 주도 화면 예. 실제 앱이라면 [SceneFetcher]가 HTTP로 JSON을 받는다. 여기서는 응답을 고를 수 있는 가짜 서버를 쓴다.
 * 서버가 배치를 바꿔도 앱의 호스트 뷰(장바구니 버튼)·데이터(이름)·동작(쿠폰)은 그대로 이어진다.
 */
class RemoteActivity : ComponentActivity() {
    private val server = FakeServer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val loader = RemoteSceneLoader(
            fetcher = server,
            cache = FileSceneRepository(File(cacheDir, "remote")),
            cacheId = "home-banner",
            fallback = RemoteLayouts.bundled,
            // 앱이 그릴 수 없는 호스트 뷰를 요구하는 문서는 쓰지 않는다.
            accept = { document -> document.slotKeys().all { it in RemoteLayouts.SLOTS } },
        )
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { RemoteScreen(loader, server) } }
    }
}

@Composable
private fun RemoteScreen(loader: RemoteSceneLoader, server: FakeServer) {
    var refresh by remember { mutableIntStateOf(0) }
    var cart by remember { mutableIntStateOf(0) }
    var coupons by remember { mutableIntStateOf(0) }
    var serverMode by remember { mutableStateOf(server.mode) }
    val state by loader.state.collectAsState()
    Column(Modifier.fillMaxSize().background(Color(0xFF101216)).safeDrawingPadding().padding(16.dp)) {
        RemoteKineticaPlayer(
            loader,
            Modifier.fillMaxWidth().weight(1f),
            refreshKey = refresh,
            data = SceneData.of(RemoteLayouts.USER_NAME to DataValue.Text("Naury")),
            slots = mapOf(
                RemoteLayouts.SLOT_CART to {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Button(onClick = { cart++ }) { Text(stringResource(R.string.remote_cart, cart)) }
                    }
                },
            ),
            onAction = { if (it == RemoteLayouts.ACTION_COUPON) coupons++ },
            fit = SceneFit.Contain,
            placeholder = { Text(stringResource(R.string.remote_loading)) },
        )
        Text(state.describe() + " · " + stringResource(R.string.remote_coupons, coupons), color = Color(0x99FFFFFF))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ServerMode.entries.forEach { mode ->
                FilterChip(
                    selected = serverMode == mode,
                    onClick = {
                        serverMode = mode
                        server.mode = mode
                    },
                    label = { Text(mode.label) },
                )
            }
            Button(onClick = { refresh++ }) { Text(stringResource(R.string.remote_refresh)) }
        }
    }
}

private fun RemoteSceneState.describe(): String = when (this) {
    RemoteSceneState.Loading -> "loading"
    is RemoteSceneState.Failed -> "failed: $error"
    is RemoteSceneState.Ready -> "origin=$origin" + (lastError?.let { ", error=$it" } ?: "")
}

private fun SceneDocument.slotKeys(): Set<String> = nodes.values.mapNotNull { (it.content as? NodeContent.HostSlot)?.key }.toSet()

private enum class ServerMode(val label: String) { LayoutA("서버 A"), LayoutB("서버 B"), Broken("깨진 JSON"), Offline("오프라인") }

/** 고른 응답을 0.6초 뒤 돌려주는 가짜 서버. 같은 버전을 다시 물으면 NotModified다. */
private class FakeServer : SceneFetcher {
    @Volatile var mode: ServerMode = ServerMode.LayoutA
    private val codec = SceneCodec()

    override suspend fun fetch(etag: String?): SceneFetch {
        delay(600)
        val current = mode
        return when (current) {
            ServerMode.Offline -> SceneFetch.Failed

            ServerMode.Broken -> SceneFetch.Body("{\"schemaVersion\":1,".toByteArray())

            ServerMode.LayoutA, ServerMode.LayoutB -> if (etag == current.name) {
                SceneFetch.NotModified
            } else {
                val layout = if (current == ServerMode.LayoutA) RemoteLayouts.promoTop else RemoteLayouts.cartTop
                SceneFetch.Body(codec.encode(layout).toByteArray(), etag = current.name)
            }
        }
    }
}

/** 서버가 내려줄 법한 배치들. 같은 키(호스트 뷰·데이터·동작)를 다른 모양·위치로 쓴다. */
private object RemoteLayouts {
    const val SLOT_CART = "cart"
    const val USER_NAME = "user.name"
    const val ACTION_COUPON = "coupon"
    val SLOTS = setOf(SLOT_CART)

    val bundled = layout("bundled", bannerY = 40.0, slotY = 420.0, color = 0xFF3A3F4B, title = "기본 화면(앱에 포함)")
    val promoTop = layout("promo-top", bannerY = 40.0, slotY = 420.0, color = 0xFF5B6CFF, title = "가을 쿠폰 받기")
    val cartTop = layout("cart-top", bannerY = 320.0, slotY = 40.0, color = 0xFFFF7A59, title = "오늘만 무료 배송")

    private fun layout(id: String, bannerY: Double, slotY: Double, color: Long, title: String): SceneDocument {
        val nodes = listOf(
            SceneNode(
                NodeId("banner"),
                NodeContent.Frame(Size(920.0, 240.0), listOf(NodeId("title"), NodeId("name")), cornerRadius = 36.0),
                transform = Transform2D(translationX = 80.0, translationY = bannerY),
                style = NodeStyle(fill = ArgbColor(color.toInt())),
                action = ACTION_COUPON,
            ),
            SceneNode(
                NodeId("title"),
                NodeContent.Text(Size(840.0, 80.0), title, fontSize = 60.0, fontWeight = 700),
                parentId = NodeId("banner"),
                transform = Transform2D(translationX = 40.0, translationY = 40.0),
                style = NodeStyle(fill = ArgbColor(0xFFFFFFFF.toInt())),
            ),
            SceneNode(
                NodeId("name"),
                NodeContent.Text(Size(840.0, 60.0), "-", fontSize = 40.0),
                parentId = NodeId("banner"),
                transform = Transform2D(translationX = 40.0, translationY = 140.0),
                style = NodeStyle(fill = ArgbColor(0xCCFFFFFF.toInt())),
                bindings = mapOf(BoundProperty.Text to USER_NAME),
            ),
            SceneNode(
                NodeId("cart"),
                NodeContent.HostSlot(Size(920.0, 240.0), SLOT_CART),
                transform = Transform2D(translationX = 80.0, translationY = slotY),
            ),
        )
        return SceneDocument(
            documentId = id,
            artboard = Artboard(1080.0, 720.0, background = ArgbColor(0xFF1B1E25.toInt())),
            nodes = nodes.associateBy { it.id },
            rootOrder = nodes.filter { it.parentId == null }.map { it.id },
        )
    }
}
