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
    // XML 호스트 구성(목록, 페이저, Fragment, CoordinatorLayout)에 넣었을 때의 동작을 시험한다.
    testImplementation(libs.androidx.viewpager2)
    testImplementation(libs.androidx.fragment.ktx)
    testImplementation(libs.androidx.coordinatorlayout)
    testImplementation(libs.material)
}
