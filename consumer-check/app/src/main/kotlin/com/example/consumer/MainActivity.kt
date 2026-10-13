package com.example.consumer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.Modifier
import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.KeyValue
import com.naury.kinetica.core.animation.Keyframe
import com.naury.kinetica.core.animation.PropertyTrack
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.player.KineticaPlayer
import com.naury.kinetica.player.rememberPlaybackController

// docs 00: 의존성 추가 뒤 20줄 안의 연동.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val box = SceneNode(NodeId("box"), NodeContent.Rectangle(Size(400.0, 400.0)), style = NodeStyle(fill = ArgbColor(0xFF7C8CFF.toInt())))
        val fade = Timeline("main", 1_000, listOf(PropertyTrack(box.id, AnimatableProperty.Opacity, listOf(Keyframe(0, KeyValue.Scalar(0.0)), Keyframe(1_000, KeyValue.Scalar(1.0))))))
        val scene = SceneDocument("hello", Artboard(1080.0, 1920.0), mapOf(box.id to box), listOf(box.id), listOf(fade))
        setContent {
            val playback = rememberPlaybackController(fade).also { it.play() }
            KineticaPlayer(scene, Modifier, playback)
        }
    }
}
