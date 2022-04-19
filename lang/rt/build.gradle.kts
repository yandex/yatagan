plugins {
    id("daggerlite.artifact")
}

val kotlinxMetadataVersion: String by extra

dependencies {
    implementation(project(":api"))
    implementation(project(":base"))
    api(project(":lang"))

    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:$kotlinxMetadataVersion")
}