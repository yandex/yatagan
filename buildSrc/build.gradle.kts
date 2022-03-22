import java.net.URI

plugins {
    `kotlin-dsl`
}

repositories {
    maven {
        name = "mavenCentral"
        url = URI.create("https://artifactory.yandex.net/central")
    }
    maven {
        name = "gradlePluginPortal"
        url = URI.create("https://artifactory.yandex.net/gradle")
    }
}

val kotlinVersion: String = providers
    .fileContents(layout.projectDirectory.file("../kotlin.version"))
    .asText.forUseAtConfigurationTime()
    .get().trimEnd()

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
}
