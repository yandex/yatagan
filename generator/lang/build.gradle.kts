plugins {
    kotlin("jvm")
    id("maven-publish")
}

dependencies {
    implementation(project(":core-lang"))
    implementation(kotlin("stdlib"))
}