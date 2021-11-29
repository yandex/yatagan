plugins {
    id("daggerlite.artifact")
}

val autoCommonVersion: String by extra
val kotlinxMetadataVersion: String by extra

dependencies {
    implementation(project(":api"))
    implementation(project(":base"))

    api(project(":lang"))
    api(project(":generator-lang"))

    implementation("com.google.auto:auto-common:$autoCommonVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:$kotlinxMetadataVersion")

    implementation(kotlin("stdlib"))
}