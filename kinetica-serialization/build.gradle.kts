plugins {
    alias(libs.plugins.kinetica.jvm.library)
}

dependencies {
    api(project(":kinetica-core"))
    implementation(libs.kotlinx.serialization.json)
    // 파일 저장소가 IO dispatcher에서 읽고 쓰는 데만 쓴다. ADR-0003.
    api(libs.kotlinx.coroutines.core)

    testImplementation(testFixtures(project(":kinetica-core")))
    testImplementation(libs.kotlinx.coroutines.test)
}
