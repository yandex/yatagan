import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("yatagan.base-module")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}