plugins {
    alias(libs.plugins.kinetica.android.library)
    alias(libs.plugins.kinetica.android.compose)
}

android {
    namespace = "com.naury.kinetica.board"
}

dependencies {
    api(project(":kinetica-render-compose"))
    api(project(":kinetica-history"))
    api(project(":kinetica-serialization"))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(testFixtures(project(":kinetica-core")))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
}
