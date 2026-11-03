pluginManagement {
    val mavenProperties =
        java.util.Properties().apply {
            val source = settingsDir.resolve("maven.properties")
            if (source.isFile) {
                source.inputStream().use { load(it) }
            }
        }
    val cliMavenLocal =
        providers
            .gradleProperty("project.repositories.mavenLocal")
            .map { it.trim().toBoolean() }
            .orNull
    val fileMavenLocal = mavenProperties.getProperty("maven.local")?.trim()?.toBooleanStrictOrNull()
    val localMaven = cliMavenLocal ?: fileMavenLocal ?: false
    val explicitRepoLocal =
        providers
            .systemProperty("maven.repo.local")
            .orNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    val localRepository =
        if (explicitRepoLocal != null) {
            File(explicitRepoLocal)
        } else {
            mavenProperties
                .getProperty("maven.localRepository")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { File(it) }
                ?: File(System.getProperty("user.home"), ".m2/repository")
        }
    require(localRepository.isAbsolute) {
        "maven.localRepository / maven.repo.local must be an absolute path: $localRepository"
    }

    // Gradle's Maven resolver and publishToMavenLocal share this repository path.
    System.setProperty("maven.repo.local", localRepository.absolutePath)
    settings.extra["mavenLocalEnabled"] = localMaven

    repositories {
        if (localMaven) mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

val localMavenEnabled = extra["mavenLocalEnabled"] as Boolean

dependencyResolutionManagement {
    // Kotlin tool setup temporarily adds and removes its Node/Yarn/Binaryen Ivy repositories.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        if (localMavenEnabled) mavenLocal()
        google()
        mavenCentral()
    }
}

// Apply the same order to every project's build and buildscript dependencies.
gradle.beforeProject {
    repositories {
        if (localMavenEnabled) mavenLocal()
        google()
        mavenCentral()
    }
    buildscript.repositories {
        if (localMavenEnabled) mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// The repo name lives in gradle.properties (project.name); scaffolds must not
// hand-edit this file to rename the project.
rootProject.name = providers.gradleProperty("project.name").get()
