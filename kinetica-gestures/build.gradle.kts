plugins {
    alias(libs.plugins.kinetica.android.library)
    alias(libs.plugins.kinetica.android.compose)
}

android {
    namespace = "com.naury.kinetica.gestures"
}

dependencies {
    api(project(":kinetica-core"))
    api(libs.androidx.compose.ui)

    testImplementation(testFixtures(project(":kinetica-core")))
    // 제스처 하나가 실행 취소 한 단계인지 실제 세션으로 확인한다. 본 코드는 history에 의존하지 않는다.
    testImplementation(project(":kinetica-history"))
}
