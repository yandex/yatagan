plugins {
    id("daggerlite.artifact")
    id("java-test-fixtures")
}

dependencies {
    api(project(":api"))

    implementation(project(":base"))
    implementation(project(":graph-impl"))
    implementation(project(":core-impl"))
    implementation(project(":lang-rt"))
    implementation(kotlin("stdlib"))

    testFixturesImplementation(testFixtures(project(":testing")))
    testFixturesImplementation(project(":api"))
    testFixturesImplementation(project(":base"))
    testFixturesImplementation(project(":graph-impl"))
    testFixturesImplementation(project(":core-impl"))
    testFixturesImplementation(project(":lang-rt"))
    testFixturesImplementation(project(":validation-impl"))
    testFixturesImplementation(kotlin("test"))
}