package com.naury.kinetica.serialization

import kotlinx.serialization.json.JsonObject

/**
 * 형식 버전 하나를 다음 버전으로 올리는 단계. JSON 트리를 받아 [fromVersion] + 1 버전의 트리를 돌려준다.
 * 결과의 `schemaVersion`도 함께 바꿔야 한다.
 */
public fun interface SceneMigration {
    public fun migrate(document: JsonObject): JsonObject
}

/**
 * 버전별 migration 묶음. 키는 시작 버전이다. 시작 버전부터 현재 버전까지 빠짐없이 이어져야 읽을 수 있다.
 */
public class SceneMigrations(private val steps: Map<Int, SceneMigration>) {
    /** [from]에서 [to]까지 올린 트리. 중간 단계가 없으면 `null`이다. */
    internal fun upgrade(document: JsonObject, from: Int, to: Int): JsonObject? {
        var current = document
        for (version in from until to) {
            val step = steps[version] ?: return null
            current = step.migrate(current)
        }
        return current
    }

    public companion object {
        /** 형식 버전 1이 처음이라 아직 migration이 없다. */
        public val None: SceneMigrations = SceneMigrations(emptyMap())
    }
}
