import com.diffplug.gradle.spotless.SpotlessExtension
import com.github.gradle.node.npm.task.NpxTask

plugins {
    base
    id("com.github.node-gradle.node") version "7.1.0"
    id("com.diffplug.spotless") version "8.10.4" apply false
}

node {
    version = "24.22.3"
    download = true
}

val markdownLint = tasks.register<NpxTask>("markdownLint") {
    group = "verification"
    description = "Checks Markdown formatting."
    command = "markdownlint-cli2@0.24.2"
    environment.put("npm_config_yes", "true")
}

allprojects {
    group = "br.dev.lucas"
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "com.diffplug.spotless")

    repositories {
        mavenCentral()
    }

    dependencies {
        add("testImplementation", platform("org.junit:junit-bom:6.1.3"))
        add("testImplementation", "org.junit.jupiter:junit-jupiter")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    extensions.configure<SpotlessExtension> {
        java {
            palantirJavaFormat("2.102.0")
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

tasks.check {
    dependsOn(markdownLint)
    dependsOn(subprojects.map { it.tasks.named("check") })
}
