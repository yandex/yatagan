plugins {
    kotlin("jvm")
    id("java-test-fixtures")
    id("maven-publish")
}

dependencies {
    implementation(project(":core-impl"))
    implementation(project(":generator"))
    implementation(project(":jap-driver-lang"))
    implementation(kotlin("stdlib"))

    api("com.google.auto:auto-common:${Versions.AutoCommon}")

    testFixturesImplementation(testFixtures(project(":testing")))
    testFixturesImplementation(kotlin("test"))
}