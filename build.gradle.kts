plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// SDK 모듈은 같은 좌표·POM으로 배포한다. 샘플·측정 도구는 배포하지 않는다.
val sdkModules = mapOf(
    "kinetica-core" to "불변 장면 문서, 좌표 계산, 명령·reducer, 검증 (Kotlin JVM)",
    "kinetica-history" to "트랜잭션 실행 취소와 편집 세션",
    "kinetica-motion" to "keyframe 평가, easing, 재생 제어, 장면 morph, 전환 진행률",
    "kinetica-guides" to "옮길 때 안내선 스냅",
    "kinetica-presets" to "모션 프리셋",
    "kinetica-serialization" to "장면 JSON 읽기·쓰기, migration, 파일 저장소",
    "kinetica-java" to "Java 호스트용 정적 입구(선택)",
    "kinetica-render-compose" to "Compose Canvas 장면 그리기",
    "kinetica-gestures" to "캔버스 포인터 상태 기계",
    "kinetica-editor-compose" to "장면 편집 화면 KineticaEditor",
    "kinetica-player-compose" to "읽기 전용 재생기와 전환",
    "kinetica-board-compose" to "호스트 화면에 넣는 자유 배치·분할 보드와 제자리 편집",
    "kinetica-export-android" to "PNG 내보내기",
    "kinetica-ui" to "글래스 UI 키트: 뒤를 흐리게 비추는 표면·팝업·시트·탭바",
)

// 호스트가 저장소를 공개하기 전이라 주소와 라이선스는 gradle.properties에서 읽는다. 라이선스가 없으면 Central 묶음을 만들지 않는다.
val projectUrl = providers.gradleProperty("KINETICA_URL").getOrElse("https://github.com/Naury74/Kinetica-Kit-SDK")
val licenseName = providers.gradleProperty("KINETICA_LICENSE_NAME").orNull
val licenseUrl = providers.gradleProperty("KINETICA_LICENSE_URL").orNull
val centralStaging = layout.buildDirectory.dir("central-staging")

subprojects {
    val summary = sdkModules[name] ?: return@subprojects
    apply(plugin = "maven-publish")
    apply(plugin = "signing")
    group = providers.gradleProperty("KINETICA_GROUP").get()
    version = providers.gradleProperty("KINETICA_VERSION").get()

    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.api.dsl.LibraryExtension> {
            publishing {
                singleVariant("release") { withSourcesJar() }
            }
        }
    }
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<JavaPluginExtension> { withSourcesJar() }
    }

    // Maven Central은 javadoc jar를 요구한다. API 설명은 소스 jar의 KDoc과 README로 제공하므로 안내 파일만 담는다.
    val javadocJar = tasks.register<Jar>("centralJavadocJar") {
        archiveClassifier.set("javadoc")
        from(rootProject.file("README.md"))
    }

    // AGP는 평가가 끝난 뒤에 컴포넌트를 만들므로, 컴포넌트가 생길 때 publication을 만든다.
    components.configureEach {
        val component = this
        val wanted = if (plugins.hasPlugin("com.android.library")) "release" else "java"
        if (component.name != wanted) return@configureEach
        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("release") {
                    from(component)
                    artifact(javadocJar)
                    artifactId = project.name
                    pom {
                        name.set(project.name)
                        description.set(summary)
                        url.set(projectUrl)
                        if (licenseName != null && licenseUrl != null) {
                            licenses {
                                license {
                                    name.set(licenseName)
                                    url.set(licenseUrl)
                                    distribution.set("repo")
                                }
                            }
                        }
                        developers {
                            developer {
                                id.set("Naury74")
                                name.set("Naury74")
                            }
                        }
                        scm {
                            url.set(projectUrl)
                            connection.set("scm:git:$projectUrl.git")
                        }
                    }
                }
            }
            repositories {
                maven {
                    name = "CentralStaging"
                    url = uri(centralStaging)
                }
            }
        }
        configureSigning()
    }
}

/**
 * `-PKINETICA_SIGN=true`일 때만 서명한다(Central 묶음용). mavenLocal 등 로컬 배포에는 서명하지 않는다.
 * 키는 저장소에 두지 않고 `~/.gradle/gradle.properties`나 환경 변수에서 읽는다. gpg는 병렬 서명에서 실패할 수 있어 `--max-workers=1`을 쓴다.
 */
fun Project.configureSigning() {
    if (providers.gradleProperty("KINETICA_SIGN").orNull != "true") return
    val inMemoryKey = providers.gradleProperty("signingInMemoryKey").orNull?.trim()?.takeIf { it.isNotEmpty() }
    val useGpg = providers.gradleProperty("signing.gnupg.keyName").isPresent
    if (inMemoryKey == null && !useGpg) return
    extensions.configure<SigningExtension> {
        if (inMemoryKey != null) {
            useInMemoryPgpKeys(inMemoryKey, providers.gradleProperty("signingInMemoryKeyPassword").orNull?.trim())
        } else {
            useGpgCmd()
        }
        sign(extensions.getByType<PublishingExtension>().publications)
    }
}

val sdkProjects = subprojects.filter { it.name in sdkModules }

/** 모든 SDK 모듈을 서명해 Central Portal 업로드용 zip을 만든다. 업로드는 하지 않는다. */
tasks.register<Zip>("centralBundle") {
    group = "publishing"
    description = "Maven Central 업로드 묶음(build/central-bundle.zip)을 만든다."
    dependsOn(sdkProjects.map { "${it.path}:publishReleasePublicationToCentralStagingRepository" })
    val hasLicense = licenseName != null && licenseUrl != null
    doFirst {
        check(hasLicense) { "KINETICA_LICENSE_NAME / KINETICA_LICENSE_URL이 없습니다. 라이선스를 정한 뒤 gradle.properties에 넣으세요." }
        val root = centralStaging.get().asFile
        val unsigned = root.walkTopDown().filter { it.isFile && it.extension in setOf("aar", "jar", "pom", "module") }
            .filter { !java.io.File(it.path + ".asc").exists() }.toList()
        check(unsigned.isEmpty()) { "서명되지 않은 파일이 있습니다. -PKINETICA_SIGN=true와 서명 키 설정을 확인하세요: ${unsigned.take(3)}" }
    }
    from(centralStaging) { exclude("**/maven-metadata*") }
    archiveFileName.set("central-bundle.zip")
    destinationDirectory.set(layout.buildDirectory)
    notCompatibleWithConfigurationCache("임시 저장소 내용을 실행 시점에 검사한다")
}
