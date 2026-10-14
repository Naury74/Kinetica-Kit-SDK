package com.naury.kinetica.editor

/** 속성 패널이 쓰는 입력 묶음. 모두 같은 [state]에 명령을 보낸다. */
internal class InspectorEdits(val property: PropertyEdits, state: EditorState) {
    val layout = LayoutEdits(state)
    val style = StyleEdits(state)
    val effects = EffectEdits(state)
    val shape = ShapeEdits(state)
    val text = TextEdits(state)
    val component = ComponentEdits(state)
    val motion = MotionEdits(state)
}
