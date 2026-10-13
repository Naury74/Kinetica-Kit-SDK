package com.naury.kinetica.core.scene

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.testing.TestScenes
import com.naury.kinetica.core.testing.TestScenes.document
import com.naury.kinetica.core.testing.TestScenes.group
import com.naury.kinetica.core.testing.TestScenes.id
import com.naury.kinetica.core.testing.TestScenes.rect
import org.junit.Assert.assertThrows
import org.junit.Test

class SceneValidatorTest {
    private val validator = SceneValidator()

    private fun codes(document: SceneDocument, validator: SceneValidator = this.validator): List<ViolationCode> =
        validator.validate(document).map { it.code }

    @Test
    fun `올바른 문서는 문제가 없다`() {
        assertThat(validator.validate(TestScenes.sample())).isEmpty()
        assertThat(validator.validate(SceneDocument.empty(TestScenes.artboard))).isEmpty()
    }

    @Test
    fun `최상위 순서에 같은 id가 두 번 있으면 거부한다`() {
        val doc = document(listOf(rect("a")), rootOrder = listOf("a", "a"))

        assertThat(codes(doc)).containsExactly(ViolationCode.DuplicateChild)
    }

    @Test
    fun `한 노드가 두 그룹에 들어 있으면 거부한다`() {
        val doc = document(
            listOf(group("g1", listOf("x")), group("g2", listOf("x")), rect("x", parent = "g1")),
            rootOrder = listOf("g1", "g2"),
        )

        assertThat(codes(doc)).contains(ViolationCode.DuplicateChild)
    }

    @Test
    fun `서로를 담은 그룹은 순환으로 거부한다`() {
        val doc = document(
            listOf(group("p", listOf("q"), parent = "q"), group("q", listOf("p"), parent = "p"), rect("r")),
            rootOrder = listOf("r"),
        )

        assertThat(codes(doc)).containsExactly(ViolationCode.Cycle, ViolationCode.Cycle)
    }

    @Test
    fun `자기 자신을 담은 그룹은 순환으로 거부한다`() {
        val doc = document(listOf(group("self", listOf("self"), parent = "self")), rootOrder = emptyList())

        assertThat(codes(doc)).containsExactly(ViolationCode.Cycle)
    }

    @Test
    fun `깊이 32는 허용하고 33은 거부한다`() {
        assertThat(codes(nested(depth = 32))).isEmpty()
        assertThat(codes(nested(depth = 33))).containsExactly(ViolationCode.TooDeep)
        assertThat(codes(nested(depth = 33), SceneValidator(SceneLimits(maxDepth = 40)))).isEmpty()
    }

    @Test
    fun `깊이 상한은 64를 넘을 수 없다`() {
        assertThrows(IllegalArgumentException::class.java) { SceneLimits(maxDepth = 65) }
    }

    @Test
    fun `노드 수 상한을 넘으면 다른 검사 없이 거부한다`() {
        val doc = document((1..11).map { rect("n$it") })

        assertThat(codes(doc, SceneValidator(SceneLimits(maxNodes = 10)))).containsExactly(ViolationCode.TooManyNodes)
    }

    @Test
    fun `부모·자식 관계가 어긋나면 거부한다`() {
        val keyMismatch = TestScenes.sample().let { it.copy(nodes = it.nodes + (id("zz") to rect("a"))) }
        val parentMismatch = document(listOf(group("g", listOf("c")), rect("c")), rootOrder = listOf("g"))
        val parentNotGroup = document(listOf(rect("a"), rect("c", parent = "a")), rootOrder = listOf("a"))
        val missingChild = document(listOf(group("g", listOf("ghost"))))
        val unplaced = document(listOf(rect("a"), rect("b")), rootOrder = listOf("a"))

        assertThat(codes(keyMismatch)).contains(ViolationCode.NodeKeyMismatch)
        assertThat(codes(parentMismatch)).contains(ViolationCode.ParentMismatch)
        assertThat(codes(parentNotGroup)).contains(ViolationCode.ParentNotGroup)
        assertThat(codes(missingChild)).contains(ViolationCode.MissingNode)
        assertThat(codes(unplaced)).containsExactly(ViolationCode.UnreachableNode)
    }

