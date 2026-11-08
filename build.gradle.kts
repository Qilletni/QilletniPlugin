plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.7.1"
}

group = "dev.qilletni.intellij"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Configure IntelliJ Platform Gradle Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    // Core parser and HTML renderer only; flexmark-all pulls in dozens of unused extensions
    implementation("com.vladsch.flexmark:flexmark:0.64.8")
    implementation("org.yaml:snakeyaml:2.2")

    testImplementation("junit:junit:4.13.2")

    implementation("se.michaelthelin.spotify:spotify-web-api-java:9.3.0") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }


    intellijPlatform {
        create("IC", "2025.1.4.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Add necessary plugin dependencies for compilation here, example:
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.plugins.yaml")
        // YAML needs the JSON plugin; without it YAML (and so this plugin) is disabled in tests
        bundledPlugin("com.intellij.modules.json")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
            // Open-ended: the verifier checks compatibility with current IDEs instead of locking to 2025.1
            untilBuild = provider { null }
        }

        changeNotes = """
            Initial version
        """.trimIndent()
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

sourceSets {
    named("main") {
        java.srcDir("src/main/gen")
    }
}
