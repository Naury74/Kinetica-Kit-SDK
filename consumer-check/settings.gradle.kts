// 배포된 artifact만 쓰는 독립 소비자 빌드(docs 16·20 Journey C). 이 저장소의 모듈을 project로 참조하지 않는다.
// 실행: ./gradlew publishToMavenLocal && ./gradlew -p consumer-check :app:assembleRelease
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal {
            content { includeGroup("com.naurylab") }
        }
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}

rootProject.name = "kinetica-consumer-check"
include(":app")
