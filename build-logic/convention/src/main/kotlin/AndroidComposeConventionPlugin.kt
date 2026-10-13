import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            extensions.getByType<CommonExtension>().buildFeatures.compose = true
            dependencies {
                // 버전 없이 쓰는 Compose 의존성의 버전을 소비자도 알 수 있게 BOM을 api로 내보낸다.
                // implementation이면 api로 노출한 Compose artifact가 소비자 쪽에서 버전 없이 남는다(소비자 확인 빌드에서 발견).
                "api"(platform(libs.library("androidx-compose-bom")))
            }
        }
    }
}
