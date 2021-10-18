import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version Versions.Kotlin apply false
    id("com.google.devtools.ksp") version Versions.Ksp apply false
}

subprojects {
    ext["version"] = "0.0.1-SNAPSHOT"
    ext["group"] = "com.yandex.daggerlite"

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = freeCompilerArgs + listOf(
                "-Xopt-in=kotlin.ExperimentalStdlibApi",
                "-Xopt-in=kotlin.contracts.ExperimentalContracts",
            )
        }
    }

    repositories {
        mavenCentral()
    }
}