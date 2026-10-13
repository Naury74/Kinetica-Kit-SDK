plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("jvmLibrary") {
            id = "kinetica.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("androidLibrary") {
            id = "kinetica.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("quality") {
            id = "kinetica.quality"
            implementationClass = "QualityConventionPlugin"
        }
        register("androidCompose") {
            id = "kinetica.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
    }
}
