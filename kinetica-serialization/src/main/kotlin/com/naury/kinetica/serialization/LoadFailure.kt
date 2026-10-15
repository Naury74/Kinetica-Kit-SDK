package com.naury.kinetica.serialization

/** 파싱을 중단하고 [error]를 돌려주기 위한 내부 예외. [SceneCodec] 밖으로 던지지 않는다. */
internal class LoadFailure(val error: SceneLoadError) : RuntimeException() {
    override fun fillInStackTrace(): Throwable = this
}
