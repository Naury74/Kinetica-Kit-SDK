plugins {
    alias(libs.plugins.kinetica.android.library)
    alias(libs.plugins.kinetica.android.compose)
}

android {
    namespace = "com.naury.kinetica.render"
}

dependencies {
    api(project(":kinetica-core"))
    api(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(testFixtures(project(":kinetica-core")))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.kotlinx.coroutines.test)
}
