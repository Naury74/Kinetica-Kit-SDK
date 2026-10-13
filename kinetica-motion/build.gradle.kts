plugins {
    alias(libs.plugins.kinetica.jvm.library)
}

dependencies {
    api(project(":kinetica-core"))

    testImplementation(testFixtures(project(":kinetica-core")))
}
