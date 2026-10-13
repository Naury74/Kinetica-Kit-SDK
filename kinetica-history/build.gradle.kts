plugins {
    alias(libs.plugins.kinetica.jvm.library)
}

dependencies {
    api(project(":kinetica-core"))
    // 세션이 문서·선택 상태를 StateFlow로 내보낸다. dispatcher나 Android 의존성은 쓰지 않는다.
    api(libs.kotlinx.coroutines.core)

    testImplementation(testFixtures(project(":kinetica-core")))
}
