plugins {
    kotlin("jvm")
    id("java-test-fixtures")
    id("maven-publish")
}

dependencies {
    implementation(project(":core-impl"))
    implementation(project(":generator"))
    implementation(project(":ksp-driver-lang"))

    implementation(kotlin("stdlib"))
    implementation("com.google.devtools.ksp:symbol-processing-api:${Versions.Ksp}")

    testFixturesImplementation(testFixtures(project(":testing")))
    testFixturesImplementation(kotlin("test"))
}