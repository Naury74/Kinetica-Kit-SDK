import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.consumer"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "com.example.consumer"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            optimization { enable = true }
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions { animationsDisabled = false }
}

// 루트에서 배포한 버전과 어긋나지 않도록 루트 gradle.properties의 버전을 읽는다. -PKINETICA_VERSION으로 덮어쓸 수 있다.
val kineticaVersion: String = providers.gradleProperty("KINETICA_VERSION").orNull
    ?: Properties().apply { rootDir.resolve("../gradle.properties").inputStream().use { load(it) } }.getProperty("KINETICA_VERSION")

dependencies {
    implementation("com.naurylab:kinetica-player-compose:$kineticaVersion")
    implementation("com.naurylab:kinetica-board-compose:$kineticaVersion")
    implementation("com.naurylab:kinetica-ui:$kineticaVersion")
    implementation("com.naurylab:kinetica-presets:$kineticaVersion")
    implementation("com.naurylab:kinetica-editor-compose:$kineticaVersion")
    implementation(libs.androidx.activity.compose)

    // 실제 호스트 앱이 흔히 함께 쓰는 라이브러리. 루트 카탈로그에 없는 것은 좌표를 직접 적는다.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.fragment:fragment-ktx:1.9.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.3.0")
    implementation("com.google.android.material:material:1.14.0")

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.4.0")
    // 폴더블 탁자 모드를 시험에서 흉내 낸다.
    androidTestImplementation(libs.androidx.window.testing)
}
