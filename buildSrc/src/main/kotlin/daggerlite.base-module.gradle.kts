import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

plugins {
    kotlin("jvm")
}

repositories {
    maven {
        name = "mavenCentral"
        url = URI.create("https://artifactory.yandex.net/central")
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-Xjvm-default=all",
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlin.ExperimentalStdlibApi",
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-Werror"
        )
    }
}

val kotlinVersion: String by extra(
    providers.fileContents(rootProject.layout.projectDirectory.file("kotlin.version"))
        .asText.get().trimEnd()
)
val dokkaVersion: String by extra(kotlinVersion)
val kspVersion by extra("$kotlinVersion-1.0.5")
val javaPoetVersion by extra("1.13.0")
val kotlinPoetVersion by extra("1.11.0")
val kotlinCompileTestingVersion by extra("1.4.8")
val kotlinCoroutinesCoreVersion by extra("1.6.2")
val autoCommonVersion by extra("1.2.1")
val kotlinxCliVersion by extra("0.3.4")
val junitVersion by extra("4.13.2")
val mockitoKotlinVersion by extra("4.0.0")

val daggerLiteVersion: String by extra(
    providers.fileContents(rootProject.layout.projectDirectory.file("daggerlite.version"))
        .asText.get().trim()
)

java {
    toolchain {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}