package com.naury.kinetica.player

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.KeyValue
import com.naury.kinetica.core.animation.Keyframe
import com.naury.kinetica.core.animation.PropertyTrack
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.motion.FrameClock
import com.naury.kinetica.motion.PlaybackController
import com.naury.kinetica.serialization.RemoteSceneLoader
import com.naury.kinetica.serialization.SceneFetch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerLifecycleTest {
    @get:Rule
    val compose = createComposeRule()

    // 호스트 뷰가 duration 동안 오른쪽으로 800 이동한다.
    private fun moving(durationMs: Long): SceneDocument {
        val slot = SceneNode(NodeId("slot"), NodeContent.HostSlot(Size(100.0, 100.0), "label"))
        val timeline = Timeline(
            "main",
            durationMs,
            listOf(
                PropertyTrack(
                    slot.id,
                    AnimatableProperty.TranslationX,
                    listOf(Keyframe(0, KeyValue.Scalar(0.0)), Keyframe(durationMs, KeyValue.Scalar(800.0))),
                ),
            ),
        )
        return SceneDocument(
            documentId = "moving",
            artboard = Artboard(1_000.0, 200.0),
            nodes = mapOf(slot.id to slot),
            rootOrder = listOf(slot.id),
            timelines = listOf(timeline),
        )
    }

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private fun frames(count: Int = 2) = repeat(count) { compose.mainClock.advanceTimeByFrame() }

    @Test
    fun `화면이 가려지면 재생을 멈추고 다시 보이면 멈춘 위치부터 이어 간다`() {
        var now = 0L
        val document = moving(1_000)
        val playback = PlaybackController(document.timelines.single(), FrameClock { now * 1_000_000L })
        val owner = TestOwner().apply { registry.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.autoAdvance = false
        playback.play()
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) { KineticaPlayer(document, Modifier.size(100.dp), playback) }
        }
        now = 300
        frames()

        owner.registry.currentState = Lifecycle.State.CREATED
        val paused = playback.state.value
        // 백그라운드에 10초 있다가 돌아온다.
        now = 10_300
        owner.registry.currentState = Lifecycle.State.RESUMED
        now = 10_400
        frames()

        assertThat(paused.isPlaying).isFalse()
        assertThat(paused.timeMs).isEqualTo(300.0)
        assertThat(playback.state.value.isPlaying).isTrue()
        assertThat(playback.state.value.timeMs).isEqualTo(400.0)
    }

    @Test
    fun `서버 화면의 자동 재생은 화면을 회전해도 처음부터 다시 재생하지 않는다`() {
        val loader = RemoteSceneLoader({ SceneFetch.Failed }, fallback = moving(4_000))
        // 회전처럼 저장한 상태만 남기고 화면을 다시 만든다. StateRestorationTester는 프레임을 직접 넘기는 동안에는 다시 만들지 않는다.
        var generation by mutableIntStateOf(0)
        var shown by mutableStateOf(true)
        var restored: Map<String, List<Any?>>? = null
        lateinit var registry: SaveableStateRegistry
        compose.mainClock.autoAdvance = false
        compose.setContent {
            registry = remember(generation) { SaveableStateRegistry(restored) { true } }
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                if (shown) {
                    RemoteKineticaPlayer(
                        loader,
                        Modifier.size(400.dp, 80.dp),
                        slots = mapOf(
                            "label" to {
                                Text("moving")
                            },
                        ),
                        autoplay = true,
                    )
                }
            }
        }
        frames()
        Thread.sleep(1_200)
        frames()
        val before = compose.onNodeWithText("moving").getBoundsInRoot().left

        restored = registry.performSave()
        shown = false
        frames(count = 1)
        generation++
        shown = true
        // 테스트 스레드에서 바꾼 상태를 재구성에 알린다. 시계를 멈춘 상태라 저절로 알려지지 않는다.
        Snapshot.sendApplyNotifications()
        frames()
        val after = compose.onNodeWithText("moving").getBoundsInRoot().left

        assertThat(before.value).isGreaterThan(10f)
        assertThat(after.value).isAtLeast(before.value)
    }

    @Test
    fun `앱을 쓰는 중에 애니메이션 삭제 설정을 바꾸면 바로 따라간다`() {
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        val scale = Settings.Global.ANIMATOR_DURATION_SCALE
        Settings.Global.putFloat(resolver, scale, 1f)
        compose.setContent { Text(if (rememberSystemReducedMotion()) "reduced" else "normal") }
        compose.onNodeWithText("normal").assertExists()

        Settings.Global.putFloat(resolver, scale, 0f)
        resolver.notifyChange(Settings.Global.getUriFor(scale), null)
        compose.waitForIdle()
        compose.onNodeWithText("reduced").assertExists()

        Settings.Global.putFloat(resolver, scale, 1f)
        resolver.notifyChange(Settings.Global.getUriFor(scale), null)
        compose.waitForIdle()
        compose.onNodeWithText("normal").assertExists()
    }

    @Test
    fun `애니메이션 삭제 설정이 바뀌어 컨트롤러가 새로 만들어져도 재생 위치와 속도를 이어받는다`() {
        val reduced = mutableStateOf(false)
        val timeline = Timeline("t", 4_000L)
        var controller: PlaybackController? = null
        compose.setContent { controller = rememberPlaybackController(timeline, reduced.value) }
        val first = checkNotNull(controller)
        first.seek(1_500.0)
        first.setRate(2.0)
        first.play()

        reduced.value = true
        compose.waitForIdle()

        // 줄이기로 바뀌면 재생 중이던 애니메이션은 곧바로 끝 장면으로 간다(멈춰 버리지 않는다).
        val second = checkNotNull(controller)
        assertThat(second).isNotSameInstanceAs(first)
        assertThat(second.state.value.rate).isEqualTo(2.0)
        assertThat(second.state.value.timeMs).isEqualTo(4_000.0)

        // 다시 켜면 이어받은 위치에서 계속 재생할 수 있다.
        second.seek(1_000.0)
        reduced.value = false
        compose.waitForIdle()
        val third = checkNotNull(controller)
        assertThat(third.state.value.timeMs).isAtLeast(1_000.0)
        third.play()
        assertThat(third.state.value.isPlaying).isTrue()
    }
}
