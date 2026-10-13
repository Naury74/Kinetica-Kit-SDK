// 측정용 장면 배치와 반복 수는 시연 데이터라 이름 붙인 상수로 빼지 않는다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.benchmarks

import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.EasingSpec
import com.naury.kinetica.core.animation.KeyValue
import com.naury.kinetica.core.animation.Keyframe
import com.naury.kinetica.core.animation.PropertyTrack
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.command.SceneReducer
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.HitTester
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneGraph
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.guides.MoveSnapper
import com.naury.kinetica.guides.SnapConfig
import com.naury.kinetica.history.KineticaSession
import com.naury.kinetica.history.SessionOpenResult
import com.naury.kinetica.motion.TimelineEvaluator
import com.naury.kinetica.serialization.SceneCodec
import java.io.File
import java.time.LocalDate

private val sizes = listOf(10, 100, 500, 2_000)
private const val WARM_RUNS = 5
private const val SAMPLES_PER_RUN = 40

/** docs 15 성능 절차의 JVM 부분. 5번 예열한 뒤 측정 묶음마다 중앙값과 p95(ms)를 적는다. */
fun main(args: Array<String>) {
    val rows = sizes.flatMap { size ->
        val scene = scene(size)
        val reducer = SceneReducer()
        val graph = SceneGraph(scene)
        val codec = SceneCodec()
        val json = codec.encode(scene)
        val timeline = scene.timelines.single()
        val session = (KineticaSession.create(scene) as SessionOpenResult.Opened).session
        val target = scene.rootOrder[size / 2]
        var step = 0.0
        listOf(
            measure("명령 적용(TransformNodes, 전체 검증 포함)", size) {
                reducer.apply(scene, SceneCommand.TransformNodes(mapOf(target to Transform2D(translationX = step++))))
            },
            measure("세션 명령 + 실행 취소 기록", size) {
                session.commands.execute(SceneCommand.TransformNodes(mapOf(target to Transform2D(translationX = step++))))
            },
            measure("timeline 평가(노드마다 track 2개)", size) { TimelineEvaluator.evaluate(scene, timeline, (step++ % 1_000)) },
            measure("hit test", size) { HitTester(SceneGraph(scene)).hitTest(Vec2(step++ % 1_000, 500.0)) },
            measure("스냅(옮기기 한 프레임)", size) {
                MoveSnapper(graph, listOf(target), SnapConfig(8.0, 12.0)).snap(Vec2(step++ % 50, 3.0), 1.0)
            },
            measure("JSON 쓰기", size) { codec.encode(scene) },
            measure("JSON 읽기(검증 포함)", size) { codec.decode(json) },
        )
    }
    val report = buildString {
        appendLine("# Kinetica JVM 기준 측정")
        appendLine()
        appendLine("- 날짜: ${LocalDate.now()}")
        appendLine(
            "- JVM: ${System.getProperty(
                "java.vm.name",
            )} ${System.getProperty("java.version")}, ${System.getProperty("os.name")} ${System.getProperty("os.arch")}",
        )
        appendLine("- 방법: 묶음당 $SAMPLES_PER_RUN 회, 예열 $WARM_RUNS 묶음 뒤 측정 $WARM_RUNS 묶음. 값은 한 번 실행의 ms.")
        appendLine("- 기기 프레임 시간은 이 표에 없다(docs 15: 실기기 측정 필요).")
        appendLine()
        appendLine("| 노드 수 | 작업 | 중앙값 ms | p95 ms |")
        appendLine("| ---: | --- | ---: | ---: |")
        rows.forEach { appendLine("| ${it.size} | ${it.name} | ${"%.3f".format(it.median)} | ${"%.3f".format(it.p95)} |") }
    }
    val output = File(args.firstOrNull() ?: "benchmarks.md")
    output.parentFile?.mkdirs()
    output.writeText(report)
    println(report)
}

private class Row(val name: String, val size: Int, val median: Double, val p95: Double)

private fun measure(name: String, size: Int, block: () -> Any?): Row {
    repeat(WARM_RUNS * SAMPLES_PER_RUN) { block() }
    val samples = (0 until WARM_RUNS * SAMPLES_PER_RUN).map {
        val start = System.nanoTime()
        block()
        (System.nanoTime() - start) / 1_000_000.0
    }.sorted()
    return Row(name, size, samples[samples.size / 2], samples[(samples.size * 95 / 100).coerceAtMost(samples.size - 1)])
}

/** 격자로 놓은 사각형 [count]개. 노드마다 x 이동·불투명도 track이 있다. */
private fun scene(count: Int): SceneDocument {
    val nodes = (0 until count).map { index ->
        SceneNode(
            id = NodeId("n$index"),
            content = NodeContent.Rectangle(Size(40.0, 30.0), cornerRadius = 4.0),
            transform = Transform2D(
                translationX = (index % 40) * 26.0,
                translationY = (index / 40) * 36.0,
                rotationDegrees =
                    (index % 7) * 5.0,
            ),
            style = NodeStyle(fill = ArgbColor(0xFF7C8CFF.toInt())),
        )
    }
    val tracks = nodes.flatMap { node ->
        listOf(
            PropertyTrack(
                node.id,
                AnimatableProperty.TranslationX,
                listOf(
                    Keyframe(0, KeyValue.Scalar(node.transform.translationX), EasingSpec.EaseInOut),
                    Keyframe(1_000, KeyValue.Scalar(node.transform.translationX + 50.0)),
                ),
            ),
            PropertyTrack(
                node.id,
                AnimatableProperty.Opacity,
                listOf(Keyframe(0, KeyValue.Scalar(0.0)), Keyframe(1_000, KeyValue.Scalar(1.0))),
            ),
        )
    }
    return SceneDocument(
        documentId = "bench-$count",
        artboard = Artboard(1080.0, 1920.0),
        nodes = nodes.associateBy { it.id },
        rootOrder = nodes.map { it.id },
        timelines = listOf(Timeline("main", 1_000, tracks)),
    )
}
