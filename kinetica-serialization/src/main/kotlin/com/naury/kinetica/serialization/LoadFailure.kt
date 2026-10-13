package com.naury.kinetica.serialization

/** 읽기를 멈추고 [error]를 돌려주기 위한 내부 예외. [SceneCodec] 밖으로 나가지 않는다. */
internal class LoadFailure(val error: SceneLoadError) : RuntimeException() {
    override fun fillInStackTrace(): Throwable = this
}
