plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

val kotlinVersion: String = providers
    .fileContents(layout.projectDirectory.file("../kotlin.version"))
    .asText.forUseAtConfigurationTime()
    .get().trimEnd()

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    testImplementation(kotlin("test"))
}
