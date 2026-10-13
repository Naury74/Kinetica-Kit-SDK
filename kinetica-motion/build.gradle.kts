plugins {
    alias(libs.plugins.kinetica.jvm.library)
}

dependencies {
    api(project(":kinetica-core"))
    // 재생 상태를 StateFlow로 내보내는 데만 쓴다. 평가(TimelineEvaluator)는 coroutines를 쓰지 않는다.
    api(libs.kotlinx.coroutines.core)

    testImplementation(testFixtures(project(":kinetica-core")))
}
