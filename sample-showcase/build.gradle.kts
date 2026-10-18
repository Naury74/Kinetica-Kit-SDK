plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kinetica.quality)
}

android {
    namespace = "com.naury.kinetica.showcase"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.naury.kinetica.showcase"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        // 샘플 앱은 SDK와 같은 버전으로 배포한다.
        versionName = providers.gradleProperty("KINETICA_VERSION").get()
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            // R8을 거친 결과를 별도 키 없이 설치해 확인할 수 있도록 debug 서명을 사용한다.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    testOptions.unitTests {
        isIncludeAndroidResources = true
        all {
            // Robolectric은 JDK 내부 API로 FileDescriptor를 다루는데, JDK 17+는 이를 기본으로 export하지 않는다.
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--enable-native-access=ALL-UNNAMED")
        }
    }
}

dependencies {
    // 한 앱에서 SDK 전체를 보여 준다: 글래스 UI, 움직임 효과, 보드, 서버 화면, 편집기.
    implementation(project(":kinetica-ui"))
    implementation(project(":kinetica-board-compose"))
    implementation(project(":kinetica-presets"))
    implementation(project(":kinetica-player-compose"))
    implementation(project(":kinetica-editor-compose"))
    implementation(project(":kinetica-export-android"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // 앱 모듈의 Robolectric 테스트는 테스트용 빈 액티비티가 debug 매니페스트에 있어야 띄울 수 있다.
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
