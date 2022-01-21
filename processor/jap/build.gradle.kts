plugins {
    id("daggerlite.artifact")
    id("java-test-fixtures")
}

val autoCommonVersion: String by extra

dependencies {
    implementation(project(":api"))
    implementation(project(":processor"))
    implementation(project(":lang-jap"))
    implementation(kotlin("stdlib"))

    api("com.google.auto:auto-common:$autoCommonVersion")

    testFixturesImplementation(testFixtures(project(":testing")))
    testFixturesImplementation(kotlin("test"))
}