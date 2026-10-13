pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Kinetica"

include(":sample-studio")
include(":kinetica-core")
include(":kinetica-history")
include(":kinetica-motion")
include(":kinetica-guides")
include(":kinetica-presets")
include(":kinetica-serialization")
include(":kinetica-render-compose")
include(":kinetica-gestures")
include(":kinetica-editor-compose")
