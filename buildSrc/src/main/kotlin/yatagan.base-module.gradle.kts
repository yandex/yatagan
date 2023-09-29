import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
    google()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-Xjvm-default=all-compatibility",
            "-Werror",
        )
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }

    sourceSets.configureEach {
        languageSettings {
            optIn("kotlin.ExperimentalStdlibApi")
            optIn("kotlin.contracts.ExperimentalContracts")
        }
    }
}

val yataganVersion: String by extra(
    providers.fileContents(rootProject.layout.projectDirectory.file("yatagan.version"))
        .asText.get().trim()
)

val enableCoverage: Boolean by extra(
    providers.gradleProperty("enable_coverage").orNull.toBoolean()
)
