plugins {
    kotlin("jvm")
    id("maven-publish")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":generator-poetry"))
    implementation(project(":generator-lang"))
    implementation(kotlin("stdlib"))
}