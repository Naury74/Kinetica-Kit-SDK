package com.naury.kinetica.java

import com.naury.kinetica.core.command.CommandResult
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.history.KineticaSession
import com.naury.kinetica.history.SessionOpenResult
import com.naury.kinetica.serialization.DecodeResult
import com.naury.kinetica.serialization.SceneCodec

/**
 * Java에서 쓰기 쉬운 진입점(docs 03 Java consumption). Kotlin 기본 인자에 기대지 않는 정적 함수를 제공하고, 실패는 예외로 알린다.
 * Kotlin 호스트는 이 모듈 없이 각 모듈의 API를 직접 쓰면 된다.
 */
public object Kinetica {
    private val codec = SceneCodec()

    /** 빈 문서를 만든다. 크기는 문서 단위이며 0보다 커야 한다. */
    @JvmStatic
    public fun emptyDocument(documentId: String, width: Double, height: Double): SceneDocument =
        SceneDocument.empty(Artboard(width, height), documentId)

    /**
     * 편집 세션을 연다.
     *
     * @throws IllegalArgumentException 문서가 규칙을 어긴 경우. 메시지에는 위반 코드만 담는다.
     */
    @JvmStatic
    public fun openSession(document: SceneDocument): KineticaSession = when (val result = KineticaSession.create(document)) {
        is SessionOpenResult.Opened -> result.session
        is SessionOpenResult.Rejected -> throw IllegalArgumentException("문서가 올바르지 않습니다: ${result.violations.map { it.code }}")
    }

    /**
     * JSON에서 장면 문서를 읽는다.
     *
     * @throws IllegalArgumentException 읽을 수 없는 경우. 메시지에 사용자 콘텐츠는 담지 않는다.
     */
    @JvmStatic
    public fun decode(json: String): SceneDocument = when (val result = codec.decode(json)) {
        is DecodeResult.Success -> result.document
        is DecodeResult.Failure -> throw IllegalArgumentException("장면을 읽을 수 없습니다: ${result.error::class.simpleName}")
    }

    @JvmStatic
    public fun encode(document: SceneDocument): String = codec.encode(document)

    /**
     * 최상위에 넣을 사각형 노드를 만든다. 위치와 크기는 문서 단위이고 [argb]는 `0xAARRGGBB` 형식이다.
     *
     * @throws IllegalArgumentException id 형식이 잘못되었거나 값이 유한하지 않은 경우
     */
    // Java에서 한 줄로 만들 수 있도록 위치, 크기, 색을 객체로 묶지 않고 그대로 인자로 받는다.
    @Suppress("LongParameterList")
    @JvmStatic
    public fun rectangle(id: String, x: Double, y: Double, width: Double, height: Double, argb: Int): SceneNode = SceneNode(
        id = NodeId(id),
        content = NodeContent.Rectangle(Size(width, height)),
        transform = Transform2D(translationX = x, translationY = y, pivotX = width / 2.0, pivotY = height / 2.0),
        style = NodeStyle(fill = ArgbColor(argb)),
    )

    /** 노드를 맨 위에 넣는다. 거부되면 `false`를 돌려주며 문서는 바뀌지 않는다. */
    @JvmStatic
    public fun add(session: KineticaSession, node: SceneNode): Boolean =
        session.commands.execute(SceneCommand.AddNode(node)) is CommandResult.Applied

    /** 노드를 문서 단위만큼 옮긴다. 실행 취소 한 단계로 기록된다. */
    @JvmStatic
    public fun moveBy(session: KineticaSession, id: String, dx: Double, dy: Double): Boolean {
        val nodeId = NodeId.parseOrNull(id) ?: return false
        val node = session.document.value.node(nodeId) ?: return false
        val moved = node.transform.copy(translationX = node.transform.translationX + dx, translationY = node.transform.translationY + dy)
        return session.commands.execute(SceneCommand.TransformNodes(mapOf(nodeId to moved))) is CommandResult.Applied
    }

    /** 현재 문서. */
    @JvmStatic
    public fun document(session: KineticaSession): SceneDocument = session.document.value
}
