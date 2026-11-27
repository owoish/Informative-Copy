import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.6.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion").get())
        bundledPlugin("Git4Idea")
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.informativecopy.plugin"
        name = providers.gradleProperty("pluginName").get()
        version = providers.gradleProperty("pluginVersion").get()
        description = """
            Context-aware high-information copy for JetBrains IDEs (Rider, IntelliJ IDEA, CLion, etc.), designed for AI Agent workflows.
            <br/>
            Press one shortcut (default <b>Ctrl+Shift+C</b>) anywhere in the IDE:
            <ul>
              <li><b>Code Editor</b>: relative path, line / line range, and selected code (or current line when nothing is selected).</li>
              <li><b>Diff Viewer</b>: file path, diff context, line numbers, and selected code.</li>
              <li><b>Git Log Commits</b>: commit hash, author, timestamp, full message, changed files, and optional unified diff.</li>
              <li><b>Local Changes</b>: changed file status list and optional diff.</li>
              <li><b>Problems / Build / Find / Debug Trees</b>: location, diagnostic message, and source line preview.</li>
              <li><b>Solution / Project Tree</b>: relative file and directory paths.</li>
            </ul>
        """.trimIndent()

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            val until = providers.gradleProperty("pluginUntilBuild").orNull
            untilBuild = if (until.isNullOrBlank()) provider { null } else provider { until }
        }
    }

    buildSearchableOptions = false
    instrumentCode = false
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    test {
        useJUnitPlatform()
    }
}
