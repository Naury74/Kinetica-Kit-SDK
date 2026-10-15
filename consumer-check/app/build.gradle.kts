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
}

dependencies {
    implementation("com.naurylab:kinetica-player-compose:${providers.gradleProperty("KINETICA_VERSION").get()}")
    implementation("com.naurylab:kinetica-board-compose:${providers.gradleProperty("KINETICA_VERSION").get()}")
    implementation("com.naurylab:kinetica-ui:${providers.gradleProperty("KINETICA_VERSION").get()}")
    implementation(libs.androidx.activity.compose)
}
