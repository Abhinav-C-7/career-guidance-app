import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Type-safe navigation routes are @Serializable classes.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.foreway"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // This becomes permanent on the first Play Store upload.
        applicationId = "app.foreway"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        // Server address and the PUBLISHABLE key, read from the gitignored local.properties:
        //   foreway.serverUrl=https://<ref>.supabase.co
        //   foreway.publishableKey=sb_publishable_...
        // The publishable key is public by design (anything in an APK is). Never put the
        // secret key here. Missing values build an app that runs on its local store only.
        val local = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
        }
        val secretLooking = local.getProperty("foreway.publishableKey", "").startsWith("sb_secret_")
        check(!secretLooking) { "foreway.publishableKey holds a SECRET key. It would ship in the APK." }
        buildConfigField("String", "SERVER_URL", "\"${local.getProperty("foreway.serverUrl", "")}\"")
        buildConfigField("String", "PUBLISHABLE_KEY", "\"${local.getProperty("foreway.publishableKey", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(libs.work.runtime.ktx)
    implementation(libs.navigation.compose)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.kotlin.test.junit)
}
