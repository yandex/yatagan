plugins {
    id("daggerlite.artifact")
    id("java-test-fixtures")
}

val autoCommonVersion: String by extra

dependencies {
    implementation(project(":core-impl"))
    implementation(project(":generator"))
    implementation(project(":jap-driver-lang"))
    implementation(kotlin("stdlib"))

    api("com.google.auto:auto-common:$autoCommonVersion")

    testFixturesImplementation(testFixtures(project(":testing")))
    testFixturesImplementation(kotlin("test"))
}