    @Test
    fun `NaN과 무한대는 값 객체를 만들 때 거부한다`() {
        assertThrows(IllegalArgumentException::class.java) { Transform2D(translationX = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { Size(Double.POSITIVE_INFINITY, 1.0) }
    }

    @Test
    fun `NaN·범위 밖 스타일은 거부한다`() {
        val nanOpacity = rect("a").copy(style = NodeStyle(opacity = Double.NaN))
        val negativeStroke = rect("b").copy(style = NodeStyle(strokeWidth = -1.0))

        assertThat(
            codes(document(listOf(nanOpacity, negativeStroke))),
        ).containsExactly(ViolationCode.InvalidStyle, ViolationCode.InvalidStyle)
    }

    @Test
    fun `퇴화한 배율과 허용되지 않은 반전을 거부한다`() {
        val degenerate = rect("a", transform = Transform2D(scaleX = 1e-7))
        val flipped = rect("b", transform = Transform2D(scaleY = -1.0))
        val doc = document(listOf(degenerate, flipped))

        assertThat(codes(doc)).containsExactly(ViolationCode.InvalidTransform, ViolationCode.InvalidTransform)
        assertThat(codes(doc, SceneValidator(SceneLimits(allowFlip = true)))).containsExactly(ViolationCode.InvalidTransform)
    }

    @Test
    fun `터무니없이 큰 좌표를 거부한다`() {
        val doc = document(listOf(rect("a", transform = Transform2D(translationX = 1e300)), rect("b", size = Size(1e9, 1.0))))

        assertThat(codes(doc)).containsExactly(ViolationCode.InvalidTransform, ViolationCode.InvalidGeometry)
    }

    @Test
    fun `글자 바이트 상한과 잘못된 유니코드를 거부한다`() {
        val limit = SceneLimits.Default.maxTextBytes
        val atLimit = textNode("ok", "가".repeat(limit / 3))
        val overLimit = textNode("long", "a".repeat(limit + 1))
        val loneSurrogate = textNode("bad", "abc\uD800def")

        assertThat(codes(document(listOf(atLimit)))).isEmpty()
        assertThat(codes(document(listOf(overLimit)))).containsExactly(ViolationCode.InvalidText)
        assertThat(codes(document(listOf(loneSurrogate)))).containsExactly(ViolationCode.InvalidText)
    }

    @Test
    fun `허용되지 않은 이미지 URI를 거부한다`() {
        fun uriCodes(uri: String) = codes(document(listOf(SceneNode(id("img"), NodeContent.Image(Size(10.0, 10.0), uri)))))

        assertThat(uriCodes("content://media/external/images/1")).isEmpty()
        assertThat(uriCodes("android.resource://com.example/drawable/photo")).isEmpty()
        listOf(
            "https://example.com/a.png",
            "http://example.com/a.png",
            "file:///sdcard/a.png",
            "/data/data/app/files/a.png",
            "content://media/../../secret",
            "content://media/%2e%2e/secret",
            "content://media/a b",
            "javascript:alert(1)",
        ).forEach { assertThat(uriCodes(it)).containsExactly(ViolationCode.InvalidUri) }
    }

    @Test
    fun `경로는 MoveTo로 시작하고 명령이 있어야 한다`() {
        val empty = SceneNode(id("p1"), NodeContent.Path(emptyList()))
        val noMove = SceneNode(id("p2"), NodeContent.Path(listOf(PathCommand.Close)))

        assertThat(codes(document(listOf(empty, noMove)))).containsExactly(ViolationCode.InvalidGeometry, ViolationCode.InvalidGeometry)
    }

    @Test
    fun `문제 설명에 사용자 글자를 넣지 않는다`() {
        val secret = "SECRET-TEXT"
        val doc = document(listOf(textNode("t", secret + "\uD800").copy(name = secret)))

        assertThat(validator.validate(doc).joinToString { it.detail }).doesNotContain(secret)
    }

    @Test
    fun `같은 내용으로 만든 문서는 같다`() {
        assertThat(TestScenes.sample()).isEqualTo(TestScenes.sample())
        assertThat(TestScenes.sample().hashCode()).isEqualTo(TestScenes.sample().hashCode())
        assertThat(TestScenes.sample().copy(rootOrder = listOf(id("b"), id("a"), id("g")))).isNotEqualTo(TestScenes.sample())
    }

    @Test
    fun `지원하지 않는 schema 버전과 잘못된 artboard를 거부한다`() {
        val doc = TestScenes.sample().copy(schemaVersion = 2, artboard = Artboard(0.0, Double.NaN), documentId = "")

        assertThat(codes(doc)).containsExactly(
            ViolationCode.InvalidDocumentId,
            ViolationCode.UnsupportedSchemaVersion,
            ViolationCode.InvalidArtboard,
        )
    }

    private fun textNode(id: String, text: String) = SceneNode(id(id), NodeContent.Text(Size(200.0, 100.0), text))

    private fun nested(depth: Int): SceneDocument {
        val nodes = (1..depth).map { level ->
            val children = if (level < depth) listOf("n${level + 1}") else emptyList()
            group("n$level", children, parent = if (level == 1) null else "n${level - 1}")
        }
        return document(nodes, rootOrder = listOf("n1"))
    }
}
