import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

// Publishes content/careers/*.json to Supabase. Runs on a developer machine with the
// secret key; never packaged into the app.
//
//   ./gradlew :tools:publish:run                 dry run, prints the plan
//   ./gradlew :tools:publish:run --args=--apply  writes
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

application {
    mainClass.set("app.foreway.tools.publish.MainKt")
}

tasks.named<JavaExec>("run") {
    // Paths like content/careers and .env resolve from the repo root, not this module.
    workingDir = rootDir
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}
