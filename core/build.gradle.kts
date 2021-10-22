plugins {
    kotlin("jvm")
    id("maven-publish")
}

dependencies {
    api(project(":api"))

    implementation(kotlin("stdlib"))
}