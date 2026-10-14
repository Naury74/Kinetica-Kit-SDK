plugins {
    alias(libs.plugins.kinetica.android.library)
    alias(libs.plugins.kinetica.android.compose)
}

android {
    namespace = "com.naury.kinetica.player"
}

dependencies {
    api(project(":kinetica-render-compose"))
    api(project(":kinetica-motion"))
    // 서버 주도 재생(RemoteKineticaPlayer)이 받은 JSON을 같은 검증으로 읽는다.
    api(project(":kinetica-serialization"))
    implementation(libs.androidx.compose.foundation)
    api(libs.androidx.compose.animation)

    testImplementation(testFixtures(project(":kinetica-core")))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.androidx.compose.material3)
}
