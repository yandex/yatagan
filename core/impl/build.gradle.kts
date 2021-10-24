plugins {
    kotlin("jvm")
    id("maven-publish")
}

dependencies {
    api(project(":core"))
    implementation(project(":core-lang"))

    implementation(kotlin("stdlib"))
}