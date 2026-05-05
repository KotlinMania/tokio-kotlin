pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins { kotlin("multiplatform") version "2.3.21" }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0" }

dependencyResolutionManagement {
    repositories {
        google()
        maven("https://repo1.maven.org/maven2/")
        mavenCentral()
    }
}

rootProject.name = "tokio-kotlin"
