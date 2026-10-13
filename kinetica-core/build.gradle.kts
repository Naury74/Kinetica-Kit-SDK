plugins {
    alias(libs.plugins.kinetica.jvm.library)
    `java-test-fixtures`
}

dependencies {
    testFixturesImplementation(libs.junit4)
}
