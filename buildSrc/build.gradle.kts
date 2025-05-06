plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    check(libs.versions.ksp.get().startsWith(libs.versions.kotlin.get())) {
        "KSP and Kotlin versions mismatch"
    }

    implementation(libs.kotlin.gradle)
    implementation(libs.kotlin.binaryCompatibilityGradle)
    implementation(libs.kotlin.koverGradle)
    implementation(libs.dokka.gradle)
    implementation(libs.publish.gradle)
    implementation(libs.ksp.gradle)

    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    implementation(libs.poets.kotlin)
}
