import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Pure JVM. No Android dependencies here, ever — the rules engine must stay runnable in a
// plain unit test without an emulator (CLAUDE.md, conventions).
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    api(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}
