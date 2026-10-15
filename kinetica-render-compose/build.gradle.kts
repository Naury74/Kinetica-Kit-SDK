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
    // ContentTransitions가 ContentTransform을 공개 API로 돌려준다.
    api(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.kotlinx.coroutines.android)
    // API 26·27에서 사진의 EXIF 회전을 읽는다(28부터는 ImageDecoder가 반영한다).
    implementation(libs.androidx.exifinterface)

    testImplementation(testFixtures(project(":kinetica-core")))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.kotlinx.coroutines.test)
}
