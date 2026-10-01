pluginManagement {
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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "foreway"

include(":domain")
include(":tools:publish")
include(":content-service")

// The server image is built without the Android SDK, and Android modules cannot even be
// configured without one. FOREWAY_SERVER_ONLY=true (set in content-service/Dockerfile)
// leaves them out; everywhere else they are part of the build as normal.
if (System.getenv("FOREWAY_SERVER_ONLY") != "true") {
    include(":app")
    include(":data")
}
