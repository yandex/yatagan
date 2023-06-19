plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.kotlin.gradle)
    implementation(libs.dokka.gradle)
    implementation(libs.nexusPublish.gradle)
    implementation(libs.ksp.gradle)

    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
}
