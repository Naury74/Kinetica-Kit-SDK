import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kinetica.quality)
}

android {
    namespace = "com.naury.kinetica.gallery"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.naury.kinetica.gallery"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
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
}

dependencies {
    // docs 20 Journey C: 재생기만 쓴다. 편집기·세션·저장 모듈은 넣지 않는다.
    implementation(project(":kinetica-player-compose"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
}

// release 런타임에 편집용 모듈이 들어가지 않았는지 확인한다.
fun Configuration.projectDependencyNames(): Provider<Set<String>> = incoming.resolutionResult.rootComponent.map { root ->
    val seen = mutableSetOf<String>()
    val pending = ArrayDeque(listOf(root))
    while (pending.isNotEmpty()) {
        pending
            .removeFirst()
            .dependencies
            .filterIsInstance<ResolvedDependencyResult>()
            .map { it.selected }
            .forEach { component ->
                val id = component.id
                if (id is ProjectComponentIdentifier && seen.add(id.projectName)) pending.add(component)
            }
    }
    seen
}

tasks.register("verifyReleaseClasspath") {
    group = "verification"
    description = "release 런타임에 재생에 필요한 모듈만 있는지 확인한다."
    val projects = configurations.named("releaseRuntimeClasspath").flatMap { it.projectDependencyNames() }
    // 서버 주도 재생이 JSON을 읽으므로 serialization은 재생기에 포함된다.
    val allowed = setOf("kinetica-player-compose", "kinetica-render-compose", "kinetica-motion", "kinetica-serialization", "kinetica-core")
    doLast {
        val names = projects.get()
        val extra = names - allowed
        check(extra.isEmpty()) { "release 런타임에 재생에 필요 없는 모듈이 들어갔습니다: $extra" }
    }
}
tasks.named("check") { dependsOn("verifyReleaseClasspath") }
