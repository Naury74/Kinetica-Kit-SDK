package com.naury.kinetica.presets

import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.EasingSpec
import com.naury.kinetica.core.scene.SceneNode

/** 기본 프리셋. 끝 키는 노드의 지금 값이라, 적용해도 마지막 장면은 편집한 모습과 같다. */
internal object BuiltInPresets {
    private const val CARD_START_SCALE = 0.92
    private const val STAGGER_STEP_MS = 80L
    private const val STAGGER_DISTANCE = 40.0
    private const val SLIDE_DISTANCE = 200.0
    private const val PARALLAX_STEP = 60.0
    private val springy = EasingSpec.Spring(dampingRatio = 0.45, stiffness = 260.0)

    /** 불투명도 0에서 지금 값으로. */
    val fadeIn = MotionPreset { nodes, params ->
        val span = Span(params.startMs, params.durationMs, EasingSpec.EaseOut)
        nodes.flatMap { node -> fromTo(node, AnimatableProperty.Opacity, 0.0, node.style.opacity, span) }
    }

    /** 살짝 작고 투명한 상태에서 커지며 나타난다. */
    val cardExpand = MotionPreset { nodes, params ->
        val span = Span(params.startMs, params.durationMs, EasingSpec.EaseOut)
        nodes.flatMap { node ->
            val t = node.transform
            fromTo(node, AnimatableProperty.ScaleX, t.scaleX * CARD_START_SCALE, t.scaleX, span) +
                fromTo(node, AnimatableProperty.ScaleY, t.scaleY * CARD_START_SCALE, t.scaleY, span) +
                fromTo(node, AnimatableProperty.Opacity, 0.0, node.style.opacity, span)
        }
    }

    /** 고른 순서대로 80ms씩 늦게 아래에서 올라오며 나타난다. */
    val staggerEntrance = MotionPreset { nodes, params ->
        nodes.flatMapIndexed { index, node ->
            val span = Span(params.startMs + index * STAGGER_STEP_MS, params.durationMs, EasingSpec.EaseOut)
            val y = node.transform.translationY
            fromTo(node, AnimatableProperty.TranslationY, y + STAGGER_DISTANCE, y, span) +
                fromTo(node, AnimatableProperty.Opacity, 0.0, node.style.opacity, span)
        }
    }

    /** 0에서 지금 크기로 튕기며 커진다. */
    val elasticPop = MotionPreset { nodes, params ->
        val span = Span(params.startMs, params.durationMs, springy)
        nodes.flatMap { node ->
            fromTo(node, AnimatableProperty.ScaleX, 0.0, node.transform.scaleX, span) +
                fromTo(node, AnimatableProperty.ScaleY, 0.0, node.transform.scaleY, span)
        }
    }

    /** 왼쪽 200 문서 단위에서 들어온다. */
    val slideInLeft = MotionPreset { nodes, params ->
        val span = Span(params.startMs, params.durationMs, EasingSpec.EaseInOut)
        nodes.flatMap { node ->
            val x = node.transform.translationX
            fromTo(node, AnimatableProperty.TranslationX, x - SLIDE_DISTANCE, x, span)
        }
    }

    /** 고른 순서가 뒤일수록(앞 레이어일수록) 더 멀리 가로로 흐른다. */
    val parallax = MotionPreset { nodes, params ->
        val span = Span(params.startMs, params.durationMs, EasingSpec.Linear)
        nodes.flatMapIndexed { index, node ->
            val x = node.transform.translationX
            fromTo(node, AnimatableProperty.TranslationX, x, x - PARALLAX_STEP * (index + 1), span)
        }
    }

    /** 시작 시각, 길이, 시작 키의 easing. */
    private class Span(val startMs: Long, val durationMs: Long, val easing: EasingSpec)

    private fun fromTo(node: SceneNode, property: AnimatableProperty, from: Double, to: Double, span: Span): List<PresetKey> = listOf(
        PresetKey(node.id, property, span.startMs, from, span.easing),
        PresetKey(node.id, property, span.startMs + span.durationMs, to, EasingSpec.Linear),
    )
}
