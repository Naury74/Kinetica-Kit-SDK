plugins {
    alias(libs.plugins.kinetica.jvm.library)
}

dependencies {
    api(project(":kinetica-core"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(testFixtures(project(":kinetica-core")))
}
