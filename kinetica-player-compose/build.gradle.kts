import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult

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
    // XML 호스트 구성(목록, 페이저, Fragment, CoordinatorLayout)에 넣었을 때의 동작을 시험한다.
    testImplementation(libs.androidx.viewpager2)
    testImplementation(libs.androidx.fragment.ktx)
}

// 재생기만 쓰는 앱에 편집용 모듈이 들어가지 않도록, 재생기 release 런타임의 SDK 모듈을 확인한다.
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
    description = "재생기의 release 런타임에 재생에 필요한 모듈만 있는지(편집기·세션이 따라오지 않는지) 확인한다."
    val projects = configurations.named("releaseRuntimeClasspath").flatMap { it.projectDependencyNames() }
    // 서버 주도 재생이 JSON을 읽으므로 serialization은 재생기에 포함된다.
    val allowed = setOf("kinetica-render-compose", "kinetica-motion", "kinetica-serialization", "kinetica-core")
    doLast {
        val names = projects.get()
        val extra = names - allowed
        check(extra.isEmpty()) { "release 런타임에 재생에 필요 없는 모듈이 들어갔습니다: $extra" }
    }
}
tasks.named("check") { dependsOn("verifyReleaseClasspath") }
