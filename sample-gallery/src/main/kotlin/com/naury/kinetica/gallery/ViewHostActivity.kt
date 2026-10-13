package com.naury.kinetica.gallery

import android.app.Activity
import android.os.Bundle
import android.widget.FrameLayout
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.naury.kinetica.player.KineticaPlayer

/**
 * Compose를 쓰지 않는 View 화면에서 재생기를 붙이는 예(docs 23 View activity). ComposeView를 View 계층에 넣고
 * View 수명에 맞춰 구성을 정리한다.
 */
class ViewHostActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = FrameLayout(this)
        val player = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { KineticaPlayer(GalleryScenes.items.first().detail, Modifier) }
        }
        container.addView(player, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(container)
    }
}
