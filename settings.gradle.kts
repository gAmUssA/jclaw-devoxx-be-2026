pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement {
    repositories { mavenCentral() }
    versionCatalogs { create("libs") { from(files(".shared/gradle/libs.versions.toml")) } }
}
rootProject.name = "jclaw-langchain4j"
include(":domain", ":mocks", ":tui", ":app")
for (name in listOf("domain", "mocks", "tui")) {
    project(":$name").projectDir = file(".shared/$name")
}
