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

val projectUrl = "https://github.com/Naury74/Kinetica-Kit-SDK"

// Maven Central 업로드 묶음을 만들 임시 저장소. 서명·체크섬이 붙은 배포 파일이 모듈별로 모인다.
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
                        licenses {
                            license {
                                name.set("The Apache License, Version 2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                                distribution.set("repo")
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
                            developerConnection.set("scm:git:ssh://git@github.com/Naury74/Kinetica-Kit-SDK.git")
                        }
                        issueManagement {
                            system.set("GitHub")
                            url.set("$projectUrl/issues")
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
 * 서명 키가 있으면 배포 파일에 서명한다. 키는 저장소에 두지 않고 `~/.gradle/gradle.properties`나 환경 변수에서 읽는다.
 *
 * - `signingInMemoryKey` / `signingInMemoryKeyPassword`: ASCII armor 비밀 키와 비밀번호(CI에 알맞음).
 * - `signing.gnupg.keyName` (+ `signing.gnupg.passphrase`): 설치된 gpg로 서명(개인 컴퓨터에 알맞음).
 *
 * 키가 없으면 서명하지 않으므로 `publishToMavenLocal`은 그대로 동작한다. Central 묶음을 만들 때는 서명이 필수다.
 */
fun Project.configureSigning() {
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

/** 모든 SDK 모듈을 서명해 임시 저장소에 모으고 Central Portal 업로드용 zip을 만든다. */
val centralBundle = tasks.register<Zip>("centralBundle") {
    group = "publishing"
    description = "Maven Central 업로드 묶음(build/central-bundle.zip)을 만든다."
    dependsOn(sdkProjects.map { "${it.path}:publishReleasePublicationToCentralStagingRepository" })
    doFirst {
        val root = centralStaging.get().asFile
        val unsigned = root.walkTopDown().filter { it.isFile && it.extension in setOf("aar", "jar", "pom", "module") }
            .filter { !java.io.File(it.path + ".asc").exists() }.toList()
        check(unsigned.isEmpty()) { "서명되지 않은 파일이 있습니다. 서명 키 설정(~/.gradle/gradle.properties)을 확인하세요: ${unsigned.take(3)}" }
    }
    from(centralStaging) { exclude("**/maven-metadata*") }
    archiveFileName.set("central-bundle.zip")
    destinationDirectory.set(layout.buildDirectory)
    notCompatibleWithConfigurationCache("임시 저장소 내용을 실행 시점에 검사한다")
}

tasks.register("cleanCentralStaging") {
    group = "publishing"
    description = "이전 버전이 섞이지 않도록 Central 임시 저장소를 비운다."
    val dir = centralStaging
    doLast { dir.get().asFile.deleteRecursively() }
}

/**
 * 묶음을 Central Portal에 올린다. 기본은 검증 후 대기(USER_MANAGED)라 포털에서 내용을 확인하고 직접 Publish를 누른다.
 * 자격 증명은 Central Portal에서 만든 사용자 토큰(`centralPortalUsername` / `centralPortalPassword`)이다.
 * `-PcentralAutoPublish=true`를 주면 검증이 끝나는 대로 바로 공개한다(되돌릴 수 없다).
 */
tasks.register("publishToCentral") {
    group = "publishing"
    description = "build/central-bundle.zip을 Maven Central(Central Portal)에 업로드한다."
    dependsOn(centralBundle)
    notCompatibleWithConfigurationCache("업로드는 네트워크 작업이다")
    doLast {
        // 복사해 붙여 넣으며 생긴 앞뒤 공백이 토큰에 섞이면 인증이 실패하므로 잘라 낸다.
        val user = providers.gradleProperty("centralPortalUsername").orNull?.trim()?.takeIf { it.isNotEmpty() }
        val password = providers.gradleProperty("centralPortalPassword").orNull?.trim()?.takeIf { it.isNotEmpty() }
        check(user != null && password != null) { "centralPortalUsername / centralPortalPassword가 없습니다. ~/.gradle/gradle.properties에 넣으세요." }
        val bundle = layout.buildDirectory.file("central-bundle.zip").get().asFile
        val auto = providers.gradleProperty("centralAutoPublish").orNull == "true"
        val token = java.util.Base64.getEncoder().encodeToString("$user:$password".toByteArray())
        val boundary = "----Kinetica${System.currentTimeMillis()}"
        val head = "--$boundary\r\nContent-Disposition: form-data; name=\"bundle\"; filename=\"${bundle.name}\"\r\n" +
            "Content-Type: application/octet-stream\r\n\r\n"
        val body = head.toByteArray() + bundle.readBytes() + "\r\n--$boundary--\r\n".toByteArray()
        val name = java.net.URLEncoder.encode("${project.property("KINETICA_GROUP")}:kinetica:${project.property("KINETICA_VERSION")}", "UTF-8")
        val type = if (auto) "AUTOMATIC" else "USER_MANAGED"
        val request = java.net.http.HttpRequest.newBuilder(
            uri("https://central.sonatype.com/api/v1/publisher/upload?publishingType=$type&name=$name"),
        )
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(body))
            .build()
        val response = java.net.http.HttpClient.newHttpClient().send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) { "업로드 실패(${response.statusCode()}): ${response.body()}" }
        logger.lifecycle("업로드 완료. 배포 ID: ${response.body()}")
        logger.lifecycle(if (auto) "검증이 끝나면 자동으로 공개됩니다." else "https://central.sonatype.com/publishing/deployments 에서 확인 후 Publish를 누르세요.")
    }
}
