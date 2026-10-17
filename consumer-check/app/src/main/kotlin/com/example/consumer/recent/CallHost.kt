package com.example.consumer.recent

import android.os.Bundle
import android.os.SystemClock
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.naury.kinetica.ui.KCallActions
import com.naury.kinetica.ui.KCallDock
import com.naury.kinetica.ui.KCallDockItem
import com.naury.kinetica.ui.KCallIcons
import com.naury.kinetica.ui.KCallParticipant
import com.naury.kinetica.ui.KCallPhase
import com.naury.kinetica.ui.KCallScreen
import com.naury.kinetica.ui.KCallState
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KParticipantGrid
import com.naury.kinetica.ui.KParticipantTile
import com.naury.kinetica.ui.KTone
import com.naury.kinetica.ui.KineticaColors
import com.naury.kinetica.ui.KineticaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** 호스트 앱이 자기 방식으로 들고 있는 통화 상태. 키트 데이터 클래스가 아니다. */
data class HostPerson(val id: String, val name: String, val local: Boolean, val micOn: Boolean = true, val cameraOn: Boolean = true)

/** 호스트 통화 모델. 실제 앱이라면 ViewModel의 StateFlow다. */
class HostCallModel {
    val phase = MutableStateFlow(KCallPhase.Connecting)
    val people = MutableStateFlow(listOf(HostPerson("me", "Me", true), HostPerson("ana", "Ana", false), HostPerson("ben", "Ben", false)))
    val connectedAt = MutableStateFlow(SystemClock.elapsedRealtime())
    var hangUps = 0
        private set

    fun toggleMic() = people.update { list -> list.map { if (it.local) it.copy(micOn = !it.micOn) else it } }

    fun toggleCamera() = people.update { list -> list.map { if (it.local) it.copy(cameraOn = !it.cameraOn) else it } }

    fun hangUp() {
        hangUps++
        phase.value = KCallPhase.Ended
    }
}

/**
 * `KCallScreen` 하나로 그린 통화 화면. 호스트 StateFlow를 [KCallState]로 옮기고, 영상은 실제 앱처럼 `TextureView`를 `AndroidView`로 넣는다.
 * Material 3 테마 안에 두고 강조색을 넘긴다.
 */
class CallHostActivity : ComponentActivity() {
    val model = HostCallModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                KineticaTheme(colors = KineticaColors(accent = MaterialTheme.colorScheme.primary)) {
                    val phase by model.phase.collectAsState()
                    val people by model.people.collectAsState()
                    val connectedAt by model.connectedAt.collectAsState()
                    KCallScreen(
                        state = KCallState(
                            phase = phase,
                            participants = people.map {
                                KCallParticipant(it.id, it.name, isLocal = it.local, isMuted = !it.micOn, isVideoOn = it.cameraOn, isSpeaking = it.id == "ana")
                            },
                            title = "Weekly sync",
                            startedAtMillis = connectedAt,
                        ),
                        actions = KCallActions(onEnd = model::hangUp, onToggleMute = model::toggleMic, onToggleCamera = model::toggleCamera),
                        video = { p -> HostVideo(p.id) },
                        audioLevel = { id -> if (id == "ana") 0.6f else 0f },
                    )
                }
            }
        }
    }
}

/** 영상 렌더러 자리. 실제 앱이라면 엔진이 이 TextureView에 프레임을 그린다. */
@Composable
private fun HostVideo(id: String) {
    AndroidView(
        factory = { context -> TextureView(context).apply { tag = "video-$id" } },
        modifier = Modifier.fillMaxSize().testTag("video-$id"),
    )
}

/**
 * 부품으로 직접 조합한 통화 화면. 호스트 목록을 그대로 [KParticipantGrid]에 넘기고, 좁은 도크가 버튼을 접는지와 버튼 위치 알림을 본다.
 */
class CallPartsActivity : ComponentActivity() {
    var audioAnchor by mutableStateOf<Rect?>(null)
    var pinned by mutableStateOf<String?>(null)
    var clicks = 0
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val people = listOf(HostPerson("me", "Me", true), HostPerson("ana", "Ana", false), HostPerson("ben", "Ben", false), HostPerson("cy", "Cy", false))
        setContent {
            MaterialTheme {
                KineticaTheme {
                    Box(Modifier.fillMaxSize()) {
                        KParticipantGrid(people, key = { it.id }, Modifier.fillMaxSize(), focusKey = pinned, selfKey = "me", contentPadding = PaddingValues(8.dp)) { p, role ->
                            KParticipantTile(
                                p.name,
                                Modifier.testTag("tile-${p.id}-$role"),
                                muted = !p.micOn,
                                onClick = { pinned = p.id.takeIf { pinned != it } },
                                onTogglePin = { pinned = p.id.takeIf { pinned != it } },
                            )
                        }
                        val items = remember {
                            listOf(
                                KCallDockItem("mic", KIcons.Mic, "Mic", { clicks++ }, essential = true),
                                KCallDockItem("audio", KIcons.Speaker, "Audio", { clicks++ }),
                                KCallDockItem("share", KCallIcons.ScreenShare, "Share", { clicks++ }, badge = 2),
                                KCallDockItem("chat", KIcons.Message, "Chat", { clicks++ }, badge = 3),
                                KCallDockItem("end", KIcons.CallEnd, "End", { clicks++ }, tone = KTone.Destructive, essential = true),
                            )
                        }
                        KCallDock(
                            items,
                            Modifier.width(240.dp).testTag("dock"),
                            onItemPositioned = { key, rect -> if (key == "audio") audioAnchor = rect },
                        )
                    }
                }
            }
        }
    }
}
