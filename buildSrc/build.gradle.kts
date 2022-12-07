plugins {
    `kotlin-dsl`
}

repositories {
    maven {
        name = "mavenCentral"
        url = uri("https://artifactory.yandex.net/central")
    }
    maven {
        name = "gradlePluginPortal"
        url = uri("https://artifactory.yandex.net/gradle")
    }
}

dependencies {
    implementation(libs.kotlin.gradle)
    implementation(libs.dokka.gradle)

    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
}
