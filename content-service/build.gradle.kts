import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.springframework.boot.gradle.plugin.SpringBootPlugin
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
}

// The content service: the human review queue now, the scheduled source fetcher and
// extraction pipeline later. Runs on Railway; never in the student read path.
//
//   ./gradlew :content-service:bootRun     local, reads .env at the repo root
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        allWarningsAsErrors.set(true)
        // Treat Spring's nullability annotations as real Kotlin types.
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

dependencies {
    // The Boot BOM as a plain platform rather than the dependency-management plugin, so it
    // cannot pull Kotlin or kotlinx versions underneath the rest of the build.
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))

    // The same model and parser the app uses. A record the app could not read is a record
    // the review screen refuses to approve.
    implementation(project(":domain"))
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.thymeleaf)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.kotlin.reflect)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.kotlin.test.junit5)
}

tasks.test {
    useJUnitPlatform()
}

// One runnable jar with a fixed name, so the Dockerfile can copy it without a glob.
tasks.bootJar {
    archiveFileName.set("content-service.jar")
}

tasks.jar {
    enabled = false
}

tasks.named<BootRun>("bootRun") {
    // .env lives at the repo root.
    workingDir = rootDir
}
