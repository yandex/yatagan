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
            "-Xopt-in=kotlin.RequiresOptIn",
            "-Xopt-in=kotlin.ExperimentalStdlibApi",
            "-Xopt-in=kotlin.contracts.ExperimentalContracts",
        )
    }
}

val kotlinVersion: String by extra(
    providers.fileContents(rootProject.layout.projectDirectory.file("kotlin.version"))
        .asText.forUseAtConfigurationTime().get().trimEnd()
)
val kspVersion by extra("$kotlinVersion-1.0.4")
val javaPoetVersion by extra("1.13.0")
val kotlinPoetVersion by extra("1.11.0")
val kotlinCompileTestingVersion by extra("1.4.7")
val autoCommonVersion by extra("1.2.1")
val kotlinxMetadataVersion by extra("0.4.0")
val junitVersion by extra("4.13.2")
val mockitoKotlinVersion by extra("4.0.0")

java {
    toolchain {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}