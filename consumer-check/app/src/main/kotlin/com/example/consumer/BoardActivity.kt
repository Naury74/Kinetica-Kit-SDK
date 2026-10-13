package com.example.consumer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import android.widget.TextView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Modifier
import com.naury.kinetica.board.BoardWidget
import com.naury.kinetica.board.KineticaBoard
import com.naury.kinetica.board.KineticaBoardState

// docs 31: 호스트 위젯 보드. 목록을 주고 키마다 그리면 끝이다.
class BoardActivity : ComponentActivity() {
    private val state by lazy { KineticaBoardState.create(listOf(BoardWidget("hello", "Hello"), BoardWidget("news", "News"))) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KineticaBoard(state, Modifier.fillMaxSize(), editing = true) { key -> AndroidView({ TextView(it).apply { text = key } }) } }
    }

    override fun onDestroy() {
        super.onDestroy()
        state.close()
    }
}
