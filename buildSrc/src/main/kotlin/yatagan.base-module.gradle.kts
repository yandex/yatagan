import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

repositories {
    maven {
        name = "mavenCentral"
        url = uri("https://artifactory.yandex.net/central")
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-Xjvm-default=all",
            "-Werror"
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

val kotlinVersion: String by extra(
    providers.fileContents(rootProject.layout.projectDirectory.file("kotlin.version"))
        .asText.get().trimEnd()
)
val dokkaVersion: String by extra(kotlinVersion)
val kspVersion by extra("$kotlinVersion-1.0.6")
val javaPoetVersion by extra("1.13.0")
val kotlinPoetVersion by extra("1.11.0")
val kotlinCompileTestingVersion by extra("1.4.9")
val autoCommonVersion by extra("1.2.1")
val kotlinxCliVersion by extra("0.3.4")
val junitVersion by extra("4.13.2")
val mockitoKotlinVersion by extra("4.0.0")

val yataganVersion: String by extra(
    providers.fileContents(rootProject.layout.projectDirectory.file("yatagan.version"))
        .asText.get().trim()
)