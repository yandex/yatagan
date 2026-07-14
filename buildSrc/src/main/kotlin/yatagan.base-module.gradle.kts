import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
    google()
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }

    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        // -Xjvm-default=all-compatibility is the compiler default since Kotlin 2.2
        allWarningsAsErrors.set(true)
    }

    sourceSets.configureEach {
        languageSettings {
            optIn("kotlin.ExperimentalStdlibApi")
            optIn("kotlin.contracts.ExperimentalContracts")
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinJvmCompile>().named { it.contains("Test") }.configureEach {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

tasks.withType<JavaCompile>().named { it.contains("Test") }.configureEach {
    sourceCompatibility = JavaVersion.VERSION_11.toString()
    targetCompatibility = JavaVersion.VERSION_11.toString()
}

extra["yataganVersion"] = providers.fileContents(rootProject.layout.projectDirectory.file("yatagan.version"))
    .asText.get().trim()

extra["enableCoverage"] = providers.gradleProperty("enable_coverage").orNull.toBoolean()
