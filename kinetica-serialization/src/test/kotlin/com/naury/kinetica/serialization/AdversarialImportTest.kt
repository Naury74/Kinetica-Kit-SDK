package com.naury.kinetica.serialization

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.scene.ViolationCode
import com.naury.kinetica.core.testing.TestScenes
import kotlin.random.Random
import org.junit.Test

/** docs 14·20 Journey D: 신뢰할 수 없는 문서를 읽어도 예외 없이 거부 이유를 돌려주고 사용자 글자를 남기지 않는다. */
class AdversarialImportTest {
    private val codec = SceneCodec()

    private fun header(nodes: String, rootOrder: String, extra: String = "") =
        """{"schemaVersion":1,"documentId":"d","canvas":{"width":100,"height":100},"nodes":[$nodes],"rootOrder":[$rootOrder]$extra}"""

    private fun rect(id: String, extra: String = "") = """{"id":"$id","type":"rect","bounds":{"width":10,"height":10}$extra}"""

    private fun error(text: String): SceneLoadError = (codec.decode(text) as DecodeResult.Failure).error

    private fun violations(text: String): List<ViolationCode> = (error(text) as SceneLoadError.Invalid).violations.map { it.code }

    @Test
    fun `25MB 입력은 해석하지 않고 거부한다`() {
        val huge = "{\"pad\":\"" + "x".repeat(25 * 1024 * 1024) + "\"}"

        assertThat(error(huge)).isInstanceOf(SceneLoadError.TooLarge::class.java)
    }

    @Test
    fun `아주 깊게 중첩된 JSON도 stack을 넘지 않고 거부한다`() {
        val deep = "[".repeat(200_000) + "]".repeat(200_000)

        assertThat(error(deep)).isInstanceOf(SceneLoadError.Malformed::class.java)
        assertThat(error("{\"a\":" + "[".repeat(200_000) + "]".repeat(200_000) + "}")).isInstanceOf(SceneLoadError.Malformed::class.java)
    }

    @Test
    fun `확장 데이터는 허용 깊이까지 보존한다`() {
        val nested = "[".repeat(50) + "1" + "]".repeat(50)
        val text = header(rect("a"), "\"a\"", ",\"extensions\":{\"host.deep\":$nested}")

        assertThat((codec.decode(text) as DecodeResult.Success).document.extensions.getValue("host.deep")).isEqualTo(nested)
        assertThat(error("\"[[[[\"")).isInstanceOf(SceneLoadError.Malformed::class.java)
    }

    @Test
    fun `순환·중복·고아 노드를 거부한다`() {
        val cycle = header(
            """{"id":"p","type":"group","parentId":"q","children":["q"]},{"id":"q","type":"group","parentId":"p","children":["p"]}""",
            "",
        )
        assertThat(violations(cycle)).contains(ViolationCode.Cycle)
        assertThat(error(header(rect("a") + "," + rect("a"), "\"a\""))).isInstanceOf(SceneLoadError.Malformed::class.java)
        assertThat(violations(header(rect("a") + "," + rect("b"), "\"a\""))).containsExactly(ViolationCode.UnreachableNode)
    }

    @Test
    fun `유한하지 않은 숫자와 잘못된 id를 거부한다`() {
        listOf("1e309", "-1e309", "NaN", "Infinity", "\"12\"", "true", "[]").forEach { value ->
            assertThat(
                error(header(rect("a", ""","transform":{"x":$value}"""), "\"a\"")),
            ).isInstanceOf(SceneLoadError.Malformed::class.java)
        }
        // 선택 필드의 null은 없는 것과 같다.
        assertThat(codec.decode(header(rect("a", ""","transform":{"x":null}"""), "\"a\""))).isInstanceOf(DecodeResult.Success::class.java)
        listOf("""a\u0000b""", "../x", "", "a b", "x".repeat(200)).forEach { id ->
            assertThat(error(header(rect(id), "\"$id\""))).isInstanceOf(SceneLoadError.Malformed::class.java)
        }
    }

