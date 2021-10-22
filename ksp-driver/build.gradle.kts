plugins {
    kotlin("jvm")
    id("java-test-fixtures")
    id("maven-publish")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":generator"))

    implementation(kotlin("stdlib"))
    implementation("com.google.devtools.ksp:symbol-processing-api:${Versions.Ksp}")

    testFixturesImplementation(testFixtures(project(":testing")))
    testFixturesImplementation(kotlin("test"))
}