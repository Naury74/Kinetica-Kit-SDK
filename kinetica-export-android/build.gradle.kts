plugins {
    alias(libs.plugins.kinetica.android.library)
    alias(libs.plugins.kinetica.android.compose)
}

android {
    namespace = "com.naury.kinetica.export"
}

dependencies {
    api(project(":kinetica-render-compose"))
    implementation(project(":kinetica-motion"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(testFixtures(project(":kinetica-core")))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
