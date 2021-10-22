plugins {
    kotlin("jvm")
    id("maven-publish")
}

dependencies {
    api("com.squareup:javapoet:${Versions.JavaPoet}")
    implementation(project(":api"))
    implementation(kotlin("stdlib"))
}