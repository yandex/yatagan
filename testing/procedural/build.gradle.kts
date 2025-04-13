import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("yatagan.base-module")
    application
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

dependencies {
    api(project(":testing:source-set"))

    implementation(libs.poets.kotlin)
}
