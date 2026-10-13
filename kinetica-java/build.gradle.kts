plugins {
    alias(libs.plugins.kinetica.jvm.library)
}

dependencies {
    api(project(":kinetica-core"))
    api(project(":kinetica-history"))
    api(project(":kinetica-serialization"))
}
