plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("java-test-fixtures")
    id("maven-publish")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":generator"))
    implementation(kotlin("stdlib"))

    api("com.google.auto:auto-common:${Versions.AutoCommon}")

    testFixturesImplementation(testFixtures(project(":testing")))
    testFixturesImplementation(kotlin("test"))
}