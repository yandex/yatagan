plugins {
    kotlin("jvm")
    id("maven-publish")
}

dependencies {
    implementation(project(":api"))
    implementation(project(":core-lang"))
    implementation(project(":generator-lang"))

    implementation("com.google.auto:auto-common:${Versions.AutoCommon}")

    implementation(kotlin("stdlib"))
}