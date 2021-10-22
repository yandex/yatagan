import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version Versions.Kotlin apply false
    id("com.google.devtools.ksp") version Versions.Ksp apply false
}

subprojects {
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = freeCompilerArgs + listOf(
                "-Xopt-in=kotlin.ExperimentalStdlibApi",
                "-Xopt-in=kotlin.contracts.ExperimentalContracts"
            )
        }
    }

    repositories {
        mavenCentral()
    }

    afterEvaluate {
        version = "0.0.1-SNAPSHOT"
        group = "com.yandex.daggerlite"

        extensions.findByType<PublishingExtension>()?.apply {
            publications {
                create<MavenPublication>(name) {
                    from(components["java"])
                }
            }
        }

        extensions.findByType<JavaPluginExtension>()?.apply {
            toolchain {
                sourceCompatibility = JavaVersion.VERSION_1_8
                targetCompatibility = JavaVersion.VERSION_1_8
            }
        }
    }
}
