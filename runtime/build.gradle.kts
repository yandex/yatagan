plugins {
    kotlin("jvm")
    id("maven-publish")
}

dependencies {
    implementation(project(":core-impl"))
    implementation(kotlin("stdlib"))
}