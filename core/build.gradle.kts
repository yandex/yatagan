plugins {
    kotlin("jvm")
    id("maven-publish")
}

dependencies {
    api(project(":api"))
    api(project(":core-lang"))

    implementation(kotlin("stdlib"))
}