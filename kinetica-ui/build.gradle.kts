plugins {
    alias(libs.plugins.kinetica.android.library)
    alias(libs.plugins.kinetica.android.compose)
}

android {
    namespace = "com.naury.kinetica.ui"
}

dependencies {
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.animation)
    implementation(libs.kotlinx.coroutines.android)
    // 팝업·시트를 뒤로 가기로 닫는다.
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    // 호스트 앱처럼 Material 3 컴포넌트에 효과를 붙여 시험한다.
    testImplementation(libs.androidx.compose.material3)
    // XML 호스트 구성(목록, 페이저, Fragment, CoordinatorLayout)에 넣었을 때의 동작을 시험한다.
    testImplementation(libs.androidx.recyclerview)
    testImplementation(libs.androidx.viewpager2)
    testImplementation(libs.androidx.fragment.ktx)
}
