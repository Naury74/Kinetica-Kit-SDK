import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kinetica.quality)
}

// 배포하지 않는 측정 도구. 결과는 build/reports/benchmarks/에 쓴다. check에는 넣지 않는다(기기·부하에 따라 흔들린다).
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":kinetica-core"))
    implementation(project(":kinetica-history"))
    implementation(project(":kinetica-motion"))
    implementation(project(":kinetica-guides"))
    implementation(project(":kinetica-serialization"))
}

tasks.register<JavaExec>("runBenchmarks") {
    group = "verification"
    description = "JVM에서 핵심 연산 시간을 재 build/reports/benchmarks/jvm.md에 쓴다."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.naury.kinetica.benchmarks.MainKt")
    val report = layout.buildDirectory.file("reports/benchmarks/jvm.md")
    args(report.get().asFile.absolutePath)
    outputs.upToDateWhen { false }
}
