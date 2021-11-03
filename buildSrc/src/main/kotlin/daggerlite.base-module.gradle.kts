import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-Xopt-in=kotlin.ExperimentalStdlibApi",
            "-Xopt-in=kotlin.contracts.ExperimentalContracts",
            "-Xopt-in=com.google.devtools.ksp.KspExperimental",
        )
    }
}

val kotlinVersion by extra(properties["daggerlite.kotlin.version"])
val kspVersion by extra("$kotlinVersion-1.0.0")
val javaPoetVersion by extra("1.13.0")
val kotlinCompileTestingVersion by extra("1.4.5")
val autoCommonVersion by extra("0.11")

java {
    toolchain {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}