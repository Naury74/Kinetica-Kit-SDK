package com.naury.kinetica.player

/** 재생기가 장면을 영역에 맞추는 방식. */
public enum class SceneFit {
    /** artboard 비율을 지켜 영역 안에 꽉 차게 맞춘다. 남는 곳은 비운다. */
    Contain,

    /**
     * artboard를 영역 모양으로 바꾸고 노드를 각자의 제약([com.naury.kinetica.core.scene.LayoutConstraints])대로 옮긴다.
     * 문서 단위 크기는 [com.naury.kinetica.render.HOST_VIEW_UNITS_PER_DP]로 고정이라 큰 화면에서는 더 넓게 펼쳐진다(화면 크기 대응).
     */
    Responsive,
}
