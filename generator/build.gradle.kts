plugins {
    kotlin("jvm")
    id("maven-publish")
}

dependencies {
    implementation(project(":core"))

    implementation(project("poetry"))
    implementation(kotlin("stdlib"))
}