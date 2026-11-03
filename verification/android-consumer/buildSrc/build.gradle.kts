plugins { java }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
dependencies {
    implementation(gradleApi())
    implementation(localGroovy())
}
sourceSets.main {
    java.setSrcDirs(listOf("../../../buildSrc/src/main/java"))
    java.include("**/Json.java", "**/BuildToolTask.java", "**/AndroidClasspathVerifier.java", "**/AndroidPublicationVerifier.java")
}