    @Test
    fun `잘못된 유니코드와 너무 긴 글자를 거부한다`() {
        val lone = header("""{"id":"t","type":"text","bounds":{"width":1,"height":1},"text":"a\uD800b"}""", "\"t\"")
        val long = header("""{"id":"t","type":"text","bounds":{"width":1,"height":1},"text":"${"가".repeat(30_000)}"}""", "\"t\"")
        val badName = header(rect("a", ""","name":"\uDC00""""), "\"a\"")

        assertThat(violations(lone)).containsExactly(ViolationCode.InvalidText)
        assertThat(violations(long)).containsExactly(ViolationCode.InvalidText)
        assertThat(violations(badName)).containsExactly(ViolationCode.InvalidName)
    }

    @Test
    fun `위험한 이미지 URI를 거부한다`() {
        listOf(
            "https://evil.example/a.png",
            "file:///data/data/x/secret",
            "content://media/../../secret",
            "content://media/%2E%2E/secret",
            "javascript:alert(1)",
            "/sdcard/a.png",
            "content://a/" + "b".repeat(5_000),
        ).forEach { uri ->
            val text = header("""{"id":"i","type":"image","bounds":{"width":1,"height":1},"uri":"$uri"}""", "\"i\"")
            assertThat(violations(text)).containsExactly(ViolationCode.InvalidUri)
        }
    }

    @Test
    fun `거대한 경로와 keyframe 폭탄은 객체를 만들기 전에 거부한다`() {
        val commands = (0..10_001).joinToString(",") { """{"op":"L","x":$it,"y":0}""" }
        val path = header("""{"id":"p","type":"path","path":[{"op":"M","x":0,"y":0},$commands]}""", "\"p\"")
        val keys = (0..100_001).joinToString(",") { """{"timeMs":$it,"value":0}""" }
        val bomb =
            header(
                rect("a"),
                "\"a\"",
                ""","animations":[{"id":"t","durationMs":200000,"tracks":[{"nodeId":"a","property":"opacity","keyframes":[$keys]}]}]""",
            )

        assertThat(error(path)).isInstanceOf(SceneLoadError.Malformed::class.java)
        assertThat(error(bomb)).isInstanceOf(SceneLoadError.Malformed::class.java)
    }

    @Test
    fun `잘못된 easing과 터무니없는 크기를 거부한다`() {
        val bezier = header(
            rect("a"),
            "\"a\"",
            ""","animations":[{"id":"t","durationMs":10,"tracks":[{"nodeId":"a","property":"opacity","keyframes":[""" +
                """{"timeMs":0,"value":0,"easing":{"type":"cubicBezier","x1":2,"y1":0,"x2":0.5,"y2":1}},{"timeMs":10,"value":1}]}]}]""",
        )
        val giant = header("""{"id":"a","type":"rect","bounds":{"width":1e12,"height":1}}""", "\"a\"")
        val artboard = """{"schemaVersion":1,"documentId":"d","canvas":{"width":1e12,"height":0},"nodes":[],"rootOrder":[]}"""

        assertThat(violations(bezier)).containsExactly(ViolationCode.InvalidEasing)
        assertThat(violations(giant)).containsExactly(ViolationCode.InvalidGeometry)
        assertThat(violations(artboard)).containsExactly(ViolationCode.InvalidArtboard)
    }

    @Test
    fun `잘린 입력은 어느 위치에서 잘려도 예외 없이 거부한다`() {
        val valid = codec.encode(TestScenes.sample())

        (0 until valid.length step 3).forEach { cut ->
            assertThat(codec.decode(valid.substring(0, cut))).isInstanceOf(DecodeResult.Failure::class.java)
        }
    }

    @Test
    fun `무작위로 바꾼 입력도 예외 없이 결과를 돌려준다`() {
        val valid = codec.encode(TestScenes.sample()).toByteArray()
        val random = Random(seed = 503)
        repeat(3_000) {
            val mutated = valid.copyOf()
            repeat(random.nextInt(1, 8)) { mutated[random.nextInt(mutated.size)] = random.nextInt(256).toByte() }

            val result = codec.decode(mutated)

            assertThat(result is DecodeResult.Success || result is DecodeResult.Failure).isTrue()
        }
    }

    @Test
    fun `오류 내용에 문서의 글자·이름·URI를 담지 않는다`() {
        val secret = "TOP-SECRET-42"
        val documents = listOf(
            header("""{"id":"t","type":"text","name":"$secret","bounds":{"width":1,"height":1},"text":"$secret\uD800"}""", "\"t\""),
            header("""{"id":"i","type":"image","bounds":{"width":1,"height":1},"uri":"https://$secret"}""", "\"i\""),
            header(rect("a", ""","metadata":{"k":"$secret","bad":1}"""), "\"a\""),
            header(rect("a", ""","style":{"fill":"$secret"}"""), "\"a\""),
        )

        documents.forEach { assertThat(error(it).toString()).doesNotContain(secret) }
    }
}
