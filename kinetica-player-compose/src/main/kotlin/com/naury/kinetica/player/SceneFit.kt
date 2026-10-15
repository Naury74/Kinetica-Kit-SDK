package com.naury.kinetica.player

/** 재생기가 장면을 영역에 맞추는 방식. */
public enum class SceneFit {
    /** artboard 비율을 유지한 채 영역 안에 최대한 크게 맞춘다. 남는 공간은 비워 둔다. */
    Contain,

    /**
     * artboard를 영역 크기에 맞게 바꾸고, 노드를 각자의 제약([com.naury.kinetica.core.scene.LayoutConstraints])에 따라 다시 배치한다.
     * 문서 단위 크기는 [com.naury.kinetica.render.HOST_VIEW_UNITS_PER_DP]로 고정되어 있어 큰 화면에서는 더 넓게 펼쳐진다.
     */
    Responsive,
}
