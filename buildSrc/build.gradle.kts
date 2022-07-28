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

val kotlinVersion: String = providers
    .fileContents(layout.projectDirectory.file("../kotlin.version"))
    .asText.get().trimEnd()

val dokkaVersion = kotlinVersion

val proguardVersion = "7.2.0"
val kotlinMetadataVersion = "0.4.2"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:$dokkaVersion")
    implementation("org.jetbrains.dokka:dokka-base:$dokkaVersion")

    implementation("com.guardsquare:proguard-gradle:$proguardVersion") {
        // Exclude kotlin, we provide our own version.
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
    }
    // This is required for proguard.
    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:$kotlinMetadataVersion")

    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
}
