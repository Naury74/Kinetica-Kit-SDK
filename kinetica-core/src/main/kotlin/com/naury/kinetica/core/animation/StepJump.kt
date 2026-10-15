package com.naury.kinetica.core.animation

/**
 * [EasingSpec.Steps]가 값을 바꾸는 시점. CSS `steps()`의 점프 방식과 같다.
 *
 * @property wireName JSON에 저장할 때 쓰는 이름.
 */
public enum class StepJump(public val wireName: String) {
    /** 각 칸이 시작할 때 값이 오른다. 첫 칸부터 `1/n`이다. CSS `jump-start`. */
    Start("start"),

    /** 각 칸이 끝날 때 값이 오른다. 마지막 칸은 `(n-1)/n`이다. CSS `jump-end`. 기본값. */
    End("end"),

    /** 첫 칸은 0, 마지막 칸은 1이고 그 사이를 `n-1`번 오른다. 칸이 2개 이상이어야 한다. CSS `jump-none`. */
    None("none"),

    /** `n+1`번 올라 첫 칸은 `1/(n+1)`, 마지막 칸은 `n/(n+1)`이다. CSS `jump-both`. */
    Both("both"),
    ;

    public companion object {
        /** [name]과 같은 [wireName]의 값. 없으면 null. */
        public fun fromWireName(name: String): StepJump? = entries.firstOrNull { it.wireName == name }
    }
}
