plugins {
    application
    id("com.diffplug.spotless") version "7.2.1"
}
dependencies {
    testImplementation(project(":domain"))
    implementation(project(":tui"))
    implementation("dev.langchain4j:langchain4j:1.21.0")
    implementation("dev.langchain4j:langchain4j-agentic:1.21.0-beta31")
    implementation("dev.langchain4j:langchain4j-mcp:1.21.0-beta31")
    implementation("dev.langchain4j:langchain4j-google-genai:1.21.0-beta31")
    implementation("com.google.genai:google-genai:1.71.0")
    implementation("dev.langchain4j:langchain4j-anthropic:1.21.0")
    implementation("dev.langchain4j:langchain4j-open-ai:1.21.0")
    implementation("dev.langchain4j:langchain4j-typesafe:1.21.0-beta31")
    implementation("dev.langchain4j:langchain4j-http-client-jdk:1.21.0")
    implementation("org.slf4j:slf4j-api:2.0.18")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
sourceSets.main { resources.srcDir(rootProject.file(".shared/domain/src/main/resources")) }
application { mainClass.set("dev.gamov.jclaw.app.Main") }
spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat("1.28.0")
    }
}
val checkScripts = tasks.register<Exec>("checkScripts") {
    workingDir(rootProject.projectDir)
    commandLine("bash", "scripts/run-tests.sh")
}
tasks.check { dependsOn(tasks.spotlessCheck, checkScripts) }
tasks.test {
    useJUnitPlatform()
    dependsOn(":mocks:mcpJars")
    systemProperty("jclaw.root", rootProject.projectDir.absolutePath)
}
tasks.register<Test>("reportFixture") {
    description = "Generate native HTML reports from a provider-free workflow fixture"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    systemProperty("jclaw.root", rootProject.projectDir.absolutePath)
    systemProperty("jclaw.report.fixture.directory", rootProject.file("state/report-fixture").absolutePath)
    filter { includeTestsMatching("dev.gamov.jclaw.agent.WorkflowReportsTest.nativeReportsCaptureRefinementAndHumanVerdicts") }
    outputs.dir(rootProject.file("state/report-fixture"))
}
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
